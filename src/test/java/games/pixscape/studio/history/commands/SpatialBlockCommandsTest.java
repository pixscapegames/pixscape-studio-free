package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockCommandsTest {
    @Test
    public void addSpatialBlock_allocatesStableIdAndUndoRedoRestoresSelection() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = world.create();
        history.historyIds().ensureForEntity(layerId);

        SpatialBlockData block = block(0, "Wall", 2f, 3f);

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                block
        );
        history.execute(command);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).get(layerId);
        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(1, component.blocks.first().id);
        Assert.assertEquals(2f, component.blocks.first().x, 0.0001f);
        Assert.assertEquals(3f, component.blocks.first().y, 0.0001f);
        Assert.assertEquals(1, command.getBlockId());
        Assert.assertEquals(layerId, selection.getEditingLayerEntityId());
        Assert.assertEquals(1, selection.getSelectedBlockId());

        history.undo();
        Assert.assertEquals(0, component.blocks.size);
        Assert.assertEquals(layerId, selection.getEditingLayerEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());

        history.redo();
        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(1, component.blocks.first().id);
        Assert.assertEquals(1, selection.getSelectedBlockId());
    }

    @Test
    public void editSpatialBlock_updatesRuntimeDataWithUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = world.create();
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(7, "Before", 1f, 1f);
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.name = "After";
        after.width = 4f;
        after.depth = 2f;
        after.altitude = 6f;
        after.height = 32f;
        after.orientation = SpatialBlockOrientation.TILE_AXIS_X;
        after.physicsCollision = true;

        history.execute(new EditSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                7,
                before,
                after
        ));

        SpatialBlockData edited = component.blocks.first();
        Assert.assertEquals("After", edited.name);
        Assert.assertEquals(4f, edited.width, 0.0001f);
        Assert.assertEquals(2f, edited.depth, 0.0001f);
        Assert.assertEquals(6f, edited.altitude, 0.0001f);
        Assert.assertEquals(32f, edited.height, 0.0001f);
        Assert.assertEquals(SpatialBlockOrientation.TILE_AXIS_X, edited.orientation);
        Assert.assertTrue(edited.physicsCollision);

        history.undo();
        SpatialBlockData restored = component.blocks.first();
        Assert.assertEquals("Before", restored.name);
        Assert.assertEquals(1f, restored.width, 0.0001f);
        Assert.assertEquals(SpatialBlockOrientation.TILE_CELL, restored.orientation);
        Assert.assertFalse(restored.physicsCollision);

        history.redo();
        Assert.assertEquals("After", component.blocks.first().name);
        Assert.assertEquals(SpatialBlockOrientation.TILE_AXIS_X, component.blocks.first().orientation);
    }

    @Test
    public void moveSpatialBlock_onlyChangesTilePosition() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = world.create();
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(3, "Mover", 2f, 4f);
        block.width = 2f;
        block.height = 12f;
        component.blocks.add(block);

        history.execute(new MoveSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                3,
                2f,
                4f,
                9f,
                10f
        ));

        Assert.assertEquals(9f, block.x, 0.0001f);
        Assert.assertEquals(10f, block.y, 0.0001f);
        Assert.assertEquals(2f, block.width, 0.0001f);
        Assert.assertEquals(12f, block.height, 0.0001f);

        history.undo();
        Assert.assertEquals(2f, block.x, 0.0001f);
        Assert.assertEquals(4f, block.y, 0.0001f);
        Assert.assertEquals(2f, block.width, 0.0001f);
        Assert.assertEquals(12f, block.height, 0.0001f);

        history.redo();
        Assert.assertEquals(9f, block.x, 0.0001f);
        Assert.assertEquals(10f, block.y, 0.0001f);
        Assert.assertEquals(2f, block.width, 0.0001f);
        Assert.assertEquals(12f, block.height, 0.0001f);
    }

    @Test
    public void deleteSpatialBlock_preservesOrderOnUndo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = world.create();
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        component.blocks.add(block(1, "A", 0f, 0f));
        component.blocks.add(block(2, "B", 1f, 0f));
        component.blocks.add(block(3, "C", 2f, 0f));
        selection.selectBlock(layerId, 2);

        history.execute(new DeleteSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                2
        ));

        Assert.assertEquals(2, component.blocks.size);
        Assert.assertEquals(1, component.blocks.get(0).id);
        Assert.assertEquals(3, component.blocks.get(1).id);
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());

        history.undo();
        Assert.assertEquals(3, component.blocks.size);
        Assert.assertEquals(1, component.blocks.get(0).id);
        Assert.assertEquals(2, component.blocks.get(1).id);
        Assert.assertEquals(3, component.blocks.get(2).id);
        Assert.assertEquals(2, selection.getSelectedBlockId());

        history.redo();
        Assert.assertEquals(2, component.blocks.size);
        Assert.assertEquals(3, component.blocks.get(1).id);
    }

    @Test
    public void editSpatialBlockPhysicsCollision_enablesStaticLayerBodyAndAddsFootprintPolygon() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(4, "Collider", 2f, 3f);
        original.width = 2f;
        original.depth = 1f;
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.physicsCollision = true;

        history.execute(new EditSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                4,
                before,
                after
        ));

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(layerId);
        Assert.assertEquals(PhysicsBodyComponent.STATIC, body.type);
        FixtureDefData fixture = spatialFixture(world, layerId, 4);
        Assert.assertNotNull(fixture);
        Assert.assertEquals(FixtureDefData.SHAPE_POLYGON, fixture.shapeType);
        Assert.assertEquals(4, fixture.polyCount);
        Assert.assertArrayEquals(
                new float[]{0f, 1.25f, 1f, 1.75f, 0.5f, 2f, -0.5f, 1.5f},
                fixture.polyVerts,
                0.0001f
        );

        history.undo();
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(layerId));
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(layerId));
        Assert.assertFalse(component.blocks.first().physicsCollision);

        history.redo();
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(layerId));
        Assert.assertTrue(component.blocks.first().physicsCollision);
    }

    @Test
    public void editSpatialBlockFootprint_updatesGeneratedCollisionPolygonAndUndoRestoresIt() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(5, "Collider", 1f, 1f);
        original.physicsCollision = true;
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.x = 2f;
        after.y = 3f;
        after.width = 2f;
        after.depth = 1f;

        SpatialBlockPhysicsSync.sync(world, layerId, original, this);
        float[] originalVerts = spatialFixture(world, layerId, 5).polyVerts.clone();

        history.execute(new EditSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                5,
                before,
                after
        ));

        Assert.assertArrayEquals(
                new float[]{0f, 1.25f, 1f, 1.75f, 0.5f, 2f, -0.5f, 1.5f},
                spatialFixture(world, layerId, 5).polyVerts,
                0.0001f
        );

        history.undo();
        Assert.assertArrayEquals(originalVerts, spatialFixture(world, layerId, 5).polyVerts, 0.0001f);
    }

    @Test
    public void deleteSpatialBlock_removesGeneratedCollisionFixtureAndUndoRestoresIt() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(6, "Collider", 1f, 1f);
        block.physicsCollision = true;
        component.blocks.add(block);
        SpatialBlockPhysicsSync.sync(world, layerId, block, this);

        history.execute(new DeleteSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                6
        ));

        Assert.assertNull(spatialFixture(world, layerId, 6));

        history.undo();
        Assert.assertNotNull(spatialFixture(world, layerId, 6));
    }

    private static SpatialBlockData block(int id, String name, float x, float y) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.name = name;
        block.enabled = true;
        block.x = x;
        block.y = y;
        block.width = 1f;
        block.depth = 1f;
        block.altitude = 0f;
        block.height = 8f;
        block.orientation = SpatialBlockOrientation.TILE_CELL;
        block.actorOccluder = true;
        return block;
    }

    private static int tiledLayer(World world) {
        int layerId = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.data = new TiledMapLayerData(
                10,
                10,
                100,
                50,
                16,
                SceneMetaRuntime.TiledProjection.ISO
        );
        return layerId;
    }

    private static FixtureDefData spatialFixture(World world, int layerId, int blockId) {
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(layerId, null);
        if (fixtures == null) return null;
        int fixtureId = SpatialBlockPhysicsSync.fixtureIdForBlock(blockId);
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) {
                return fixture;
            }
        }
        return null;
    }
}
