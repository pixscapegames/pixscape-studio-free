package games.pixscape.studio.ui.preview;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Owned by the uncapped Desktop Preview and used only on its GL thread.
 */
class PreviewGpuFrames {
    private final long[] fences = new long[2];
    private int next;

    boolean tryBeginFrame() {
        long oldest = fences[next];
        if (oldest == 0) return true;
        switch (waitForFence(oldest)) {
            case GL_ALREADY_SIGNALED, GL_CONDITION_SATISFIED -> {
                deleteFence(oldest);
                fences[next] = 0;
                return true;
            }
            case GL_TIMEOUT_EXPIRED -> {
                // Leave this slot occupied. Let LWJGL process input and close requests.
                return false;
            }
            default -> throw new SyncFailure("Desktop Preview could not wait for the GPU frame fence.");
        }
    }

    void endFrame() {
        long fence = insertFence();
        if (fence == 0) throw new SyncFailure("Desktop Preview could not create a GPU frame fence.");
        fences[next] = fence;
        next = (next + 1) % fences.length;
    }

    void dispose() {
        for (int i = 0; i < fences.length; i++) {
            if (fences[i] != 0) {
                deleteFence(fences[i]);
                fences[i] = 0;
            }
        }
    }

    long insertFence() {
        return glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    int waitForFence(long fence) {
        // Returns immediately if complete. This bounds one wait, not the frame rate.
        return glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 8_000_000L);
    }

    void deleteFence(long fence) {
        glDeleteSync(fence);
    }

    static final class SyncFailure extends IllegalStateException {
        SyncFailure(String message) {
            super(message);
        }
    }
}
