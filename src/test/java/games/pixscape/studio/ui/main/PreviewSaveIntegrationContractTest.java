package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class PreviewSaveIntegrationContractTest {

    @Test
    public void bottomMenuBar_previewFlow_runsSaveProgressOnlyWhenGuardRequiresIt() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/BottomMenuBar.java"),
                StandardCharsets.UTF_8
        );

        String launchPreviewWithSaveGuardBody = methodBody(source, "private void launchPreviewWithSaveGuard()");

        assertTrue(launchPreviewWithSaveGuardBody.contains("if (!app.getSceneService().requiresSaveBeforePreview())"));
        assertTrue(launchPreviewWithSaveGuardBody.contains("launchPreviewNow();"));
        assertTrue(launchPreviewWithSaveGuardBody.contains("return;"));
        assertTrue(launchPreviewWithSaveGuardBody.contains("app.getSceneService().saveProjectAndCurrentSceneWithProgress("));
        assertTrue(launchPreviewWithSaveGuardBody.contains("this::launchPreviewNow"));
    }

    @Test
    public void topMenuBar_manualSave_usesSaveProgressFlow() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/TopMenuBar.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("onClick(save, () -> runSaveWithProgress(null));"));
        String runSaveWithProgressBody = methodBody(source, "private void runSaveWithProgress(Runnable onSuccess)");
        assertTrue(runSaveWithProgressBody.contains("sceneService.saveProjectAndCurrentSceneWithProgress("));
    }

    @Test
    public void quitUsesSharedCurrentSceneSaveDecisionInsteadOfPreviewReadiness() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java"),
                StandardCharsets.UTF_8
        );
        String closeBody = methodBody(source, "public boolean closeRequested()");
        String guardBody = methodBody(source, "public void runAfterCurrentSceneSaveDecision(");

        assertTrue(closeBody.contains("runAfterCurrentSceneSaveDecision("));
        assertTrue(closeBody.contains("Gdx.app::exit"));
        assertTrue(guardBody.contains("sceneService.requiresSaveBeforeLeavingCurrentScene()"));
        assertTrue(guardBody.contains("sceneService.saveProjectAndCurrentSceneWithProgress("));
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.lastIndexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
