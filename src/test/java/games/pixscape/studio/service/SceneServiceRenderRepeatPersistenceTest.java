package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditRenderRepeatCommand;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class SceneServiceRenderRepeatPersistenceTest {
    @Test
    public void saveAndLoad_preservesRenderRepeatComponent() {
        World world = worldWithSerialization();
        int entityId = world.create();
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entityId);
        repeat.repeatX = true;
        repeat.repeatY = false;
        world.process();

        FileHandle sceneFile = new FileHandle(new File(
                "build/tmp/scene-service-render-repeat-persistence/" + System.nanoTime() + "/scene.json"
        ));
        sceneFile.parent().mkdirs();

        SceneService.saveScene(world, sceneFile, false);
        Assert.assertTrue(sceneFile.exists());
        String json = sceneFile.readString(StandardCharsets.UTF_8.name());
        Assert.assertTrue(json.contains("RenderRepeatComponent"));
        Assert.assertTrue(json.contains("repeatX"));

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, sceneFile, false);
        loaded.process();

        IntBag repeated = loaded.getAspectSubscriptionManager()
                .get(Aspect.all(RenderRepeatComponent.class))
                .getEntities();
        Assert.assertEquals(1, repeated.size());

        int loadedEntity = repeated.get(0);
        RenderRepeatComponent loadedRepeat =
                loaded.getMapper(RenderRepeatComponent.class).get(loadedEntity);
        Assert.assertTrue(loadedRepeat.repeatX);
        Assert.assertFalse(loadedRepeat.repeatY);
    }

    @Test
    public void saveAndLoad_preservesRepeatStateNormalizedByEditorCommand() {
        World world = worldWithSerialization();
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.rotationRad = 0.8f;

        history.execute(new EditRenderRepeatCommand(
                world,
                history.historyIds(),
                entityId,
                EditRenderRepeatCommand.Snapshot.disabled(),
                new EditRenderRepeatCommand.Snapshot(true, false)
        ));
        Assert.assertEquals(0f, transform.rotationRad, 0.0001f);
        Assert.assertTrue(world.getMapper(RenderRepeatComponent.class).has(entityId));
        world.process();

        FileHandle sceneFile = new FileHandle(new File(
                "build/tmp/scene-service-render-repeat-normalized-persistence/" + System.nanoTime() + "/scene.json"
        ));
        sceneFile.parent().mkdirs();

        SceneService.saveScene(world, sceneFile, false);

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, sceneFile, false);
        loaded.process();

        IntBag repeated = loaded.getAspectSubscriptionManager()
                .get(Aspect.all(RenderRepeatComponent.class))
                .getEntities();
        Assert.assertEquals(1, repeated.size());

        int loadedEntity = repeated.get(0);
        TransformComponent loadedTransform = loaded.getMapper(TransformComponent.class).get(loadedEntity);
        RenderRepeatComponent loadedRepeat = loaded.getMapper(RenderRepeatComponent.class).get(loadedEntity);
        Assert.assertEquals(0f, loadedTransform.rotationRad, 0.0001f);
        Assert.assertTrue(loadedRepeat.repeatX);
        Assert.assertFalse(loadedRepeat.repeatY);
    }

    private static World worldWithSerialization() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }
}
