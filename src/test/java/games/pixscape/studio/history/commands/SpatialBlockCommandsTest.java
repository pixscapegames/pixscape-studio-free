package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialBlockData;
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
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlockData block = block(0, "Wall", 2f, 3f);
        occupyLinkedTiles(world, layerId, block);

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
        Assert.assertTrue(component.blocks.first().linkedTileRefsAuthored);
        Assert.assertEquals(1, component.blocks.first().linkedTileRefs.size);
        Assert.assertEquals(2, component.blocks.first().linkedTileRefs.get(0).gx);
        Assert.assertEquals(3, component.blocks.first().linkedTileRefs.get(0).gy);
        Assert.assertEquals(1001, component.blocks.first().linkedTileRefs.get(0).tileAssetId);
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
        Assert.assertEquals(1, component.blocks.first().linkedTileRefs.size);
        Assert.assertEquals(1001, component.blocks.first().linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(1, selection.getSelectedBlockId());
    }

    @Test
    public void addSpatialBlock_redoDoesNotDuplicateLinkedRefs() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlockData block = block(0, "Wall", 2f, 3f);
        occupyLinkedTiles(world, layerId, block);

        history.execute(new AddSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                block
        ));

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).get(layerId);
        history.undo();
        history.redo();
        history.undo();
        history.redo();

        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(1, component.blocks.first().linkedTileRefs.size);
        Assert.assertEquals(2, component.blocks.first().linkedTileRefs.first().gx);
        Assert.assertEquals(3, component.blocks.first().linkedTileRefs.first().gy);
    }

    @Test
    public void addSpatialBlock_refusesMalformedAuthoredGeometry() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlockData block = block(0, "Invalid", 2f, 3f);
        block.width = 0f;

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                block
        );

        Assert.assertTrue(command.isNoop());
        command.redo();
        Assert.assertFalse(world.getMapper(SpatialBlocksComponent.class).has(layerId));
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());
    }

    @Test
    public void spatialBlockCopyPreservesLinkedRefsDeeplyEnoughForHistory() {
        SpatialBlockData original = block(3, "Original", 4f, 5f);

        SpatialBlockData copy = original.copy();
        original.linkedTileRefs.first().tileAssetId = 999;
        original.addLinkedTileRef(6, 7, 777);

        Assert.assertTrue(copy.linkedTileRefsAuthored);
        Assert.assertEquals(1, copy.linkedTileRefs.size);
        Assert.assertEquals(4, copy.linkedTileRefs.first().gx);
        Assert.assertEquals(5, copy.linkedTileRefs.first().gy);
        Assert.assertEquals(1003, copy.linkedTileRefs.first().tileAssetId);
    }

    @Test
    public void editSpatialBlock_updatesRuntimeDataWithUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(7, "Before", 1f, 1f);
        occupyLinkedTiles(world, layerId, original);
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.name = "After";
        after.x = 1.12f;
        after.y = 1.18f;
        after.width = 0.73f;
        after.depth = 0.61f;
        after.altitude = 6f;
        after.height = 32f;
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
        Assert.assertEquals(1.12f, edited.x, 0f);
        Assert.assertEquals(1.18f, edited.y, 0f);
        Assert.assertEquals(0.73f, edited.width, 0f);
        Assert.assertEquals(0.61f, edited.depth, 0f);
        Assert.assertEquals(1, edited.linkedTileRefs.size);
        Assert.assertEquals(6f, edited.altitude, 0.0001f);
        Assert.assertEquals(32f, edited.height, 0.0001f);
        Assert.assertTrue(edited.physicsCollision);

        history.undo();
        SpatialBlockData restored = component.blocks.first();
        Assert.assertEquals("Before", restored.name);
        Assert.assertEquals(1f, restored.width, 0.0001f);
        Assert.assertFalse(restored.physicsCollision);

        history.redo();
        Assert.assertEquals("After", component.blocks.first().name);
    }

    @Test
    public void moveSpatialBlock_onlyChangesTilePosition() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(3, "Mover", 2.1f, 4.1f);
        setLinkedRect(block, 2, 4, 4, 6);
        occupyLinkedTiles(world, layerId, block);
        block.width = 0.5f;
        block.depth = 0.5f;
        block.height = 12f;
        component.blocks.add(block);

        history.execute(new MoveSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                3,
                2.1f,
                4.1f,
                3.2f,
                5.2f
        ));

        Assert.assertEquals(3.2f, component.blocks.first().x, 0f);
        Assert.assertEquals(5.2f, component.blocks.first().y, 0f);
        Assert.assertEquals(0.5f, component.blocks.first().width, 0f);
        Assert.assertEquals(12f, component.blocks.first().height, 0.0001f);

        history.undo();
        Assert.assertEquals(2.1f, component.blocks.first().x, 0f);
        Assert.assertEquals(4.1f, component.blocks.first().y, 0f);
        Assert.assertEquals(0.5f, component.blocks.first().width, 0f);
        Assert.assertEquals(12f, component.blocks.first().height, 0.0001f);

        history.redo();
        Assert.assertEquals(3.2f, component.blocks.first().x, 0f);
        Assert.assertEquals(5.2f, component.blocks.first().y, 0f);
        Assert.assertEquals(0.5f, component.blocks.first().width, 0f);
        Assert.assertEquals(12f, component.blocks.first().height, 0.0001f);
    }

    @Test
    public void deleteSpatialBlock_preservesOrderOnUndo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData a = block(1, "A", 0f, 0f);
        SpatialBlockData b = block(2, "B", 1f, 0f);
        SpatialBlockData c = block(3, "C", 2f, 0f);
        occupyLinkedTiles(world, layerId, a);
        occupyLinkedTiles(world, layerId, b);
        occupyLinkedTiles(world, layerId, c);
        component.blocks.add(a);
        component.blocks.add(b);
        component.blocks.add(c);
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
        setLinkedRect(original, 2, 3, 3, 3);
        occupyLinkedTiles(world, layerId, original);
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
        setLinkedRect(original, 1, 1, 3, 3);
        occupyLinkedTiles(world, layerId, original);
        original.physicsCollision = true;
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.x = 1.2f;
        after.y = 1.3f;
        after.width = 1.4f;
        after.depth = 0.5f;

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
                new float[]{0.45f, 0.625f, 1.15f, 0.975f, 0.9f, 1.1f, 0.2f, 0.75f},
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
        occupyLinkedTiles(world, layerId, block);
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

    @Test
    public void fractionalFootprintUndoRedoHasNoDriftAndKeepsRefs() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(11, "Precise", 2f, 3f);
        occupyLinkedTiles(world, layerId, original);
        component.blocks.add(original);
        SpatialBlockData after = original.copy();
        after.x = 2.1415927f;
        after.y = 3.271828f;
        after.width = 0.612345f;
        after.depth = 0.198765f;

        history.execute(new EditSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, 11, original.copy(), after));
        for (int i = 0; i < 4; i++) {
            history.undo();
            history.redo();
        }

        SpatialBlockData edited = component.blocks.first();
        Assert.assertEquals(Float.floatToIntBits(after.x), Float.floatToIntBits(edited.x));
        Assert.assertEquals(Float.floatToIntBits(after.y), Float.floatToIntBits(edited.y));
        Assert.assertEquals(Float.floatToIntBits(after.width), Float.floatToIntBits(edited.width));
        Assert.assertEquals(Float.floatToIntBits(after.depth), Float.floatToIntBits(edited.depth));
        Assert.assertEquals(1, edited.linkedTileRefs.size);
        Assert.assertEquals(2, edited.linkedTileRefs.first().gx);
        Assert.assertEquals(3, edited.linkedTileRefs.first().gy);
    }

    private static SpatialBlockData block(int id, String name, float x, float y) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.structureId = Math.max(1, id);
        block.name = name;
        block.x = x;
        block.y = y;
        block.width = 1f;
        block.depth = 1f;
        block.altitude = 0f;
        block.height = 8f;
        block.actorOccluder = true;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(Math.round(x), Math.round(y), 1000 + Math.max(1, id));
        return block;
    }

    private static void occupyLinkedTiles(World world, int layerId, SpatialBlockData block) {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        for (int i = 0; i < block.linkedTileRefs.size; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            tiled.data.setTile(ref.gx, ref.gy, ref.tileAssetId);
        }
    }

    private static void setLinkedRect(SpatialBlockData block,
                                      int minGx, int minGy, int maxGx, int maxGy) {
        block.beginAuthoredLinkedTileRefs();
        for (int gy = minGy; gy <= maxGy; gy++) {
            for (int gx = minGx; gx <= maxGx; gx++) block.addLinkedTileRef(gx, gy, 1);
        }
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
        for (int gy = 0; gy < 10; gy++) {
            for (int gx = 0; gx < 10; gx++) tiled.data.setTile(gx, gy, 1);
        }
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
