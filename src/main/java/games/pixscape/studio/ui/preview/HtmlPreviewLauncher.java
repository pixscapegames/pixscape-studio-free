package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExportPaths;
import games.pixscape.studio.exception.HtmlPreviewNotReadyException;
import games.pixscape.studio.io.StudioFs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class HtmlPreviewLauncher {

    private static final String MISSING_TEMPLATE_MESSAGE =
            "HTML preview template is missing. Rebuild it with buildHtmlPreviewTemplate.";
    private static final String TEMPLATE_RESOURCE = "html-preview-template/";
    private static final String TEMPLATE_ASSETS_RESOURCE = TEMPLATE_RESOURCE + "assets/";
    private static final String RUNTIME_ROUTE = "assets/" + PixscapeEngine.RUNTIME_DIR_NAME + "/";
    private static final String MANIFEST_ROUTE = "assets/assets.txt";
    private static final String STATIC_ASSET_ANCHOR = TEMPLATE_ASSETS_RESOURCE + "font/default.fnt";

    private static HttpServer activeServer;
    private static Runnable activeOnClosedCallback;

    private HtmlPreviewLauncher() {
    }

    public static synchronized void open(ProjectConfig cfg, Runnable onOpened, Runnable onClosed) {
        Objects.requireNonNull(cfg, "cfg");
        try {
            stop();
            Path studioProjectRoot = StudioFs.requireStudioProjectDir(cfg).file().toPath();
            Path runtimeRoot = getRequiredExportRoot(cfg).resolve(PixscapeEngine.RUNTIME_DIR_NAME);
            if (!Files.isDirectory(runtimeRoot)) {
                throw new IllegalStateException(
                        "HTML preview failed: missing runtime project directory: " + runtimeRoot);
            }

            Path manifest = preparePreviewState(studioProjectRoot, runtimeRoot);

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", new PreviewHttpHandler(runtimeRoot, manifest));
            server.start();
            activeServer = server;
            activeOnClosedCallback = onClosed;

            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            Gdx.app.log("HtmlPreviewLauncher", "HTML preview running at " + url);
            if (onOpened != null) onOpened.run();
            Gdx.net.openURI(url);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to start HTML preview: " + ex.getMessage(), ex);
        }
    }

    static Path preparePreviewState(Path studioProjectRoot, Path runtimeRoot) throws IOException {
        Path manifest = studioProjectRoot.resolve(".pixscape").resolve("preview")
                .resolve("html").resolve(MANIFEST_ROUTE);
        Files.createDirectories(manifest.getParent());
        writeAssetsManifest(manifest, runtimeRoot);
        return manifest;
    }

    private static Path getRequiredExportRoot(ProjectConfig cfg) {
        String exportRootPath = cfg.exportRootPathDir;
        if (exportRootPath == null || exportRootPath.isBlank()) {
            throw new IllegalStateException("HTML preview failed: export root is not configured.");
        }
        Path exportRoot = RuntimeExportPaths.userRootPath(Path.of(exportRootPath));
        if (!Files.isDirectory(exportRoot)) {
            throw new IllegalStateException(
                    "HTML preview failed: export root directory does not exist: " + exportRoot);
        }
        return exportRoot;
    }

    static void writeAssetsManifest(Path manifest, Path runtimeRoot) throws IOException {
        Map<String, ManifestEntry> entries = new HashMap<>();
        addStaticTemplateAssets(entries);
        entries.put(PixscapeEngine.RUNTIME_DIR_NAME,
                new ManifestEntry(PixscapeEngine.RUNTIME_DIR_NAME, 0L, true, true));
        addTree(entries, runtimeRoot, PixscapeEngine.RUNTIME_DIR_NAME, false);
        writeManifestAtomically(manifest, entries);
    }

    /** Test helper for a materialized assets tree. Preview launches use the routed overload above. */
    static void writeAssetsManifest(Path assetsRoot) throws IOException {
        Map<String, ManifestEntry> entries = new HashMap<>();
        addTree(entries, assetsRoot, "", false);
        entries.remove("assets.txt");
        writeManifestAtomically(assetsRoot.resolve("assets.txt"), entries);
    }

    private static void addStaticTemplateAssets(Map<String, ManifestEntry> entries) throws IOException {
        ClassLoader loader = HtmlPreviewLauncher.class.getClassLoader();
        URL anchor = loader.getResource(STATIC_ASSET_ANCHOR);
        if (anchor == null) {
            throw new HtmlPreviewNotReadyException(MISSING_TEMPLATE_MESSAGE);
        }

        if ("file".equalsIgnoreCase(anchor.getProtocol())) {
            URL root = loader.getResource(TEMPLATE_ASSETS_RESOURCE);
            if (root == null) throw new IOException("Missing HTML preview assets directory.");
            try {
                addTree(entries, Path.of(root.toURI()), "", true);
                return;
            } catch (Exception ex) {
                throw new IOException("Failed to enumerate HTML preview assets.", ex);
            }
        }

        if ("jar".equalsIgnoreCase(anchor.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) anchor.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> jarEntries = jar.entries();
                while (jarEntries.hasMoreElements()) {
                    JarEntry entry = jarEntries.nextElement();
                    if (!entry.getName().startsWith(TEMPLATE_ASSETS_RESOURCE)) continue;
                    String relative = entry.getName().substring(TEMPLATE_ASSETS_RESOURCE.length());
                    if (relative.isEmpty()) continue;
                    entries.put(relative, new ManifestEntry(relative, entry.getSize(), entry.isDirectory(), true));
                }
            }
            return;
        }

        throw new IOException("Unsupported HTML preview resource protocol: " + anchor.getProtocol());
    }

    private static void addTree(Map<String, ManifestEntry> entries, Path root,
                                String pathPrefix, boolean preload) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (relative.isEmpty()) continue;
                String manifestPath = pathPrefix.isEmpty() ? relative : pathPrefix + "/" + relative;
                boolean directory = Files.isDirectory(path);
                long size = directory ? 0L : Files.size(path);
                boolean eager = directory || preload || preloadAtBootstrap(manifestPath);
                entries.put(manifestPath,
                        new ManifestEntry(manifestPath, size, directory, eager));
            }
        }
    }

    private static void writeManifestAtomically(Path manifest,
                                                Map<String, ManifestEntry> entries) throws IOException {
        List<String> paths = new ArrayList<>(entries.keySet());
        Collections.sort(paths);
        StringBuilder body = new StringBuilder();
        for (String path : paths) {
            ManifestEntry entry = entries.get(path);
            if (body.length() > 0) body.append('\n');
            if (entry.directory) {
                body.append("d:").append(path).append(':').append(path)
                        .append(":0:text/plain:1");
            } else {
                body.append(assetType(path)).append(':').append(path).append(':').append(path)
                        .append(':').append(entry.size).append(':').append(mimeType(path)).append(':')
                        .append(entry.preload ? '1' : '0');
            }
        }

        Files.createDirectories(manifest.getParent());
        Path temporary = manifest.resolveSibling(manifest.getFileName() + ".tmp");
        Files.writeString(temporary, body, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, manifest, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean preloadAtBootstrap(String path) {
        String runtimePrefix = PixscapeEngine.RUNTIME_DIR_NAME + "/";
        if (!path.startsWith(runtimePrefix)) return true;
        String runtimePath = path.substring(runtimePrefix.length());
        if (runtimePath.equals("project.json") || runtimePath.equals("animations.json")
                || runtimePath.equals("tiled-animations.json")
                || runtimePath.equals("tileset-profiles.json")) {
            return true;
        }
        return runtimePath.startsWith("shaders/");
    }

    public static synchronized void stop() {
        HttpServer server = activeServer;
        Runnable callback = activeOnClosedCallback;
        activeServer = null;
        activeOnClosedCallback = null;
        if (server != null) {
            try {
                server.stop(0);
            } catch (Throwable failure) {
                logStopFailure("Failed to stop HTML preview server.", failure);
            }
        }
        if (callback != null) {
            try {
                callback.run();
            } catch (Throwable failure) {
                logStopFailure("HTML preview close callback failed.", failure);
            }
        }
    }

    private static void logStopFailure(String message, Throwable failure) {
        if (Gdx.app != null) {
            Gdx.app.error("HtmlPreviewLauncher", message, failure);
        } else {
            System.err.println("HtmlPreviewLauncher: " + message);
            if (failure != null) failure.printStackTrace(System.err);
        }
    }

    private static String assetType(String path) {
        String value = path.toLowerCase();
        if (value.endsWith(".png") || value.endsWith(".jpg")
                || value.endsWith(".jpeg") || value.endsWith(".webp")) return "i";
        if (value.endsWith(".mp3") || value.endsWith(".ogg")
                || value.endsWith(".wav")) return "a";
        if (value.endsWith(".json") || value.endsWith(".atlas")
                || value.endsWith(".frag") || value.endsWith(".vert")
                || value.endsWith(".xml") || value.endsWith(".txt")
                || value.endsWith(".glsl") || value.endsWith(".p")
                || value.endsWith(".fnt") || value.endsWith(GameObjectAsset.EXTENSION)
                || value.endsWith(".pixfragment")
                || value.endsWith(".pixfragment.json")) return "t";
        return "b";
    }

    private static String mimeType(String path) {
        String value = path.toLowerCase();
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".jpg") || value.endsWith(".jpeg")) return "image/jpeg";
        if (value.endsWith(".webp")) return "image/webp";
        if (value.endsWith(".gif")) return "image/gif";
        if (value.endsWith(".mp3")) return "audio/mpeg";
        if (value.endsWith(".ogg")) return "audio/ogg";
        if (value.endsWith(".wav")) return "audio/wav";
        if (value.endsWith(".json")) return "application/json";
        if (value.endsWith(".xml")) return "application/xml";
        if (value.endsWith(".html")) return "text/html";
        if (value.endsWith(".js")) return "application/javascript";
        if (value.endsWith(".css")) return "text/css";
        if (value.endsWith(".wasm")) return "application/wasm";
        if (value.endsWith(".atlas") || value.endsWith(".frag")
                || value.endsWith(".vert") || value.endsWith(".glsl")
                || value.endsWith(".txt") || value.endsWith(".p")
                || value.endsWith(".fnt") || value.endsWith(GameObjectAsset.EXTENSION)
                || value.endsWith(".pixfragment")
                || value.endsWith(".pixfragment.json")) return "text/plain";
        return "application/octet-stream";
    }

    static final class PreviewHttpHandler implements HttpHandler {
        private final Path runtimeRoot;
        private final Path manifest;

        PreviewHttpHandler(Path runtimeRoot, Path manifest) {
            this.runtimeRoot = runtimeRoot.toAbsolutePath().normalize();
            this.manifest = manifest.toAbsolutePath().normalize();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                sendMissing(exchange, 405);
                return;
            }
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            String route = path == null || path.isBlank() || "/".equals(path)
                    ? "index.html" : path.substring(1);

            if (MANIFEST_ROUTE.equals(route)) {
                serveFile(exchange, method, manifest);
                return;
            }
            if (route.startsWith(RUNTIME_ROUTE)) {
                Path candidate = runtimeRoot.resolve(route.substring(RUNTIME_ROUTE.length()))
                        .toAbsolutePath().normalize();
                if (!candidate.startsWith(runtimeRoot)) {
                    sendMissing(exchange, 404);
                    return;
                }
                serveFile(exchange, method, candidate);
                return;
            }
            serveResource(exchange, method, TEMPLATE_RESOURCE + route);
        }

        private static void serveFile(HttpExchange exchange, String method, Path file)
                throws IOException {
            if (!Files.isRegularFile(file)) {
                sendMissing(exchange, 404);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", mimeType(file.toString()));
            long size = Files.size(file);
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, size);
            try (OutputStream output = exchange.getResponseBody()) {
                Files.copy(file, output);
            }
        }

        private static void serveResource(HttpExchange exchange, String method, String resource)
                throws IOException {
            URL url = HtmlPreviewLauncher.class.getClassLoader().getResource(resource);
            if (url == null || resource.contains("../")) {
                sendMissing(exchange, 404);
                return;
            }
            byte[] content;
            try (InputStream input = url.openStream()) {
                content = input.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", mimeType(resource));
            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(content);
            }
        }

        private static void sendMissing(HttpExchange exchange, int status) throws IOException {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        }
    }

    private record ManifestEntry(String path, long size, boolean directory, boolean preload) {
    }
}
