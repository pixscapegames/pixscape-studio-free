package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.Gdx;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

/** Coordinates generic, generation-aware asynchronous preparation. */
public final class AsyncAtlasRepackCoordinator<T extends AutoCloseable> {

    public enum RepackReason {
        GENERIC,
        DROP,
        SAVE
    }

    @FunctionalInterface
    public interface PackRunner<T extends AutoCloseable> {
        T pack(String targetKey, long generation, RepackReason reason) throws Exception;
    }

    public record PackFailure(String targetKey, long generation, Exception cause) {}

    /**
     * The coordinator owns a prepared payload until the caller takes it. Callers own a taken
     * payload; discarded or unclaimed payloads are closed by this envelope.
     */
    public static final class Prepared<T extends AutoCloseable> implements AutoCloseable {
        private final String targetKey;
        private final long generation;
        private T payload;

        private Prepared(String targetKey, long generation, T payload) {
            this.targetKey = targetKey;
            this.generation = generation;
            this.payload = payload;
        }

        public String targetKey() {
            return targetKey;
        }

        public long generation() {
            return generation;
        }

        public T takePayload() {
            T taken = payload;
            payload = null;
            return taken;
        }

        public void discard() {
            if (payload == null) return;
            try {
                payload.close();
            } catch (Exception failure) {
                error("Unable to discard prepared payload", failure);
            } finally {
                payload = null;
            }
        }

        @Override
        public void close() {
            discard();
        }
    }

    private static final String TAG = "AtlasRepackCoordinator";
    private static final long PACK_DEBOUNCE_MS = 400L;

    private final PackRunner<T> packRunner;

    private static ExecutorService createExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "pixscape-atlas-repack");
            t.setDaemon(true);
            return t;
        });
    }

    private final ExecutorService executor;
    private final LongSupplier clock;
    private final long debounceMs;

    private Future<?> runningFuture;

    private boolean disposed;
    private boolean asyncPackRequested;
    private boolean asyncPackRunning;
    private boolean asyncPackReady;

    private String requestedTargetKey;
    private String runningTargetKey;

    private long requestedGeneration;
    private long runningGeneration;

    private RepackReason requestedReason = RepackReason.GENERIC;
    private long lastRequestMs;

    private Prepared<T> readyPrepared;
    private PackFailure readyFailure;

    public AsyncAtlasRepackCoordinator(PackRunner<T> packRunner) {
        this(packRunner, createExecutor(), System::currentTimeMillis, PACK_DEBOUNCE_MS);
    }

    /** Executor/clock injection for deterministic behavioral tests, not a separate repack framework. */
    public AsyncAtlasRepackCoordinator(PackRunner<T> packRunner, ExecutorService executor,
                                       LongSupplier clock, long debounceMs) {
        if (packRunner == null) throw new IllegalArgumentException("packRunner is null");
        this.packRunner = packRunner;
        this.executor = java.util.Objects.requireNonNull(executor);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.debounceMs = debounceMs;
    }

    public synchronized long requestAsyncPack(String targetKey, RepackReason reason) {
        if (disposed) return requestedGeneration;
        if (targetKey == null || targetKey.isBlank()) {
            throw new IllegalArgumentException("targetKey is blank");
        }

        requestedGeneration++;
        requestedTargetKey = targetKey;
        requestedReason = reason != null ? reason : RepackReason.GENERIC;
        asyncPackRequested = true;
        lastRequestMs = clock.getAsLong();
        readyFailure = null;

        cancelRunning("newer request");
        discardReadyIfStale();

        return requestedGeneration;
    }

    public synchronized void update() {
        if (disposed || !asyncPackRequested || asyncPackRunning) return;

        long now = clock.getAsLong();
        if (now - lastRequestMs < debounceMs) return;

        launchAsyncPack();
    }

    public synchronized Prepared<T> pollReadyAsyncPack() {
        if (!asyncPackReady || readyPrepared == null) return null;

        Prepared<T> prepared = readyPrepared;
        asyncPackReady = false;
        readyPrepared = null;

        if (prepared.generation() != requestedGeneration) {
            prepared.discard();
            return null;
        }

        return prepared;
    }

    public synchronized boolean hasQueuedOrRunningFor(String targetKey) {
        if (targetKey == null || targetKey.isBlank()) {
            return asyncPackRequested || asyncPackRunning || asyncPackReady;
        }

        return (asyncPackRequested && targetKey.equals(requestedTargetKey))
                || (asyncPackRunning && targetKey.equals(runningTargetKey))
                || (readyPrepared != null && targetKey.equals(readyPrepared.targetKey()));
    }

    /** Delivered once on the caller/UI thread; cancellations and stale failures are not errors. */
    public synchronized PackFailure pollFailure() {
        PackFailure failure = readyFailure;
        readyFailure = null;
        return failure != null && failure.generation() == requestedGeneration ? failure : null;
    }

    public synchronized boolean isAsyncPackRequested() {
        return asyncPackRequested;
    }

    public synchronized boolean isAsyncPackRunning() {
        return asyncPackRunning;
    }

    public synchronized long currentGeneration() {
        return requestedGeneration;
    }

    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;

        asyncPackRequested = false;
        cancelRunning("dispose");

        if (readyPrepared != null) {
            readyPrepared.discard();
            readyPrepared = null;
        }

        asyncPackReady = false;
        readyFailure = null;

        executor.shutdownNow();
    }

    private void launchAsyncPack() {
        final String targetKey = requestedTargetKey;
        final long generation = requestedGeneration;
        final RepackReason reason = requestedReason;

        asyncPackRequested = false;
        asyncPackRunning = true;
        runningTargetKey = targetKey;
        runningGeneration = generation;

        runningFuture = executor.submit(() -> {
            T payload = null;

            try {
                log("Async pack started target=" + targetKey + " gen=" + generation + " reason=" + reason);

                payload = packRunner.pack(targetKey, generation, reason);

                if (Thread.currentThread().isInterrupted()) {
                    discardPayload(payload);
                    return;
                }

                synchronized (this) {
                    if (disposed || generation != requestedGeneration) {
                        discardPayload(payload);
                        return;
                    }

                    readyPrepared = new Prepared<>(targetKey, generation, payload);
                    payload = null;
                    asyncPackReady = true;
                }

                log("Async pack ready target=" + targetKey + " gen=" + generation);

            } catch (Exception ex) {
                discardPayload(payload);
                if (!Thread.currentThread().isInterrupted()) {
                    boolean currentFailure = false;
                    synchronized (this) {
                        if (!disposed && generation == requestedGeneration) {
                            readyFailure = new PackFailure(targetKey, generation, ex);
                            currentFailure = true;
                        }
                    }
                    if (currentFailure) error("Async pack failed target=" + targetKey + " gen=" + generation, ex);
                }
            } finally {
                synchronized (this) {
                    if (runningGeneration == generation) {
                        asyncPackRunning = false;
                        runningTargetKey = null;
                        runningFuture = null;
                    }
                }
            }
        });
    }

    private void cancelRunning(String reason) {
        if (runningFuture != null && !runningFuture.isDone()) {
            log("Cancelling async pack target=" + runningTargetKey
                    + " gen=" + runningGeneration
                    + " reason=" + reason);
            runningFuture.cancel(true);
        }

        asyncPackRunning = false;
        runningTargetKey = null;
        runningFuture = null;
    }

    private void discardReadyIfStale() {
        if (readyPrepared != null && readyPrepared.generation() != requestedGeneration) {
            readyPrepared.discard();
            readyPrepared = null;
            asyncPackReady = false;
        }
    }

    private static void discardPayload(AutoCloseable payload) {
        if (payload == null) return;
        try {
            payload.close();
        } catch (Exception failure) {
            error("Unable to discard prepared payload", failure);
        }
    }

    private static void log(String msg) {
        if (Gdx.app != null) Gdx.app.log(TAG, msg);
    }

    private static void error(String msg, Exception ex) {
        if (Gdx.app != null) Gdx.app.error(TAG, msg, ex);
    }
}
