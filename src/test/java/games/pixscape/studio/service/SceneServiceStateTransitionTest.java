package games.pixscape.studio.service;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.badlogic.gdx.files.FileHandle;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SceneServiceStateTransitionTest {

    @Test
    public void loadOpenContext_currentSceneNotPresentInMetadata_throws() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-missing-current-scene-meta");
        FileHandle projectFile = writeProject(dir, "{" +
                "\"projectKind\":\"pixscape-studio-project\"," +
                "\"projectTitle\":\"Test\"," +
                "\"projectFileName\":\"test\"," +
                "\"version\":\"1\"," +
                "\"exportRootPathDir\":\"/tmp/export\"," +
                "\"glProfile\":\"GL30\"," +
                "\"glSamples\":0," +
                "\"currentSceneName\":\"Ghost\"," +
                "\"nextSceneIndex\":2," +
                "\"scenes\":{\"Main\":{\"name\":\"Main\",\"file\":\"scene1.json\","
                        + "\"nextEntityStableId\":1,\"nextPhysicsShapeId\":1}}" +
                "}");

        Files.createDirectories(dir.resolve("scenes"));
        Files.writeString(dir.resolve("scenes/scene1.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("assets.json"), "{}", StandardCharsets.UTF_8);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                SceneService.loadOpenContextOrThrow(projectFile));

        assertTrue(ex.getMessage().contains("is not declared in scenes map"));
    }

    @Test
    public void rollbackSceneSwitchConfigPointer_withInvalidPrevious_doesNotMutateCurrentScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");

        boolean restored = SceneService.rollbackSceneSwitchConfigPointer(cfg, " ");

        assertFalse(restored);
        assertEquals("Main", cfg.getCurrentSceneName());
    }

    @Test
    public void activationValidationRejectsInvalidSpatialV3WallWithoutMutatingWorld() {
        World world = new World(new WorldConfiguration());
        int layerId = tiledLayer(world);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        blocks.blocks.add(actorBlockWithoutRefs());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> ResolvedSceneActivationPipeline.validateSpatialBlocksForActivation(world, "Invalid")
        );

        assertTrue(ex.getMessage().contains("Scene activation was rejected"));
        assertTrue(ex.getMessage().contains("INVALID_STRUCTURE_ID"));
        assertTrue(world.getMapper(SpatialBlocksComponent.class).has(layerId));
        assertEquals(1, world.getMapper(SpatialBlocksComponent.class).get(layerId).blocks.size);
    }

    @Test
    public void activationValidationKeepsValidSpatialV3Wall() {
        World world = new World(new WorldConfiguration());
        int layerId = tiledLayer(world);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        tiled.data.setTile(2, 3, 101);
        SpatialBlockData block = actorBlockWithoutRefs();
        block.structureId = 4;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 3, 101);

        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        blocks.blocks.add(block);

        ResolvedSceneActivationPipeline.validateSpatialBlocksForActivation(world, "Valid");
        assertTrue(world.getMapper(SpatialBlocksComponent.class).has(layerId));
        assertEquals(1, world.getMapper(SpatialBlocksComponent.class).get(layerId).blocks.size);
    }

    private static FileHandle writeProject(Path dir, String json) throws Exception {
        Files.writeString(dir.resolve("project.json"), json, StandardCharsets.UTF_8);
        return new FileHandle(dir.resolve("project.json").toFile());
    }

    private static int tiledLayer(World world) {
        int layerId = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.data = new TiledMapLayerData(
                8,
                8,
                16,
                16,
                8,
                SceneMetaRuntime.TiledProjection.ORTHO
        );
        return layerId;
    }

    private static SpatialBlockData actorBlockWithoutRefs() {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 9;
        block.x = 2f;
        block.y = 3f;
        block.width = 1f;
        block.depth = 1f;
        block.height = 8f;
        block.actorOccluder = true;
        return block;
    }
}
