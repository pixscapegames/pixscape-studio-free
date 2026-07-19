package games.pixscape.studio.ui.main;

import games.pixscape.runtime.spatial.SpatialConstraintInvariantException;
import games.pixscape.runtime.spatial.SpatialTileOrderInvariantException;
import games.pixscape.runtime.spatial.SpatialTileSyncInvariantException;
import org.junit.Assert;
import org.junit.Test;

public class SpatialPreviewInvariantBoundaryTest {
    @Test
    public void invariantFailureBlocksSameSceneAndSurfacesExactDiagnosticOnce() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        FailingFrame frame = new FailingFrame();
        RecordingListener listener = new RecordingListener();

        Assert.assertFalse(boundary.process("scene-a", frame, listener));
        Assert.assertFalse(boundary.process("scene-a", frame, listener));

        Assert.assertTrue(boundary.isBlocked());
        Assert.assertEquals(1, frame.calls);
        Assert.assertEquals(1, listener.calls);
        Assert.assertEquals(2, ((SpatialConstraintInvariantException) listener.failure).unresolvedConstraintCount());
        Assert.assertEquals("exact conflict", listener.failure.getMessage());
    }

    @Test
    public void activatingAnotherSceneClearsThePreviewBlock() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        FailingFrame failing = new FailingFrame();
        RecordingListener listener = new RecordingListener();
        boundary.process("scene-a", failing, listener);

        CountingFrame valid = new CountingFrame();
        Assert.assertTrue(boundary.process("scene-b", valid, listener));

        Assert.assertFalse(boundary.isBlocked());
        Assert.assertEquals(1, valid.calls);
        Assert.assertEquals(1, listener.calls);
    }

    @Test
    public void staticTileOrderFailureBlocksPreviewWithExactDiagnostic() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        RecordingListener listener = new RecordingListener();

        Assert.assertFalse(boundary.process("cycle", () -> {
            throw new SpatialTileOrderInvariantException("exact static cycle");
        }, listener));

        Assert.assertTrue(boundary.isBlocked());
        Assert.assertEquals("exact static cycle", listener.failure.getMessage());
    }

    @Test
    public void tiledSyncFailureIsRecoverableAtPreviewBoundary() {
        SpatialPreviewInvariantBoundary boundary = new SpatialPreviewInvariantBoundary();
        RecordingListener listener = new RecordingListener();

        Assert.assertFalse(boundary.process("edit", () -> {
            throw new SpatialTileSyncInvariantException("cell=(4,19), canonicalRankState=missing");
        }, listener));

        Assert.assertTrue(boundary.isBlocked());
        Assert.assertEquals(1, listener.calls);
        Assert.assertTrue(listener.failure.getMessage().contains("cell=(4,19)"));
    }

    private static final class FailingFrame implements SpatialPreviewInvariantBoundary.FrameProcessor {
        int calls;
        @Override public void processFrame() {
            calls++;
            throw new SpatialConstraintInvariantException(2, "exact conflict");
        }
    }

    private static final class CountingFrame implements SpatialPreviewInvariantBoundary.FrameProcessor {
        int calls;
        @Override public void processFrame() { calls++; }
    }

    private static final class RecordingListener implements SpatialPreviewInvariantBoundary.FailureListener {
        int calls;
        RuntimeException failure;
        @Override public void onSpatialInvariantFailure(RuntimeException failure) {
            calls++;
            this.failure = failure;
        }
    }
}
