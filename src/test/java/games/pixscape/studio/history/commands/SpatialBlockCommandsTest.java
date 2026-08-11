package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;

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
    public void addSpatialBlock_highWaterMismatchDoesNotMutateStateOrHistory() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlockData candidate = block(0, "Prepared", 2f, 3f);
        occupyLinkedTiles(world, layerId, candidate);
        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, candidate);

        SpatialBlocksComponent component =
                world.getMapper(SpatialBlocksComponent.class).create(layerId);
        component.nextSpatialBlockId = 2;
        component.revision = 7;
        selection.selectBlock(layerId, 41);

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class, () -> history.execute(command));

        Assert.assertTrue(failure.getMessage().contains("expected 1, current 2"));
        Assert.assertEquals(0, component.blocks.size);
        Assert.assertEquals(7, component.revision);
        Assert.assertEquals(2, component.nextSpatialBlockId);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertEquals(layerId, selection.getEditingLayerEntityId());
        Assert.assertEquals(41, selection.getSelectedBlockId());
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

        history.undo();
        SpatialBlockData restored = component.blocks.first();
        Assert.assertEquals("Before", restored.name);
        Assert.assertEquals(1f, restored.width, 0.0001f);

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

    @Test
    public void rejectedExecuteLeavesModelRevisionAndHistoryUnchanged() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlockData candidate = block(0, "Candidate", 2f, 3f);
        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, candidate);
        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 0);

        Application previousApplication = Gdx.app;
        int[] rejectionFeedback = {0};
        Gdx.app = countingApplication(rejectionFeedback);
        try {
            history.execute(command);
        } finally {
            Gdx.app = previousApplication;
        }

        Assert.assertFalse(world.getMapper(SpatialBlocksComponent.class).has(layerId));
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertFalse(history.isDirty());
        Assert.assertEquals(1, rejectionFeedback[0]);
    }

    @Test
    public void topologyRejectedExecuteDoesNotEnterHistory() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData existing = block(1, "Existing", 2f, 3f);
        component.blocks.add(existing);
        SpatialBlockData duplicate = block(0, "Duplicate", 2f, 3f);
        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, duplicate);

        history.execute(command);

        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals("Existing", component.blocks.first().name);
        Assert.assertEquals(0, component.revision);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
    }

    @Test
    public void rejectedUndoStaysInUndoUntilAuthoredMapIsValidAgain() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(7, "Before", 2f, 3f);
        component.blocks.add(original);
        SpatialBlockData edited = original.copy();
        edited.name = "After";
        history.execute(new EditSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, 7, original.copy(), edited));
        int revisionAfterExecute = component.revision;
        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 0);

        history.undo();

        Assert.assertEquals("After", component.blocks.first().name);
        Assert.assertEquals(revisionAfterExecute, component.revision);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertEquals("Edit Spatial Wall", history.peekUndoLabel());

        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 1);
        history.undo();
        Assert.assertEquals("Before", component.blocks.first().name);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertTrue(history.canRedo());
    }

    @Test
    public void rejectedRedoStaysRetryableWithoutRevisionOrModelChange() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlockData candidate = block(0, "Candidate", 2f, 3f);
        occupyLinkedTiles(world, layerId, candidate);
        history.execute(new AddSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, candidate));
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).get(layerId);
        history.undo();
        int revisionAfterUndo = component.revision;
        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 0);

        history.redo();

        Assert.assertEquals(0, component.blocks.size);
        Assert.assertEquals(revisionAfterUndo, component.revision);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertTrue(history.canRedo());
        Assert.assertEquals("Add Spatial Wall", history.peekRedoLabel());

        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 1);
        history.redo();
        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(revisionAfterUndo + 1, component.revision);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(history.canRedo());
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

    private static Application countingApplication(int[] errorCount) {
        return (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> {
                    if ("error".equals(method.getName())) errorCount[0]++;
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == float.class) return 0f;
                    return null;
                });
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

    private static SelectionService selectionService(World world, HistoryManager history) {
        LayerService layers = new LayerService(
                world,
                new TiledAllocatorService(),
                history.historyIds(),
                identityRegistry(world));
        return new SelectionService(world, layers);
    }

    private static IdentityRegistry identityRegistry(World world) {
        IdentityRegistry registry = new IdentityRegistry();
        registry.bind(world, new SceneMetaRuntime());
        return registry;
    }
}
