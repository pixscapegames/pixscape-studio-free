package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.service.PreparedAtlasPublication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class AsyncAtlasRepackCoordinator {

    public enum RepackReason {
        GENERIC,
        DROP,
        SAVE
    }

    public interface PackRunner {
        RepackArtifact pack(String sceneTag, long generation, RepackReason reason) throws Exception;
    }

    public static final class RepackArtifact {
        private final String sceneTag;
        private final long generation;
        private final FileHandle outputDir;
        private final FileHandle atlasFile;
        private final FileHandle pngFile;
        private PreparedAtlasPublication preparedPublication;

        public RepackArtifact(String sceneTag,
                              long generation,
                              FileHandle outputDir,
                              FileHandle atlasFile,
                              FileHandle pngFile,
                              PreparedAtlasPublication preparedPublication) {
            this.sceneTag = sceneTag;
            this.generation = generation;
            this.outputDir = outputDir;
            this.atlasFile = atlasFile;
            this.pngFile = pngFile;
            this.preparedPublication = preparedPublication;
        }

        public String sceneTag() {
            return sceneTag;
        }

        public long generation() {
            return generation;
        }

        public FileHandle outputDir() {
            return outputDir;
        }

        public FileHandle atlasFile() {
            return atlasFile;
        }

        public FileHandle pngFile() {
            return pngFile;
        }

        public PreparedAtlasPublication takePreparedPublication() {
            PreparedAtlasPublication taken = preparedPublication;
            preparedPublication = null;
            return taken;
        }

        public void discard() {
            if (preparedPublication != null) {
                preparedPublication.close();
                preparedPublication = null;
            }
            deleteQuietly(outputDir);
        }
    }

    private static final String TAG = "AtlasRepackCoordinator";
    private static final long PACK_DEBOUNCE_MS = 400L;

    private final PackRunner packRunner;
    private final ExecutorService executor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "pixscape-atlas-repack");
                t.setDaemon(true);
                return t;
            });

    private Future<?> runningFuture;

    private boolean disposed;
    private boolean asyncPackRequested;
    private boolean asyncPackRunning;
    private boolean asyncPackReady;

    private String requestedSceneTag;
    private String runningSceneTag;

    private long requestedGeneration;
    private long runningGeneration;

    private RepackReason requestedReason = RepackReason.GENERIC;
    private long lastRequestMs;

    private RepackArtifact readyArtifact;

    public AsyncAtlasRepackCoordinator(PackRunner packRunner) {
        if (packRunner == null) throw new IllegalArgumentException("packRunner is null");
        this.packRunner = packRunner;
    }

    public synchronized long requestAsyncPack(String sceneTag, RepackReason reason) {
        if (disposed) return requestedGeneration;
        if (sceneTag == null || sceneTag.isBlank()) {
            throw new IllegalArgumentException("sceneTag is blank");
        }

        requestedGeneration++;
        requestedSceneTag = sceneTag;
        requestedReason = reason != null ? reason : RepackReason.GENERIC;
        asyncPackRequested = true;
        lastRequestMs = System.currentTimeMillis();

        cancelRunning("newer request");
        discardReadyIfStale();

        return requestedGeneration;
    }

    public synchronized void update() {
        if (disposed || !asyncPackRequested || asyncPackRunning) return;

        long now = System.currentTimeMillis();
        if (now - lastRequestMs < PACK_DEBOUNCE_MS) return;

        launchAsyncPack();
    }

    public synchronized RepackArtifact pollReadyAsyncPack() {
        if (!asyncPackReady || readyArtifact == null) return null;

        RepackArtifact artifact = readyArtifact;
        asyncPackReady = false;
        readyArtifact = null;

        if (artifact.generation() != requestedGeneration) {
            artifact.discard();
            return null;
        }

        return artifact;
    }

    public synchronized boolean hasQueuedOrRunningFor(String sceneTag) {
        if (sceneTag == null || sceneTag.isBlank()) {
            return asyncPackRequested || asyncPackRunning || asyncPackReady;
        }

        return (asyncPackRequested && sceneTag.equals(requestedSceneTag))
                || (asyncPackRunning && sceneTag.equals(runningSceneTag))
                || (readyArtifact != null && sceneTag.equals(readyArtifact.sceneTag()));
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

        if (readyArtifact != null) {
            readyArtifact.discard();
            readyArtifact = null;
        }

        asyncPackReady = false;

        executor.shutdownNow();
    }

    private void launchAsyncPack() {
        final String sceneTag = requestedSceneTag;
        final long generation = requestedGeneration;
        final RepackReason reason = requestedReason;

        asyncPackRequested = false;
        asyncPackRunning = true;
        runningSceneTag = sceneTag;
        runningGeneration = generation;

        runningFuture = executor.submit(() -> {
            RepackArtifact artifact = null;

            try {
                log("Async pack started scene=" + sceneTag + " gen=" + generation + " reason=" + reason);

                artifact = packRunner.pack(sceneTag, generation, reason);

                if (Thread.currentThread().isInterrupted()) {
                    discardArtifact(artifact);
                    return;
                }

                synchronized (this) {
                    if (disposed || generation != requestedGeneration) {
                        discardArtifact(artifact);
                        return;
                    }

                    readyArtifact = artifact;
                    asyncPackReady = true;
                }

                log("Async pack ready scene=" + sceneTag + " gen=" + generation);

            } catch (Exception ex) {
                discardArtifact(artifact);
                if (!Thread.currentThread().isInterrupted()) {
                    error("Async pack failed scene=" + sceneTag + " gen=" + generation, ex);
                }
            } finally {
                synchronized (this) {
                    if (runningGeneration == generation) {
                        asyncPackRunning = false;
                        runningSceneTag = null;
                        runningFuture = null;
                    }
                }
            }
        });
    }

    private void cancelRunning(String reason) {
        if (runningFuture != null && !runningFuture.isDone()) {
            log("Cancelling async pack scene=" + runningSceneTag
                    + " gen=" + runningGeneration
                    + " reason=" + reason);
            runningFuture.cancel(true);
        }

        asyncPackRunning = false;
        runningSceneTag = null;
        runningFuture = null;
    }

    private void discardReadyIfStale() {
        if (readyArtifact != null && readyArtifact.generation() != requestedGeneration) {
            readyArtifact.discard();
            readyArtifact = null;
            asyncPackReady = false;
        }
    }

    private static void deleteQuietly(FileHandle file) {
        if (file == null) return;
        try {
            if (file.exists()) file.deleteDirectory();
        } catch (Exception ignored) {
        }
    }

    private static void discardArtifact(RepackArtifact artifact) {
        if (artifact != null) artifact.discard();
    }

    private static void log(String msg) {
        Gdx.app.log(TAG, msg);
    }

    private static void error(String msg, Exception ex) {
        Gdx.app.error(TAG, msg, ex);
    }
}
