package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.service.SceneService;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrefabInstanceScenePersistenceTest {
    @Test
    public void studioSceneRoundTripPreservesPrefabMembership() throws Exception {
        Path path = Files.createTempFile("prefab-instance-scene", ".json");
        FileHandle file = new FileHandle(path.toFile());
        World authored = world();
        World loaded = world();
        try {
            int entity = authored.create();
            authored.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 1;
            PrefabInstanceComponent prefab =
                    authored.getMapper(PrefabInstanceComponent.class).create(entity);
            prefab.instanceId = 27;
            prefab.prefabId = "Castle";
            authored.process();

            SceneService.saveScene(authored, file, false);
            assertTrue(file.readString("UTF-8").contains("PrefabInstanceComponent"));
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 2;
            SceneLoader.loadScene(loaded, file, false, meta);
            loaded.process();

            int restoredEntity = loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(PrefabInstanceComponent.class)).getEntities().get(0);
            PrefabInstanceComponent restored =
                    loaded.getMapper(PrefabInstanceComponent.class).get(restoredEntity);
            assertEquals(27, restored.instanceId);
            assertEquals("Castle", restored.prefabId);
        } finally {
            authored.dispose();
            loaded.dispose();
            Files.deleteIfExists(path);
        }
    }

    private static World world() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }
}
