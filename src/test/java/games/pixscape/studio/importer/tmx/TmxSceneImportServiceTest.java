package games.pixscape.studio.importer.tmx;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.api.ClassProperty;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.TagRegistry;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.UpdateWorldGeometrySystem;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.AddTiledMapCommand;
import games.pixscape.studio.history.commands.GizmoTransformCommand;
import games.pixscape.studio.history.commands.ToggleTiledMapSpatialDepthCommand;
import games.pixscape.studio.history.commands.TransformOp;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioEditingModeService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
    public void importScenePersistsSourceConfigurationOnMapWithoutMutatingSceneDefaults() throws Exception {
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
        assertEquals(32f, meta.tileWidth, 0.0001f);
        assertEquals(32f, meta.tileHeight, 0.0001f);
        assertEquals(TiledProjection.ORTHO, meta.tiledProjection);

        TiledLayerComponent tiled = firstTiled(loadImportedWorld(h, result));
        assertEquals(TiledProjection.ISO, tiled.projection);
        assertEquals(32, tiled.tileWidth);
        assertEquals(16, tiled.tileHeight);
        assertEquals(16, tiled.chunkSize);
        assertEquals(2, tiled.mapWidthCells);
        assertEquals(2, tiled.mapHeightCells);
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
    public void importSceneCreatesUniversalMapLayersInRenderOrder() throws Exception {
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
        int aboveMap = tiledMapEntity(world, 1);
        assertEquals(3f, world.getMapper(TiledLayerComponent.class).get(aboveMap).originX, 0.0001f);
        assertEquals(4f, world.getMapper(TiledLayerComponent.class).get(aboveMap).originY, 0.0001f);
    }

    @Test
    public void importedTileLayerUsesUniversalOwnershipAndCurrentMapEditingPaths() throws Exception {
        Harness h = harness("tmx-import-universal-map-layer");
        writePng(h.projectDir.child("terrain.png"), 64, 32);
        FileHandle tmx = writeTmx(h.root.resolve("universal-map.tmx"), """
                <map orientation="isometric" width="2" height="2" tilewidth="32" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="32" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="64" height="32"/>
                  </tileset>
                  <layer name="Ground" visible="0" parallaxx="1.5" parallaxy="0.75"
                         offsetx="6" offsety="9" width="2" height="2">
                    <data encoding="csv">1,0,2,4</data>
                  </layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Universal Import"));
        assertTrue("import failed: " + result.failure() + " " + result.diagnostics(),
                result.imported());
        World world = loadImportedWorld(h, result);
        int layerEntity = layerEntity(world, 0, true);
        int mapEntity = tiledMapEntity(world, 0);
        LayerComponent layer = world.getMapper(LayerComponent.class).get(layerEntity);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(mapEntity);
        EntityIndexComponent mapIndex = world.getMapper(EntityIndexComponent.class).get(mapEntity);

        assertFalse(layer.spatialEnabled);
        assertFalse(world.getMapper(LayerComponent.class).has(mapEntity));
        assertEquals(0, mapIndex.layerIndex);
        assertEquals(0, mapIndex.zIndex);
        assertEquals(TiledProjection.ISO, tiled.projection);
        assertEquals(32, tiled.tileWidth);
        assertEquals(16, tiled.tileHeight);
        assertEquals(2, tiled.mapWidthCells);
        assertEquals(2, tiled.mapHeightCells);
        assertEquals(6f, tiled.originX, 0f);
        assertEquals(9f, tiled.originY, 0f);
        assertFalse(world.getMapper(VisibilityComponent.class).get(layerEntity).visible);
        assertTrue(world.getMapper(VisibilityComponent.class).get(mapEntity).visible);
        assertEquals(1.5f, world.getMapper(LayerParallaxComponent.class)
                .get(layerEntity).factorX, 0f);
        assertEquals(0.75f, world.getMapper(LayerParallaxComponent.class)
                .get(layerEntity).factorY, 0f);
        assertFalse(tiled.spatialEnabled);
        tiled.data = tiled.createMapData();
        assertFalse(tiled.data.spatialEnabled);
        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(layerEntity));
        assertFalse(world.getMapper(PhysicsShapesComponent.class).has(layerEntity));
        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(mapEntity));
        assertFalse(world.getMapper(PhysicsShapesComponent.class).has(mapEntity));

        SceneMeta importedMeta = h.cfg.getSceneMeta(result.sceneName());
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, importedMeta);
        HistoryManager history = new HistoryManager(16);
        LayerService layers = new LayerService(world, null, history.historyIds(), identities);
        assertEquals(1, layers.count());
        assertTrue(layers.isLayerEntity(layerEntity));

        int sprite = world.create();
        world.getMapper(EntityIndexComponent.class).create(sprite).layerIndex = 0;
        world.getMapper(TextureRegionComponent.class).create(sprite);
        int light = world.create();
        world.getMapper(EntityIndexComponent.class).create(light).layerIndex = 0;
        world.getMapper(PointLightComponent.class).create(light);

        AtomicInteger selectedMap = new AtomicInteger(-1);
        history.execute(new AddTiledMapCommand(
                layers, layerEntity, 3, 3, TiledProjection.ORTHO,
                16, 16, 4, selectedMap::set));
        world.process();
        assertTrue(selectedMap.get() >= 0);
        assertEquals(2, world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)
                        .exclude(LayerComponent.class))
                .getEntities().size());
        assertEquals(0, world.getMapper(EntityIndexComponent.class)
                .get(selectedMap.get()).layerIndex);
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(sprite).layerIndex);
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(light).layerIndex);
        history.execute(new ToggleTiledMapSpatialDepthCommand(
                world, history.historyIds(), mapEntity,
                true, 3f, 12f));
        assertTrue(tiled.spatialEnabled);
        assertTrue(tiled.data.spatialEnabled);
        assertFalse(layer.spatialEnabled);

        StudioEditingModeService editingModes = new StudioEditingModeService();
        SelectionService selection = new SelectionService(world, layers, editingModes);
        selection.setTiledMapEditingTarget(mapEntity, SelectionService.SelectionSource.TREE);
        assertTrue(selection.isTiledMapEditingTargetActive());
        assertEquals(mapEntity, selection.getTiledMapEditingTargetEntityId());
        assertEquals(layerEntity, selection.getActivelayerId());
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
    public void importSceneMaterializesEmptyObjectLayerInMixedGlobalOrder() throws Exception {
        Harness h = harness("tmx-import-empty-object-layer");
        FileHandle tmx = writeTmx(h.root.resolve("empty-object-layer.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Below" width="1" height="1"><data encoding="csv">1</data></layer>
                  <objectgroup name="Gameplay"/>
                  <layer name="Above" width="1" height="1"><data encoding="csv">0</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Objects"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        assertEquals("Below", world.getMapper(LayerMetaComponent.class).get(layerEntity(world, 0, true)).name);
        int objectLayer = layerEntity(world, 1, false);
        assertEquals("Gameplay", world.getMapper(LayerMetaComponent.class).get(objectLayer).name);
        assertEquals("Above", world.getMapper(LayerMetaComponent.class).get(layerEntity(world, 2, true)).name);
        assertEquals(0, objectCountInLayer(world, 1));
    }

    @Test
    public void importSceneRoundTripsObjectLayerRectanglesPointsPropertiesAndIdentity() throws Exception {
        Harness h = harness("tmx-import-object-roundtrip");
        FileHandle tmx = writeTmx(h.root.resolve("objects.tmx"), """
                <map orientation="orthogonal" width="20" height="10" tilewidth="16" tileheight="16">
                  <group name="World" visible="0" offsetx="3" offsety="4" parallaxx="2" parallaxy="0.5">
                    <objectgroup name="Gameplay" offsetx="5" offsety="6" parallaxx="0.5" parallaxy="2">
                      <properties>
                        <property name="role" value="logic"/>
                        <property name="enabled" type="bool" value="true"/>
                        <property name="count" type="int" value="7"/>
                        <property name="weight" type="float" value="1.5"/>
                        <property name="tint" type="color" value="#40010203"/>
                      </properties>
                      <object id="777" name="Duplicate" class="Trigger" type="LegacyTrigger"
                              x="10" y="20" width="30" height="40" visible="0">
                        <properties>
                          <property name="label" value="door"/>
                          <property name="armed" type="bool" value="true"/>
                          <property name="damage" type="int" value="20"/>
                          <property name="ratio" type="float" value="2.25"/>
                          <property name="tint" type="color" value="#800A0B0C"/>
                        </properties>
                      </object>
                      <object id="777" name="Duplicate" x="4" y="5"><point/></object>
                      <object name="Zero" x="1" y="2" width="0" height="0"/>
                      <object x="2" y="3"><point/></object>
                      <object id="9" name="Deferred"><ellipse/></object>
                    </objectgroup>
                  </group>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Object Round Trip"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        int layerEntity = layerEntity(world, 0, false);
        LayerComponent layer = world.getMapper(LayerComponent.class).get(layerEntity);
        assertFalse(layer.spatialEnabled);
        assertEquals("World/Gameplay", world.getMapper(LayerMetaComponent.class).get(layerEntity).name);
        assertFalse(world.getMapper(VisibilityComponent.class).get(layerEntity).visible);
        assertEquals(1f, world.getMapper(LayerParallaxComponent.class).get(layerEntity).factorX, 0.0001f);
        assertEquals(1f, world.getMapper(LayerParallaxComponent.class).get(layerEntity).factorY, 0.0001f);
        CustomPropertiesComponent layerProperties = world.getMapper(CustomPropertiesComponent.class).get(layerEntity);
        assertEquals("logic", layerProperties.properties.getString("role", null));
        assertTrue(layerProperties.properties.getBoolean("enabled", false));
        assertEquals(7, layerProperties.properties.getInt("count", 0));
        assertEquals(1.5f, layerProperties.properties.getFloat("weight", 0f), 0.0001f);
        assertEquals(0x01020340, layerProperties.properties.getColorRgba8888("tint", 0));
        TmxObjectLayerPlan plannedLayer = (TmxObjectLayerPlan) result.planResult().plan().layers().get(0);
        assertNotSame(plannedLayer.properties(), layerProperties.properties);
        assertTrue(world.getMapper(PixscapeIdentityComponent.class).get(layerEntity).stableId > 0);

        int[] duplicates = objectEntitiesByName(world, "Duplicate");
        assertEquals(2, duplicates.length);
        int rectangle = world.getMapper(DimensionsComponent.class).has(duplicates[0])
                ? duplicates[0] : duplicates[1];
        int point = rectangle == duplicates[0] ? duplicates[1] : duplicates[0];
        assertEquals(4, objectCountInLayer(world, 0));

        TransformComponent rectangleTransform = world.getMapper(TransformComponent.class).get(rectangle);
        DimensionsComponent rectangleDimensions = world.getMapper(DimensionsComponent.class).get(rectangle);
        assertEquals(33f, rectangleTransform.x, 0.0001f);
        assertEquals(110f, rectangleTransform.y, 0.0001f);
        assertEquals(15f, rectangleTransform.originX, 0f);
        assertEquals(20f, rectangleTransform.originY, 0f);
        assertEquals(30f, rectangleDimensions.width, 0f);
        assertEquals(40f, rectangleDimensions.height, 0f);
        assertFalse(world.getMapper(VisibilityComponent.class).get(rectangle).visible);
        assertEquals(3, world.getMapper(EntityIndexComponent.class).get(rectangle).zIndex);
        assertNotNull(world.getMapper(EntityMetaComponent.class).get(rectangle));
        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(rectangle).kind);
        assertTrue(world.getMapper(AABBComponent.class).has(rectangle));
        assertTrue(world.getMapper(OrientedBoundsComponent.class).has(rectangle));
        PixscapeTagComponent rectangleTags = world.getMapper(PixscapeTagComponent.class).get(rectangle);
        assertEquals(1, rectangleTags.tags.size);
        assertEquals("Trigger", rectangleTags.tags.first());
        assertFalse(rectangleTags.tags.contains("LegacyTrigger", false));
        assertFalse(world.getMapper(AssetRefComponent.class).has(rectangle));
        assertFalse(world.getMapper(TextureRegionComponent.class).has(rectangle));
        assertFalse(world.getMapper(RenderMaterialComponent.class).has(rectangle));
        assertFalse(world.getMapper(TintComponent.class).has(rectangle));
        CustomPropertiesComponent objectProperties = world.getMapper(CustomPropertiesComponent.class).get(rectangle);
        assertEquals("door", objectProperties.properties.getString("label", null));
        assertTrue(objectProperties.properties.getBoolean("armed", false));
        assertEquals(20, objectProperties.properties.getInt("damage", 0));
        assertEquals(2.25f, objectProperties.properties.getFloat("ratio", 0f), 0.0001f);
        assertEquals(0x0A0B0C80, objectProperties.properties.getColorRgba8888("tint", 0));
        assertNotSame(layerProperties.properties, objectProperties.properties);
        assertNotSame(plannedLayer.objects().get(0).properties(), objectProperties.properties);

        TransformComponent pointTransform = world.getMapper(TransformComponent.class).get(point);
        assertEquals(12f, pointTransform.x, 0.0001f);
        assertEquals(145f, pointTransform.y, 0.0001f);
        assertFalse(world.getMapper(DimensionsComponent.class).has(point));
        assertFalse(world.getMapper(AABBComponent.class).has(point));
        assertFalse(world.getMapper(OrientedBoundsComponent.class).has(point));
        assertEquals(EntityKind.TILED_POINT,
                world.getMapper(EntityMetaComponent.class).get(point).kind);
        assertTrue(world.getMapper(VisibilityComponent.class).get(point).visible);
        assertEquals(2, world.getMapper(EntityIndexComponent.class).get(point).zIndex);

        int zero = objectEntityByName(world, "Zero");
        DimensionsComponent zeroDimensions = world.getMapper(DimensionsComponent.class).get(zero);
        assertEquals(0f, zeroDimensions.width, 0f);
        assertEquals(0f, zeroDimensions.height, 0f);
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(zero).zIndex);
        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(zero).kind);

        int unnamed = objectEntityByName(world, "unnamed");
        assertFalse(world.getMapper(DimensionsComponent.class).has(unnamed));
        assertEquals(1, world.getMapper(EntityIndexComponent.class).get(unnamed).zIndex);

        PixscapeIdentityComponent rectangleIdentity = world.getMapper(PixscapeIdentityComponent.class).get(rectangle);
        PixscapeIdentityComponent pointIdentity = world.getMapper(PixscapeIdentityComponent.class).get(point);
        PixscapeIdentityComponent zeroIdentity = world.getMapper(PixscapeIdentityComponent.class).get(zero);
        PixscapeIdentityComponent unnamedIdentity = world.getMapper(PixscapeIdentityComponent.class).get(unnamed);
        assertTrue(rectangleIdentity.stableId > 0);
        assertTrue(pointIdentity.stableId > 0);
        assertTrue(zeroIdentity.stableId > 0);
        assertTrue(unnamedIdentity.stableId > 0);
        assertNotEquals(777, rectangleIdentity.stableId);
        assertNotEquals(rectangleIdentity.stableId, pointIdentity.stableId);
        assertNotEquals(pointIdentity.stableId, zeroIdentity.stableId);
        assertNotEquals(zeroIdentity.stableId, unnamedIdentity.stableId);
        assertEquals("Duplicate", rectangleIdentity.name);
        assertEquals("Duplicate", pointIdentity.name);
        assertEquals("Zero", zeroIdentity.name);
        assertEquals("unnamed", unnamedIdentity.name);

        TagRegistry registry = new TagRegistry();
        registry.bind(world);
        registry.rebuild();
        assertTrue(registry.hasTag(rectangle, "Trigger"));
        assertEquals(rectangle, registry.first("Trigger"));
        assertEquals(1, registry.get("Trigger").size);
        assertEquals(20, objectProperties.properties.getInt("damage", 0));
        assertFalse(objectProperties.properties.contains("class"));
        assertFalse(objectProperties.properties.contains("type"));
    }

    @Test
    public void importedRectangleUsesNormalBoundsAndGizmoScaleHistory() throws Exception {
        Harness h = harness("tmx-import-rectangle-geometry");
        FileHandle tmx = writeTmx(h.root.resolve("rectangle-geometry.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Gameplay">
                    <object name="Rectangle" x="10" y="20" width="30" height="40" rotation="90"/>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Rectangle Geometry"));
        World world = loadImportedWorldWithGeometry(h, result);
        int rectangle = objectEntityByName(world, "Rectangle");
        TransformComponent transform = world.getMapper(TransformComponent.class).get(rectangle);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(rectangle);
        AABBComponent aabb = world.getMapper(AABBComponent.class).get(rectangle);
        OrientedBoundsComponent obb = world.getMapper(OrientedBoundsComponent.class).get(rectangle);

        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(rectangle).kind);
        assertEquals(-10f, transform.x, 0.0001f);
        assertEquals(125f, transform.y, 0.0001f);
        assertEquals(15f, transform.originX, 0.0001f);
        assertEquals(20f, transform.originY, 0.0001f);
        float[] importedCorners = new float[8];
        OrientedBoundsHelper.toCorners(obb, importedCorners);
        assertSameCornerSet(tiledSourceCorners(10f, 140f, 30f, 40f, -MathUtils.PI / 2f),
                importedCorners);
        assertEquals(-30f, aabb.minX, 0.0001f);
        assertEquals(10f, aabb.maxX, 0.0001f);
        assertEquals(110f, aabb.minY, 0.0001f);
        assertEquals(140f, aabb.maxY, 0.0001f);
        assertTrue(OrientedBoundsHelper.contains(obb, -10f, 125f));
        assertFalse(OrientedBoundsHelper.contains(obb, 15f, 125f));

        HistoryManager history = new HistoryManager(8);
        long historyId = history.historyIds().ensureForEntity(rectangle);
        GizmoTransformCommand scale = new GizmoTransformCommand(
                world, history.historyIds(), TransformOp.SCALE);
        scale.addEntry(historyId, GizmoTransformCommand.Snapshot.of(transform),
                new GizmoTransformCommand.Snapshot(
                        transform.x, transform.y, transform.rotationRad,
                        1.5f, 0.5f, transform.originX, transform.originY));
        history.execute(scale);
        world.process();

        assertEquals(1.5f, transform.scaleX, 0f);
        assertEquals(0.5f, transform.scaleY, 0f);
        assertEquals(30f, dimensions.width, 0f);
        assertEquals(40f, dimensions.height, 0f);
        assertEquals(-20f, aabb.minX, 0.0001f);
        assertEquals(0f, aabb.maxX, 0.0001f);
        assertEquals(102.5f, aabb.minY, 0.0001f);
        assertEquals(147.5f, aabb.maxY, 0.0001f);

        history.undo();
        world.process();
        assertEquals(1f, transform.scaleX, 0f);
        assertEquals(1f, transform.scaleY, 0f);
        assertEquals(-30f, aabb.minX, 0.0001f);
        assertEquals(10f, aabb.maxX, 0.0001f);

        history.redo();
        world.process();
        assertEquals(1.5f, transform.scaleX, 0f);
        assertEquals(0.5f, transform.scaleY, 0f);
    }

    @Test
    public void centeredRectanglePivotPreservesTiledSourceGeometry() {
        assertCenteredRectangleGeometry(10f, 140f, 30f, 40f, 0f);
        assertCenteredRectangleGeometry(18f, 130f, 30f, 40f, 0.65f);
        assertCenteredRectangleGeometry(-3f, 90f, 17f, 9f, -1.1f);
        assertCenteredRectangleGeometry(18f, 130f, 0f, 40f, 0.65f);
        assertCenteredRectangleGeometry(18f, 130f, 30f, 0f, -1.1f);
        assertCenteredRectangleGeometry(18f, 130f, 0f, 0f, 0.65f);
    }

    @Test
    public void classPropertiesSurviveMaterializationSceneSaveReloadAndRuntimeReadback() throws Exception {
        Harness h = harness("tmx-import-class-round-trip");
        FileHandle tmx = writeTmx(h.root.resolve("class-properties.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="actors" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0"><properties><property name="physics" type="class" propertytype="Physics"><properties>
                      <property name="mass" type="float" value="1"/>
                      <property name="sensor" type="bool" value="false"/>
                    </properties></property></properties></tile>
                  </tileset>
                  <objectgroup name="Gameplay"><properties>
                    <property name="settings" type="class" propertytype="LayerSettings"><properties>
                      <property name="enabled" type="bool" value="true"/>
                    </properties></property>
                  </properties>
                    <object name="Rectangle" width="4" height="5"><properties>
                      <property name="attack" type="class" propertytype="Attack"><properties>
                        <property name="damage" type="int" value="20"/>
                      </properties></property>
                    </properties></object>
                    <object name="Point"><point/><properties>
                      <property name="follow" type="class" propertytype="Follow"/>
                    </properties></object>
                    <object name="Tile" gid="1"><properties>
                      <property name="physics" type="class" propertytype="Physics"><properties>
                        <property name="mass" type="float" value="2"/>
                        <property name="sensor" type="bool" value="true"/>
                      </properties></property>
                    </properties></object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Class Round Trip"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        ClassProperty settings = world.getMapper(CustomPropertiesComponent.class)
                .get(layerEntity(world, 0, false)).properties.getClassValue("settings");
        assertEquals("LayerSettings", settings.typeName());
        assertTrue(settings.properties().getBoolean("enabled", false));

        ClassProperty attack = world.getMapper(CustomPropertiesComponent.class)
                .get(objectEntityByName(world, "Rectangle")).properties.getClassValue("attack");
        assertEquals("Attack", attack.typeName());
        assertEquals(20, attack.properties().getInt("damage", 0));
        assertFalse(world.getMapper(PixscapeTagComponent.class)
                .has(objectEntityByName(world, "Rectangle")));

        ClassProperty follow = world.getMapper(CustomPropertiesComponent.class)
                .get(objectEntityByName(world, "Point")).properties.getClassValue("follow");
        assertEquals("Follow", follow.typeName());
        assertTrue(follow.properties().isEmpty());

        ClassProperty physics = world.getMapper(CustomPropertiesComponent.class)
                .get(visualEntityByName(world, "Tile")).properties.getClassValue("physics");
        assertEquals("Physics", physics.typeName());
        assertEquals(2f, physics.properties().getFloat("mass", 0f), 0.0001f);
        assertTrue(physics.properties().getBoolean("sensor", false));
    }

    @Test
    public void importSceneMapsModernAndLegacyClassificationToOneNormalizedTag() throws Exception {
        Harness h = harness("tmx-import-object-tags");
        FileHandle tmx = writeTmx(h.root.resolve("object-tags.tmx"), """
                <map orientation="orthogonal" width="4" height="4" tilewidth="16" tileheight="16">
                  <objectgroup name="Classified">
                    <object id="99" name="ClassOnly" class="Enemy" width="2" height="3"/>
                    <object id="99" name="TypeOnly" type="Legacy Enemy"><point/></object>
                    <object name="Equal" class="Same" type="Same"><point/></object>
                    <object name="Conflict" class="Modern" type="Legacy"><point/></object>
                    <object name="BlankClass" class="   " type="Fallback"><point/></object>
                    <object name="None"><point/></object>
                    <object name="InternalSpace" class="  Boss Enemy.v2  "><point/></object>
                    <object name="LowerCase" class="enemy"><point/></object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Object Tags"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        assertSingleTag(world, "ClassOnly", "Enemy");
        assertSingleTag(world, "TypeOnly", "Legacy Enemy");
        assertSingleTag(world, "Equal", "Same");
        assertSingleTag(world, "Conflict", "Modern");
        assertSingleTag(world, "BlankClass", "Fallback");
        assertSingleTag(world, "InternalSpace", "Boss Enemy.v2");
        assertSingleTag(world, "LowerCase", "enemy");
        assertFalse(world.getMapper(PixscapeTagComponent.class).has(objectEntityByName(world, "None")));
        assertFalse(world.getMapper(PixscapeTagComponent.class).has(layerEntity(world, 0, false)));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> "TMX_OBJECT_CLASS_TYPE_CONFLICT".equals(diagnostic.code())));

        TagRegistry registry = new TagRegistry();
        registry.bind(world);
        registry.rebuild();
        int classOnly = objectEntityByName(world, "ClassOnly");
        int typeOnly = objectEntityByName(world, "TypeOnly");
        int conflict = objectEntityByName(world, "Conflict");
        int internalSpace = objectEntityByName(world, "InternalSpace");
        int lowerCase = objectEntityByName(world, "LowerCase");
        assertTrue(registry.hasTag(classOnly, " Enemy "));
        assertTrue(registry.hasTag(typeOnly, "Legacy Enemy"));
        assertTrue(registry.hasTag(conflict, "Modern"));
        assertFalse(registry.hasTag(conflict, "Legacy"));
        assertTrue(registry.hasTag(internalSpace, "  Boss Enemy.v2  "));
        assertTrue(registry.hasTag(lowerCase, "enemy"));
        assertFalse(registry.hasTag(lowerCase, "Enemy"));
        assertEquals(1, registry.get("Enemy").size);
        assertEquals(1, registry.get("enemy").size);
    }

    @Test
    public void importScenePreservesTiledRectanglePivotAndRotatedCorners() throws Exception {
        Harness h = harness("tmx-import-object-rotation");
        FileHandle tmx = writeTmx(h.root.resolve("rotations.tmx"), """
                <map orientation="orthogonal" width="20" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Geometry">
                    <object id="1" name="Zero" x="10" y="20" width="30" height="40" rotation="0"/>
                    <object id="2" name="Clockwise" x="10" y="20" width="30" height="40" rotation="90"/>
                    <object id="3" name="CounterClockwise" x="10" y="20" width="30" height="40" rotation="-90"/>
                  </objectgroup>
                </map>
                """);

        World world = loadImportedWorld(h,
                h.importer().importScene(request(tmx, "Rotated Objects")));

        assertCorners(world, objectEntityByName(world, "Zero"), new float[]{
                10f, 140f, 40f, 140f, 40f, 100f, 10f, 100f
        });
        assertCorners(world, objectEntityByName(world, "Clockwise"), new float[]{
                10f, 140f, 10f, 110f, -30f, 110f, -30f, 140f
        });
        assertCorners(world, objectEntityByName(world, "CounterClockwise"), new float[]{
                10f, 140f, 10f, 170f, 50f, 170f, 50f, 140f
        });
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
    public void importAssetsRetainsSeparateStaticCellAndAnimationMappings() throws Exception {
        Harness h = harness("tmx-import-animation-mappings");
        FileHandle tmx = animatedTileTmx(h, "animated-mappings.tmx", "1,2,0,0");
        TmxSceneImportService importer = h.importer();
        TmxImportPlanResult planned = importer.plan(request(tmx, "Mappings"));
        SceneMeta meta = new SceneMeta("Mappings", "mappings.json");
        importer.initializeSceneRuntimeAvailability(meta);

        TmxSceneImportService.ImportAssetsResult assets = importer.importAssets(planned.plan(), meta);
        int tilesetIndex = planned.plan().tilesets().get(0).planIndex();
        int staticBaseAssetId = requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id();
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
        int logicalAnimationId = animations.animations.get(0).id;

        assertEquals(1, animations.animations.size);
        assertEquals(Integer.valueOf(logicalAnimationId),
                assets.animationIdsByTileset().get(tilesetIndex).get(0));
        assertEquals(Integer.valueOf(logicalAnimationId),
                assets.cellLogicalIdsByTileset().get(tilesetIndex).get(0));
        assertEquals(Integer.valueOf(staticBaseAssetId),
                assets.staticTileAssetIdsByTileset().get(tilesetIndex).get(0));
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
    public void importSceneReusesOneLogicalAnimationForTileLayerAndTileObject() throws Exception {
        Harness h = harness("tmx-import-shared-animation-consumers");
        FileHandle tmx = writeTmx(h.root.resolve("shared-animation.tmx"), """
                <map orientation="orthogonal" width="2" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0"><animation>
                      <frame tileid="0" duration="90"/>
                      <frame tileid="1" duration="140"/>
                    </animation></tile>
                  </tileset>
                  <layer name="Ground" width="2" height="1"><data encoding="csv">1,0</data></layer>
                  <objectgroup name="Actors"><object name="SharedAnimated" gid="1" x="8" y="16"/></objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Shared Animation"));
        World world = loadImportedWorldWithGeometry(h, result);
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));

        assertTrue(result.imported());
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertEquals(90, def.frameDurationsMs[0]);
        assertEquals(140, def.frameDurationsMs[1]);
        assertTileAsset(firstTiled(world), 0, 0, def.id);

        int object = visualEntityByName(world, "SharedAnimated");
        assertEquals(def.id,
                world.getMapper(TiledAnimationComponent.class).get(object).animationId);
        assertEquals(requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id(),
                world.getMapper(AssetRefComponent.class).get(object).assetId);
        assertTrue(h.cfg.getSceneMeta(result.sceneName()).runtimeAvailability
                .tiledAnimationIds.contains(def.id));
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

    @Test
    public void tileObjectMaterializationFailureRollsBackPartialSceneAssetsAndRestoresCurrentScene() throws Exception {
        Harness h = harness("tmx-import-object-rollback");
        FileHandle tmx = writeTmx(h.root.resolve("object-failure.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="objects" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <objectgroup name="Gameplay"><object id="1" name="Spawn" gid="1"/></objectgroup>
                </map>
                """);
        TmxSceneImportSession session = h.importer().beginImport(request(tmx, "Broken Objects"));
        session.prepare();
        session.createScene();
        session.importAssets();
        h.cfg.getSceneMeta("Broken Objects").nextEntityStableId = Integer.MAX_VALUE - 1;

        RuntimeException materializeFailure = null;
        try {
            session.materializeAndSaveScene();
        } catch (RuntimeException failure) {
            materializeFailure = failure;
        }

        assertNotNull(materializeFailure);
        assertFalse(session.hasTemporaryWorld());
        TmxSceneImportResult rollback = session.rollback(materializeFailure);
        assertEquals(TmxSceneImportStatus.FAILED_ROLLED_BACK, rollback.status());
        assertTrue(rollback.rollbackSucceeded());
        assertEquals("Main", h.cfg.getCurrentSceneName());
        assertNull(h.cfg.getSceneMeta("Broken Objects"));
        assertFalse(h.projectDir.child(StudioFs.DIR_SCENES).child("scene2.json").exists());
        assertEquals(0, h.db.size());
        assertEquals(0, h.projectDir.child(StudioFs.DIR_ORIG_TILES).list().length);
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

    @Test
    public void importSceneMaterializesStaticTileObjectsWithExactSpriteMetadataAndTransforms() throws Exception {
        Harness h = harness("tmx-import-static-tile-objects");
        writePng(h.projectDir.child("center.png"), 32, 16);
        writeString(h.projectDir.child("center.tsx"), """
                <tileset name="center" tilewidth="16" tileheight="16" tilecount="2" columns="2"
                         objectalignment="center">
                  <tileoffset x="2" y="3"/>
                  <image source="center.png" width="32" height="16"/>
                  <tile id="0" class="House"><properties>
                    <property name="tile_speed" type="float" value="0.6"/>
                  </properties></tile>
                </tileset>
                """);
        FileHandle tmx = writeTmx(h.root.resolve("tile-objects.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="inline" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="1" class="TileGem"><properties>
                      <property name="tile_label" value="inline"/>
                      <property name="collectible" type="bool" value="true"/>
                      <property name="damage" type="int" value="10"/>
                      <property name="animation_speed" type="float" value="0.5"/>
                    </properties></tile>
                  </tileset>
                  <tileset firstgid="10" source="center.tsx"/>
                  <layer name="Ground" width="10" height="10"><data encoding="csv">2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0</data></layer>
                  <group opacity="0.5" offsetx="1" offsety="2">
                    <objectgroup name="Actors" opacity="0.5" offsetx="2" offsety="2" draworder="topdown" visible="0">
                      <object id="7" name="Shared" class="gem" type="legacy" gid="2"
                              x="10" y="20" width="32" height="24" visible="0">
                        <properties>
                          <property name="animation_speed" type="float" value="0.6"/>
                          <property name="damage" type="int" value="25"/>
                          <property name="instance" value="yes"/>
                        </properties>
                      </object>
                      <object name="BottomNegative" gid="2" x="20" y="30" rotation="-90"/>
                      <object id="7" name="CenterRotated" gid="10" x="50" y="40" rotation="90"/>
                      <object name="HFlip" gid="2147483649" x="70" y="60"/>
                      <object name="VFlip" gid="1073741825" x="80" y="70"/>
                      <object name="HVFlip" gid="3221225473" x="90" y="80"/>
                      <object name="DiagonalRotated" gid="536870913" x="95" y="82"
                              width="34" height="18" rotation="30"/>
                      <object name="CleanHexPeer" gid="1" x="105" y="84"/>
                      <object name="IgnoredHex" gid="268435457" x="105" y="84"/>
                      <object name="DuplicateTile" gid="1" x="100" y="85"/>
                      <object name="DuplicateTile" gid="1" x="110" y="90"/>
                    </objectgroup>
                  </group>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Tile Objects"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        int layer = layerEntity(world, 1, false);
        assertFalse(world.getMapper(VisibilityComponent.class).get(layer).visible);
        assertEquals(11, drawableCountInLayer(world, 1));
        assertEquals(2, visualEntityCountByName(world, "DuplicateTile"));

        int shared = visualEntityByName(world, "Shared");
        TransformComponent sharedTransform = world.getMapper(TransformComponent.class).get(shared);
        DimensionsComponent sharedDimensions = world.getMapper(DimensionsComponent.class).get(shared);
        AssetRefComponent sharedAsset = world.getMapper(AssetRefComponent.class).get(shared);
        assertEquals(13f, sharedTransform.x, 0.0001f);
        assertEquals(136f, sharedTransform.y, 0.0001f);
        assertEquals(0f, sharedTransform.originX, 0.0001f);
        assertEquals(0f, sharedTransform.originY, 0.0001f);
        assertEquals(32f, sharedDimensions.width, 0.0001f);
        assertEquals(24f, sharedDimensions.height, 0.0001f);
        assertFalse(world.getMapper(VisibilityComponent.class).get(shared).visible);
        assertEquals(0x40FFFFFF, world.getMapper(TintComponent.class).get(shared).getRgba());
        assertEquals("gem", world.getMapper(PixscapeTagComponent.class).get(shared).tags.first());
        assertEquals(0.6f, world.getMapper(CustomPropertiesComponent.class).get(shared)
                .properties.getFloat("animation_speed", 0f), 0.0001f);
        CustomPropertiesComponent sharedProperties = world.getMapper(CustomPropertiesComponent.class).get(shared);
        assertEquals("inline", sharedProperties.properties.getString("tile_label", null));
        assertTrue(sharedProperties.properties.getBoolean("collectible", false));
        assertEquals(25, sharedProperties.properties.getInt("damage", 0));
        assertEquals("yes", sharedProperties.properties.getString("instance", null));
        assertTrue(world.getMapper(PixscapeIdentityComponent.class).get(shared).stableId > 0);
        assertTrue(world.getMapper(TextureRegionComponent.class).has(shared));
        assertTrue(world.getMapper(RenderMaterialComponent.class).has(shared));
        assertFalse(world.getMapper(AnimationComponent.class).has(shared));
        assertFalse(world.getMapper(TiledAnimationComponent.class).has(shared));
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(shared).kind);
        assertEquals(firstTiled(world).tileAssetIds.get(0), sharedAsset.assetId);
        assertTrue(h.cfg.getSceneMeta(result.sceneName()).runtimeAvailability.spriteAssetIds
                .contains(sharedAsset.assetId));

        int center = visualEntityByName(world, "CenterRotated");
        TransformComponent centerTransform = world.getMapper(TransformComponent.class).get(center);
        assertEquals(53f, centerTransform.x, 0.0001f);
        assertEquals(116f, centerTransform.y, 0.0001f);
        assertEquals(6f, centerTransform.originX, 0.0001f);
        assertEquals(11f, centerTransform.originY, 0.0001f);
        assertEquals(-MathUtils.PI / 2f, centerTransform.rotationRad, 0.0001f);
        assertTransformedCorners(world, center, new float[]{58, 122, 58, 106, 42, 106, 42, 122});
        assertEquals("House", world.getMapper(PixscapeTagComponent.class).get(center).tags.first());
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(center).kind);
        assertEquals(0.6f, world.getMapper(CustomPropertiesComponent.class).get(center)
                .properties.getFloat("tile_speed", 0f), 0.0001f);

        int bottomNegative = visualEntityByName(world, "BottomNegative");
        assertEquals(MathUtils.PI / 2f,
                world.getMapper(TransformComponent.class).get(bottomNegative).rotationRad, 0.0001f);
        assertTransformedCorners(world, bottomNegative,
                new float[]{7, 126, 7, 142, 23, 142, 23, 126});

        assertFlip(world, "HFlip", -1f, 1f, 16f, 0f);
        assertFlip(world, "VFlip", 1f, -1f, 0f, 16f);
        assertFlip(world, "HVFlip", -1f, -1f, 16f, 16f);
        assertTransformedBounds(world, visualEntityByName(world, "HFlip"), 73, 96, 89, 112);
        assertTransformedBounds(world, visualEntityByName(world, "VFlip"), 83, 86, 99, 102);
        assertTransformedBounds(world, visualEntityByName(world, "HVFlip"), 93, 76, 109, 92);
        int diagonal = visualEntityByName(world, "DiagonalRotated");
        assertEquals(98f, world.getMapper(TransformComponent.class).get(diagonal).x, 0.0001f);
        assertEquals(74f, world.getMapper(TransformComponent.class).get(diagonal).y, 0.0001f);
        assertEquals(34f, world.getMapper(DimensionsComponent.class).get(diagonal).width, 0.0001f);
        assertEquals(18f, world.getMapper(DimensionsComponent.class).get(diagonal).height, 0.0001f);
        assertEquivalentSpriteTransform(world,
                visualEntityByName(world, "CleanHexPeer"), visualEntityByName(world, "IgnoredHex"));
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(shared).zIndex);
        assertEquals(5, world.getMapper(EntityIndexComponent.class).get(visualEntityByName(world, "HVFlip")).zIndex);
    }

    @Test
    public void importSceneMaterializesSharedAnimatedTileObjectsWithoutTileLayerConsumer() throws Exception {
        Harness h = harness("tmx-import-isolated-animated-tile-objects");
        FileHandle tmx = animatedTileObjectTmx(h, "animated-objects.tmx");
        TmxSceneImportRequest request = new TmxSceneImportRequest(tmx, "Animated Objects", true);

        TmxSceneImportResult result = h.importer().importScene(request);
        World world = loadImportedWorldWithGeometry(h, result);
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
        int staticBaseAssetId = requireTile(h.db.findByLogicalPath("tiles/terrain/0")).id();

        assertTrue(result.imported());
        assertTrue(result.planResult().preflightReport().isImportableCandidate());
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertEquals(100, def.frameDurationsMs[0]);
        assertEquals(150, def.frameDurationsMs[1]);

        int first = visualEntityByName(world, "AnimatedA");
        int second = visualEntityByName(world, "AnimatedB");
        int staticPeer = visualEntityByName(world, "StaticPeer");
        TiledAnimationComponent firstAnimation = world.getMapper(TiledAnimationComponent.class).get(first);
        TiledAnimationComponent secondAnimation = world.getMapper(TiledAnimationComponent.class).get(second);

        assertNotNull(firstAnimation);
        assertNotNull(secondAnimation);
        assertEquals(def.id, firstAnimation.animationId);
        assertEquals(def.id, secondAnimation.animationId);
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(first).kind);
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(second).kind);
        assertEquals(0, firstAnimation.frameIndex);
        assertEquals(0, firstAnimation.frameElapsedMs);
        assertEquals(-1, firstAnimation.appliedFrameAssetId);
        assertEquals(0, secondAnimation.frameIndex);
        assertEquals(0, secondAnimation.frameElapsedMs);
        assertEquals(-1, secondAnimation.appliedFrameAssetId);
        assertEquals(staticBaseAssetId, world.getMapper(AssetRefComponent.class).get(first).assetId);
        assertEquals(staticBaseAssetId, world.getMapper(AssetRefComponent.class).get(second).assetId);
        assertFalse(world.getMapper(TiledAnimationComponent.class).has(staticPeer));

        assertTrue(world.getMapper(TextureRegionComponent.class).has(first));
        assertTrue(world.getMapper(RenderMaterialComponent.class).has(first));
        assertTrue(world.getMapper(VisibilityComponent.class).has(first));
        assertTrue(world.getMapper(PixscapeIdentityComponent.class).has(first));
        assertTrue(world.getMapper(EntityMetaComponent.class).has(first));
        assertTrue(world.getMapper(EntityIndexComponent.class).has(first));
        assertEquals("enemy", world.getMapper(PixscapeTagComponent.class).get(first).tags.first());
        assertEquals(5, world.getMapper(CustomPropertiesComponent.class).get(first)
                .properties.getInt("speed", 0));
        assertEquivalentTransformAndDimensions(world, first, staticPeer);
        assertFlip(world, "AnimatedB", -1f, 1f, 16f, 0f);

        SceneMeta sceneMeta = h.cfg.getSceneMeta(result.sceneName());
        assertTrue(sceneMeta.runtimeAvailability.tiledAnimationIds.contains(def.id));
        for (int frameAssetId : def.frameAssetIds) {
            assertTrue(sceneMeta.runtimeAvailability.tiledTileAssetIds.contains(frameAssetId));
            AssetMeta frameAsset = h.db.findById(frameAssetId);
            assertNotNull(frameAsset);
            assertTrue(h.projectDir.child(StudioFs.DIR_ATLASES)
                    .child(StudioFs.DIR_INPUT)
                    .child(result.sceneTag())
                    .child(new FileHandle(frameAsset.sourceRelPath()).name())
                    .exists());
        }
        assertTrue(h.projectDir.child(StudioFs.DIR_ATLASES)
                .child(StudioFs.withExt(result.sceneTag(), StudioFs.EXT_ATLAS)).exists());

        FileHandle sceneFile = h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName());
        String sceneJson = sceneFile.readString(StandardCharsets.UTF_8.name());
        assertTrue(sceneJson.contains("animationId"));
        assertFalse(sceneJson.contains("frameIndex"));
        assertFalse(sceneJson.contains("frameElapsedMs"));
        assertFalse(sceneJson.contains("appliedFrameAssetId"));

        FileHandle exportDir = new FileHandle(h.root.resolve("isolated-export").toFile());
        RuntimeExport.exportRuntime(h.cfg, h.projectDir, exportDir);
        JsonValue profiles = new JsonReader().parse(exportDir.child(RuntimeExport.RUNTIME_DIR_NAME)
                .child(RuntimeFs.FILE_TILESET_PROFILES_JSON));
        JsonValue tileIds = profiles.get("tilesets").get(0).get("tileAssetIds");
        for (int frameAssetId : def.frameAssetIds) {
            assertTrue(containsJsonInt(tileIds, frameAssetId));
        }
    }

    @Test
    public void importSceneSupportsAnimatedTileObjectFromExternalImageCollectionTileset() throws Exception {
        Harness h = harness("tmx-import-animated-collection-object");
        writePng(h.projectDir.child("collection-0.png"), 16, 16);
        writePng(h.projectDir.child("collection-1.png"), 16, 16);
        writeString(h.projectDir.child("animated-collection.tsx"), """
                <tileset name="collection" tilewidth="16" tileheight="16" tilecount="2" columns="0">
                  <tile id="0">
                    <image source="collection-0.png" width="16" height="16"/>
                    <animation>
                      <frame tileid="0" duration="80"/>
                      <frame tileid="1" duration="120"/>
                    </animation>
                  </tile>
                  <tile id="1"><image source="collection-1.png" width="16" height="16"/></tile>
                </tileset>
                """);
        FileHandle tmx = writeTmx(h.root.resolve("animated-collection-object.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="5" source="animated-collection.tsx"/>
                  <objectgroup name="Actors"><object name="CollectionAnimated" gid="5" x="8" y="16"/></objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Animated Collection"));
        World world = loadImportedWorld(h, result);
        int entity = visualEntityByName(world, "CollectionAnimated");
        TileAnimationsMetaDatabase animations = TileAnimationsIO.load(
                h.projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON));

        assertTrue(result.imported());
        assertEquals(1, animations.animations.size);
        TileAnimationProjectDefData def = animations.animations.get(0);
        assertEquals(def.id, world.getMapper(TiledAnimationComponent.class).get(entity).animationId);
        assertEquals(def.frameAssetIds[0], world.getMapper(AssetRefComponent.class).get(entity).assetId);
        assertEquals(80, def.frameDurationsMs[0]);
        assertEquals(120, def.frameDurationsMs[1]);
    }

    @Test
    public void importSceneUsesActualImageCollectionTileAssetAndNativeSize() throws Exception {
        Harness h = harness("tmx-import-image-collection-object");
        writePng(h.projectDir.child("tree.png"), 23, 29);
        FileHandle tmx = writeTmx(h.root.resolve("collection-object.tmx"), """
                <map orientation="orthogonal" width="2" height="2" tilewidth="16" tileheight="16">
                  <tileset firstgid="5" name="collection" tilewidth="16" tileheight="16" tilecount="3" columns="0"
                           objectalignment="topleft">
                    <tile id="2" class="Tree"><image source="tree.png" width="23" height="29"/>
                      <properties><property name="solid" type="bool" value="true"/></properties>
                    </tile>
                  </tileset>
                  <objectgroup name="Props"><object name="Tree" gid="7" x="8" y="9" rotation="-90"/></objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Collection Object"));
        World world = loadImportedWorld(h, result);
        int tree = visualEntityByName(world, "Tree");

        assertTrue(result.imported());
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(tree);
        TransformComponent transform = world.getMapper(TransformComponent.class).get(tree);
        assertEquals(23f, dimensions.width, 0.0001f);
        assertEquals(29f, dimensions.height, 0.0001f);
        assertEquals(0f, transform.originX, 0.0001f);
        assertEquals(29f, transform.originY, 0.0001f);
        assertEquals(MathUtils.PI / 2f, transform.rotationRad, 0.0001f);
        assertTransformedCorners(world, tree, new float[]{8, 23, 8, 46, 37, 46, 37, 23});
        AssetMeta asset = h.db.findById(world.getMapper(AssetRefComponent.class).get(tree).assetId);
        assertNotNull(asset);
        assertTrue(h.projectDir.child(asset.sourceRelPath()).exists());
        assertEquals("Tree", world.getMapper(PixscapeTagComponent.class).get(tree).tags.first());
        assertTrue(world.getMapper(CustomPropertiesComponent.class).get(tree)
                .properties.getBoolean("solid", false));
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

    private static FileHandle animatedTileObjectTmx(Harness h, String name) throws Exception {
        long hFlipAnimated = TmxGidSupport.FLIPPED_HORIZONTALLY_FLAG | 1L;
        return writeTmx(h.root.resolve(name), """
                <map orientation="orthogonal" width="4" height="4" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="4" columns="2">
                    <image source="terrain.png" width="32" height="32"/>
                    <tile id="0" class="SourceEnemy">
                      <properties><property name="speed" type="int" value="3"/></properties>
                      <animation>
                        <frame tileid="1" duration="100"/>
                        <frame tileid="2" duration="150"/>
                      </animation>
                    </tile>
                  </tileset>
                  <objectgroup name="Actors">
                    <object id="1" name="AnimatedA" class="enemy" gid="1" x="10" y="20">
                      <properties><property name="speed" type="int" value="5"/></properties>
                    </object>
                    <object id="2" name="AnimatedB" gid="%d" x="30" y="20"/>
                    <object id="3" name="StaticPeer" gid="2" x="10" y="20"/>
                  </objectgroup>
                </map>
                """.formatted(hFlipAnimated));
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

    private static World loadImportedWorldWithGeometry(Harness h, TmxSceneImportResult result) {
        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(64),
                        new UpdateWorldGeometrySystem())
                .build());
        SceneLoader.loadScene(
                world,
                h.projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName()),
                false,
                h.cfg.getSceneMeta(result.sceneName()));
        world.process();
        SceneLoader.forceFullRenderDirty(world);
        world.process();
        return world;
    }

    private static void assertCenteredRectangleGeometry(float sourceX,
                                                        float sourceY,
                                                        float width,
                                                        float height,
                                                        float rotationRad) {
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64), new UpdateWorldGeometrySystem())
                .build());
        try {
            int entity = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
            transform.x = sourceX;
            transform.y = sourceY;
            transform.rotationRad = rotationRad;
            transform.scaleX = 1f;
            transform.scaleY = 1f;
            TmxSceneImportService.centerRectangleTransformFromTiledPivot(transform, width, height);
            transform.refreshCaches();
            DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
            dimensions.width = width;
            dimensions.height = height;
            world.getMapper(AABBComponent.class).create(entity);
            OrientedBoundsComponent bounds = world.getMapper(OrientedBoundsComponent.class).create(entity);

            world.process();
            world.getSystem(DirtyTrackerSystem.class).geometry(entity, GeometryDirty.ALL);
            world.process();

            float[] expected = tiledSourceCorners(sourceX, sourceY, width, height, rotationRad);
            float[] actual = new float[8];
            OrientedBoundsHelper.toCorners(bounds, actual);
            assertSameCornerSet(expected, actual);

            AABBComponent aabb = world.getMapper(AABBComponent.class).get(entity);
            for (int i = 0; i < expected.length; i += 2) {
                assertTrue(expected[i] >= aabb.minX - 0.0001f);
                assertTrue(expected[i] <= aabb.maxX + 0.0001f);
                assertTrue(expected[i + 1] >= aabb.minY - 0.0001f);
                assertTrue(expected[i + 1] <= aabb.maxY + 0.0001f);
            }
        } finally {
            world.dispose();
        }
    }

    private static float[] tiledSourceCorners(float sourceX,
                                              float sourceY,
                                              float width,
                                              float height,
                                              float rotationRad) {
        float cos = MathUtils.cos(rotationRad);
        float sin = MathUtils.sin(rotationRad);
        float[] local = {0f, 0f, width, 0f, width, -height, 0f, -height};
        float[] corners = new float[8];
        for (int i = 0; i < local.length; i += 2) {
            corners[i] = sourceX + cos * local[i] - sin * local[i + 1];
            corners[i + 1] = sourceY + sin * local[i] + cos * local[i + 1];
        }
        return corners;
    }

    private static void assertSameCornerSet(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i += 2) {
            boolean found = false;
            for (int j = 0; j < actual.length; j += 2) {
                if (Math.abs(expected[i] - actual[j]) <= 0.0001f
                        && Math.abs(expected[i + 1] - actual[j + 1]) <= 0.0001f) {
                    found = true;
                    break;
                }
            }
            assertTrue("Missing expected corner (" + expected[i] + ", " + expected[i + 1] + ")", found);
        }
    }

    private static TiledLayerComponent firstTiled(World world) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();
        assertEquals(1, entities.size());
        return world.getMapper(TiledLayerComponent.class).get(entities.get(0));
    }

    private static int layerEntity(World world, int index, boolean requireMap) {
        ComponentMapper<LayerComponent> layers = world.getMapper(LayerComponent.class);
        Aspect.Builder aspect = Aspect.all(LayerComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(aspect)
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (layers.get(entity).layerIndex == index) {
                if (requireMap) {
                    tiledMapEntity(world, index);
                }
                return entity;
            }
        }
        throw new AssertionError("Missing layer index " + index);
    }

    private static int tiledMapEntity(World world, int layerIndex) {
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)
                        .exclude(LayerComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (indexes.get(entity).layerIndex == layerIndex) return entity;
        }
        throw new AssertionError("Missing Tiled map in layer index " + layerIndex);
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

    private static int objectCountInLayer(World world, int layerIndex) {
        int count = 0;
        ComponentMapper<EntityIndexComponent> indices = world.getMapper(EntityIndexComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TransformComponent.class,
                        PixscapeIdentityComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (indices.get(entity).layerIndex == layerIndex
                    && !world.getMapper(AssetRefComponent.class).has(entity)) {
                count++;
            }
        }
        return count;
    }

    private static int objectEntityByName(World world, String name) {
        int[] entities = objectEntitiesByName(world, name);
        if (entities.length != 1) {
            throw new AssertionError("Expected one object named " + name + " but found " + entities.length);
        }
        return entities[0];
    }

    private static int visualEntityByName(World world, String name) {
        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TransformComponent.class,
                        PixscapeIdentityComponent.class, AssetRefComponent.class))
                .getEntities();
        int found = -1;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (!name.equals(identities.get(entity).name)) continue;
            if (found >= 0) throw new AssertionError("More than one visual entity named " + name);
            found = entity;
        }
        if (found < 0) throw new AssertionError("Missing visual entity named " + name);
        return found;
    }

    private static int visualEntityCountByName(World world, String name) {
        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, PixscapeIdentityComponent.class,
                        AssetRefComponent.class))
                .getEntities();
        int count = 0;
        for (int i = 0; i < entities.size(); i++) {
            if (name.equals(identities.get(entities.get(i)).name)) count++;
        }
        return count;
    }

    private static void assertFlip(World world, String name,
                                   float scaleX, float scaleY, float originX, float originY) {
        TransformComponent transform = world.getMapper(TransformComponent.class)
                .get(visualEntityByName(world, name));
        assertEquals(scaleX, transform.scaleX, 0.0001f);
        assertEquals(scaleY, transform.scaleY, 0.0001f);
        assertEquals(originX, transform.originX, 0.0001f);
        assertEquals(originY, transform.originY, 0.0001f);
    }

    private static void assertTransformedCorners(World world, int entity, float[] expected) {
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        float[] local = {0f, dimensions.height, dimensions.width, dimensions.height,
                dimensions.width, 0f, 0f, 0f};
        for (int i = 0; i < local.length; i += 2) {
            float dx = (local[i] - transform.originX) * transform.scaleX;
            float dy = (local[i + 1] - transform.originY) * transform.scaleY;
            assertEquals(expected[i], transform.x + transform.cos * dx - transform.sin * dy, 0.0001f);
            assertEquals(expected[i + 1], transform.y + transform.sin * dx + transform.cos * dy, 0.0001f);
        }
    }

    private static void assertTransformedBounds(World world, int entity,
                                                float minX, float minY, float maxX, float maxY) {
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        float[] local = {0f, dimensions.height, dimensions.width, dimensions.height,
                dimensions.width, 0f, 0f, 0f};
        float actualMinX = Float.POSITIVE_INFINITY;
        float actualMinY = Float.POSITIVE_INFINITY;
        float actualMaxX = Float.NEGATIVE_INFINITY;
        float actualMaxY = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < local.length; i += 2) {
            float dx = (local[i] - transform.originX) * transform.scaleX;
            float dy = (local[i + 1] - transform.originY) * transform.scaleY;
            float x = transform.x + transform.cos * dx - transform.sin * dy;
            float y = transform.y + transform.sin * dx + transform.cos * dy;
            actualMinX = Math.min(actualMinX, x);
            actualMinY = Math.min(actualMinY, y);
            actualMaxX = Math.max(actualMaxX, x);
            actualMaxY = Math.max(actualMaxY, y);
        }
        assertEquals(minX, actualMinX, 0.0001f);
        assertEquals(minY, actualMinY, 0.0001f);
        assertEquals(maxX, actualMaxX, 0.0001f);
        assertEquals(maxY, actualMaxY, 0.0001f);
    }

    private static void assertEquivalentSpriteTransform(World world, int expectedEntity, int actualEntity) {
        assertEquivalentTransformAndDimensions(world, expectedEntity, actualEntity);
        assertEquals(world.getMapper(AssetRefComponent.class).get(expectedEntity).assetId,
                world.getMapper(AssetRefComponent.class).get(actualEntity).assetId);
    }

    private static void assertEquivalentTransformAndDimensions(World world,
                                                               int expectedEntity,
                                                               int actualEntity) {
        TransformComponent expected = world.getMapper(TransformComponent.class).get(expectedEntity);
        TransformComponent actual = world.getMapper(TransformComponent.class).get(actualEntity);
        assertEquals(expected.x, actual.x, 0.0001f);
        assertEquals(expected.y, actual.y, 0.0001f);
        assertEquals(expected.originX, actual.originX, 0.0001f);
        assertEquals(expected.originY, actual.originY, 0.0001f);
        assertEquals(expected.rotationRad, actual.rotationRad, 0.0001f);
        assertEquals(expected.scaleX, actual.scaleX, 0.0001f);
        assertEquals(expected.scaleY, actual.scaleY, 0.0001f);
        DimensionsComponent expectedDimensions = world.getMapper(DimensionsComponent.class).get(expectedEntity);
        DimensionsComponent actualDimensions = world.getMapper(DimensionsComponent.class).get(actualEntity);
        assertEquals(expectedDimensions.width, actualDimensions.width, 0.0001f);
        assertEquals(expectedDimensions.height, actualDimensions.height, 0.0001f);
    }

    private static int[] objectEntitiesByName(World world, String name) {
        ComponentMapper<PixscapeIdentityComponent> identities =
                world.getMapper(PixscapeIdentityComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TransformComponent.class,
                        PixscapeIdentityComponent.class))
                .getEntities();
        int count = 0;
        for (int i = 0; i < entities.size(); i++) {
            if (name.equals(identities.get(entities.get(i)).name)
                    && !world.getMapper(AssetRefComponent.class).has(entities.get(i))) {
                count++;
            }
        }
        int[] matching = new int[count];
        int next = 0;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (name.equals(identities.get(entity).name)
                    && !world.getMapper(AssetRefComponent.class).has(entity)) {
                matching[next++] = entity;
            }
        }
        return matching;
    }

    private static void assertCorners(World world, int entity, float[] expected) {
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        float[] local = {
                0f, dimensions.height,
                dimensions.width, dimensions.height,
                dimensions.width, 0f,
                0f, 0f
        };
        for (int i = 0; i < local.length; i += 2) {
            float dx = local[i] - transform.originX;
            float dy = local[i + 1] - transform.originY;
            float worldX = transform.x + transform.cos * dx - transform.sin * dy;
            float worldY = transform.y + transform.sin * dx + transform.cos * dy;
            assertEquals(expected[i], worldX, 0.0001f);
            assertEquals(expected[i + 1], worldY, 0.0001f);
        }
    }

    private static void assertSingleTag(World world, String entityName, String expectedTag) {
        int entity = objectEntityByName(world, entityName);
        PixscapeTagComponent tags = world.getMapper(PixscapeTagComponent.class).get(entity);
        assertNotNull(tags);
        assertEquals(1, tags.tags.size);
        assertEquals(expectedTag, tags.tags.first());
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

    @Test
    public void finalizesObjectReferencesWithPixscapeStableIdsNotTiledSourceIds() {
        PropertySet base = new PropertySet().putClass("behavior", "Behavior", new PropertySet());
        java.util.List<TmxObjectPropertyReference> references = java.util.List.of(
                new TmxObjectPropertyReference(java.util.List.of("target"), 2002, "source"),
                new TmxObjectPropertyReference(java.util.List.of("none"), 0, "source"),
                new TmxObjectPropertyReference(java.util.List.of("behavior", "self"), 1001, "source"));
        PropertySet finalized = TmxSceneImportService.finalizeObjectProperties(
                base, references, java.util.Map.of(1001, 3, 2002, 7));

        assertEquals(PropertyType.OBJECT, finalized.typeOf("target"));
        assertEquals(7, finalized.getObjectStableId("target", -1));
        assertNotEquals(2002, finalized.getObjectStableId("target", -1));
        assertEquals(-1, finalized.getObjectStableId("none", 0));
        assertEquals(3, finalized.getClassValue("behavior").properties()
                .getObjectStableId("self", -1));
        assertFalse(base.contains("target"));
    }

    @Test
    public void importSceneResolvesObjectPropertiesAcrossLayersAndPersistsStableIds()
            throws Exception {
        Harness h = harness("tmx-import-object-references-round-trip");
        FileHandle tmx = writeTmx(h.root.resolve("object-references.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Triggers">
                    <properties>
                      <property name="layerTarget" type="object" value="2002"/>
                    </properties>
                    <object id="1001" name="Switch" x="10" y="20" width="8" height="6">
                      <properties>
                        <property name="target" type="object" value="2002"/>
                        <property name="none" type="object" value="0"/>
                        <property name="behavior" type="class" propertytype="Behavior">
                          <properties>
                            <property name="self" type="object" value="1001"/>
                          </properties>
                        </property>
                      </properties>
                    </object>
                  </objectgroup>
                  <objectgroup name="Targets">
                    <object id="2002" name="Door" x="40" y="50"><point/></object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Object References"));
        World world = loadImportedWorld(h, result);

        assertTrue(result.imported());
        int switchEntity = objectEntityByName(world, "Switch");
        int doorEntity = objectEntityByName(world, "Door");
        ComponentMapper<PixscapeIdentityComponent> identities =
                world.getMapper(PixscapeIdentityComponent.class);
        int switchStableId = identities.get(switchEntity).stableId;
        int doorStableId = identities.get(doorEntity).stableId;
        assertTrue(switchStableId > 0);
        assertTrue(doorStableId > 0);
        assertNotEquals(1001, switchStableId);
        assertNotEquals(2002, doorStableId);

        PropertySet switchProperties = world.getMapper(CustomPropertiesComponent.class)
                .get(switchEntity).properties;
        assertEquals(doorStableId, switchProperties.getObjectStableId("target", -1));
        assertNotEquals(2002, switchProperties.getObjectStableId("target", -1));
        assertEquals(-1, switchProperties.getObjectStableId("none", 0));
        assertEquals(switchStableId, switchProperties.getClassValue("behavior").properties()
                .getObjectStableId("self", -1));

        PropertySet layerProperties = world.getMapper(CustomPropertiesComponent.class)
                .get(layerEntity(world, 0, false)).properties;
        assertEquals(doorStableId, layerProperties.getObjectStableId("layerTarget", -1));
    }

    @Test
    public void importSceneRoundTripsPolygonAndPolylineAsGenericAuthoredGeometry()
            throws Exception {
        Harness h = harness("tmx-import-authored-path-geometry");
        FileHandle tmx = writeTmx(h.root.resolve("paths.tmx"), """
                <map orientation="orthogonal" width="10" height="10" tilewidth="16" tileheight="16">
                  <objectgroup name="Shapes" offsetx="3" offsety="4">
                    <object id="1001" name="Polygon" class="Area" x="100" y="80" rotation="0">
                      <polygon points="0,0 40,10 20,50 -10,20"/>
                      <properties><property name="target" type="object" value="2002"/></properties>
                    </object>
                    <object id="2002" name="Polyline" type="Route" x="20" y="30" rotation="90">
                      <polyline points="-10,5 20,-7 40,12"/>
                      <properties><property name="label" value="exit"/></properties>
                    </object>
                  </objectgroup>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Authored Paths"));
        World world = loadImportedWorldWithGeometry(h, result);

        assertTrue(result.imported());
        int polygonEntity = objectEntityByName(world, "Polygon");
        int polylineEntity = objectEntityByName(world, "Polyline");
        PolygonComponent polygon = world.getMapper(PolygonComponent.class).get(polygonEntity);
        PolylineComponent polyline = world.getMapper(PolylineComponent.class).get(polylineEntity);
        assertNotNull(polygon);
        assertNotNull(polyline);
        assertArrayEquals(new float[]{10f, 50f, 50f, 40f, 30f, 0f, 0f, 30f},
                polygon.vertices, 0.0001f);
        assertArrayEquals(new float[]{0f, 7f, 30f, 19f, 50f, 0f}, polyline.vertices, 0.0001f);

        DimensionsComponent polygonDimensions = world.getMapper(DimensionsComponent.class).get(polygonEntity);
        DimensionsComponent polylineDimensions = world.getMapper(DimensionsComponent.class).get(polylineEntity);
        assertEquals(50f, polygonDimensions.width, 0.0001f);
        assertEquals(50f, polygonDimensions.height, 0.0001f);
        assertEquals(50f, polylineDimensions.width, 0.0001f);
        assertEquals(19f, polylineDimensions.height, 0.0001f);
        assertTrue(world.getMapper(AABBComponent.class).has(polygonEntity));
        assertTrue(world.getMapper(OrientedBoundsComponent.class).has(polygonEntity));
        assertTrue(world.getMapper(AABBComponent.class).has(polylineEntity));
        assertTrue(world.getMapper(OrientedBoundsComponent.class).has(polylineEntity));
        assertEquals(EntityKind.POLYGON, world.getMapper(EntityMetaComponent.class).get(polygonEntity).kind);
        assertEquals(EntityKind.POLYLINE, world.getMapper(EntityMetaComponent.class).get(polylineEntity).kind);

        assertWorldVertices(world.getMapper(TransformComponent.class).get(polygonEntity), polygon.vertices,
                new float[]{103f, 76f, 143f, 66f, 123f, 26f, 93f, 56f});
        assertWorldVertices(world.getMapper(TransformComponent.class).get(polylineEntity), polyline.vertices,
                new float[]{18f, 136f, 30f, 106f, 11f, 86f});

        int polygonStableId = world.getMapper(PixscapeIdentityComponent.class).get(polygonEntity).stableId;
        int polylineStableId = world.getMapper(PixscapeIdentityComponent.class).get(polylineEntity).stableId;
        assertTrue(polygonStableId > 0);
        assertTrue(polylineStableId > 0);
        assertNotEquals(2002, polylineStableId);
        assertEquals(polylineStableId, world.getMapper(CustomPropertiesComponent.class).get(polygonEntity)
                .properties.getObjectStableId("target", -1));
        assertEquals("exit", world.getMapper(CustomPropertiesComponent.class).get(polylineEntity)
                .properties.getString("label", null));
        assertSingleTag(world, "Polygon", "Area");
        assertSingleTag(world, "Polyline", "Route");
    }

    @Test
    public void importSceneRoundTripsIsometricObjectLayerGeometryAndDefaultTileObjectAlignment()
            throws Exception {
        Harness h = harness("tmx-import-isometric-objects");
        writePng(h.projectDir.child("terrain.png"), 64, 16);
        FileHandle tmx = writeTmx(h.root.resolve("isometric-objects.tmx"), """
                <map orientation="isometric" width="4" height="4" tilewidth="32" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="32" tileheight="16" tilecount="2" columns="2">
                    <image source="terrain.png" width="64" height="16"/>
                  </tileset>
                  <layer name="Below" width="4" height="4"><data encoding="csv">0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0</data></layer>
                  <group name="World" visible="0" offsetx="2" offsety="4" parallaxx="2" parallaxy="0.5">
                    <objectgroup name="Gameplay" offsetx="1" offsety="1" parallaxx="3" parallaxy="4" draworder="topdown">
                      <properties><property name="role" value="logic"/></properties>
                      <object id="101" name="Point" class="Spawn" x="24" y="24"><point/>
                        <properties><property name="target" type="object" value="102"/></properties>
                      </object>
                      <object id="102" name="Rectangle" class="Area" type="LegacyArea" x="24" y="24" width="16" height="16"/>
                      <object name="RotatedRectangle" x="24" y="24" width="16" height="16" rotation="90"/>
                      <object id="103" name="Polygon" class="Zone" x="24" y="24" rotation="90"><polygon points="0,0 16,0 16,16"/></object>
                      <object name="Polyline" x="24" y="24" rotation="90"><polyline points="0,0 16,0 16,16"/></object>
                      <object name="Tile" gid="1" x="24" y="24"/>
                    </objectgroup>
                  </group>
                  <layer name="Above" width="4" height="4"><data encoding="csv">0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0</data></layer>
                </map>
                """);

        TmxSceneImportResult result = h.importer().importScene(request(tmx, "Isometric Objects"));
        World world = loadImportedWorldWithGeometry(h, result);

        assertTrue(result.imported());
        assertTrue(result.planResult().hasPlan());
        assertEquals(List.of("Below", "World/Gameplay", "Above"), result.planResult().plan().layers()
                .stream().map(TmxLayerPlan::name).toList());
        TmxObjectLayerPlan layer = (TmxObjectLayerPlan) result.planResult().plan().layers().get(1);
        assertEquals(List.of(0, 1, 2, 3, 4, 5),
                layer.objects().stream().map(TmxObjectPlan::zIndex).toList());
        int objectLayer = layerEntity(world, 1, false);
        assertFalse(world.getMapper(VisibilityComponent.class).get(objectLayer).visible);
        assertEquals(6f, world.getMapper(LayerParallaxComponent.class).get(objectLayer).factorX, 0.0001f);
        assertEquals(2f, world.getMapper(LayerParallaxComponent.class).get(objectLayer).factorY, 0.0001f);
        assertEquals("logic", world.getMapper(CustomPropertiesComponent.class).get(objectLayer)
                .properties.getString("role", null));

        int point = objectEntityByName(world, "Point");
        TransformComponent pointTransform = world.getMapper(TransformComponent.class).get(point);
        assertEquals(3f, pointTransform.x, 0.0001f);
        assertEquals(27f, pointTransform.y, 0.0001f);
        assertEquals(EntityKind.TILED_POINT, world.getMapper(EntityMetaComponent.class).get(point).kind);
        assertEquals("Spawn", world.getMapper(PixscapeTagComponent.class).get(point).tags.first());

        int rectangle = objectEntityByName(world, "Rectangle");
        PolygonComponent rectanglePolygon = world.getMapper(PolygonComponent.class).get(rectangle);
        assertNotNull(rectanglePolygon);
        assertEquals(EntityKind.TILED_RECTANGLE,
                world.getMapper(EntityMetaComponent.class).get(rectangle).kind);
        assertFalse(world.getMapper(PolylineComponent.class).has(rectangle));
        assertArrayEquals(new float[]{0f, 8f, 16f, 16f, 32f, 8f, 16f, 0f},
                rectanglePolygon.vertices, 0.0001f);
        assertWorldVertices(world.getMapper(TransformComponent.class).get(rectangle), rectanglePolygon.vertices,
                new float[]{3f, 27f, 19f, 35f, 35f, 27f, 19f, 19f});
        assertEquals(32f, world.getMapper(DimensionsComponent.class).get(rectangle).width, 0.0001f);
        assertEquals(16f, world.getMapper(DimensionsComponent.class).get(rectangle).height, 0.0001f);
        assertEquals(0f, world.getMapper(TransformComponent.class).get(rectangle).rotationRad, 0f);
        assertEquals("Area", world.getMapper(PixscapeTagComponent.class).get(rectangle).tags.first());
        int rectangleStableId = world.getMapper(PixscapeIdentityComponent.class).get(rectangle).stableId;
        assertTrue(rectangleStableId > 0);
        assertEquals(rectangleStableId, world.getMapper(CustomPropertiesComponent.class).get(point)
                .properties.getObjectStableId("target", -1));

        int rotatedRectangle = objectEntityByName(world, "RotatedRectangle");
        PolygonComponent rotatedRectanglePolygon = world.getMapper(PolygonComponent.class).get(rotatedRectangle);
        assertNotNull(rotatedRectanglePolygon);
        assertArrayEquals(new float[]{32f, 8f, 64f, 4f, 32f, 0f, 0f, 4f},
                rotatedRectanglePolygon.vertices, 0.0001f);
        assertWorldVertices(world.getMapper(TransformComponent.class).get(rotatedRectangle),
                rotatedRectanglePolygon.vertices,
                new float[]{3f, 27f, 35f, 23f, 3f, 19f, -29f, 23f});
        assertEquals(0f, world.getMapper(TransformComponent.class).get(rotatedRectangle).rotationRad, 0f);

        int polygon = objectEntityByName(world, "Polygon");
        assertWorldVertices(world.getMapper(TransformComponent.class).get(polygon),
                world.getMapper(PolygonComponent.class).get(polygon).vertices,
                new float[]{3f, 27f, 35f, 23f, 3f, 19f});
        assertEquals(0f, world.getMapper(TransformComponent.class).get(polygon).rotationRad, 0f);
        int polyline = objectEntityByName(world, "Polyline");
        assertWorldVertices(world.getMapper(TransformComponent.class).get(polyline),
                world.getMapper(PolylineComponent.class).get(polyline).vertices,
                new float[]{3f, 27f, 35f, 23f, 3f, 19f});
        assertFalse(world.getMapper(PolygonComponent.class).has(polyline));
        assertEquals(0f, world.getMapper(TransformComponent.class).get(polyline).rotationRad, 0f);

        int tile = visualEntityByName(world, "Tile");
        TransformComponent tileTransform = world.getMapper(TransformComponent.class).get(tile);
        assertEquals(3f, tileTransform.x, 0.0001f);
        assertEquals(27f, tileTransform.y, 0.0001f);
        assertEquals(16f, tileTransform.originX, 0.0001f);
        assertEquals(0f, tileTransform.originY, 0.0001f);
        assertEquals(EntityKind.SPRITE, world.getMapper(EntityMetaComponent.class).get(tile).kind);
    }

    private static void assertWorldVertices(TransformComponent transform,
                                            float[] vertices,
                                            float[] expected) {
        assertEquals(expected.length, vertices.length);
        for (int i = 0; i < vertices.length; i += 2) {
            float x = (vertices[i] - transform.originX) * transform.scaleX;
            float y = (vertices[i + 1] - transform.originY) * transform.scaleY;
            float worldX = transform.x + transform.cos * x - transform.sin * y;
            float worldY = transform.y + transform.sin * x + transform.cos * y;
            assertEquals(expected[i], worldX, 0.0001f);
            assertEquals(expected[i + 1], worldY, 0.0001f);
        }
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
