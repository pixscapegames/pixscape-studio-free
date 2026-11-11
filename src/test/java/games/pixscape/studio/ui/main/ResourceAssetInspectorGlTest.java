package games.pixscape.studio.ui.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.service.hud.HudScreenAssetAuthoringService;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.studio.ui.asset.AssetsThumbsView;
import games.pixscape.studio.ui.asset.FolderTreeView;
import games.pixscape.studio.ui.property.PropertiesPanel;
import games.pixscape.studio.ui.property.ResourceAssetProperties;
import org.junit.Test;

import java.nio.file.Files;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real Assets/Inspector wiring, with isolated projects and owned preview resources. */
public class ResourceAssetInspectorGlTest {
    @Test public void assetLayerAndEditorShareOneHudScreenDescription() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("shared-hud"));
            new HudScreenAssetAuthoringService().create(project.parent(), "Other");
            open(studio, project);
            PropertiesPanel panel = inspector(studio);
            var scene = (games.pixscape.studio.document.SceneEditorDocument) studio.getEditorDocumentManager().activeDocument();
            var associations = (games.pixscape.studio.service.hud.SceneHudAssociationService) field(studio, "sceneHudAssociationService");
            assertTrue(associations.associate(scene, "hud/Screen"));
            var hud = studio.openHudScreen("hud/Screen");
            hud.editSession().editSkin("Skin selection", "orig/skins/2/theme.json");
            var other = studio.openHudScreen("hud/Other");
            studio.getEditorDocumentManager().activate(scene.key());
            flush(panel);
            var files = authoredSnapshot(project.parent());

            panel.inspectAsset(new AssetNode(AssetNode.Kind.HUD_SCREEN, AssetNode.Root.HUD, "hud/Screen", "Screen", null));
            var assetDescription = hudDescription(panel);
            assertFlatHudScreen(panel, true);
            assertNull(panel.findActor("hudScreenSkin"));
            screenshot(studio, "hud-screen-assets");
            assertEquals("orig/skins/2/theme.json", assetDescription.get("Skin"));
            assertEquals(hud.asset().textureProfileId, assetDescription.get("Texture profile"));
            assertTrue(assetDescription.get("Status").contains("unsaved"));
            panel.inspectAsset(new AssetNode(AssetNode.Kind.HUD_SCREEN, AssetNode.Root.HUD, "hud/Other", "Other", null));
            clickLayer(studio, -1, 1);
            assertFlatHudScreen(panel, true);
            assertNull(panel.findActor("hudScreenSkin"));
            assertEquals("The scene association wins over Assets and the last open HUD", assetDescription, hudDescription(panel));
            screenshot(studio, "shared-scene-hud");
            screenshotBottom(studio, "shared-scene-hud-details");
            panel.findActor("openHudAssetEditor").fire(new ChangeListener.ChangeEvent());
            assertSame(hud, studio.getEditorDocumentManager().activeDocument());
            assertEquals(3, studio.getEditorDocumentManager().documents().size());
            studio.getHudEditorSession().selectNode(null);
            assertFlatHudScreen(panel, true);
            assertEquals(assetDescription, editorHudDescription(panel));
            assertNotNull(panel.findActor("hudScreenSkin"));
            assertNull(panel.findActor("openHudAssetEditor"));
            screenshot(studio, "hud-screen-editor");
            screenshotBottom(studio, "hud-screen-editor-details");
            studio.getEditorDocumentManager().activate(scene.key());
            flush(panel);
            clickLayer(studio, -1, 1);
            hud.editSession().undo();
            flush(panel);
            assertEquals("None", hudDescription(panel).get("Skin"));
            hud.editSession().redo();
            flush(panel);
            assertEquals("orig/skins/2/theme.json", hudDescription(panel).get("Skin"));
            studio.getEditorDocumentManager().closeNow(hud.key());
            flush(panel);
            assertEquals("Closing an unsaved HUD restores the persisted description", "None", hudDescription(panel).get("Skin"));
            assertEquals("Project file", hudDescription(panel).get("Source"));
            assertTrue(associations.associate(scene, "hud/Other"));
            flush(panel);
            assertEquals("hud/Other", hudDescription(panel).get("Screen ID"));
            panel.findActor("openHudAssetEditor").fire(new ChangeListener.ChangeEvent());
            assertSame(other, studio.getEditorDocumentManager().activeDocument());
            assertEquals(files, authoredSnapshot(project.parent()));
        });
    }

    @Test public void unavailableSceneHudReferencesStayExplicitAndReleaseTheirState() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("unavailable-hud"));
            open(studio, project);
            PropertiesPanel panel = inspector(studio);
            var scene = (games.pixscape.studio.document.SceneEditorDocument) studio.getEditorDocumentManager().activeDocument();
            var associations = (games.pixscape.studio.service.hud.SceneHudAssociationService) field(studio, "sceneHudAssociationService");
            assertTrue(associations.associate(scene, "hud/Screen"));
            clickLayer(studio, -1, 1);
            var layerCard = (ResourceAssetProperties) field(panel, "hudLayerAssetProperties");
            FileHandle assetFile = project.parent().child("hud/Screen" + games.pixscape.runtime.hud.HudScreenAsset.EXTENSION);
            byte[] assetBytes = assetFile.readBytes();
            assertTrue(assetFile.delete());
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
            flush(panel);
            assertTrue(hudDescription(panel).get("Status").contains("missing"));
            assertNull(panel.findActor("resourceAssetDocument"));
            assertNull(panel.findActor("openHudAssetEditor"));
            assetFile.writeString("invalid asset JSON", false);
            studio.reloadAssetInspection();
            assertTrue(hudDescription(panel).get("Status").contains("Unavailable"));
            assetFile.writeBytes(assetBytes, false);
            studio.reloadAssetInspection();
            assertEquals("Document loaded", hudDescription(panel).get("Status"));

            // Simulate invalid persisted metadata; inspection must never repair the association.
            scene.context().sceneMeta().defaultHudScreenId = "../invalid";
            EventFlow.i().publish(new EventFlow.SceneHudAssociationChanged(scene.context().sceneIdentity(), "../invalid", -77));
            flush(panel);
            assertEquals("../invalid", hudDescription(panel).get("Screen ID"));
            assertTrue(hudDescription(panel).get("Status").contains("Unavailable"));
            assertEquals("../invalid", scene.context().sceneMeta().defaultHudScreenId);
            assertNull(panel.findActor("resourceAssetDocument"));
            assertTrue(associations.remove(scene));
            flush(panel);
            assertEquals("No HUD associated", hudDescription(panel).get("Status"));
            assertNull(panel.findActor("resourceAssetScreenID"));
            assertFlatHudScreen(panel, false);
            assertNull(field(layerCard, "displayedHud"));
            assertNull(field(layerCard, "screenId"));
            assertTrue(associations.associate(scene, "hud/Screen"));
            flush(panel);
            assertEquals("hud/Screen", hudDescription(panel).get("Screen ID"));
            studio.getEditorDocumentManager().closeNow(scene.key());
            flush(panel);
            assertTrue(((Group) field(panel, "contentHolder")).getChildren().isEmpty());
            assertReleased(layerCard);
            assertNotNull(ProjectConfig.getInstance().projectFileName);
            studio.getSceneService().unloadProjectToEmptyEditor();
            flush(panel);
            assertReleased(layerCard);
            assertNull(layerCard.getParent());
        });
    }

    private static java.util.Map<String, String> hudDescription(PropertiesPanel panel) {
        var result = new java.util.LinkedHashMap<String, String>();
        for (String label : new String[]{"Screen ID", "Name", "Dimensions", "Document", "Skin", "Atlas", "Texture profile", "Status", "Source"}) {
            VisLabel value = panel.findActor("resourceAsset" + label.replace(" ", ""));
            if (value != null) result.put(label, value.getText().toString());
        }
        return result;
    }

    private static java.util.Map<String, String> editorHudDescription(PropertiesPanel panel) throws Exception {
        var result = new java.util.LinkedHashMap<String, String>();
        for (String label : new String[]{"Screen ID", "Name", "Dimensions", "Document", "Skin", "Atlas", "Texture profile", "Status", "Source"}) {
            if (label.equals("Skin")) {
                var choice = ((com.kotcrab.vis.ui.widget.VisSelectBox<?>) panel.findActor("hudScreenSkin")).getSelected();
                String skinId = (String) field(field(choice, "value"), "skinId");
                result.put(label, skinId != null ? skinId : "None");
            } else {
                VisLabel value = panel.findActor("hudScreen" + label.replace(" ", ""));
                assertNotNull(label, value);
                result.put(label, value.getText().toString());
            }
        }
        return result;
    }

    private static void assertFlatHudScreen(Group view, boolean hasScreen) {
        var labels = new java.util.ArrayList<String>();
        collectLabels(view, labels);
        assertEquals(1, java.util.Collections.frequency(labels, "HUD SCREEN"));
        assertEquals(hasScreen ? 1 : 0, java.util.Collections.frequency(labels, "Screen ID:"));
        for (String redundant : new String[]{"SCENE HUD", "HUD ASSET", "Associated HUD", "Visible in editor"})
            assertFalse(redundant, labels.contains(redundant));
    }

    private static void collectLabels(Group view, java.util.List<String> labels) {
        for (Actor child : view.getChildren()) {
            if (child instanceof VisLabel label) labels.add(label.getText().toString());
            if (child instanceof Group group) collectLabels(group, labels);
        }
    }

    @Test public void layersClicksInspectTheSceneHudAssociationAndRetakeTheInspector() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("layers"));
            open(studio, project);
            PropertiesPanel panel = inspector(studio);
            var scene = studio.getEditorDocumentManager().activeDocument();
            var context = studio.getSceneEditorContext();
            var associations = (games.pixscape.studio.service.hud.SceneHudAssociationService)
                    field(studio, "sceneHudAssociationService");
            assertTrue(associations.associate((games.pixscape.studio.document.SceneEditorDocument) scene, "hud/Screen"));
            context.world().process();
            flush(panel);
            int layer = context.layerService().getFirstLayerEntity();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().process();
            context.selectionService().selectOnly(entity);
            flush(panel);
            assertEquals(entity, field(panel, "boundEntity"));
            var filesBeforeInspection = snapshot(project.parent());

            clickLayer(studio, -1, 1);
            assertSceneHudLayer(panel);
            assertSame("Single click must keep the Scene document active", scene,
                    studio.getEditorDocumentManager().activeDocument());
            assertEquals(1, studio.getEditorDocumentManager().documents().size());
            assertEquals(entity, context.selectionService().getSelectionSnapshot().first());
            screenshot(studio, "scene-hud-layer");
            var rows = (Group) field(field(studio, "layersPanel"), "listTable");
            var hudRow = (Group) rows.getChildren().first();
            var visible = (com.badlogic.gdx.scenes.scene2d.ui.CheckBox) hudRow.getChildren().first();
            visible.setChecked(false);
            studio.getUiStage().act(0f);
            assertFalse(studio.isSceneHudCompositionVisible(context.sceneIdentity()));
            assertNull(panel.findActor("sceneHudLayerVisibility"));
            visible.setChecked(true);
            assertTrue(studio.isSceneHudCompositionVisible(context.sceneIdentity()));

            clickLayer(studio, layer, 1);
            assertEquals(layer, field(panel, "boundLayer"));
            assertNull(panel.findActor("resourceAssetScreenID"));
            clickLayer(studio, -1, 1);
            assertSceneHudLayer(panel);
            for (AssetNode.Kind kind : kinds()) {
                inspect(studio, kind);
                assertNotNull(panel.inspectedResourceProperties());
                clickLayer(studio, -1, 1); // HUD row was already selected in Layers.
                assertSceneHudLayer(panel);
                clickLayer(studio, layer, 1);
                inspect(studio, kind);
                clickLayer(studio, layer, 1); // Same active ECS layer, different Inspector target.
                assertEquals(layer, field(panel, "boundLayer"));
                assertNull(panel.inspectedResourceProperties());
            }
            clickLayer(studio, -1, 1);
            context.selectionService().selectOnly(entity);
            flush(panel);
            assertEquals(entity, field(panel, "boundEntity"));
            assertNull(panel.findActor("resourceAssetScreenID"));

            // A double click still opens the existing HUD editor, never the scene-layer view.
            clickLayer(studio, -1, 2);
            var hud = studio.getEditorDocumentManager().activeDocument();
            assertEquals(games.pixscape.studio.document.EditorDocumentType.HUD_SCREEN, hud.type());
            assertSame(field(panel, "hudInspectorView"), ((Group) field(panel, "contentHolder")).getChildren().first());
            assertNull(panel.findActor("resourceAssetScreenID"));
            studio.getEditorDocumentManager().activate(scene.key());
            flush(panel);
            clickLayer(studio, -1, 2);
            assertSame(hud, studio.getEditorDocumentManager().activeDocument());
            assertEquals(2, studio.getEditorDocumentManager().documents().size());
            assertEquals(filesBeforeInspection, snapshot(project.parent()));
            studio.getEditorDocumentManager().activate(scene.key());
            flush(panel);
            clickLayer(studio, -1, 1);
            assertTrue(studio.removeHudScreenAssociation(context));
            flush(panel);
            assertEquals("No HUD associated", hudDescription(panel).get("Status"));
            assertNull("An absent association must not retain the shared HUD card", panel.findActor("resourceAssetScreenID"));

            context.historyManager().undo();
            flush(panel);
            clickLayer(studio, -1, 1);
            assertSceneHudLayer(panel);
            FileHandle next = project(studio, root.child("next"));
            open(studio, next);
            flush(panel);
            assertNull(panel.findActor("resourceAssetScreenID"));
            assertNull(ProjectConfig.getInstance().getCurrentSceneMeta().defaultHudScreenId);
        });
    }

    private static void assertSceneHudLayer(PropertiesPanel panel) {
        assertFlatHudScreen(panel, true);
        assertNull(panel.inspectedResourceProperties());
        assertEquals("hud/Screen", hudDescription(panel).get("Screen ID"));
    }

    private static void clickLayer(StudioApplicationAdapter studio, int entityId, int taps) throws Exception {
        studio.render(); studio.render();
        Group rows = (Group) field(field(studio, "layersPanel"), "listTable");
        for (Actor actor : rows.getChildren()) {
            if (!(actor instanceof games.pixscape.studio.ui.layer.LayerRow row) || row.getEntityId() != entityId) continue;
            for (var listener : row.getListeners()) if (listener instanceof ClickListener click) click.setTapCount(0);
            Actor label = row.getChildren().get(2);
            var point = studio.getUiStage().stageToScreenCoordinates(label.localToStageCoordinates(
                    new com.badlogic.gdx.math.Vector2(label.getWidth() / 2f, label.getHeight() / 2f)));
            for (int i = 0; i < taps; i++) {
                assertTrue(studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, com.badlogic.gdx.Input.Buttons.LEFT));
                studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, com.badlogic.gdx.Input.Buttons.LEFT);
            }
            flush(inspector(studio));
            return;
        }
        fail("Missing layer row " + entityId);
    }

    @Test public void informationPreviewsReloadAndHudEditorRespectAuthoredState() throws Exception {
        withStudio((studio, root) -> {
            assertNull(ProjectConfig.getInstance().projectFileName);
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
            PropertiesPanel panel = inspector(studio);
            assertNull(panel.inspectedResourceProperties());
            FileHandle project = project(studio, root.child("first"));
            open(studio, project);
            var scene = studio.getEditorDocumentManager().activeDocument();
            var selection = studio.getSceneEditorContext().selectionService();
            int entity = studio.getSceneEditorContext().world().create();
            studio.getSceneEditorContext().world().getMapper(TransformComponent.class).create(entity);
            studio.getSceneEditorContext().world().process();
            selection.selectOnly(entity);
            flush(panel);
            var before = snapshot(project.parent());
            String availability = new com.badlogic.gdx.utils.Json().toJson(ProjectConfig.getInstance().getCurrentSceneMeta().runtimeAvailability);
            String catalog = studio.getSceneService().getAssetMetaDatabase().toSnapshotJson();
            var ownedTextures = new java.util.ArrayList<com.badlogic.gdx.graphics.Texture>();
            for (AssetNode.Kind kind : kinds()) {
                inspect(studio, kind);
                assertEquals(entity, selection.getSelectionSnapshot().first());
                assertNotNull(panel.inspectedResourceProperties());
                assertFalse(label(panel, "Name").isBlank());
                assertEquals(kind == AssetNode.Kind.HUD_SCREEN ? "hud/Screen" : kind == AssetNode.Kind.FONT ? "1" : "2",
                        label(panel, kind == AssetNode.Kind.HUD_SCREEN ? "Screen ID" : "ID"));
                if (kind == AssetNode.Kind.HUD_SCREEN) {
                    assertTrue(label(panel, "Dimensions").contains("viewport"));
                    assertEquals("None", label(panel, "Skin"));
                } else if (kind == AssetNode.Kind.FONT) {
                    assertEquals("BMFont", label(panel, "Format"));
                    assertNotNull(panel.inspectedResourceProperties().findActor("fontAssetPreview"));
                    var font = (com.badlogic.gdx.graphics.g2d.BitmapFont) field(panel.inspectedResourceProperties(), "previewFont");
                    ownedTextures.add(font.getRegion().getTexture());
                } else {
                    assertTrue(label(panel, "Styles").contains("LabelStyle / default"));
                    assertTrue(label(panel, "Resources").contains("BitmapFont / default-font"));
                    assertNotNull(panel.inspectedResourceProperties().findActor("skinAssetPreview"));
                    var skin = (com.badlogic.gdx.scenes.scene2d.ui.Skin) field(panel.inspectedResourceProperties(), "previewSkin");
                    ownedTextures.add(skin.getFont("default-font").getRegion().getTexture());
                }
                assertExistingDragPayload(studio, kind);
                screenshot(studio, kind.name().toLowerCase());
            }
            assertEquals("Inspection and preview must not write authored data", before, snapshot(project.parent()));
            assertEquals(catalog, studio.getSceneService().getAssetMetaDatabase().toSnapshotJson());
            assertEquals(availability, new com.badlogic.gdx.utils.Json().toJson(ProjectConfig.getInstance().getCurrentSceneMeta().runtimeAvailability));
            assertNull(ProjectConfig.getInstance().getCurrentSceneMeta().defaultHudScreenId);
            inspect(studio, AssetNode.Kind.HUD_SCREEN);
            for (var texture : ownedTextures) assertEquals("Owned preview textures must be disposed", 0, texture.getTextureObjectHandle());
            assertTrue("Shared UI fonts remain usable", com.kotcrab.vis.ui.VisUI.getSkin().getFont("default-font")
                    .getRegion().getTexture().getTextureObjectHandle() != 0);
            panel.inspectedResourceProperties().findActor("openHudAssetEditor").fire(new ChangeListener.ChangeEvent());
            assertNull(panel.inspectedResourceProperties());
            var hud = (games.pixscape.studio.document.HudScreenEditorDocument) studio.getEditorDocumentManager().activeDocument();
            assertSame(hud, studio.openHudScreen("hud/Screen"));
            studio.getEditorDocumentManager().activate(scene.key());
            hud.editSession().editSkin("Skin selection", "orig/skins/2/theme.json");
            assertTrue(hud.isDirty());
            inspect(studio, AssetNode.Kind.HUD_SCREEN);
            assertEquals("orig/skins/2/theme.json", label(panel, "Skin"));
            assertTrue(label(panel, "Source").contains("Open document"));
            hud.editSession().undo();
            flush(panel);
            assertEquals("None", label(panel, "Skin"));
            hud.editSession().redo();
            flush(panel);
            assertEquals("orig/skins/2/theme.json", label(panel, "Skin"));
            assertEquals("Open HUD inspection must not save", before, snapshot(project.parent()));
            Actor hudTile = selectedTile(studio, AssetNode.Kind.HUD_SCREEN);
            click(hudTile, 2);
            assertSame(hud, studio.getEditorDocumentManager().activeDocument());
            assertEquals(2, studio.getEditorDocumentManager().documents().size());
            assertNull(panel.inspectedResourceProperties());
            studio.getEditorDocumentManager().activate(scene.key());
            inspect(studio, AssetNode.Kind.HUD_SCREEN);
            studio.getEditorDocumentManager().closeNow(hud.key());
            flush(panel);
            assertEquals("Closing the unsaved HUD returns to persisted information", "None", label(panel, "Skin"));

            inspect(studio, AssetNode.Kind.FONT);
            Object oldFont = field(panel.inspectedResourceProperties(), "previewFont");
            FileHandle importFont = root.child("reimport-font/font.fnt");
            copyFont(importFont.parent());
            importFont.writeString(importFont.readString().replaceFirst("lineHeight=[0-9]+", "lineHeight=42"), false);
            studio.getSceneService().reimportBitmapFontAsset(1, "orig/fonts/1/font.fnt", project.parent(), importFont);
            assertEquals("42.0", label(panel, "Lineheight"));
            assertNotSame(oldFont, field(panel.inspectedResourceProperties(), "previewFont"));

            inspect(studio, AssetNode.Kind.SKIN);
            FileHandle importSkin = root.child("reimport-skin/theme.json");
            writeSkin(importSkin, "alternate");
            studio.getSceneService().reimportScene2dSkinAsset(2, "orig/skins/2/theme.json", project.parent(), importSkin);
            assertTrue(label(panel, "Styles").contains("LabelStyle / alternate"));
        });
    }

    @Test public void explicitSelectionAndConfirmedDeletionControlTheInspectionTarget() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("first"));
            open(studio, project);
            PropertiesPanel panel = inspector(studio);
            var context = studio.getSceneEditorContext();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().process();
            int layer = context.layerService().getFirstLayerEntity();
            context.selectionService().selectOnly(entity);
            flush(panel);
            for (AssetNode.Kind kind : kinds()) {
                inspect(studio, kind);
                ResourceAssetProperties view = panel.inspectedResourceProperties();
                context.selectionService().clearSelection();
                context.selectionService().selectOnly(entity);
                flush(panel);
                assertNull(panel.inspectedResourceProperties());
                assertNull(field(view, "previewFont"));
                assertNull(field(view, "previewSkin"));
                inspect(studio, kind);
                EventFlow.i().publish(new EventFlow.CurrentLayerChanged(layer,
                        games.pixscape.studio.service.SelectionService.SelectionSource.TREE, -555));
                flush(panel);
                assertNull(panel.inspectedResourceProperties());
                inspect(studio, kind);
                AssetNode selected = (AssetNode) selectedTile(studio, kind).getUserObject();
                showDelete(studio, selected);
                respond(lastDialog(studio), false);
                assertSame(view, panel.inspectedResourceProperties());
                assertEquals(selected.kind == AssetNode.Kind.HUD_SCREEN ? "hud/Screen" : Integer.toString(selected.assetId),
                        label(panel, selected.kind == AssetNode.Kind.HUD_SCREEN ? "Screen ID" : "ID"));
                // A different HUD's successful deletion must not steal the current inspection.
                String other = new HudScreenAssetAuthoringService().create(project.parent(), "Other");
                showDelete(studio, new AssetNode(AssetNode.Kind.HUD_SCREEN, AssetNode.Root.HUD, other, "Other", null));
                respond(lastDialog(studio), true);
                assertSame(view, panel.inspectedResourceProperties());
                var saved = snapshot(project.parent());
                showDelete(studio, selected);
                respond(lastDialog(studio), true);
                flush(panel);
                assertNull("Confirmed deletion must leave asset inspection", panel.inspectedResourceProperties());
                assertEquals(entity, field(panel, "boundEntity"));
                restore(project.parent(), saved);
                // Restore through the project catalog and browser refresh, without selecting the restored asset.
                var databaseField = SceneService.class.getDeclaredField("assetMetaDatabase");
                databaseField.setAccessible(true);
                databaseField.set(studio.getSceneService(), AssetMetaDatabase.load(project.parent().child("assets.json")));
                studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
                flush(panel);
                assertNull("Restoration must not reselect the Inspector target", panel.inspectedResourceProperties());
            }
            // Persisted HUD references must also keep Font/Skin deletion refusals in the current view.
            var protectedAsset = new games.pixscape.runtime.hud.HudScreenAsset();
            protectedAsset.documentId = "hud/Protected.json";
            protectedAsset.skinId = "orig/skins/2/theme.json";
            var rootNode = new games.pixscape.runtime.hud.document.HudNode("root", games.pixscape.runtime.hud.document.HudNodeKind.GROUP);
            var text = new games.pixscape.runtime.hud.document.HudNode("text", games.pixscape.runtime.hud.document.HudNodeKind.LABEL);
            text.label = new games.pixscape.runtime.hud.document.HudLabelData();
            text.label.text = "Font reference";
            text.label.fontAssetId = 1;
            rootNode.children.add(games.pixscape.runtime.hud.document.HudChild.direct(text));
            try (var protectedDocument = new games.pixscape.studio.document.HudScreenEditorDocument("hud/Protected", "Protected",
                    protectedAsset, new games.pixscape.runtime.hud.document.HudDocumentV1(rootNode))) {
                new games.pixscape.studio.service.hud.HudDocumentPersistenceService().save(project.parent(), protectedDocument);
            }
            for (var kind : new AssetNode.Kind[]{AssetNode.Kind.FONT, AssetNode.Kind.SKIN}) {
                inspect(studio, kind);
                showDelete(studio, (AssetNode) selectedTile(studio, kind).getUserObject());
                respond(lastDialog(studio), true);
                assertEquals("Asset In Use", lastDialog(studio).getTitleLabel().getText().toString());
                lastDialog(studio).remove();
                assertNotNull(panel.inspectedResourceProperties());
            }
            new games.pixscape.studio.service.hud.HudScreenDeletionService().delete(project.parent(), ProjectConfig.getInstance(), "hud/Protected");
            inspect(studio, AssetNode.Kind.HUD_SCREEN);
            ProjectConfig.getInstance().getCurrentSceneMeta().defaultHudScreenId = "hud/Screen";
            showDelete(studio, (AssetNode) selectedTile(studio, AssetNode.Kind.HUD_SCREEN).getUserObject());
            assertTrue(lastDialog(studio).getTitleLabel().getText().toString().contains("In Use"));
            assertNotNull(panel.inspectedResourceProperties());
            lastDialog(studio).remove();
            ProjectConfig.getInstance().getCurrentSceneMeta().defaultHudScreenId = null;
            context.selectionService().clearSelection();
            flush(panel);
            inspect(studio, AssetNode.Kind.SKIN);
            showDelete(studio, (AssetNode) selectedTile(studio, AssetNode.Kind.SKIN).getUserObject());
            respond(lastDialog(studio), true);
            flush(panel);
            assertEquals("Without an entity, return to the current layer", layer, field(panel, "boundLayer"));
            studio.getEditorDocumentManager().clear();
            inspect(studio, AssetNode.Kind.FONT);
            showDelete(studio, (AssetNode) selectedTile(studio, AssetNode.Kind.FONT).getUserObject());
            respond(lastDialog(studio), true);
            flush(panel);
            assertTrue("Without a document, deletion returns to the empty Inspector",
                    ((Group) field(panel, "contentHolder")).getChildren().isEmpty());
        });
    }

    @Test public void everyResourceReleasesItsReferencesAcrossProjectChangesAndUnload() throws Exception {
        withStudio((studio, root) -> {
            FileHandle first = project(studio, root.child("first"));
            FileHandle next = project(studio, root.child("next"));
            for (AssetNode.Kind kind : kinds()) {
                open(studio, first);
                inspect(studio, kind);
                ResourceAssetProperties view = inspector(studio).inspectedResourceProperties();
                open(studio, next);
                assertNull(inspector(studio).inspectedResourceProperties());
                assertReleased(view);
                inspect(studio, kind);
                ProjectConfig cfg = ProjectConfig.getInstance();
                studio.getEditorDocumentManager().clear();
                flush(inspector(studio));
                assertSame(cfg, ProjectConfig.getInstance());
                assertNotNull("Assets remain inspectable without a document", inspector(studio).inspectedResourceProperties());
                studio.getSceneService().unloadProjectToEmptyEditor();
                flush(inspector(studio));
                assertNull(inspector(studio).inspectedResourceProperties());
                assertReleased(view);
                assertTrue(((Group) field(inspector(studio), "contentHolder")).getChildren().isEmpty());
                studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
            }
        });
    }

    private static void assertReleased(ResourceAssetProperties view) throws Exception {
        for (String name : new String[]{"previewFont", "previewSkin", "displayedHud", "displayedMeta", "screenId", "kind"})
            assertNull(name, field(view, name));
    }

    private static AssetNode.Kind[] kinds() {
        return new AssetNode.Kind[]{AssetNode.Kind.HUD_SCREEN, AssetNode.Kind.FONT, AssetNode.Kind.SKIN};
    }

    @FunctionalInterface private interface Scenario {
        void run(StudioApplicationAdapter studio, FileHandle root) throws Exception;
    }

    private static void withStudio(Scenario scenario) throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_DOCUMENT_GL_TEST")));
        String previousHome = System.getProperty("user.home");
        ProjectConfig previous = ProjectConfig.getInstance();
        FileHandle root = new FileHandle(Files.createTempDirectory("resource-inspection").toFile());
        System.setProperty("user.home", root.path());
        ProjectConfig.setInstance(new ProjectConfig());
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Resource asset inspection regression");
        gl.setInitialVisible(false);
        gl.setWindowedMode(1000, 700);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        gl.disableAudio(true);
        try (var globals = new StudioGlTestScope()) {
            // LWJGL frees its callback on exit but leaves the native pointer installed.
            org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio;
                @Override public void create() {
                    try {
                        studio = new StudioApplicationAdapter() {
                            @Override public void saveActiveDocumentWithProgress(Runnable ok, Consumer<Throwable> error) {
                                fail("No must never invoke saving");
                            }
                        };
                        studio.create();
                    } catch (Throwable ex) {
                        failure[0] = ex;
                        Gdx.app.exit();
                    }
                }
                @Override public void render() {
                    if (failure[0] != null) return;
                    try {
                        studio.render();
                        scenario.run(studio, root);
                        studio.render();
                    } catch (Throwable ex) { failure[0] = ex; }
                    finally { Gdx.app.exit(); }
                }
                @Override public void dispose() {
                    try { if (studio != null) studio.dispose(); }
                    catch (Throwable ex) {
                        if (failure[0] == null) failure[0] = ex;
                        else failure[0].addSuppressed(ex);
                    }
                }
            }, gl);
        } finally {
            org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
            System.setProperty("user.home", previousHome);
            ProjectConfig.setInstance(previous);
            root.deleteDirectory();
        }
        if (failure[0] != null) throw new AssertionError("Resource asset inspection failed", failure[0]);
    }


    private static FileHandle project(StudioApplicationAdapter studio, FileHandle dir) {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectDirectoryPath = dir.path(); cfg.projectFileName = "project"; cfg.projectTitle = "Resources";
        cfg.exportRootPathDir = dir.child("export").path(); cfg.createSceneMeta("Scene");
        var context = studio.getCanvas().createSceneContext(cfg.canonicalSceneTagCurrent(), cfg.getCurrentSceneMeta());
        context.layerService().addLayerTop("Layer"); context.world().process();
        SceneService.saveScene(context.world(), dir.child("scenes/scene1.json"), false);
        studio.getCanvas().releaseSceneContext(context); context.dispose();
        AssetMetaDatabase db = new AssetMetaDatabase();
        db.registerIfAbsent(AssetType.FONT, "fonts/Body", "orig/fonts/1/font.fnt", AssetMeta.AssetScope.USER);
        db.registerIfAbsent(AssetType.SKIN, "skins/Theme", "orig/skins/2/theme.json", AssetMeta.AssetScope.USER);
        copyFont(dir.child("orig/fonts/1"));
        writeSkin(dir.child("orig/skins/2/theme.json"), "default");
        db.save(dir.child("assets.json"));
        new HudScreenAssetAuthoringService().create(dir, "Screen");
        FileHandle file = dir.child("project.json"); ProjectConfig.ProjectIO.saveProject(cfg, file); return file;
    }

    private static void copyFont(FileHandle dir) {
        Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt").copyTo(dir.child("font.fnt"));
        Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.png").copyTo(dir.child("lsans-15.png"));
    }

    private static void writeSkin(FileHandle file, String style) {
        copyFont(file.parent());
        file.writeString("{\"com.badlogic.gdx.graphics.g2d.BitmapFont\":{\"default-font\":{\"file\":\"font.fnt\"}},"
                + "\"com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle\":{\"" + style
                + "\":{\"font\":\"default-font\"}}}", false);
    }

    private static void open(StudioApplicationAdapter studio, FileHandle file) {
        studio.getSceneService().tryOpenProject(file, "resource test").ifPresent(failure -> {
            throw new AssertionError(failure.message(), failure.cause());
        });
    }

    private static void inspect(StudioApplicationAdapter studio, AssetNode.Kind kind) throws Exception {
        flush(inspector(studio));
        AssetNode.Root root = kind == AssetNode.Kind.FONT ? AssetNode.Root.FONTS
                : kind == AssetNode.Kind.SKIN ? AssetNode.Root.SKINS : AssetNode.Root.HUD;
        var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
        tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, root, "", "", null));
        click(selectedTile(studio, kind), 1);
        flush(inspector(studio));
    }

    private static Actor selectedTile(StudioApplicationAdapter studio, AssetNode.Kind kind) throws Exception {
        var thumbs = field(studio.getAssetsPanel(), "thumbsView");
        for (Object object : (Array<?>) field(thumbs, "tileWidgets")) {
            Actor actor = (Actor) object;
            if (actor.getUserObject() instanceof AssetNode node && node.kind == kind) return actor;
        }
        throw new AssertionError("Missing thumbnail " + kind);
    }

    private static void click(Actor actor, int taps) {
        for (var listener : actor.getListeners()) if (listener instanceof ClickListener click) {
            click.setTapCount(taps); click.clicked(new InputEvent(), 0, 0); return;
        }
        fail("Missing click listener");
    }

    private static void assertExistingDragPayload(StudioApplicationAdapter studio, AssetNode.Kind kind) throws Exception {
        Object thumbs = field(studio.getAssetsPanel(), "thumbsView");
        var build = AssetsThumbsView.class.getDeclaredMethod("buildPayload", AssetNode.class);
        build.setAccessible(true);
        var payload = (games.pixscape.studio.ui.asset.dnd.DragPayload) build.invoke(thumbs, selectedTile(studio, kind).getUserObject());
        if (kind == AssetNode.Kind.HUD_SCREEN) {
            assertEquals("hud-screen", payload.type);
            assertEquals("hud/Screen", payload.path);
            var reset = AssetsThumbsView.class.getDeclaredMethod("resetCursor");
            reset.setAccessible(true); reset.invoke(null);
            if (payload.ghostPixmap != null) payload.ghostPixmap.dispose();
        } else assertNull("Font/Skin did not previously define a drag payload", payload);
    }

    private static void showDelete(StudioApplicationAdapter studio, AssetNode node) throws Exception {
        Object thumbs = field(studio.getAssetsPanel(), "thumbsView");
        boolean hud = node.kind == AssetNode.Kind.HUD_SCREEN;
        var method = AssetsThumbsView.class.getDeclaredMethod(hud ? "showDeleteHudScreenDialog" : "showDeleteAssetDialog",
                hud ? AssetNode.class : Array.class);
        method.setAccessible(true); method.invoke(thumbs, hud ? node : Array.with(node));
    }

    private static VisDialog lastDialog(StudioApplicationAdapter studio) {
        VisDialog last = null;
        for (Actor actor : studio.getUiStage().getActors()) if (actor instanceof VisDialog dialog) last = dialog;
        assertNotNull(last); return last;
    }

    private static void respond(VisDialog dialog, boolean yes) throws Exception {
        var method = dialog.getClass().getDeclaredMethod("result", Object.class);
        method.setAccessible(true); method.invoke(dialog, yes); dialog.remove();
    }

    private static void screenshot(StudioApplicationAdapter studio, String name) {
        studio.render(); studio.render();
        int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        image.getPixels().put(pixels); image.getPixels().flip();
        PixmapIO.writePNG(Gdx.files.local("build/local-validation/resource-inspector-" + name + ".png"), image);
        image.dispose();
    }

    private static void screenshotBottom(StudioApplicationAdapter studio, String name) throws Exception {
        Actor content = (Actor) field(inspector(studio), "contentHolder");
        var scroller = (com.badlogic.gdx.scenes.scene2d.ui.ScrollPane) content.getParent();
        scroller.setScrollPercentY(1f);
        scroller.updateVisualScroll();
        screenshot(studio, name);
        scroller.setScrollPercentY(0f);
        scroller.updateVisualScroll();
    }

    private static java.util.Map<String, String> snapshot(FileHandle directory) throws Exception {
        var root = directory.file().toPath(); var result = new java.util.TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(Files::isRegularFile).toList())
                result.put(root.relativize(path).toString(), java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
        }
        return result;
    }

    private static java.util.Map<String, String> authoredSnapshot(FileHandle directory) throws Exception {
        var files = snapshot(directory);
        // Rendering an open HUD may prepare its atlas in this disposable work directory.
        files.keySet().removeIf(path -> path.replace('\\', '/').startsWith("atlases/.tmp/"));
        return files;
    }

    private static void restore(FileHandle dir, java.util.Map<String, String> saved) {
        saved.forEach((path, bytes) -> dir.child(path).writeBytes(java.util.Base64.getDecoder().decode(bytes), false));
    }

    private static PropertiesPanel inspector(StudioApplicationAdapter studio) throws Exception {
        return (PropertiesPanel) field(studio, "propertiesPanel");
    }
    private static String label(PropertiesPanel panel, String label) {
        return ((VisLabel) panel.inspectedResourceProperties().findActor("resourceAsset" + label.replace(" ", ""))).getText().toString();
    }
    private static void flush(PropertiesPanel panel) { EventFlow.i().flush(); panel.updateIfDirty(); }
    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(object);
            } catch (NoSuchFieldException missing) { }
        }
        throw new NoSuchFieldException(name);
    }
}
