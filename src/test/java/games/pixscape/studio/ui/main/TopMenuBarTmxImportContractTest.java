package games.pixscape.studio.ui.main;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TopMenuBarTmxImportContractTest {

    @Test
    public void fileImportMenu_listsTmxImportAfterAssetImport() throws Exception {
        String source = topMenuBarSource();

        int assetsIndex = source.indexOf("MenuItem importAssetsItem = new MenuItem(\"Assets...\");");
        int tmxIndex = source.indexOf("MenuItem importTmxItem = new MenuItem(\"Tiled map (.tmx)...\");");

        assertTrue("Asset import menu item should exist.", assetsIndex >= 0);
        assertTrue("TMX import menu item should exist.", tmxIndex > assetsIndex);
        assertTrue(source.contains("onClick(importTmxItem, this::openTmxImportChooser);"));
    }

    @Test
    public void tmxImportUi_preflightsThenCallsSceneServiceForNewSceneImport() throws Exception {
        String source = topMenuBarSource();

        String chooserBody = methodBody(source, "private void openTmxImportChooser()");
        assertTrue(chooserBody.contains("new StudioFileChooser(studioProjectDirectoryOrDefault(), FileChooser.Mode.OPEN)"));
        assertTrue(chooserBody.contains("FileChooser.SelectionMode.FILES"));
        assertTrue(chooserBody.contains("typeFilter.addRule(\"Tiled map (*.tmx)\", \"tmx\");"));
        assertTrue(chooserBody.contains("prepareTmxImport(file);"));

        String prepareBody = methodBody(source, "private void prepareTmxImport(FileHandle file)");
        assertTrue(prepareBody.contains("TmxImportUiSupport.prepare(file)"));
        assertTrue(prepareBody.contains("!preparation.planResult().hasPlan() || preparation.hasBlockingDiagnostics()"));
        assertTrue(prepareBody.contains("TmxImportUiSupport.formatDiagnostics(preparation.diagnostics())"));
        assertTrue(prepareBody.contains("TmxImportMessageDialog.show("));
        assertTrue(prepareBody.contains("new TmxImportDialog("));
        assertTrue(prepareBody.contains("sceneName -> importTmxAsNewScene(file, sceneName)"));

        String importBody = methodBody(source, "private void importTmxAsNewScene(FileHandle file, String sceneName)");
        assertTrue(importBody.contains("sceneService.importTmxAsNewScene(new TmxSceneImportRequest(file, sceneName))"));
        assertTrue(importBody.contains("TmxImportUiSupport.formatSuccessMessage(result)"));
        assertTrue(importBody.contains("TmxImportUiSupport.formatFailureMessage(result)"));
        assertTrue(importBody.contains("TmxImportMessageDialog.show("));
        assertFalse(source.contains("new TmxSceneImportService("));
    }

    @Test
    public void tmxImportDialog_isModalSummaryWithEditableSceneName() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/importer/TmxImportDialog.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("setModal(true);"));
        assertTrue(source.contains("private final SimpleTextField sceneNameField"));
        assertTrue(source.contains("addRow(root, \"Source\""));
        assertTrue(source.contains("addRow(root, \"Orientation / projection\""));
        assertTrue(source.contains("addRow(root, \"Map size\""));
        assertTrue(source.contains("addRow(root, \"Tile layers\""));
        assertTrue(source.contains("sceneNameField.setText(preparation.proposedSceneName());"));
        assertTrue(source.contains("TmxImportUiSupport.resolveSceneName("));
        assertTrue(source.contains("onImport.accept(sceneName);"));
    }

    @Test
    public void tmxImportFailureDialog_usesScrollableDiagnostics() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/importer/TmxImportMessageDialog.java"),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("new VisScrollPane(content)"));
        assertTrue(source.contains("body.setWrap(true);"));
        assertTrue(source.contains("stage.getHeight()"));
        assertTrue(source.contains("dialog.button(\"OK\")"));
    }

    private static String topMenuBarSource() throws Exception {
        return Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/TopMenuBar.java"),
                StandardCharsets.UTF_8
        );
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
