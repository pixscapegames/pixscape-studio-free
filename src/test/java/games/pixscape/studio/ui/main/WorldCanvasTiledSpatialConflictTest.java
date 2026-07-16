package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.tiled.TiledBrushSession;
import games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner;
import games.pixscape.studio.service.tiled.TiledSpatialMutationRejection;
import org.junit.Assert;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class WorldCanvasTiledSpatialConflictTest {
    @Test
    public void linkedConflictUsesAuthoringBoundaryOnceAndLeavesCanvasUsable() throws Exception {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4);
        tiled.data.setTile(2, 2, 1);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layer);
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 12;
        wall.structureId = 4;
        wall.x = 2;
        wall.y = 2;
        wall.width = 1;
        wall.depth = 1;
        wall.height = 16;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(2, 2, 1);
        blocks.blocks.add(wall);
        HistoryManager history = new HistoryManager(16);
        RecordingCanvas canvas = canvas(world, history);
        int revision = tiled.data.contentRevision();

        TiledBrushSession rejected = new TiledBrushSession(layer);
        rejected.apply(tiled, 2, 2, 0);
        Assert.assertFalse(canvas.commitTiledBrushSession(rejected));

        Assert.assertEquals(1, canvas.authoringRejections);
        Assert.assertEquals(0, canvas.genericInvariantFailures);
        Assert.assertEquals(1, tiled.data.getTile(2, 2));
        Assert.assertEquals(revision, tiled.data.contentRevision());
        Assert.assertEquals(0, history.getCursor());
        Assert.assertEquals(12, canvas.rejection.firstBlockId());

        TiledBrushSession unrelated = new TiledBrushSession(layer);
        unrelated.apply(tiled, 0, 0, 9);
        Assert.assertTrue(canvas.commitTiledBrushSession(unrelated));
        Assert.assertEquals(9, tiled.data.getTile(0, 0));
        Assert.assertEquals(1, history.getCursor());
    }

    private static RecordingCanvas canvas(World world, HistoryManager history) throws Exception {
        Unsafe unsafe = unsafe();
        RecordingCanvas canvas = (RecordingCanvas) unsafe.allocateInstance(RecordingCanvas.class);
        set(unsafe, canvas, "world", world);
        set(unsafe, canvas, "historyManager", history);
        set(unsafe, canvas, "tiledSpatialMutationPlanner", new TiledSpatialMutationPlanner());
        return canvas;
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void set(Unsafe unsafe, Object target, String name, Object value) throws Exception {
        Field field = WorldCanvas.class.getDeclaredField(name);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }

    private static final class RecordingCanvas extends WorldCanvas {
        int authoringRejections;
        int genericInvariantFailures;
        TiledSpatialMutationRejection rejection;

        private RecordingCanvas() { super(null, null); }

        @Override
        void showTiledSpatialRejection(int layerEntityId, TiledSpatialMutationRejection rejection) {
            authoringRejections++;
            this.rejection = rejection;
        }

        @Override
        public void onSpatialInvariantFailure(RuntimeException failure) {
            genericInvariantFailures++;
        }
    }
}
