package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class ChangeZIndexCommandTest {

    @Test
    public void genericCommandChangesTiledMapZWithUndoRedoAndOrderOnlyDirty() {
        World world = new World(new WorldConfiguration().setSystem(new DirtyTrackerSystem(32)));
        HistoryManager history = new HistoryManager(8);
        int map = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(map);
        index.zIndex = 3;
        world.getMapper(TiledLayerComponent.class).create(map);
        long historyId = history.historyIds().ensureForEntity(map);
        ChangeZIndexCommand command = new ChangeZIndexCommand(world, history.historyIds());
        command.addEntry(historyId, 3, 9);

        history.execute(command);
        Assert.assertEquals(9, index.zIndex);
        Assert.assertTrue(world.getSystem(DirtyTrackerSystem.class).isDirty(map, DirtyBits.ORDER));
        Assert.assertFalse(world.getSystem(DirtyTrackerSystem.class).isDirty(
                map, DirtyBits.GEOMETRY | DirtyBits.MATERIAL | DirtyBits.PHYSICS));

        history.undo();
        Assert.assertEquals(3, index.zIndex);
        history.redo();
        Assert.assertEquals(9, index.zIndex);
        world.dispose();
    }

    @Test
    public void acceptsExactZBoundaries() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int map = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(map);
        world.getMapper(TiledLayerComponent.class).create(map);
        long historyId = history.historyIds().ensureForEntity(map);
        ChangeZIndexCommand command = new ChangeZIndexCommand(world, history.historyIds());
        command.addEntry(historyId, SortKey64.MIN_Z, SortKey64.MAX_Z);
        index.zIndex = SortKey64.MIN_Z;

        history.execute(command);
        Assert.assertEquals(SortKey64.MAX_Z, index.zIndex);
        history.undo();
        Assert.assertEquals(SortKey64.MIN_Z, index.zIndex);
        world.dispose();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZBelowSupportedRange() {
        new ChangeZIndexCommand(new World(new WorldConfiguration()), new HistoryManager(1).historyIds())
                .addEntry(1L, 0, SortKey64.MIN_Z - 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZAboveSupportedRange() {
        new ChangeZIndexCommand(new World(new WorldConfiguration()), new HistoryManager(1).historyIds())
                .addEntry(1L, 0, SortKey64.MAX_Z + 1);
    }
}
