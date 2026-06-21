package games.pixscape.studio.ui.main;

import org.junit.Test;

import static org.junit.Assert.*;

public class PreviewLaunchSupportTest {

    @Test
    public void launchKeepingEditorAlive_previewLaunchOperationalFailure_returnsFalseWithPopupAndNoFalseSuccess() {
        final int[] saveCalls = {0};
        final int[] launchCalls = {0};
        final int[] popupCalls = {0};
        final int[] loggerCalls = {0};
        final String[] popupMessage = {null};

        boolean success = PreviewLaunchSupport.launchKeepingEditorAlive(
                true,
                () -> saveCalls[0]++,
                () -> {
                    launchCalls[0]++;
                    throw new RuntimeException("Preview window creation failed");
                },
                (message, ex) -> loggerCalls[0]++,
                message -> {
                    popupCalls[0]++;
                    popupMessage[0] = message;
                }
        );

        assertFalse(success);
        assertEquals(1, saveCalls[0]);
        assertEquals(1, launchCalls[0]);
        assertEquals(1, popupCalls[0]);
        assertEquals(1, loggerCalls[0]);
        assertEquals("Preview window creation failed", popupMessage[0]);
    }

    @Test
    public void launchKeepingEditorAlive_saveOperationalFailure_keepsEditorAliveAndDoesNotLaunchPreview() {
        final int[] launchCalls = {0};
        final int[] popupCalls = {0};

        boolean success = PreviewLaunchSupport.launchKeepingEditorAlive(
                true,
                () -> {
                    throw new RuntimeException("Project save failed");
                },
                () -> launchCalls[0]++,
                (message, ex) -> {
                },
                message -> popupCalls[0]++
        );

        assertFalse(success);
        assertEquals(0, launchCalls[0]);
        assertEquals(1, popupCalls[0]);
    }

    @Test
    public void launchKeepingEditorAlive_successPath_runsSaveAndLaunchWithoutPopup() {
        final int[] saveCalls = {0};
        final int[] launchCalls = {0};
        final int[] popupCalls = {0};

        boolean success = PreviewLaunchSupport.launchKeepingEditorAlive(
                true,
                () -> saveCalls[0]++,
                () -> launchCalls[0]++,
                (message, ex) -> fail("logger should not be called"),
                message -> popupCalls[0]++
        );

        assertTrue(success);
        assertEquals(1, saveCalls[0]);
        assertEquals(1, launchCalls[0]);
        assertEquals(0, popupCalls[0]);
    }

    @Test(expected = IllegalStateException.class)
    public void launchKeepingEditorAlive_internalInvariantFailure_isNotConvertedToPopup() {
        final int[] popupCalls = {0};

        try {
            PreviewLaunchSupport.launchKeepingEditorAlive(
                    false,
                    () -> {
                    },
                    () -> {
                        throw new IllegalStateException("internal invariant broken");
                    },
                    (message, ex) -> {
                    },
                    message -> popupCalls[0]++
            );
        } finally {
            assertEquals(0, popupCalls[0]);
        }
    }
}
