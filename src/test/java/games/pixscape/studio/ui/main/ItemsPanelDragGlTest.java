package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import games.pixscape.runtime.component.*;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.commands.ItemMovePreparation;
import games.pixscape.studio.ui.tree.EntityNode;
import games.pixscape.studio.ui.tree.IdVisTree;
import org.junit.Test;

import java.util.List;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

/**
 * Actual Studio/Stage gestures, separate from visual inspection of captured frames.
 */
public class ItemsPanelDragGlTest {
    @Test
    public void expandedGroupDestinationSurvivesIntermediateHorizontalMotion() throws Exception {
        withStudio(900, (studio, root) -> {
            open(studio, project(studio, root.child("stable-target")));
            var context = studio.getSceneEditorContext();
            int a = item(studio, "A", 30), b = item(studio, "B", 20), c = item(studio, "C", 10);
            for (int i = 0; i < 10; i++) {
                int child = item(studio, "Child " + i, i);
                context.world().getMapper(GameObjectMemberComponent.class).create(child).parentStableId = context.identityRegistry().getStableId(a);
            }
            var indices = context.world().getMapper(EntityIndexComponent.class);
            indices.get(a).zIndex = 30;
            indices.get(b).zIndex = 20;
            indices.get(c).zIndex = 10;
            settle(studio);
            click(studio, a);
            int cursor = context.historyManager().getCursor();
            Vector2 end = point(studio, tree(studio).findNode(b), .08f);
            begin(studio, a, end);
            var prepared = plan(studio);
            assertNotNull(prepared);
            assertEquals(1, prepared.destination().insertionIndex());
            for (int i = 0; i < 12; i++) {
                end.x += 1;
                drag(studio, end);
                studio.getUiStage().act(.016f);
                assertSame("Horizontal motion " + i + " must retain the displayed destination", prepared, plan(studio));
                assertEquals(cursor, context.historyManager().getCursor());
                assertEquals(30, indices.get(a).zIndex);
            }
            screenshot(studio, "items-stable-expanded-ghost");
            end = displayedPoint(studio, tree(studio).findNode(c), .08f);
            drag(studio, end);
            assertEquals(2, plan(studio).destination().insertionIndex());
            // Empty projected space must stay empty: never fall back to the old descendant rows.
            Vector2 blank = studio.getUiStage().stageToScreenCoordinates(tree(studio).localToStageCoordinates(new Vector2(120, tree(studio).getHeight() - 5)));
            drag(studio, blank);
            assertNull(plan(studio));
            for (int i = 0; i < 8; i++) {
                blank.x++;
                drag(studio, blank);
                assertNull(plan(studio));
            }
            end = displayedPoint(studio, tree(studio).findNode(b), .08f);
            drag(studio, end);
            assertEquals(1, plan(studio).destination().insertionIndex());
            release(studio, end);
            assertEquals(cursor + 1, context.historyManager().getCursor());
            assertTrue(indices.get(b).zIndex > indices.get(a).zIndex);
            assertTrue(indices.get(a).zIndex > indices.get(c).zIndex);
            context.historyManager().undo();
            settle(studio);
            assertEquals(30, indices.get(a).zIndex);
            context.historyManager().redo();
            settle(studio);
            assertTrue(indices.get(b).zIndex > indices.get(a).zIndex);
        });
    }

    @Test
    public void expandedMultipleSourcesCanChangeTargetExpandAndReenter() throws Exception {
        withStudio(900, (studio, root) -> {
            open(studio, project(studio, root.child("multiple-stability")));
            var context = studio.getSceneEditorContext();
            int a = item(studio, "A", 30), b = item(studio, "B", 20), c = item(studio, "C", 10), firstChild = -1;
            for (int i = 0; i < 11; i++) {
                int child = item(studio, "Child " + i, i);
                if (i == 0) firstChild = child;
                context.world().getMapper(GameObjectMemberComponent.class).create(child).parentStableId = context.identityRegistry().getStableId(i == 10 ? b : a);
            }
            var indices = context.world().getMapper(EntityIndexComponent.class);
            indices.get(a).zIndex = 30;
            indices.get(b).zIndex = 20;
            indices.get(c).zIndex = 10;
            settle(studio);
            var tree = tree(studio);
            tree.getSelection().clear();
            tree.getSelection().addAll(com.badlogic.gdx.utils.Array.with(tree.findNode(a), tree.findNode(firstChild), tree.findNode(c)));
            tree.findNode(b).setExpanded(false);
            tree.invalidateHierarchy();
            settle(studio);
            int cursor = context.historyManager().getCursor();
            Vector2 end = point(studio, tree.findNode(b), .5f);
            begin(studio, a, end);
            assertTrue(tree.findNode(b).isExpanded());
            var prepared = plan(studio);
            assertNotNull(prepared);
            assertEquals(2, prepared.sources().size());
            for (int i = 0; i < 12; i++) {
                end.x++;
                drag(studio, end);
                assertSame(prepared, plan(studio));
            }
            int ghosts = 0;
            for (Object row : (List<?>) field(tree, "preview")) if ((boolean) field(row, "ghost")) ghosts++;
            assertEquals(2, ghosts);
            screenshot(studio, "items-stable-multiple-open-ghost");
            Vector2 rootTarget = displayedPoint(studio, tree.findNode(context.layerService().getLayerEntity(0)), .5f);
            drag(studio, rootTarget);
            assertEquals(0, plan(studio).destination().parentStableId());
            drag(studio, new Vector2(900, 400));
            assertNull(plan(studio));
            assertNull(field(tree, "preview"));
            end = point(studio, tree.findNode(b), .5f);
            drag(studio, end);
            assertEquals(context.identityRegistry().getStableId(b), plan(studio).destination().parentStableId());
            for (int i = 0; i < 8; i++) {
                end.x--;
                drag(studio, end);
                assertNotNull(plan(studio));
            }
            assertEquals(cursor, context.historyManager().getCursor());
            assertEquals(0, parent(studio, a));
            assertEquals(0, parent(studio, c));
            release(studio, end);
            assertEquals(cursor + 1, context.historyManager().getCursor());
            assertEquals(context.identityRegistry().getStableId(b), parent(studio, a));
            assertEquals(context.identityRegistry().getStableId(b), parent(studio, c));
            context.historyManager().undo();
            settle(studio);
            assertEquals(0, parent(studio, a));
            assertEquals(0, parent(studio, c));
            context.historyManager().redo();
            settle(studio);
            assertEquals(context.identityRegistry().getStableId(b), parent(studio, a));
        });
    }

    @Test
    public void modifiersNoopInformativeRowsAndCollapsedDestinations() throws Exception {
        withStudio(900, (studio, root) -> {
            open(studio, project(studio, root.child("clicks")));
            var context = studio.getSceneEditorContext();
            int a = item(studio, "First", 10), b = item(studio, "Second", 20), c = item(studio, "Third", 30);
            context.world().getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).create(c);
            settle(studio);
            var tree = tree(studio);
            click(studio, a);
            modifiedClick(studio, b, Input.Keys.CONTROL_LEFT);
            assertEquals(2, context.selectionService().getSelectionSnapshot().size);
            Vector2 click = point(studio, tree.findNode(a), .5f);
            down(studio, click);
            assertEquals(2, context.selectionService().getSelectionSnapshot().size);
            release(studio, click);
            assertEquals(1, context.selectionService().getSelectionSnapshot().size);
            modifiedClick(studio, c, Input.Keys.SHIFT_LEFT);
            assertEquals(3, context.selectionService().getSelectionSnapshot().size);
            click(studio, a);
            int cursor = context.historyManager().getCursor();
            Vector2 same = point(studio, tree.findNode(a), .95f);
            begin(studio, a, same);
            assertNotNull(plan(studio));
            assertTrue(plan(studio).isNoop());
            release(studio, same);
            assertEquals(cursor, context.historyManager().getCursor());
            assertNull(plan(studio));
            tree.findNode(c).setExpanded(true);
            tree.invalidateHierarchy();
            settle(studio);
            var body = tree.findBodyNode(c);
            assertNotNull(body);
            assertNotNull(body.getActor().getStage());
            Vector2 bodyPoint = point(studio, body, .5f);
            down(studio, bodyPoint);
            drag(studio, new Vector2(bodyPoint.x + 20, bodyPoint.y));
            assertNull(plan(studio));
            release(studio, new Vector2(bodyPoint.x + 20, bodyPoint.y));

            tree.findNode(c).setExpanded(false);
            tree.invalidateHierarchy();
            settle(studio);
            Vector2 inside = point(studio, tree.findNode(c), .5f);
            begin(studio, a, inside);
            assertTrue(tree.findNode(c).isExpanded());
            screenshot(studio, "items-collapsed-open-ghost");
            release(studio, inside);
            assertEquals(context.identityRegistry().getStableId(c), parent(studio, a));
            // A refresh keeps a deliberately collapsed unrelated group and scroll position.
            tree.findNode(c).setExpanded(false);
            studio.getItemTreePanel().markDirty();
            settle(studio);
            assertFalse(tree.findNode(c).isExpanded());
            context.historyManager().undo();
            settle(studio);
            assertEquals(0, parent(studio, a));
            // Lock compatibility belongs to preparation, not a UI filter that silently drops sources.
            context.layerService().setLayerLocked(context.layerService().getLayerEntity(0), true);
            settle(studio);
            Vector2 locked = point(studio, tree.findNode(b), .5f);
            begin(studio, a, locked);
            assertNull(plan(studio));
            assertNull(field(tree, "preview"));
            release(studio, locked);
            assertEquals(0, parent(studio, a));
        });
    }

    @Test
    public void edgeScrollingAndContextLossCancelWithoutMutation() throws Exception {
        withStudio(700, (studio, root) -> {
            open(studio, project(studio, root.child("scroll")));
            var context = studio.getSceneEditorContext();
            int source = -1;
            for (int i = 0; i < 45; i++) source = item(studio, "Item " + i, i);
            for (int i = 0; i < 10; i++) {
                int child = item(studio, "Scrolled child " + i, i);
                context.world().getMapper(GameObjectMemberComponent.class).create(child).parentStableId = context.identityRegistry().getStableId(source);
            }
            context.world().getMapper(EntityIndexComponent.class).get(source).zIndex = 1000;
            settle(studio);
            ScrollPane scroll = (ScrollPane) field(studio.getItemTreePanel(), "scroller");
            scroll.setScrollY(0);
            scroll.updateVisualScroll();
            settle(studio);
            int cursor = context.historyManager().getCursor();
            Vector2 edge = studio.getUiStage().stageToScreenCoordinates(scroll.localToStageCoordinates(new Vector2(scroll.getWidth() * .5f, 12)));
            begin(studio, source, edge);
            // Feedback reserves space only during a gesture; target the now-visible scroll edge.
            edge = studio.getUiStage().stageToScreenCoordinates(scroll.localToStageCoordinates(new Vector2(scroll.getWidth() * .5f, 12)));
            drag(studio, edge);
            screenshot(studio, "items-scroll-start");
            for (int i = 0; i < 500; i++) studio.getUiStage().act(.02f);
            assertTrue("scroll=" + scroll.getScrollY() + " max=" + scroll.getMaxY() + " active=" + field(field(studio.getItemTreePanel(), "itemMoves"), "document"), scroll.getScrollY() > 200);
            assertEquals(scroll.getMaxY(), scroll.getScrollY(), 1f);
            assertEquals(cursor, context.historyManager().getCursor());
            screenshot(studio, "items-scrolled-ghost");
            Vector2 upper = studio.getUiStage().stageToScreenCoordinates(scroll.localToStageCoordinates(new Vector2(scroll.getWidth() * .5f, scroll.getHeight() - 12)));
            drag(studio, upper);
            for (int i = 0; i < 500; i++) studio.getUiStage().act(.02f);
            assertEquals(0, scroll.getScrollY(), 1f);
            screenshot(studio, "items-scrolled-top-ghost");
            Vector2 outside = new Vector2(900, 400);
            drag(studio, outside);
            float stopped = scroll.getScrollY();
            for (int i = 0; i < 20; i++) studio.getUiStage().act(.02f);
            assertEquals(stopped, scroll.getScrollY(), 0f);
            release(studio, outside);
            assertEquals(cursor, context.historyManager().getCursor());
            scroll.setScrollY(0);
            scroll.updateVisualScroll();
            settle(studio);
            begin(studio, source, edge);
            studio.getItemTreePanel().setVisible(false);
            studio.getUiStage().act(.02f);
            assertNull(plan(studio));
            release(studio, edge);
            studio.getItemTreePanel().setVisible(true);
            settle(studio);
            begin(studio, source, edge);
            open(studio, project(studio, root.child("replacement")));
            release(studio, edge);
            assertNull(plan(studio));
            assertFalse(studio.getSceneEditorContext().historyManager().canUndo());
        });
    }

    @Test
    public void canvasOrderMatchesDropAndUndo() throws Exception {
        withStudio(900, (studio, root) -> {
            var file = project(studio, root.child("canvas"));
            for (String name : new String[]{"red", "blue"}) {
                addImage(file.parent(), name, 120, 90);
                Pixmap pixels = new Pixmap(120, 90, Pixmap.Format.RGBA8888);
                try {
                    pixels.setColor(name.equals("red") ? 0xff4040ff : 0x4090ffff);
                    pixels.fill();
                    PixmapIO.writePNG(file.parent().child("orig/images/" + name + ".png"), pixels);
                } finally {
                    pixels.dispose();
                }
            }
            open(studio, file);
            int red = studio.getCanvas().getEditorOps().createStandaloneSprite("red.png", -180, 20);
            int blue = studio.getCanvas().getEditorOps().createStandaloneSprite("blue.png", -130, 45);
            settle(studio);
            click(studio, blue);
            var content = (com.kotcrab.vis.ui.widget.VisTable) field(studio.getItemTreePanel(), "worldContent");
            var scroll = (ScrollPane) field(studio.getItemTreePanel(), "scroller");
            var feedback = (Actor) field(field(studio.getItemTreePanel(), "itemMoves"), "feedback");
            assertEquals("Only the tree and transient DnD feedback remain", 2, content.getChildren().size);
            assertEquals(0f, feedback.getHeight(), 0f);
            assertEquals("The tree reaches the bottom without an empty toolbar/status strip", content.getPadBottom(), scroll.getY(), .01f);
            var context = studio.getSceneEditorContext();
            int cursor = context.historyManager().getCursor();
            screenshot(studio, "items-canvas-before");
            Vector2 end = point(studio, tree(studio).findNode(red), .08f);
            begin(studio, blue, end);
            assertTrue(feedback.getHeight() > 0);
            screenshot(studio, "items-canvas-ghost");
            release(studio, end);
            assertEquals(0f, feedback.getHeight(), 0f);
            assertEquals(content.getPadBottom(), scroll.getY(), .01f);
            assertEquals(cursor + 1, context.historyManager().getCursor());
            var indices = context.world().getMapper(EntityIndexComponent.class);
            assertTrue(indices.get(red).zIndex > indices.get(blue).zIndex);
            assertEquals(blue, context.selectionService().getFirstSelectedEntityId());
            assertEquals(blue, field(field(studio, "propertiesPanel"), "boundEntity"));
            screenshot(studio, "items-canvas-after");
            context.historyManager().undo();
            settle(studio);
            assertTrue(indices.get(blue).zIndex > indices.get(red).zIndex);
            screenshot(studio, "items-canvas-undo");
        });
    }

    @Test
    public void multipleReorderThenAttachDetachAndTransferCommitOnlyOnRelease() throws Exception {
        withStudio(900, (studio, root) -> {
            var file = project(studio, root.child("items"));
            open(studio, file);
            var context = studio.getSceneEditorContext();
            int a = item(studio, "A", 30), b = item(studio, "B", 20), c = item(studio, "C", 10);
            var indices = context.world().getMapper(EntityIndexComponent.class);
            indices.get(a).zIndex = 30;
            indices.get(b).zIndex = 20;
            indices.get(c).zIndex = 10;
            context.layerService().addLayerTop("Empty layer");
            settle(studio);
            var tree = tree(studio);
            tree.getSelection().clear();
            tree.getSelection().addAll(com.badlogic.gdx.utils.Array.with(tree.findNode(a), tree.findNode(b)));
            settle(studio);
            int cursor = context.historyManager().getCursor();
            var selected = context.selectionService().getSelectionSnapshot();
            Vector2 end = point(studio, tree.findNode(c), .08f);
            begin(studio, a, end);
            var prepared = plan(studio);
            assertNotNull(prepared);
            assertEquals(1, prepared.destination().insertionIndex());
            assertEquals(2, prepared.sources().size());
            assertEquals(cursor, context.historyManager().getCursor());
            assertEquals(selected, context.selectionService().getSelectionSnapshot());
            assertEquals(30, context.world().getMapper(EntityIndexComponent.class).get(a).zIndex);
            for (int i = 0; i < 10; i++) studio.getUiStage().act(.016f);
            assertSame(prepared, plan(studio));
            screenshot(studio, "items-multiple-ghost");
            release(studio, end);
            assertEquals(cursor + 1, context.historyManager().getCursor());
            assertEquals(selected, context.selectionService().getSelectionSnapshot());
            assertTrue(context.world().getMapper(EntityIndexComponent.class).get(c).zIndex > context.world().getMapper(EntityIndexComponent.class).get(a).zIndex);
            context.historyManager().undo();
            settle(studio);
            assertEquals(30, context.world().getMapper(EntityIndexComponent.class).get(a).zIndex);
            context.historyManager().redo();
            settle(studio);

            tree.findNode(c).setExpanded(false);
            end = point(studio, tree.findNode(c), .5f);
            begin(studio, a, end);
            assertEquals(context.identityRegistry().getStableId(c), plan(studio).destination().parentStableId());
            assertTrue(tree.findNode(c).isExpanded());
            screenshot(studio, "items-inside-ghost");
            release(studio, end);
            assertEquals(context.identityRegistry().getStableId(c), parent(studio, a));
            assertEquals(context.identityRegistry().getStableId(c), parent(studio, b));
            assertEquals(a, ((EntityNode) tree.findNode(c).getChildren().first()).getEntityId());

            click(studio, b);
            end = point(studio, tree.findNode(a), .95f);
            begin(studio, b, end);
            assertEquals(context.identityRegistry().getStableId(c), plan(studio).destination().parentStableId());
            screenshot(studio, "items-sibling-ghost");
            release(studio, end);
            assertEquals(b, ((EntityNode) tree.findNode(c).getChildren().first()).getEntityId());
            context.historyManager().undo();
            settle(studio);
            assertEquals(a, ((EntityNode) tree.findNode(c).getChildren().first()).getEntityId());
            context.historyManager().redo();
            settle(studio);
            // The lower quarter of an expanded group means after the entire group, not inside it.
            click(studio, a);
            end = point(studio, tree.findNode(c), .08f);
            begin(studio, a, end);
            assertEquals(0, plan(studio).destination().parentStableId());
            screenshot(studio, "items-after-group-ghost");
            release(studio, end);
            assertEquals(0, parent(studio, a));
            assertEquals(context.identityRegistry().getStableId(c), parent(studio, b));
            context.historyManager().undo();
            settle(studio);
            tree.getSelection().clear();
            tree.getSelection().addAll(com.badlogic.gdx.utils.Array.with(tree.findNode(a), tree.findNode(b)));
            settle(studio);

            int empty = context.layerService().getLayerEntity(1);
            end = point(studio, tree.findNode(empty), .5f);
            begin(studio, a, end);
            screenshot(studio, "items-empty-layer-ghost");
            release(studio, end);
            assertEquals(0, parent(studio, a));
            assertEquals(1, context.world().getMapper(EntityIndexComponent.class).get(a).layerIndex);
            assertEquals(0, parent(studio, b));
            context.historyManager().undo();
            settle(studio);
            assertEquals(context.identityRegistry().getStableId(c), parent(studio, a));
            context.historyManager().redo();
            settle(studio);
            studio.getSceneService().saveProjectAndCurrentScene();
            int stable = context.identityRegistry().getStableId(a);
            open(studio, file);
            settle(studio);
            int restored = studio.getSceneEditorContext().identityRegistry().findByStableId(stable);
            assertEquals(1, studio.getSceneEditorContext().layerService().getEffectiveLayerIndex(restored));
            screenshot(studio, "items-reloaded");
        });
    }

    @Test
    public void normalizedGhostsRefusalsStaleDropAndCancellation() throws Exception {
        withStudio(900, (studio, root) -> {
            open(studio, project(studio, root.child("cancel")));
            var context = studio.getSceneEditorContext();
            int p = item(studio, "Parent", 30), child = item(studio, "Child", 20), q = item(studio, "Target", 10);
            context.world().getMapper(GameObjectMemberComponent.class).create(child).parentStableId = context.identityRegistry().getStableId(p);
            settle(studio);
            var tree = tree(studio);
            tree.getSelection().clear();
            tree.getSelection().addAll(com.badlogic.gdx.utils.Array.with(tree.findNode(p), tree.findNode(child)));
            settle(studio);
            int cursor = context.historyManager().getCursor();
            Vector2 end = point(studio, tree.findNode(q), .5f);
            begin(studio, p, end);
            assertEquals(List.of(context.identityRegistry().getStableId(p)), plan(studio).sources());
            var rows = (List<?>) field(tree, "preview");
            int ghosts = 0;
            for (Object row : rows) if ((boolean) field(row, "ghost")) ghosts++;
            assertEquals(1, ghosts);
            screenshot(studio, "items-normalized-ghost");
            studio.getUiStage().keyDown(Input.Keys.ESCAPE);
            release(studio, end);
            assertEquals(cursor, context.historyManager().getCursor());
            assertEquals(0, parent(studio, p));

            begin(studio, p, point(studio, tree.findNode(child), .5f));
            assertNull(plan(studio));
            assertNull(field(tree, "preview"));
            screenshot(studio, "items-refused");
            release(studio, point(studio, tree.findNode(child), .5f));
            assertEquals(cursor, context.historyManager().getCursor());
            begin(studio, p, end);
            context.world().getMapper(TransformComponent.class).get(q).x += 10; // No event/publication: final revalidation must refuse.
            release(studio, end);
            assertEquals(cursor, context.historyManager().getCursor());
            begin(studio, p, end);
            release(studio, new Vector2(900, 400));
            assertEquals(cursor, context.historyManager().getCursor());
            begin(studio, p, end);
            drag(studio, new Vector2(900, 400));
            drag(studio, end);
            assertNotNull("Re-entering the same position must restore the destination", plan(studio));
            studio.getUiStage().keyDown(Input.Keys.ESCAPE);
            release(studio, end);
            begin(studio, p, end);
            studio.getUiStage().cancelTouchFocus();
            settle(studio);
            assertNull(plan(studio));
            assertEquals(cursor, context.historyManager().getCursor());
            begin(studio, p, end);
            studio.getItemTreePanel().bindSceneContext(context);
            release(studio, end);
            assertNull(plan(studio));
            assertEquals(cursor, context.historyManager().getCursor());
            // Ordinary release is still selection, and sub-threshold motion is still a click.
            Vector2 click = point(studio, tree.findNode(q), .5f);
            down(studio, click);
            drag(studio, new Vector2(click.x + 2, click.y));
            release(studio, click);
            assertEquals(q, context.selectionService().getFirstSelectedEntityId());
        });
    }

    private static int item(StudioApplicationAdapter studio, String name, int z) {
        int entity = studio.getCanvas().getEditorOps().createGameObject(0, 0);
        var world = studio.getSceneEditorContext().world();
        world.getMapper(PixscapeIdentityComponent.class).get(entity).name = name;
        world.getMapper(EntityIndexComponent.class).get(entity).zIndex = z;
        return entity;
    }

    private static int parent(StudioApplicationAdapter studio, int entity) {
        var m = studio.getSceneEditorContext().world().getMapper(GameObjectMemberComponent.class).getSafe(entity, null);
        return m == null ? 0 : m.parentStableId;
    }

    private static IdVisTree tree(StudioApplicationAdapter studio) throws Exception {
        return (IdVisTree) field(studio.getItemTreePanel(), "tree");
    }

    private static ItemMovePreparation.Prepared plan(StudioApplicationAdapter studio) throws Exception {
        return (ItemMovePreparation.Prepared) field(field(studio.getItemTreePanel(), "itemMoves"), "prepared");
    }

    private static Vector2 point(StudioApplicationAdapter studio, EntityNode node, float y) {
        Actor actor = node.getActor();
        return studio.getUiStage().stageToScreenCoordinates(actor.localToStageCoordinates(new Vector2(Math.max(5, actor.getWidth() / 2), actor.getHeight() * y)));
    }

    private static Vector2 displayedPoint(StudioApplicationAdapter studio, EntityNode node, float y) throws Exception {
        var tree = tree(studio);
        List<?> rows = (List<?>) field(tree, "preview");
        if (rows != null) for (Object row : rows)
            if (field(row, "node") == node && !(boolean) field(row, "ghost")) {
                float top = (float) field(row, "top"), height = (float) field(row, "height");
                return studio.getUiStage().stageToScreenCoordinates(tree.localToStageCoordinates(new Vector2(node.getActor().getX() + 10, top - height * (1 - y))));
            }
        return point(studio, node, y);
    }

    private static void settle(StudioApplicationAdapter studio) {
        EventFlow.i().flush();
        studio.render();
        studio.render();
    }

    private static void click(StudioApplicationAdapter studio, int entity) throws Exception {
        Vector2 p = point(studio, tree(studio).findNode(entity), .5f);
        down(studio, p);
        release(studio, p);
    }

    private static void modifiedClick(StudioApplicationAdapter studio, int entity, int key) throws Exception {
        Input original = Gdx.input;
        Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) ->
                method.getName().equals("isKeyPressed") && (int) args[0] == key ? true : method.invoke(original, args));
        try {
            click(studio, entity);
        } finally {
            Gdx.input = original;
        }
    }

    private static void down(StudioApplicationAdapter studio, Vector2 p) {
        assertTrue(studio.getUiStage().touchDown(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT));
    }

    private static void drag(StudioApplicationAdapter studio, Vector2 p) {
        studio.getUiStage().touchDragged(Math.round(p.x), Math.round(p.y), 0);
    }

    private static void begin(StudioApplicationAdapter studio, int entity, Vector2 p) throws Exception {
        down(studio, point(studio, tree(studio).findNode(entity), .5f));
        drag(studio, p);
    }

    private static void release(StudioApplicationAdapter studio, Vector2 p) {
        studio.getUiStage().touchUp(Math.round(p.x), Math.round(p.y), 0, Input.Buttons.LEFT);
        settle(studio);
    }
}
