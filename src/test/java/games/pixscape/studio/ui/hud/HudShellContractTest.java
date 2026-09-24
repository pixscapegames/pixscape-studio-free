package games.pixscape.studio.ui.hud;

import games.pixscape.runtime.hud.document.HudNodeKind;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HudShellContractTest {
    @Test
    public void toolboxExposesOnlyLayoutAndNonResourceContentConstructs() {
        Assert.assertEquals(List.of(HudNodeKind.GROUP, HudNodeKind.TABLE,
                HudNodeKind.STACK, HudNodeKind.CONTAINER, HudNodeKind.SCROLL_PANE,
                HudNodeKind.WINDOW, HudNodeKind.DIALOG),
                HudWidgetsPanel.layoutKinds());
        Assert.assertEquals(List.of(HudNodeKind.LABEL, HudNodeKind.TEXTRA_LABEL,
                        HudNodeKind.TEXT_FIELD, HudNodeKind.TEXT_BUTTON,
                        HudNodeKind.IMAGE_BUTTON, HudNodeKind.IMAGE_TEXT_BUTTON,
                        HudNodeKind.CHECK_BOX, HudNodeKind.SELECT_BOX, HudNodeKind.LIST, HudNodeKind.SLIDER,
                        HudNodeKind.PROGRESS_BAR),
                HudWidgetsPanel.contentKinds());
    }

    @Test
    public void previewUsesRuntimeMaterializerWithStudioAuthoringLifecycle() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/hud/HudEditorSession.java"));
        Assert.assertTrue(source.contains("new HudMaterializer"));
        Assert.assertTrue(source.contains("HudAuthoringResources.prepare"));
        Assert.assertTrue(source.contains("new HudAuthoringSession"));
        Assert.assertFalse(source.contains("HudSession.create"));
        Assert.assertFalse(source.contains("HudResources.prepare"));
    }

    @Test
    public void sceneTabsReplaceTheObsoleteBackToSceneControl()
            throws Exception {
        String hierarchy = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/hud/HudHierarchyPanel.java"));
        String widgets = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/hud/HudWidgetsPanel.java"));
        Assert.assertFalse(hierarchy.contains("Back to Scene"));
        Assert.assertFalse(widgets.contains("activateMaterializedScene"));
        Assert.assertFalse(hierarchy.contains("widgetsButton"));
        Assert.assertFalse(widgets.contains("hierarchyButton"));
    }

    @Test
    public void normalModeRestoresWorldWorkspaceAndInspectorContracts() throws Exception {
        String treeSource = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java"));
        Assert.assertTrue(treeSource.contains(
                "projectionForDocument(type, worldContent, hudHierarchy)"));

        String propertiesSource = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java"));
        Assert.assertTrue(propertiesSource.contains("hudMode = type == EditorDocumentType.HUD_SCREEN"));
        Assert.assertTrue(propertiesSource.replace("\r\n", "\n")
                .contains("} else {\n            dirty = true;"));

        String applicationSource = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java"));
        Assert.assertTrue(applicationSource.contains("dockManager.setRulersVisible(rulersVisibleBeforeHud)"));
        Assert.assertTrue(applicationSource.contains("canvas.act(dt);"));
        Assert.assertTrue(applicationSource.contains("canvas.draw();"));
    }

    @Test
    public void frameOrderResolvesLogicalCenterAndEndsWithKnownUiViewport()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java"));
        int act = source.indexOf("uiStage.act(dt)");
        int resolve = source.indexOf("resolveCenterBoundsLogical(", act);
        int hudDraw = source.indexOf("hudEditorSession.draw(centerBoundsLogical)");
        int uiViewport = source.indexOf("uiStage.getViewport().apply(false)", hudDraw);
        int uiDraw = source.indexOf("uiStage.draw()", uiViewport);

        Assert.assertTrue(act >= 0);
        Assert.assertTrue(resolve > act);
        Assert.assertTrue(hudDraw > resolve);
        Assert.assertTrue(uiViewport > hudDraw);
        Assert.assertTrue(uiDraw > uiViewport);
    }

    @Test
    public void hudPassDelegatesLogicalBoundsAndViewportApplicationToAuthoringSession()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/hud/HudEditorSession.java"));
        Assert.assertTrue(source.contains("authoringSession.configure(asset, x, y, width, height)"));
        Assert.assertTrue(source.contains("authoringSession.draw()"));
        Assert.assertFalse(source.contains("glViewport"));
        Assert.assertFalse(source.contains("glScissor"));
        Assert.assertFalse(source.contains("HudPreviewGeometry"));
    }

    @Test
    public void candidatePublicationCompletesBeforeOldResourceCleanup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/hud/HudEditorSession.java"));
        int publishSession = source.indexOf("currentResources = candidateResources;");
        int publishDocument = source.indexOf("document = candidate;", publishSession);
        int cleanup = source.indexOf("disposeReplaced(previousProjection, previousResources);",
                publishDocument);

        Assert.assertTrue(publishSession >= 0);
        Assert.assertTrue(publishDocument > publishSession);
        Assert.assertTrue(cleanup > publishDocument);
        Assert.assertFalse(source.contains("HudResources"));
    }

    @Test
    public void isolationSliceContainsNoHudMutationCommands() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/hud/HudWidgetsPanel.java"));
        Assert.assertFalse(source.contains("HudHierarchyEditor"));
        Assert.assertFalse(source.contains("Add Child"));
        Assert.assertFalse(source.contains("wrapSelected"));
        Assert.assertFalse(source.contains("removeSelected"));
        Assert.assertFalse(source.contains("documentManager.activate("));
        Assert.assertFalse(source.contains("documentManager.requestClose("));
        Assert.assertFalse(source.contains("TabbedPane"));
    }

    @Test
    public void layersPanelNoLongerOwnsACompetingHudSurface() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/layer/LayersPanel.java"));
        Assert.assertFalse(source.contains("hudProjection"));
        Assert.assertTrue(source.contains("add(sceneContent).grow()"));
    }

    @Test
    public void panelsDoNotProjectStaleSceneContentWithoutAnActiveDocument() throws Exception {
        String items = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java"));
        String properties = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java"));
        String layers = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/layer/LayersPanel.java"));

        Assert.assertTrue(items.contains("current != null ? current.type() : null"));
        Assert.assertTrue(items.contains("if (projection != null)"));
        Assert.assertTrue(properties.contains("current != null ? current.type() : null"));
        Assert.assertTrue(properties.contains("if (type == null)"));
        Assert.assertFalse(layers.contains("hudProjection"));
    }
}
