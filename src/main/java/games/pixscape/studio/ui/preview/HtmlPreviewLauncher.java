package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.Gdx;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExportPaths;
import games.pixscape.studio.exception.HtmlPreviewNotReadyException;
import games.pixscape.studio.io.StudioFs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class HtmlPreviewLauncher {

    private static final String MISSING_TEMPLATE_MESSAGE =
            "HTML preview template is missing. HTML preview infrastructure is ready, but the generic HTML player has not been added yet.";

    private static final String TEMPLATE_DIR_RESOURCE = "html-preview-template";
    private static final String TEMPLATE_INDEX_RESOURCE = TEMPLATE_DIR_RESOURCE + "/index.html";
    private static final String TEMPLATE_ZIP_RESOURCE = "html-preview-template.zip";

    private static HttpServer activeServer;
    private static Runnable activeOnClosedCallback;

    private HtmlPreviewLauncher() {
    }

    public static synchronized void open(ProjectConfig cfg, Runnable onOpened, Runnable onClosed) {
        Objects.requireNonNull(cfg, "cfg");

        try {
            stop();

            Path studioProjectRoot = StudioFs.requireStudioProjectDir(cfg).file().toPath();
            Path exportRoot = getRequiredExportRoot(cfg);
            Path runtimeRoot = exportRoot.resolve(PixscapeEngine.RUNTIME_DIR_NAME);

            if (!Files.isDirectory(runtimeRoot)) {
                throw new IllegalStateException("HTML preview failed: missing runtime project directory: " + runtimeRoot);
            }

            Path previewRoot = preparePreviewRoot(studioProjectRoot);
            Path assetsRoot = previewRoot.resolve("assets");

            copyHtmlTemplate(previewRoot);
            Files.createDirectories(assetsRoot);

            copyDirectory(runtimeRoot, assetsRoot.resolve(PixscapeEngine.RUNTIME_DIR_NAME));
            writeAssetsManifest(assetsRoot);

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", new StaticFileHandler(previewRoot));
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

    private static Path getRequiredExportRoot(ProjectConfig cfg) {
        String exportRootPath = cfg.exportRootPathDir;
        if (exportRootPath == null || exportRootPath.isBlank()) {
            throw new IllegalStateException("HTML preview failed: export root is not configured.");
        }

        Path exportRoot = RuntimeExportPaths.userRootPath(Path.of(exportRootPath));
        if (!Files.isDirectory(exportRoot)) {
            throw new IllegalStateException("HTML preview failed: export root directory does not exist: " + exportRoot);
        }

        return exportRoot;
    }

    private static Path preparePreviewRoot(Path studioProjectRoot) throws IOException {
        Path root = studioProjectRoot.resolve(".pixscape").resolve("preview").resolve("html");
        if (Files.exists(root)) deleteRecursively(root);
        Files.createDirectories(root);
        return root;
    }

    private static void copyHtmlTemplate(Path previewRoot) throws IOException {
        try {
            copyClasspathDirectory(TEMPLATE_DIR_RESOURCE, previewRoot);
            return;
        } catch (IOException directoryCopyFailure) {
            try (InputStream in = HtmlPreviewLauncher.class.getClassLoader().getResourceAsStream(TEMPLATE_ZIP_RESOURCE)) {
                if (in == null) {
                    HtmlPreviewNotReadyException missingTemplate = new HtmlPreviewNotReadyException(MISSING_TEMPLATE_MESSAGE);
                    missingTemplate.addSuppressed(directoryCopyFailure);
                    throw missingTemplate;
                }
                unzipTemplate(in, previewRoot);
            } catch (IOException zipCopyFailure) {
                zipCopyFailure.addSuppressed(directoryCopyFailure);
                throw zipCopyFailure;
            }
        }
    }

    private static CopyResult copyClasspathDirectory(String resourceDir, Path target) throws IOException {
        return copyClasspathDirectory(resourceDir, target, defaultChildResource(resourceDir));
    }

    private static CopyResult copyClasspathDirectory(String resourceDir, Path target, String childResource) throws IOException {
        ClassLoader loader = HtmlPreviewLauncher.class.getClassLoader();
        URL url = loader.getResource(resourceDir);

        if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
            try {
                int copiedFiles = copyDirectory(Path.of(url.toURI()), target);
                return new CopyResult("dev filesystem", copiedFiles);
            } catch (Exception e) {
                throw new IOException("Failed to copy resource directory: " + resourceDir, e);
            }
        }

        if (url == null && childResource != null) {
            url = loader.getResource(childResource);
        }

        if (url == null) {
            throw new IOException("Missing resource directory on classpath: " + resourceDir);
        }

        if ("jar".equalsIgnoreCase(url.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();

            try (JarFile jar = connection.getJarFile()) {
                String prefix = resourceDir.endsWith("/") ? resourceDir : resourceDir + "/";
                Enumeration<JarEntry> entries = jar.entries();
                int copiedFiles = 0;

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (!name.startsWith(prefix)) continue;

                    String rel = name.substring(prefix.length());
                    if (rel.isEmpty()) continue;

                    Path destination = target.resolve(rel).normalize();

                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        try (InputStream in = jar.getInputStream(entry)) {
                            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                        copiedFiles++;
                    }
                }

                if (copiedFiles == 0) {
                    throw new IOException("No classpath entries copied for resource directory: " + resourceDir);
                }

                return new CopyResult("classpath jar", copiedFiles);
            }
        }

        throw new IOException("Unsupported resource protocol for " + resourceDir + ": " + url.getProtocol());
    }

    private static String defaultChildResource(String resourceDir) {
        if (TEMPLATE_DIR_RESOURCE.equals(resourceDir)) return TEMPLATE_INDEX_RESOURCE;
        return null;
    }

    private static void writeAssetsManifest(Path assetsRoot) throws IOException {
        Path manifest = assetsRoot.resolve("assets.txt");

        try (Stream<Path> stream = Files.walk(assetsRoot)) {
            String body = stream
                    .filter(path -> !path.equals(manifest))
                    .map(path -> {
                        try {
                            String rel = assetsRoot.relativize(path).toString().replace('\\', '/');
                            if (rel.isEmpty()) return null;

                            if (Files.isDirectory(path)) {
                                return "d:" + rel + ":" + rel + ":0:text/plain:1";
                            }

                            return assetType(rel) + ":" + rel + ":" + rel + ":"
                                    + Files.size(path) + ":" + mimeType(rel) + ":1";
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.joining("\n"));

            Files.writeString(manifest, body, StandardCharsets.UTF_8);
        }
    }

    public static synchronized void stop() {
        HttpServer server = activeServer;
        Runnable callback = activeOnClosedCallback;

        activeServer = null;
        activeOnClosedCallback = null;

        if (server != null) {
            try {
                server.stop(0);
            } catch (Throwable t) {
                logStopFailure("Failed to stop HTML preview server.", t);
            }
        }

        if (callback != null) {
            try {
                callback.run();
            } catch (Throwable t) {
                logStopFailure("HTML preview close callback failed.", t);
            }
        }
    }

    private static void logStopFailure(String message, Throwable t) {
        if (Gdx.app != null) {
            Gdx.app.error("HtmlPreviewLauncher", message, t);
            return;
        }

        System.err.println("HtmlPreviewLauncher: " + message);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    private static int copyDirectory(Path source, Path target) throws IOException {
        int[] copiedFiles = {0};
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative.toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        copiedFiles[0]++;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            throw ex;
        }
        return copiedFiles[0];
    }

    private static void unzipTemplate(InputStream input, Path targetRoot) throws IOException {
        Path safeRoot = targetRoot.toAbsolutePath().normalize();

        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path destination = safeRoot.resolve(entry.getName()).normalize();
                if (!destination.startsWith(safeRoot)) {
                    throw new IOException("Unsafe zip entry in HTML preview template: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) throw io;
            throw ex;
        }
    }

    private static String assetType(String path) {
        String p = path.toLowerCase();

        if (p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".webp")) return "i";
        if (p.endsWith(".mp3") || p.endsWith(".ogg") || p.endsWith(".wav")) return "a";
        if (p.endsWith(".json") || p.endsWith(".atlas") || p.endsWith(".frag") || p.endsWith(".vert")
                || p.endsWith(".xml") || p.endsWith(".txt") || p.endsWith(".glsl") || p.endsWith(".p")
                || p.endsWith(".fnt")
                || p.endsWith(".pixprefab") || p.endsWith(".pixfragment") || p.endsWith(".pixfragment.json")) {
            return "t";
        }

        return "b";
    }

    private static String mimeType(String path) {
        String p = path.toLowerCase();

        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".ogg")) return "audio/ogg";
        if (p.endsWith(".wav")) return "audio/wav";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".xml")) return "application/xml";

        if (p.endsWith(".atlas") || p.endsWith(".frag") || p.endsWith(".vert")
                || p.endsWith(".glsl") || p.endsWith(".txt") || p.endsWith(".p")
                || p.endsWith(".fnt")
                || p.endsWith(".pixprefab") || p.endsWith(".pixfragment.json")) {
            return "text/plain";
        }

        return "application/octet-stream";
    }

    private static final class StaticFileHandler implements HttpHandler {

        private final Path root;
        private final Map<String, String> mimeTypes = new HashMap<>();

        private StaticFileHandler(Path root) {
            this.root = root.toAbsolutePath().normalize();

            mimeTypes.put(".html", "text/html");
            mimeTypes.put(".js", "application/javascript");
            mimeTypes.put(".css", "text/css");
            mimeTypes.put(".json", "application/json");
            mimeTypes.put(".png", "image/png");
            mimeTypes.put(".jpg", "image/jpeg");
            mimeTypes.put(".jpeg", "image/jpeg");
            mimeTypes.put(".webp", "image/webp");
            mimeTypes.put(".atlas", "text/plain");
            mimeTypes.put(".frag", "text/plain");
            mimeTypes.put(".vert", "text/plain");
            mimeTypes.put(".glsl", "text/plain");
            mimeTypes.put(".p", "text/plain");
            mimeTypes.put(".pixprefab", "text/plain");
            mimeTypes.put(".pixfragment.json", "text/plain");
            mimeTypes.put(".wasm", "application/wasm");
            mimeTypes.put(".xml", "application/xml");
            mimeTypes.put(".txt", "text/plain");
            mimeTypes.put(".data", "application/octet-stream");
            mimeTypes.put(".fnt", "text/plain");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            URI requestUri = exchange.getRequestURI();
            String rawPath = requestUri.getPath();
            String normalizedPath = (rawPath == null || rawPath.isBlank() || "/".equals(rawPath))
                    ? "index.html"
                    : rawPath.substring(1);

            Path candidate = root.resolve(normalizedPath).toAbsolutePath().normalize();

            if (!candidate.startsWith(root) || Files.isDirectory(candidate) || !Files.exists(candidate)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", resolveMimeType(candidate));

            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(200, Files.size(candidate));
            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(candidate, os);
            }
        }

        private String resolveMimeType(Path file) {
            String name = file.getFileName().toString().toLowerCase();
            for (Map.Entry<String, String> entry : mimeTypes.entrySet()) {
                if (name.endsWith(entry.getKey())) return entry.getValue();
            }
            String guessed = URLConnection.guessContentTypeFromName(name);
            return guessed != null ? guessed : "application/octet-stream";
        }
    }

    private record CopyResult(String sourceMode, int fileCount) {
    }
}
