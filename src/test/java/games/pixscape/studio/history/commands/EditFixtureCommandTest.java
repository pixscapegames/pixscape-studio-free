package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Consumer;

public class EditFixtureCommandTest {
    @Before
    public void activateSceneAllocator() {
        games.pixscape.studio.configuration.ProjectConfig config =
                new games.pixscape.studio.configuration.ProjectConfig();
        config.createSceneMeta("Main");
        games.pixscape.studio.configuration.ProjectConfig.setInstance(config);
    }

    @Test
    public void editDensityFrictionRestitutionUndoRedoRestoresExactValues() {
        FixtureHarness harness = FixtureHarness.create();

        EditFixtureCommand command = harness.newEdit(cmd -> {
            cmd.density = 2.5f;
            cmd.friction = 0.75f;
            cmd.restitution = 0.35f;
        }, false);

        command.redo();
        assertFixtureScalars(harness.fixture(), 2.5f, 0.75f, 0.35f);

        command.undo();
        assertFixtureScalars(harness.fixture(), 1f, 0.2f, 0f);

        command.redo();
        assertFixtureScalars(harness.fixture(), 2.5f, 0.75f, 0.35f);
    }

    @Test
    public void toggleSensorUndoRedoRestoresExactValue() {
        FixtureHarness harness = FixtureHarness.create();

        EditFixtureCommand command = harness.newEdit(cmd -> cmd.sensor = true, false);

        command.redo();
        Assert.assertTrue(harness.fixture().sensor);

        command.undo();
        Assert.assertFalse(harness.fixture().sensor);

        command.redo();
        Assert.assertTrue(harness.fixture().sensor);
    }

    @Test
    public void editFilterBitsUndoRedoRestoresExactValues() {
        FixtureHarness harness = FixtureHarness.create();

        EditFixtureCommand command = harness.newEdit(cmd -> {
            cmd.categoryBits = (short) 0x00F0;
            cmd.maskBits = (short) 0x0FF0;
            cmd.groupIndex = (short) -7;
        }, false, games.pixscape.runtime.render.PhysicsDirtyBits.FILTER);

        command.redo();
        assertFixtureFilter(harness.fixture(), (short) 0x00F0, (short) 0x0FF0, (short) -7);

        command.undo();
        assertFixtureFilter(harness.fixture(), (short) 0x0001, (short) 0xFFFF, (short) 0);

        command.redo();
        assertFixtureFilter(harness.fixture(), (short) 0x00F0, (short) 0x0FF0, (short) -7);
    }

    @Test
    public void editBoxCircleAndOffsetUndoRedoRestoresExactValues() {
        FixtureHarness harness = FixtureHarness.create();

        EditFixtureCommand toBox = harness.newEdit(cmd -> {
            cmd.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_BOX;
            cmd.directGeometry.halfWidth = 1.25f;
            cmd.directGeometry.halfHeight = 0.75f;
            cmd.directGeometry.offsetX = 0.4f;
            cmd.directGeometry.offsetY = -0.2f;
        }, false);

        toBox.redo();
        assertFixtureShape(harness.fixture(), PhysicsDirectGeometryData.SHAPE_BOX, 1.25f, 0.75f, 0.5f, 0.4f, -0.2f);

        toBox.undo();
        assertFixtureShape(harness.fixture(), PhysicsDirectGeometryData.SHAPE_BOX, 0.5f, 0.5f, 0.5f, 0f, 0f);

        EditFixtureCommand toCircle = harness.newEdit(cmd -> {
            cmd.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_CIRCLE;
            cmd.directGeometry.radius = 0.9f;
            cmd.directGeometry.offsetX = -0.15f;
            cmd.directGeometry.offsetY = 0.33f;
        }, false);

        toCircle.redo();
        assertFixtureShape(harness.fixture(), PhysicsDirectGeometryData.SHAPE_CIRCLE, 0.5f, 0.5f, 0.9f, -0.15f, 0.33f);

        toCircle.undo();
        assertFixtureShape(harness.fixture(), PhysicsDirectGeometryData.SHAPE_BOX, 0.5f, 0.5f, 0.5f, 0f, 0f);
    }

    @Test
    public void shapeTypeChangeUndoRedoRestoresExactShapeData() {
        FixtureHarness harness = FixtureHarness.create();

        EditFixtureCommand command = harness.newEdit(cmd -> {
            cmd.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
            cmd.directGeometry.polygonVertexCount = 4;
            cmd.directGeometry.polygonVertices = new float[] {
                    -1f, -1f,
                    1f, -1f,
                    1f, 1f,
                    -1f, 1f
            };
        }, true);

        command.redo();
        assertPolygon(harness.fixture(), 4, new float[] {
                -1f, -1f,
                1f, -1f,
                1f, 1f,
                -1f, 1f
        });

        command.undo();
        Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_BOX, harness.fixture().directGeometry.shapeType);
        assertPolygon(harness.fixture(), 0, new float[0]);

        command.redo();
        Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_POLYGON, harness.fixture().directGeometry.shapeType);
        assertPolygon(harness.fixture(), 4, new float[] {
                -1f, -1f,
                1f, -1f,
                1f, 1f,
                -1f, 1f
        });
    }

    @Test
    public void polygonReplaceUndoRedoRestoresExactVertices() {
        FixtureHarness harness = FixtureHarness.create();

        EditFixtureCommand toPolygon = harness.newEdit(cmd -> {
            cmd.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
            cmd.directGeometry.polygonVertexCount = 3;
            cmd.directGeometry.polygonVertices = new float[] {
                    0f, 0f,
                    1f, 0f,
                    0f, 1f
            };
        }, true);
        toPolygon.redo();

        EditFixtureCommand replace = harness.newEdit(cmd -> {
            cmd.directGeometry.polygonVertexCount = 4;
            cmd.directGeometry.polygonVertices = new float[] {
                    -0.5f, -0.5f,
                    0.8f, -0.4f,
                    1.2f, 0.7f,
                    -0.3f, 1.1f
            };
        }, true);

        replace.redo();
        assertPolygon(harness.fixture(), 4, new float[] {
                -0.5f, -0.5f,
                0.8f, -0.4f,
                1.2f, 0.7f,
                -0.3f, 1.1f
        });

        replace.undo();
        assertPolygon(harness.fixture(), 3, new float[] {
                0f, 0f,
                1f, 0f,
                0f, 1f
        });

        replace.redo();
        assertPolygon(harness.fixture(), 4, new float[] {
                -0.5f, -0.5f,
                0.8f, -0.4f,
                1.2f, 0.7f,
                -0.3f, 1.1f
        });
    }

    @Test
    public void noopEditDoesNotCreateMeaningfulHistoryMutation() {
        FixtureHarness harness = FixtureHarness.create();

        EditFixtureCommand noop = harness.newEdit(cmd -> {
            // same values
        }, false);
        Assert.assertTrue(noop.isNoop());

        executeIfMeaningful(harness.history, noop);

        Assert.assertFalse(harness.history.canUndo());
        Assert.assertEquals(0, harness.history.getCursor());
    }

    @Test
    public void mixedChainEnableAddEditUndoRedoKeepsExactEcsValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(64);
        PhysicsSelectionService selection = new PhysicsSelectionService();

        int bodyEid = world.create();
        historyIds.ensureForEntity(bodyEid);
        world.getMapper(TransformComponent.class).create(bodyEid);

        TogglePhysicsBodyCommand enable = new TogglePhysicsBodyCommand(
                world,
                historyIds,
                new games.pixscape.runtime.service.PhysicsService(
                        world, null,
                        games.pixscape.studio.configuration.ProjectConfig.getInstance()
                                .getCurrentSceneMeta()),
                bodyEid,
                true,
                PhysicsBodyComponent.DYNAMIC,
                true
        );
        history.execute(enable);

        AddFixtureCommand add = new AddFixtureCommand(
                world, historyIds, selection,
                new games.pixscape.runtime.service.PhysicsService(
                        world, null,
                        games.pixscape.studio.configuration.ProjectConfig.getInstance()
                                .getCurrentSceneMeta()),
                bodyEid);
        history.execute(add);

        int physicsShapeId = add.getCreatedFixtureId();
        selection.setSelectedShape(bodyEid, physicsShapeId);

        EditFixtureCommand editA = newEdit(world, historyIds, selection, bodyEid, physicsShapeId, fixture -> {
            fixture.density = 3f;
            fixture.friction = 0.65f;
            fixture.restitution = 0.1f;
        }, games.pixscape.runtime.render.PhysicsDirtyBits.FIXTURE, false);
        history.execute(editA);

        EditFixtureCommand editB = newEdit(world, historyIds, selection, bodyEid, physicsShapeId, fixture -> {
            fixture.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_CIRCLE;
            fixture.directGeometry.radius = 0.9f;
            fixture.directGeometry.offsetX = 0.2f;
            fixture.directGeometry.offsetY = -0.25f;
            fixture.sensor = true;
            fixture.categoryBits = (short) 0x0010;
            fixture.maskBits = (short) 0x0FFF;
            fixture.groupIndex = (short) -3;
        }, games.pixscape.runtime.render.PhysicsDirtyBits.FIXTURE, true);
        history.execute(editB);

        PhysicsShapeData fixture = fixture(world, bodyEid, physicsShapeId);
        Assert.assertNotNull(fixture);
        assertFixtureScalars(fixture, 3f, 0.65f, 0.1f);
        assertFixtureShape(fixture, PhysicsDirectGeometryData.SHAPE_CIRCLE, 0.5f, 0.5f, 0.9f, 0.2f, -0.25f);
        assertFixtureFilter(fixture, (short) 0x0010, (short) 0x0FFF, (short) -3);
        Assert.assertTrue(fixture.sensor);

        history.undo();
        fixture = fixture(world, bodyEid, physicsShapeId);
        assertFixtureScalars(fixture, 3f, 0.65f, 0.1f);
        assertFixtureShape(fixture, PhysicsDirectGeometryData.SHAPE_BOX, 0.5f, 0.5f, 0.5f, 0f, 0f);
        assertFixtureFilter(fixture, (short) 0x0001, (short) 0xFFFF, (short) 0);
        Assert.assertFalse(fixture.sensor);

        history.undo();
        fixture = fixture(world, bodyEid, physicsShapeId);
        assertFixtureScalars(fixture, 1f, 0.2f, 0f);
        assertFixtureShape(fixture, PhysicsDirectGeometryData.SHAPE_BOX, 0.5f, 0.5f, 0.5f, 0f, 0f);

        history.undo();
        Assert.assertNull(fixture(world, bodyEid, physicsShapeId));

        history.undo();
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(bodyEid));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(bodyEid));

        history.redo();
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(bodyEid));
        Assert.assertTrue(world.getMapper(PhysicsShapesComponent.class).has(bodyEid));

        history.redo();
        fixture = fixture(world, bodyEid, physicsShapeId);
        Assert.assertNotNull(fixture);

        history.redo();
        fixture = fixture(world, bodyEid, physicsShapeId);
        assertFixtureScalars(fixture, 3f, 0.65f, 0.1f);

        history.redo();
        fixture = fixture(world, bodyEid, physicsShapeId);
        assertFixtureScalars(fixture, 3f, 0.65f, 0.1f);
        assertFixtureShape(fixture, PhysicsDirectGeometryData.SHAPE_CIRCLE, 0.5f, 0.5f, 0.9f, 0.2f, -0.25f);
        assertFixtureFilter(fixture, (short) 0x0010, (short) 0x0FFF, (short) -3);
        Assert.assertTrue(fixture.sensor);
    }

    private static void assertFixtureScalars(PhysicsShapeData fixture, float density, float friction, float restitution) {
        Assert.assertEquals(density, fixture.density, 0f);
        Assert.assertEquals(friction, fixture.friction, 0f);
        Assert.assertEquals(restitution, fixture.restitution, 0f);
    }

    private static void assertFixtureFilter(PhysicsShapeData fixture, short categoryBits, short maskBits, short groupIndex) {
        Assert.assertEquals(categoryBits, fixture.categoryBits);
        Assert.assertEquals(maskBits, fixture.maskBits);
        Assert.assertEquals(groupIndex, fixture.groupIndex);
    }

    private static void assertFixtureShape(PhysicsShapeData fixture,
                                           int shapeType,
                                           float halfWidth,
                                           float halfHeight,
                                           float radius,
                                           float offsetX,
                                           float offsetY) {
        Assert.assertEquals(shapeType, fixture.directGeometry.shapeType);
        Assert.assertEquals(halfWidth, fixture.directGeometry.halfWidth, 0f);
        Assert.assertEquals(halfHeight, fixture.directGeometry.halfHeight, 0f);
        Assert.assertEquals(radius, fixture.directGeometry.radius, 0f);
        Assert.assertEquals(offsetX, fixture.directGeometry.offsetX, 0f);
        Assert.assertEquals(offsetY, fixture.directGeometry.offsetY, 0f);
    }

    private static void assertPolygon(PhysicsShapeData fixture, int expectedCount, float[] expectedVerts) {
        Assert.assertEquals(expectedCount, fixture.directGeometry.polygonVertexCount);
        Assert.assertNotNull(fixture.directGeometry.polygonVertices);
        Assert.assertEquals(expectedCount * 2, fixture.directGeometry.polygonVertices.length);

        for (int i = 0; i < expectedCount * 2; i++) {
            Assert.assertEquals(expectedVerts[i], fixture.directGeometry.polygonVertices[i], 0f);
        }
    }

    private static EditFixtureCommand newEdit(World world,
                                              HistoryIdRegistry historyIds,
                                              PhysicsSelectionService selection,
                                              int bodyEid,
                                              int physicsShapeId,
                                              Consumer<PhysicsShapeData> mutation,
                                              int dirtyMask,
                                              boolean publishStructureChanged) {
        PhysicsShapeData current = fixture(world, bodyEid, physicsShapeId);
        Assert.assertNotNull(current);

        PhysicsShapeData after = current.copy();
        mutation.accept(after);

        return new EditFixtureCommand(
                world,
                historyIds,
                selection,
                bodyEid,
                physicsShapeId,
                EditFixtureCommand.Snapshot.capture(current),
                EditFixtureCommand.Snapshot.capture(after),
                dirtyMask,
                publishStructureChanged
        );
    }

    private static PhysicsShapeData fixture(World world, int bodyEid, int physicsShapeId) {
        PhysicsShapesComponent fixtures = world.getMapper(PhysicsShapesComponent.class).getSafe(bodyEid, null);
        if (fixtures == null || !fixtures.hasShapes()) return null;

        for (int i = 0, n = fixtures.shapes.size; i < n; i++) {
            PhysicsShapeData fixture = fixtures.shapes.get(i);
            if (fixture == null) continue;
            if (fixture.physicsShapeId == physicsShapeId) return fixture;
        }
        return null;
    }

    private static void executeIfMeaningful(HistoryManager history, Command command) {
        if (command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        history.execute(command);
    }

    private static final class FixtureHarness {
        private final World world;
        private final HistoryIdRegistry historyIds;
        private final HistoryManager history;
        private final PhysicsSelectionService selection;
        private final int bodyEid;
        private final int physicsShapeId;

        private FixtureHarness(World world,
                               HistoryIdRegistry historyIds,
                               HistoryManager history,
                               PhysicsSelectionService selection,
                               int bodyEid,
                               int physicsShapeId) {
            this.world = world;
            this.historyIds = historyIds;
            this.history = history;
            this.selection = selection;
            this.bodyEid = bodyEid;
            this.physicsShapeId = physicsShapeId;
        }

        static FixtureHarness create() {
            World world = new World(new WorldConfiguration());
            HistoryIdRegistry historyIds = new HistoryIdRegistry();
            HistoryManager history = new HistoryManager(64);
            PhysicsSelectionService selection = new PhysicsSelectionService();

            int bodyEid = world.create();
            historyIds.ensureForEntity(bodyEid);
            world.getMapper(TransformComponent.class).create(bodyEid);

            PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(bodyEid);
            body.type = PhysicsBodyComponent.DYNAMIC;
            body.enabled = true;

            world.getMapper(PhysicsRuntimeBodyComponent.class).create(bodyEid);
            PhysicsShapesComponent fixtures = world.getMapper(PhysicsShapesComponent.class).create(bodyEid);
            PhysicsShapeData fixture = FixtureCommandSupport.createDefaultFixture();
            fixture.physicsShapeId = 1;
            fixtures.shapes.add(fixture);

            selection.setSelectedShape(bodyEid, fixture.physicsShapeId);

            return new FixtureHarness(world, historyIds, history, selection, bodyEid, fixture.physicsShapeId);
        }

        EditFixtureCommand newEdit(Consumer<PhysicsShapeData> mutation, boolean publishStructureChanged) {
            return newEdit(mutation, publishStructureChanged, games.pixscape.runtime.render.PhysicsDirtyBits.FIXTURE);
        }

        EditFixtureCommand newEdit(Consumer<PhysicsShapeData> mutation,
                                   boolean publishStructureChanged,
                                   int dirtyMask) {
            return EditFixtureCommandTest.newEdit(
                    world,
                    historyIds,
                    selection,
                    bodyEid,
                    physicsShapeId,
                    mutation,
                    dirtyMask,
                    publishStructureChanged
            );
        }

        PhysicsShapeData fixture() {
            return EditFixtureCommandTest.fixture(world, bodyEid, physicsShapeId);
        }
    }
}
