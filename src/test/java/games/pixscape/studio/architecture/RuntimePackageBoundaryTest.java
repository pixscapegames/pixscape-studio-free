package games.pixscape.studio.architecture;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimePackageBoundaryTest {

    private static final Pattern RUNTIME_PACKAGE_DECLARATION = Pattern.compile(
            "(?m)^\\s*package\\s+games\\.pixscape\\.runtime(?:\\s*;|\\.)"
    );

    @Test
    public void productionSourcesDoNotDeclareRuntimePackages() throws Exception {
        Path productionRoot = Paths.get("src", "main", "java");
        Path runtimePackageDirectory = productionRoot.resolve(
                Paths.get("games", "pixscape", "runtime")
        );
        List<Path> violations = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(productionRoot)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = new String(
                                    Files.readAllBytes(path), StandardCharsets.UTF_8
                            );
                            if (RUNTIME_PACKAGE_DECLARATION.matcher(source).find()) {
                                violations.add(path);
                            }
                        } catch (Exception failure) {
                            throw new IllegalStateException("Cannot inspect source: " + path, failure);
                        }
                    });
        }

        assertFalse("Studio production Runtime package directory must not exist",
                Files.exists(runtimePackageDirectory));
        assertTrue("Studio production sources declare Runtime packages: " + violations,
                violations.isEmpty());
    }
}
