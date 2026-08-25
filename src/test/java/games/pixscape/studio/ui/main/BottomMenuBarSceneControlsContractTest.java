package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BottomMenuBarSceneControlsContractTest {
    private static final Path SOURCE_PATH = Path.of(
            "src/main/java/games/pixscape/studio/ui/main/BottomMenuBar.java"
    );

    @Test
    public void sceneSelectorContainsOnlyConfiguredSceneNames() throws Exception {
        String source = readSource();
        String refreshBody = methodBody(source, "public void refreshSelectBox()");

        assertTrue(refreshBody.contains("items.clear();"));
        assertTrue(refreshBody.contains("items.addAll(cfg.getSceneNames());"));
        assertFalse(source.contains("\"New...\""));
    }

    @Test
    public void addButtonUsesAddStyleAndIsImmediatelyBeforeDelete() throws Exception {
        String source = readSource();
        String constructorBody = methodBody(source, "public BottomMenuBar(StudioApplicationAdapter application)");

        assertTrue(constructorBody.contains("btnAddScene = new Button(VisUI.getSkin(), \"add\");"));
        assertOrdered(
                constructorBody,
                "add(sceneSelectBox).width(120).left();",
                "add(btnAddScene).padLeft(4).left();",
                "add(btnDeleteScene).padLeft(4).padRight(100).left();"
        );
    }

    @Test
    public void addButtonOpensAndCentersExistingNewSceneWindow() throws Exception {
        String source = readSource();
        String constructorBody = methodBody(source, "public BottomMenuBar(StudioApplicationAdapter application)");
        String openBody = methodBody(source, "private void openNewSceneWindow()");

        assertTrue(constructorBody.contains("openNewSceneWindow();"));
        assertOrdered(
                openBody,
                "newSceneWindow.resetSceneName();",
                "app.getUiStage().addActor(newSceneWindow.fadeIn());",
                "newSceneWindow.centerWindow();"
        );
    }

    @Test
    public void selectingRealSceneUsesExistingSwitchWorkflow() throws Exception {
        String constructorBody = methodBody(
                readSource(),
                "public BottomMenuBar(StudioApplicationAdapter application)"
        );

        assertTrue(constructorBody.contains("if (cur == null || cur.equals(lastValue)) return;"));
        assertTrue(constructorBody.contains("sceneSwitchWorkflow.request(cur);"));
    }

    @Test
    public void refreshSelectsCurrentSceneOrFirstRealScene() throws Exception {
        String refreshBody = methodBody(readSource(), "public void refreshSelectBox()");

        assertTrue(refreshBody.contains("items.contains(curName, false)"));
        assertTrue(refreshBody.contains("sceneSelectBox.getSelection().set(curName);"));
        assertTrue(refreshBody.contains("lastValue = curName;"));
        assertTrue(refreshBody.contains("sceneSelectBox.getSelection().set(items.first());"));
        assertTrue(refreshBody.contains("lastValue = null;"));
    }

    @Test
    public void busyStateDisablesSelectorAndAddWhilePreservingDeleteRule() throws Exception {
        String source = readSource();
        String busyBody = methodBody(source, "private void setSceneControlsBusy(boolean busy)");
        String deleteBody = methodBody(source, "private void updateDeleteSceneButtonState()");

        assertTrue(source.contains("this::setSceneControlsBusy"));
        assertTrue(busyBody.contains("sceneSelectBox.setDisabled(busy);"));
        assertTrue(busyBody.contains("btnAddScene.setDisabled(busy);"));
        assertTrue(busyBody.contains("updateDeleteSceneButtonState();"));
        assertTrue(deleteBody.contains("sceneControlsBusy"));
        assertTrue(deleteBody.contains("cfg.getCurrentSceneName() == null"));
        assertTrue(deleteBody.contains("cfg.getSceneNames().size <= 1"));
    }

    @Test
    public void successfulCreationRefreshesSelectionAndCloseDoesNotRepairSentinelState() throws Exception {
        String source = readSource();
        String constructorBody = methodBody(source, "public BottomMenuBar(StudioApplicationAdapter application)");

        assertOrdered(
                constructorBody,
                "app.getSceneService().createNewScene(",
                "newSceneWindow.fadeOut();",
                "refreshSelectBox();"
        );
        assertFalse(source.contains("protected void close()"));
    }

    private static String readSource() throws Exception {
        return Files.readString(SOURCE_PATH, StandardCharsets.UTF_8);
    }

    private static void assertOrdered(String source, String... fragments) {
        int previous = -1;
        for (String fragment : fragments) {
            int index = source.indexOf(fragment, previous + 1);
            assertTrue("Missing or out-of-order fragment: " + fragment, index > previous);
            previous = index;
        }
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
                if (depth == 0) return source.substring(bodyStart + 1, i);
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
