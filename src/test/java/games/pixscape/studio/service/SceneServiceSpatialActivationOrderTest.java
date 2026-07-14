package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class SceneServiceSpatialActivationOrderTest {

    @Test
    public void freshActivation_resolvesOwningMapBeforeSpatialValidationAndCompilation() throws Exception {
        Fixture fixture = validFixture("fresh-activation");
        String authoredBefore = fixture.sceneFile.readString("UTF-8");

        World loaded = serializationWorld();
        SceneLoader.loadScene(loaded, fixture.sceneFile, false);
        loaded.process();
        int layer = loaded.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(TiledLayerComponent.class))
                .getEntities().get(0);
        assertNull(loaded.getMapper(TiledLayerComponent.class).get(layer).data);

        SceneService.resolveTiledLayersForActivation(
                loaded, fixture.meta, null, null, fixture.cfg.projectTitle, "Main");
        assertNotNull(loaded.getMapper(TiledLayerComponent.class).get(layer).data);
        assertEquals(101, loaded.getMapper(TiledLayerComponent.class).get(layer).data.getTile(2, 3));

        SceneService.validateAndCompileSpatialBlocksForActivation(
                loaded, fixture.cfg.projectTitle, "Main");
        assertEquals(authoredBefore, fixture.sceneFile.readString("UTF-8"));
        loaded.dispose();
    }

    @Test
    public void detachedPreparation_succeedsOnFirstAndRepeatedAttemptWithoutCachedState() throws Exception {
        Fixture fixture = validFixture("repeat-activation");
        String authoredBefore = fixture.sceneFile.readString("UTF-8");

        SceneService.PreparedSceneActivation first = SceneService.prepareSceneActivation(
                fixture.cfg, "Main", fixture.projectDir);
        SceneService.PreparedSceneActivation second = SceneService.prepareSceneActivation(
                fixture.cfg, "Main", fixture.projectDir);

        assertEquals(first.sceneFile().path(), second.sceneFile().path());
        assertEquals(authoredBefore, fixture.sceneFile.readString("UTF-8"));
    }

    @Test
    public void detachedPreparation_genuinelyMissingOwningMapFailsDeterministically() throws Exception {
        Fixture fixture = fixture("missing-owner");
        World authored = serializationWorld();
        int layer = authored.create();
        authored.getMapper(SpatialBlocksComponent.class).create(layer).blocks.add(validWall());
        authored.process();
        SceneService.saveScene(authored, fixture.sceneFile, false);
        authored.dispose();
        String authoredBefore = fixture.sceneFile.readString("UTF-8");

        RuntimeException first = assertThrows(RuntimeException.class, () ->
                SceneService.prepareSceneActivation(fixture.cfg, "Main", fixture.projectDir));
        RuntimeException second = assertThrows(RuntimeException.class, () ->
                SceneService.prepareSceneActivation(fixture.cfg, "Main", fixture.projectDir));

        assertTrue(first.getMessage().contains("Project 'Spatial activation test'"));
        assertTrue(first.getMessage().contains("scene 'Main'"));
        assertTrue(first.getMessage().contains("no owning TiledLayerComponent map data"));
        assertEquals(first.getMessage(), second.getMessage());
        assertEquals(authoredBefore, fixture.sceneFile.readString("UTF-8"));
    }

    @Test
    public void detachedPreparation_invalidStructureIdRejectsAfterMapResolutionWithoutDoublePunctuation()
            throws Exception {
        Fixture fixture = validFixture("invalid-structure", false);
        String authoredBefore = fixture.sceneFile.readString("UTF-8");

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                SceneService.prepareSceneActivation(fixture.cfg, "Main", fixture.projectDir));

        assertTrue(failure.getMessage().contains("INVALID_STRUCTURE_ID"));
        assertFalse(failure.getMessage().contains("MISSING_MAP"));
        assertFalse(failure.getMessage().contains(".."));
        assertEquals(authoredBefore, fixture.sceneFile.readString("UTF-8"));
    }

    private static Fixture validFixture(String name) throws Exception {
        return validFixture(name, true);
    }

    private static Fixture validFixture(String name, boolean validStructureId) throws Exception {
        Fixture fixture = fixture(name);
        World authored = serializationWorld();
        int layer = authored.create();
        TiledLayerComponent tiled = authored.getMapper(TiledLayerComponent.class).create(layer);
        tiled.mapWidthCells = 8;
        tiled.mapHeightCells = 8;
        tiled.tileXs.add(2);
        tiled.tileYs.add(3);
        tiled.tileAssetIds.add(101);
        tiled.tileTransformFlags.add((byte) 0);
        SpatialBlockData wall = validWall();
        if (!validStructureId) wall.structureId = 0;
        authored.getMapper(SpatialBlocksComponent.class).create(layer).blocks.add(wall);
        authored.process();
        SceneService.saveScene(authored, fixture.sceneFile, false);
        authored.dispose();
        return fixture;
    }

    private static Fixture fixture(String name) throws Exception {
        Path path = Files.createTempDirectory("scene-service-spatial-" + name);
        FileHandle projectDir = new FileHandle(path.toFile());
        FileHandle scenesDir = projectDir.child(StudioFs.DIR_SCENES);
        scenesDir.mkdirs();

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Spatial activation test";
        cfg.projectFileName = "spatial-activation-test";
        cfg.exportRootPathDir = path.resolve("export").toString();
        cfg.createSceneMeta("Main");
        SceneMeta meta = cfg.getSceneMeta("Main");
        meta.tileWidth = 16;
        meta.tileHeight = 16;
        meta.chunkSize = 4;
        meta.tiledProjection = SceneMetaRuntime.TiledProjection.ORTHO;
        FileHandle sceneFile = scenesDir.child(meta.getFile());
        return new Fixture(cfg, meta, projectDir, sceneFile);
    }

    private static SpatialBlockData validWall() {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 2;
        wall.structureId = 7;
        wall.x = 2f;
        wall.y = 3f;
        wall.width = 1f;
        wall.depth = 1f;
        wall.height = 8f;
        wall.actorOccluder = true;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(2, 3, 101);
        return wall;
    }

    private static World serializationWorld() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }

    private record Fixture(ProjectConfig cfg,
                           SceneMeta meta,
                           FileHandle projectDir,
                           FileHandle sceneFile) {
    }
}
