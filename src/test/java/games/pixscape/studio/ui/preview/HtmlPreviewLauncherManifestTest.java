package games.pixscape.studio.ui.preview;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HtmlPreviewLauncherManifestTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void writeAssetsManifest_registersAllFilesAndDefersRuntimePayloads() throws Exception {
        Path assets = temp.newFolder("assets").toPath();
        String[] bootstrap = {
                "pixscape-project/project.json",
                "pixscape-project/animations.json",
                "pixscape-project/tiled-animations.json",
                "pixscape-project/tileset-profiles.json",
                "pixscape-project/shaders/es3.vert",
                "pixscape-project/shaders/desktop.vert"
        };
        String[] deferred = {
                "pixscape-project/scenes/a.json",
                "pixscape-project/scenes/b.json",
                "pixscape-project/atlases/a.atlas",
                "pixscape-project/atlases/a.png",
                "pixscape-project/atlases/b.atlas",
                "pixscape-project/atlases/b.png",
                "pixscape-project/effects/fire.p",
                "pixscape-project/audio/music.ogg",
                "pixscape-project/prefabs/enemy.pixfragment.json"
        };

        for (String path : bootstrap) writeFile(assets, path);
        for (String path : deferred) writeFile(assets, path);

        HtmlPreviewLauncher.writeAssetsManifest(assets);

        List<String> lines = Files.readAllLines(assets.resolve("assets.txt"), StandardCharsets.UTF_8);
        Map<String, String> preloadByPath = preloadByPath(lines);

        for (String path : bootstrap) {
            assertEquals("bootstrap file must preload: " + path, "1", preloadByPath.get(path));
        }
        for (String path : deferred) {
            assertEquals("runtime payload must remain registered but deferred: " + path,
                    "0", preloadByPath.get(path));
        }

        assertEquals(bootstrap.length + deferred.length,
                preloadByPath.entrySet().stream()
                        .filter(entry -> !entry.getKey().endsWith("/"))
                        .filter(entry -> !isDirectoryLine(lines, entry.getKey()))
                        .count());
        assertTrue(lines.stream().filter(line -> line.startsWith("d:")).allMatch(line -> line.endsWith(":1")));
    }

    private static void writeFile(Path assets, String relative) throws Exception {
        Path file = assets.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x", StandardCharsets.UTF_8);
    }

    private static Map<String, String> preloadByPath(List<String> lines) {
        Map<String, String> out = new HashMap<>();
        for (String line : lines) {
            String[] fields = line.split(":");
            out.put(fields[1], fields[5]);
        }
        return out;
    }

    private static boolean isDirectoryLine(List<String> lines, String path) {
        for (String line : lines) {
            if (line.startsWith("d:" + path + ":")) return true;
        }
        return false;
    }
}
