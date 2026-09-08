package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.service.SceneService;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GameObjectScenePersistenceTest {
    @Test
    public void schemaThreeRoundTripPreservesRootMembershipAndLocalAuthoredState() throws Exception {
        Path path = Files.createTempFile("game-object-scene", ".json");
        FileHandle file = new FileHandle(path.toFile());
        World authored = world();
        World loaded = world();
        try {
            int root = entity(authored, 1, 3, 9, 10f, 20f);
            authored.getMapper(GameObjectComponent.class).create(root).sourceAssetId = "";
            int child = entity(authored, 2, 8, -4, 2f, 5f);
            authored.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 1;
            authored.process();

            SceneService.saveScene(authored, file, false);
            String json = file.readString("UTF-8");
            assertTrue(json.contains("GameObjectComponent"));
            assertTrue(json.contains("GameObjectMemberComponent"));

            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 3;
            SceneLoader.loadScene(loaded, file, false, meta);
            loaded.process();

            int restoredRoot = loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(GameObjectComponent.class)).getEntities().get(0);
            int restoredChild = loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(GameObjectMemberComponent.class)).getEntities().get(0);
            assertEquals(1, loaded.getMapper(PixscapeIdentityComponent.class)
                    .get(restoredRoot).stableId);
            assertEquals(10f, loaded.getMapper(TransformComponent.class).get(restoredRoot).x, 0f);
            assertEquals(1, loaded.getMapper(GameObjectMemberComponent.class)
                    .get(restoredChild).parentStableId);
            assertEquals(2f, loaded.getMapper(TransformComponent.class).get(restoredChild).x, 0f);
            assertEquals(5f, loaded.getMapper(TransformComponent.class).get(restoredChild).y, 0f);
            assertEquals(-4, loaded.getMapper(EntityIndexComponent.class).get(restoredChild).zIndex);
        } finally {
            authored.dispose();
            loaded.dispose();
            Files.deleteIfExists(path);
        }
    }

    private static int entity(World world, int stableId, int layer, int z, float x, float y) {
        int entity = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layer;
        index.zIndex = z;
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = x;
        transform.y = y;
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        transform.refreshCaches();
        return entity;
    }

    private static World world() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }
}
