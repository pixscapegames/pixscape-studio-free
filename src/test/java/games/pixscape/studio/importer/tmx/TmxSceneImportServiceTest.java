package games.pixscape.studio.importer.tmx;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class TmxSceneImportServiceTest {

    private static HeadlessApplication app;

    @BeforeClass
    public static void startGdx() {
        if (Gdx.app == null) {
            app = new HeadlessApplication(new ApplicationAdapter() {
            }, new HeadlessApplicationConfiguration());
        }
    }

    @AfterClass
    public static void stopGdx() {
        if (app != null) {
            app.exit();
            app = null;
        }
    }

    @Test
    public void importSceneReusesPlannerAndCreatesNewScene() throws Exception {
        Harness h = harness("tmx-import-new-scene");
        FileHandle tmx = simpleTmx(h, "map.tmx", "1,0,2,4");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Map"));

        assertTrue(result.imported());
        assertNotNull(result.planResult());
        assertTrue(result.planResult().hasPlan());
        assertEquals("Imported Map", result.sceneName());
        assertEquals("Imported Map", h.cfg.getCurrentSceneName());
        assertNotNull(h.cfg.getSceneMeta("Main"));
        assertNotNull(h.cfg.getSceneMeta("Imported Map"));
        assertFalse("import must create a new scene file", "scene1.json".equals(result.sceneFileName()));
    }

    @Test
    public void importSceneConfiguresSceneMetaTiledFields() throws Exception {
        Harness h = harness("tmx-import-scene-meta");
        writePng(h.projectDir.child("terrain.png"), 64, 32);
        FileHandle tmx = writeTmx(h.root.resolve("iso.tmx"), """
                <map orientation="isometric" width="2" height="2" tilewidth="32" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="32" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="64" height="32"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">1,0,2,4</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, null));

        assertTrue(result.imported());
        SceneMeta meta = h.cfg.getSceneMeta(result.sceneName());
        assertTrue(meta.tiledEnabled);
        assertEquals(32f, meta.tileWidth, 0.0001f);
        assertEquals(16f, meta.tileHeight, 0.0001f);
        assertEquals(SceneMetaRuntime.TiledProjection.ISO, meta.tiledProjection);
    }

    @Test
    public void importSceneImportsTilesetsAndPersistsProjectAssetsAndScene() throws Exception {
        Harness h = harness("tmx-import-persistence");
        FileHandle tmx = simpleTmx(h, "map.tmx", "1,0,2,4");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));

        assertTrue(h.projectDir.child(StudioFs.FILE_ASSETS_JSON).exists());
        assertTrue(StudioFs.requireStudioProjectFile(h.cfg).exists());
        assertTrue(h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName()).exists());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(4, result.importedTileCount());
        assertNotNull(h.db.findByLogicalPath("tiles/terrain"));
        assertTrue(h.cfg.getSceneMeta("Imported").runtimeAvailability.tiledTileAssetIds.size() >= 4);
    }

    @Test
    public void importSceneCreatesTiledLayersInRenderOrder() throws Exception {
        Harness h = harness("tmx-import-layer-order");
        FileHandle tmx = writeTmx(h.root.resolve("layers.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                  <layer name="Above" visible="0" parallaxx="2" parallaxy="0.5" offsetx="3" offsety="4" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        World world = loadImportedWorld(h, result);

        int ground = layerEntity(world, 0, true);
        int above = layerEntity(world, 1, true);
        assertEquals("Ground", world.getMapper(LayerMetaComponent.class).get(ground).name);
        assertEquals("Above", world.getMapper(LayerMetaComponent.class).get(above).name);
        assertFalse(world.getMapper(VisibilityComponent.class).get(above).visible);
        assertEquals(2f, world.getMapper(LayerParallaxComponent.class).get(above).factorX, 0.0001f);
        assertEquals(0.5f, world.getMapper(LayerParallaxComponent.class).get(above).factorY, 0.0001f);
        assertEquals(3f, world.getMapper(TiledLayerComponent.class).get(above).originX, 0.0001f);
        assertEquals(4f, world.getMapper(TiledLayerComponent.class).get(above).originY, 0.0001f);
    }

    @Test
    public void importSceneCreatesImageLayersAsClassicLayersWithSprite() throws Exception {
        Harness h = harness("tmx-import-image-layer");
        writePng(h.projectDir.child("background.png"), 64, 32);
        FileHandle tmx = writeTmx(h.root.resolve("image-layer.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                  <imagelayer name="Backdrop" visible="0" opacity="0.5" offsetx="3" offsety="4" x="10" y="20" parallaxx="2" parallaxy="0.25">
                    <image source="background.png" width="64" height="32"/>
                  </imagelayer>
                  <layer name="Above" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        World world = loadImportedWorld(h, result);

        int ground = layerEntity(world, 0, true);
        int backdrop = layerEntity(world, 1, false);
        int above = layerEntity(world, 2, true);
        assertEquals("Ground", world.getMapper(LayerMetaComponent.class).get(ground).name);
        assertEquals("Backdrop", world.getMapper(LayerMetaComponent.class).get(backdrop).name);
        assertEquals("Above", world.getMapper(LayerMetaComponent.class).get(above).name);
        assertEquals(LayerComponent.TYPE_CLASSIC, world.getMapper(LayerComponent.class).get(backdrop).type);
        assertFalse(world.getMapper(VisibilityComponent.class).get(backdrop).visible);
        assertEquals(2f, world.getMapper(LayerParallaxComponent.class).get(backdrop).factorX, 0.0001f);
        assertEquals(0.25f, world.getMapper(LayerParallaxComponent.class).get(backdrop).factorY, 0.0001f);

        int sprite = drawableInLayer(world, 1);
        TransformComponent transform = world.getMapper(TransformComponent.class).get(sprite);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(sprite);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).get(sprite);
        TintComponent tint = world.getMapper(TintComponent.class).get(sprite);

        assertEquals(13f, transform.x, 0.0001f);
        assertEquals(-40f, transform.y, 0.0001f);
        assertEquals(0f, transform.originX, 0.0001f);
        assertEquals(0f, transform.originY, 0.0001f);
        assertEquals(64f, dimensions.width, 0.0001f);
        assertEquals(32f, dimensions.height, 0.0001f);
        assertTrue(assetRef.assetId > 0);
        assertEquals(result.sceneTag(), assetRef.atlasTag);
        assertEquals(0x80FFFFFF, tint.getRgba());

        AssetMeta imageMeta = h.db.findById(assetRef.assetId);
        assertNotNull(imageMeta);
        assertTrue(imageMeta.sourceRelPath().startsWith(StudioFs.DIR_ORIG_IMAGES + "/"));
        assertTrue(h.projectDir.child(imageMeta.sourceRelPath()).exists());
        assertTrue(h.cfg.getSceneMeta("Imported").runtimeAvailability.spriteAssetIds.contains(assetRef.assetId));
        assertTrue(h.projectDir.child(StudioFs.DIR_ATLASES)
                .child(StudioFs.DIR_INPUT)
                .child(result.sceneTag())
                .child(new FileHandle(imageMeta.sourceRelPath()).name())
                .exists());
    }

    @Test
    public void importScenePlacesOrthogonalImageLayersUsingTiledTopLeftCoordinates() throws Exception {
        Harness h = harness("tmx-import-image-layer-placement");
        writePng(h.projectDir.child("graphics").child("sky.png"), 384, 240);
        FileHandle tmx = writeTmx(h.root.resolve("maps").resolve("image-layer-placement.tmx"), """
                <map orientation="orthogonal" width="75" height="20" tilewidth="16" tileheight="16">
                  <imagelayer name="Sky">
                    <image source="../graphics/sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyOffset" offsetx="10" offsety="5">
                    <image source="../graphics/sky.png" width="384" height="240"/>
                  </imagelayer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Placement"));
        World world = loadImportedWorld(h, result);

        TransformComponent sky = world.getMapper(TransformComponent.class).get(drawableInLayer(world, 0));
        TransformComponent skyOffset = world.getMapper(TransformComponent.class).get(drawableInLayer(world, 1));

        assertEquals(0f, sky.x, 0.0001f);
        assertEquals(80f, sky.y, 0.0001f);
        assertEquals(10f, skyOffset.x, 0.0001f);
        assertEquals(75f, skyOffset.y, 0.0001f);
    }

    @Test
    public void importScenePreservesMixedImageAndTileLayerStackOrder() throws Exception {
        Harness h = harness("tmx-import-mixed-layer-order");
        writePng(h.projectDir.child("sky.png"), 384, 240);
        writePng(h.projectDir.child("jungle.png"), 384, 240);
        FileHandle tmx = writeTmx(h.root.resolve("mixed-layers.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <imagelayer id="1" name="sky">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer id="2" name="jungle">
                    <image source="jungle.png" width="384" height="240"/>
                  </imagelayer>
                  <layer id="3" name="ground" width="1" height="1"><data encoding="csv">1</data></layer>
                  <layer id="4" name="props" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Order"));
        World world = loadImportedWorld(h, result);

        assertEquals("sky", world.getMapper(LayerMetaComponent.class).get(layerEntity(world, 0, false)).name);
        assertEquals("jungle", world.getMapper(LayerMetaComponent.class).get(layerEntity(world, 1, false)).name);
        assertEquals("ground", world.getMapper(LayerMetaComponent.class).get(layerEntity(world, 2, true)).name);
        assertEquals("props", world.getMapper(LayerMetaComponent.class).get(layerEntity(world, 3, true)).name);
        assertEquals(LayerComponent.TYPE_CLASSIC, world.getMapper(LayerComponent.class).get(layerEntity(world, 0, false)).type);
        assertEquals(LayerComponent.TYPE_CLASSIC, world.getMapper(LayerComponent.class).get(layerEntity(world, 1, false)).type);
        assertEquals(LayerComponent.TYPE_TILED, world.getMapper(LayerComponent.class).get(layerEntity(world, 2, true)).type);
        assertEquals(LayerComponent.TYPE_TILED, world.getMapper(LayerComponent.class).get(layerEntity(world, 3, true)).type);
    }

    @Test
    public void importSceneAttachesImageLayerRepeatComponentWithoutDuplicatingSprites() throws Exception {
        Harness h = harness("tmx-import-image-layer-repeat");
        writePng(h.projectDir.child("sky.png"), 384, 240);
        FileHandle tmx = writeTmx(h.root.resolve("image-layer-repeat.tmx"), """
                <map orientation="orthogonal" width="75" height="20" tilewidth="16" tileheight="16">
                  <imagelayer name="SkyX" parallaxx="1.3" repeatx="1">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyY" repeaty="true">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyBoth" repeatx="true" repeaty="1">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                  <imagelayer name="SkyNone">
                    <image source="sky.png" width="384" height="240"/>
                  </imagelayer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported Repeat"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        assertEquals(4, result.importedLayerCount());
        assertEquals(1, drawableCountInLayer(world, 0));
        assertEquals(1, drawableCountInLayer(world, 1));
        assertEquals(1, drawableCountInLayer(world, 2));
        assertEquals(1, drawableCountInLayer(world, 3));

        assertRepeat(world, drawableInLayer(world, 0), true, false);
        assertEquals(1.3f, world.getMapper(LayerParallaxComponent.class).get(layerEntity(world, 0, false)).factorX, 0.0001f);
        assertEquals(80f, world.getMapper(TransformComponent.class).get(drawableInLayer(world, 0)).y, 0.0001f);
        assertRepeat(world, drawableInLayer(world, 1), false, true);
        assertRepeat(world, drawableInLayer(world, 2), true, true);
        assertFalse(world.getMapper(RenderRepeatComponent.class).has(drawableInLayer(world, 3)));
    }

    @Test
    public void importSceneMapsTmxYDownCoordinatesToPixscapeYUpCells() throws Exception {
        Harness h = harness("tmx-import-coordinate-map");
        FileHandle tmx = simpleTmx(h, "map.tmx", "1,0,0,4");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));

        assertEquals(2, tiled.tileXs.size);
        assertCell(tiled, 0, 0, 1);
        assertCell(tiled, 1, 1, 0);
    }

    @Test
    public void importScenePreservesTmxTransformFlags() throws Exception {
        Harness h = harness("tmx-import-transform-flags");
        long hFlip = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        long vFlip = TmxGidSupport.FLIPPED_VERTICALLY_FLAG | 1L;
        long dFlip = TmxGidSupport.FLIPPED_DIAGONALLY_FLAG | 1L;
        FileHandle tmx = writeTmx(h.root.resolve("flags.tmx"), """
                <map orientation="orthogonal" width="3" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="3" height="1"><data encoding="csv">%d,%d,%d</data></layer>
                </map>
                """.formatted(hFlip, vFlip, dFlip));

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Imported"));
        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));

        assertEquals(TileTransformFlags.FLIP_H, tiled.tileTransformFlags.get(0));
        assertEquals(TileTransformFlags.FLIP_V, tiled.tileTransformFlags.get(1));
        assertEquals(TileTransformFlags.FLIP_D, tiled.tileTransformFlags.get(2));
    }

    @Test
    public void importSceneImportsExternalTsxImageCollectionTilesets() throws Exception {
        Harness h = harness("tmx-import-image-collection");
        FileHandle graphics = h.projectDir.child("graphics");
        FileHandle tilesets = h.projectDir.child("tilesets");
        writePng(graphics.child("tree.png"), 16, 32);
        writePng(graphics.child("rock.png"), 16, 16);
        writeString(tilesets.child("props.tsx"), """
                <tileset version="1.10" tiledversion="1.12.1" name="props" tilewidth="16" tileheight="16" tilecount="2" columns="0">
                  <tile id="0">
                    <image source="../graphics/tree.png" width="16" height="32"/>
                  </tile>
                  <tile id="1">
                    <image source="../graphics/rock.png" width="16" height="16"/>
                  </tile>
                </tileset>
                """);
        long hFlipTree = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        FileHandle tmx = writeTmx(h.root.resolve("maps").resolve("props-map.tmx"), """
                <map orientation="orthogonal" width="2" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" source="../tilesets/props.tsx"/>
                  <layer name="ground" width="2" height="1">
                    <data encoding="csv">%d,2</data>
                  </layer>
                </map>
                """.formatted(hFlipTree));

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Props"));

        assertTrue(result.imported());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(2, result.importedTileCount());

        TilesetAssetMeta tileset = requireTileset(h.db.findByLogicalPath("tiles/props"));
        assertEquals(16, tileset.tileWidth);
        assertEquals(16, tileset.tileHeight);
        assertEquals(16, tileset.referenceCellWidth);
        assertEquals(16, tileset.referenceCellHeight);
        assertEquals(TilesetAnchor.BOTTOM_CENTER, tileset.anchor);

        TileAssetMeta tree = requireTile(h.db.findByLogicalPath("tiles/props/0"));
        TileAssetMeta rock = requireTile(h.db.findByLogicalPath("tiles/props/1"));
        assertPngSize(h.projectDir.child(tree.sourceRelPath()), 16, 32);
        assertPngSize(h.projectDir.child(rock.sourceRelPath()), 16, 16);

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 0, tree.id());
        assertTileAsset(tiled, 1, 0, rock.id());
        assertEquals(TileTransformFlags.FLIP_H, tiled.tileTransformFlags.get(0));
        assertEquals(TileTransformFlags.NONE, tiled.tileTransformFlags.get(1));
        assertTrue(h.cfg.getSceneMeta("Props").runtimeAvailability.tiledTileAssetIds.contains(tree.id()));
        assertTrue(h.cfg.getSceneMeta("Props").runtimeAvailability.tiledTileAssetIds.contains(rock.id()));
    }

    @Test
    public void importSceneCreatesTiledAnimationMetadataAndMapsAnimatedCells() throws Exception {
        Harness h = harness("tmx-import-tile-animation");
        FileHandle tmx = animatedTileTmx(h, "animated.tmx", "1,2,0,0");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated"));

        assertTrue(result.imported());
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertTrue(def.id > 0);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id(), def.frameAssetIds[0]);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/2")).id(), def.frameAssetIds[1]);
        assertEquals(100, def.frameDurationsMs[0]);
        assertEquals(150, def.frameDurationsMs[1]);
        assertTrue(h.cfg.getSceneMeta("Animated").runtimeAvailability.tiledAnimationIds.contains(def.id));

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 1, def.id);
        assertTileAsset(tiled, 1, 1, requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id());
    }

    @Test
    public void importScenePreservesTransformFlagsOnAnimatedCells() throws Exception {
        Harness h = harness("tmx-import-animated-transform-flags");
        long hFlipAnimated = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        FileHandle tmx = animatedTileTmx(h, "animated-flags.tmx", hFlipAnimated + ",0,0,0");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated Flags"));

        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );
        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 1, animations.animations.get(0).id);
        assertEquals(TileTransformFlags.FLIP_H, tiled.tileTransformFlags.get(0));
    }

    @Test
    public void importSceneRuntimeExportWritesImportedTileAnimations() throws Exception {
        Harness h = harness("tmx-import-animation-runtime-export");
        FileHandle tmx = animatedTileTmx(h, "animated.tmx", "1,0,0,0");

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated"));
        assertTrue(result.imported());
        RuntimeExport.exportRuntime(h.cfg, h.projectDir, new FileHandle(h.root.resolve("export").toFile()));

        FileHandle runtimeAnimations = new FileHandle(h.root
                .resolve("export")
                .resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
                .toFile());
        TileAnimationsMetaDatabase exported = TileAnimationsIO.load(runtimeAnimations);
        assertEquals(1, exported.animations.size);
        TileAnimationProjectDefData def = exported.animations.get(0);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id(), def.frameAssetIds[0]);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/2")).id(), def.frameAssetIds[1]);
        assertEquals(100, def.frameDurationsMs[0]);
        assertEquals(150, def.frameDurationsMs[1]);

        JsonValue profiles = new JsonReader().parse(new FileHandle(h.root
                .resolve("export")
                .resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve("tileset-profiles.json")
                .toFile()));
        JsonValue tileIds = profiles.get("tilesets").get(0).get("tileAssetIds");
        assertTrue(containsJsonInt(tileIds, def.frameAssetIds[0]));
        assertTrue(containsJsonInt(tileIds, def.frameAssetIds[1]));
    }

    @Test
    public void importSceneImportsTilesetsWithSpacingAndMargin() throws Exception {
        Harness h = harness("tmx-import-spacing-margin");
        int[] tileColors = {
                0xFF0000FF,
                0x00FF00FF,
                0x0000FFFF,
                0xFFFF00FF
        };
        writeSpacedAtlas(h.projectDir.child("terrain.png"), 8, 8, 2, 2, 1, 1, tileColors);
        FileHandle tmx = writeTmx(h.root.resolve("spacing.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="2" tileheight="2">
                  <tileset firstgid="1" name="terrain" tilewidth="2" tileheight="2" tilecount="4" columns="2" spacing="1" margin="1">
                    <image source="terrain.png" width="8" height="8"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">1,2,3,4</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Spacing"));

        assertTrue(result.imported());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(4, result.importedTileCount());

        TilesetAssetMeta tileset = requireTileset(h.db.findByLogicalPath("tiles/terrain"));
        assertEquals(2, tileset.tileWidth);
        assertEquals(2, tileset.tileHeight);
        assertEquals(2, tileset.columns);
        assertEquals(2, tileset.rows);
        assertEquals(1, tileset.spacing);
        assertEquals(1, tileset.margin);

        int tile0 = requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id();
        int tile1 = requireTile(h.db.findByLogicalPath("tiles/terrain/1")).id();
        int tile2 = requireTile(h.db.findByLogicalPath("tiles/terrain/2")).id();
        int tile3 = requireTile(h.db.findByLogicalPath("tiles/terrain/3")).id();

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertTileAsset(tiled, 0, 1, tile0);
        assertTileAsset(tiled, 1, 1, tile1);
        assertTileAsset(tiled, 0, 0, tile2);
        assertTileAsset(tiled, 1, 0, tile3);
    }

    @Test
    public void importSceneReturnsPreflightFailureWithoutMutation() throws Exception {
        Harness h = harness("tmx-import-preflight-failure");
        FileHandle tmx = writeTmx(h.root.resolve("missing-image.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="missing.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Missing"));

        assertEquals(TmxSceneImportStatus.PREFLIGHT_FAILED, result.status());
        assertFalse(h.cfg.getScenesMap().containsKey("Missing"));
        assertEquals(0, h.db.size());
    }

    @Test
    public void importSceneRollsBackSceneProjectAndAssetsWhenMutationFails() throws Exception {
        Harness h = harness("tmx-import-rollback");
        h.projectDir.child("broken.png").writeString("not a png", false, "UTF-8");
        FileHandle tmx = writeTmx(h.root.resolve("broken.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="broken" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="broken.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Broken"));

        assertEquals(TmxSceneImportStatus.FAILED_ROLLED_BACK, result.status());
        assertTrue(result.rollbackAttempted());
        assertTrue(result.rollbackSucceeded());
        assertEquals("Main", h.cfg.getCurrentSceneName());
        assertFalse(h.cfg.getScenesMap().containsKey("Broken"));
        assertFalse(h.projectDir.child(StudioFs.DIR_SCENES).child("scene2.json").exists());
        assertEquals(0, h.db.size());
    }

    @Test
    public void sessionExecutesPhasesInOrderAndProducesEquivalentSuccess() throws Exception {
        Harness h = harness("tmx-import-session-success");
        FileHandle tmx = simpleTmx(h, "session.tmx", "1,2,3,4");
        TmxSceneImportSession session = h.importer().beginImport(request(tmx, "Session"));

        session.prepare();
        session.createScene();
        session.importAssets();
        session.materializeAndSaveScene();
        session.updateAtlas();
        TmxSceneImportResult result = session.persistAndFinish();

        assertTrue(result.imported());
        assertEquals("Session", result.sceneName());
        assertEquals(1, result.importedTilesetCount());
        assertEquals(4, result.importedTileCount());
        assertFalse(session.hasTemporaryWorld());
    }

    @Test
    public void sessionRejectsDuplicateAndOutOfOrderPhases() throws Exception {
        Harness h = harness("tmx-import-session-order");
        TmxSceneImportSession session = h.importer().beginImport(
                request(simpleTmx(h, "order.tmx", "1,0,0,0"), "Order")
        );

        assertIllegalState(session::importAssets);
        session.prepare();
        assertIllegalState(session::prepare);
        session.createScene();
        assertIllegalState(session::persistAndFinish);

        TmxSceneImportResult rollback = session.rollback(new RuntimeException("stop after order checks"));
        assertEquals(TmxSceneImportStatus.FAILED_ROLLED_BACK, rollback.status());
    }

    @Test
    public void sessionMaterializeFailureDisposesWorldAndRollsBackExactlyOnce() throws Exception {
        Harness h = harness("tmx-import-session-dispose");
        TmxSceneImportSession session = h.importer().beginImport(
                request(simpleTmx(h, "dispose.tmx", "1,0,0,0"), "Dispose")
        );
        session.prepare();
        session.createScene();
        session.importAssets();
        FileHandle scenesPath = h.projectDir.child(StudioFs.DIR_SCENES);
        scenesPath.deleteDirectory();
        scenesPath.writeString("not a directory", false, "UTF-8");

        RuntimeException materializeFailure = null;
        try {
            session.materializeAndSaveScene();
        } catch (RuntimeException failure) {
            materializeFailure = failure;
        }

        assertNotNull(materializeFailure);
        assertFalse(session.hasTemporaryWorld());
        TmxSceneImportResult firstRollback = session.rollback(materializeFailure);
        TmxSceneImportResult secondRollback = session.rollback(new RuntimeException("must be ignored"));
        assertSame(firstRollback, secondRollback);
        assertTrue(firstRollback.rollbackSucceeded());
        assertNull(h.cfg.getSceneMeta("Dispose"));
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static TmxSceneImportRequest request(FileHandle tmx, String sceneName) {
        return new TmxSceneImportRequest(tmx, sceneName, false);
    }

    private static Harness harness(String name) throws Exception {
        Path root = Files.createTempDirectory(name);
        FileHandle projectDir = new FileHandle(root.toFile());
        projectDir.child(StudioFs.DIR_SCENES).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_TILES).mkdirs();
        projectDir.child(StudioFs.DIR_ATLASES).mkdirs();

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = name;
        cfg.projectFileName = name;
        cfg.projectDirectoryPath = root.toString();
        cfg.exportRootPathDir = root.resolve("export").toString();
        cfg.createSceneMeta("Main");
        ProjectConfig.setInstance(cfg);

        AssetMetaDatabase db = new AssetMetaDatabase();
        db.save(projectDir.child(StudioFs.FILE_ASSETS_JSON));
        ProjectConfig.ProjectIO.saveProject(cfg, StudioFs.requireStudioProjectFile(cfg));
        Files.writeString(root.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);
        writePng(projectDir.child("terrain.png"), 32, 32);
        return new Harness(root, projectDir, cfg, db);
    }

    private static FileHandle simpleTmx(Harness h, String name, String csv) throws Exception {
        return writeTmx(h.root.resolve(name), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">%s</data></layer>
                </map>
                """.formatted(csv));
    }

    private static FileHandle animatedTileTmx(Harness h, String name, String csv) throws Exception {
        return writeTmx(h.root.resolve(name), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0">
                      <animation>
                        <frame tileid="1" duration="100"/>
                        <frame tileid="2" duration="150"/>
                      </animation>
                    </tile>
                  </tileset>
                  <layer name="Ground" width="2" height="2"><data encoding="csv">%s</data></layer>
                </map>
                """.formatted(csv));
    }

    private static FileHandle writeTmx(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }

    private static void writeString(FileHandle file, String text) {
        file.parent().mkdirs();
        file.writeString(text, false, StandardCharsets.UTF_8.name());
    }

    private static void writePng(FileHandle file, int width, int height) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(0.2f, 0.6f, 0.3f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static void assertPngSize(FileHandle file, int width, int height) {
        Pixmap pixmap = new Pixmap(file);
        try {
            assertEquals(width, pixmap.getWidth());
            assertEquals(height, pixmap.getHeight());
        } finally {
            pixmap.dispose();
        }
    }

    private static void writeSpacedAtlas(FileHandle file,
                                         int width,
                                         int height,
                                         int tileWidth,
                                         int tileHeight,
                                         int spacing,
                                         int margin,
                                         int[] tileColors) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(0xFF00FFFF);
            pixmap.fill();
            int index = 0;
            for (int y = margin; y <= height - tileHeight; y += tileHeight + spacing) {
                for (int x = margin; x <= width - tileWidth; x += tileWidth + spacing) {
                    pixmap.setColor(tileColors[index++]);
                    pixmap.fillRectangle(x, y, tileWidth, tileHeight);
                }
            }
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static World loadImportedWorld(Harness h, TmxSceneImportResult result) {
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        SceneLoader.loadScene(
                world,
                h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName()),
                false,
                h.cfg.getSceneMeta(result.sceneName()));
        world.process();
        return world;
    }

    private static TiledLayerComponent firstTiled(World world) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();
        assertEquals(1, entities.size());
        return world.getMapper(TiledLayerComponent.class).get(entities.get(0));
    }

    private static int layerEntity(World world, int index, boolean requireTiled) {
        ComponentMapper<LayerComponent> layers = world.getMapper(LayerComponent.class);
        Aspect.Builder aspect = requireTiled
                ? Aspect.all(LayerComponent.class, TiledLayerComponent.class)
                : Aspect.all(LayerComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(aspect)
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (layers.get(entity).layerIndex == index) {
                return entity;
            }
        }
        throw new AssertionError("Missing layer index " + index);
    }

    private static int drawableInLayer(World world, int layerIndex) {
        ComponentMapper<EntityIndexComponent> indices = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, AssetRefComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (indices.get(entity).getLayerIndex() == layerIndex) {
                return entity;
            }
        }
        throw new AssertionError("Missing drawable in layer " + layerIndex);
    }

    private static int drawableCountInLayer(World world, int layerIndex) {
        int count = 0;
        ComponentMapper<EntityIndexComponent> indices = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, AssetRefComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (indices.get(entity).getLayerIndex() == layerIndex) {
                count++;
            }
        }
        return count;
    }

    private static void assertRepeat(World world, int entity, boolean repeatX, boolean repeatY) {
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).get(entity);
        assertNotNull(repeat);
        assertEquals(repeatX, repeat.repeatX);
        assertEquals(repeatY, repeat.repeatY);
    }

    private static void assertCell(TiledLayerComponent tiled, int sparseIndex, int expectedX, int expectedY) {
        assertEquals(expectedX, tiled.tileXs.get(sparseIndex));
        assertEquals(expectedY, tiled.tileYs.get(sparseIndex));
        assertTrue(tiled.tileAssetIds.get(sparseIndex) > 0);
    }

    private static void assertTileAsset(TiledLayerComponent tiled, int expectedX, int expectedY, int expectedAssetId) {
        for (int i = 0; i < tiled.tileXs.size; i++) {
            if (tiled.tileXs.get(i) == expectedX && tiled.tileYs.get(i) == expectedY) {
                assertEquals(expectedAssetId, tiled.tileAssetIds.get(i));
                return;
            }
        }
        throw new AssertionError("Missing tile cell " + expectedX + "," + expectedY);
    }

    private static boolean containsJsonInt(JsonValue array, int value) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonValue item = array.child; item != null; item = item.next) {
            if (item.asInt() == value) {
                return true;
            }
        }
        return false;
    }

    private static TilesetAssetMeta requireTileset(AssetMeta meta) {
        assertNotNull(meta);
        assertTrue(meta instanceof TilesetAssetMeta);
        return (TilesetAssetMeta) meta;
    }

    private static TileAssetMeta requireTile(AssetMeta meta) {
        assertNotNull(meta);
        assertTrue(meta instanceof TileAssetMeta);
        return (TileAssetMeta) meta;
    }

    private record Harness(Path root,
                           FileHandle projectDir,
                           ProjectConfig cfg,
                           AssetMetaDatabase db) {
        TmxSceneImportService importer() {
            return new TmxSceneImportService(cfg, projectDir, db);
        }
    }
}
