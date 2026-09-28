package games.pixscape.studio.configuration;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.SceneService;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContentLayerBoundaryTest {
    @Test
    public void invalidContentDoesNotReplaceSavedScene() throws Exception {
        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager()).build());
        int content = world.create();
        world.getMapper(EntityIndexComponent.class).create(content).layerIndex = 1;
        world.getMapper(LayerComponent.class).create(content).layerIndex = 1;
        world.process();
        Path scene = Files.createTempFile("content-layer-save", ".json");
        Files.writeString(scene, "previous", StandardCharsets.UTF_8);

        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                () -> SceneService.saveScene(world, new FileHandle(scene.toFile()), false));
        Assert.assertTrue(failure.getMessage().contains("entityId=" + content));
        Assert.assertEquals("previous", Files.readString(scene));
        world.dispose();
    }

    @Test
    public void invalidSceneDoesNotReplacePreviousRuntimeExport() throws Exception {
        Path studio = Files.createTempDirectory("content-layer-studio");
        Path destination = Files.createTempDirectory("content-layer-export");
        ProjectConfig config = new ProjectConfig();
        config.projectFileName = "content-layer";
        config.createSceneMeta("Main");
        Path scenes = Files.createDirectories(studio.resolve(StudioFs.DIR_SCENES));
        Files.writeString(scenes.resolve("scene1.json"),
                "{\"entities\":{\"42\":{\"components\":{\"LayerComponent\":{},"
                        + "\"EntityIndexComponent\":{}}}}}", StandardCharsets.UTF_8);
        Path previous = Files.createDirectories(destination.resolve(RuntimeExport.RUNTIME_DIR_NAME))
                .resolve("existing.txt");
        Files.writeString(previous, "previous", StandardCharsets.UTF_8);

        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                () -> RuntimeExport.exportRuntime(config, new FileHandle(studio.toFile()),
                        new FileHandle(destination.toFile())));
        Assert.assertTrue(failure.getMessage().contains("scene1.json"));
        Assert.assertTrue(failure.getMessage().contains("entity=42"));
        Assert.assertEquals("previous", Files.readString(previous));
    }
}
