package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.sun.net.httpserver.HttpServer;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.studio.helper.RuntimeShaderResources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.*;

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
    public void routedPreviewPreloadsAndServesCompleteRuntimeShaderCatalog() throws Exception {
        Path studio = temp.newFolder("shader-studio").toPath();
        Path runtime = temp.newFolder("shader-runtime").toPath();
        write(runtime, "project.json");
        // A project override must neither hide nor replace the built-in catalog.
        Files.createDirectories(runtime.resolve("shaders/examples"));
        Files.writeString(runtime.resolve("shaders/examples/catalog.json"), "[]");
        Path manifest = HtmlPreviewLauncher.preparePreviewState(studio, runtime);
        Map<String, String[]> registered = new HashMap<>();
        for (String line : Files.readAllLines(manifest)) {
            String[] fields = line.split(":");
            assertNull("Duplicate manifest path: " + fields[1], registered.put(fields[1], fields));
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HtmlPreviewLauncher.PreviewHttpHandler(runtime, manifest));
        server.start();
        try (HttpClient client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/assets/";
            Map<String, String> sources = new HashMap<>();
            for (RuntimeShaderResources.Resource resource : RuntimeShaderResources.bundledResources()) {
                if (resource.directory()) continue;
                String path = resource.path();
                String[] entry = registered.get(path);
                assertNotNull("Not registered: " + path, entry);
                assertEquals("Must preload: " + path, "1", entry[5]);
                assertEquals("URL case/path: " + path, path, entry[2]);
                HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(base + path)).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertEquals("Not served: " + path, 200, response.statusCode());
                try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
                    assertNotNull(input);
                    assertArrayEquals("Wrong dependency bytes: " + path, input.readAllBytes(), response.body());
                }
                assertEquals(response.body().length, Long.parseLong(entry[3]));
                sources.put(path, new String(response.body(), StandardCharsets.UTF_8));
            }
            JsonValue catalog = new JsonReader().parse(sources.get("shaders/examples/catalog.json"));
            assertTrue(catalog.size > 0);
            for (JsonValue item : catalog) {
                String root = "shaders/examples/" + item.getString("path") + "/";
                if (item.getString("kind").equals("MATERIAL")) {
                    assertNotNull(root, sources.get(root + "shader.json"));
                    assertNotNull(root, sources.get(root + "surface.glsl"));
                } else {
                    for (String variant : List.of("desktop-gl30", "es3-webgl2")) {
                        assertNotNull(root, sources.get(root + variant + "/" + item.getString("name") + ".vert"));
                        assertNotNull(root, sources.get(root + variant + "/" + item.getString("name") + ".frag"));
                    }
                }
            }
            assertNotNull(sources.get("shaders/examples/material/lit-dissolve/mask.glsl"));
            assertNotNull(sources.get("shaders/includes/pixscape_common.glsl"));
            assertNotNull(sources.get("shaders/includes/pixscape_entity_params.glsl"));
            HttpResponse<String> projectCatalog = client.send(HttpRequest.newBuilder(
                    URI.create(base + "pixscape-project/shaders/examples/catalog.json")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals("[]", projectCatalog.body());
        } finally {
            server.stop(0);
        }
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
        assertTrue(Files.isRegularFile(template.resolve("assets")
                .resolve(HudBuiltInLabelStyle.FONT_DESCRIPTOR)));
        String index = Files.readString(template.resolve("index.html"), StandardCharsets.UTF_8);
        assertFalse(index.toLowerCase().contains("libgdx.png"));
        assertFalse(index.toLowerCase().contains("logo.png"));
    }

    @Test
    public void packagedPlayerMatchesExplicitTemplate() throws Exception {
        Path generated=Path.of("src/main/resources/html-preview-template/htmlplayer");
        Path packaged=Path.of("build/resources/main/html-preview-template/htmlplayer");
        String bootstrap=Files.readString(generated.resolve("htmlplayer.nocache.js"));
        assertEquals(bootstrap,Files.readString(packaged.resolve("htmlplayer.nocache.js")));
        java.util.regex.Matcher hashes=java.util.regex.Pattern.compile("[A-F0-9]{32}").matcher(bootstrap);
        java.util.Set<String> names=new java.util.HashSet<>();
        while(hashes.find())names.add(hashes.group()+".cache.js");
        assertFalse(names.isEmpty());
        for(String name:names)assertArrayEquals(Files.readAllBytes(generated.resolve(name)),Files.readAllBytes(packaged.resolve(name)));
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
