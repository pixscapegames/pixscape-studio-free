package games.pixscape.studio.ui.preview;

import org.junit.Test;

import static org.junit.Assert.*;

public class PreviewDesktopApplicationTest {
    @Test
    public void separatePreviewIsUncappedWithoutDiagnosticProperties() throws Exception {
        var options = new PreviewDesktopApplication.Arguments(
                java.nio.file.Path.of("fixture"), "scene", 1280, 720, 0, false, "session");
        var configuration = PreviewDesktopApplication.configuration(options);
        var vsync = com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowConfiguration.class
                .getDeclaredField("vSyncEnabled");
        var fps = com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration.class
                .getDeclaredField("foregroundFPS");
        vsync.setAccessible(true);
        fps.setAccessible(true);
        assertFalse(vsync.getBoolean(configuration));
        assertEquals(0, fps.getInt(configuration));
    }

    @Test
    public void argumentsPreserveAPathWithSpacesAndRendererSelection() {
        PreviewDesktopApplication.Arguments arguments = PreviewDesktopApplication.Arguments.parse(new String[]{
                "--user-root", "C:\\Preview projects\\fixture",
                "--scene", "Scene one",
                "--width", "1280",
                "--height", "720",
                "--gl-samples", "4",
                "--forward-lighting", "true",
                "--session", "session-a"
        });

        assertEquals("Scene one", arguments.sceneName());
        assertEquals(1280, arguments.width());
        assertEquals(720, arguments.height());
        assertEquals(4, arguments.glSamples());
        assertTrue(arguments.forwardLighting());
        assertEquals("session-a", arguments.sessionId());
        assertTrue(arguments.userRoot().toString().contains("Preview projects"));
    }

    @Test
    public void argumentsRejectIncompleteAndInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> PreviewDesktopApplication.Arguments.parse(new String[]{"--user-root"}));
        assertThrows(IllegalArgumentException.class,
                () -> PreviewDesktopApplication.Arguments.parse(new String[]{
                        "--user-root", "C:\\fixture", "--scene", "scene", "--width", "0",
                        "--height", "10", "--gl-samples", "0", "--forward-lighting", "false",
                        "--session", "session"
                }));
    }
}
