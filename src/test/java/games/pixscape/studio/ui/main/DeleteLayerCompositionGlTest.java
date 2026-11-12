package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.studio.history.commands.AddEntityToGameObjectCommand;
import games.pixscape.studio.ui.layer.LayerRow;
import games.pixscape.studio.ui.tree.IdVisTree;
import org.junit.Test;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

public class DeleteLayerCompositionGlTest {
    @Test
    public void editorRestoreCallbackDoesNotHideMissingImmediateIdentityRegistration() throws Exception {
        withStudio(900, (studio, directory) -> {
            var file = project(studio, directory.child("ordinary-restore"));
            addImage(file.parent(), "sprite", 80, 40);
            open(studio, file);
            settle(studio);
            var context = studio.getSceneEditorContext();
            var layers = context.layerService();
            layers.addLayerTop("B");
            context.selectionService().setActivelayerId(layers.getLayerEntity(0));
            var ops = studio.getCanvas().getEditorOps();
            int sprite = ops.createStandaloneSprite("sprite.png", -160, 20);
            settle(studio);
            int stable = context.identityRegistry().getStableId(sprite);
            long historyId = context.historyManager().historyIds().ensureForEntity(sprite);
            ops.deleteEntities(com.badlogic.gdx.utils.IntArray.with(sprite));
            settle(studio);
            context.historyManager().undo(); // Executes the actual render-rebind callback, without another frame.
            context.historyManager().execute(new games.pixscape.studio.history.commands.DeleteLayerCommand(
                    layers, layers.getLayerEntity(0), null));
            assertEquals(-1, context.historyManager().historyIds().entityOfHistoryId(historyId));
            context.historyManager().undo();
            int restored = context.historyManager().historyIds().entityOfHistoryId(historyId);
            assertEquals(restored, context.identityRegistry().findByStableId(stable));
            settle(studio);
            assertEquals(restored, context.identityRegistry().findByStableId(stable));
        });
    }

    @Test
    public void deleteButtonsAndUndoRedoKeepHierarchyInspectorAndRenderingCoherent() throws Exception {
        withStudio(900, (studio, directory) -> {
            var file = project(studio, directory.child("delete-layer"));
            addImage(file.parent(), "child", 80, 40);
            var asset = file.parent().child("gameobjects/Assembly.gameobject");
            new GameObjectAssetLoader().save(asset, gameObjectAsset());
            open(studio, file);
            settle(studio);
            var context = studio.getSceneEditorContext();
            var layers = context.layerService();
            layers.meta(0).name = "Old layer A";
            layers.addLayerTop("Effective layer B");
            settle(studio);
            var instance = studio.getCanvas().getGameObjectAssetService().instantiateGameObject(
                    asset, "gameobjects/Assembly.gameobject", 1, -180, 20);
            context.selectionService().setActivelayerId(layers.getLayerEntity(0));
            int child = studio.getCanvas().getEditorOps().createStandaloneSprite("child.png", -160, 20);
            settle(studio);
            context.historyManager().execute(new AddEntityToGameObjectCommand(context.world(),
                    context.historyManager().historyIds(), context.identityRegistry(), child,
                    instance.sourceToCreated().get(3, -1), 0));
            settle(studio);
            int stable = context.identityRegistry().getStableId(child);
            int parentStable = context.world().getMapper(GameObjectMemberComponent.class).get(child).parentStableId;
            Object panel = field(studio, "layersPanel");
            click(studio, row(panel, layers.getLayerEntity(0)));
            click(studio, (Actor) field(panel, "btnDelete"));
            assertEquals(child, context.identityRegistry().findByStableId(stable));
            assertEquals(0, layers.getEffectiveLayerIndex(child));
            screenshot(studio, "delete-old-layer-preserves-composition");
            shortcut(studio, Input.Keys.Z);
            assertEquals(2, layers.count());
            assertEquals(0, context.world().getMapper(EntityIndexComponent.class).get(child).layerIndex);

            int cursor = context.historyManager().getCursor();
            clickItem(studio, child);
            screenshot(studio, "delete-effective-layer-before");
            click(studio, (Actor) field(panel, "btnDelete"));
            assertEquals(cursor + 1, context.historyManager().getCursor());
            for (int i = 0; i < 2; i++) {
                assertEquals(-1, context.identityRegistry().findByStableId(stable));
                assertNull(tree(studio).findNode(child));
                assertEquals(-1, field(field(studio, "propertiesPanel"), "boundEntity"));
                screenshot(studio, "delete-effective-layer-removed");
                shortcut(studio, Input.Keys.Z);
                child = context.identityRegistry().findByStableId(stable);
                assertTrue(child >= 0);
                assertEquals(parentStable, context.world().getMapper(GameObjectMemberComponent.class).get(child).parentStableId);
                assertEquals(1, layers.getEffectiveLayerIndex(child));
                clickItem(studio, child);
                assertEquals(child, field(field(studio, "propertiesPanel"), "boundEntity"));
                screenshot(studio, "delete-effective-layer-restored");
                shortcut(studio, Input.Keys.Y);
            }
            shortcut(studio, Input.Keys.Z);
            studio.getSceneService().saveProjectAndCurrentScene();
            open(studio, file);
            settle(studio);
            var restored = studio.getSceneEditorContext();
            child = restored.identityRegistry().findByStableId(stable);
            assertTrue(child >= 0);
            assertEquals(parentStable, restored.world().getMapper(GameObjectMemberComponent.class).get(child).parentStableId);
            assertEquals(1, restored.layerService().getEffectiveLayerIndex(child));
            assertEquals(0, restored.world().getMapper(EntityIndexComponent.class).get(child).layerIndex);
            clickItem(studio, child);
            screenshot(studio, "delete-effective-layer-reloaded");
        });
    }

    private static IdVisTree tree(StudioApplicationAdapter studio) throws Exception {
        return (IdVisTree) field(studio.getItemTreePanel(), "tree");
    }

    private static void clickItem(StudioApplicationAdapter studio, int entity) throws Exception {
        click(studio, tree(studio).findNode(entity).getLabel());
    }

    private static Actor row(Object panel, int entity) throws Exception {
        for (Actor actor : ((Group) field(panel, "listTable")).getChildren()) {
            if (actor instanceof LayerRow row && row.getEntityId() == entity) return row;
        }
        throw new AssertionError("Layer row missing");
    }

    private static void click(StudioApplicationAdapter studio, Actor actor) {
        Vector2 point = studio.getUiStage().stageToScreenCoordinates(actor.localToStageCoordinates(
                new Vector2(actor.getWidth() * .6f, actor.getHeight() / 2)));
        assertTrue(studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, 0));
        studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, 0);
        settle(studio);
    }

    private static void shortcut(StudioApplicationAdapter studio, int key) {
        Input original = Gdx.input;
        Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (proxy, method, args) -> method.getName().equals("isKeyPressed")
                        ? (Integer) args[0] == Input.Keys.CONTROL_LEFT : method.invoke(original, args));
        try {
            assertTrue(studio.getUiStage().keyDown(key));
            studio.getUiStage().keyUp(key);
        } finally {
            Gdx.input = original;
        }
        settle(studio);
    }

    private static void settle(StudioApplicationAdapter studio) {
        studio.render();
        studio.render();
    }
}
