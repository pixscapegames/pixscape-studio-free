package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAssetMeta;
import games.pixscape.studio.helper.InternalAssets;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class RuntimeExportAnimationsTest {

    @Test
    public void exportRuntimeWritesAnimationMetadataJsonMatchingRuntimeContract() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-animations-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-animations-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Animation Export";
        cfg.projectFileName = "animation-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);

        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta meta = db.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero",
                AssetMeta.AssetScope.USER
        );
        AnimationAssetMeta animation = (AnimationAssetMeta) meta;
        animation.frameCount = 8;
        animation.fps = 10f;
        animation.currentClip = "run";
        animation.clips.put("idle", new AnimationComponent.Clip(0, 1));
        AnimationComponent.Clip run = new AnimationComponent.Clip(2, 7);
        run.flipX = true;
        animation.clips.put("run", run);
        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        FileHandle out = new FileHandle(userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("animations.json").toFile());
        assertTrue(out.exists());

        JsonValue root = new JsonReader().parse(out);
        JsonValue exported = root.get("animations").get(0);
        assertEquals(animation.id, exported.getInt("assetId"));
        assertEquals("hero", exported.getString("name"));
        assertEquals(10f, exported.getFloat("fps"), 0.0001f);
        assertEquals("run", exported.getString("currentClip"));
        assertEquals(8, exported.getInt("frameCount"));

        JsonValue clips = exported.get("clips");
        assertTrue(clips.isArray());
        assertEquals(2, clips.size);
        assertEquals("idle", clips.get(0).getString("name"));
        assertEquals(0, clips.get(0).getInt("start"));
        assertEquals(1, clips.get(0).getInt("end"));
        assertFalse(clips.get(0).getBoolean("flipX"));
        assertEquals("run", clips.get(1).getString("name"));
        assertEquals(2, clips.get(1).getInt("start"));
        assertEquals(7, clips.get(1).getInt("end"));
        assertTrue(clips.get(1).getBoolean("flipX"));
    }

    @Test
    public void exportRuntimeWritesDefaultClipWhenAnimationHasNoClips() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-animation-default-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-animation-default-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Animation Default Export";
        cfg.projectFileName = "animation-default-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);

        AssetMetaDatabase db = new AssetMetaDatabase();
        AnimationAssetMeta animation = (AnimationAssetMeta) db.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/slime",
                "orig/animations/slime",
                AssetMeta.AssetScope.USER
        );
        animation.frameCount = 3;
        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        FileHandle out = new FileHandle(userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("animations.json").toFile());
        JsonValue exported = new JsonReader().parse(out).get("animations").get(0);
        JsonValue clip = exported.get("clips").get(0);

        assertEquals("slime", exported.getString("name"));
        assertEquals("default", exported.getString("currentClip"));
        assertEquals("default", clip.getString("name"));
        assertEquals(0, clip.getInt("start"));
        assertEquals(2, clip.getInt("end"));
    }

    @Test
    public void exportRuntimeWritesExpandedRuntimeAvailability() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-availability-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-availability-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Runtime Availability Export";
        cfg.projectFileName = "runtime-availability-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");
        SceneMeta scene = cfg.getCurrentSceneMeta();
        scene.runtimeAvailability.spriteAssetIds.add(10);
        scene.runtimeAvailability.animationAssetIds.add(11);
        scene.runtimeAvailability.particleEffectPaths.add("impact.p");
        scene.runtimeAvailability.prefabIds.add("enemy");
        scene.runtimeAvailability.tiledAnimationIds.add(13);

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        tileset.tileWidth = 32;
        tileset.tileHeight = 32;
        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/grass",
                "orig/tiles/terrain.png",
                AssetMeta.AssetScope.USER
        );
        tile.tilesetId = tileset.id;
        db.save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));
        scene.runtimeAvailability.tiledTileAssetIds.add(tile.id);

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        FileHandle out = new FileHandle(userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve(RuntimeExport.PROJECT_JSON).toFile());
        JsonValue availability = new JsonReader().parse(out)
                .get("scenes")
                .get("Main")
                .get("runtimeAvailability");

        assertEquals(10, availability.get("sprites").get(0).asInt());
        assertEquals(11, availability.get("animations").get(0).asInt());
        assertEquals("impact.p", availability.get("particles").get(0).asString());
        assertEquals("enemy", availability.get("prefabs").get(0).asString());
        assertEquals(tile.id, availability.get("tiledTiles").get(0).asInt());
        assertEquals(13, availability.get("tiledAnimations").get(0).asInt());
    }

    @Test
    public void exportRuntimePreservesLayerSpatialFlagsAndTiledDefaults() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-tiled-spatial-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-tiled-spatial-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Tiled Spatial Export";
        cfg.projectFileName = "tiled-spatial-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        /* Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), """
                {
                  "entities": {
                    "0": {
                      "components": {
                        "LayerComponent": {
                          "layerIndex": 0,
                          "type": 3,
                          "spatialEnabled": true
                        },
                        "TiledLayerComponent": {
                          "mapWidthCells": 4,
                          "mapHeightCells": 4,
                          "spatialEnabled": true,
                          "defaultTileAltitude": 2.5,
                          "defaultTileHeight": 16
                        }
                      }
                    }
                  }
                }
                """, StandardCharsets.UTF_8); */
        RuntimeExportTestSceneSupport.writeSpatialLayerScene(new FileHandle(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json").toFile()), cfg.getCurrentSceneMeta());
        new AssetMetaDatabase().save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        FileHandle out = new FileHandle(userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve("scenes")
                .resolve("scene1.json")
                .toFile());
        JsonValue components = new JsonReader().parse(out)
                .get("entities")
                .get("0")
                .get("components");
        JsonValue layer = components.get("LayerComponent");
        JsonValue tiled = components.get("TiledLayerComponent");

        assertTrue(layer.getBoolean("spatialEnabled", false));
        assertTrue(tiled.getBoolean("spatialEnabled", false));
        assertEquals(2.5f, tiled.getFloat("defaultTileAltitude", 0f), 0.0001f);
        assertEquals(16f, tiled.getFloat("defaultTileHeight", 0f), 0.0001f);
    }

    @Test
    public void exportRuntimePreservesSpatialBlocksComponentOnTiledLayers() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-spatial-blocks-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-spatial-blocks-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Spatial Blocks Export";
        cfg.projectFileName = "spatial-blocks-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        /* Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), """
                {
                  "entities": {
                    "0": {
                      "components": {
                        "LayerComponent": {
                          "layerIndex": 0,
                          "type": 3,
                          "spatialEnabled": true
                        },
                        "TiledLayerComponent": {
                          "mapWidthCells": 4,
                          "mapHeightCells": 4,
                          "spatialEnabled": true,
                          "defaultTileAltitude": 2,
                          "defaultTileHeight": 16
                        },
                        "SpatialBlocksComponent": {
                          "blocks": [
                            {
                              "id": 5,
                              "name": "North wall",
                              "enabled": true,
                              "x": 1,
                              "y": 2,
                              "width": 3,
                              "depth": 1,
                              "altitude": 4,
                              "height": 24,
                              "orientation": "TILE_CELL",
                              "actorOccluder": true,
                              "lightOccluder": false,
                              "shadowCaster": false,
                              "particleOccluder": false,
                              "linkedTileRefsAuthored": true,
                              "linkedTileRefs": [
                                { "gx": 1, "gy": 2, "tileAssetId": 101 },
                                { "gx": 2, "gy": 2, "tileAssetId": 102 }
                              ]
                            }
                          ]
                        }
                      }
                    }
                  }
                }
                """, StandardCharsets.UTF_8); */
        RuntimeExportTestSceneSupport.writeSpatialScene(new FileHandle(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json").toFile()), cfg.getCurrentSceneMeta());
        new AssetMetaDatabase().save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        FileHandle out = new FileHandle(userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve("scenes")
                .resolve("scene1.json")
                .toFile());
        JsonValue block = new JsonReader().parse(out)
                .get("entities")
                .get("0")
                .get("components")
                .get("SpatialBlocksComponent")
                .get("blocks")
                .get(0);

        assertEquals(5, block.getInt("id"));
        assertEquals("North wall", block.getString("name"));
        assertEquals(1f, block.getFloat("x"), 0.0001f);
        assertEquals(2f, block.getFloat("y"), 0.0001f);
        assertNull(block.get(legacyAnchorField("Gx")));
        assertNull(block.get(legacyAnchorField("Gy")));
        assertEquals(3f, block.getFloat("width"), 0.0001f);
        assertEquals(1f, block.getFloat("depth"), 0.0001f);
        assertEquals(4f, block.getFloat("altitude"), 0.0001f);
        assertEquals(24f, block.getFloat("height"), 0.0001f);
        assertNull(block.get("orientation"));
        assertTrue(block.getBoolean("actorOccluder", true));
        assertNull(block.get("physics" + "Collision"));
        assertTrue(block.getBoolean("linkedTileRefsAuthored"));
        assertEquals(2, block.get("linkedTileRefs").size);
        assertEquals(1, block.get("linkedTileRefs").get(0).getInt("gx"));
        assertEquals(2, block.get("linkedTileRefs").get(0).getInt("gy"));
        assertEquals(101, block.get("linkedTileRefs").get(0).getInt("tileAssetId"));
    }

    @Test
    public void exportRuntimeCopiesCoreRuntimeShaders() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-shaders-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-shaders-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Shader Export";
        cfg.projectFileName = "shader-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);
        new AssetMetaDatabase().save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        Path shaders = userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("shaders");
        assertTrue(Files.isRegularFile(shaders.resolve("core/es3-webgl2/texture-array.vert")));
        assertTrue(Files.isRegularFile(shaders.resolve("core/es3-webgl2/texture-array.frag")));
        assertTrue(Files.isRegularFile(shaders.resolve("includes/pixscape_common.glsl")));
    }

    @Test
    public void exportRuntimeDoesNotCopyAtlasInputWhitePixelSource() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-white-pixel-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-white-pixel-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "White Pixel Export";
        cfg.projectFileName = "white-pixel-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);
        new AssetMetaDatabase().save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        Path inputWhitePixel = studioDir
                .resolve(StudioFs.DIR_ATLASES)
                .resolve(StudioFs.DIR_INPUT)
                .resolve("scene1")
                .resolve("__pixscape_internal__")
                .resolve(InternalAssets.WHITE_PIXEL_FILE);
        Files.createDirectories(inputWhitePixel.getParent());
        Files.write(inputWhitePixel, new byte[]{1, 2, 3});
        Files.writeString(studioDir.resolve(StudioFs.DIR_ATLASES).resolve("scene1.atlas"), "atlas", StandardCharsets.UTF_8);

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        Path runtimeAtlasDir = userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("atlases");
        assertFalse(Files.exists(runtimeAtlasDir.resolve(StudioFs.DIR_INPUT)));
        assertFalse(Files.exists(runtimeAtlasDir.resolve(StudioFs.DIR_INPUT)
                .resolve("scene1")
                .resolve("__pixscape_internal__")
                .resolve(InternalAssets.WHITE_PIXEL_FILE)));
        assertTrue(Files.isRegularFile(runtimeAtlasDir.resolve("scene1.atlas")));
    }

    @Test
    public void exportRuntimeSanitizesPrefabFragmentsForRuntime() throws Exception {
        Path studioDir = Files.createTempDirectory("pixscape-studio-export-prefab-fragment-studio");
        Path userDir = Files.createTempDirectory("pixscape-studio-export-prefab-fragment-user");

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Prefab Fragment Export";
        cfg.projectFileName = "prefab-fragment-export";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");

        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.createDirectories(studioDir.resolve(StudioFs.DIR_PREFABS));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);
        new AssetMetaDatabase().save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        String fragment = "{" +
                "\"schemaVersion\":1," +
                "\"metadata\":{\"version\":1}," +
                "\"componentIdentifiers\":{" +
                "\"games.pixscape.studio.component.EntityMetaComponent\":\"EntityMetaComponent\"," +
                "\"games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent\":\"PhysicsCompiledFixturesComponent\"," +
                "\"games.pixscape.runtime.component.PixscapeIdentityComponent\":\"PixscapeIdentityComponent\"}," +
                "\"entities\":{\"0\":{\"archetype\":1,\"components\":{" +
                "\"EntityMetaComponent\":{\"kind\":\"SPRITE\"}," +
                "\"PhysicsCompiledFixturesComponent\":{}," +
                "\"PixscapeIdentityComponent\":{\"stableId\":42,\"name\":\"car\"}}}}," +
                "\"archetypes\":{\"1\":[\"EntityMetaComponent\",\"PhysicsCompiledFixturesComponent\",\"PixscapeIdentityComponent\"]}}";
        Files.writeString(
                studioDir.resolve(StudioFs.DIR_PREFABS).resolve("car.pixfragment.json"),
                fragment,
                StandardCharsets.UTF_8
        );

        RuntimeExport.exportRuntime(cfg, new FileHandle(studioDir.toFile()), new FileHandle(userDir.toFile()));

        FileHandle out = new FileHandle(userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve("prefabs")
                .resolve("car.pixfragment.json")
                .toFile());
        String exported = out.readString("UTF-8");

        assertTrue(exported.contains("\"schemaVersion\": 1"));
        assertFalse(exported.contains("games.pixscape.studio"));
        assertFalse(exported.contains("EntityMetaComponent"));
        assertFalse(exported.contains("PhysicsCompiledFixturesComponent"));
        assertFalse(exported.contains("stableId"));
        assertTrue(exported.contains("PixscapeIdentityComponent"));
    }

    private static String legacyAnchorField(String axis) {
        return "anchor" + axis;
    }
}
