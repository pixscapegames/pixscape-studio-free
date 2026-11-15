package games.pixscape.studio.ui.main;

import com.artemis.Aspect;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import games.pixscape.runtime.component.*;
import games.pixscape.studio.configuration.EditorSettings;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.ui.tree.EntityNode;
import games.pixscape.studio.ui.tree.IdVisTree;
import org.junit.Test;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

public class ItemsVisibilityGlTest {
    @Test
    public void importedLayerEyesAreIndependentAndGridComposesBeforeTools() throws Exception {
        withStudio(900, (studio, root) -> {
            FileHandle file = project(studio, root.child("visibility"));
            open(studio, file);
            var result = studio.getSceneService().importTmxAsNewScene(new TmxSceneImportRequest(
                    new FileHandle("src/test/resources/tmx/mixed-layers/mixed-layers.tmx"), "Mixed Layers", true));
            assertTrue(String.valueOf(result.failure()), result.imported());
            settle(studio);
            var context = studio.getSceneEditorContext();
            var world = context.world();
            var layers = context.layerService();
            var ids = context.identityRegistry();
            int map = world.getAspectSubscriptionManager().get(Aspect.all(TiledMapComponent.class)).getEntities().get(0);
            int top = ids.firstByName("Top blue");
            int objects = ids.firstByName("Middle green");
            int image = ids.firstByName("Middle yellow");
            assertTrue(top >= 0 && objects >= 0 && image >= 0);
            IdVisTree tree = (IdVisTree) field(studio.getItemTreePanel(), "tree");
            tree.findNode(map).setExpanded(true);
            tree.findNode(objects).setExpanded(true);
            settle(studio);
            Actor column = studio.getItemTreePanel().findActor("items-visibility");
            assertNotNull(column);
            assertEquals(26, column.getWidth(), 0);
            ScrollPane scroller = (ScrollPane) field(studio.getItemTreePanel(), "scroller");
            scroller.setForceScroll(true, true);
            scroller.invalidateHierarchy();
            settle(studio);
            float rightPadding = scroller.getStyle().background != null
                    ? scroller.getStyle().background.getRightWidth() : 0;
            assertEquals("Eyes precede the vertical scrollbar", scroller.getRight() - rightPadding
                    - scroller.getScrollBarWidth(), column.getRight(), .01f);
            assertEquals(scroller.getScrollHeight(), column.getHeight(), .01f);
            float columnX = column.getX();
            scroller.setScrollX(scroller.getMaxX());
            scroller.updateVisualScroll();
            settle(studio);
            assertEquals("Horizontal scrolling leaves eyes fixed", columnX, column.getX(), .01f);
            screenshot(studio, "items-eyes-before-scrollbar");
            scroller.setScrollX(0);
            scroller.updateVisualScroll();
            scroller.setForceScroll(false, false);
            scroller.invalidateHierarchy();
            settle(studio);
            var selected = context.selectionService().getSelectionSnapshot().toArray();
            int cursor = context.historyManager().getCursor();
            eye(studio, column, tree.findNode(top));
            assertFalse(layers.isTiledEditorVisible(top));
            assertArrayEquals(selected, context.selectionService().getSelectionSnapshot().toArray());
            assertEquals(cursor, context.historyManager().getCursor());
            assertTrue(tree.findNode(map).isExpanded());
            assertNull(field(field(studio.getItemTreePanel(), "itemMoves"), "prepared"));
            eye(studio, column, tree.findNode(map));
            assertFalse(layers.isPublishedContentVisible(image));
            assertFalse(context.selectionService().isSelectableInPublishedViewport(objects));
            eye(studio, column, tree.findNode(image)); // Individual choice remains editable under a hidden parent.
            screenshot(studio, "items-hidden-parent");
            eye(studio, column, tree.findNode(map));
            assertFalse(layers.isPublishedContentVisible(top));
            assertFalse(layers.isPublishedContentVisible(image));
            assertTrue(layers.isPublishedContentVisible(objects));
            eye(studio, column, tree.findNode(top));
            eye(studio, column, tree.findNode(image));
            assertTrue(world.getMapper(VisibilityComponent.class).get(top).visible);
            int sceneLayer = layers.getEffectiveLayerEntity(map);
            layers.setLayerVisible(sceneLayer, false);
            settle(studio);
            assertFalse(context.selectionService().isSelectableInPublishedViewport(image));
            layers.setLayerVisible(sceneLayer, true);
            settle(studio);
            // Ordinary row clicks and disclosure still use the displaced tree's native coordinates.
            click(studio, tree.findNode(image).getActor(), 8, 8);
            assertEquals(image, context.selectionService().getFirstSelectedEntityId());
            tree.findNode(map).setExpanded(false);
            settle(studio);
            tree.findNode(map).setExpanded(true);
            settle(studio);
            assertTrue(tree.findNode(map).isExpanded());

            studio.getCanvas().focusCameraAt(16, 16);
            var camera = (OrthographicCamera) studio.getCanvas().getGridStage().getCamera();
            var onTop = (VisCheckBox) studio.getUiStage().getRoot().findActor("editor-grid-on-top");
            assertNotNull(onTop);
            assertFalse(onTop.isChecked());
            for (StudioEditingMode mode : new StudioEditingMode[]{StudioEditingMode.NORMAL, StudioEditingMode.TILED}) {
                if (mode == StudioEditingMode.TILED) {
                    context.selectionService().setTiledMapEditingTarget(top, SelectionService.SelectionSource.TREE);
                }
                studio.getCanvas().getStudioEditingModeService().setMode(mode, 0);
                for (float zoom : new float[]{.125f, .25f}) {
                    camera.zoom = zoom;
                    camera.position.add(2, 3, 0);
                    onTop.setChecked(false);
                    settle(studio);
                    Pixmap behind = captureWorld(studio);
                    onTop.setChecked(true);
                    settle(studio);
                    Pixmap above = captureWorld(studio);
                    try {
                        int changed = 0;
                        for (int y = 0; y < behind.getHeight(); y++) {
                            for (int x = 0; x < behind.getWidth(); x++) {
                                if (behind.getPixel(x, y) != above.getPixel(x, y)) {
                                    changed++;
                                }
                            }
                        }
                        assertTrue(mode + " must show grid over opaque tiles at zoom " + zoom, changed > 20);
                    } finally {
                        behind.dispose();
                        above.dispose();
                    }
                }
                screenshot(studio, "grid-on-top-" + mode);
            }
            layers.setTiledEditorVisible(top, false);
            var saved = root.child("scene-visibility.json");
            SceneService.saveScene(world, saved, false);
            String json = saved.readString();
            assertFalse(json.contains("hiddenTiledItems"));
            assertFalse(json.contains("gridOnTop"));
            EditorSettings.load();
            assertTrue(EditorSettings.get().gridOnTop);
            layers.bindEditorVisibility(file.parent().path(), context.sceneMeta().file);
            assertFalse(layers.isTiledEditorVisible(top));
            int topStable = ids.getStableId(top);
            for (int cycle = 0; cycle < 2; cycle++) {
                context.historyManager().undo(); // Undo the normal TMX import.
                settle(studio);
                assertEquals(-1, ids.findByStableId(topStable));
                context.historyManager().redo();
                settle(studio);
                top = ids.findByStableId(topStable);
                assertTrue(top >= 0);
                assertFalse(layers.isTiledEditorVisible(top));
                assertFalse(context.selectionService().isSelectableInPublishedViewport(top));
            }
            studio.getSceneService().changeSceneNow("Scene");
            settle(studio);
            studio.getSceneService().changeSceneNow("Mixed Layers");
            settle(studio);
            assertSame(context, studio.getSceneEditorContext());
            assertFalse(layers.isTiledEditorVisible(top));
            SceneService.saveScene(world, file.parent().child("scenes/" + context.sceneMeta().file), false);
            var cfg = games.pixscape.studio.configuration.ProjectConfig.getInstance();
            games.pixscape.studio.configuration.RuntimeExport.exportRuntime(cfg, file.parent(), root.child("export"));
            var exported = root.child("export/pixscape-project/scenes/" + context.sceneMeta().file);
            assertTrue(exported.path(), exported.exists());
            assertFalse(exported.readString().contains("hiddenTiledItems"));
            assertTrue(world.getMapper(VisibilityComponent.class).get(top).visible);
        });
    }

    private static Pixmap captureWorld(StudioApplicationAdapter studio) {
        var view = studio.getCanvas().getGridStage().getViewport();
        return Pixmap.createFromFrameBuffer(view.getScreenX(), view.getScreenY(),
                view.getScreenWidth(), view.getScreenHeight());
    }

    private static void eye(StudioApplicationAdapter studio, Actor column, EntityNode node) {
        Vector2 row = node.getActor().localToStageCoordinates(new Vector2(0, node.getActor().getHeight() / 2));
        column.stageToLocalCoordinates(row);
        click(studio, column, column.getWidth() / 2, row.y);
    }

    private static void click(StudioApplicationAdapter studio, Actor actor, float x, float y) {
        Vector2 point = studio.getUiStage().stageToScreenCoordinates(actor.localToStageCoordinates(new Vector2(x, y)));
        assertTrue(studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT));
        studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
        settle(studio);
    }

    private static void settle(StudioApplicationAdapter studio) {
        studio.render();
        studio.render();
    }
}
