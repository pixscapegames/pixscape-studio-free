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
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TintComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.loading.WorldConfigFactory;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
        assertEquals(24f, transform.y, 0.0001f);
        assertEquals(0f, transform.originX, 0.0001f);
        assertEquals(0f, transform.originY, 0.0001f);
        assertEquals(64f, dimensions.width, 0.0001f);
        assertEquals(32f, dimensions.height, 0.0001f);
        assertTrue(assetRef.assetId > 0);
        assertEquals(result.sceneTag(), assetRef.atlasTag);
        assertEquals(0x80FFFFFF, tint.getRgba());

        AssetMeta imageMeta = h.db.findById(assetRef.assetId);
        assertNotNull(imageMeta);
        assertTrue(imageMeta.sourceRelPath.startsWith(StudioFs.DIR_ORIG_IMAGES + "/"));
        assertTrue(h.projectDir.child(imageMeta.sourceRelPath).exists());
        assertTrue(h.cfg.getSceneMeta("Imported").runtimeAvailability.spriteAssetIds.contains(assetRef.assetId));
        assertTrue(h.projectDir.child(StudioFs.DIR_ATLASES)
                .child(StudioFs.DIR_INPUT)
                .child(result.sceneTag())
                .child(new FileHandle(imageMeta.sourceRelPath).name())
                .exists());
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
        long hFlip = Integer.toUnsignedLong(TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1);
        long vFlip = Integer.toUnsignedLong(TmxGidSupport.FLIPPED_VERTICALLY_FLAG | 1);
        long dFlip = Integer.toUnsignedLong(TmxGidSupport.FLIPPED_DIAGONALLY_FLAG | 1);
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
    public void importSceneRejectsPlansAboveFixedTiledBudgetBeforeMutation() throws Exception {
        Harness h = harness("tmx-import-budget");
        writePng(h.projectDir.child("wide.png"), 16, 16);
        int width = WorldConfigFactory.DEFAULT_TILED_BUDGET + 1;
        StringBuilder zeros = new StringBuilder(width * 2);
        for (int i = 0; i < width; i++) {
            if (i > 0) zeros.append(',');
            zeros.append('0');
        }
        FileHandle tmx = writeTmx(h.root.resolve("wide.tmx"), """
                <map orientation="orthogonal" width="%d" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="wide" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="wide.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="%d" height="1"><data encoding="csv">%s</data></layer>
                </map>
                """.formatted(width, width, zeros));

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Too Wide"));

        assertEquals(TmxSceneImportStatus.TILED_BUDGET_EXCEEDED, result.status());
        assertEquals("Main", h.cfg.getCurrentSceneName());
        assertFalse(h.cfg.getScenesMap().containsKey("Too Wide"));
        assertFalse(h.projectDir.child(StudioFs.DIR_SCENES).child("scene2.json").exists());
    }

    @Test
    public void importSceneRejectsSpacingMarginTilesetsBeforeMutation() throws Exception {
        Harness h = harness("tmx-import-spacing-margin");
        FileHandle tmx = writeTmx(h.root.resolve("spacing.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1" spacing="1">
                    <image source="terrain.png" width="17" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Spacing"));

        assertEquals(TmxSceneImportStatus.UNSUPPORTED_TILESET_SPACING_MARGIN, result.status());
        assertEquals("Main", h.cfg.getCurrentSceneName());
        assertFalse(h.cfg.getScenesMap().containsKey("Spacing"));
        assertEquals(0, h.db.assets.size);
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
        assertEquals(0, h.db.assets.size);
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
        assertEquals(0, h.db.assets.size);
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

    private static FileHandle writeTmx(Path path, String text) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
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

    private static World loadImportedWorld(Harness h, TmxSceneImportResult result) {
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        SceneLoader.loadScene(world, h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName()), false);
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

    private static void assertCell(TiledLayerComponent tiled, int sparseIndex, int expectedX, int expectedY) {
        assertEquals(expectedX, tiled.tileXs.get(sparseIndex));
        assertEquals(expectedY, tiled.tileYs.get(sparseIndex));
        assertTrue(tiled.tileAssetIds.get(sparseIndex) > 0);
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
