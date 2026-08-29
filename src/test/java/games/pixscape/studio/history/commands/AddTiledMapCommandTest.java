package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
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
    private int layerEntity;

    @Before
    public void setUp() {
        world = new World(new WorldConfiguration());
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
        SceneMeta meta = config.getCurrentSceneMeta();
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        layers = new LayerService(world, null, new HistoryIdRegistry(), identities);
        layerEntity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = 0;
        layer.type = LayerComponent.TYPE_CLASSIC;
        world.getMapper(LayerMetaComponent.class).create(layerEntity).name = "Universal";
        world.process();
        layers.rebuildFromWorld();
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

        assertEquals(LayerComponent.TYPE_CLASSIC,
                world.getMapper(LayerComponent.class).get(layerEntity).type);
        assertTrue(layers.isUniversalLayerEntity(layerEntity));
        assertNotEquals(isoId, orthoId);
        assertEquals("scene1", world.getMapper(TiledLayerComponent.class).get(isoId).atlasTag);
        assertEquals("scene1", world.getMapper(TiledLayerComponent.class).get(orthoId).atlasTag);
        assertMap(isoId, 0, 0, 64, 32, TiledProjection.ISO, 64, 32);
        assertMap(orthoId, 0, 1, 20, 10, TiledProjection.ORTHO, 32, 32);
        assertTrue("Map content must not consume the Layer's add capability",
                layers.isUniversalLayerEntity(layerEntity));
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
        assertEquals(LayerComponent.TYPE_CLASSIC,
                world.getMapper(LayerComponent.class).get(restoredLayer).type);
        assertEquals(2, mapCount());
    }

    @Test
    public void genericLayerMoveChangesOnlyMapOwnershipAndIsUndoable() {
        AtomicInteger selected = new AtomicInteger(-1);
        command(13, 9, TiledProjection.ISO, 40, 20, selected).redo();
        int map = selected.get();
        int secondLayer = world.create();
        LayerComponent target = world.getMapper(LayerComponent.class).create(secondLayer);
        target.layerIndex = 1;
        target.type = LayerComponent.TYPE_CLASSIC;
        target.spatialEnabled = true;
        world.getMapper(LayerMetaComponent.class).create(secondLayer).name = "Other";
        world.process();
        layers.rebuildFromWorld();
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
        source.type = LayerComponent.TYPE_CLASSIC;
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
        assertTrue(layers.isUniversalLayerEntity(layerEntity));

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
}
