package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.kotcrab.vis.ui.widget.VisSplitPane;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.hud.SceneHudAssociationService;
import games.pixscape.studio.ui.layer.LayerRow;
import games.pixscape.studio.ui.layer.LayersPanel;
import org.junit.Test;

import java.util.Arrays;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

/** Real Stage pointer/key events against the docked Studio, including held-edge scrolling. */
public class LayersPanelDragGlTest {
    @Test public void sceneOverlapChangesOnlyOnDropAndUndoesExactly() throws Exception {
        withStudio(900, (studio, root) -> {
            var file = project(studio, root.child("overlap"));
            addImage(file.parent(), "red", 150, 110); addImage(file.parent(), "blue", 120, 130);
            solidImage(file.parent().child("orig/images/red.png"), 150, 110, 0xe45040ff);
            solidImage(file.parent().child("orig/images/blue.png"), 120, 130, 0x4088e4ff);
            open(studio, file); prepare(studio, 2);
            var context = studio.getSceneEditorContext(); var layers = context.layerService();
            int bottom = layers.getLayerEntity(0), top = layers.getLayerEntity(1);
            layers.meta(0).name = "Red rectangle"; layers.meta(1).name = "Blue rectangle";
            context.selectionService().setActivelayerId(bottom);
            int red = studio.getCanvas().getEditorOps().createStandaloneSprite("red.png", -180, 0);
            context.selectionService().setActivelayerId(top);
            int blue = studio.getCanvas().getEditorOps().createStandaloneSprite("blue.png", -140, 30);
            assertTrue(red >= 0 && blue >= 0);
            context.selectionService().clearSelection(); settle(studio);
            screenshot(studio, "layers-scene-before");
            var indices = context.world().getMapper(EntityIndexComponent.class);
            int redZ = indices.get(red).zIndex, blueZ = indices.get(blue).zIndex;
            Vector2 end = point(studio, row(studio, top), .65f, .95f);
            begin(studio, bottom, end);
            screenshot(studio, "layers-scene-hover");
            assertEquals(0, indices.get(red).layerIndex); assertEquals(1, indices.get(blue).layerIndex);
            release(studio, end);
            screenshot(studio, "layers-scene-drop");
            assertEquals(1, indices.get(red).layerIndex); assertEquals(0, indices.get(blue).layerIndex);
            assertEquals(redZ, indices.get(red).zIndex); assertEquals(blueZ, indices.get(blue).zIndex);
            context.historyManager().undo(); settle(studio);
            screenshot(studio, "layers-scene-undo");
            assertEquals(0, indices.get(red).layerIndex); assertEquals(1, indices.get(blue).layerIndex);
        });
    }

    private static void solidImage(FileHandle file, int width, int height, int color) {
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try { pixels.setColor(color); pixels.fill(); PixmapIO.writePNG(file, pixels); }
        finally { pixels.dispose(); }
    }

    @Test public void movesCommitOnceOnReleaseAndPreserveSelectionAndInspector() throws Exception {
        withStudio(900, (studio, root) -> {
            var file = project(studio, root.child("layers")); open(studio, file);
            prepare(studio, 5);
            var context = studio.getSceneEditorContext();
            var layers = context.layerService(); var history = context.historyManager();
            int[] initial = order(layers);
            // Lock/Spatial are properties of ordinary layers, not reorder restrictions.
            layers.setLayerLocked(initial[0], true);
            context.world().getMapper(LayerComponent.class).get(initial[3]).spatialEnabled = true;
            EventFlow.i().publish(new EventFlow.LayerSpatialDepthChanged(initial[3], 0));
            int entity = context.world().create();
            context.world().getMapper(TransformComponent.class).create(entity);
            context.world().process(); context.selectionService().selectOnly(entity);
            settle(studio);
            Object inspector = field(studio, "propertiesPanel");
            assertEquals(entity, field(inspector, "boundEntity"));
            int selected = context.selectionService().getActivelayerId();
            // Bottom to top, top to bottom, and intermediate upward/downward moves.
            for (int[] move : new int[][]{{0, 4}, {4, 0}, {1, 3}, {3, 1}}) {
                int[] before = order(layers); int cursor = history.getCursor();
                int source = before[move[0]], target = before[move[1]];
                Vector2 end = point(studio, row(studio, target), .65f, move[1] > move[0] ? .95f : .05f);
                begin(studio, source, end);
                assertArrayEquals(before, order(layers));
                assertEquals(cursor, history.getCursor());
                assertNotNull(field(panel(studio), "movingRow"));
                int gap = (int) field(panel(studio), "destination");
                for (int i = 0; i < 8; i++) studio.getUiStage().act(.016f);
                assertEquals("Stationary ghost must not oscillate", gap, field(panel(studio), "destination"));
                screenshot(studio, "layers-ghost-" + move[0] + "-" + move[1]);
                release(studio, end);
                assertEquals(cursor + 1, history.getCursor());
                assertEquals(move[1], layers.indexOfLayerEntity(source));
                assertEquals(selected, context.selectionService().getActivelayerId());
                assertEquals(entity, field(inspector, "boundEntity"));
                int[] after = order(layers);
                history.undo(); settle(studio); assertArrayEquals(before, order(layers));
                history.redo(); settle(studio); assertArrayEquals(after, order(layers));
            }
            assertArrayEquals(initial, order(layers));
            Vector2 top = point(studio, row(studio, initial[4]), .65f, .95f);
            begin(studio, initial[0], top); release(studio, top);
            String[] saved = names(layers);
            studio.getSceneService().saveProjectAndCurrentScene();
            screenshot(studio, "layers-reordered");
            open(studio, file); settle(studio);
            assertArrayEquals(saved, names(studio.getSceneEditorContext().layerService()));
        });
    }

    @Test public void cancellationsControlsHudAndContextChangesNeverApplyAMove() throws Exception {
        withStudio(900, (studio, root) -> {
            var file = project(studio, root.child("cancel")); open(studio, file); prepare(studio, 5);
            var context = studio.getSceneEditorContext(); var layers = context.layerService();
            var associations = (SceneHudAssociationService) field(studio, "sceneHudAssociationService");
            assertTrue(associations.associate((SceneEditorDocument) studio.getEditorDocumentManager().activeDocument(), "hud/Screen"));
            settle(studio);
            int[] before = order(layers); int cursor = context.historyManager().getCursor();
            Vector2 source = point(studio, row(studio, before[2]), .65f, .5f);
            begin(studio, before[2], new Vector2(source.x + 8, source.y)); release(studio, source);
            assertEquals(cursor, context.historyManager().getCursor());
            for (String kind : new String[]{"outside", "escape", "hud", "hide", "rebind"}) {
                Vector2 end = point(studio, row(studio, before[4]), .65f, .95f);
                begin(studio, before[0], end);
                switch (kind) {
                    case "outside" -> { end.set(10, 10); drag(studio, end); }
                    case "escape" -> {
                        assertTrue(studio.getUiStage().keyDown(Input.Keys.ESCAPE));
                        end = point(studio, row(studio, before[0]), .65f, .5f);
                        drag(studio, end); // Even returning to the pressed row must not select it.
                    }
                    case "hud" -> { end = point(studio, row(studio, -1), .65f, .5f); drag(studio, end);
                        assertEquals(-1, field(panel(studio), "destination")); }
                    case "hide" -> panel(studio).remove();
                    case "rebind" -> panel(studio).bindSceneContext(context);
                }
                release(studio, end);
                assertArrayEquals(kind, before, order(layers));
                assertEquals(kind, cursor, context.historyManager().getCursor());
                assertNull(field(panel(studio), "movingRow"));
                if (kind.equals("hide")) {
                    panel(studio).getDockManager().show(panel(studio)); settle(studio);
                }
            }
            // HUD cannot be dragged. Row controls never initiate a move.
            for (Actor handle : new Actor[]{row(studio, -1).getChildren().get(2),
                    row(studio, before[2]).getChildren().first(), row(studio, before[2]).getChildren().get(1)}) {
                Vector2 start = point(studio, handle, .5f, .5f);
                down(studio, start); drag(studio, new Vector2(start.x + 20, start.y - 35));
                assertNull(field(panel(studio), "movingRow")); release(studio, new Vector2(start.x + 20, start.y - 35));
            }
            // A completed ordinary click still selects and inspects, including an already active layer.
            Vector2 click = point(studio, row(studio, before[3]), .65f, .5f);
            down(studio, click); drag(studio, new Vector2(click.x + 3, click.y));
            assertNull("Motion below the usual threshold is still a click", field(panel(studio), "movingRow"));
            release(studio, click);
            assertEquals(before[3], context.selectionService().getActivelayerId());
            assertEquals(before[3], field(field(studio, "propertiesPanel"), "boundLayer"));
            LayerRow controlled = row(studio, before[3]);
            var eye = (com.badlogic.gdx.scenes.scene2d.ui.CheckBox) controlled.getChildren().first();
            boolean visible = eye.isChecked();
            Vector2 eyePoint = point(studio, eye, .5f, .5f); down(studio, eyePoint); release(studio, eyePoint);
            assertEquals(!visible, ((com.badlogic.gdx.scenes.scene2d.ui.CheckBox) row(studio, before[3]).getChildren().first()).isChecked());
            var lock = (com.badlogic.gdx.scenes.scene2d.ui.CheckBox) row(studio, before[3]).getChildren().get(1);
            boolean locked = lock.isChecked();
            Vector2 lockPoint = point(studio, lock, .5f, .5f); down(studio, lockPoint); release(studio, lockPoint);
            assertEquals(!locked, ((com.badlogic.gdx.scenes.scene2d.ui.CheckBox) row(studio, before[3]).getChildren().get(1)).isChecked());
            Vector2 historyEnd = point(studio, row(studio, before[4]), .65f, .95f);
            begin(studio, before[0], historyEnd);
            context.historyManager().undo(); context.historyManager().redo();
            release(studio, historyEnd);
            assertArrayEquals("Returning to the same state still invalidates the old gesture", before, order(layers));
            assertEquals(cursor, context.historyManager().getCursor());
            // A structural change before queued events flush invalidates indices at release.
            Vector2 end = point(studio, row(studio, before[4]), .65f, .95f);
            begin(studio, before[0], end); layers.addLayerTop("External"); release(studio, end);
            assertEquals(cursor, context.historyManager().getCursor());
            assertEquals(0, layers.indexOfLayerEntity(before[0]));
            // Project switch also clears focus/ghost ownership, with no command in the new project.
            settle(studio); end = point(studio, row(studio, before[4]), .65f, .95f);
            begin(studio, before[0], end);
            var second = project(studio, root.child("second")); open(studio, second); release(studio, end);
            assertNull(field(panel(studio), "movingRow"));
            assertFalse(studio.getSceneEditorContext().historyManager().canUndo());
        });
    }

    @Test public void heldEdgesScrollAndRecomputeDestinationWithoutMutatingTheScene() throws Exception {
        withStudio(700, (studio, root) -> {
            open(studio, project(studio, root.child("scroll"))); prepare(studio, 38);
            var layers = studio.getSceneEditorContext().layerService(); int[] before = order(layers);
            ScrollPane scroll = (ScrollPane) field(panel(studio), "scroller");
            scroll.setScrollY(0); scroll.updateVisualScroll(); settle(studio);
            Vector2 edge = point(studio, scroll, .65f, .02f);
            begin(studio, before[37], edge);
            for (int i = 0; i < 500; i++) { studio.getUiStage().act(.02f); studio.getUiStage().draw(); }
            assertTrue(scroll.getScrollY() > 200);
            assertEquals(scroll.getMaxY(), scroll.getScrollY(), 1f);
            assertArrayEquals(before, order(layers));
            assertEquals(37, field(panel(studio), "destination"));
            screenshot(studio, "layers-scrolled-ghost");
            release(studio, edge);
            assertEquals(0, layers.indexOfLayerEntity(before[37]));
            // Reverse scroll while holding at the upper edge.
            Vector2 upper = point(studio, scroll, .65f, .98f);
            begin(studio, before[37], upper);
            for (int i = 0; i < 500; i++) { studio.getUiStage().act(.02f); studio.getUiStage().draw(); }
            assertEquals(0, scroll.getScrollY(), 1f);
            screenshot(studio, "layers-scrolled-top-ghost");
            drag(studio, new Vector2(10, 10));
            float stopped = scroll.getScrollY();
            for (int i = 0; i < 20; i++) studio.getUiStage().act(.02f);
            assertEquals(stopped, scroll.getScrollY(), 0);
            release(studio, new Vector2(10, 10));
            assertEquals(0, layers.indexOfLayerEntity(before[37]));
        });
    }

    private static void prepare(StudioApplicationAdapter studio, int count) throws Exception {
        var layers = studio.getSceneEditorContext().layerService();
        while (layers.count() < count) layers.addLayerTop("Layer " + layers.count());
        Object docks = field(studio, "dockManager");
        ((VisSplitPane) field(docks, "rightSplit")).setSplitAmount(.35f);
        settle(studio);
    }
    private static LayersPanel panel(StudioApplicationAdapter studio) throws Exception {
        return (LayersPanel) field(studio, "layersPanel");
    }
    private static LayerRow row(StudioApplicationAdapter studio, int id) throws Exception {
        for (Actor actor : ((Group) field(panel(studio), "listTable")).getChildren())
            if (actor instanceof LayerRow row && row.getEntityId() == id) return row;
        throw new AssertionError("Missing layer " + id);
    }
    private static int[] order(LayerService layers) {
        int[] ids = new int[layers.count()]; for (int i = 0; i < ids.length; i++) ids[i] = layers.getLayerEntity(i); return ids;
    }
    private static String[] names(LayerService layers) {
        return Arrays.stream(layers.getLayerUIs().toArray(LayerService.LayerUI.class)).map(LayerService.LayerUI::name).toArray(String[]::new);
    }
    private static Vector2 point(StudioApplicationAdapter studio, Actor actor, float x, float y) {
        return studio.getUiStage().stageToScreenCoordinates(actor.localToStageCoordinates(new Vector2(actor.getWidth() * x, actor.getHeight() * y)));
    }
    private static void settle(StudioApplicationAdapter studio) { EventFlow.i().flush(); studio.render(); studio.render(); }
    private static void down(StudioApplicationAdapter studio, Vector2 point) {
        assertTrue(studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT));
    }
    private static void drag(StudioApplicationAdapter studio, Vector2 point) {
        studio.getUiStage().touchDragged(Math.round(point.x), Math.round(point.y), 0);
    }
    private static void begin(StudioApplicationAdapter studio, int id, Vector2 end) throws Exception {
        Vector2 start = point(studio, row(studio, id), .65f, .5f); down(studio, start); drag(studio, end);
    }
    private static void release(StudioApplicationAdapter studio, Vector2 end) {
        studio.getUiStage().touchUp(Math.round(end.x), Math.round(end.y), 0, Input.Buttons.LEFT); settle(studio);
    }
}
