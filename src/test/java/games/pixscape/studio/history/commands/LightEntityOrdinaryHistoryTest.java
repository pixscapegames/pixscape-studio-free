package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import org.junit.Assert;
import org.junit.Test;

public class LightEntityOrdinaryHistoryTest {

    @Test
    public void deletingSpatialConeLightAndUndoRestoresTechnicalRepresentation() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsService physics = new PhysicsService(world, null, new SceneMeta());
        int entity = createConeLight(world, 3);
        PhysicsShapeData footprint = new PhysicsShapeData();
        footprint.geometry = new PhysicsGeometryData();
        footprint.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        footprint.geometry.radius = 0.05f;
        footprint.spatialFootprint = true;
        footprint.technicalSpatialLight = true;
        footprint.sensor = true;
        footprint.maskBits = 0;
        history.execute(new ToggleSpatialActorCommand(world, history.historyIds(), physics,
                entity, true, true, footprint, true));
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class)
                .get(entity).technicalSpatialLight);

        history.execute(new DeleteEntitiesCommand(
                world, history.historyIds(), new IntArray(new int[]{entity})));
        world.process();
        Assert.assertEquals(0, coneLightCount(world));
        history.undo();
        world.process();

        int restored = firstConeLight(world);
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class)
                .get(restored).technicalSpatialLight);
        Assert.assertTrue(world.getMapper(PhysicsShapesComponent.class)
                .get(restored).shapes.first().technicalSpatialLight);
        Assert.assertTrue(world.getMapper(SpatialHeightComponent.class).has(restored));
    }

    @Test
    public void lightMovesBetweenOrdinaryLayersWithUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entity = createConeLight(world, 2);
        long historyId = history.historyIds().ensureForEntity(entity);
        ChangeLayerIndexCommand command = new ChangeLayerIndexCommand(world, history.historyIds());
        command.addEntry(historyId, 2, 5);

        history.execute(command);
        Assert.assertEquals(5, world.getMapper(EntityIndexComponent.class).get(entity).layerIndex);
        history.undo();
        Assert.assertEquals(2, world.getMapper(EntityIndexComponent.class).get(entity).layerIndex);
        history.redo();
        Assert.assertEquals(5, world.getMapper(EntityIndexComponent.class).get(entity).layerIndex);
        world.dispose();
    }

    @Test
    public void genericDeleteUndoRedoRestoresAndRemovesCompleteLight() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entity = createConeLight(world, 3);
        history.historyIds().ensureForEntity(entity);

        history.execute(new DeleteEntitiesCommand(
                world, history.historyIds(), new IntArray(new int[]{entity})));
        world.process();
        Assert.assertEquals(0, coneLightCount(world));

        history.undo();
        world.process();
        Assert.assertEquals(1, coneLightCount(world));
        int restored = firstConeLight(world);
        ConeLightComponent restoredLight = world.getMapper(ConeLightComponent.class).get(restored);
        Assert.assertEquals(77f, restoredLight.radius, 0f);
        Assert.assertEquals(41f, restoredLight.coneAngleDeg, 0f);
        Assert.assertEquals(3, world.getMapper(EntityIndexComponent.class).get(restored).layerIndex);

        history.redo();
        world.process();
        Assert.assertEquals(0, coneLightCount(world));
        world.dispose();
    }

    private static int createConeLight(World world, int layerIndex) {
        int entity = world.create();
        new GenericEntityInitializer(world)
                .configureConeLightProcedural(
                        8f, 9f, 0.4f, 6, 2, 12, layerIndex, "Cone",
                        77f, 1.5f, 41f, 0.2f, 2f, 0.1f, 0.2f, 0.3f)
                .init(entity);
        return entity;
    }

    private static int coneLightCount(World world) {
        return world.getAspectSubscriptionManager()
                .get(Aspect.all(ConeLightComponent.class)).getEntities().size();
    }

    private static int firstConeLight(World world) {
        return world.getAspectSubscriptionManager()
                .get(Aspect.all(ConeLightComponent.class)).getEntities().get(0);
    }
}
