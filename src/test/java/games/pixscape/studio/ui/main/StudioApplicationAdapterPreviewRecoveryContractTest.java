package games.pixscape.studio.ui.main;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StudioApplicationAdapterPreviewRecoveryContractTest {
    @Test
    public void leavingDesktopPreviewRestoresStudioShaderRegistry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java"),
                StandardCharsets.UTF_8);
        int transition = source.indexOf("if (!active) {");
        int restoreCall = source.indexOf("restoreStudioShadersAfterPreview();", transition);
        int reload = source.indexOf("ShaderRegistry.reloadForProject(", restoreCall);
        int event = source.indexOf("new EventFlow.ShaderListChanged", reload);

        Assert.assertTrue(transition >= 0);
        Assert.assertTrue(restoreCall > transition);
        Assert.assertTrue(reload > restoreCall);
        Assert.assertTrue(event > reload);
    }
}
