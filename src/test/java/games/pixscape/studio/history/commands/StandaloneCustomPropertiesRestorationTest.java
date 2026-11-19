package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Test;

import static org.junit.Assert.*;

public class StandaloneCustomPropertiesRestorationTest {
    @Test
    public void standaloneDeletionUndoRestoresCustomProperties() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            world.getMapper(TransformComponent.class).create(entity);
            world.getMapper(EntityIndexComponent.class).create(entity);
            world.getMapper(CustomPropertiesComponent.class).create(entity).properties
                    .putInt("health", 42);
            world.process();
            HistoryManager history = new HistoryManager(20);
            long historyId = history.historyIds().ensureForEntity(entity);
            Command deletion = DeleteEntitiesCommandFactory.create(world, history.historyIds(),
                    new IntArray(new int[]{entity}), null);
            assertTrue(deletion instanceof DeleteEntitiesCommand);

            history.execute(deletion);
            world.process();
            assertFalse(world.getEntityManager().isActive(entity));
            history.undo();
            world.process();

            int restored = history.historyIds().entityOfHistoryId(historyId);
            assertTrue("Undo must restore CustomPropertiesComponent",
                    world.getMapper(CustomPropertiesComponent.class).has(restored));
            assertEquals(42, world.getMapper(CustomPropertiesComponent.class)
                    .get(restored).properties.getInt("health", -1));
        } finally {
            world.dispose();
        }
    }
    @Test
    public void repeatedUndoPreservesAllTypesIdentityAndSavedData() {
        World world = serializationWorld();
        World loaded = serializationWorld();
        try {
            int entity = world.create();
            world.getMapper(TransformComponent.class).create(entity);
            world.getMapper(EntityIndexComponent.class).create(entity);
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 123;
            PropertySet expected = allTypes();
            world.getMapper(CustomPropertiesComponent.class).create(entity).properties = expected.copy();
            world.process();
            HistoryManager history = new HistoryManager(20);
            long id = history.historyIds().ensureForEntity(entity);
            history.execute(DeleteEntitiesCommandFactory.create(world, history.historyIds(),
                    new IntArray(new int[]{entity}), null));
            world.process();
            for (int cycle = 0; cycle < 3; cycle++) {
                history.undo();
                world.process();
                int restored = history.historyIds().entityOfHistoryId(id);
                assertEquals(123, world.getMapper(PixscapeIdentityComponent.class).get(restored).stableId);
                assertProperties(expected, properties(world, restored));
                if (cycle == 2) {
                    WorldSerializationManager writer = world.getSystem(WorldSerializationManager.class);
                    writer.setSerializer(new JsonArtemisSerializer(world));
                    SaveFileFormat save = new SaveFileFormat();
                    save.entities.add(restored);
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    writer.save(bytes, save);
                    WorldSerializationManager reader = loaded.getSystem(WorldSerializationManager.class);
                    reader.setSerializer(new JsonArtemisSerializer(loaded));
                    SaveFileFormat result = reader.load(new ByteArrayInputStream(bytes.toByteArray()), SaveFileFormat.class);
                    loaded.process();
                    assertProperties(expected, properties(loaded, result.entities.get(0)));
                } else {
                    properties(world, restored).putClass("nested", "Changed", new PropertySet().putInt("x", 99));
                    history.redo();
                    world.process();
                    assertFalse(world.getEntityManager().isActive(restored));
                }
            }
        } finally {
            world.dispose();
            loaded.dispose();
        }
    }

    @Test
    public void absentAndEmptyComponentsRemainDistinctAfterDeletionUndo() {
        World world = new World();
        try {
            for (boolean present : new boolean[]{false, true}) {
                int entity = world.create();
                world.getMapper(TransformComponent.class).create(entity);
                if (present) {
                    world.getMapper(CustomPropertiesComponent.class).create(entity);
                }
                world.process();
                HistoryManager history = new HistoryManager(4);
                long id = history.historyIds().ensureForEntity(entity);
                history.execute(DeleteEntitiesCommandFactory.create(world, history.historyIds(),
                        new IntArray(new int[]{entity}), null));
                world.process();
                history.undo();
                world.process();
                int restored = history.historyIds().entityOfHistoryId(id);
                assertEquals(present, world.getMapper(CustomPropertiesComponent.class).has(restored));
                if (present) {
                    assertTrue(properties(world, restored).isEmpty());
                }
            }
        } finally {
            world.dispose();
        }
    }

    @Test
    public void snapshotTransferAndDuplicationHaveIndependentPropertyValues() {
        World source = new World();
        World destination = new World();
        try {
            int entity = source.create();
            source.getMapper(CustomPropertiesComponent.class).create(entity).properties = allTypes();
            GenericEntityInitializer captured = new GenericEntityInitializer(source);
            captured.syncFrom(entity);
            GenericEntityInitializer duplicate = captured.duplicateFor(destination);
            GenericEntitySnapshotData data = captured.toSnapshotData(-1);
            GenericEntityInitializer transferred = new GenericEntityInitializer(destination).applySnapshotData(data);
            properties(source, entity).putClass("nested", "Source", new PropertySet());
            data.customProperties.putClass("nested", "Data", new PropertySet());
            captured.setCustomProperties(new PropertySet().putInt("different", 1));
            int first = destination.create();
            duplicate.init(first);
            assertProperties(allTypes(), properties(destination, first));
            properties(destination, first).putClass("nested", "Restored", new PropertySet());
            int second = destination.create();
            duplicate.init(second);
            assertProperties(allTypes(), properties(destination, second));
            int third = destination.create();
            transferred.init(third);
            assertProperties(allTypes(), properties(destination, third));

            // Hierarchy/asset remapping replaces raw snapshot references before the single init.
            PropertySet remapped = allTypes().putObjectStableId("self", 456);
            transferred.setCustomProperties(remapped);
            remapped.putObjectStableId("self", 999);
            int mapped = destination.create();
            transferred.init(mapped);
            assertEquals(456, properties(destination, mapped).getObjectStableId("self", -1));
            transferred.setCustomProperties(null);
            int absent = destination.create();
            transferred.init(absent);
            assertFalse(destination.getMapper(CustomPropertiesComponent.class).has(absent));
        } finally {
            source.dispose();
            destination.dispose();
        }
    }

    static PropertySet allTypes() {
        return new PropertySet().putString(" exact Name ", "Text")
                .putBoolean("enabled", true).putInt("health", 42).putFloat("speed", 16777217d)
                .putList("items", com.badlogic.gdx.utils.Array.with(PropertyValue.ofObjectStableId(123),
                        PropertyValue.ofList(com.badlogic.gdx.utils.Array.with(PropertyValue.ofFloat(0.1234567890123456)))))
                .put("mode", PropertyValue.ofEnum(new games.pixscape.runtime.property.EnumDefinition(
                        "Mode", games.pixscape.runtime.property.PropertyType.STRING, false, "A", "B"), "B"))
                .putColorRgba8888("color", 0x12345678).putObjectStableId("self", 123)
                .putObjectStableId("none", -1)
                .putClass("nested", "Outer", new PropertySet().putClass("inner", "Inner",
                        new PropertySet().putObjectStableId("target", 123).putInt("count", 7)))
                .put("imported", PropertyValue.ofImported("file", "Asset", "<property name=\"imported\" type=\"file\" value=\"image.png\"/>"));
    }

    static void assertProperties(PropertySet expected, PropertySet actual) {
        assertTrue("Names, types and complete values must match", EditCustomPropertiesCommand.sameProperties(expected, actual));
        assertNotSame(expected, actual);
    }

    private static PropertySet properties(World world, int entity) {
        return world.getMapper(CustomPropertiesComponent.class).get(entity).properties;
    }

    private static World serializationWorld() {
        return new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
    }

}
