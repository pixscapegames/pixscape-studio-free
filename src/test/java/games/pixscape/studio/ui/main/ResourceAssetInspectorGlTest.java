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
    @Test public void tileAnimationInternalMovesAreAtomicAndPreserveOccurrences() throws Exception {
        withStudio(1000, (studio, root) -> {
            var file = project(studio, root.child("internal-moves")); int[] tiles = tileFixtures(file.parent());
            open(studio, file); studio.render(); studio.render();
            var service = studio.getSceneService(); var panel = inspector(studio);
            int id = service.createEmptyTileAnimation("Move occurrences");
            int[] frames = {tiles[0], tiles[1], tiles[0], tiles[1], tiles[0]};
            service.insertTileAnimationFrames(id, 0, frames);
            for (int i = 0; i < 5; i++) service.updateTileAnimationFrameDuration(id, i, 100 + i);
            int[] originalDurations = {100, 101, 102, 103, 104};
            studio.editTileAnimation(id);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.95f);
            var split = (games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "centerRightSplit");
            for (float width : new float[]{.45f, .72f}) {
                split.setSplitAmount(width);
                for (int[] scenario : new int[][]{{0, 4}, {4, 0}, {3, 1, 3}, {3, 1, 0}, {2, 1, 2}}) {
                    int[] moving = java.util.Arrays.copyOf(scenario, scenario.length - 1);
                    int destination = scenario[scenario.length - 1];
                    frameClick(studio, moving[0], false);
                    for (int i = 1; i < moving.length; i++) {
                        int occurrence = moving[i];
                        withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> frameClick(studio, occurrence, false));
                    }
                    java.util.Arrays.sort(moving);
                    int cursor = service.tileAnimationHistory().getCursor();
                    internalMove(studio, moving[0], destination, false, null, "move-" + width + "-" + destination);
                    int[] expected = new int[5]; int rest = 0, moved = 0;
                    for (int i = 0; i < 5; i++) {
                        if (java.util.Arrays.binarySearch(moving, i) >= 0) expected[destination + moved++] = originalDurations[i];
                        else { expected[rest + (rest >= destination ? moving.length : 0)] = originalDurations[i]; rest++; }
                    }
                    assertArrayEquals(expected, service.findTileAnimationProjectDef(id).frameDurationsMs);
                    assertEquals(cursor + 1, service.tileAnimationHistory().getCursor());
                    int[] selection = new int[moving.length]; for (int i = 0; i < selection.length; i++) selection[i] = destination + i;
                    assertArrayEquals(selection, frameSelection(panel));
                    var saved = games.pixscape.studio.io.TileAnimationsIO.load(file.parent().child(games.pixscape.runtime.helper.RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
                    assertArrayEquals(expected, saved.animations.first().frameDurationsMs);
                    assertTrue(studio.undoActiveDocument()); flush(panel);
                    assertArrayEquals(frames, service.findTileAnimationProjectDef(id).frameAssetIds);
                    assertArrayEquals(originalDurations, service.findTileAnimationProjectDef(id).frameDurationsMs);
                    assertTrue(studio.redoActiveDocument()); flush(panel);
                    assertArrayEquals(expected, service.findTileAnimationProjectDef(id).frameDurationsMs);
                    assertTrue(studio.undoActiveDocument()); flush(panel);
                }
            }
            // Dragging an unselected occurrence replaces the old selection only when the drag starts.
            frameClick(studio, 0, false); internalMove(studio, 4, 0, false, null, null);
            assertArrayEquals(new int[]{104, 100, 101, 102, 103}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            assertArrayEquals(new int[]{0}, frameSelection(panel));
            assertTrue(studio.undoActiveDocument()); flush(panel);
            int before = service.tileAnimationHistory().getCursor();
            frameClick(studio, 1, false);
            withKey(com.badlogic.gdx.Input.Keys.SHIFT_LEFT, () -> frameClick(studio, 2, false));
            internalMove(studio, 1, 1, false, null, null); // Same order.
            assertEquals(before, service.tileAnimationHistory().getCursor());
            assertArrayEquals(originalDurations, service.findTileAnimationProjectDef(id).frameDurationsMs);
            internalMove(studio, 1, 3, true, null, null);
            assertEquals(before, service.tileAnimationHistory().getCursor());
            internalMove(studio, 1, 3, false, () -> studio.getUiStage().touchUp(-10, -10, 0, 0), null);
            assertEquals(before, service.tileAnimationHistory().getCursor());
            internalMove(studio, 1, 3, false, () -> studio.getUiStage().keyDown(com.badlogic.gdx.Input.Keys.ESCAPE), null);
            assertEquals(before, service.tileAnimationHistory().getCursor());
            // The numeric field owns its drag (text selection), never a frame move.
            var duration = panel.findActor("tileAnimationDuration0"); clickActor(studio, duration);
            var stage = studio.getUiStage();
            var p = stage.stageToScreenCoordinates(duration.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(8, 8)));
            stage.touchDown((int)p.x, (int)p.y, 0, 0); stage.touchDragged((int)p.x + 70, (int)p.y, 0);
            assertNull(field(panel.inspectedTileAnimationProperties(), "movingSource")); stage.cancelTouchFocus();
            assertEquals(before, service.tileAnimationHistory().getCursor());
            // Any intervening publication invalidates captured occurrence indices.
            frameClick(studio, 1, false);
            internalMove(studio, 1, 3, false, () -> service.updateTileAnimationFrameDuration(id, 0, 777), null);
            assertEquals(before + 1, service.tileAnimationHistory().getCursor());
            assertArrayEquals(new int[]{777, 101, 102, 103, 104}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            // Project replacement tears down the gesture before a late source release can commit.
            var savedBefore = snapshot(file.parent());
            frameClick(studio, 1, false);
            internalMove(studio, 1, 3, false, () -> open(studio, project(studio, root.child("after-move"))), null);
            assertEquals(savedBefore, snapshot(file.parent()));
            assertNull(panel.inspectedTileAnimationProperties());
        });
    }

    private static void internalMove(StudioApplicationAdapter studio, int sourceIndex, int destination,
                                     boolean cancel, InputAction duringHover, String capture) throws Exception {
        var panel = inspector(studio); var card = panel.inspectedTileAnimationProperties();
        var sequence = (games.pixscape.studio.ui.widget.InsertionPreviewGrid) panel.findActor("tileAnimationSequence");
        studio.render(); studio.render();
        var stage = studio.getUiStage(); var def = studio.getSceneService().findTileAnimationProjectDef(card.assetId());
        int[] selection = frameSelection(panel);
        if (java.util.Arrays.binarySearch(selection, sourceIndex) < 0) selection = new int[]{sourceIndex};
        Actor source = panel.findActor("tileAnimationFrame" + sourceIndex);
        var start = stage.stageToScreenCoordinates(source.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(20, source.getHeight() - 20)));
        int[] beforePress = frameSelection(panel);
        assertTrue(stage.touchDown((int)start.x, (int)start.y, 0, 0));
        assertArrayEquals(beforePress, frameSelection(panel));
        stage.touchDragged((int)start.x + 8, (int)start.y, 0);
        studio.render(); studio.render();
        assertArrayEquals(selection, frameSelection(panel));
        assertNotNull(field(card, "movingSource"));
        var remaining = new java.util.ArrayList<Actor>();
        for (int i = 0; i < def.frameAssetIds.length; i++) {
            Actor cell = panel.findActor("tileAnimationFrame" + i);
            if (java.util.Arrays.binarySearch(selection, i) < 0) { remaining.add(cell); assertTrue(cell.isVisible()); }
            else assertFalse(cell.isVisible());
        }
        boolean end = destination == remaining.size();
        Actor targetCell = end ? remaining.get(remaining.size() - 1) : remaining.get(destination);
        var target = stage.stageToScreenCoordinates(targetCell.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(
                end ? targetCell.getWidth() - 4 : 4, targetCell.getHeight() - 20)));
        stage.touchDragged((int)target.x, (int)target.y, 0);
        for (int i = 0; i < 5; i++) { studio.render(); assertEquals(destination, sequence.insertionIndex()); }
        assertSame(def, studio.getSceneService().findTileAnimationProjectDef(card.assetId()));
        if (capture != null) screenshot(studio, capture);
        if (duringHover != null) duringHover.run();
        if (cancel) stage.cancelTouchFocus(); else stage.touchUp((int)target.x, (int)target.y, 0, 0);
        studio.render(); flush(panel);
        assertNull(field(card, "movingSource")); assertEquals(-1, sequence.insertionIndex());
        if (panel.inspectedTileAnimationProperties() == card)
            for (int i = 0; i < def.frameAssetIds.length; i++) assertTrue(panel.findActor("tileAnimationFrame" + i).isVisible());
    }

    @Test public void tileAnimationBrowserShowsTerminalAssetsAndExplicitlyOpensInspectorEditing() throws Exception {
        withStudio(1000, (studio, root) -> {
            var file = project(studio, root.child("terminal-animation")); int[] tiles = tileFixtures(file.parent());
            open(studio, file); studio.render(); studio.render();
            var service = studio.getSceneService(); var panel = inspector(studio);
            int id = service.createEmptyTileAnimation("Water"); service.insertTileAnimationFrames(id, 0, new int[]{tiles[0], tiles[1], tiles[0]});
            service.updateTileAnimationFrameDuration(id, 1, 123);
            studio.getAssetsPanel().selectTiledAnimationById(id); studio.render(); studio.render();
            var thumbs = field(studio.getAssetsPanel(), "thumbsView");
            var assets = (Array<AssetNode>) field(thumbs, "currentAssets");
            assertEquals(1, assets.size); assertEquals(AssetNode.Kind.TILED_ANIMATION, assets.first().kind);
            assertEquals(id, studio.getAssetsPanel().getSelectedAssetNode().tileAnimationId);
            assertFalse(panel.inspectedTileAnimationProperties().isEditing());
            var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
            var widget = (com.kotcrab.vis.ui.widget.VisTree) field(tree, "tree");
            var node = tileAnimationsRoot(widget.getRootNodes()); assertNotNull(node);
            assertEquals("Individual animations only belong in the gallery", 0, node.getChildren().size);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rootSplit")).setSplitAmount(.6f);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.95f);
            screenshot(studio, "terminal-animation-browser");
            Actor tile = selectedTile(studio, AssetNode.Kind.TILED_ANIMATION);
            clickActor(studio, tile); assertFalse(panel.inspectedTileAnimationProperties().isEditing());
            clickActor(studio, tile); assertTrue(panel.inspectedTileAnimationProperties().isEditing());
            assertArrayEquals(new int[]{300, 123, 300}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            screenshot(studio, "terminal-animation-edit");
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            assertFalse(panel.inspectedTileAnimationProperties().isEditing());
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); flush(panel);
            assertEquals(id, panel.inspectedTileAnimationProperties().assetId());
            assertArrayEquals(new int[]{300, 123, 300}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            var loaded = games.pixscape.studio.io.TileAnimationsIO.load(file.parent().child(games.pixscape.runtime.helper.RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
            assertArrayEquals(new int[]{tiles[0], tiles[1], tiles[0]}, loaded.animations.first().frameAssetIds);
            assertArrayEquals(new int[]{300, 123, 300}, loaded.animations.first().frameDurationsMs);
        });
    }

    private static com.kotcrab.vis.ui.widget.VisTree.Node tileAnimationsRoot(Array<com.kotcrab.vis.ui.widget.VisTree.Node> nodes) {
        for (var node : nodes) {
            if (node.getActor().getUserObject() instanceof AssetNode asset && asset.kind == AssetNode.Kind.TILED_ANIMATIONS_FOLDER) return node;
            var found = tileAnimationsRoot(node.getChildren()); if (found != null) return found;
        }
        return null;
    }

    @Test public void tileAnimationBrowserStillDropsTheAnimationIntoTheSelectedMap() throws Exception {
        withStudio(1000, (studio, root) -> {
            var file = project(studio, root.child("animation-scene-drop")); int[] tiles = tileFixtures(file.parent());
            open(studio, file); studio.render(); studio.render();
            var service = studio.getSceneService(); int id = service.createEmptyTileAnimation("Water");
            service.insertTileAnimationFrames(id, 0, tiles);
            var canvas = studio.getCanvas(); var context = canvas.getAttachedSceneContext();
            int mapId = canvas.getEditorOps().addTiledMap(context.selectionService().getActivelayerId(), 32, 32,
                    games.pixscape.runtime.tiled.TiledProjection.ORTHO, 16, 16, 8);
            studio.render(); studio.render(); canvas.focusCameraAt(128, 128);
            var map = context.world().getMapper(games.pixscape.runtime.component.TiledLayerComponent.class).get(mapId);
            studio.getAssetsPanel().selectTiledAnimationById(id); studio.render(); studio.render();
            Actor source = selectedTile(studio, AssetNode.Kind.TILED_ANIMATION); var stage = studio.getUiStage();
            var from = stage.stageToScreenCoordinates(source.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(20, 20)));
            var coordinates = (games.pixscape.studio.service.CoordSpaces) field(canvas, "coordSpaces");
            var to = coordinates.worldToScreen(map.data.tileToWorldX(2, 2) + 4, map.data.tileToWorldY(2, 2) + 4, new com.badlogic.gdx.math.Vector2());
            to.y = Gdx.graphics.getHeight() - to.y;
            assertEquals(mapId, context.selectionService().getTiledMapEditingTargetEntityId());
            var targetStage = stage.screenToStageCoordinates(new com.badlogic.gdx.math.Vector2(to));
            assertNull("Canvas drop must not hit a UI overlay", stage.hit(targetStage.x, targetStage.y, true));
            var worldPoint = coordinates.screenToWorldLogical(to.x, to.y, context.selectionService().getActiveLayerIndex(), context.layerService(), new com.badlogic.gdx.math.Vector2());
            assertTrue("Drop world position " + worldPoint, map.data.isInside(map.data.worldToTileX(worldPoint.x, worldPoint.y), map.data.worldToTileY(worldPoint.x, worldPoint.y)));
            assertTrue(context.selectionService().isTiledMapEditingTargetActive());
            int cursor = context.historyManager().getCursor(); var input = Gdx.input;
            Gdx.input = (com.badlogic.gdx.Input) java.lang.reflect.Proxy.newProxyInstance(com.badlogic.gdx.Input.class.getClassLoader(),
                    new Class<?>[]{com.badlogic.gdx.Input.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> Math.round(to.x); case "getY" -> Math.round(to.y);
                        default -> method.invoke(input, args);
                    });
            try {
                stage.touchDown((int)from.x, (int)from.y, 0, 0); stage.touchDragged((int)to.x, (int)to.y, 0);
                var payload = games.pixscape.studio.ui.asset.dnd.DragContext.get().peek();
                assertNotNull(payload); assertEquals("tiled-animation", payload.type); assertEquals(id, payload.tileAnimationId);
                stage.touchUp((int)to.x, (int)to.y, 0, 0); studio.render(); studio.render();
                assertFalse(games.pixscape.studio.ui.asset.dnd.DragContext.get().active());
                assertEquals(cursor + 1, context.historyManager().getCursor());
                int found = 0;
                for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) if (map.data.getTile(x, y) == id) found++;
                assertEquals(1, found); assertArrayEquals(tiles, service.findTileAnimationProjectDef(id).frameAssetIds);
            } finally { Gdx.input = input; stage.cancelTouchFocus(); }
        });
    }

    @Test public void tileAnimationGridSelectionDeletionAndResizeUseRealInput() throws Exception {
        withStudio(1000, (studio, root) -> {
            FileHandle file = project(studio, root.child("grid-selection"));
            int[] tiles = tileFixtures(file.parent()); open(studio, file); studio.render(); studio.render();
            var service = studio.getSceneService(); var panel = inspector(studio);
            int id = service.createEmptyTileAnimation("Occurrences");
            int[] frames = {tiles[0], tiles[1], tiles[0], tiles[1], tiles[0], tiles[1], tiles[0]};
            service.insertTileAnimationFrames(id, 0, frames);
            for (int i = 0; i < frames.length; i++) service.updateTileAnimationFrameDuration(id, i, 100 + i);
            int[] durations = service.findTileAnimationProjectDef(id).frameDurationsMs.clone();
            studio.inspectTileAnimation(id);
            var width = (games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "centerRightSplit");
            width.setSplitAmount(.45f);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.95f);
            studio.render(); studio.render();
            var readonly = (games.pixscape.studio.ui.widget.SimpleTextField) panel.findActor("tileAnimationDuration0");
            assertTrue(readonly.isDisabled());
            frameClick(studio, 0, false); assertEquals(0, frameSelection(panel).length);
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            var sequence = (games.pixscape.studio.ui.widget.InsertionPreviewGrid) panel.findActor("tileAnimationSequence");
            frameClick(studio, 0, false);
            withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> frameClick(studio, 2, false));
            assertArrayEquals(new int[]{0, 2}, frameSelection(panel));
            withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> frameClick(studio, 2, false));
            assertArrayEquals(new int[]{0}, frameSelection(panel));
            withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> frameClick(studio, 2, false));
            frameClick(studio, 0, true); assertArrayEquals(new int[]{0, 2}, frameSelection(panel));
            screenshot(studio, "tile-animation-grid-wide-menu");
            int before = service.tileAnimationHistory().getCursor();
            clickActor(studio, studio.getUiStage().getRoot().findActor("tileAnimationDelete")); flush(panel);
            assertEquals(before + 1, service.tileAnimationHistory().getCursor());
            assertArrayEquals(new int[]{tiles[1], tiles[1], tiles[0], tiles[1], tiles[0]}, service.findTileAnimationProjectDef(id).frameAssetIds);
            assertArrayEquals(new int[]{101, 103, 104, 105, 106}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            assertTrue(studio.undoActiveDocument()); flush(panel);
            assertArrayEquals(frames, service.findTileAnimationProjectDef(id).frameAssetIds);
            assertArrayEquals(durations, service.findTileAnimationProjectDef(id).frameDurationsMs);
            assertTrue(studio.redoActiveDocument()); flush(panel);
            assertEquals(5, service.findTileAnimationProjectDef(id).frameAssetIds.length);
            assertTrue(studio.undoActiveDocument()); flush(panel);
            frameClick(studio, 1, false);
            withKey(com.badlogic.gdx.Input.Keys.SHIFT_LEFT, () -> frameClick(studio, 4, false));
            assertArrayEquals(new int[]{1, 2, 3, 4}, frameSelection(panel));
            var duration = (games.pixscape.studio.ui.widget.SimpleTextField) panel.findActor("tileAnimationDuration0");
            withKey(com.badlogic.gdx.Input.Keys.SHIFT_LEFT, () -> clickActor(studio, duration));
            assertArrayEquals(new int[]{1, 2, 3, 4}, frameSelection(panel));
            duration.setText("234"); duration.setCursorPosition(0);
            before = service.tileAnimationHistory().getCursor();
            Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.FORWARD_DEL);
            Gdx.input.getInputProcessor().keyTyped((char) 127);
            Gdx.input.getInputProcessor().keyUp(com.badlogic.gdx.Input.Keys.FORWARD_DEL);
            assertEquals("34", duration.getText());
            assertArrayEquals(frames, service.findTileAnimationProjectDef(id).frameAssetIds);
            assertEquals(before, service.tileAnimationHistory().getCursor());
            int wideColumns = sequence.columns();
            width.setSplitAmount(.72f); studio.render(); studio.render();
            assertTrue("Columns " + wideColumns + " -> " + sequence.columns() + ", width=" + sequence.getWidth()
                    , sequence.columns() < wideColumns);
            assertSame(duration, panel.findActor("tileAnimationDuration0"));
            assertSame(duration, studio.getUiStage().getKeyboardFocus()); assertEquals("34", duration.getText());
            assertArrayEquals(new int[]{1, 2, 3, 4}, frameSelection(panel));
            assertTrue(panel.findActor("tileAnimationFrame3").getY() < panel.findActor("tileAnimationFrame0").getY());
            screenshot(studio, "tile-animation-grid-narrow");
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.65f);
            studio.render(); studio.render();
            Actor scrollParent = sequence.getParent();
            while (!(scrollParent instanceof com.badlogic.gdx.scenes.scene2d.ui.ScrollPane)) scrollParent = scrollParent.getParent();
            var scroller = (com.badlogic.gdx.scenes.scene2d.ui.ScrollPane) scrollParent;
            var scrollPoint = studio.getUiStage().stageToScreenCoordinates(scroller.localToStageCoordinates(
                    new com.badlogic.gdx.math.Vector2(scroller.getWidth() / 2, scroller.getHeight() / 2)));
            studio.getUiStage().mouseMoved(Math.round(scrollPoint.x), Math.round(scrollPoint.y)); studio.getUiStage().act(.01f);
            assertTrue(studio.getUiStage().scrolled(0, 4)); studio.getUiStage().act(.5f);
            assertTrue(scroller.getScrollY() > 0);
            assertSame(duration, panel.findActor("tileAnimationDuration0"));
            assertArrayEquals(new int[]{1, 2, 3, 4}, frameSelection(panel));
            screenshot(studio, "tile-animation-grid-scrolled");
            studio.getUiStage().scrolled(0, -100); studio.getUiStage().act(.5f);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.95f);
            studio.render(); studio.render();
            duration.setText("100"); // Unchanged value: moving focus must not add a command.
            frameClick(studio, 6, true); assertArrayEquals(new int[]{6}, frameSelection(panel));
            clickActor(studio, studio.getUiStage().getRoot().findActor("tileAnimationDelete")); flush(panel);
            assertEquals(before + 1, service.tileAnimationHistory().getCursor());
            assertEquals(6, service.findTileAnimationProjectDef(id).frameAssetIds.length);
            assertTrue(studio.undoActiveDocument()); flush(panel);
            before = service.tileAnimationHistory().getCursor();
            studio.getUiStage().setKeyboardFocus(sequence); deleteKey(studio);
            assertEquals(before, service.tileAnimationHistory().getCursor()); // No selection.
            frameClick(studio, 0, false);
            withKey(com.badlogic.gdx.Input.Keys.SHIFT_LEFT, () -> frameClick(studio, 6, false));
            deleteKey(studio);
            assertEquals(before + 1, service.tileAnimationHistory().getCursor());
            assertNotNull(service.findTileAnimationProjectDef(id)); // DEL did not reach Assets.
            assertEquals(0, service.findTileAnimationProjectDef(id).frameAssetIds.length);
            assertNotNull(panel.findActor("tileAnimationEmptyDrop"));
            assertFalse(studio.getCanvas().getTileAnimationRegistry().contains(id));
            assertTrue(studio.undoActiveDocument()); flush(panel);
            assertArrayEquals(frames, service.findTileAnimationProjectDef(id).frameAssetIds);
            assertArrayEquals(durations, service.findTileAnimationProjectDef(id).frameDurationsMs);
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            assertFalse(panel.inspectedTileAnimationProperties().isEditing());
            assertTrue(((games.pixscape.studio.ui.widget.SimpleTextField) panel.findActor("tileAnimationDuration0")).isDisabled());
        });
    }

    private static int[] frameSelection(PropertiesPanel panel) throws Exception {
        int[] indices = ((com.badlogic.gdx.utils.IntSet) field(panel.inspectedTileAnimationProperties(), "selected")).iterator().toArray().toArray();
        java.util.Arrays.sort(indices); return indices;
    }

    @Test public void tileAnimationGridDropsCrossRowsWithoutHoverMutation() throws Exception {
        withStudio(1000, (studio, root) -> {
            FileHandle file = project(studio, root.child("grid-drops"));
            int[] tiles = tileFixtures(file.parent()); open(studio, file); studio.render(); studio.render();
            var service = studio.getSceneService(); var panel = inspector(studio);
            int id = service.createEmptyTileAnimation("Wrapped drops");
            int[] frames = {tiles[0], tiles[1], tiles[0], tiles[1], tiles[0]};
            service.insertTileAnimationFrames(id, 0, frames); studio.editTileAnimation(id);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "centerRightSplit")).setSplitAmount(.72f);
            var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.TILES, "water", "water", null));
            studio.render(); studio.render();
            var sequence = (games.pixscape.studio.ui.widget.InsertionPreviewGrid) panel.findActor("tileAnimationSequence");
            assertEquals(2, sequence.columns());
            for (int position : new int[]{0, 1, 2, 4, 5}) {
                Actor source = tileActor(studio, tiles[0]);
                clickActor(studio, tileActor(studio, tiles[1]));
                withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> clickActor(studio, source));
                int before = service.tileAnimationHistory().getCursor();
                tileDrop(studio, source, position, false, true);
                int[] expected = new int[frames.length + tiles.length];
                System.arraycopy(frames, 0, expected, 0, position);
                System.arraycopy(tiles, 0, expected, position, tiles.length);
                System.arraycopy(frames, position, expected, position + tiles.length, frames.length - position);
                assertArrayEquals(expected, service.findTileAnimationProjectDef(id).frameAssetIds);
                assertEquals(before + 1, service.tileAnimationHistory().getCursor());
                assertTrue(studio.undoActiveDocument()); flush(panel);
                assertArrayEquals(frames, service.findTileAnimationProjectDef(id).frameAssetIds);
            }
            Actor source = tileActor(studio, tiles[0]); clickActor(studio, source);
            tileDrop(studio, source, 2, true, false);
            assertArrayEquals(frames, service.findTileAnimationProjectDef(id).frameAssetIds);
        });
    }

    private static void frameClick(StudioApplicationAdapter studio, int index, boolean right) throws Exception {
        studio.render(); studio.render();
        Actor cell = inspector(studio).findActor("tileAnimationFrame" + index);
        var point = studio.getUiStage().stageToScreenCoordinates(cell.localToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(cell.getWidth() / 2, cell.getHeight() - 20)));
        int button = right ? com.badlogic.gdx.Input.Buttons.RIGHT : com.badlogic.gdx.Input.Buttons.LEFT;
        studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, button);
        studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, button);
        flush(inspector(studio));
    }

    private static void deleteKey(StudioApplicationAdapter studio) throws Exception {
        assertTrue(Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.FORWARD_DEL));
        assertTrue(Gdx.input.getInputProcessor().keyUp(com.badlogic.gdx.Input.Keys.FORWARD_DEL));
        flush(inspector(studio));
    }

    @Test public void tileAnimationDurationsCommitOnEnterTabAndUserFocusLoss() throws Exception {
        withStudio(1000, (studio, root) -> {
            FileHandle file = project(studio, root.child("duration-focus"));
            int[] tiles = tileFixtures(file.parent()); open(studio, file); studio.render(); studio.render();
            var service = studio.getSceneService(); var panel = inspector(studio);
            int id = service.createEmptyTileAnimation("Focus"); service.insertTileAnimationFrames(id, 0, tiles);
            studio.getAssetsPanel().selectTiledAnimationById(id); studio.editTileAnimation(id);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.95f);
            var history = service.tileAnimationHistory();
            {
                String prefix = "tileAnimationDuration";
                Group owner = panel;
                for (int gesture = 0; gesture < 3; gesture++) {
                    studio.render(); studio.render();
                    var duration = (games.pixscape.studio.ui.widget.SimpleTextField) owner.findActor(prefix + "0");
                    clickActor(studio, duration);
                    int before = history.getCursor(); int value = 400 + gesture;
                    var preview = panel.findActor("tileAnimationPreview");
                    duration.setText(Integer.toString(value));
                    if (gesture == 0) {
                        studio.getUiStage().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                        studio.getUiStage().keyUp(com.badlogic.gdx.Input.Keys.ENTER);
                        studio.getUiStage().setKeyboardFocus(null);
                    } else if (gesture == 1) {
                        studio.getUiStage().keyDown(com.badlogic.gdx.Input.Keys.TAB);
                        studio.getUiStage().keyTyped('\t'); studio.getUiStage().keyUp(com.badlogic.gdx.Input.Keys.TAB);
                    } else {
                        clickActor(studio, owner.findActor(prefix + "1"));
                    }
                    flush(panel);
                    if (studio.getUiStage().getKeyboardFocus() != null)
                        assertSame("Focus must not remain on a destroyed duration field", studio.getUiStage(), studio.getUiStage().getKeyboardFocus().getStage());
                    assertEquals(prefix + " gesture=" + gesture, value, service.findTileAnimationProjectDef(id).frameDurationsMs[0]);
                    assertEquals(before + 1, history.getCursor()); assertNotSame(preview, panel.findActor("tileAnimationPreview"));
                    studio.getUiStage().setKeyboardFocus(null); assertEquals(before + 1, history.getCursor());
                    assertTrue(studio.undoActiveDocument()); assertTrue(studio.redoActiveDocument());
                }
                for (String invalid : new String[]{"", "0", "-5", "NaN", "999999999999999999999", "2147483647"}) {
                    studio.render(); studio.render();
                    var duration = (games.pixscape.studio.ui.widget.SimpleTextField) owner.findActor(prefix + "0");
                    clickActor(studio, duration); int before = history.getCursor();
                    duration.setText(invalid);
                    clickActor(studio, owner.findActor(prefix + "1")); flush(panel);
                    assertEquals(before, history.getCursor());
                    assertEquals(Integer.toString(service.findTileAnimationProjectDef(id).frameDurationsMs[0]), duration.getText());
                }
                studio.render(); studio.render();
                var unchanged = (games.pixscape.studio.ui.widget.SimpleTextField) owner.findActor(prefix + "0");
                clickActor(studio, unchanged); int before = history.getCursor();
                studio.getUiStage().keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                studio.getUiStage().setKeyboardFocus(null); assertEquals(before, history.getCursor());
            }
            assertNull(panel.findActor("tileAnimationRemove0"));
            var pending = (games.pixscape.studio.ui.widget.SimpleTextField) panel.findActor("tileAnimationDuration0");
            clickActor(studio, pending); pending.setText("250"); int beforeDone = history.getCursor();
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            assertFalse(panel.inspectedTileAnimationProperties().isEditing());
            assertEquals(250, service.findTileAnimationProjectDef(id).frameDurationsMs[0]);
            assertEquals(beforeDone + 1, history.getCursor());
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            var edit = (com.kotcrab.vis.ui.widget.VisTextButton) panel.findActor("tileAnimationEdit");
            assertEquals(edit.getPrefWidth(), edit.getWidth(), .01f);
        });
    }

    @Test public void tileAnimationDurationTeardownCannotCommitOldBindings() throws Exception {
        withStudio(1000, (studio, root) -> {
            FileHandle file = project(studio, root.child("duration-teardown"));
            int[] tiles = tileFixtures(file.parent()); open(studio, file); studio.render(); studio.render();
            var service = studio.getSceneService(); var panel = inspector(studio);
            int id = service.createEmptyTileAnimation("Old"); service.insertTileAnimationFrames(id, 0, tiles);
            int other = service.createEmptyTileAnimation("New"); service.insertTileAnimationFrames(other, 0, tiles);
            var history = service.tileAnimationHistory();
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.95f);
            {
                studio.getAssetsPanel().selectTiledAnimationById(id); studio.editTileAnimation(id); studio.render(); studio.render();
                Group owner = panel;
                String prefix = "tileAnimationDuration";
                var stale = (games.pixscape.studio.ui.widget.SimpleTextField) owner.findActor(prefix + "0");
                clickActor(studio, stale); stale.setText("777"); int before = history.getCursor();
                // An external structural replacement abandons the pending duration before removing the occurrence.
                service.removeFrameFromTileAnimation(id, 0); flush(panel);
                assertEquals(before + 1, history.getCursor()); assertArrayEquals(new int[]{300}, service.findTileAnimationProjectDef(id).frameDurationsMs);
                stale.commit(); assertEquals(before + 1, history.getCursor());
                history.undo(); flush(panel);
                studio.render(); studio.render();
                stale = (games.pixscape.studio.ui.widget.SimpleTextField) owner.findActor(prefix + "0");
                clickActor(studio, stale); stale.setText("888"); before = history.getCursor();
                studio.editTileAnimation(other);
                stale.commit(); studio.getUiStage().setKeyboardFocus(null);
                assertEquals(before, history.getCursor()); assertEquals(300, service.findTileAnimationProjectDef(id).frameDurationsMs[0]);
                assertEquals(300, service.findTileAnimationProjectDef(other).frameDurationsMs[0]);
            }
            int deleted = service.createEmptyTileAnimation("Deleted while focused"); service.insertTileAnimationFrames(deleted, 0, tiles);
            studio.editTileAnimation(deleted); studio.render(); studio.render();
            var deletedField = (games.pixscape.studio.ui.widget.SimpleTextField) panel.findActor("tileAnimationDuration0");
            clickActor(studio, deletedField); deletedField.setText("666");
            service.deleteTileAnimation(deleted); deletedField.commit(); studio.getUiStage().setKeyboardFocus(null);
            assertNull(service.findTileAnimationProjectDef(deleted)); assertFalse(history.canUndo());
            studio.getAssetsPanel().selectTiledAnimationById(id); studio.editTileAnimation(id); studio.render(); studio.render();
            var inspectorField = (games.pixscape.studio.ui.widget.SimpleTextField) panel.findActor("tileAnimationDuration0");
            clickActor(studio, inspectorField); inspectorField.setText("999");
            var persisted = snapshot(file.parent());
            open(studio, project(studio, root.child("duration-next-project")));
            inspectorField.commit(); studio.getUiStage().setKeyboardFocus(null);
            assertEquals(persisted, snapshot(file.parent())); assertFalse(service.tileAnimationHistory().canUndo());
        });
    }

    @Test public void tileAnimationIndexRejectsCollisionsAndRebuildsAtPublication() throws Exception {
        withStudio((studio, root) -> {
            open(studio, project(studio, root.child("tile-index")));
            var service = studio.getSceneService(); int id = service.createEmptyTileAnimation("Indexed");
            var definition = service.findTileAnimationProjectDef(id);
            var database = (games.pixscape.studio.asset.TileAnimationsMetaDatabase) field(service, "tileAnimationsMetaDatabase");
            var source = database.animations;
            database.animations = new Array<>() {
                @Override public Array.ArrayIterator<games.pixscape.studio.asset.TileAnimationProjectDefData> iterator() {
                    throw new AssertionError("A direct lookup must not scan the source collection");
                }
            };
            try {
                for (int i = 0; i < 100; i++) { assertSame(definition, service.findTileAnimationProjectDef(id)); assertNull(service.findTileAnimationProjectDef(-10)); }
            } finally { database.animations = source; }
            var reload = SceneService.class.getDeclaredMethod("reloadTileAnimationRegistryFromProjectData"); reload.setAccessible(true);
            source.add(definition);
            try {
                var failure = assertThrows(java.lang.reflect.InvocationTargetException.class, () -> reload.invoke(service));
                assertTrue(failure.getCause().getMessage().contains("Duplicate"));
            } finally { source.pop(); reload.invoke(service); }
            assertSame(definition, service.findTileAnimationProjectDef(id));
            service.unloadProjectToEmptyEditor();
            assertNull(service.findTileAnimationProjectDef(id)); assertFalse(service.tileAnimationHistory().canUndo());
        });
    }

    @Test public void tileAnimationCreationAndEditingUseRealBrowserDropsAndHistory() throws Exception {
        withStudio(1000, (studio, root) -> {
            FileHandle file = project(studio, root.child("tile-edit"));
            int[] tiles = tileFixtures(file.parent());
            open(studio, file);
            studio.render(); studio.render();
            var panel = inspector(studio);
            var vertical = (games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit");
            vertical.setSplitAmount(.95f);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "centerRightSplit")).setSplitAmount(.72f);
            var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
            tree.selectFolder(new AssetNode(AssetNode.Kind.TILED_ANIMATIONS_FOLDER, AssetNode.Root.TILES,
                    AssetNode.TILED_ANIMATIONS_NODE_PATH, "Animations", null));
            var thumbs = field(studio.getAssetsPanel(), "thumbsView");
            screenshot(studio, "tile-create-before");
            clickActor(studio, (Actor) field(thumbs, "newAnimationBtn"));
            var dialog = lastDialog(studio);
            var name = (com.kotcrab.vis.ui.widget.VisTextField) studio.getUiStage().getKeyboardFocus();
            name.setText("Water");
            clickActor(studio, buttonWithText(dialog, "Create"));
            studio.getUiStage().act(1); // Finish the existing dialog fade-out.
            var properties = panel.inspectedTileAnimationProperties();
            assertNotNull(properties); assertFalse(properties.isEditing());
            int id = properties.assetId();
            assertEquals(AssetNode.Kind.TILED_ANIMATIONS_FOLDER, tree.getSelectedFolder().kind);
            assertEquals(id, studio.getAssetsPanel().getSelectedAssetNode().tileAnimationId);
            assertEquals("Water", ((VisLabel) panel.findActor("tileAnimationName")).getText().toString());
            assertEquals("0 frames, 0 ms", ((VisLabel) panel.findActor("tileAnimationSummary")).getText().toString());
            assertNull(panel.findActor("tileAnimationEmptyDrop"));
            assertNull(panel.findActor("tileAnimationSequence"));
            assertNotNull(buttonWithText(panel, "Edit"));
            screenshot(studio, "tile-animation-empty-consultation");
            assertEquals(id, studio.getAssetsPanel().getSelectedAssetNode().tileAnimationId);
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.TILES, "water", "water", null));
            clickActor(studio, tileActor(studio, tiles[0]));
            assertNotNull(panel.inspectedTileProperties());
            var service = studio.getSceneService();
            var history = service.tileAnimationHistory();
            assertEquals("Create tile animation", history.peekUndoLabel());
            assertFalse(studio.getCanvas().getTileAnimationRegistry().contains(id));
            history.undo(); flush(panel); assertNull(service.findTileAnimationProjectDef(id));
            history.redo(); flush(panel); assertNotNull(service.findTileAnimationProjectDef(id));
            studio.getAssetsPanel().selectTiledAnimationById(id); flush(panel);
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            assertNotNull(panel.findActor("tileAnimationEmptyDrop"));
            screenshot(studio, "tile-animation-empty-edit");
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.TILES, "water", "water", null));
            Actor first = tileActor(studio, tiles[0]), second = tileActor(studio, tiles[1]);
            clickActor(studio, second);
            Actor multiSource = first;
            withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> clickActor(studio, multiSource));
            assertEquals(id, panel.inspectedTileAnimationProperties().assetId());
            int cursor = history.getCursor();
            tileDrop(studio, first, 0, false, true);
            assertArrayEquals(tiles, service.findTileAnimationProjectDef(id).frameAssetIds);
            assertArrayEquals(new int[]{300, 300}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            assertEquals(cursor + 1, history.getCursor());
            withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> {
                studio.getUiStage().setKeyboardFocus(null);
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.Z);
                Gdx.input.getInputProcessor().keyUp(com.badlogic.gdx.Input.Keys.Z);
            });
            flush(panel); assertEquals(0, service.findTileAnimationProjectDef(id).frameAssetIds.length);
            withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> {
                Gdx.input.getInputProcessor().keyDown(com.badlogic.gdx.Input.Keys.Y);
                Gdx.input.getInputProcessor().keyUp(com.badlogic.gdx.Input.Keys.Y);
            });
            flush(panel); assertArrayEquals(tiles, service.findTileAnimationProjectDef(id).frameAssetIds);
            assertTrue(studio.getCanvas().getTileAnimationRegistry().contains(id));
            // The browser was rebuilt at publication: use its current actors, not stale touch targets.
            first = tileActor(studio, tiles[0]); clickActor(studio, first);
            tileDrop(studio, first, 0, false, false);
            assertArrayEquals(new int[]{tiles[0], tiles[0], tiles[1]}, service.findTileAnimationProjectDef(id).frameAssetIds);
            history.undo(); flush(panel);
            first = tileActor(studio, tiles[0]); clickActor(studio, first);
            tileDrop(studio, first, 1, false, true);
            assertArrayEquals(new int[]{tiles[0], tiles[0], tiles[1]}, service.findTileAnimationProjectDef(id).frameAssetIds);
            history.undo(); flush(panel);
            first = tileActor(studio, tiles[0]); clickActor(studio, first);
            tileDrop(studio, first, 2, false, false);
            assertArrayEquals(new int[]{tiles[0], tiles[1], tiles[0]}, service.findTileAnimationProjectDef(id).frameAssetIds);
            int before = history.getCursor();
            var oldPreview = panel.findActor("tileAnimationPreview");
            enterDuration(studio, 2, "125");
            assertArrayEquals(new int[]{300, 300, 125}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            assertNotSame(oldPreview, panel.findActor("tileAnimationPreview"));
            assertEquals(before + 1, history.getCursor());
            for (String invalid : new String[]{"", "-5", "0", "NaN", "2147483647"}) {
                enterDuration(studio, 2, invalid);
                assertArrayEquals(new int[]{300, 300, 125}, service.findTileAnimationProjectDef(id).frameDurationsMs);
                assertEquals(before + 1, history.getCursor());
            }
            history.undo(); flush(panel); assertEquals(300, service.findTileAnimationProjectDef(id).frameDurationsMs[2]);
            history.redo(); flush(panel); assertEquals(125, service.findTileAnimationProjectDef(id).frameDurationsMs[2]);
            // Preview uses occurrence durations and loops; drawing is independent of advancement.
            var preview = (games.pixscape.studio.ui.asset.AnimatedThumbnail) panel.findActor("tileAnimationPreview");
            preview.startPreview(); for (int i = 0; i < 6; i++) preview.act(.1f);
            assertEquals(2, field(preview, "currentFrame"));
            preview.act(.1f); preview.act(.03f); assertEquals(0, field(preview, "currentFrame"));
            first = tileActor(studio, tiles[0]); clickActor(studio, first);
            tileDrop(studio, first, 1, true, false);
            assertEquals(before + 1, history.getCursor());
            screenshot(studio, "tile-animation-edited");
            frameClick(studio, 0, false);
            withKey(com.badlogic.gdx.Input.Keys.SHIFT_LEFT, () -> frameClick(studio, 2, false));
            deleteKey(studio);
            assertEquals(0, service.findTileAnimationProjectDef(id).frameAssetIds.length);
            assertNull(panel.findActor("tileAnimationPreview"));
            assertNotNull(panel.findActor("tileAnimationEmptyDrop"));
            history.undo(); flush(panel);
            assertArrayEquals(new int[]{300, 300, 125}, service.findTileAnimationProjectDef(id).frameDurationsMs);
            var persisted = games.pixscape.studio.io.TileAnimationsIO.load(file.parent().child(games.pixscape.runtime.helper.RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
            assertArrayEquals(service.findTileAnimationProjectDef(id).frameAssetIds, persisted.animations.first().frameAssetIds);
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            assertFalse(panel.inspectedTileAnimationProperties().isEditing());
            clickActor(studio, tileActor(studio, tiles[0]));
            assertNotNull(panel.inspectedTileProperties());
            studio.getAssetsPanel().selectTiledAnimationById(id); flush(panel);
            clickActor(studio, panel.findActor("tileAnimationEdit"));
            enterDuration(studio, 0, "80");
            assertEquals("3 frames, 505 ms", ((VisLabel) panel.findActor("tileAnimationSummary")).getText().toString());
            open(studio, file); flush(panel);
            assertEquals(80, service.findTileAnimationProjectDef(id).frameDurationsMs[0]);
            studio.render(); studio.render();
            tree.selectFolder(new AssetNode(AssetNode.Kind.TILED_ANIMATIONS_FOLDER, AssetNode.Root.TILES,
                    AssetNode.TILED_ANIMATIONS_NODE_PATH, "Animations", null));
            studio.render(); studio.render();
            clickActor(studio, selectedTile(studio, AssetNode.Kind.TILED_ANIMATION));
            assertEquals(id, panel.inspectedTileAnimationProperties().assetId()); assertFalse(panel.inspectedTileAnimationProperties().isEditing());
        });
    }

    @Test public void tileAnimationPinnedTargetSurvivesReloadAndRejectsOtherPayloads() throws Exception {
        withStudio(1000, (studio, root) -> {
            FileHandle file = project(studio, root.child("tile-lifecycle"));
            int[] tiles = tileFixtures(file.parent()); addImage(file.parent(), "other", 16, 16);
            open(studio, file);
            studio.render(); studio.render();
            var service = studio.getSceneService(); var panel = inspector(studio);
            int id = service.createEmptyTileAnimation("Loop");
            service.insertTileAnimationFrames(id, 0, tiles); studio.editTileAnimation(id);
            var properties = panel.inspectedTileAnimationProperties();
            var history = service.tileAnimationHistory();
            var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.IMAGES, "", "", null));
            screenshot(studio, "tile-source-before"); Actor source = selectedTile(studio, AssetNode.Kind.IMAGE); clickActor(studio, source);
            assertSame(properties, panel.inspectedTileAnimationProperties());
            int before = history.getCursor();
            tileDrop(studio, source, 0, false, false);
            assertArrayEquals(tiles, service.findTileAnimationProjectDef(id).frameAssetIds);
            assertEquals(before, history.getCursor());
            assertTrue(((VisLabel) panel.findActor("tileAnimationStatus")).getText().toString().contains("Only tile"));
            assertThrows(IllegalArgumentException.class, () -> service.insertTileAnimationFrames(id, 1, new int[]{tiles[0], -10}));
            assertEquals(before, history.getCursor()); assertArrayEquals(tiles, service.findTileAnimationProjectDef(id).frameAssetIds);
            var preview = (games.pixscape.studio.ui.asset.AnimatedThumbnail) panel.findActor("tileAnimationPreview");
            var textures = (Array<com.badlogic.gdx.graphics.Texture>) field(preview, "frames");
            var borrowed = textures.first();
            panel.setVisible(false); preview.startPreview(); preview.act(.1f);
            assertEquals(0, field(preview, "currentFrame"));
            panel.setVisible(true);
            studio.getDockManager().hide(panel); assertTrue(properties.isEditing());
            studio.getDockManager().show(panel); assertSame(properties, panel.inspectedTileAnimationProperties());
            var parent = panel.getParent(); int index = panel.getZIndex(); panel.remove();
            assertTrue(properties.isEditing()); parent.addActorAt(index, panel);
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); panel.reloadAssetInspection(); flush(panel);
            assertSame(properties, panel.inspectedTileAnimationProperties()); assertTrue(properties.isEditing());
            assertTrue(borrowed.getTextureObjectHandle() != 0);
            ((games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit")).setSplitAmount(.7f);
            clickLayer(studio, studio.getCanvas().getAttachedSceneContext().selectionService().getActivelayerId(), 1);
            flush(panel); assertNull(panel.inspectedTileAnimationProperties());
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); panel.reloadAssetInspection(); flush(panel); assertNull(panel.inspectedTileAnimationProperties());
            studio.editTileAnimation(id);
            var deletion = studio.getAssetsPanel().getClass().getDeclaredMethod("showDeleteTiledAnimationDialog", int.class, String.class);
            deletion.setAccessible(true); deletion.invoke(studio.getAssetsPanel(), id, "Loop");
            clickActor(studio, buttonWithText(lastDialog(studio), "Cancel")); studio.getUiStage().act(1);
            assertEquals(id, panel.inspectedTileAnimationProperties().assetId()); assertTrue(properties.isEditing());
            // The existing usage guard also refuses deletion when scene data cannot be safely checked.
            var sceneFile = file.parent().child("scenes/scene1.json"); String savedScene = sceneFile.readString();
            sceneFile.writeString("invalid scene", false);
            deletion.invoke(studio.getAssetsPanel(), id, "Loop");
            clickActor(studio, buttonWithText(lastDialog(studio), "Delete")); studio.getUiStage().act(1);
            assertEquals(id, panel.inspectedTileAnimationProperties().assetId()); assertNotNull(service.findTileAnimationProjectDef(id));
            clickActor(studio, buttonWithText(lastDialog(studio), "OK")); studio.getUiStage().act(1);
            sceneFile.writeString(savedScene, false);
            int other = service.createEmptyTileAnimation("Other"); service.deleteTileAnimation(other); flush(panel);
            assertEquals(id, panel.inspectedTileAnimationProperties().assetId());
            deletion.invoke(studio.getAssetsPanel(), id, "Loop");
            clickActor(studio, buttonWithText(lastDialog(studio), "Delete")); studio.getUiStage().act(1);
            flush(panel); assertNull(panel.inspectedTileAnimationProperties());
            assertNull(service.findTileAnimationProjectDef(id));
            int next = service.createEmptyTileAnimation("Next"); studio.editTileAnimation(next);
            FileHandle secondProject = project(studio, root.child("second-project")); open(studio, secondProject); flush(panel);
            assertNull(panel.inspectedTileAnimationProperties()); assertNull(service.findTileAnimationProjectDef(next));
            assertEquals(-1, properties.assetId()); assertFalse(properties.isEditing());
        });
    }

    private static int[] tileFixtures(FileHandle project) {
        var db = AssetMetaDatabase.load(project.child("assets.json")); int[] ids = new int[2];
        for (int i = 0; i < ids.length; i++) {
            String path = "orig/tiles/water/" + i + ".png";
            var meta = db.registerIfAbsent(AssetType.TILE, "tiles/water/" + i, path, AssetMeta.AssetScope.USER);
            ids[i] = meta.id();
            Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            pixmap.setColor(.1f, .4f + .2f * i, .9f, 1); pixmap.fill();
            pixmap.setColor(.4f, .8f, 1, 1); pixmap.fillRectangle(i * 3, 3, 10, 2);
            PixmapIO.writePNG(project.child(path), pixmap); pixmap.dispose();
        }
        db.save(project.child("assets.json")); return ids;
    }

    private static Actor tileActor(StudioApplicationAdapter studio, int id) throws Exception {
        studio.render(); studio.render();
        for (Object item : (Array<?>) field(field(studio.getAssetsPanel(), "thumbsView"), "tileWidgets")) {
            Actor actor = (Actor) item;
            if (actor.getUserObject() instanceof AssetNode node && node.assetId == id) return actor;
        }
        throw new AssertionError("Missing tile " + id);
    }

    private static Actor buttonWithText(Actor actor, String text) {
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.TextButton button && button.getText().toString().equals(text)) return actor;
        if (actor instanceof Group group) for (Actor child : group.getChildren()) {
            Actor found = buttonWithText(child, text); if (found != null) return found;
        }
        return null;
    }

    private static void enterDuration(StudioApplicationAdapter studio, int frame, String value) throws Exception {
        var duration = (games.pixscape.studio.ui.widget.SimpleTextField) inspector(studio).findActor("tileAnimationDuration" + frame);
        clickActor(studio, duration); duration.setText(value);
        studio.getUiStage().keyDown(com.badlogic.gdx.Input.Keys.ENTER); studio.getUiStage().keyUp(com.badlogic.gdx.Input.Keys.ENTER);
        flush(inspector(studio));
    }

    /** Real source capture, ordered browser payload, hover hit testing, release/cancel and target act. */
    private static void tileDrop(StudioApplicationAdapter studio, Actor source, int position, boolean cancel, boolean capture) throws Exception {
        var panel = inspector(studio);
        var vertical = (games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit");
        vertical.setSplitAmount(.95f);
        studio.render(); studio.render();
        var stage = studio.getUiStage();
        var sequence = (games.pixscape.studio.ui.widget.InsertionPreviewGrid) panel.findActor("tileAnimationSequence");
        Actor row = panel.findActor("tileAnimationFrame" + position);
        Actor last = panel.findActor("tileAnimationFrame" + (position - 1));
        var local = row != null ? row.localToActorCoordinates(sequence, new com.badlogic.gdx.math.Vector2(4, row.getHeight() - 20))
                : last != null ? last.localToActorCoordinates(sequence, new com.badlogic.gdx.math.Vector2(last.getWidth() - 4, last.getHeight() - 20))
                : new com.badlogic.gdx.math.Vector2(sequence.getWidth() / 2, sequence.getHeight() - 20);
        var target = stage.stageToScreenCoordinates(sequence.localToStageCoordinates(local));
        var start = stage.stageToScreenCoordinates(source.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(source.getWidth() / 2, source.getHeight() / 2)));
        var originalInput = Gdx.input;
        Gdx.input = (com.badlogic.gdx.Input) java.lang.reflect.Proxy.newProxyInstance(com.badlogic.gdx.Input.class.getClassLoader(),
                new Class<?>[]{com.badlogic.gdx.Input.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> Math.round(target.x); case "getY" -> Math.round(target.y);
                    default -> method.invoke(originalInput, args);
                });
        try {
            int id = panel.inspectedTileAnimationProperties().assetId();
            var before = studio.getSceneService().findTileAnimationProjectDef(id).frameAssetIds.clone();
            var files = snapshot(StudioFs.requireStudioProjectDir(ProjectConfig.getInstance()));
            assertTrue(stage.touchDown(Math.round(start.x), Math.round(start.y), 0, 0));
            stage.touchDragged(Math.round(target.x), Math.round(target.y), 0);
            assertTrue(games.pixscape.studio.ui.asset.dnd.DragContext.get().active());
            boolean accepted = "tile-asset".equals(games.pixscape.studio.ui.asset.dnd.DragContext.get().peek().type);
            stage.act(.01f); sequence.validate();
            assertArrayEquals(before, studio.getSceneService().findTileAnimationProjectDef(id).frameAssetIds);
            assertEquals(files, snapshot(StudioFs.requireStudioProjectDir(ProjectConfig.getInstance())));
            assertEquals(accepted ? position : -1, sequence.insertionIndex());
            // Repeated real layout/act cycles must not move the destination as ghosts wrap.
            for (int i = 0; i < 5; i++) {
                studio.render();
                assertEquals(accepted ? position : -1, sequence.insertionIndex());
            }
            if (capture) screenshot(studio, "tile-animation-ghosts-" + position);
            if (cancel) {
                float insideX = target.x; target.x = -20;
                stage.touchDragged(-20, Math.round(target.y), 0); stage.act(.01f);
                assertEquals(-1, sequence.insertionIndex());
                assertArrayEquals(before, studio.getSceneService().findTileAnimationProjectDef(id).frameAssetIds);
                target.x = insideX; stage.touchDragged(Math.round(target.x), Math.round(target.y), 0); stage.act(.01f);
                assertEquals(position, sequence.insertionIndex());
                panel.setVisible(false); stage.act(.01f); assertEquals(-1, sequence.insertionIndex());
                panel.setVisible(true);
            }
            if (cancel) stage.cancelTouchFocus(); else stage.touchUp(Math.round(target.x), Math.round(target.y), 0, 0);
            stage.act(.01f); flush(panel);
            assertFalse(games.pixscape.studio.ui.asset.dnd.DragContext.get().active());
            assertEquals(-1, sequence.insertionIndex());
            if (cancel) assertArrayEquals(before, studio.getSceneService().findTileAnimationProjectDef(id).frameAssetIds);
        } finally { Gdx.input = originalInput; stage.cancelTouchFocus(); }
    }

    @Test public void gameObjectCaptureUsesSavedGeometryAndRestoresGraphicsState() throws Exception {
        var closingTexture = new com.badlogic.gdx.graphics.Texture[1];
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("saved-capture"));
            int id = addImage(project.parent(), "white", 8, 8);
            Pixmap white = new Pixmap(8, 8, Pixmap.Format.RGBA8888); white.setColor(1, 1, 1, 1); white.fill();
            PixmapIO.writePNG(project.parent().child("orig/images/white.png"), white); white.dispose();
            var asset = gameObjectAsset(); asset.entities.truncate(1);
            var red = gameObjectSprite(2, 1, id, 0, 0, 80, 40);
            red.tint = new games.pixscape.runtime.gameobject.GameObjectAsset.TintData(); red.tint.rgba = 0xff0000ff;
            var blue = gameObjectSprite(3, 1, id, 20, 0, 40, 40);
            blue.tint = new games.pixscape.runtime.gameobject.GameObjectAsset.TintData(); blue.tint.rgba = 0x0000ffff;
            // Authored physics is present but must not participate in capture.
            red.physicsBody = new games.pixscape.runtime.gameobject.GameObjectAsset.PhysicsBodyData();
            red.physicsBody.type = games.pixscape.runtime.component.physics.PhysicsBodyComponent.DYNAMIC;
            asset.entities.add(red); asset.entities.add(blue);
            var file = project.parent().child("gameobjects/Assembly.gameobject");
            var loader = new games.pixscape.runtime.gameobject.GameObjectAssetLoader(); loader.save(file, asset);
            open(studio, project);
            var document = studio.openGameObject(file.path()); assertNotNull(document);
            int sprite = document.context().world().getAspectSubscriptionManager().get(com.artemis.Aspect.all(
                    games.pixscape.runtime.component.AssetRefComponent.class)).getEntities().get(0);
            var transform = document.context().world().getMapper(TransformComponent.class).get(sprite);
            transform.x = 150;
            var history = document.context().historyManager();
            history.execute(new games.pixscape.studio.history.commands.ChangeEntityNameCommand(document.context().world(),
                    history.historyIds(), history.historyIds().ensureForEntity(document.rootEntityId()), "Root", "Modified", 0));
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            var panel = inspector(studio);
            assertEquals("Saved version - editor has unsaved changes", label(panel, "Document"));
            var capture = (games.pixscape.studio.service.gameobject.GameObjectPreviewCapture)
                    field(panel.inspectedResourceProperties(), "gameObjectPreview");
            assertEquals(88f, capture.width(), .01f);
            assertEquals(150, transform.x, 0);
            assertTrue(document.isDirty());
            var framebuffer = (com.badlogic.gdx.graphics.glutils.FrameBuffer) field(capture, "framebuffer");
            framebuffer.begin();
            Pixmap pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
            framebuffer.end();
            try {
                // Packed vertex colors reserve one alpha bit; allow the resulting one-unit blend error.
                assertRgb(0x0000ff, pixels.getPixel(34, 24)); // Higher local z overlays red.
                assertRgb(0xff0000, pixels.getPixel(8, 24)); // Uncovered area stays red.
            } finally { pixels.dispose(); }
            var outer = new com.badlogic.gdx.graphics.glutils.FrameBuffer(Pixmap.Format.RGBA8888, 128, 128, false);
            var previous = capture.region().getTexture();
            try {
                outer.begin(); Gdx.gl.glViewport(3, 4, 81, 63);
                Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST); Gdx.gl.glScissor(5, 7, 20, 30);
                Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
                Gdx.gl.glBlendFuncSeparate(com.badlogic.gdx.graphics.GL20.GL_ONE, com.badlogic.gdx.graphics.GL20.GL_ZERO,
                        com.badlogic.gdx.graphics.GL20.GL_ZERO, com.badlogic.gdx.graphics.GL20.GL_ONE);
                int fbo = glInt(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_BINDING);
                studio.reloadAssetInspection();
                assertEquals(fbo, glInt(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_BINDING));
                var values = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(4);
                Gdx.gl.glGetIntegerv(com.badlogic.gdx.graphics.GL20.GL_VIEWPORT, values);
                assertEquals(3, values.get(0)); assertEquals(4, values.get(1)); assertEquals(81, values.get(2)); assertEquals(63, values.get(3));
                assertTrue(Gdx.gl.glIsEnabled(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST));
                assertTrue(Gdx.gl.glIsEnabled(com.badlogic.gdx.graphics.GL20.GL_BLEND));
                assertEquals(com.badlogic.gdx.graphics.GL20.GL_ZERO, glInt(com.badlogic.gdx.graphics.GL20.GL_BLEND_SRC_ALPHA));
                assertEquals(com.badlogic.gdx.graphics.GL20.GL_ONE, glInt(com.badlogic.gdx.graphics.GL20.GL_BLEND_DST_ALPHA));
                assertEquals(0, previous.getTextureObjectHandle());
            } finally { Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST); outer.end(); outer.dispose(); }
            var save = StudioApplicationAdapter.class.getDeclaredMethod("saveGameObjectDocument", document.getClass());
            save.setAccessible(true); save.invoke(studio, document); flush(panel);
            capture = (games.pixscape.studio.service.gameobject.GameObjectPreviewCapture)
                    field(panel.inspectedResourceProperties(), "gameObjectPreview");
            assertEquals("Saving refreshes the geometry while still inspected", 218, capture.width(), .01f);
            assertEquals("Saved version - editor open", label(panel, "Document"));
            closingTexture[0] = capture.region().getTexture();
        });
        assertEquals("Studio shutdown disposes the last capture", 0, closingTexture[0].getTextureObjectHandle());
    }

    private static int glInt(int name) {
        var value = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1); Gdx.gl.glGetIntegerv(name, value); return value.get(0);
    }

    private static void assertRgb(int expected, int rgba) {
        for (int shift : new int[]{0, 8, 16})
            assertEquals((expected >>> shift) & 255, (rgba >>> (shift + 8)) & 255, 2);
    }

    @Test public void gameObjectCaptureFitsCompositionsAndReleasesOnlyOwnedPixels() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("capture"));
            int imageId = addImage(project.parent(), "composition", 64, 32);
            var asset = gameObjectAsset(); asset.entities.truncate(1);
            asset.entities.add(gameObjectSprite(2, 1, imageId, 0, 0, 960, 180));
            asset.entities.add(gameObjectSprite(3, 1, imageId, 200, 30, 90, 70));
            var file = project.parent().child("gameobjects/Assembly.gameobject");
            var loader = new games.pixscape.runtime.gameobject.GameObjectAssetLoader(); loader.save(file, asset);
            open(studio, project);
            var context = studio.getSceneEditorContext();
            var borrowed = studio.getCanvas().getAssetVisualResolver().resolveFirst(imageId, null).texture();
            var filterMin = borrowed.getMinFilter(); var filterMag = borrowed.getMagFilter();
            var files = snapshot(project.parent());
            int worlds = studio.getEditorDocumentManager().documents().size();
            long frames = context.processedFrameCount();
            int entityCount = context.world().getAspectSubscriptionManager().get(com.artemis.Aspect.all()).getEntities().size();
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            PropertiesPanel panel = inspector(studio);
            var capture = (games.pixscape.studio.service.gameobject.GameObjectPreviewCapture)
                    field(panel.inspectedResourceProperties(), "gameObjectPreview");
            assertNotNull("Capture must be available", capture);
            assertNotNull(capture.region());
            assertEquals(968f, capture.width(), .01f); assertEquals(188f, capture.height(), .01f);
            var owned = capture.region().getTexture();
            var image = (com.kotcrab.vis.ui.widget.VisImage) panel.findActor("gameObjectAssetPreview");
            var frame = (com.badlogic.gdx.scenes.scene2d.ui.Container<?>) image.getParent();
            assertEquals(worlds, studio.getEditorDocumentManager().documents().size());
            assertSame(context, studio.getCanvas().getAttachedSceneContext());
            assertEquals(frames, context.processedFrameCount());
            assertEquals(entityCount, context.world().getAspectSubscriptionManager().get(com.artemis.Aspect.all()).getEntities().size());
            assertEquals(files, snapshot(project.parent()));
            assertEquals(filterMin, borrowed.getMinFilter()); assertEquals(filterMag, borrowed.getMagFilter());
            for (int width : new int[]{500, 170, 340}) {
                frame.setSize(width, 240); frame.validate(); image.validate(); assertFitted(image, 968, 188);
                assertEquals(Math.min(width, 968), image.getImageWidth(), .01f);
            }
            screenshotGameObjectWidths(studio, "wide");
            studio.getDockManager().hide(panel); studio.getDockManager().show(panel);
            studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_BOTTOM);
            studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_TOP);
            assertSame(capture, field(panel.inspectedResourceProperties(), "gameObjectPreview"));
            assertTrue(owned.getTextureObjectHandle() != 0);
            asset.entities.get(1).dimensions.width = 8192;
            loader.save(file, asset); studio.reloadAssetInspection();
            var large = (games.pixscape.studio.service.gameobject.GameObjectPreviewCapture)
                    field(panel.inspectedResourceProperties(), "gameObjectPreview");
            assertEquals(1024, large.region().getTexture().getWidth());
            assertTrue(large.region().getTexture().getHeight() <= 1024);
            // Nested transform: translation (36, 0), 90-degree rotation and uniform scale .75.
            asset = gameObjectAsset(); asset.entities.truncate(2);
            asset.entities.get(1).transform.rotationRad = com.badlogic.gdx.math.MathUtils.PI / 2;
            asset.entities.add(gameObjectSprite(3, 2, imageId, 8, 0, 120, 40));
            loader.save(file, asset); studio.reloadAssetInspection();
            assertEquals(0, owned.getTextureObjectHandle()); assertNull(image.getDrawable());
            capture = (games.pixscape.studio.service.gameobject.GameObjectPreviewCapture)
                    field(panel.inspectedResourceProperties(), "gameObjectPreview");
            assertEquals(38, capture.width(), .01f); assertEquals(98, capture.height(), .01f);
            screenshotGameObjectWidths(studio, "nested");
            image = panel.findActor("gameObjectAssetPreview");
            image.validate(); assertFitted(image, 38, 98);
            assertEquals("Small compositions are not enlarged", 38, image.getImageWidth(), .01f);
            owned = capture.region().getTexture();
            // Missing source must invalidate even a previously cached texture; no old image survives.
            project.parent().child("orig/images/composition.png").delete(); studio.reloadAssetInspection();
            assertNull(panel.findActor("gameObjectAssetPreview"));
            assertTrue(label(panel, "Preview").contains("missing"));
            assertEquals(0, owned.getTextureObjectHandle());
            assertTrue(borrowed.getTextureObjectHandle() != 0);
            assertFalse(project.parent().child("orig/images/composition.png").exists());
            writeImage(project.parent().child("orig/images/composition.png"), 64, 32);
            studio.reloadAssetInspection();
            capture = (games.pixscape.studio.service.gameobject.GameObjectPreviewCapture)
                    field(panel.inspectedResourceProperties(), "gameObjectPreview");
            owned = capture.region().getTexture();
            inspect(studio, AssetNode.Kind.HUD_SCREEN);
            assertEquals("Changing targets disposes captured pixels", 0, owned.getTextureObjectHandle());
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            capture = (games.pixscape.studio.service.gameobject.GameObjectPreviewCapture)
                    field(panel.inspectedResourceProperties(), "gameObjectPreview");
            owned = capture.region().getTexture();
            FileHandle next = project(studio, root.child("next"));
            open(studio, next);
            assertEquals("Changing projects disposes captured pixels", 0, owned.getTextureObjectHandle());
        });
    }

    private static void screenshotGameObjectWidths(StudioApplicationAdapter studio, String name) throws Exception {
        var split = (games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "centerRightSplit");
        var vertical = (games.pixscape.studio.ui.widget.DockSplitPane) field(studio.getDockManager(), "rightSplit");
        vertical.setSplitAmount(.95f);
        for (float amount : new float[]{.32f, .76f}) {
            split.setSplitAmount(amount);
            screenshot(studio, "gameobject-" + name + (amount < .5 ? "-large-panel" : "-narrow-panel"));
            var image = (com.kotcrab.vis.ui.widget.VisImage) inspector(studio).findActor("gameObjectAssetPreview");
            assertTrue(image.getImageWidth() <= inspector(studio).getWidth());
            assertTrue(((com.badlogic.gdx.scenes.scene2d.ui.Container<?>) image.getParent()).getClip());
        }
    }

    private static games.pixscape.runtime.gameobject.GameObjectAsset.GameObjectEntityData gameObjectSprite(
            int id, int parent, int image, float x, float y, float width, float height) {
        var data = new games.pixscape.runtime.gameobject.GameObjectAsset.GameObjectEntityData();
        data.sourceEntityId = id; data.parentSourceEntityId = parent;
        data.transform = new games.pixscape.runtime.gameobject.GameObjectAsset.TransformData();
        data.transform.x = x; data.transform.y = y; data.transform.scaleX = data.transform.scaleY = 1;
        data.entityIndex = new games.pixscape.runtime.gameobject.GameObjectAsset.EntityIndexData(); data.entityIndex.zIndex = id;
        data.meta = new games.pixscape.runtime.gameobject.GameObjectAsset.MetaData(); data.meta.kind = "SPRITE";
        data.visibility = new games.pixscape.runtime.gameobject.GameObjectAsset.VisibilityData(); data.visibility.visible = true;
        data.boundsFlags = new games.pixscape.runtime.gameobject.GameObjectAsset.BoundsFlagsData();
        data.boundsFlags.hasAabb = data.boundsFlags.hasObb = true;
        data.dimensions = new games.pixscape.runtime.gameobject.GameObjectAsset.DimensionsData();
        data.dimensions.width = width; data.dimensions.height = height;
        data.renderMaterial = new games.pixscape.runtime.gameobject.GameObjectAsset.RenderMaterialData();
        data.renderMaterial.blendModeId = games.pixscape.runtime.render.BlendMode.ALPHA.id;
        data.renderMaterial.shaderIdx = games.pixscape.runtime.service.ShaderRegistry.indexOf(
                games.pixscape.runtime.render.ShaderMode.TEXTURE_ARRAY.defaultShaderName());
        data.assetRef = new games.pixscape.runtime.gameobject.GameObjectAsset.AssetRefData(); data.assetRef.assetId = image;
        return data;
    }

    @Test public void gameObjectInspectionKeepsCanvasAndReusesItsEditor() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("gameobjects"));
            FileHandle source = project.parent().child("gameobjects/Assembly.gameobject");
            var loader = new games.pixscape.runtime.gameobject.GameObjectAssetLoader();
            loader.save(source, gameObjectAsset());
            open(studio, project);
            var context = studio.getSceneEditorContext();
            var scene = studio.getEditorDocumentManager().activeDocument();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity).x = 73f;
            context.world().getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(entity);
            context.identityRegistry().ensureStableId(entity);
            context.world().process(); context.selectionService().selectOnly(entity);
            studio.render(); studio.render();
            var before = snapshot(project.parent());
            boolean dirty = context.isDirty(), undo = context.historyManager().canUndo();
            PropertiesPanel panel = inspector(studio);
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            assertEquals("Assembly", label(panel, "Name"));
            assertEquals("gameobjects/Assembly.gameobject", label(panel, "Source"));
            assertEquals("Saved version", label(panel, "Document"));
            assertNull(panel.findActor("resourceAssetID"));
            var texts = new java.util.ArrayList<String>(); collectLabels(panel.inspectedResourceProperties(), texts);
            assertTrue(texts.contains("GAME OBJECT"));
            assertEquals(entity, context.selectionService().getSelectionSnapshot().first());
            assertEquals(73f, context.world().getMapper(TransformComponent.class).get(entity).x, 0f);
            assertEquals(dirty, context.isDirty()); assertEquals(undo, context.historyManager().canUndo());
            assertEquals("Inspection must not generate a thumbnail or rewrite the asset", before, snapshot(project.parent()));
            panel.findActor("openGameObjectAssetEditor").fire(new ChangeListener.ChangeEvent());
            var document = (games.pixscape.studio.document.GameObjectEditorDocument) studio.getEditorDocumentManager().activeDocument();
            assertEquals("gameobjects/Assembly.gameobject", document.assetId());
            assertNull(panel.inspectedResourceProperties());
            var history = document.context().historyManager();
            history.execute(new games.pixscape.studio.history.commands.ChangeEntityNameCommand(document.context().world(),
                    history.historyIds(), history.historyIds().ensureForEntity(document.rootEntityId()), "Root", "Unsaved root", 0));
            assertTrue(document.isDirty());
            studio.getEditorDocumentManager().activate(scene.key());
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            assertEquals("Saved version - editor has unsaved changes", label(panel, "Document"));
            studio.reloadAssetInspection();
            assertEquals("Saved version - editor has unsaved changes", label(panel, "Document"));
            assertEquals("Root", loader.load(source).entities.first().identity.name);
            int count = studio.getEditorDocumentManager().documents().size();
            panel.findActor("openGameObjectAssetEditor").fire(new ChangeListener.ChangeEvent());
            assertSame(document, studio.getEditorDocumentManager().activeDocument());
            assertEquals(count, studio.getEditorDocumentManager().documents().size());
            assertTrue(document.isDirty());
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            var save = StudioApplicationAdapter.class.getDeclaredMethod("saveGameObjectDocument", document.getClass());
            save.setAccessible(true); save.invoke(studio, document); flush(panel);
            assertEquals("Unsaved root", loader.load(source).entities.first().identity.name);
            assertEquals("Saved version - editor open", label(panel, "Document"));
            document.context().selectionService().clearSelection();
            document.context().selectionService().selectOnly(document.rootEntityId()); flush(panel);
            assertNull(panel.inspectedResourceProperties());
            EventFlow.i().publish(new EventFlow.GameObjectsChanged(-151)); flush(panel);
            assertNull("Save notification must not reclaim Inspector", panel.inspectedResourceProperties());
            assertEquals(73f, context.world().getMapper(TransformComponent.class).get(entity).x, 0f);
        });
    }

    @Test public void gameObjectInspectionRetainsTargetThroughDockingAndOnlyLeavesAfterDeletion() throws Exception {
        var closingView = new ResourceAssetProperties[1];
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("gameobjects"));
            FileHandle source = project.parent().child("gameobjects/Assembly.gameobject");
            var loader = new games.pixscape.runtime.gameobject.GameObjectAssetLoader();
            loader.save(source, gameObjectAsset());
            FileHandle next = project(studio, root.child("next"));
            loader.save(next.parent().child("gameobjects/Assembly.gameobject"), gameObjectAsset());
            open(studio, project);
            PropertiesPanel panel = inspector(studio);
            var context = studio.getSceneEditorContext();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(entity);
            context.identityRegistry().ensureStableId(entity);
            context.world().process(); context.selectionService().selectOnly(entity); flush(panel);
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            var view = panel.inspectedResourceProperties();
            studio.getDockManager().hide(panel); studio.getDockManager().show(panel);
            studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_BOTTOM);
            studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_TOP);
            assertSame(view, panel.inspectedResourceProperties());
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); flush(panel);
            assertEquals("Assembly", label(panel, "Name"));
            var target = studio.getAssetsPanel().getSelectedAssetNode();
            showDelete(studio, target); respond(lastDialog(studio), false);
            assertSame(view, panel.inspectedResourceProperties()); assertTrue(source.exists());
            var other = source.sibling("Other.gameobject"); loader.save(other, gameObjectAsset());
            showDelete(studio, new AssetNode(AssetNode.Kind.GAME_OBJECT, AssetNode.Root.GAME_OBJECTS, other.path(), "Other", null));
            respond(lastDialog(studio), true); flush(panel);
            assertSame(view, panel.inspectedResourceProperties()); assertFalse(other.exists());
            showDelete(studio, target); respond(lastDialog(studio), true); flush(panel);
            assertFalse(source.exists()); assertNull(panel.inspectedResourceProperties());
            assertEquals(entity, field(panel, "boundEntity"));
            loader.save(source, gameObjectAsset());
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); flush(panel);
            assertNull("Restoring a target must not reclaim focus", panel.inspectedResourceProperties());
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            context.selectionService().clearSelection(); flush(panel);
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            source.delete(); studio.reloadAssetInspection(); flush(panel);
            assertEquals(context.layerService().getFirstLayerEntity(), field(panel, "boundLayer"));
            loader.save(source, gameObjectAsset());
            studio.getEditorDocumentManager().clear();
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            source.delete(); studio.reloadAssetInspection(); flush(panel);
            assertTrue(((Group) field(panel, "contentHolder")).getChildren().isEmpty());
            loader.save(source, gameObjectAsset());
            inspect(studio, AssetNode.Kind.GAME_OBJECT);
            open(studio, next);
            assertNull(panel.inspectedResourceProperties()); assertNull(field(view, "gameObjectId"));
            assertNull(field(view, "displayedGameObject"));
            inspect(studio, AssetNode.Kind.GAME_OBJECT); closingView[0] = panel.inspectedResourceProperties();
        });
        assertNull(field(closingView[0], "gameObjectId"));
        assertNull(field(closingView[0], "displayedGameObject"));
    }

    @Test public void gameObjectInspectionRefreshesSavedDiagnosticsWithoutRepair() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("gameobjects"));
            FileHandle source = project.parent().child("gameobjects/Assembly.gameobject");
            var loader = new games.pixscape.runtime.gameobject.GameObjectAssetLoader();
            var asset = gameObjectAsset(); asset.entities.truncate(1); loader.save(source, asset);
            open(studio, project); inspect(studio, AssetNode.Kind.GAME_OBJECT);
            PropertiesPanel panel = inspector(studio);
            assertEquals("Empty composition", label(panel, "Preview"));
            loader.save(source, gameObjectAsset());
            EventFlow.i().publish(new EventFlow.GameObjectsChanged(-151)); flush(panel);
            assertEquals("No visual content", label(panel, "Preview"));
            screenshot(studio, "gameobject-card");
            source.writeString("invalid", false);
            // No file polling during ordinary frames; refresh happens only on notification/reload.
            flush(panel); assertEquals("No visual content", label(panel, "Preview"));
            studio.reloadAssetInspection();
            assertEquals("Unavailable: invalid source or missing composition resource", label(panel, "Preview"));
            assertEquals("invalid", source.readString());
            assertNotNull(panel.findActor("openGameObjectAssetEditor"));
            screenshot(studio, "gameobject-unavailable");
        });
    }

    private static games.pixscape.runtime.gameobject.GameObjectAsset gameObjectAsset() {
        var asset = new games.pixscape.runtime.gameobject.GameObjectAsset();
        asset.rootSourceEntityId = 1;
        for (int i = 1; i <= 3; i++) {
            var entity = new games.pixscape.runtime.gameobject.GameObjectAsset.GameObjectEntityData();
            entity.sourceEntityId = i; entity.parentSourceEntityId = i == 1 ? -1 : i - 1;
            entity.gameObject = new games.pixscape.runtime.gameobject.GameObjectAsset.GameObjectData();
            entity.transform = new games.pixscape.runtime.gameobject.GameObjectAsset.TransformData();
            entity.transform.x = i == 1 ? 0 : i * 18f;
            entity.transform.scaleX = entity.transform.scaleY = i == 1 ? 1f : .75f;
            entity.transform.rotationRad = i == 1 ? 0 : .4f;
            entity.entityIndex = new games.pixscape.runtime.gameobject.GameObjectAsset.EntityIndexData();
            entity.entityIndex.zIndex = i == 1 ? 0 : i;
            entity.meta = new games.pixscape.runtime.gameobject.GameObjectAsset.MetaData(); entity.meta.kind = "GAME_OBJECT";
            entity.identity = new games.pixscape.runtime.gameobject.GameObjectAsset.IdentityData(); entity.identity.name = "Root";
            asset.entities.add(entity);
        }
        return asset;
    }

    @Test public void imageInspectionFitsNativePixelsAndReadsRendererFilters() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("images"));
            int small = addImage(project.parent(), "pixel", 16, 16);
            int large = addImage(project.parent(), "wide", 640, 192);
            open(studio, project);
            PropertiesPanel panel = inspector(studio);
            var context = studio.getSceneEditorContext();
            var texture = studio.getCanvas().getAssetVisualResolver().resolveFirst(small, context.sceneIdentity()).texture();
            texture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
                    com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
            String catalog = studio.getSceneService().getAssetMetaDatabase().toSnapshotJson();
            var files = snapshot(project.parent());
            boolean dirty = context.historyManager().isDirty(), undo = context.historyManager().canUndo();
            inspectImage(studio, small);
            assertEquals("pixel", label(panel, "Name"));
            assertEquals("16 x 16 px", label(panel, "Dimensions"));
            assertEquals("orig/images/pixel.png", label(panel, "Source"));
            assertEquals("Effective - Studio standalone texture", label(panel, "Filtering"));
            assertEquals("Nearest", label(panel, "Min")); assertEquals("Linear", label(panel, "Mag"));
            assertNull(panel.findActor("resourceAssetID"));
            assertNull(panel.findActor("resourceAssetTextureprofile"));
            var node = studio.getAssetsPanel().getSelectedAssetNode();
            var thumb = games.pixscape.studio.ui.asset.AssetPreviewCache.get(node).getTexture();
            assertNotSame(texture, thumb);
            assertEquals(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, thumb.getMinFilter());
            var image = (com.kotcrab.vis.ui.widget.VisImage) panel.findActor("imageAssetPreview");
            studio.render(); studio.render();
            assertFitted(image, 16, 16);
            assertEquals(16, image.getImageWidth(), 0.001f);
            assertSame(texture, ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable) image.getDrawable()).getRegion().getTexture());
            studio.getDockManager().hide((games.pixscape.studio.ui.docking.DockablePanel) field(studio, "layersPanel"));
            screenshot(studio, "image-pixel-art");

            inspectImage(studio, large);
            assertNull("Abandoned UI actors release borrowed references", image.getDrawable());
            assertTrue("The Inspector does not dispose cache textures", texture.getTextureObjectHandle() != 0);
            assertEquals(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest, texture.getMinFilter());
            assertEquals(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, texture.getMagFilter());
            image = panel.findActor("imageAssetPreview");
            studio.render(); studio.render();
            assertEquals("640 x 192 px", label(panel, "Dimensions"));
            assertFitted(image, 640, 192);
            assertTrue(image.getImageWidth() < 640);
            screenshot(studio, "image-wide-transparent");
            var frame = (com.badlogic.gdx.scenes.scene2d.ui.Container<?>) image.getParent();
            for (float[] size : new float[][]{{110, 240}, {200, 40}, {900, 240}}) {
                frame.setSize(size[0], size[1]); frame.validate(); image.validate();
                assertFitted(image, 640, 192);
                assertEquals(Math.min(1f, Math.min(size[0] / 640, size[1] / 192)) * 640,
                        image.getImageWidth(), 0.01f);
            }
            assertEquals("640 x 192 px", label(panel, "Dimensions"));
            assertEquals(catalog, studio.getSceneService().getAssetMetaDatabase().toSnapshotJson());
            assertEquals(files, snapshot(project.parent()));
            assertEquals(dirty, context.historyManager().isDirty()); assertEquals(undo, context.historyManager().canUndo());
        });
    }

    @Test public void imageAtlasRegionsReadSharedEffectiveFiltersAndFollowPublication() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("images"));
            int first = addImage(project.parent(), "first", 64, 32);
            int second = addImage(project.parent(), "second", 12, 12);
            open(studio, project);
            FileHandle pageFile = root.child("fixture/page.png");
            writeImage(pageFile, 128, 64);
            FileHandle descriptor = pageFile.sibling("fixture.atlas");
            descriptor.writeString("page.png\nsize: 128,64\nformat: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\n"
                    + "first__a" + first + "\n  rotate: false\n  xy: 0,0\n  size: 64,32\n  orig: 64,32\n  offset: 0,0\n  index: -1\n"
                    + "second__a" + second + "\n  rotate: false\n  xy: 64,0\n  size: 12,12\n  orig: 12,12\n  offset: 0,0\n  index: -1\n", false);
            var atlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas(descriptor);
            var replacement = new com.badlogic.gdx.graphics.g2d.TextureAtlas(descriptor);
            var service = studio.getCanvas().getAtlasService();
            String tag = studio.getSceneEditorContext().sceneIdentity();
            try {
                var texture = atlas.getRegions().first().getTexture();
                assertEquals(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest, texture.getMinFilter());
                service.loadBorrowed(tag, atlas); // Runtime publication applies its actual Linear policy.
                inspectImage(studio, first);
                PropertiesPanel panel = inspector(studio);
                assertEquals("64 x 32 px", label(panel, "Dimensions"));
                assertEquals("page.png (" + tag + ")", label(panel, "Atlas page"));
                assertEquals("Effective - shared scene atlas texture", label(panel, "Filtering"));
                assertEquals("Linear", label(panel, "Min")); assertEquals("Linear", label(panel, "Mag"));
                assertTrue(descriptor.readString().contains("filter: Nearest,Nearest"));
                assertSame(texture, studio.getCanvas().getAssetVisualResolver().resolveFirst(second, tag).texture());
                var image = (com.kotcrab.vis.ui.widget.VisImage) panel.findActor("imageAssetPreview");
                var region = ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable) image.getDrawable()).getRegion();
                assertEquals(64, region.getRegionWidth()); assertEquals(32, region.getRegionHeight());
                texture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest,
                        com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
                flush(panel);
                assertEquals("Nearest", label(panel, "Min")); assertEquals("Linear", label(panel, "Mag"));
                studio.reloadAssetInspection();
                assertEquals(com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest, texture.getMinFilter());
                assertEquals(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, texture.getMagFilter());
                assertTrue(texture.getTextureObjectHandle() != 0);
                image = panel.findActor("imageAssetPreview");
                service.loadBorrowed(tag, replacement);
                flush(panel);
                assertNull(image.getDrawable());
                image = panel.findActor("imageAssetPreview");
                assertSame(replacement.getRegions().first().getTexture(),
                        ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable) image.getDrawable()).getRegion().getTexture());
                assertEquals("Linear", label(panel, "Min"));
                service.unload(tag); flush(panel);
                assertEquals("Effective - Studio standalone texture", label(panel, "Filtering"));
                assertNull(panel.findActor("resourceAssetAtlaspage"));
            } finally { service.unload(tag); atlas.dispose(); replacement.dispose(); }
        });
    }

    @Test public void imageInspectionLifecycleMissingSourceAndDeletionPreserveCanvas() throws Exception {
        var closingImage = new com.kotcrab.vis.ui.widget.VisImage[1];
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("images"));
            int id = addImage(project.parent(), "target", 24, 12);
            int other = addImage(project.parent(), "other", 12, 12);
            FileHandle next = project(studio, root.child("next"));
            int nextId = addImage(next.parent(), "next", 8, 8);
            open(studio, project);
            var context = studio.getSceneEditorContext();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(entity);
            context.identityRegistry().ensureStableId(entity);
            context.world().process(); context.selectionService().selectOnly(entity);
            studio.render(); studio.render();
            inspectImage(studio, id);
            PropertiesPanel panel = inspector(studio);
            assertEquals(entity, context.selectionService().getSelectionSnapshot().first());
            var image = (com.kotcrab.vis.ui.widget.VisImage) panel.findActor("imageAssetPreview");
            var texture = ((com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable) image.getDrawable()).getRegion().getTexture();
            studio.getDockManager().hide(panel);
            assertNotNull(image.getDrawable());
            studio.getDockManager().show(panel);
            studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_BOTTOM);
            studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_TOP);
            assertSame(image, panel.findActor("imageAssetPreview"));
            assertTrue(texture.getTextureObjectHandle() != 0);
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); flush(panel);
            assertEquals("target", label(panel, "Name"));
            assertNull(image.getDrawable());
            assertTrue(texture.getTextureObjectHandle() != 0);
            showDelete(studio, studio.getAssetsPanel().getSelectedAssetNode()); respond(lastDialog(studio), false);
            assertNotNull(panel.findActor("imageAssetPreview"));
            var reference = context.world().getMapper(games.pixscape.runtime.component.AssetRefComponent.class);
            reference.create(entity).assetId = id; context.world().process();
            assertThrows(IllegalStateException.class, () -> studio.getSceneService().deleteProjectAssets(Array.with(id)));
            flush(panel); assertNotNull(panel.findActor("imageAssetPreview"));
            reference.remove(entity); context.world().process();
            studio.getSceneService().deleteProjectAssets(Array.with(other)); flush(panel);
            assertEquals("target", label(panel, "Name"));
            var tree = (games.pixscape.studio.ui.tree.IdVisTree) field(studio.getItemTreePanel(), "tree");
            clickActor(studio, tree.findNode(entity).getLabel());
            assertNull(panel.inspectedResourceProperties());
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); flush(panel);
            assertNull("Reload must not reclaim focus", panel.inspectedResourceProperties());
            assertEquals(entity, field(panel, "boundEntity"));
            inspectImage(studio, id);
            FileHandle source = project.parent().child("orig/images/target.png");
            source.writeString("invalid pixels", false); studio.reloadAssetInspection();
            assertNull(panel.findActor("imageAssetPreview"));
            assertTrue(label(panel, "Preview unavailable").contains("Invalid image source"));
            assertEquals("invalid pixels", source.readString());
            source.delete(); studio.reloadAssetInspection();
            assertTrue(label(panel, "Preview unavailable").contains("missing"));
            assertNull(panel.findActor("resourceAssetMin"));
            studio.getSceneService().deleteProjectAssets(Array.with(id)); flush(panel);
            assertNull(panel.inspectedResourceProperties()); assertEquals(entity, field(panel, "boundEntity"));
            open(studio, next); inspectImage(studio, nextId);
            image = panel.findActor("imageAssetPreview");
            studio.render(); studio.render();
            open(studio, project);
            assertNull(image.getDrawable()); assertNull(panel.inspectedResourceProperties());
            open(studio, next); inspectImage(studio, nextId);
            closingImage[0] = panel.findActor("imageAssetPreview");
        });
        assertNull("Closing Studio releases references before cache disposal", closingImage[0].getDrawable());
    }

    private static void assertFitted(com.kotcrab.vis.ui.widget.VisImage image, int width, int height) {
        assertTrue(image.getImageWidth() > 0 && image.getImageHeight() > 0);
        assertTrue(image.getImageWidth() <= width && image.getImageHeight() <= height);
        assertTrue(image.getImageWidth() <= image.getWidth() + 0.001f);
        assertTrue(image.getImageHeight() <= image.getHeight() + 0.001f);
        assertEquals((float) width / height, image.getImageWidth() / image.getImageHeight(), 0.001f);
        // Scene2D snaps the centered offsets to whole pixels.
        assertEquals((image.getWidth() - image.getImageWidth()) / 2, image.getImageX(), 1f);
        assertEquals((image.getHeight() - image.getImageHeight()) / 2, image.getImageY(), 1f);
        assertEquals((image.getParent().getWidth() - image.getWidth()) / 2, image.getX(), 1f);
        assertEquals((image.getParent().getHeight() - image.getHeight()) / 2, image.getY(), 1f);
        assertTrue(((com.badlogic.gdx.scenes.scene2d.ui.Container<?>) image.getParent()).getClip());
    }

    private static int addImage(FileHandle dir, String name, int width, int height) {
        String source = "orig/images/" + name + ".png";
        writeImage(dir.child(source), width, height);
        var db = AssetMetaDatabase.load(dir.child("assets.json"));
        int id = db.registerIfAbsent(AssetType.IMAGE, "images/" + name, source, AssetMeta.AssetScope.USER).id();
        db.save(dir.child("assets.json")); return id;
    }

    private static void writeImage(FileHandle file, int width, int height) {
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixels.setColor(0.1f, 0.8f, 0.4f, 0.45f); pixels.fillRectangle(width / 8, height / 8, width * 3 / 4, height * 3 / 4);
        pixels.setColor(1f, 0.6f, 0.1f, 1f); pixels.fillRectangle(width / 4, height / 4, width / 4, height / 2);
        pixels.setColor(0.2f, 0.4f, 1f, 1f); pixels.fillCircle(width * 3 / 4, height / 2, height / 4);
        PixmapIO.writePNG(file, pixels); pixels.dispose();
    }

    private static void inspectImage(StudioApplicationAdapter studio, int id) throws Exception {
        flush(inspector(studio));
        var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
        tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.IMAGES, "", "", null));
        var thumbs = field(studio.getAssetsPanel(), "thumbsView");
        for (Object object : (Array<?>) field(thumbs, "tileWidgets")) {
            Actor actor = (Actor) object;
            if (actor.getUserObject() instanceof AssetNode node && node.assetId == id) {
                click(actor, 1); flush(inspector(studio)); return;
            }
        }
        fail("Missing image " + id);
    }

    @Test public void particlePreviewPlaybackAndRenderingAreIndependent() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("particles"));
            addParticle(project.parent(), "continuous", true);
            open(studio, project);
            inspect(studio, AssetNode.Kind.PARTICLE);
            PropertiesPanel panel = inspector(studio);
            assertNull(panel.findActor("resourceAssetID"));
            Object preview = field(panel.inspectedResourceProperties(), "particlePreview");
            var effect = (games.pixscape.runtime.particle.ParticleEffect) field(preview, "effect");
            var emitter = effect.getEmitters().first();
            var thumbnail = findParticle((AssetsThumbsView) field(studio.getAssetsPanel(), "thumbsView"));
            var thumbnailEffect = (games.pixscape.runtime.particle.ParticleEffect) field(thumbnail, "effect");
            assertNotSame(effect, thumbnailEffect);
            assertNotSame(emitter.getSprites().first().getTexture(), particleTexture(thumbnail));
            var sceneEffect = new games.pixscape.runtime.particle.ParticleEffect();
            sceneEffect.load(project.parent().child("orig/effects/continuous.p"), project.parent().child("orig/images"));
            sceneEffect.start(); sceneEffect.update(0.02f);
            float sceneTime = sceneEffect.getEmitters().first().durationTimer;
            var before = snapshot(project.parent());
            String catalog = studio.getSceneService().getAssetMetaDatabase().toSnapshotJson();
            var history = studio.getSceneEditorContext().historyManager();
            boolean undo = history.canUndo(), dirty = history.isDirty();
            var slider = (games.pixscape.studio.ui.widget.SimpleFloatSlider) panel.findActor("particlePreviewSpeed");
            assertNull(panel.findActor("particlePreviewMax"));
            try {
                assertEquals(1f, slider.getValue(), 0f);
                assertEquals(16f, slider.getMaxValue(), 0f);
                assertEquals("1.00x", ((VisLabel) panel.findActor("particlePreviewSpeedValue")).getText().toString());
                invokePreview(preview, "restart");
                studio.getUiStage().draw();
                Actor canvas = panel.findActor("particleAssetPreview");
                canvas.act(0.05f);
                assertEquals("Visible frames advance the preview", 50f, emitter.durationTimer, 0f);
                studio.getUiStage().draw(); studio.getUiStage().draw();
                assertEquals("Drawing alone never advances it", 50f, emitter.durationTimer, 0f);
                for (float speed : new float[]{0.01f, 0.25f, 1f, 4f, 16f}) {
                    slider.setValue(speed);
                    assertEquals(java.lang.String.format(java.util.Locale.ROOT, "%.2fx", speed),
                            ((VisLabel) panel.findActor("particlePreviewSpeedValue")).getText().toString());
                    invokePreview(preview, "restart");
                    for (int i = 0; i < 100; i++) advancePreview(preview, 0.01f);
                    // Continuous cycles still run; low speeds accumulate rather than losing motion.
                    assertFalse(effect.isComplete());
                    if (speed <= 1) assertEquals(speed * 1000, emitter.durationTimer, 11f);
                    assertTrue(emitter.getActiveCount() > 0);
                    assertEquals(100f, emitter.getVelocity().getHighMin(), 0f);
                }
                slider.setValue(1f); invokePreview(preview, "restart"); advancePreview(preview, 0.05f);
                float time = emitter.durationTimer;
                float x = emitter.getParticles()[0].getX();
                float y = emitter.getParticles()[0].getY();
                slider.setValue(0f);
                advancePreview(preview, 1000f);
                studio.getUiStage().draw(); studio.getUiStage().draw();
                assertEquals(time, emitter.durationTimer, 0f);
                assertEquals(x, emitter.getParticles()[0].getX(), 0f);
                slider.setValue(0.5f); advancePreview(preview, 0.04f);
                assertEquals(time + 20, emitter.durationTimer, 0f);
                assertEquals(2f, com.badlogic.gdx.math.Vector2.dst(x, y,
                        emitter.getParticles()[0].getX(), emitter.getParticles()[0].getY()), 0.001f);
                slider.setValue(0.25f);
                assertEquals(0.25f, slider.getValue(), 0f);
                ((com.kotcrab.vis.ui.widget.VisTextButton) panel.findActor("particlePreviewRestart"))
                        .fire(new ChangeListener.ChangeEvent());
                assertEquals(0, emitter.getActiveCount());
                assertEquals(0.25f, slider.getValue(), 0f);
                slider.setValue(16);
                advancePreview(preview, 1000f);
                assertEquals("A stalled frame simulates at most 800 ms", 800f, emitter.durationTimer, 0f);
                slider.setValue(0f);
                var batch = studio.getUiStage().getBatch();
                int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
                int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
                batch.setBlendFunctionSeparate(1, 1, 0, 1);
                batch.begin();
                ((Actor) panel.findActor("particleAssetPreview")).draw(batch, 1f);
                batch.end();
                assertEquals(1, batch.getBlendSrcFunc()); assertEquals(1, batch.getBlendDstFunc());
                assertEquals(0, batch.getBlendSrcFuncAlpha()); assertEquals(1, batch.getBlendDstFuncAlpha());
                batch.setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha);
                studio.getDockManager().hide((games.pixscape.studio.ui.docking.DockablePanel) field(studio, "layersPanel"));
                screenshot(studio, "particle-continuous-additive");
                screenshotBottom(studio, "particle-continuous-controls");
                assertEquals(sceneTime, sceneEffect.getEmitters().first().durationTimer, 0f);
                assertEquals(0, thumbnailEffect.getEmitters().first().getActiveCount());
                assertEquals(catalog, studio.getSceneService().getAssetMetaDatabase().toSnapshotJson());
                assertEquals(undo, history.canUndo()); assertEquals(dirty, history.isDirty());
                assertEquals(before, snapshot(project.parent()));
            } finally { sceneEffect.dispose(); }
        });
    }

    @Test public void particleInspectionRetainsSettingsAndReleasesOwnedResources() throws Exception {
        var closingTexture = new com.badlogic.gdx.graphics.Texture[1];
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("particles"));
            int id = addParticle(project.parent(), "burst", false);
            open(studio, project);
            var context = studio.getSceneEditorContext();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().process(); context.selectionService().selectOnly(entity);
            inspect(studio, AssetNode.Kind.PARTICLE);
            PropertiesPanel panel = inspector(studio);
            assertEquals(entity, context.selectionService().getSelectionSnapshot().first());
            Object preview = field(panel.inspectedResourceProperties(), "particlePreview");
            var effect = (games.pixscape.runtime.particle.ParticleEffect) field(preview, "effect");
            var texture = effect.getEmitters().first().getSprites().first().getTexture();
            var slider = (games.pixscape.studio.ui.widget.SimpleFloatSlider) panel.findActor("particlePreviewSpeed");
            slider.setValue(2f);
            for (int i = 0; i < 30; i++) advancePreview(preview, 0.05f);
            assertTrue("One-shot effects finish without auto-restart", effect.isComplete());
            invokePreview(preview, "restart"); advancePreview(preview, 0.05f);
            assertFalse(effect.isComplete());
            assertEquals(2f, slider.getValue(), 0f);
            slider.setValue(0f);
            studio.getDockManager().hide((games.pixscape.studio.ui.docking.DockablePanel) field(studio, "layersPanel"));
            screenshot(studio, "particle-burst-additive");
            screenshotBottom(studio, "particle-burst-controls");
            slider.setValue(2f);
            float time = effect.getEmitters().first().durationTimer;
            studio.getDockManager().hide(panel);
            ((Actor) preview).act(20f);
            assertEquals(time, effect.getEmitters().first().durationTimer, 0f);
            assertTrue(texture.getTextureObjectHandle() != 0);
            studio.getDockManager().show(panel);
            studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_TOP);
            assertSame(preview, field(panel.inspectedResourceProperties(), "particlePreview"));
            assertEquals(2f, slider.getValue(), 0f);
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance()); flush(panel);
            assertNotNull(panel.inspectedResourceProperties());
            assertEquals(0, texture.getTextureObjectHandle());
            assertEquals(2f, ((games.pixscape.studio.ui.widget.SimpleFloatSlider) panel.findActor("particlePreviewSpeed")).getValue(), 0f);
            showDelete(studio, studio.getAssetsPanel().getSelectedAssetNode());
            respond(lastDialog(studio), false);
            assertNotNull(panel.inspectedResourceProperties());
            // A second missing sprite must also release the first texture allocated by the failed load.
            preview = field(panel.inspectedResourceProperties(), "particlePreview");
            effect = (games.pixscape.runtime.particle.ParticleEffect) field(preview, "effect");
            effect.getEmitters().first().setImagePaths(Array.with("spark.png", "missing.png"));
            try (var writer = project.parent().child("orig/effects/burst.p").writer(false)) { effect.save(writer); }
            int managedTextures = com.badlogic.gdx.graphics.Texture.getNumManagedTextures();
            studio.reloadAssetInspection();
            assertNull(panel.findActor("particleAssetPreview"));
            assertEquals(managedTextures - 1, com.badlogic.gdx.graphics.Texture.getNumManagedTextures());
            assertTrue(label(panel, "Preview unavailable").contains("missing.png"));
            project.parent().child("orig/images/spark.png").delete();
            studio.reloadAssetInspection();
            assertNull(panel.findActor("particleAssetPreview"));
            assertTrue(label(panel, "Preview unavailable").contains("missing"));
            project.parent().child("orig/effects/burst.p").delete();
            studio.reloadAssetInspection();
            assertTrue(label(panel, "Preview unavailable").contains("file is missing"));
            studio.getSceneService().deleteProjectAssets(Array.with(id)); flush(panel);
            assertNull(panel.inspectedResourceProperties());
            assertEquals(entity, field(panel, "boundEntity"));
            FileHandle next = project(studio, root.child("next"));
            addParticle(next.parent(), "next", true); open(studio, next);
            inspect(studio, AssetNode.Kind.PARTICLE);
            preview = field(panel.inspectedResourceProperties(), "particlePreview");
            effect = (games.pixscape.runtime.particle.ParticleEffect) field(preview, "effect");
            texture = effect.getEmitters().first().getSprites().first().getTexture();
            studio.render(); studio.render();
            open(studio, project);
            assertEquals(0, texture.getTextureObjectHandle());
            assertNull(panel.inspectedResourceProperties());
            open(studio, next);
            inspect(studio, AssetNode.Kind.PARTICLE);
            preview = field(panel.inspectedResourceProperties(), "particlePreview");
            effect = (games.pixscape.runtime.particle.ParticleEffect) field(preview, "effect");
            texture = effect.getEmitters().first().getSprites().first().getTexture();
            studio.getDockManager().show((games.pixscape.studio.ui.docking.DockablePanel) field(studio, "layersPanel"));
            clickLayer(studio, studio.getSceneEditorContext().layerService().getFirstLayerEntity(), 1);
            assertNull(panel.inspectedResourceProperties());
            assertEquals(0, texture.getTextureObjectHandle());
            inspect(studio, AssetNode.Kind.PARTICLE);
            preview = field(panel.inspectedResourceProperties(), "particlePreview");
            effect = (games.pixscape.runtime.particle.ParticleEffect) field(preview, "effect");
            closingTexture[0] = effect.getEmitters().first().getSprites().first().getTexture();
        });
        assertEquals("Closing Studio releases the active preview", 0, closingTexture[0].getTextureObjectHandle());
    }

    private static int addParticle(FileHandle dir, String name, boolean continuous) throws Exception {
        Pixmap pixels = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixels.setColor(1f, 0.45f, 0.05f, 0.8f); pixels.fillCircle(8, 8, 7);
        PixmapIO.writePNG(dir.child("orig/images/spark.png"), pixels); pixels.dispose();
        var emitter = new games.pixscape.runtime.particle.ParticleEmitter();
        emitter.setName(name); emitter.setMaxParticleCount(100); emitter.setContinuous(continuous); emitter.setAdditive(true);
        emitter.setImagePaths(Array.with("spark.png"));
        emitter.getDuration().setLow(1000); emitter.getLife().setHigh(800);
        emitter.getEmission().setHigh(50); emitter.getXScale().setHigh(12);
        emitter.getVelocity().setActive(true); emitter.getVelocity().setHigh(100);
        emitter.getAngle().setActive(true); emitter.getAngle().setHigh(0, 360);
        emitter.getTransparency().setHigh(1); emitter.getTint().setColors(new float[]{1f, 0.6f, 0.1f});
        var effect = new games.pixscape.runtime.particle.ParticleEffect(); effect.getEmitters().add(emitter);
        try (var writer = dir.child("orig/effects/" + name + ".p").writer(false)) { effect.save(writer); }
        var db = AssetMetaDatabase.load(dir.child("assets.json"));
        int id = db.registerIfAbsent(AssetType.PARTICLE, "particles/" + name, "orig/effects/" + name + ".p", AssetMeta.AssetScope.USER).id();
        db.save(dir.child("assets.json")); return id;
    }

    private static void advancePreview(Object preview, float delta) throws Exception {
        var method = preview.getClass().getDeclaredMethod("advance", float.class);
        method.setAccessible(true); method.invoke(preview, delta);
    }

    private static void invokePreview(Object preview, String name) throws Exception {
        var method = preview.getClass().getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(preview);
    }

    @Test public void resourceInspectionSurvivesVisualDetachAndRefresh() throws Exception {
        withStudio((studio, root) -> {
            open(studio, project(studio, root.child("first")));
            PropertiesPanel panel = inspector(studio);
            for (AssetNode.Kind kind : kinds()) {
                inspect(studio, kind);
                String identity = label(panel, kind == AssetNode.Kind.HUD_SCREEN ? "Screen ID" : "ID");
                for (int pass = 0; pass < 2; pass++) {
                    var preview = (com.badlogic.gdx.scenes.scene2d.ui.Label) panel.findActor(
                            kind == AssetNode.Kind.FONT ? "fontAssetPreview" : "skinAssetPreview");
                    var texture = preview != null ? preview.getStyle().font.getRegion().getTexture() : null;
                    studio.getDockManager().hide(panel);
                    if (texture != null) assertTrue(texture.getTextureObjectHandle() != 0);
                    studio.reloadAssetInspection();
                    if (texture != null) assertEquals("Refresh releases the superseded preview", 0, texture.getTextureObjectHandle());
                    flush(panel);
                    studio.getDockManager().show(panel);
                    studio.render();
                    assertEquals(identity, label(panel, kind == AssetNode.Kind.HUD_SCREEN ? "Screen ID" : "ID"));
                    studio.getDockManager().dock(panel, games.pixscape.studio.ui.docking.DockSlot.RIGHT_TOP);
                    studio.reloadAssetInspection();
                    studio.render();
                    assertEquals(identity, label(panel, kind == AssetNode.Kind.HUD_SCREEN ? "Screen ID" : "ID"));
                    if (kind != AssetNode.Kind.HUD_SCREEN) {
                        assertNotNull(panel.findActor(kind == AssetNode.Kind.FONT ? "fontAssetPreview" : "skinAssetPreview"));
                    }
                }
            }
        });
    }

    @Test public void clickingAlreadySelectedItemRetakesInspector() throws Exception {
        withStudio((studio, root) -> {
            open(studio, project(studio, root.child("first")));
            var context = studio.getSceneEditorContext();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(entity);
            context.identityRegistry().ensureStableId(entity);
            int other = context.world().create();
            context.world().getMapper(TransformComponent.class).create(other);
            context.world().getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(other);
            context.identityRegistry().ensureStableId(other);
            context.selectionService().selectOnly(entity);
            studio.render(); studio.render();
            var tree = (games.pixscape.studio.ui.tree.IdVisTree) field(studio.getItemTreePanel(), "tree");
            assertNotNull(tree.findNode(entity));
            inspect(studio, AssetNode.Kind.FONT);
            var snapshots = new java.util.ArrayList<com.badlogic.gdx.utils.IntArray>();
            EventFlow.Listener<EventFlow.SelectionChanged> observer = event -> {
                if (event.source() == games.pixscape.studio.service.SelectionService.SelectionSource.TREE)
                    snapshots.add(new com.badlogic.gdx.utils.IntArray(context.selectionService().getSelectionSnapshot()));
            };
            EventFlow.i().subscribe(EventFlow.SelectionChanged.class, observer);
            try {
                clickActor(studio, tree.findNode(entity).getLabel());
                assertNull(inspector(studio).inspectedResourceProperties());
                assertEquals(entity, context.selectionService().getSelectionSnapshot().first());
                assertEquals(entity, field(inspector(studio), "boundEntity"));
                assertEquals("One activation, without an empty intermediate selection", 1, snapshots.size());
                assertEquals(entity, snapshots.getFirst().first());
                snapshots.clear();
                clickActor(studio, tree.findNode(other).getLabel());
                assertEquals("An ordinary click is handled once", 1, snapshots.size());
                assertEquals(other, context.selectionService().getSelectionSnapshot().first());
                snapshots.clear();
                clickActor(studio, tree.findNode(other).getLabel());
                clickActor(studio, tree.findNode(other).getLabel());
                assertEquals("Both clicks remain independent activations", 2, snapshots.size());

                withKey(com.badlogic.gdx.Input.Keys.CONTROL_LEFT, () -> clickActor(studio, tree.findNode(entity).getLabel()));
                assertEquals(2, context.selectionService().getSelectionSnapshot().size);
                context.selectionService().selectOnly(entity);
                studio.render();
                withKey(com.badlogic.gdx.Input.Keys.SHIFT_LEFT, () -> clickActor(studio, tree.findNode(other).getLabel()));
                assertEquals(2, context.selectionService().getSelectionSnapshot().size);

                context.selectionService().selectOnly(entity);
                studio.render();
                inspect(studio, AssetNode.Kind.FONT);
                snapshots.clear();
                Actor row = tree.findNode(entity).getLabel();
                var point = studio.getUiStage().stageToScreenCoordinates(row.localToStageCoordinates(
                        new com.badlogic.gdx.math.Vector2(row.getWidth() / 2, row.getHeight() / 2)));
                studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, 0);
                studio.getUiStage().touchDragged(Math.round(point.x), Math.round(point.y + 70), 0);
                studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y + 70), 0, 0);
                assertTrue("A drag is not an explicit row activation", snapshots.isEmpty());
                assertNotNull(inspector(studio).inspectedResourceProperties());

                var layerNode = tree.findNode(context.layerService().getFirstLayerEntity());
                tree.getSelection().set(layerNode);
                inspect(studio, AssetNode.Kind.FONT);
                boolean expanded = layerNode.isExpanded();
                point = studio.getUiStage().stageToScreenCoordinates(tree.localToStageCoordinates(
                        new com.badlogic.gdx.math.Vector2(layerNode.getActor().getX() - 5,
                                layerNode.getActor().getY() + layerNode.getActor().getHeight() / 2)));
                studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, 0);
                studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, 0);
                assertEquals(!expanded, layerNode.isExpanded());
                assertNotNull("Expanding a node does not reclaim the Inspector", inspector(studio).inspectedResourceProperties());
            } finally {
                EventFlow.i().unsubscribe(EventFlow.SelectionChanged.class, observer);
            }
        });
    }

    @Test public void tileInspectionSurvivesUnrelatedBrowserReloadAndDeletion() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("first"));
            AssetMetaDatabase db = AssetMetaDatabase.load(project.parent().child("assets.json"));
            var set = db.registerIfAbsent(AssetType.TILESET, "tiles/set", null, AssetMeta.AssetScope.USER);
            var tile = (games.pixscape.studio.asset.TileAssetMeta) db.registerIfAbsent(
                    AssetType.TILE, "tiles/set/0", "orig/tiles/set/0.png", AssetMeta.AssetScope.USER);
            tile.tilesetId = set.id(); tile.sheetIndex = 7;
            Pixmap pixels = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
            PixmapIO.writePNG(project.parent().child(tile.sourceRelPath()), pixels); pixels.dispose();
            db.save(project.parent().child("assets.json"));
            open(studio, project);
            studio.render(); studio.render();
            var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.TILES, "set", "set", null));
            studio.render(); studio.render();
            AssetsThumbsView thumbs = (AssetsThumbsView) field(studio.getAssetsPanel(), "thumbsView");
            Array<?> widgets = (Array<?>) field(thumbs, "tileWidgets");
            for (int i = 0; i < widgets.size; i++) {
                Actor actor = (Actor) widgets.get(i);
                if (actor.getUserObject() instanceof AssetNode node && node.assetId == tile.id()) {
                    click(actor, 1);
                    break;
                }
            }
            assertNotNull(inspector(studio).inspectedTileProperties());
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
            flush(inspector(studio));
            assertNotNull("Technical reload preserves the inspected tile", inspector(studio).inspectedTileProperties());
            studio.getSceneService().deleteProjectAssets(Array.with(1)); // Unrelated Font.
            flush(inspector(studio));
            assertNotNull("Deleting another asset preserves the inspected tile", inspector(studio).inspectedTileProperties());
            assertEquals("7", ((VisLabel) inspector(studio).findActor("tileLocalId")).getText().toString());
            var context = studio.getSceneEditorContext();
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().process();
            context.selectionService().selectOnly(entity);
            flush(inspector(studio));
            studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
            flush(inspector(studio));
            assertNull("A browser reload cannot reclaim canvas focus", inspector(studio).inspectedTileProperties());
            assertEquals(entity, field(inspector(studio), "boundEntity"));
            click(selectedTile(studio, AssetNode.Kind.IMAGE), 1);
            assertNotNull(inspector(studio).inspectedTileProperties());
            assertEquals(entity, context.selectionService().getSelectionSnapshot().first());
            showDelete(studio, studio.getAssetsPanel().getSelectedAssetNode());
            respond(lastDialog(studio), false);
            assertNotNull(inspector(studio).inspectedTileProperties());
            studio.getSceneService().deleteProjectAssets(Array.with(tile.id()));
            flush(inspector(studio));
            assertNull(inspector(studio).inspectedTileProperties());
            assertEquals(entity, field(inspector(studio), "boundEntity"));
        });
    }

    @Test public void rebuildingParticleGridDisposesOnlyAbandonedOwnedTextures() throws Exception {
        withStudio((studio, root) -> {
            FileHandle project = project(studio, root.child("first"));
            FileHandle dir = project.parent();
            Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.png").copyTo(dir.child("orig/images/particle.png"));
            var emitter = new games.pixscape.runtime.particle.ParticleEmitter();
            emitter.setName("Preview"); emitter.setMaxParticleCount(2);
            emitter.setImagePaths(Array.with("particle.png"));
            var effect = new games.pixscape.runtime.particle.ParticleEffect();
            effect.getEmitters().add(emitter);
            try (var writer = dir.child("orig/effects/preview.p").writer(false)) { effect.save(writer); }
            AssetMetaDatabase db = AssetMetaDatabase.load(dir.child("assets.json"));
            db.registerIfAbsent(AssetType.PARTICLE, "particles/preview", "orig/effects/preview.p", AssetMeta.AssetScope.USER);
            db.save(dir.child("assets.json"));
            open(studio, project);
            studio.render(); studio.render();
            var tree = (FolderTreeView) field(studio.getAssetsPanel(), "treeView");
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.PARTICLES, "", "", null));
            studio.render(); studio.render();
            AssetsThumbsView thumbs = (AssetsThumbsView) field(studio.getAssetsPanel(), "thumbsView");
            var shared = com.kotcrab.vis.ui.VisUI.getSkin().getFont("default-font").getRegion().getTexture();
            for (int i = 0; i < 3; i++) {
                var thumbnail = findParticle(thumbs);
                assertNotNull("Particle thumbnail in " + tree.getSelectedFolder() + ": " + field(thumbs, "currentAssets"), thumbnail);
                var owned = particleTexture(thumbnail);
                assertTrue(owned.getTextureObjectHandle() != 0);
                studio.getDockManager().hide(studio.getAssetsPanel());
                assertTrue("Temporary detach must retain reusable previews", owned.getTextureObjectHandle() != 0);
                studio.getDockManager().show(studio.getAssetsPanel());
                thumbs.setTileSize(64 + i * 8);
                studio.render(); studio.render();
                assertEquals("Replaced thumbnail releases its owned texture", 0, owned.getTextureObjectHandle());
                thumbnail.startPreview(); thumbnail.act(1); thumbnail.stopPreview(); thumbnail.dispose();
                assertTrue(shared.getTextureObjectHandle() != 0);
            }
            var owned = particleTexture(findParticle(thumbs));
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.HUD, "", "", null));
            assertEquals(0, owned.getTextureObjectHandle());
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.PARTICLES, "", "", null));
            studio.render(); studio.render();
            owned = particleTexture(findParticle(thumbs));
            open(studio, project(studio, root.child("next")));
            assertEquals("Changing project abandons its particles", 0, owned.getTextureObjectHandle());
            open(studio, project);
            studio.render(); studio.render();
            tree.selectFolder(new AssetNode(AssetNode.Kind.FOLDER, AssetNode.Root.PARTICLES, "", "", null));
            studio.render(); studio.render();
            owned = particleTexture(findParticle(thumbs));
            studio.getSceneService().unloadProjectToEmptyEditor();
            assertEquals(0, owned.getTextureObjectHandle());
            assertTrue(shared.getTextureObjectHandle() != 0);
        });
    }

    private static games.pixscape.studio.ui.asset.ParticleThumbnail findParticle(Actor actor) {
        if (actor instanceof games.pixscape.studio.ui.asset.ParticleThumbnail particle) return particle;
        if (actor instanceof Group group) for (Actor child : group.getChildren()) {
            var found = findParticle(child); if (found != null) return found;
        }
        return null;
    }

    private static com.badlogic.gdx.graphics.Texture particleTexture(
            games.pixscape.studio.ui.asset.ParticleThumbnail thumbnail) throws Exception {
        var effect = (games.pixscape.runtime.particle.ParticleEffect) field(thumbnail, "effect");
        return effect.getEmitters().first().getSprites().first().getTexture();
    }

    private static void clickActor(StudioApplicationAdapter studio, Actor actor) throws Exception {
        studio.render(); studio.render();
        assertSame("Click target was rebuilt/detached: " + actor, studio.getUiStage(), actor.getStage());
        var point = studio.getUiStage().stageToScreenCoordinates(actor.localToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(actor.getWidth() / 2, actor.getHeight() / 2)));
        assertTrue(studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, 0));
        studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, 0);
        flush(inspector(studio));
    }

    @FunctionalInterface private interface InputAction { void run() throws Exception; }

    private static void withKey(int key, InputAction action) throws Exception {
        var input = Gdx.input;
        Gdx.input = (com.badlogic.gdx.Input) java.lang.reflect.Proxy.newProxyInstance(
                com.badlogic.gdx.Input.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.Input.class},
                (proxy, method, args) -> method.getName().equals("isKeyPressed")
                        ? (Integer) args[0] == key : method.invoke(input, args));
        try { action.run(); } finally { Gdx.input = input; }
    }

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
            var files = snapshot(project.parent());

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
            assertEquals(files, snapshot(project.parent()));
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
                studio.getAssetsPanel().reloadFromProject(ProjectConfig.getInstance());
                flush(panel);
                assertNull("Technical restoration cannot reclaim canvas focus", panel.inspectedResourceProperties());
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
                assertNull("Identical IDs in another project are not browser selections", studio.getAssetsPanel().getSelectedAssetNode());
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
        withStudio(700, scenario);
    }

    private static void withStudio(int height, Scenario scenario) throws Exception {
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
        gl.setWindowedMode(1000, height);
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
                : kind == AssetNode.Kind.GAME_OBJECT ? AssetNode.Root.GAME_OBJECTS
                : kind == AssetNode.Kind.PARTICLE ? AssetNode.Root.PARTICLES
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
        var method = AssetsThumbsView.class.getDeclaredMethod(hud ? "showDeleteHudScreenDialog"
                : node.kind == AssetNode.Kind.GAME_OBJECT ? "showDeleteGameObjectDialog" : "showDeleteAssetDialog",
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
            // Background HUD rendering owns these transient atlas files and may remove them at any time.
            for (var path : paths.filter(path -> !root.relativize(path).toString().replace('\\', '/')
                    .startsWith("atlases/.tmp/")).filter(Files::isRegularFile).toList())
                result.put(root.relativize(path).toString(), java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
        }
        return result;
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
