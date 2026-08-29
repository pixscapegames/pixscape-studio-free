package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.tiled.TiledPaintService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AddTiledMapCommandTest {
    private World world;
    private LayerService layers;
    private HistoryManager history;
    private IdentityRegistry identities;
    private int layerEntity;

    @Before
    public void setUp() {
        world = new World(new WorldConfiguration());
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
        SceneMeta meta = config.getCurrentSceneMeta();
        identities = new IdentityRegistry();
        identities.bind(world, meta);
        history = new HistoryManager(16);
        layers = new LayerService(world, null, history.historyIds(), identities);
        layerEntity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = 0;
        world.getMapper(LayerMetaComponent.class).create(layerEntity).name = "Universal";
        world.process();
        assertEquals(1, layers.count());
    }

    @After public void tearDown() {
        ProjectConfig.setInstance(null);
        world.dispose();
    }

    @Test
    public void addsTwoIndependentConfigurationsWithoutChangingOwningLayerType() {
        AtomicInteger selected = new AtomicInteger(-1);
        AddTiledMapCommand iso = command(64, 32, TiledProjection.ISO, 64, 32, selected);
        iso.redo();
        int isoId = selected.get();
        world.process();
        AddTiledMapCommand ortho = command(20, 10, TiledProjection.ORTHO, 32, 32, selected);
        ortho.redo();
        int orthoId = selected.get();
        world.process();

        assertTrue(layers.isLayerEntity(layerEntity));
        assertNotEquals(isoId, orthoId);
        assertEquals("scene1", world.getMapper(TiledLayerComponent.class).get(isoId).atlasTag);
        assertEquals("scene1", world.getMapper(TiledLayerComponent.class).get(orthoId).atlasTag);
        assertMap(isoId, 0, 0, 64, 32, TiledProjection.ISO, 64, 32);
        assertMap(orthoId, 0, 1, 20, 10, TiledProjection.ORTHO, 32, 32);
        assertTrue("Map content must not consume the Layer's add capability",
                layers.isLayerEntity(layerEntity));
    }

    @Test
    public void undoDeletesOnlyMapAndRedoRestoresIdentityAndConfiguration() {
        AtomicInteger selected = new AtomicInteger(-1);
        AddTiledMapCommand command = command(17, 19, TiledProjection.ISO, 48, 24, selected);
        command.redo();
        int firstEntity = selected.get();
        int stableId = world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class)
                .get(firstEntity).stableId;

        command.undo();
        world.process();
        assertTrue(world.getEntityManager().isActive(layerEntity));
        assertEquals(0, mapCount());

        command.redo();
        int restored = selected.get();
        assertEquals(stableId, world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class)
                .get(restored).stableId);
        assertMap(restored, 0, 0, 17, 19, TiledProjection.ISO, 48, 24);
    }

    @Test
    public void newlyAddedMapUsesCurrentSceneAtlasAndAcceptsTilePaint() {
        AtomicInteger selected = new AtomicInteger(-1);
        command(12, 10, TiledProjection.ISO, 64, 32, selected).redo();

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(selected.get());
        assertNotNull(tiled.data);
        assertEquals("scene1", tiled.atlasTag);

        TiledPaintService paint = new TiledPaintService();
        paint.setActiveTileAssetId(321);
        paint.paintTile(tiled, 2, 3);

        assertEquals(321, tiled.data.getTile(2, 3));
        assertEquals(1, tiled.tileAssetIds.size);
        assertEquals(321, tiled.tileAssetIds.first());
    }

    @Test
    public void dedicatedDeleteLeavesSiblingAndLayerAndUndoRestoresMap() {
        AtomicInteger selected = new AtomicInteger(-1);
        AddTiledMapCommand a = command(8, 8, TiledProjection.ORTHO, 16, 16, selected);
        a.redo();
        int mapA = selected.get();
        AddTiledMapCommand b = command(9, 7, TiledProjection.ISO, 32, 16, selected);
        b.redo();
        int mapB = selected.get();
        DeleteTiledMapCommand delete = new DeleteTiledMapCommand(layers, mapA, selected::set);

        delete.redo();
        world.process();
        assertTrue(world.getEntityManager().isActive(layerEntity));
        assertTrue(world.getEntityManager().isActive(mapB));
        assertEquals(1, mapCount());

        delete.undo();
        world.process();
        assertEquals(2, mapCount());
        int restored = selected.get();
        assertMap(restored, 0, 0, 8, 8, TiledProjection.ORTHO, 16, 16);
        assertFalse(world.getMapper(CustomPropertiesComponent.class).has(restored));
    }

    @Test
    public void dedicatedDeleteUndoRedoDeeplyPreservesCustomPropertiesAndMapState() {
        AtomicInteger selected = new AtomicInteger(-1);
        command(7, 5, TiledProjection.ISO, 24, 12, selected).redo();
        int map = selected.get();

        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(map);
        index.zIndex = 4;
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(map);
        tiled.originX = 48f;
        tiled.originY = -24f;
        tiled.data.originX = tiled.originX;
        tiled.data.originY = tiled.originY;
        tiled.spatialEnabled = true;
        tiled.data.spatialEnabled = true;
        tiled.defaultTileAltitude = 3.5f;
        tiled.defaultTileHeight = 9f;
        tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
        tiled.data.defaultTileHeight = tiled.defaultTileHeight;
        TiledPaintService paint = new TiledPaintService();
        paint.setActiveTileAssetId(404);
        paint.paintTile(tiled, 2, 1);
        tiled.setSparseSpatialOverride(0, 6f, 11f, 3);
        world.getMapper(games.pixscape.runtime.component.VisibilityComponent.class)
                .get(map).visible = false;

        CustomPropertiesComponent component = world.getMapper(CustomPropertiesComponent.class)
                .create(map);
        component.properties.copyFrom(representativeProperties("map-a", 777));

        int stableId = world.getMapper(PixscapeIdentityComponent.class).get(map).stableId;
        long historyId = history.historyIds().ensureForEntity(map);
        DeleteTiledMapCommand delete = new DeleteTiledMapCommand(layers, map, selected::set);

        component.properties.putString("label", "mutated-after-snapshot");
        component.properties.putClass("settings", "MapSettings",
                new PropertySet().putInt("level", 99));
        history.execute(delete);
        world.process();
        assertEquals(0, mapCount());

        history.undo();
        world.process();
        int restored = history.historyIds().entityOfHistoryId(historyId);
        assertRestoredCustomMap(restored, stableId, historyId);

        history.redo();
        world.process();
        assertEquals(0, mapCount());

        history.undo();
        world.process();
        restored = history.historyIds().entityOfHistoryId(historyId);
        assertRestoredCustomMap(restored, stableId, historyId);
    }

    @Test
    public void layerSnapshotRestoresAllOrdinaryHostedMaps() {
        AtomicInteger selected = new AtomicInteger(-1);
        command(8, 6, TiledProjection.ORTHO, 16, 16, selected).redo();
        world.process();
        command(11, 7, TiledProjection.ISO, 32, 16, selected).redo();
        world.process();
        LayerService.LayerSnapshot snapshot = layers.snapshotLayer(0);

        layers.removeLayerCascade(0);
        world.process();
        assertEquals(0, mapCount());

        int restoredLayer = layers.insertLayerSnapshot(0, snapshot);
        world.process();
        assertEquals(2, mapCount());
    }

    @Test
    public void deleteLayerUndoRestoresMixedEntitiesAndIndependentMapProperties() {
        AtomicInteger selected = new AtomicInteger(-1);
        command(8, 6, TiledProjection.ORTHO, 16, 16, selected).redo();
        int mapA = selected.get();
        world.process();
        command(11, 7, TiledProjection.ISO, 32, 16, selected).redo();
        int mapB = selected.get();

        world.getMapper(CustomPropertiesComponent.class).create(mapA).properties
                .copyFrom(representativeProperties("map-a", 501));
        world.getMapper(CustomPropertiesComponent.class).create(mapB).properties
                .copyFrom(new PropertySet()
                        .putString("label", "map-b")
                        .putBoolean("night", true));

        int sprite = world.create();
        new GenericEntityInitializer(world)
                .configureStandaloneSprite(
                        101, "scene1", 16, 16, 4f, 5f, 8f, 8f,
                        0, 0, 7, "Sprite", 0)
                .init(sprite);
        identities.ensureStableId(sprite);

        int light = world.create();
        new GenericEntityInitializer(world)
                .configureConeLightProcedural(
                        6f, 7f, 0.25f, 6, 2, 12, 0, "Light",
                        77f, 1.5f, 41f, 0.2f, 2f, 0.1f, 0.2f, 0.3f)
                .init(light);
        identities.ensureStableId(light);

        world.getMapper(EntityIndexComponent.class).get(mapA).zIndex = 30;
        world.getMapper(EntityIndexComponent.class).get(sprite).zIndex = 20;
        world.getMapper(EntityIndexComponent.class).get(light).zIndex = 10;
        world.getMapper(EntityIndexComponent.class).get(mapB).zIndex = 0;
        world.process();

        long layerHistoryId = history.historyIds().ensureForEntity(layerEntity);
        long mapAHistoryId = history.historyIds().ensureForEntity(mapA);
        long mapBHistoryId = history.historyIds().ensureForEntity(mapB);
        long spriteHistoryId = history.historyIds().ensureForEntity(sprite);
        long lightHistoryId = history.historyIds().ensureForEntity(light);
        int mapAStableId = stableId(mapA);
        int mapBStableId = stableId(mapB);
        int spriteStableId = stableId(sprite);
        int lightStableId = stableId(light);

        history.execute(new DeleteLayerCommand(layers, layerEntity, null));
        world.process();
        assertEquals(0, layers.count());
        assertEquals(0, mapCount());

        history.undo();
        world.process();
        assertRestoredMixedLayer(
                layerHistoryId, mapAHistoryId, mapBHistoryId, spriteHistoryId, lightHistoryId,
                mapAStableId, mapBStableId, spriteStableId, lightStableId);

        history.redo();
        world.process();
        assertEquals(0, layers.count());
        assertEquals(0, mapCount());

        history.undo();
        world.process();
        assertRestoredMixedLayer(
                layerHistoryId, mapAHistoryId, mapBHistoryId, spriteHistoryId, lightHistoryId,
                mapAStableId, mapBStableId, spriteStableId, lightStableId);
    }

    @Test
    public void genericLayerMoveChangesOnlyMapOwnershipAndIsUndoable() {
        AtomicInteger selected = new AtomicInteger(-1);
        command(13, 9, TiledProjection.ISO, 40, 20, selected).redo();
        int map = selected.get();
        int secondLayer = world.create();
        LayerComponent target = world.getMapper(LayerComponent.class).create(secondLayer);
        target.layerIndex = 1;
        target.spatialEnabled = true;
        world.getMapper(LayerMetaComponent.class).create(secondLayer).name = "Other";
        world.process();
        assertFalse(world.getMapper(TiledLayerComponent.class).get(map).spatialEnabled);

        long historyId = layers.historyIds().ensureForEntity(map);
        ChangeLayerIndexCommand move = new ChangeLayerIndexCommand(world, layers.historyIds());
        move.addEntry(historyId, 0, 1);
        move.redo();

        assertEquals(1, world.getMapper(EntityIndexComponent.class).get(map).layerIndex);
        assertEquals(TiledProjection.ISO, world.getMapper(TiledLayerComponent.class).get(map).projection);
        assertFalse(world.getMapper(TiledLayerComponent.class).get(map).spatialEnabled);
        move.undo();
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(map).layerIndex);
    }

    @Test
    public void genericSpriteCanMoveIntoLayerContainingMultipleMaps() {
        AtomicInteger selected = new AtomicInteger(-1);
        command(8, 8, TiledProjection.ORTHO, 16, 16, selected).redo();
        world.process();
        command(9, 7, TiledProjection.ISO, 32, 16, selected).redo();
        world.process();

        int sourceLayer = world.create();
        LayerComponent source = world.getMapper(LayerComponent.class).create(sourceLayer);
        source.layerIndex = 1;
        world.getMapper(LayerMetaComponent.class).create(sourceLayer).name = "Source";

        int sprite = world.create();
        EntityIndexComponent spriteIndex = world.getMapper(EntityIndexComponent.class).create(sprite);
        spriteIndex.layerIndex = 1;
        spriteIndex.zIndex = 0;
        world.getMapper(TextureRegionComponent.class).create(sprite);
        world.process();

        long historyId = layers.historyIds().ensureForEntity(sprite);
        ChangeLayerIndexCommand move = new ChangeLayerIndexCommand(world, layers.historyIds());
        move.addEntry(historyId, 1, 0);
        move.redo();

        assertEquals(0, spriteIndex.layerIndex);
        assertEquals(2, mapCount());
        assertTrue(layers.isLayerEntity(layerEntity));

        move.undo();
        assertEquals(1, spriteIndex.layerIndex);
    }

    private AddTiledMapCommand command(int width, int height, TiledProjection projection,
                                       int tileWidth, int tileHeight, AtomicInteger selected) {
        return new AddTiledMapCommand(layers, layerEntity, width, height, projection,
                tileWidth, tileHeight, 8, selected::set);
    }

    private void assertMap(int id, int layer, int z, int width, int height,
                           TiledProjection projection, int tileWidth, int tileHeight) {
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(id);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(id);
        assertEquals(layer, index.layerIndex);
        assertEquals(z, index.zIndex);
        assertEquals(width, tiled.mapWidthCells);
        assertEquals(height, tiled.mapHeightCells);
        assertEquals(projection, tiled.projection);
        assertEquals(tileWidth, tiled.tileWidth);
        assertEquals(tileHeight, tiled.tileHeight);
        assertTrue(tiled.chunkSize > 0);
        assertNotNull(tiled.data);
        assertFalse(tiled.spatialEnabled);
        assertFalse(tiled.data.spatialEnabled);
        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(id));
        assertEquals(EntityKind.TILED_MAP,
                world.getMapper(EntityMetaComponent.class).get(id).kind);
    }

    private int mapCount() {
        return world.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class))
                .getEntities().size();
    }

    private void assertRestoredMixedLayer(long layerHistoryId,
                                          long mapAHistoryId,
                                          long mapBHistoryId,
                                          long spriteHistoryId,
                                          long lightHistoryId,
                                          int mapAStableId,
                                          int mapBStableId,
                                          int spriteStableId,
                                          int lightStableId) {
        assertEquals(1, layers.count());
        assertTrue(history.historyIds().entityOfHistoryId(layerHistoryId) >= 0);
        int mapA = history.historyIds().entityOfHistoryId(mapAHistoryId);
        int mapB = history.historyIds().entityOfHistoryId(mapBHistoryId);
        int sprite = history.historyIds().entityOfHistoryId(spriteHistoryId);
        int light = history.historyIds().entityOfHistoryId(lightHistoryId);
        assertTrue(mapA >= 0);
        assertTrue(mapB >= 0);
        assertTrue(sprite >= 0);
        assertTrue(light >= 0);

        assertEquals(mapAStableId, stableId(mapA));
        assertEquals(mapBStableId, stableId(mapB));
        assertEquals(spriteStableId, stableId(sprite));
        assertEquals(lightStableId, stableId(light));
        assertEquals(30, world.getMapper(EntityIndexComponent.class).get(mapA).zIndex);
        assertEquals(20, world.getMapper(EntityIndexComponent.class).get(sprite).zIndex);
        assertEquals(10, world.getMapper(EntityIndexComponent.class).get(light).zIndex);
        assertEquals(0, world.getMapper(EntityIndexComponent.class).get(mapB).zIndex);

        PropertySet restoredA = world.getMapper(CustomPropertiesComponent.class)
                .get(mapA).properties;
        assertRepresentativeProperties(restoredA, "map-a", 501);
        assertFalse(restoredA.contains("night"));
        PropertySet restoredB = world.getMapper(CustomPropertiesComponent.class)
                .get(mapB).properties;
        assertEquals(2, restoredB.size());
        assertEquals("map-b", restoredB.getString("label", null));
        assertTrue(restoredB.getBoolean("night", false));
        assertFalse(restoredB.contains("count"));

        assertTrue(world.getMapper(TextureRegionComponent.class).has(sprite));
        assertEquals(4f, world.getMapper(TransformComponent.class).get(sprite).x, 0f);
        ConeLightComponent restoredLight = world.getMapper(ConeLightComponent.class).get(light);
        assertEquals(77f, restoredLight.radius, 0f);
        assertEquals(41f, restoredLight.coneAngleDeg, 0f);
    }

    private int stableId(int entityId) {
        return world.getMapper(PixscapeIdentityComponent.class).get(entityId).stableId;
    }

    private void assertRestoredCustomMap(int restored, int stableId, long historyId) {
        assertTrue(restored >= 0);
        assertEquals(stableId,
                world.getMapper(PixscapeIdentityComponent.class).get(restored).stableId);
        assertEquals(historyId, history.historyIds().historyIdOfEntity(restored));
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(restored);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(restored);
        assertEquals(0, index.layerIndex);
        assertEquals(4, index.zIndex);
        assertEquals(7, tiled.mapWidthCells);
        assertEquals(5, tiled.mapHeightCells);
        assertEquals(TiledProjection.ISO, tiled.projection);
        assertEquals(24, tiled.tileWidth);
        assertEquals(12, tiled.tileHeight);
        assertNotNull(tiled.data);
        assertEquals(48f, tiled.originX, 0f);
        assertEquals(-24f, tiled.originY, 0f);
        assertEquals(48f, world.getMapper(games.pixscape.runtime.component.TransformComponent.class)
                .get(restored).x, 0f);
        assertEquals(-24f, world.getMapper(games.pixscape.runtime.component.TransformComponent.class)
                .get(restored).y, 0f);
        assertEquals(404, tiled.data.getTile(2, 1));
        assertTrue(tiled.spatialEnabled);
        assertEquals(3.5f, tiled.defaultTileAltitude, 0f);
        assertEquals(9f, tiled.defaultTileHeight, 0f);
        assertTrue(tiled.hasSparseSpatialOverride(0));
        assertEquals(6f, tiled.sparseTileAltitude(0), 0f);
        assertEquals(11f, tiled.sparseTileHeight(0), 0f);
        assertEquals(3, tiled.sparseTileSpatialFlags(0));
        assertFalse(world.getMapper(games.pixscape.runtime.component.VisibilityComponent.class)
                .get(restored).visible);

        CustomPropertiesComponent properties = world.getMapper(CustomPropertiesComponent.class)
                .get(restored);
        assertRepresentativeProperties(properties.properties, "map-a", 777);
    }

    static PropertySet representativeProperties(String label, int objectStableId) {
        PropertySet nested = new PropertySet()
                .putInt("level", 3)
                .putBoolean("active", true);
        return new PropertySet()
                .putString("label", label)
                .putBoolean("enabled", true)
                .putInt("count", 12)
                .putFloat("weight", 1.25f)
                .putColorRgba8888("tint", 0x12345678)
                .putObjectStableId("target", objectStableId)
                .putClass("settings", "MapSettings", nested);
    }

    static void assertRepresentativeProperties(PropertySet properties,
                                               String label,
                                               int objectStableId) {
        assertNotNull(properties);
        assertEquals(7, properties.size());
        assertEquals(label, properties.getString("label", null));
        assertTrue(properties.getBoolean("enabled", false));
        assertEquals(12, properties.getInt("count", 0));
        assertEquals(1.25f, properties.getFloat("weight", 0f), 0f);
        assertEquals(0x12345678, properties.getColorRgba8888("tint", 0));
        assertEquals(objectStableId, properties.getObjectStableId("target", -1));
        var settings = properties.getClassValue("settings");
        assertNotNull(settings);
        assertEquals("MapSettings", settings.typeName());
        assertEquals(3, settings.properties().getInt("level", 0));
        assertTrue(settings.properties().getBoolean("active", false));
    }
}
