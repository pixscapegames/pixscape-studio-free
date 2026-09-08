package games.pixscape.studio.ui.preview;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HtmlPreviewPipelineTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void previewState_containsOnlyManifestAndNeverCopiesRuntimeOrPlayer() throws Exception {
        Path studio = temp.newFolder("studio").toPath();
        Path runtime = temp.newFolder("runtime").toPath();
        write(runtime, "project.json");
        write(runtime, "scenes/demo.json");
        write(runtime, "effects/fire.p");
        write(runtime, "gameobjects/enemy.gameobject");

        Path first = HtmlPreviewLauncher.preparePreviewState(studio, runtime);
        Path second = HtmlPreviewLauncher.preparePreviewState(studio, runtime);
        assertEquals(first, second);
        assertTrue(Files.isRegularFile(first));
        try (Stream<Path> files = Files.walk(studio.resolve(".pixscape/preview/html"))) {
            assertEquals(List.of(first), files.filter(Files::isRegularFile).toList());
        }

        List<String> manifest = Files.readAllLines(first, StandardCharsets.UTF_8);
        assertTrue(manifest.contains(
                "d:pixscape-project:pixscape-project:0:text/plain:1"));
        assertTrue(manifestLine(manifest, "pixscape-project/project.json").endsWith(":1"));
        assertTrue(manifestLine(manifest, "pixscape-project/scenes/demo.json").endsWith(":0"));
        assertTrue(manifestLine(manifest, "pixscape-project/effects/fire.p").endsWith(":0"));
        assertTrue(manifestLine(manifest, "pixscape-project/gameobjects/enemy.gameobject")
                .endsWith(":0"));
        assertFalse(Files.exists(first.getParent().resolve("pixscape-project")));
        assertFalse(Files.exists(studio.resolve(".pixscape/preview/html/htmlplayer")));
        assertFalse(Files.exists(first.resolveSibling("assets.txt.tmp")));
    }

    @Test
    public void productionTemplate_hasNoDevelopmentOrLogoArtifacts() throws Exception {
        Path template = Path.of("src/main/resources/html-preview-template");
        assertFalse(Files.exists(template.resolve("WEB-INF")));
        assertTrue(Files.isRegularFile(template.resolve("htmlplayer/gwt/chrome/chrome.css")));
        assertFalse(Files.exists(template.resolve("htmlplayer/gwt/chrome/chrome_rtl.css")));
        assertFalse(Files.exists(template.resolve("htmlplayer/gwt/chrome/images/ie6")));
        assertFalse(Files.exists(template.resolve("htmlplayer/htmlplayer.devmode.js")));
        assertFalse(Files.exists(template.resolve("htmlplayer/compilation-mappings.txt")));
        assertFalse(Files.exists(template.resolve("htmlplayer/logo.png")));
        String index = Files.readString(template.resolve("index.html"), StandardCharsets.UTF_8);
        assertFalse(index.toLowerCase().contains("libgdx.png"));
        assertFalse(index.toLowerCase().contains("logo.png"));
    }

    private static void write(Path root, String relative) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x", StandardCharsets.UTF_8);
    }

    private static String manifestLine(List<String> lines, String path) {
        return lines.stream().filter(line -> line.startsWith(assetTypePrefix(path) + path + ":"))
                .findFirst().orElseThrow(() -> new AssertionError("Missing manifest path: " + path));
    }

    private static String assetTypePrefix(String path) {
        return path.endsWith(".json") || path.endsWith(".gameobject")
                || path.endsWith(".p") ? "t:" : "b:";
    }
}
