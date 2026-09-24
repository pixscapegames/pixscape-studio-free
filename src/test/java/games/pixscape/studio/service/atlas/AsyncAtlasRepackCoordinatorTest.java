package games.pixscape.studio.service.atlas;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class AsyncAtlasRepackCoordinatorTest {
    @Test public void requestDebouncesAndRunsOnlyThroughInjectedExecutor() throws Exception {
        ControlledAtlasExecutor executor = new ControlledAtlasExecutor();
        long[] now = {0};
        AtomicInteger calls = new AtomicInteger();
        try (var close = closeable(new AsyncAtlasRepackCoordinator<TestPayload>((key, gen, reason) -> {
            calls.incrementAndGet();
            assertEquals("controlled-atlas-worker", Thread.currentThread().getName());
            return new TestPayload();
        }, executor, () -> now[0], 400))) {
            var coordinator = close.coordinator;
            coordinator.requestAsyncPack("project::profile", null);
            coordinator.update();
            assertEquals(0, executor.queued());
            now[0] = 400;
            coordinator.update();
            assertEquals(0, calls.get());
            executor.runNext();
            assertEquals(1, calls.get());
            var prepared = coordinator.pollReadyAsyncPack();
            assertNotNull(prepared);
            prepared.discard();
        }
    }
    @Test public void newerGenerationDiscardsReadyAndLateNonCooperativeResults() throws Exception {
        ControlledAtlasExecutor executor = new ControlledAtlasExecutor();
        AtomicInteger discarded = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        var coordinator = new AsyncAtlasRepackCoordinator<CountedPayload>((key, gen, reason) -> {
            if (gen == 1) {
                started.countDown();
                boolean released = false;
                while (!released) {
                    try { finish.await(); released = true; }
                    catch (InterruptedException ignored) { /* Simulate a non-cooperative packer. */ }
                }
                Thread.interrupted();
            }
            return new CountedPayload(discarded);
        }, executor, () -> 0L, 0);
        coordinator.requestAsyncPack("project::profile", null);
        coordinator.update();
        Thread old = executor.startNext();
        assertTrue(started.await(10, TimeUnit.SECONDS));
        coordinator.requestAsyncPack("project::profile", null);
        coordinator.update();
        finish.countDown();
        ControlledAtlasExecutor.join(old);
        assertEquals(1, discarded.get());
        assertNull(coordinator.pollReadyAsyncPack());
        executor.runNext();
        coordinator.requestAsyncPack("project::profile", null);
        assertEquals(2, discarded.get()); // Ready generation 2 is also discarded.
        coordinator.update();
        executor.runNext();
        var newest = coordinator.pollReadyAsyncPack();
        assertEquals(3, newest.generation());
        newest.discard();
        coordinator.dispose();
    }
    @Test public void backgroundFailureIsDeliveredExactlyOnceAndStaleFailureIsIgnored() throws Exception {
        ControlledAtlasExecutor executor = new ControlledAtlasExecutor();
        var coordinator = new AsyncAtlasRepackCoordinator<TestPayload>((key, gen, reason) -> {
            throw new IllegalStateException("pack failed");
        }, executor, () -> 0L, 0);
        coordinator.requestAsyncPack("profile", null);
        coordinator.update();
        executor.runNext();
        assertEquals("pack failed", coordinator.pollFailure().cause().getMessage());
        assertNull(coordinator.pollFailure());
        assertNull(coordinator.pollReadyAsyncPack());
        coordinator.requestAsyncPack("profile", null);
        coordinator.update();
        executor.runNext();
        coordinator.requestAsyncPack("profile", null);
        assertNull(coordinator.pollFailure());
        coordinator.dispose();
    }
    @Test public void disposeClosesUnclaimedPreparedPayload() throws Exception {
        ControlledAtlasExecutor executor = new ControlledAtlasExecutor();
        AtomicInteger closed = new AtomicInteger();
        var coordinator = new AsyncAtlasRepackCoordinator<CountedPayload>(
                (key, gen, reason) -> new CountedPayload(closed), executor, () -> 0L, 0);
        coordinator.requestAsyncPack("profile", null);
        coordinator.update();
        executor.runNext();
        coordinator.dispose();
        assertEquals(1, closed.get());
    }
    @Test public void independentlyTypedCoordinatorsKeepPayloadTypesSeparate() throws Exception {
        ControlledAtlasExecutor sceneExecutor = new ControlledAtlasExecutor();
        ControlledAtlasExecutor hudExecutor = new ControlledAtlasExecutor();
        var scene = new AsyncAtlasRepackCoordinator<ScenePayload>(
                (key, gen, reason) -> new ScenePayload(), sceneExecutor, () -> 0L, 0);
        var hud = new AsyncAtlasRepackCoordinator<HudPayload>(
                (key, gen, reason) -> new HudPayload(), hudExecutor, () -> 0L, 0);
        scene.requestAsyncPack("scene", null);
        hud.requestAsyncPack("profile", null);
        scene.update();
        hud.update();
        sceneExecutor.runNext();
        hudExecutor.runNext();
        try (ScenePayload scenePayload = scene.pollReadyAsyncPack().takePayload();
             HudPayload hudPayload = hud.pollReadyAsyncPack().takePayload()) {
            assertNotNull(scenePayload);
            assertNotNull(hudPayload);
        } finally {
            scene.dispose();
            hud.dispose();
        }
    }
    private static Closing<TestPayload> closeable(AsyncAtlasRepackCoordinator<TestPayload> coordinator) {
        return new Closing<>(coordinator);
    }
    private record Closing<T extends AutoCloseable>(AsyncAtlasRepackCoordinator<T> coordinator) implements AutoCloseable {
        @Override public void close() { coordinator.dispose(); }
    }
    private static class TestPayload implements AutoCloseable {
        @Override public void close() {}
    }
    private static final class CountedPayload extends TestPayload {
        private final AtomicInteger closed;
        private CountedPayload(AtomicInteger closed) { this.closed = closed; }
        @Override public void close() { closed.incrementAndGet(); }
    }
    private static final class ScenePayload extends TestPayload {}
    private static final class HudPayload extends TestPayload {}
}
