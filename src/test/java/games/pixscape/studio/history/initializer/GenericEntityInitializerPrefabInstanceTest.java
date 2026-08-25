package games.pixscape.studio.history.initializer;

import com.artemis.World;
import games.pixscape.studio.component.PrefabInstanceComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class GenericEntityInitializerPrefabInstanceTest {
    @Test
    public void syncDuplicateAndInitPreservePrefabMembershipWhileClearStripsIt() {
        World world = new World();
        try {
            int source = world.create();
            PrefabInstanceComponent sourcePrefab =
                    world.getMapper(PrefabInstanceComponent.class).create(source);
            sourcePrefab.instanceId = 17;
            sourcePrefab.prefabId = "Castle";

            GenericEntityInitializer captured = new GenericEntityInitializer(world);
            captured.syncFrom(source);
            GenericEntityInitializer duplicate = captured.duplicate();

            int restored = world.create();
            duplicate.init(restored);
            PrefabInstanceComponent restoredPrefab =
                    world.getMapper(PrefabInstanceComponent.class).getSafe(restored, null);
            assertNotNull(restoredPrefab);
            assertEquals(17, restoredPrefab.instanceId);
            assertEquals("Castle", restoredPrefab.prefabId);

            int flattened = world.create();
            duplicate.clearPrefabInstance().init(flattened);
            assertFalse(world.getMapper(PrefabInstanceComponent.class).has(flattened));
        } finally {
            world.dispose();
        }
    }
}
