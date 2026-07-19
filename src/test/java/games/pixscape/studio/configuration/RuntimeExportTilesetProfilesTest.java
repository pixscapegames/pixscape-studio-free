package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class RuntimeExportTilesetProfilesTest {
    private static final String TILESET_PROFILES_JSON = "tileset-profiles.json";

    @Test(expected = GdxRuntimeException.class)
    public void exportRuntimeRejectsRuntimeAvailableTileWithoutTilesetProfileMetadata() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-missing-tileset-profile-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-missing-tileset-profile-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Missing Tileset Profile Export";
        cfg.projectFileName = "missing-tileset-profile-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);

        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta notATile = db.registerIfAbsent(
                AssetType.IMAGE,
                "images/not-a-tile",
                "orig/images/not-a-tile.png",
                AssetMeta.AssetScope.USER
        );
        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(notATile.id);

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));
    }

    @Test
    public void exportRuntimeWritesTilesetProfilesForRuntimeAvailableTiles() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-tileset-profiles-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-tileset-profiles-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Tileset Profiles Export";
        cfg.projectFileName = "tileset-profiles-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);

        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta terrain = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        terrain.tileWidth = 64;
        terrain.tileHeight = 96;
        terrain.referenceCellWidth = 32;
        terrain.referenceCellHeight = 16;
        terrain.projection = SceneMetaRuntime.TiledProjection.ISO;
        terrain.anchor = TilesetAnchor.TOP_CENTER;
        terrain.offsetX = 4;
        terrain.offsetY = -8;
        terrain.renderSize = TilesetRenderSize.NATIVE;

        TileAssetMeta grass = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/grass",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        grass.tilesetId = terrain.id;
        grass.sheetIndex = 0;
        grass.cellX = 0;
        grass.cellY = 0;

        TileAssetMeta water = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/water",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        water.tilesetId = terrain.id;
        water.sheetIndex = 1;
        water.cellX = 1;
        water.cellY = 0;

        TilesetAssetMeta props = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/props",
                null,
                AssetMeta.AssetScope.USER
        );
        props.tileWidth = 32;
        props.tileHeight = 32;
        props.referenceCellWidth = 32;
        props.referenceCellHeight = 32;
        props.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        props.anchor = TilesetAnchor.BOTTOM_LEFT;

        TileAssetMeta crate = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/props/crate",
                "orig/tiles/props/crate.png",
                AssetMeta.AssetScope.USER
        );
        crate.tilesetId = props.id;
        crate.sheetIndex = 0;
        crate.cellX = 0;
        crate.cellY = 0;

        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        SceneMeta scene = cfg.getCurrentSceneMeta();
        scene.runtimeAvailability.tiledTileAssetIds.add(water.id);
        scene.runtimeAvailability.tiledTileAssetIds.add(grass.id);
        scene.runtimeAvailability.tiledTileAssetIds.add(crate.id);

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        Path runtimeDir = userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        assertTrue(Files.isRegularFile(runtimeDir.resolve(RuntimeExport.PROJECT_JSON)));
        assertTrue(Files.isRegularFile(runtimeDir.resolve("animations.json")));
        assertTrue(Files.isRegularFile(runtimeDir.resolve("tiled-animations.json")));

        FileHandle manifest = new FileHandle(runtimeDir.resolve(TILESET_PROFILES_JSON).toFile());
        assertTrue(manifest.exists());

        JsonValue root = new JsonReader().parse(manifest);
        assertEquals("pixscape.tileset-profiles", root.getString("format"));
        assertEquals(1, root.getInt("version"));

        JsonValue tilesets = root.get("tilesets");
        assertEquals(2, tilesets.size);

        JsonValue propsJson = tilesets.get(0);
        assertEquals(props.id, propsJson.getInt("tilesetId"));
        assertEquals("tiles/props", propsJson.getString("logicalPath"));
        assertEquals("orthogonal", propsJson.getString("projection"));
        assertEquals("bottom-left", propsJson.getString("anchor"));
        assertEquals("native", propsJson.getString("renderSize"));
        assertEquals(crate.id, propsJson.get("tileAssetIds").get(0).asInt());

        JsonValue terrainJson = tilesets.get(1);
        assertEquals(terrain.id, terrainJson.getInt("tilesetId"));
        assertEquals("tiles/terrain", terrainJson.getString("logicalPath"));
        assertEquals(64, terrainJson.getInt("tileWidth"));
        assertEquals(96, terrainJson.getInt("tileHeight"));
        assertEquals(32, terrainJson.getInt("referenceCellWidth"));
        assertEquals(16, terrainJson.getInt("referenceCellHeight"));
        assertEquals("isometric", terrainJson.getString("projection"));
        assertEquals("top-center", terrainJson.getString("anchor"));
        assertEquals(4, terrainJson.getInt("offsetX"));
        assertEquals(-8, terrainJson.getInt("offsetY"));
        assertEquals("native", terrainJson.getString("renderSize"));
        assertEquals(grass.id, terrainJson.get("tileAssetIds").get(0).asInt());
        assertEquals(water.id, terrainJson.get("tileAssetIds").get(1).asInt());
    }

    @Test
    public void exportRuntimeWritesTilesetProfilesForSceneUsedTilesWhenRuntimeAvailabilityIsEmpty() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-scene-used-tileset-profiles-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-scene-used-tileset-profiles-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Scene Used Tileset Profiles Export";
        cfg.projectFileName = "scene-used-tileset-profiles-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        tileset.tileWidth = 32;
        tileset.tileHeight = 32;
        tileset.referenceCellWidth = 32;
        tileset.referenceCellHeight = 32;
        tileset.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        tileset.anchor = TilesetAnchor.BOTTOM_CENTER;
        tileset.renderSize = TilesetRenderSize.NATIVE;

        TileAssetMeta first = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/first",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        first.tilesetId = tileset.id;

        TileAssetMeta second = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/second",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        second.tilesetId = tileset.id;

        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(
                studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"),
                tiledSceneJson(first.id, 0, second.id),
                StandardCharsets.UTF_8
        );

        assertTrue(cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.isEmpty());

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        Path runtimeDir = userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        JsonValue projectRoot = new JsonReader().parse(
                new FileHandle(runtimeDir.resolve(RuntimeExport.PROJECT_JSON).toFile())
        );
        JsonValue runtimeAvailability = projectRoot.get("scenes").get("Main").get("runtimeAvailability");
        assertEquals(0, runtimeAvailability.get("tiledTiles").size);
        assertTrue(cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.isEmpty());

        JsonValue root = new JsonReader().parse(
                new FileHandle(runtimeDir.resolve(TILESET_PROFILES_JSON).toFile())
        );
        JsonValue tilesets = root.get("tilesets");
        assertEquals(1, tilesets.size);

        JsonValue tileAssetIds = tilesets.get(0).get("tileAssetIds");
        assertEquals(2, tileAssetIds.size);
        assertEquals(first.id, tileAssetIds.get(0).asInt());
        assertEquals(second.id, tileAssetIds.get(1).asInt());
    }

    @Test
    public void exportRuntimeWritesTilesetProfilesForTiledAnimationFramesWhenRuntimeAvailabilityTilesAreEmpty() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-tiled-animation-profiles-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-tiled-animation-profiles-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Tiled Animation Profiles Export";
        cfg.projectFileName = "tiled-animation-profiles-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        tileset.tileWidth = 32;
        tileset.tileHeight = 32;
        tileset.referenceCellWidth = 32;
        tileset.referenceCellHeight = 32;
        tileset.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        tileset.anchor = TilesetAnchor.TOP_CENTER;
        tileset.renderSize = TilesetRenderSize.NATIVE;

        TileAssetMeta firstFrame = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/frame-a",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        firstFrame.tilesetId = tileset.id;

        TileAssetMeta secondFrame = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/frame-b",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        secondFrame.tilesetId = tileset.id;

        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        TileAnimationsMetaDatabase animations = TileAnimationsIO.createEmpty();
        TileAnimationProjectDefData animation = new TileAnimationProjectDefData();
        animation.id = 9001;
        animation.name = "terrain_anim_0";
        animation.frameAssetIds = new int[]{firstFrame.id, secondFrame.id};
        animation.frameDurationsMs = new int[]{100, 150};
        animations.animations.add(animation);
        TileAnimationsIO.save(
                animations,
                new FileHandle(studioDir.resolve(RuntimeFs.FILE_TILE_ANIMATIONS_JSON).toFile())
        );

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(
                studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"),
                tiledSceneJson(animation.id),
                StandardCharsets.UTF_8
        );

        assertTrue(cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.isEmpty());

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        Path runtimeDir = userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        JsonValue projectRoot = new JsonReader().parse(
                new FileHandle(runtimeDir.resolve(RuntimeExport.PROJECT_JSON).toFile())
        );
        JsonValue runtimeAvailability = projectRoot.get("scenes").get("Main").get("runtimeAvailability");
        assertEquals(0, runtimeAvailability.get("tiledTiles").size);
        assertTrue(cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.isEmpty());

        JsonValue root = new JsonReader().parse(
                new FileHandle(runtimeDir.resolve(TILESET_PROFILES_JSON).toFile())
        );
        JsonValue tileAssetIds = root.get("tilesets").get(0).get("tileAssetIds");
        assertEquals(2, tileAssetIds.size);
        assertEquals(firstFrame.id, tileAssetIds.get(0).asInt());
        assertEquals(secondFrame.id, tileAssetIds.get(1).asInt());
    }

    @Test(expected = GdxRuntimeException.class)
    public void exportRuntimeRejectsSceneUsedTileWithoutTileMetadata() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-scene-used-missing-tile-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-scene-used-missing-tile-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Scene Used Missing Tile Export";
        cfg.projectFileName = "scene-used-missing-tile-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        AssetMetaDatabase db = new AssetMetaDatabase();
        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(
                studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"),
                tiledSceneJson(1451),
                StandardCharsets.UTF_8
        );

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));
    }

    @Test
    public void exportRuntimeAllowsEmptyTilesetProfilesWhenNoTiledUsageExists() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-no-tiled-usage-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-no-tiled-usage-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "No Tiled Usage Export";
        cfg.projectFileName = "no-tiled-usage-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        AssetMetaDatabase db = new AssetMetaDatabase();
        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        JsonValue root = new JsonReader().parse(
                new FileHandle(userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve(TILESET_PROFILES_JSON).toFile())
        );
        assertEquals(0, root.get("tilesets").size);
    }

    private static String tiledSceneJson(int... tileAssetIds) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < tileAssetIds.length; i++) {
            if (i > 0) ids.append(", ");
            ids.append(tileAssetIds[i]);
        }
        return """
                {
                  "entities": {
                    "0": {
                      "components": {
                        "TiledLayerComponent": {
                          "tileAssetIds": {
                            "items": [ %s ],
                            "size": %d
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(ids, tileAssetIds.length);
    }
}
