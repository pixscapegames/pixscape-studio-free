package games.pixscape.studio.ui.main;

import com.badlogic.gdx.math.Vector2;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.studio.history.commands.AddEntityToGameObjectCommand;
import games.pixscape.studio.history.commands.ChangeLayerIndexCommand;
import games.pixscape.studio.ui.tree.IdVisTree;
import org.junit.Test;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

/**
 * Real Items clicks, docked Inspector, scene persistence and isolated asset editing.
 */
public class EffectiveLayerOwnershipGlTest {
    @Test
    public void inheritedLayerTracksCurrentRootAndSurvivesSceneReload() throws Exception {
        withStudio(900, (studio, directory) -> {
            var file = project(studio, directory.child("ownership"));
            addImage(file.parent(), "child", 80, 40);
            var assetFile = file.parent().child("gameobjects/Assembly.gameobject");
            new GameObjectAssetLoader().save(assetFile, gameObjectAsset());
            open(studio, file);
            settle(studio);
            var context = studio.getSceneEditorContext();
            var layers = context.layerService();
            layers.meta(0).name = "Source A";
            int destination = layers.addLayerTop("Destination B");
            settle(studio);
            var instance = studio.getCanvas().getGameObjectAssetService().instantiateGameObject(
                    assetFile, "gameobjects/Assembly.gameobject", destination, 0, 0);
            int root = instance.sourceToCreated().get(1, -1);
            int nested = instance.sourceToCreated().get(3, -1);
            context.selectionService().setActivelayerId(layers.getLayerEntity(0));
            int child = studio.getCanvas().getEditorOps().createStandaloneSprite("child.png", 50, 20);
            settle(studio);
            context.historyManager().execute(new AddEntityToGameObjectCommand(context.world(),
                    context.historyManager().historyIds(), context.identityRegistry(), child, nested, 0));
            assertEquals(destination, layers.getEffectiveLayerIndex(child));
            assertEquals(0, context.world().getMapper(EntityIndexComponent.class).get(child).layerIndex);
            settle(studio);
            clickItem(studio, child);
            assertEquals(layers.getLayerEntity(destination), context.selectionService().getActivelayerId());
            assertEquals("Destination B\n(inherited)", layerLabel(studio));
            screenshot(studio, "effective-layer-inherited");

            var move = new ChangeLayerIndexCommand(context.world(), context.historyManager().historyIds());
            move.addEntry(context.historyManager().historyIds().ensureForEntity(root), destination, 0);
            context.historyManager().execute(move);
            // Flush UI notifications without publishing ECS: label must use authored relations.
            games.pixscape.studio.event.EventFlow.i().flush();
            ((games.pixscape.studio.ui.property.PropertiesPanel) field(studio, "propertiesPanel")).updateIfDirty();
            assertEquals("Source A\n(inherited)", layerLabel(studio));
            context.historyManager().undo();
            settle(studio);
            assertEquals("Destination B\n(inherited)", layerLabel(studio));

            context.sceneMeta().forwardLighting = false;
            for (boolean point : new boolean[]{true, false}) {
                context.selectionService().setActivelayerId(layers.getLayerEntity(0));
                int light = point ? studio.getCanvas().getEditorOps().createPointLight(0, 0)
                        : studio.getCanvas().getEditorOps().createConeLight(0, 0);
                settle(studio);
                context.historyManager().execute(new AddEntityToGameObjectCommand(context.world(),
                        context.historyManager().historyIds(), context.identityRegistry(), light, nested, 1));
                settle(studio);
                clickItem(studio, light);
                String propertyName = point ? "pointLightProperties" : "coneLightProperties";
                Object properties = field(field(studio, "propertiesPanel"), propertyName);
                VisLabel label = (VisLabel) field(properties, "layerValueLabel");
                assertEquals("Destination B\n(inherited)", label.getText().toString());
                var transfer = new ChangeLayerIndexCommand(context.world(), context.historyManager().historyIds());
                transfer.addEntry(context.historyManager().historyIds().ensureForEntity(root), destination, 0);
                context.historyManager().execute(transfer);
                settle(studio);
                assertEquals("Source A\n(inherited)", label.getText().toString());
                context.historyManager().undo();
                settle(studio);
                assertEquals("Destination B\n(inherited)", label.getText().toString());
            }

            layers.setLayerLocked(layers.getLayerEntity(0), true);
            context.selectionService().clearSelection();
            settle(studio);
            clickItem(studio, child);
            assertEquals(child, context.selectionService().getSelectionSnapshot().first());
            layers.setLayerLocked(layers.getLayerEntity(destination), true);
            context.selectionService().clearSelection();
            settle(studio);
            clickItem(studio, child);
            assertTrue(context.selectionService().getSelectionSnapshot().isEmpty());
            screenshot(studio, "effective-layer-locked");
            layers.setLayerLocked(layers.getLayerEntity(destination), false);

            int childStableId = context.identityRegistry().getStableId(child);
            studio.getSceneService().saveProjectAndCurrentScene();
            open(studio, file);
            settle(studio);
            var restored = studio.getSceneEditorContext();
            int restoredChild = restored.identityRegistry().findByStableId(childStableId);
            assertTrue(restoredChild >= 0);
            assertEquals(0, restored.world().getMapper(EntityIndexComponent.class).get(restoredChild).layerIndex);
            assertEquals(destination, restored.layerService().getEffectiveLayerIndex(restoredChild));
            clickItem(studio, restoredChild);
            assertEquals("Destination B\n(inherited)", layerLabel(studio));

            var document = studio.openGameObject(assetFile.path());
            assertNotNull(document);
            assertNotSame(restored, document.context());
            settle(studio);
            var tree = (IdVisTree) field(studio.getItemTreePanel(), "tree");
            int assetChild = ((games.pixscape.studio.ui.tree.EntityNode) tree.findNode(document.rootEntityId()).getChildren().first()).getEntityId();
            clickItem(studio, assetChild);
            assertEquals("Game Object\n(inherited)", layerLabel(studio));
            assertEquals(0, document.context().historyManager().getCursor());
            assertFalse(document.isDirty());
            screenshot(studio, "effective-layer-asset-editor");
        });
    }

    private static void settle(StudioApplicationAdapter studio) {
        studio.render();
        studio.render();
    }

    private static String layerLabel(StudioApplicationAdapter studio) throws Exception {
        Object properties = field(field(studio, "propertiesPanel"), "entityProperties");
        return ((VisLabel) field(properties, "layerValueLabel")).getText().toString();
    }

    private static void clickItem(StudioApplicationAdapter studio, int entity) throws Exception {
        var tree = (IdVisTree) field(studio.getItemTreePanel(), "tree");
        var actor = tree.findNode(entity).getLabel();
        Vector2 point = studio.getUiStage().stageToScreenCoordinates(actor.localToStageCoordinates(
                new Vector2(actor.getWidth() / 2, actor.getHeight() / 2)));
        studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, 0);
        studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, 0);
        settle(studio);
    }
}
