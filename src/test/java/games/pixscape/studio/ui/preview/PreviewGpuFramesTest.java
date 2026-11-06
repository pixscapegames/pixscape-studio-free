package games.pixscape.studio.ui.preview;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.lwjgl.opengl.GL32C.*;

public class PreviewGpuFramesTest {
    private static final class FakeGpu extends PreviewGpuFrames {
        long lastFence;
        long waited;
        int result = GL_TIMEOUT_EXPIRED;
        final List<Long> deleted = new ArrayList<>();

        @Override long insertFence() { return ++lastFence; }
        @Override int waitForFence(long fence) { waited = fence; return result; }
        @Override void deleteFence(long fence) { deleted.add(fence); }
    }

    @Test public void thirdFrameWaitsForOldestAndShutdownReleasesOutstandingFences() {
        FakeGpu gpu = new FakeGpu();
        assertTrue(gpu.tryBeginFrame());
        gpu.endFrame();
        assertTrue(gpu.tryBeginFrame());
        gpu.endFrame();
        assertEquals(0, gpu.waited);

        assertFalse(gpu.tryBeginFrame());
        assertFalse(gpu.tryBeginFrame());
        assertEquals(1, gpu.waited);
        assertTrue(gpu.deleted.isEmpty());

        gpu.result = GL_CONDITION_SATISFIED;
        assertTrue(gpu.tryBeginFrame());
        gpu.endFrame();
        gpu.result = GL_WAIT_FAILED;
        assertThrows(PreviewGpuFrames.SyncFailure.class, gpu::tryBeginFrame);
        assertEquals(2, gpu.waited);
        gpu.dispose();
        gpu.dispose();
        assertEquals(List.of(1L, 3L, 2L), gpu.deleted);
    }
}
