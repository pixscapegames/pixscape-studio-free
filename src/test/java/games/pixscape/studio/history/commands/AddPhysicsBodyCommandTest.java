package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class AddPhysicsBodyCommandTest {
    @Test
    public void ordinaryLayerEntityCanReceivePhysics() {
        Harness harness = new Harness();
        int layerEntity = harness.world.create();
        LayerComponent layer = harness.world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = 0;
        layer.type = LayerComponent.TYPE_CLASSIC;
        int entityId = harness.world.create();
        harness.world.getMapper(EntityIndexComponent.class).create(entityId).layerIndex = 0;

        harness.history.execute(new AddPhysicsBodyCommand(
                harness.world,
                harness.historyIds,
                harness.physics,
                entityId,
                PhysicsBodyComponent.DYNAMIC,
                true));

        Assert.assertTrue(harness.world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertEquals(1,
                harness.world.getMapper(PhysicsShapesComponent.class).get(entityId).shapes.size);
        Assert.assertEquals(0,
                harness.world.getMapper(EntityIndexComponent.class).get(entityId).layerIndex);
    }

    @Test
    public void movingPhysicalEntityChangesOnlyLogicalLayerOwnership() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        TransformComponent transform = harness.world.getMapper(TransformComponent.class).create(entityId);
        transform.x = 23f;
        transform.y = -7f;
        harness.world.getMapper(EntityIndexComponent.class).create(entityId).layerIndex = 1;
        harness.history.execute(new AddPhysicsBodyCommand(
                harness.world,
                harness.historyIds,
                harness.physics,
                entityId,
                PhysicsBodyComponent.DYNAMIC,
                true));
        PhysicsBodyComponent body = harness.world.getMapper(PhysicsBodyComponent.class).get(entityId);
        int shapeId = harness.world.getMapper(PhysicsShapesComponent.class)
                .get(entityId).shapes.first().physicsShapeId;
        long historyId = harness.history.historyIds().ensureForEntity(entityId);
        ChangeLayerIndexCommand move = new ChangeLayerIndexCommand(
                harness.world, harness.history.historyIds());
        move.addEntry(historyId, 1, 4);

        harness.history.execute(move);

        Assert.assertEquals(4,
                harness.world.getMapper(EntityIndexComponent.class).get(entityId).layerIndex);
        Assert.assertSame(body, harness.world.getMapper(PhysicsBodyComponent.class).get(entityId));
        Assert.assertEquals(shapeId, harness.world.getMapper(PhysicsShapesComponent.class)
                .get(entityId).shapes.first().physicsShapeId);
        Assert.assertEquals(23f, transform.x, 0f);
        Assert.assertEquals(-7f, transform.y, 0f);

        harness.history.undo();
        Assert.assertEquals(1,
                harness.world.getMapper(EntityIndexComponent.class).get(entityId).layerIndex);
        harness.history.redo();
        Assert.assertEquals(4,
                harness.world.getMapper(EntityIndexComponent.class).get(entityId).layerIndex);
    }

    @Test
    public void addUndoRedoRestoresSameShapeIdWithoutRewindingHighWater() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        TransformComponent transform =
                harness.world.getMapper(TransformComponent.class).create(entityId);
        transform.x = 17f;

        AddPhysicsBodyCommand command = new AddPhysicsBodyCommand(
                harness.world,
                harness.historyIds,
                harness.physics,
                entityId,
                PhysicsBodyComponent.STATIC,
                true);
        harness.history.execute(command);

        Assert.assertTrue(harness.physics.hasPhysics(entityId));
        Assert.assertTrue(harness.world.getMapper(PhysicsShapesComponent.class)
                .has(entityId));
        Assert.assertTrue(harness.world.getMapper(
                PhysicsCompiledFixturesComponent.class).get(entityId).valid);
        int physicsShapeId = harness.world.getMapper(PhysicsShapesComponent.class)
                .get(entityId).shapes.first().physicsShapeId;
        int highWater = harness.meta.nextPhysicsShapeId;
        Assert.assertSame(transform, harness.world.getMapper(
                TransformComponent.class).get(entityId));
        Assert.assertEquals(PhysicsBodyComponent.STATIC, harness.world.getMapper(
                PhysicsBodyComponent.class).get(entityId).type);

        harness.history.undo();
        Assert.assertFalse(harness.physics.hasPhysics(entityId));
        Assert.assertTrue(harness.world.getMapper(TransformComponent.class)
                .has(entityId));
        Assert.assertEquals(highWater, harness.meta.nextPhysicsShapeId);

        harness.history.redo();
        Assert.assertEquals(physicsShapeId,
                harness.world.getMapper(PhysicsShapesComponent.class)
                        .get(entityId).shapes.first().physicsShapeId);
        Assert.assertEquals(highWater, harness.meta.nextPhysicsShapeId);
    }

    @Test
    public void addWithoutDefaultShapePublishesValidEmptyCache() {
        Harness harness = new Harness();
        int entityId = harness.world.create();

        harness.history.execute(new AddPhysicsBodyCommand(
                harness.world,
                harness.historyIds,
                harness.physics,
                entityId,
                PhysicsBodyComponent.DYNAMIC,
                false));

        Assert.assertTrue(harness.physics.hasPhysics(entityId));
        Assert.assertEquals(0, harness.world.getMapper(
                PhysicsShapesComponent.class).get(entityId).shapes.size);
        PhysicsCompiledFixturesComponent compiled = harness.world.getMapper(
                PhysicsCompiledFixturesComponent.class).get(entityId);
        Assert.assertTrue(compiled.valid);
        Assert.assertEquals(0, compiled.fixtures.size);
    }

    @Test
    public void tiledLayerIgnoresRequestedDynamicBodyType() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.world.getMapper(TiledLayerComponent.class).create(entityId);

        harness.history.execute(new AddPhysicsBodyCommand(
                harness.world,
                harness.historyIds,
                harness.physics,
                entityId,
                PhysicsBodyComponent.DYNAMIC,
                false));

        Assert.assertEquals(PhysicsBodyComponent.STATIC,
                harness.world.getMapper(PhysicsBodyComponent.class)
                        .get(entityId).type);
    }

    @Test
    public void commandCreatedTransformIsRemovedByUndo() {
        Harness harness = new Harness();
        int entityId = harness.world.create();

        harness.history.execute(new AddPhysicsBodyCommand(
                harness.world,
                harness.historyIds,
                harness.physics,
                entityId,
                PhysicsBodyComponent.DYNAMIC,
                false));

        TransformComponent created = harness.world.getMapper(
                TransformComponent.class).get(entityId);
        Assert.assertEquals(1f, created.scaleX, 0f);
        Assert.assertEquals(1f, created.scaleY, 0f);
        harness.history.undo();
        Assert.assertFalse(harness.world.getMapper(
                TransformComponent.class).has(entityId));
    }

    @Test
    public void existingBodyIsNoopAndConsumesNoShapeId() {
        Harness harness = new Harness();
        int entityId = harness.world.create();
        harness.world.getMapper(PhysicsBodyComponent.class).create(entityId);
        int highWater = harness.meta.nextPhysicsShapeId;

        harness.history.execute(new AddPhysicsBodyCommand(
                harness.world,
                harness.historyIds,
                harness.physics,
                entityId,
                PhysicsBodyComponent.DYNAMIC,
                true));

        Assert.assertFalse(harness.history.canUndo());
        Assert.assertEquals(highWater, harness.meta.nextPhysicsShapeId);
    }

    private static final class Harness {
        final World world = new World();
        final SceneMeta meta = new SceneMeta();
        final PhysicsService physics = new PhysicsService(world, null, meta);
        final HistoryIdRegistry historyIds = new HistoryIdRegistry();
        final HistoryManager history = new HistoryManager(16);

        Harness() {
            history.historyIds().clear();
        }
    }
}
