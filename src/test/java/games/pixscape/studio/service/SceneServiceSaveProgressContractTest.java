package games.pixscape.studio.service;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneServiceSaveProgressContractTest {

    @Test
    public void saveProjectAndCurrentSceneWithProgress_declaresExpectedStepMessages() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "public void saveProjectAndCurrentSceneWithProgress(");

        assertTrue(methodBody.contains("\"Preparing save...\""));
        assertTrue(methodBody.contains("\"Saving project file...\""));
        assertTrue(methodBody.contains("\"Repacking atlas...\""));
        assertTrue(methodBody.contains("\"Rebuilding tiled sparse data...\""));
        assertTrue(methodBody.contains("\"Saving scene...\""));
        assertTrue(methodBody.contains("\"Saving tiled animations...\""));
        assertTrue(methodBody.contains("\"Exporting runtime...\""));
        assertTrue(methodBody.contains("\"Finalizing...\""));
    }

    @Test
    public void saveProjectAndCurrentSceneWithProgress_usesRunnerAndSceneGuard() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "public void saveProjectAndCurrentSceneWithProgress(");

        assertTrue(methodBody.contains("SaveProgressRunner runner = new SaveProgressRunner(uiStage);"));
        assertTrue(methodBody.contains("if (!plan.hasSceneToSave())"));
        assertTrue(methodBody.contains("runner.run(steps, onSuccess, onError);"));
    }

    @Test
    public void asyncAtlasProgress_propagatesDeferredFailuresToRunner() throws Exception {
        String source = readSceneServiceSource();
        String saveBody = methodBody(source, "public void saveProjectAndCurrentSceneWithProgress(");
        String waitBody = methodBody(source, "private void waitForAsyncPackCompletion(");

        assertTrue(saveBody.contains("(progress, next, fail) -> maybeRepackAtlasAsync(plan, progress, next, fail)"));
        assertTrue(waitBody.contains("catch (Throwable t)"));
        assertTrue(waitBody.contains("onError.accept(t)"));
    }

    @Test
    public void saveProjectAndCurrentSceneWithProgress_requiresRuntimeExportSuccess() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "public void saveProjectAndCurrentSceneWithProgress(");
        String fallbackBody = methodBody(source, "private void executePreparedSavePlan(SaveExecutionPlan plan)");

        assertTrue(methodBody.contains("exportRuntime(plan.cfg(), plan.studioDir())"));
        assertFalse(methodBody.contains("exportRuntimeBestEffort(plan.cfg(), plan.studioDir())"));
        assertTrue(fallbackBody.contains("exportRuntime(plan.cfg(), plan.studioDir())"));
        assertFalse(fallbackBody.contains("exportRuntimeBestEffort(plan.cfg(), plan.studioDir())"));
    }

    @Test
    public void tmxImportProgress_declaresRealMonotonicPhasesAndActivatesAfterPersistence() throws Exception {
        String source = readSceneServiceSource();
        String methodBody = methodBody(source, "public void importTmxAsNewSceneWithProgress(");
        String[] expectedSteps = {
                "0.00f, \"Preparing import...\"",
                "0.08f, \"Reading and validating Tiled map...\"",
                "0.18f, \"Creating imported scene...\"",
                "0.30f, \"Importing tilesets and images...\"",
                "0.62f, \"Creating layers and tiles...\"",
                "0.82f, \"Updating scene atlas...\"",
                "0.94f, \"Saving project metadata...\"",
                "0.98f, \"Opening imported scene...\"",
                "1.00f, \"Import complete\""
        };

        int previous = -1;
        for (String expectedStep : expectedSteps) {
            int index = methodBody.indexOf(expectedStep);
            assertTrue("Missing progress step: " + expectedStep, index > previous);
            previous = index;
        }
        assertTrue(methodBody.indexOf("context.session.persistAndFinish()")
                < methodBody.indexOf("activateImportedTmxScene("));
        assertTrue(methodBody.indexOf("activateImportedTmxScene(")
                < methodBody.indexOf("1.00f, \"Import complete\""));
        assertTrue(methodBody.contains("new SaveProgressRunner("));
        assertTrue(methodBody.contains("\"Importing Tiled map\""));
        assertTrue(methodBody.contains("() -> context.terminal"));
    }

    @Test
    public void tmxImportProgress_preservesRollbackAndActivationRecoveryBoundaries() throws Exception {
        String source = readSceneServiceSource();
        String progressBody = methodBody(source, "public void importTmxAsNewSceneWithProgress(");
        String phaseBody = methodBody(source, "private SaveProgressRunner.Step tmxImportStep(");

        assertTrue(progressBody.contains("recoverTmxImportActivationFailure("));
        assertTrue(progressBody.contains("restorePreviousSceneAfterTmxActivationFailure("));
        assertTrue(phaseBody.contains("context.session.rollback(failure)"));
        assertTrue(phaseBody.contains("context.terminal = true;"));
        assertTrue(phaseBody.contains("fail.accept(failure);"));
    }

    @Test
    public void tmxImportAssumesCallerAlreadyResolvedCurrentSceneSaveDecision() throws Exception {
        String source = readSceneServiceSource();
        String synchronousBody = methodBody(source, "public TmxSceneImportResult importTmxAsNewScene(");
        String progressBody = methodBody(source, "public void importTmxAsNewSceneWithProgress(");

        assertFalse(synchronousBody.contains("requiresSaveBeforeLeavingCurrentScene()"));
        assertFalse(synchronousBody.contains("saveCurrentSceneOnly("));
        assertTrue(progressBody.contains("context.previousSceneName = context.cfg.getCurrentSceneName()"));
        assertFalse(progressBody.contains("requiresSaveBeforeLeavingCurrentScene()"));
        assertFalse(progressBody.contains("saveCurrentSceneOnly("));
        assertFalse(progressBody.contains("\"Saving current scene and repacking atlas...\""));
    }

    private static String readSceneServiceSource() throws Exception {
        Path sceneServicePath = Path.of("src/main/java/games/pixscape/studio/service/SceneService.java");
        return Files.readString(sceneServicePath, StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
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
