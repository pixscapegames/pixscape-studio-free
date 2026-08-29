package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.service.SceneService;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpatialActorLayerPersistenceTest {

    @Test
    public void spatialEnabledOrdinaryLayerSurvivesSaveAndLoad() throws Exception {
        Path path = Files.createTempFile("spatial-actor-layer", ".json");
        FileHandle sceneFile = new FileHandle(path.toFile());
        World authored = serializationWorld();
        World loaded = serializationWorld();
        try {
            int entity = authored.create();
            authored.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 1;
            LayerComponent layer = authored.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = 0;
            layer.spatialEnabled = true;
            authored.getMapper(LayerMetaComponent.class).create(entity).name = "Spatial";
            authored.process();

            SceneService.saveScene(authored, sceneFile, false);
            SceneMetaRuntime sceneMeta = new SceneMetaRuntime();
            sceneMeta.nextEntityStableId = 2;
            SceneLoader.loadScene(loaded, sceneFile, true, sceneMeta);
            loaded.process();

            int layerEntity = loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(LayerComponent.class)).getEntities().get(0);
            LayerComponent restored = loaded.getMapper(LayerComponent.class).get(layerEntity);
            assertTrue(restored.spatialEnabled);
        } finally {
            authored.dispose();
            loaded.dispose();
            Files.deleteIfExists(path);
        }
    }

    private static World serializationWorld() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }
}
