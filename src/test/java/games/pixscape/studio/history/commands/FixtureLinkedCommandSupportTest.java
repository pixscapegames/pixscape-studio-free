package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FixtureLinkedCommandSupportTest {
    private ProjectConfig previousConfig;
    private SceneMeta meta;

    @Before
    public void configureScene() {
        previousConfig = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Linked");
        meta = config.getCurrentSceneMeta();
        meta.pixelsPerMeter = 32f;
        meta.nextPhysicsShapeId = 4;
        ProjectConfig.setInstance(config);
    }

    @After
    public void restoreConfig() {
        ProjectConfig.setInstance(previousConfig);
    }

    @Test
    public void manualAddAndEditRecompileCompleteMixedBody() {
        Harness harness = new Harness();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        historyIds.ensureForEntity(harness.owner);

        AddFixtureCommand add = new AddFixtureCommand(
                harness.world,
                historyIds,
                selection,
                harness.physics,
                harness.owner);
        add.redo();

        PhysicsShapesComponent shapes = harness.shapes();
        PhysicsCompiledFixturesComponent compiled = harness.compiled();
        Assert.assertEquals(4, shapes.shapes.size);
        Assert.assertEquals(4, compiled.fixtures.size);
        Assert.assertNull(shapes.shapes.get(1).geometry);
        Assert.assertEquals(2, compiled.fixtures.get(1).physicsShapeId);

        PhysicsShapeData manual = shapes.shapes.first();
        PhysicsShapeData edited = manual.copy();
        edited.geometry.halfWidth = 3f;
        EditFixtureCommand edit = new EditFixtureCommand(
                harness.world,
                historyIds,
                selection,
                harness.owner,
                manual.physicsShapeId,
                EditFixtureCommand.Snapshot.capture(manual),
                EditFixtureCommand.Snapshot.capture(edited),
                1,
                false);
        edit.redo();

        Assert.assertEquals(3f,
                harness.shapes().shapes.first().geometry.halfWidth, 0f);
        Assert.assertEquals(4, harness.compiled().fixtures.size);
        Assert.assertEquals(1,
                harness.compiled().fixtures.get(0).physicsShapeId);
        Assert.assertEquals(2,
                harness.compiled().fixtures.get(1).physicsShapeId);
        Assert.assertNull(harness.shapes().shapes.get(1).geometry);
    }

    @Test
    public void linkedPhysicalPropertyEditKeepsRelationAndDerivedGeometry() {
        Harness harness = new Harness();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        historyIds.ensureForEntity(harness.owner);
        PhysicsShapeData linked = harness.shapes().shapes.get(1);
        PhysicsShapeData edited = linked.copy();
        edited.density = 2.75f;
        edited.sensor = true;

        EditFixtureCommand command = new EditFixtureCommand(
                harness.world,
                historyIds,
                selection,
                harness.owner,
                linked.physicsShapeId,
                EditFixtureCommand.Snapshot.capture(linked),
                EditFixtureCommand.Snapshot.capture(edited),
                1,
                false);
        command.redo();

        PhysicsShapeData published = harness.shapes().shapes.get(1);
        Assert.assertEquals(2.75f, published.density, 0f);
        Assert.assertTrue(published.sensor);
        Assert.assertEquals(7, published.spatialBlockId);
        Assert.assertNull(published.geometry);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON,
                harness.compiled().fixtures.get(1).shapeType);
    }

    @Test
    public void relationMutationsAreRejectedWithBodyAndShapeDiagnostics() {
        Harness harness = new Harness();
        String relationMutation =
                "linked relation mutations are not supported";

        Array<PhysicsShapeData> deleted =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        deleted.removeIndex(1);
        assertRejected(harness, deleted, 2, relationMutation);

        Array<PhysicsShapeData> changedId =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        changedId.get(1).physicsShapeId = 20;
        assertRejected(harness, changedId, 2, relationMutation);

        Array<PhysicsShapeData> changedBlock =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        changedBlock.get(1).spatialBlockId = 8;
        assertRejected(harness, changedBlock, 2, relationMutation);

        Array<PhysicsShapeData> changedGeometry =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        changedGeometry.get(1).geometry = new PhysicsGeometryData();
        assertRejected(harness, changedGeometry, 2,
                "writing linked geometry is not supported");

        Array<PhysicsShapeData> added =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        PhysicsShapeData additional = added.get(1).copy();
        additional.physicsShapeId = 30;
        added.add(additional);
        assertRejected(harness, added, 30, relationMutation);

        Array<PhysicsShapeData> duplicated =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        PhysicsShapeData duplicate = duplicated.get(1).copy();
        duplicated.add(duplicate);
        assertRejected(harness, duplicated, 2, relationMutation);

        Array<PhysicsShapeData> manualToLinked =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        manualToLinked.get(0).spatialBlockId = 7;
        manualToLinked.get(0).geometry = null;
        assertRejected(harness, manualToLinked, 2, relationMutation);

        Array<PhysicsShapeData> linkedToManual =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        linkedToManual.get(1).spatialBlockId = 0;
        linkedToManual.get(1).geometry = new PhysicsGeometryData();
        assertRejected(harness, linkedToManual, 2, relationMutation);

        harness.appendLinked(20, 8);
        Array<PhysicsShapeData> reordered =
                FixtureCommandSupport.copyFixtures(harness.world, harness.owner);
        reordered.swap(1, 3);
        assertRejected(harness, reordered, 2, relationMutation);
    }

    @Test
    public void linkedDuplicateDeleteAndBodyRemovalCommandsAreNoChange() {
        Harness harness = new Harness();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        historyIds.ensureForEntity(harness.owner);
        int highWater = meta.nextPhysicsShapeId;

        DuplicateFixtureCommand duplicate = new DuplicateFixtureCommand(
                harness.world,
                historyIds,
                selection,
                harness.physics,
                harness.owner,
                2);
        DeleteFixtureCommand delete = new DeleteFixtureCommand(
                harness.world,
                historyIds,
                selection,
                harness.owner,
                2);
        RemovePhysicsBodyCommand remove = new RemovePhysicsBodyCommand(
                harness.world,
                historyIds,
                harness.physics,
                harness.owner);

        Assert.assertTrue(duplicate.isNoop());
        Assert.assertTrue(delete.isNoop());
        Assert.assertEquals(CommandOutcome.NO_CHANGE, delete.executeOutcome());
        Assert.assertTrue(remove.isNoop());
        Assert.assertEquals(CommandOutcome.NO_CHANGE, remove.executeOutcome());
        Assert.assertEquals(highWater, meta.nextPhysicsShapeId);
        Assert.assertTrue(harness.world.getMapper(
                PhysicsBodyComponent.class).has(harness.owner));
        Assert.assertEquals(3, harness.shapes().shapes.size);
    }

    private static void assertRejected(
            Harness harness,
            Array<PhysicsShapeData> candidate,
            int physicsShapeId,
            String operation) {
        int currentSize = harness.shapes().shapes.size;
        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> FixtureCommandSupport.prepareAndPublish(
                        harness.world, harness.owner, candidate));
        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains(
                        "Body entity " + harness.owner));
        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains(
                        "physicsShapeId " + physicsShapeId));
        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains(operation));
        Assert.assertEquals(currentSize, harness.shapes().shapes.size);
        Assert.assertNull(harness.shapes().shapes.get(1).geometry);
    }

    private final class Harness {
        final World world = new World();
        final PhysicsService physics = new PhysicsService(world, null, meta);
        final int owner = world.create();

        Harness() {
            world.getMapper(TransformComponent.class).create(owner);
            world.getMapper(PhysicsBodyComponent.class).create(owner);
            TiledLayerComponent tiled = world.getMapper(
                    TiledLayerComponent.class).create(owner);
            tiled.data = new TiledMapLayerData(
                    20, 20, 32, 16, 8,
                    SceneMetaRuntime.TiledProjection.ORTHO);
            SpatialBlocksComponent blocks = world.getMapper(
                    SpatialBlocksComponent.class).create(owner);
            SpatialBlockData block = new SpatialBlockData();
            block.id = 7;
            block.structureId = 1;
            block.x = 2f;
            block.y = 3f;
            block.width = 2f;
            block.depth = 3f;
            blocks.blocks.add(block);

            PhysicsShapesComponent shapes = world.getMapper(
                    PhysicsShapesComponent.class).create(owner);
            shapes.shapes.add(manual(1, PhysicsGeometryData.SHAPE_BOX));
            PhysicsShapeData linked = new PhysicsShapeData();
            linked.physicsShapeId = 2;
            linked.spatialBlockId = 7;
            shapes.shapes.add(linked);
            shapes.shapes.add(manual(3, PhysicsGeometryData.SHAPE_CIRCLE));

            PreparedPhysicsBodyCandidate prepared =
                    PhysicsService.prepareBodyCandidate(
                            world, owner, shapes.shapes, meta.pixelsPerMeter);
            PhysicsService.publishPreparedCandidate(
                    shapes,
                    world.getMapper(PhysicsCompiledFixturesComponent.class)
                            .create(owner),
                    prepared);
        }

        PhysicsShapesComponent shapes() {
            return world.getMapper(PhysicsShapesComponent.class).get(owner);
        }

        PhysicsCompiledFixturesComponent compiled() {
            return world.getMapper(
                    PhysicsCompiledFixturesComponent.class).get(owner);
        }

        void appendLinked(int physicsShapeId, int spatialBlockId) {
            SpatialBlockData block = new SpatialBlockData();
            block.id = spatialBlockId;
            block.structureId = 2;
            block.x = 8f;
            block.y = 9f;
            block.width = 1f;
            block.depth = 1f;
            world.getMapper(SpatialBlocksComponent.class)
                    .get(owner).blocks.add(block);

            PhysicsShapeData linked = new PhysicsShapeData();
            linked.physicsShapeId = physicsShapeId;
            linked.spatialBlockId = spatialBlockId;
            shapes().shapes.add(linked);
            PreparedPhysicsBodyCandidate prepared =
                    PhysicsService.prepareBodyCandidate(
                            world, owner, shapes().shapes, meta.pixelsPerMeter);
            PhysicsService.publishPreparedCandidate(
                    shapes(), compiled(), prepared);
        }
    }

    private static PhysicsShapeData manual(int id, int shapeType) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = id;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = shapeType;
        shape.geometry.halfWidth = 1f;
        shape.geometry.halfHeight = 1f;
        shape.geometry.radius = 1f;
        return shape;
    }
}
