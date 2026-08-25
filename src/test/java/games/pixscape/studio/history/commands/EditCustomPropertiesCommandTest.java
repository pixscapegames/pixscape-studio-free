package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EditCustomPropertiesCommandTest {

    @Test
    public void addThenUndoRemovesAnInitiallyAbsentComponent() {
        World world = new World(new WorldConfiguration());
        try {
            int entityId = world.create();
            HistoryManager history = new HistoryManager(10);
            HistoryIdRegistry ids = history.historyIds();
            long historyId = ids.ensureForEntity(entityId);
            EditCustomPropertiesCommand command = new EditCustomPropertiesCommand(
                    world, ids, historyId, false, new PropertySet(),
                    new PropertySet().putInt("health", 100), 0, null);

            history.execute(command);
            Assert.assertEquals(100, properties(world, entityId).getInt("health", 0));
            history.undo();
            Assert.assertFalse(world.getMapper(CustomPropertiesComponent.class).has(entityId));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void editUndoRedoAndDeleteLastPropertyPreserveCompleteSnapshots() {
        World world = new World(new WorldConfiguration());
        try {
            int entityId = world.create();
            CustomPropertiesComponent component = world.getMapper(CustomPropertiesComponent.class).create(entityId);
            component.properties.putClass("attack", "Attack", new PropertySet().putInt("damage", 20));
            HistoryManager history = new HistoryManager(10);
            HistoryIdRegistry ids = history.historyIds();
            long historyId = ids.ensureForEntity(entityId);

            PropertySet before = component.properties.copy();
            PropertySet after = new PropertySet().putClass(
                    "attack", "Attack", new PropertySet().putInt("damage", 30));
            EditCustomPropertiesCommand edit = new EditCustomPropertiesCommand(
                    world, ids, historyId, true, before, after, 0, null);
            after.putClass("attack", "Attack", new PropertySet().putInt("damage", 99));

            history.execute(edit);
            Assert.assertEquals(30, properties(world, entityId).getClassValue("attack")
                    .properties().getInt("damage", 0));
            history.undo();
            Assert.assertEquals(20, properties(world, entityId).getClassValue("attack")
                    .properties().getInt("damage", 0));
            history.redo();
            Assert.assertEquals(30, properties(world, entityId).getClassValue("attack")
                    .properties().getInt("damage", 0));

            EditCustomPropertiesCommand delete = new EditCustomPropertiesCommand(
                    world, ids, historyId, true, properties(world, entityId), new PropertySet(), 0, null);
            history.execute(delete);
            Assert.assertFalse(world.getMapper(CustomPropertiesComponent.class).has(entityId));
            history.undo();
            Assert.assertEquals(30, properties(world, entityId).getClassValue("attack")
                    .properties().getInt("damage", 0));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void oneCommandIsOneHistoryOperationAndSnapshotsDoNotAlias() {
        World world = new World(new WorldConfiguration());
        try {
            int entityId = world.create();
            HistoryManager history = new HistoryManager(10);
            HistoryIdRegistry ids = history.historyIds();
            long historyId = ids.ensureForEntity(entityId);
            PropertySet after = new PropertySet()
                    .putString("title", "Boss")
                    .putBoolean("aggressive", true)
                    .putInt("health", 100)
                    .putFloat("speed", 1.5f)
                    .putColorRgba8888("tint", 0x01020304);
            EditCustomPropertiesCommand command = new EditCustomPropertiesCommand(
                    world, ids, historyId, false, new PropertySet(), after, 0, null);
            after.putInt("health", 999);

            history.execute(command);
            Assert.assertEquals(1, history.getCursor());
            Assert.assertEquals(100, properties(world, entityId).getInt("health", 0));
            Assert.assertEquals(0x01020304,
                    properties(world, entityId).getColorRgba8888("tint", 0));
        } finally {
            world.dispose();
        }
    }

    private static PropertySet properties(World world, int entityId) {
        return world.getMapper(CustomPropertiesComponent.class).get(entityId).properties;
    }
}
