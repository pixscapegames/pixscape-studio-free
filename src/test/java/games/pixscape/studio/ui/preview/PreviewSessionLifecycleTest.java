package games.pixscape.studio.ui.preview;

import org.junit.Test;

import static org.junit.Assert.*;

public class PreviewSessionLifecycleTest {
    @Test
    public void successfulChildMovesFromStartupThroughLoadingToReady() {
        PreviewSessionLifecycle lifecycle = new PreviewSessionLifecycle();

        assertEquals(PreviewSessionLifecycle.State.STARTING, lifecycle.state());
        assertTrue(lifecycle.loading());
        assertTrue(lifecycle.ready());
        assertEquals(PreviewSessionLifecycle.State.READY, lifecycle.state());
        assertFalse(lifecycle.ready());
    }

    @Test
    public void failedOrClosingChildCannotPublishLateReady() {
        PreviewSessionLifecycle failed = new PreviewSessionLifecycle();
        assertTrue(failed.failed());
        assertFalse(failed.ready());
        assertEquals(PreviewSessionLifecycle.State.FAILED, failed.state());

        PreviewSessionLifecycle closing = new PreviewSessionLifecycle();
        assertTrue(closing.loading());
        assertTrue(closing.closing());
        assertFalse(closing.ready());
        assertFalse(closing.failed());
        assertEquals(PreviewSessionLifecycle.State.CLOSING, closing.state());
    }

    @Test
    public void doubleCloseIsIgnoredAndAClosedSessionDoesNotReturnToStartup() {
        PreviewSessionLifecycle lifecycle = new PreviewSessionLifecycle();

        assertTrue(lifecycle.closing());
        assertFalse(lifecycle.closing());
        assertFalse(lifecycle.loading());
        assertEquals(PreviewSessionLifecycle.State.CLOSING, lifecycle.state());
    }
}
