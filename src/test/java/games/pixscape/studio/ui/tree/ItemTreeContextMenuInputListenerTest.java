package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ItemTreeContextMenuInputListenerTest {
    @BeforeClass
    public static void loadVisUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void validLayerRightClickActivatesOnDownAndShowsOnlyOnUp() {
        Stage stage = stage();
        try {
            EntityNode layer = new EntityNode(
                    "Main", null, 17, true,
                    EntityNode.NodeKind.LAYER);
            StubTree tree = new StubTree(layer);
            stage.addActor(tree);

            IntArray activated = new IntArray();
            IntArray presented = new IntArray();
            float[] presentationCoordinates = new float[2];
            ItemTreeContextMenuInputListener<Object> listener = new ItemTreeContextMenuInputListener<>(
                    tree,
                    node -> node.isLayerNode() && node.getEntityId() == 17,
                    node -> activated.add(node.getEntityId()),
                    () -> null,
                    (node, context, shownStage, stageX, stageY) -> {
                        assertEquals(stage, shownStage);
                        presented.add(node.getEntityId());
                        presentationCoordinates[0] = stageX;
                        presentationCoordinates[1] = stageY;
                    });

            InputEvent event = inputEvent(stage, 120f, 80f);
            assertTrue(listener.touchDown(event, 4f, 9f, 0, Input.Buttons.RIGHT));
            assertEquals(new IntArray(new int[]{17}), activated);
            assertTrue(presented.isEmpty());

            listener.touchUp(event, 4f, 9f, 0, Input.Buttons.RIGHT);
            assertEquals(new IntArray(new int[]{17}), presented);
            assertEquals(120f, presentationCoordinates[0], 0f);
            assertEquals(80f, presentationCoordinates[1], 0f);
            assertTrue(event.isHandled());
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void ignoresOtherButtonsAndNonLayerRows() {
        Stage stage = stage();
        try {
            EntityNode entity = new EntityNode("Sprite", null, 23, true);
            StubTree tree = new StubTree(entity);
            stage.addActor(tree);
            IntArray activated = new IntArray();
            IntArray presented = new IntArray();
            ItemTreeContextMenuInputListener<Object> listener = new ItemTreeContextMenuInputListener<>(
                    tree, EntityNode::isLayerNode,
                    node -> activated.add(node.getEntityId()),
                    () -> null,
                    (node, context, shownStage, stageX, stageY) -> presented.add(node.getEntityId()));

            InputEvent event = inputEvent(stage, 30f, 20f);
            assertFalse(listener.touchDown(event, 2f, 3f, 0, Input.Buttons.LEFT));
            assertFalse(listener.touchDown(event, 2f, 3f, 0, Input.Buttons.RIGHT));
            listener.touchUp(event, 2f, 3f, 0, Input.Buttons.RIGHT);
            assertTrue(activated.isEmpty());
            assertTrue(presented.isEmpty());
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void acceptsRealGameObjectEntityNode() {
        Stage stage = stage();
        try {
            EntityNode realRoot = new EntityNode("Game Object", null, 41, true);
            StubTree realTree = new StubTree(realRoot);
            stage.addActor(realTree);
            IntArray presented = new IntArray();
            ItemTreeContextMenuInputListener<Object> realListener = new ItemTreeContextMenuInputListener<>(
                    realTree,
                    node -> node.isEntityNode() && node.getEntityId() == 41,
                    ignored -> { },
                    () -> null,
                    (node, context, shownStage, stageX, stageY) -> presented.add(node.getEntityId()));
            InputEvent event = inputEvent(stage, 10f, 11f);
            assertTrue(realListener.touchDown(event, 1f, 2f, 0, Input.Buttons.RIGHT));
            realListener.touchUp(event, 1f, 2f, 0, Input.Buttons.RIGHT);
            assertEquals(new IntArray(new int[]{41}), presented);

        } finally {
            stage.dispose();
        }
    }

    @Test
    public void scene2dDispatchLeavesPopupAttachedAfterTheRightClickCompletes() {
        Stage stage = stage();
        try {
            EntityNode layer = new EntityNode(
                    "Main", null, 17, true, EntityNode.NodeKind.LAYER);
            StubTree tree = new StubTree(layer);
            tree.setBounds(0f, 0f, 16f, 16f);
            tree.setTouchable(Touchable.enabled);
            stage.addActor(tree);

            PopupMenu menu = new PopupMenu();
            menu.addItem(new MenuItem("Add"));
            tree.addListener(new ItemTreeContextMenuInputListener<>(
                    tree,
                    node -> node.isLayerNode() && node.getEntityId() == 17,
                    ignored -> { },
                    () -> null,
                    (node, context, shownStage, stageX, stageY) ->
                            menu.showMenu(shownStage, stageX, stageY)));

            InputEvent down = inputEvent(stage, 4f, 9f);
            down.setType(InputEvent.Type.touchDown);
            down.setPointer(0);
            down.setButton(Input.Buttons.RIGHT);
            tree.fire(down);
            assertTrue(down.isHandled());
            assertNull(menu.getStage());

            InputEvent up = inputEvent(stage, 4f, 9f);
            up.setType(InputEvent.Type.touchUp);
            up.setPointer(0);
            up.setButton(Input.Buttons.RIGHT);
            tree.fire(up);
            assertTrue(up.isHandled());
            assertEquals(stage, menu.getStage());
        } finally {
            stage.dispose();
        }
    }

    @Test
    public void capturesAssetContextBeforeItemsActivationChangesSelectionState() {
        Stage stage = stage();
        try {
            EntityNode root = new EntityNode("Game Object", null, 41, true);
            StubTree tree = new StubTree(root);
            stage.addActor(tree);
            Object selectedAsset = new Object();
            Object[] liveSelection = {selectedAsset};
            Object[] presentedContext = {null};
            ItemTreeContextMenuInputListener<Object> listener =
                    new ItemTreeContextMenuInputListener<>(
                            tree,
                            node -> true,
                            ignored -> liveSelection[0] = null,
                            () -> liveSelection[0],
                            (node, context, shownStage, stageX, stageY) ->
                                    presentedContext[0] = context);

            InputEvent event = inputEvent(stage, 10f, 11f);
            assertTrue(listener.touchDown(event, 1f, 2f, 0, Input.Buttons.RIGHT));
            assertNull(liveSelection[0]);
            listener.touchUp(event, 1f, 2f, 0, Input.Buttons.RIGHT);
            assertEquals(selectedAsset, presentedContext[0]);
        } finally {
            stage.dispose();
        }
    }

    private static InputEvent inputEvent(Stage stage, float stageX, float stageY) {
        InputEvent event = new InputEvent();
        event.setStage(stage);
        event.setStageX(stageX);
        event.setStageY(stageY);
        return event;
    }

    private static Stage stage() {
        Batch batch = (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(),
                new Class[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new Stage(new ScreenViewport(), batch);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) return null;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Float.TYPE) return 0f;
        if (returnType == Double.TYPE) return 0d;
        if (returnType == Character.TYPE) return '\0';
        return null;
    }

    private static final class StubTree extends IdVisTree {
        private final EntityNode node;

        private StubTree(EntityNode node) {
            this.node = node;
        }

        @Override
        public EntityNode getNodeAt(float y) {
            return node;
        }
    }
}
