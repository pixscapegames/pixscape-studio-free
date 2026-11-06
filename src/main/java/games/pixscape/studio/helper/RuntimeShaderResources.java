package games.pixscape.studio.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class RuntimeShaderResources {
    private static final String DEV_SOURCE_PATHS_PROPERTY = "pixscape.runtimeShaders.devSourcePaths";
    private static final String REQUIRED_TEXTURE_ARRAY_VERT = "core/es3-webgl2/texture-array.vert";
    private static final String REQUIRED_TEXTURE_ARRAY_FRAG = "core/es3-webgl2/texture-array.frag";
    private static final String RUNTIME_SHADER_RESOURCE_PREFIX = "shaders/";
    private static final String RUNTIME_SHADER_SENTINEL_RESOURCE =
            RUNTIME_SHADER_RESOURCE_PREFIX + REQUIRED_TEXTURE_ARRAY_VERT;

    private RuntimeShaderResources() {
    }

    public static int copyTo(Path target) throws IOException {
        if (Boolean.getBoolean(DEV_SOURCE_PATHS_PROPERTY)) {
            Path[] devCandidates = {
                    Path.of("pixscape-runtime", "src", "main", "resources", "shaders"),
                    Path.of("..", "pixscape-runtime", "src", "main", "resources", "shaders"),
                    Path.of("src", "main", "resources", "shaders")
            };

            for (Path candidate : devCandidates) {
                if (Files.isDirectory(candidate)) {
                    int copiedFiles = copyDirectory(candidate, target);
                    failIfClassFilesCopied(target);
                    validateRuntimeShaders(target);
                    return copiedFiles;
                }
            }
        }

        int copiedFiles = copyRuntimeShaderResources(target);
        validateRuntimeShaders(target);
        return copiedFiles;
    }

    static int copyRuntimeShaderResources(Path target) throws IOException {
        return (int) readBundledResources(target).stream().filter(resource -> !resource.directory()).count();
    }

    /** Enumerates the resolved Runtime dependency, shared by export and HTML preloading. */
    public static List<Resource> bundledResources() throws IOException {
        return readBundledResources(null);
    }

    private static List<Resource> readBundledResources(Path target) throws IOException {
        URL sentinel = RuntimeShaderResources.class.getClassLoader().getResource(RUNTIME_SHADER_SENTINEL_RESOURCE);
        if (sentinel == null) {
            throw new IOException("Missing runtime shader sentinel resource on classpath: "
                    + RUNTIME_SHADER_SENTINEL_RESOURCE);
        }
        List<Resource> resources = new ArrayList<>();
        if ("file".equalsIgnoreCase(sentinel.getProtocol())) {
            try {
                Path root = Path.of(sentinel.toURI());
                for (int i = 0; i < REQUIRED_TEXTURE_ARRAY_VERT.split("/").length; i++) root = root.getParent();
                try (Stream<Path> paths = Files.walk(root)) {
                    for (Path path : paths.toList()) {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        if (relative.isEmpty()) continue;
                        boolean directory = Files.isDirectory(path);
                        resources.add(new Resource(RUNTIME_SHADER_RESOURCE_PREFIX + relative,
                                directory ? 0 : Files.size(path), directory));
                    }
                }
                validateBundledResources(resources);
                if (target != null) copyDirectory(root, target);
            } catch (Exception e) {
                if (e instanceof IOException io) throw io;
                throw new IOException("Failed to enumerate runtime shader resources.", e);
            }
        } else if ("jar".equalsIgnoreCase(sentinel.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) sentinel.openConnection();
            connection.setUseCaches(false);
            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(RUNTIME_SHADER_RESOURCE_PREFIX)
                            || name.equals(RUNTIME_SHADER_RESOURCE_PREFIX)) continue;
                    if (entry.isDirectory()) name = name.substring(0, name.length() - 1);
                    resources.add(new Resource(name, entry.getSize(), entry.isDirectory()));
                }
                validateBundledResources(resources);
                if (target != null) {
                    // A local snapshot may have been republished since the class loader opened it.
                    // Read bytes from the same archive whose entries we just enumerated.
                    for (Resource resource : resources) {
                        Path destination = target.resolve(resource.path().substring(RUNTIME_SHADER_RESOURCE_PREFIX.length()));
                        if (resource.directory()) {
                            Files.createDirectories(destination);
                        } else {
                            Files.createDirectories(destination.getParent());
                            try (InputStream input = jar.getInputStream(jar.getJarEntry(resource.path()))) {
                                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }
        } else {
            throw new IOException("Unsupported runtime shader resource protocol: " + sentinel.getProtocol());
        }
        return resources;
    }

    private static void validateBundledResources(List<Resource> resources) throws IOException {
        for (Resource resource : resources) {
            if (resource.path().endsWith(".class")) {
                throw new IOException("Runtime shader resources contain compiled classes: " + resource.path());
            }
        }
        for (String required : List.of(REQUIRED_TEXTURE_ARRAY_VERT, REQUIRED_TEXTURE_ARRAY_FRAG,
                "examples/catalog.json")) {
            if (resources.stream().noneMatch(r -> !r.directory()
                    && r.path().equals(RUNTIME_SHADER_RESOURCE_PREFIX + required))) {
                throw new IOException("Missing runtime shader resource: " + required);
            }
        }
    }

    public record Resource(String path, long size, boolean directory) {
    }

    private static int copyDirectory(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) return 0;

        Files.createDirectories(target);
        int[] copiedFiles = {0};

        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path rel = source.relativize(path);
                if (rel.toString().isEmpty()) continue;

                Path destination = target.resolve(rel).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                    copiedFiles[0]++;
                }
            }
        }

        return copiedFiles[0];
    }

    private static void validateRuntimeShaders(Path target) throws IOException {
        Path vert = target.resolve(REQUIRED_TEXTURE_ARRAY_VERT);
        Path frag = target.resolve(REQUIRED_TEXTURE_ARRAY_FRAG);

        if (!Files.isRegularFile(vert) || !Files.isRegularFile(frag)) {
            throw new IOException("Runtime shaders are incomplete under " + target.toAbsolutePath().normalize()
                    + " (missing " + REQUIRED_TEXTURE_ARRAY_VERT + " or " + REQUIRED_TEXTURE_ARRAY_FRAG + ")");
        }
    }

    private static void failIfClassFilesCopied(Path target) throws IOException {
        if (!Files.exists(target)) return;

        try (Stream<Path> stream = Files.walk(target)) {
            Path classFile = stream
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .findFirst()
                    .orElse(null);

            if (classFile != null) {
                throw new IOException("Runtime shader extraction copied compiled class files into shaders: "
                        + classFile);
            }
        }
    }
}
