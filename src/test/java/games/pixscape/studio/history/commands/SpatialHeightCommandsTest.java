package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class SpatialHeightCommandsTest {
    @Test
    public void toggleSpatialHeight_addsRemovesAndRestoresComponent() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        ToggleSpatialHeightCommand command = new ToggleSpatialHeightCommand(
                world,
                history.historyIds(),
                entityId,
                true,
                0f,
                42f
        );

        history.execute(command);
        SpatialHeightComponent component = world.getMapper(SpatialHeightComponent.class).get(entityId);
        Assert.assertEquals(0f, component.altitude, 0.0001f);
        Assert.assertEquals(42f, component.height, 0.0001f);

        history.undo();
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(entityId));

        history.redo();
        component = world.getMapper(SpatialHeightComponent.class).get(entityId);
        Assert.assertEquals(0f, component.altitude, 0.0001f);
        Assert.assertEquals(42f, component.height, 0.0001f);
    }

    @Test
    public void editSpatialHeight_updatesAltitudeAndHeightWithUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        SpatialHeightComponent component = world.getMapper(SpatialHeightComponent.class).create(entityId);
        component.altitude = 1.5f;
        component.height = 8f;

        EditSpatialHeightCommand.Snapshot before = EditSpatialHeightCommand.Snapshot.capture(component);
        EditSpatialHeightCommand.Snapshot after = before
                .withAltitude(3.25f)
                .withHeight(12.5f);

        history.execute(new EditSpatialHeightCommand(
                world,
                history.historyIds(),
                entityId,
                before,
                after
        ));

        Assert.assertEquals(3.25f, component.altitude, 0.0001f);
        Assert.assertEquals(12.5f, component.height, 0.0001f);

        history.undo();
        Assert.assertEquals(1.5f, component.altitude, 0.0001f);
        Assert.assertEquals(8f, component.height, 0.0001f);

        history.redo();
        Assert.assertEquals(3.25f, component.altitude, 0.0001f);
        Assert.assertEquals(12.5f, component.height, 0.0001f);
    }

    @Test
    public void toggleSpatialHeight_disableRemovesExistingComponentAndUndoRestoresValues() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        SpatialHeightComponent component = world.getMapper(SpatialHeightComponent.class).create(entityId);
        component.altitude = -2f;
        component.height = 6f;

        ToggleSpatialHeightCommand command = new ToggleSpatialHeightCommand(
                world,
                history.historyIds(),
                entityId,
                false,
                0f,
                0f
        );

        history.execute(command);
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(entityId));

        history.undo();
        component = world.getMapper(SpatialHeightComponent.class).get(entityId);
        Assert.assertEquals(-2f, component.altitude, 0.0001f);
        Assert.assertEquals(6f, component.height, 0.0001f);
    }
}