package games.pixscape.studio.ui.preview;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HtmlPreviewLauncherManifestTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void writeAssetsManifest_registersAllFilesAndDefersRuntimePayloads() throws Exception {
        Path assets = temp.newFolder("assets").toPath();
        Path[] staticPlayerRoots = {
                Path.of("src/main/resources/html-preview-template/assets"),
                Path.of("html-player/assets")
        };
        for (Path staticRoot : staticPlayerRoots) copyTree(staticRoot, assets);
        writeFile(assets, "player-shaders/loading.vert");

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

        for (Path staticRoot : staticPlayerRoots) {
            try (Stream<Path> staticFiles = Files.walk(staticRoot)) {
                staticFiles.filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().equals(".keep"))
                        .forEach(source -> {
                            String path = staticRoot.relativize(source)
                                    .toString().replace('\\', '/');
                            assertEquals("static HTML-player asset must preload: " + path,
                                    "1", preloadByPath.get(path));
                        });
            }
        }
        assertEquals("1", preloadByPath.get("player-shaders/loading.vert"));
        assertFontPagesArePreloaded(
                assets.resolve("font/default.fnt"), preloadByPath);

        for (String path : bootstrap) {
            assertEquals("bootstrap file must preload: " + path, "1", preloadByPath.get(path));
        }
        for (String path : deferred) {
            assertEquals("runtime payload must remain registered but deferred: " + path,
                    "0", preloadByPath.get(path));
        }

        for (String path : bootstrap) assertTrue(preloadByPath.containsKey(path));
        for (String path : deferred) assertTrue(preloadByPath.containsKey(path));
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

    private static void copyTree(Path sourceRoot, Path targetRoot) throws Exception {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                Path target = targetRoot.resolve(sourceRoot.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else if (!source.getFileName().toString().equals(".keep")) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void assertFontPagesArePreloaded(
            Path descriptor, Map<String, String> preloadByPath) throws Exception {
        Pattern pagePattern = Pattern.compile("(?m)^page\\s+id=\\d+\\s+file=\"([^\"]+)\"");
        Matcher pages = pagePattern.matcher(Files.readString(descriptor, StandardCharsets.UTF_8));
        int pageCount = 0;
        while (pages.find()) {
            pageCount++;
            String pagePath = "font/" + pages.group(1);
            assertTrue("font page must exist: " + pagePath,
                    Files.isRegularFile(descriptor.getParent().resolve(pages.group(1))));
            assertEquals("font page must preload: " + pagePath,
                    "1", preloadByPath.get(pagePath));
        }
        assertTrue("font descriptor must reference at least one page", pageCount > 0);
    }
}
