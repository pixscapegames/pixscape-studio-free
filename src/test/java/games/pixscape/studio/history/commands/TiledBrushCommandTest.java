package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.tiled.TiledBrushSession;
import games.pixscape.studio.service.tiled.TiledMutationPlan;
import games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner;
import org.junit.Assert;
import org.junit.Test;

public class TiledBrushCommandTest {
    @Test
    public void executeUndoRedoApplyCompleteSnapshotWithOneRevisionEach() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4);
        tiled.data.setTile(1, 1, 3, TileTransformFlags.FLIP_V);
        int initialRevision = tiled.data.contentRevision();
        TiledBrushSession session = new TiledBrushSession(layer);
        session.apply(tiled, 1, 1, 8, TileTransformFlags.FLIP_H);
        session.apply(tiled, 2, 1, 9);
        TiledMutationPlan plan = session.toPlan();
        HistoryManager history = new HistoryManager(16);
        long historyId = history.historyIds().ensureForEntity(layer);
        TiledBrushCommand command = new TiledBrushCommand(world, null, history.historyIds(),
                historyId, plan, new TiledSpatialMutationPlanner());

        history.execute(command);
        Assert.assertEquals(initialRevision + 1, tiled.data.contentRevision());
        Assert.assertEquals(8, tiled.data.getTile(1, 1));
        Assert.assertEquals(9, tiled.data.getTile(2, 1));
        Assert.assertEquals(1, history.getCursor());

        history.undo();
        Assert.assertEquals(initialRevision + 2, tiled.data.contentRevision());
        Assert.assertEquals(3, tiled.data.getTile(1, 1));
        Assert.assertEquals(TileTransformFlags.FLIP_V, tiled.data.getTileTransformFlags(1, 1));
        Assert.assertEquals(0, tiled.data.getTile(2, 1));
        Assert.assertEquals(0, history.getCursor());

        history.redo();
        Assert.assertEquals(initialRevision + 3, tiled.data.contentRevision());
        Assert.assertEquals(8, tiled.data.getTile(1, 1));
        Assert.assertEquals(9, tiled.data.getTile(2, 1));
        Assert.assertEquals(1, history.getCursor());
    }

    @Test
    public void rejectedRedoLeavesHistoryCursorAndMapUnchanged() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4);
        tiled.data.setTile(2, 2, 3);
        TiledBrushSession session = new TiledBrushSession(layer);
        session.apply(tiled, 2, 2, 0);
        HistoryManager history = new HistoryManager(16);
        TiledBrushCommand command = new TiledBrushCommand(world, null, history.historyIds(),
                history.historyIds().ensureForEntity(layer), session.toPlan(),
                new TiledSpatialMutationPlanner());
        history.execute(command);
        history.undo();
        int revision = tiled.data.contentRevision();

        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layer);
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 5;
        wall.structureId = 9;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(2, 2, 3);
        blocks.blocks.add(wall);
        try {
            history.redo();
            Assert.fail("Expected linked-anchor redo rejection.");
        } catch (games.pixscape.studio.service.tiled.TiledMutationRejectedException expected) {
            Assert.assertEquals(0, history.getCursor());
            Assert.assertEquals(3, tiled.data.getTile(2, 2));
            Assert.assertEquals(revision, tiled.data.contentRevision());
            Assert.assertTrue(history.canRedo());
        }
    }
}
