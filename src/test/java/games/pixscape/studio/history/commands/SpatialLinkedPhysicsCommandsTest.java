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
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
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

public class SpatialLinkedPhysicsCommandsTest {
    private ProjectConfig previousConfig;
    private SceneMeta meta;

    @Before
    public void configureScene() {
        previousConfig = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Linked");
        meta = config.getCurrentSceneMeta();
        meta.pixelsPerMeter = 32f;
        ProjectConfig.setInstance(config);
    }

    @After
    public void restoreConfig() {
        ProjectConfig.setInstance(previousConfig);
    }

    @Test
    public void moveResizeUndoAndRedoRecompileLinkedFixture() {
        Harness harness = new Harness(true);
        float[] before = vertices(harness.compiled());
        SpatialBlockData edited = harness.block(7).copy();
        edited.x = 2.25f;
        edited.y = 3.25f;
        edited.width = 0.75f;
        edited.depth = 0.75f;

        harness.history.execute(new EditSpatialBlockCommand(
                harness.world,
                harness.history.historyIds(),
                harness.selection,
                harness.owner,
                7,
                harness.block(7).copy(),
                edited));

        float[] after = vertices(harness.compiled());
        Assert.assertEquals(2.25f, harness.block(7).x, 0f);
        Assert.assertEquals(0.75f, harness.block(7).width, 0f);
        Assert.assertFalse(java.util.Arrays.equals(before, after));
        Assert.assertNull(harness.shapes().shapes.first().geometry);

        harness.history.undo();
        Assert.assertEquals(2f, harness.block(7).x, 0f);
        Assert.assertArrayEquals(before, vertices(harness.compiled()), 0.0001f);

        harness.history.redo();
        Assert.assertEquals(2.25f, harness.block(7).x, 0f);
        Assert.assertArrayEquals(after, vertices(harness.compiled()), 0.0001f);
        Assert.assertNull(harness.shapes().shapes.first().geometry);
    }

    @Test
    public void linkedBlockDeletionRemovesCollisionAndUndoRestoresSameIdAndStaticBody() {
        Harness harness = new Harness(true);
        SpatialBlocksComponent blocks = harness.blocks();
        int revision = blocks.revision;
        int physicsShapeId = harness.shapes().shapes.first().physicsShapeId;

        DeleteSpatialBlockCommand command = new DeleteSpatialBlockCommand(
                harness.world,
                harness.history.historyIds(),
                harness.selection,
                harness.owner,
                7);
        harness.history.execute(command);

        Assert.assertEquals(1, harness.history.getCursor());
        Assert.assertNull(harness.block(7));
        Assert.assertEquals(revision + 1, blocks.revision);
        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class)
                .has(harness.owner));
        Assert.assertFalse(harness.world.getMapper(PhysicsShapesComponent.class)
                .has(harness.owner));

        harness.history.undo();
        Assert.assertNotNull(harness.block(7));
        Assert.assertEquals(physicsShapeId,
                harness.shapes().shapes.first().physicsShapeId);
        Assert.assertNull(harness.shapes().shapes.first().geometry);
        Assert.assertEquals(PhysicsBodyComponent.STATIC,
                harness.world.getMapper(PhysicsBodyComponent.class)
                        .get(harness.owner).type);
        Assert.assertTrue(harness.compiled().valid);
        Assert.assertEquals(7, harness.selection.getSelectedBlockId());

        harness.history.redo();
        Assert.assertNull(harness.block(7));
        Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class)
                .has(harness.owner));
    }

    @Test
    public void deletingLinkedBlockPreservesManualShapeAndStaticBody() {
        Harness harness = new Harness(true);
        PhysicsShapeData manual = PhysicsService.createDefaultShape(2);
        harness.shapes().shapes.add(manual);
        PreparedPhysicsBodyCandidate prepared = PhysicsService.prepareBodyCandidate(
                harness.world,
                harness.owner,
                harness.shapes().shapes,
                meta.pixelsPerMeter);
        PhysicsService.publishPreparedCandidate(
                harness.shapes(), harness.compiled(), prepared);
        harness.world.getMapper(PhysicsBodyComponent.class)
                .get(harness.owner).type = PhysicsBodyComponent.DYNAMIC;

        harness.history.execute(new DeleteSpatialBlockCommand(
                harness.world,
                harness.history.historyIds(),
                harness.selection,
                harness.owner,
                7));

        Assert.assertNull(harness.block(7));
        Assert.assertEquals(1, harness.shapes().shapes.size);
        Assert.assertEquals(2, harness.shapes().shapes.first().physicsShapeId);
        Assert.assertEquals(PhysicsBodyComponent.STATIC,
                harness.world.getMapper(PhysicsBodyComponent.class)
                        .get(harness.owner).type);
        Assert.assertEquals(1, harness.compiled().fixtures.size);
    }

    @Test
    public void deletingOneLinkedBlockPreservesOtherLinkedCollision() {
        Harness harness = new Harness(true);
        PhysicsShapeData other = new PhysicsShapeData();
        other.physicsShapeId = 2;
        other.spatialBlockId = 8;
        harness.shapes().shapes.add(other);
        PreparedPhysicsBodyCandidate prepared = PhysicsService.prepareBodyCandidate(
                harness.world,
                harness.owner,
                harness.shapes().shapes,
                meta.pixelsPerMeter);
        PhysicsService.publishPreparedCandidate(
                harness.shapes(), harness.compiled(), prepared);

        harness.history.execute(new DeleteSpatialBlockCommand(
                harness.world,
                harness.history.historyIds(),
                harness.selection,
                harness.owner,
                7));

        Assert.assertNull(harness.block(7));
        Assert.assertNotNull(harness.block(8));
        Assert.assertEquals(1, harness.shapes().shapes.size);
        Assert.assertEquals(2, harness.shapes().shapes.first().physicsShapeId);
        Assert.assertEquals(8, harness.shapes().shapes.first().spatialBlockId);
        Assert.assertNull(harness.shapes().shapes.first().geometry);
        Assert.assertEquals(PhysicsBodyComponent.STATIC,
                harness.world.getMapper(PhysicsBodyComponent.class)
                        .get(harness.owner).type);
    }

    @Test
    public void deletePreparationFailureLeavesBlockRelationCacheAndRevisionUntouched() {
        Harness harness = new Harness(true);
        SpatialBlocksComponent blocks = harness.blocks();
        int revision = blocks.revision;
        PhysicsShapesComponent shapes = harness.shapes();
        PhysicsCompiledFixturesComponent compiled = harness.compiled();
        CompiledFixtureData fixture = compiled.fixtures.first();
        PhysicsShapeData invalid = new PhysicsShapeData();
        invalid.physicsShapeId = 2;
        invalid.spatialBlockId = 999;
        shapes.shapes.add(invalid);

        DeleteSpatialBlockCommand command = new DeleteSpatialBlockCommand(
                harness.world,
                harness.history.historyIds(),
                harness.selection,
                harness.owner,
                7);
        Assert.assertEquals(CommandOutcome.REJECTED, command.executeOutcome());

        Assert.assertNotNull(harness.block(7));
        Assert.assertEquals(revision, blocks.revision);
        Assert.assertSame(shapes, harness.shapes());
        Assert.assertSame(compiled, harness.compiled());
        Assert.assertSame(fixture, compiled.fixtures.first());
        Assert.assertEquals(2, shapes.shapes.size);
    }

    @Test
    public void unlinkedBlockDeletionIsAllowedAndRecompilesLinkedBody() {
        Harness harness = new Harness(true);
        int revision = harness.blocks().revision;

        DeleteSpatialBlockCommand command = new DeleteSpatialBlockCommand(
                harness.world,
                harness.history.historyIds(),
                harness.selection,
                harness.owner,
                8);
        Assert.assertEquals(CommandOutcome.APPLIED, command.executeOutcome());

        Assert.assertNull(harness.block(8));
        Assert.assertNotNull(harness.block(7));
        Assert.assertEquals(revision + 1, harness.blocks().revision);
        Assert.assertTrue(harness.compiled().valid);
    }

    @Test
    public void failedPhysicsPreparationRestoresTemporaryBlockSubstitution() {
        Harness harness = new Harness(false);
        SpatialBlocksComponent blocks = harness.blocks();
        Array<SpatialBlockData> original = blocks.blocks;
        int revision = blocks.revision;
        Array<SpatialBlockData> candidate =
                new Array<>(SpatialBlockData[]::new);
        SpatialBlockData moved = harness.block(7).copy();
        moved.x = 9f;
        candidate.add(moved);

        Assert.assertEquals(
                CommandOutcome.REJECTED,
                SpatialBlockCommandSupport.replaceAllValidated(
                        harness.world, harness.owner, candidate));

        Assert.assertSame(original, blocks.blocks);
        Assert.assertEquals(2, blocks.blocks.first().x, 0f);
        Assert.assertEquals(revision, blocks.revision);
    }

    private final class Harness {
        final World world = new World();
        final int owner = world.create();
        final HistoryManager history = new HistoryManager(8);
        final SpatialBlockSelectionService selection =
                new SpatialBlockSelectionService();

        Harness(boolean validCompiledCache) {
            history.historyIds().ensureForEntity(owner);
            world.getMapper(TransformComponent.class).create(owner);
            world.getMapper(PhysicsBodyComponent.class).create(owner);
            TiledLayerComponent tiled = world.getMapper(
                    TiledLayerComponent.class).create(owner);
            tiled.data = new TiledMapLayerData(
                    20, 20, 32, 16, 8,
                    TiledProjection.ORTHO);
            for (int gy = 0; gy < 20; gy++) {
                for (int gx = 0; gx < 20; gx++) {
                    tiled.data.setTile(gx, gy, 1);
                }
            }
            SpatialBlocksComponent blocks = world.getMapper(
                    SpatialBlocksComponent.class).create(owner);
            blocks.blocks.add(SpatialLinkedPhysicsCommandsTest.block(
                    7, 2f, 3f));
            blocks.blocks.add(SpatialLinkedPhysicsCommandsTest.block(
                    8, 10f, 10f));

            PhysicsShapesComponent shapes = world.getMapper(
                    PhysicsShapesComponent.class).create(owner);
            PhysicsShapeData linked = new PhysicsShapeData();
            linked.physicsShapeId = 1;
            linked.spatialBlockId = 7;
            shapes.shapes.add(linked);
            PhysicsCompiledFixturesComponent compiled = world.getMapper(
                    PhysicsCompiledFixturesComponent.class).create(owner);
            if (validCompiledCache) {
                PreparedPhysicsBodyCandidate prepared =
                        PhysicsService.prepareBodyCandidate(
                                world, owner, shapes.shapes,
                                meta.pixelsPerMeter);
                PhysicsService.publishPreparedCandidate(
                        shapes, compiled, prepared);
            }
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

        PhysicsCompiledFixturesComponent compiled() {
            return world.getMapper(
                    PhysicsCompiledFixturesComponent.class).get(owner);
        }
    }

    private static SpatialBlockData block(int id, float x, float y) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.structureId = id;
        block.x = x;
        block.y = y;
        block.width = 1f;
        block.depth = 1f;
        block.altitude = 0f;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(Math.round(x), Math.round(y), 1);
        return block;
    }

    private static float[] vertices(
            PhysicsCompiledFixturesComponent compiled) {
        float[] source = compiled.fixtures.first().polygonVertices;
        float[] copy = new float[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }
}
