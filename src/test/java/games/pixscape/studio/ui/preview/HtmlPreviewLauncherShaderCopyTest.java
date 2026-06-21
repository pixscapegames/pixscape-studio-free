package games.pixscape.studio.ui.preview;

import games.pixscape.studio.helper.RuntimeShaderResources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public class HtmlPreviewLauncherShaderCopyTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void copyRuntimeShaderResources_shadersFromRuntimeClasspath_copiesRequiredWebGlTextureArrayShaders() throws Exception {
        Path target = temp.newFolder("shaders").toPath();

        RuntimeShaderResources.copyTo(target);

        assertTrue(Files.isRegularFile(target.resolve("core/es3-webgl2/texture-array.vert")));
        assertTrue(Files.isRegularFile(target.resolve("core/es3-webgl2/texture-array.frag")));
        assertTrue(Files.isRegularFile(target.resolve("examples/params.json")));
        assertTrue(Files.isRegularFile(target.resolve("includes/pixscape_common.glsl")));
        try (Stream<Path> stream = Files.walk(target)) {
            assertTrue(stream.noneMatch(path -> path.getFileName().toString().endsWith(".class")));
        }
    }
}
