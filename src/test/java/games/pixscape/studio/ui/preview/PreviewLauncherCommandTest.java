package games.pixscape.studio.ui.preview;

import games.pixscape.studio.ui.main.Resolution;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class PreviewLauncherCommandTest {
    @Test
    public void childCommandUsesTheCurrentJvmClasspathAndKeepsArgumentsAtomic() {
        String previous = System.getProperty("pixscape.poc.forwardLighting");
        System.setProperty("pixscape.poc.forwardLighting", "true");
        try {
            List<String> command = PreviewLauncher.commandFor("session-a",
                    Path.of("C:\\Preview projects\\fixture"), "Scene one", new Resolution(1280, 720), 4, true,
                    Path.of("C:\\Preview projects\\fixture\\.pixscape\\preview-diagnostics\\child.log"));

            assertEquals(PreviewLauncher.javaExecutable().toString(), command.get(0));
            assertTrue(command.contains("-Dpixscape.poc.forwardLighting=true"));
            assertTrue(command.contains("-Dpixscape.preview.session=session-a"));
            assertTrue(command.stream().anyMatch(argument -> argument.startsWith(
                    "-Dpixscape.preview.diagnosticLog=C:\\Preview projects\\fixture\\.pixscape")));
            assertEquals(System.getProperty("java.class.path"), command.get(command.indexOf("-cp") + 1));
            assertEquals(PreviewDesktopApplication.class.getName(), command.get(command.indexOf("-cp") + 2));
            assertEquals("C:\\Preview projects\\fixture", command.get(command.indexOf("--user-root") + 1));
            assertEquals("Scene one", command.get(command.indexOf("--scene") + 1));
        } finally {
            if (previous == null) System.clearProperty("pixscape.poc.forwardLighting");
            else System.setProperty("pixscape.poc.forwardLighting", previous);
        }
    }
}
