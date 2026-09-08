package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SpatialBlockPhysicsCollisionCommandTest {
    private ProjectConfig previousConfig;

    @Before
    public void configureScene() {
        previousConfig = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Collision");
        config.getCurrentSceneMeta().pixelsPerMeter = 32f;
        ProjectConfig.setInstance(config);
    }

    @After
    public void restoreConfig() {
        ProjectConfig.setInstance(previousConfig);
    }

    @Test
    public void enableWithoutTransformCreatesIdentityTransformAndCollision() {
        Harness harness = new Harness();
        int highWaterBefore = harness.meta.nextPhysicsShapeId;
        Assert.assertFalse(harness.world.getMapper(TransformComponent.class)
                .has(harness.owner));

        SetSpatialBlockPhysicsCollisionCommand command = harness.command(7, true);
        Assert.assertEquals(CommandOutcome.APPLIED, command.executeOutcome());

        TransformComponent transform = harness.world.getMapper(
                TransformComponent.class).get(harness.owner);
        Assert.assertEquals(0f, transform.x, 0f);
        Assert.assertEquals(0f, transform.y, 0f);
        Assert.assertEquals(0f, transform.rotationRad, 0f);
        Assert.assertEquals(1f, transform.scaleX, 0f);
        Assert.assertEquals(1f, transform.scaleY, 0f);
        PhysicsShapeData linked = harness.linked(7);
        Assert.assertNotNull(linked);
        Assert.assertTrue(linked.physicsShapeId > 0);
        Assert.assertNull(linked.geometry);
        Assert.assertEquals(PhysicsBodyComponent.STATIC, harness.body().type);
        Assert.assertTrue(harness.compiled().valid);
        Assert.assertEquals(
                PhysicsGeometryData.SHAPE_POLYGON,
                harness.compiled().fixtures.first().shapeType);
        Assert.assertEquals(highWaterBefore + 1, harness.meta.nextPhysicsShapeId);
        int allocatedId = linked.physicsShapeId;
        int highWater = harness.meta.nextPhysicsShapeId;

        Assert.assertEquals(CommandOutcome.APPLIED, command.undoOutcome());
        Assert.assertNull(harness.linked(7));
        Assert.assertFalse(harness.hasBody());
        Assert.assertNotNull(harness.block(7));
        Assert.assertSame(transform, harness.world.getMapper(
                TransformComponent.class).get(harness.owner));

        Assert.assertEquals(CommandOutcome.APPLIED, command.redoOutcome());
        Assert.assertEquals(allocatedId, harness.linked(7).physicsShapeId);
        Assert.assertEquals(highWater, harness.meta.nextPhysicsShapeId);
        Assert.assertEquals(PhysicsBodyComponent.STATIC, harness.body().type);
        Assert.assertSame(transform, harness.world.getMapper(
                TransformComponent.class).get(harness.owner));
    }

    @Test
    public void disablePreservesManualShapeAndUndoRestoresPhysicalProperties() {
        Harness harness = new Harness();
        harness.execute(7, true);
        PhysicsShapeData linked = harness.linked(7);
        linked.density = 2.5f;
        linked.friction = 0.75f;
        linked.restitution = 0.4f;
        linked.sensor = true;
        int linkedId = linked.physicsShapeId;
        PhysicsShapeData manual = PhysicsService.createDefaultShape(
                harness.physics.allocateNewPhysicsShapeId());
        harness.shapes().shapes.insert(0, manual);
        FixtureCommandSupport.prepareAndPublish(
                harness.world,
                harness.owner,
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner));
        harness.body().type = PhysicsBodyComponent.DYNAMIC;

        harness.execute(7, false);

        Assert.assertNull(harness.linked(7));
        Assert.assertEquals(1, harness.shapes().shapes.size);
        Assert.assertEquals(manual.physicsShapeId, harness.shapes().shapes.first().physicsShapeId);
        Assert.assertEquals(PhysicsBodyComponent.STATIC, harness.body().type);

        harness.history.undo();
        PhysicsShapeData restored = harness.linked(7);
        Assert.assertEquals(linkedId, restored.physicsShapeId);
        Assert.assertEquals(2.5f, restored.density, 0f);
        Assert.assertEquals(0.75f, restored.friction, 0f);
        Assert.assertEquals(0.4f, restored.restitution, 0f);
        Assert.assertTrue(restored.sensor);
        Assert.assertNull(restored.geometry);
    }

    @Test
    public void twoBlocksHaveIndependentLinkedShapes() {
        Harness harness = new Harness();
        harness.execute(7, true);
        harness.execute(8, true);
        int firstId = harness.linked(7).physicsShapeId;
        int secondId = harness.linked(8).physicsShapeId;

        harness.execute(7, false);

        Assert.assertNull(harness.linked(7));
        Assert.assertNotEquals(firstId, secondId);
        Assert.assertEquals(secondId, harness.linked(8).physicsShapeId);
        Assert.assertEquals(1, harness.compiled().fixtures.size);
    }

    @Test
    public void failedPreparationPublishesNothing() {
        Harness harness = new Harness();
        harness.block(7).width = 0f;
        int revision = harness.blocks().revision;

        SetSpatialBlockPhysicsCollisionCommand command = harness.command(7, true);
        Assert.assertEquals(CommandOutcome.REJECTED, command.executeOutcome());

        Assert.assertFalse(harness.hasBody());
        Assert.assertFalse(harness.world.getMapper(TransformComponent.class)
                .has(harness.owner));
        Assert.assertFalse(harness.world.getMapper(PhysicsShapesComponent.class)
                .has(harness.owner));
        Assert.assertFalse(harness.world.getMapper(PhysicsCompiledFixturesComponent.class)
                .has(harness.owner));
        Assert.assertEquals(revision, harness.blocks().revision);
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK,
                harness.selection.getSelectedBlockId());
    }

    private static final class Harness {
        final World world = new World();
        final int owner = world.create();
        final HistoryManager history = new HistoryManager(16);
        final SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        final SceneMeta meta = new SceneMeta();
        final PhysicsService physics = new PhysicsService(world, null, meta);

        Harness() {
            TiledLayerComponent tiled =
                    world.getMapper(TiledLayerComponent.class).create(owner);
            tiled.data = new TiledMapLayerData(
                    20, 20, 32, 16, 8, TiledProjection.ORTHO);
            for (int gy = 0; gy < 20; gy++) {
                for (int gx = 0; gx < 20; gx++) tiled.data.setTile(gx, gy, 1);
            }
            SpatialBlocksComponent blocks =
                    world.getMapper(SpatialBlocksComponent.class).create(owner);
            blocks.blocks.add(SpatialBlockPhysicsCollisionCommandTest.block(7, 2, 3));
            blocks.blocks.add(SpatialBlockPhysicsCollisionCommandTest.block(8, 8, 9));
            history.historyIds().ensureForEntity(owner);
        }

        void execute(int blockId, boolean enabled) {
            history.execute(command(blockId, enabled));
        }

        SetSpatialBlockPhysicsCollisionCommand command(int blockId, boolean enabled) {
            return new SetSpatialBlockPhysicsCollisionCommand(
                    world, history.historyIds(), selection, physics,
                    owner, blockId, enabled);
        }

        SpatialBlocksComponent blocks() {
            return world.getMapper(SpatialBlocksComponent.class).get(owner);
        }

        SpatialBlockData block(int id) {
            return SpatialBlockCommandSupport.find(blocks(), id);
        }

        PhysicsShapesComponent shapes() {
            return world.getMapper(PhysicsShapesComponent.class).get(owner);
        }

        PhysicsShapeData linked(int blockId) {
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class)
                    .getSafe(owner, null);
            int index = SpatialBlockCommandSupport.indexOfLinkedPhysicsShape(
                    shapes, blockId);
            return index >= 0 ? shapes.shapes.get(index) : null;
        }

        boolean hasBody() {
            return world.getMapper(PhysicsBodyComponent.class).has(owner);
        }

        PhysicsBodyComponent body() {
            return world.getMapper(PhysicsBodyComponent.class).get(owner);
        }

        PhysicsCompiledFixturesComponent compiled() {
            return world.getMapper(PhysicsCompiledFixturesComponent.class).get(owner);
        }
    }

    private static SpatialBlockData block(int id, int x, int y) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.structureId = id;
        block.x = x;
        block.y = y;
        block.width = 1f;
        block.depth = 1f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(x, y, 1);
        return block;
    }
}
