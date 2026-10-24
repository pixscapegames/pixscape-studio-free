package games.pixscape.studio.ui.preview;

import org.junit.Test;

import static org.junit.Assert.*;

public class PreviewProcessProtocolTest {
    @Test
    public void sessionMessagesAreAcceptedOnlyForTheirExactSession() {
        String session = "session-a";

        assertTrue(PreviewProcessProtocol.isLoadingLine("PIXSCAPE_PREVIEW LOADING session-a", session));
        assertTrue(PreviewProcessProtocol.isReadyLine("PIXSCAPE_PREVIEW READY session-a", session));
        assertFalse(PreviewProcessProtocol.isReadyLine("PIXSCAPE_PREVIEW READY session-b", session));
        assertEquals("load refused", PreviewProcessProtocol.errorMessage(
                "PIXSCAPE_PREVIEW ERROR session-a load refused", session));
        assertNull(PreviewProcessProtocol.errorMessage("PIXSCAPE_PREVIEW ERROR session-b load refused", session));
    }

    @Test
    public void closeCommandCannotTargetAnotherPreviewSession() {
        assertTrue(PreviewProcessProtocol.isCloseCommand("CLOSE session-a", "session-a"));
        assertFalse(PreviewProcessProtocol.isCloseCommand("CLOSE session-b", "session-a"));
        assertFalse(PreviewProcessProtocol.isCloseCommand("CLOSE session-a extra", "session-a"));
    }
}
