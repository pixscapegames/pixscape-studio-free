package games.pixscape.studio.helper;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
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
        ClassLoader loader = RuntimeShaderResources.class.getClassLoader();
        URL sentinel = loader.getResource(RUNTIME_SHADER_SENTINEL_RESOURCE);

        if (sentinel == null) {
            throw new IOException("Missing runtime shader sentinel resource on classpath: "
                    + RUNTIME_SHADER_SENTINEL_RESOURCE);
        }

        if ("file".equalsIgnoreCase(sentinel.getProtocol())) {
            try {
                Path sentinelPath = Path.of(sentinel.toURI());
                Path shaderRoot = sentinelPath;
                for (int i = 0; i < REQUIRED_TEXTURE_ARRAY_VERT.split("/").length; i++) {
                    shaderRoot = shaderRoot.getParent();
                }

                int copiedFiles = copyDirectory(shaderRoot, target);
                failIfClassFilesCopied(target);
                return copiedFiles;
            } catch (Exception e) {
                if (e instanceof IOException io) throw io;
                throw new IOException("Failed to copy runtime shader resources from filesystem.", e);
            }
        }

        if ("jar".equalsIgnoreCase(sentinel.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) sentinel.openConnection();

            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                int copiedFiles = 0;

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (!name.startsWith(RUNTIME_SHADER_RESOURCE_PREFIX)) continue;

                    String rel = name.substring(RUNTIME_SHADER_RESOURCE_PREFIX.length());
                    if (rel.isEmpty()) continue;

                    if (rel.endsWith(".class")) {
                        throw new IOException("Runtime shader extraction resolved compiled classes instead of shader resources: "
                                + name);
                    }

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
                    throw new IOException("No runtime shader resources copied from classpath jar.");
                }

                failIfClassFilesCopied(target);
                return copiedFiles;
            }
        }

        throw new IOException("Unsupported runtime shader resource protocol: " + sentinel.getProtocol());
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
