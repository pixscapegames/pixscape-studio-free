package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ChangeLayerOrderCommand;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LayerServiceTiledSwapDirtyTest {
    @Test
    public void universalLayerSwapMovesMixedMultiMapContentsWithoutRebuildingChunks() {
        Harness h = new Harness();
        try {
            int layerA = h.layer("A", LayerComponent.TYPE_CLASSIC, true, 0.75f, false);
            int layerB = h.layer("B", LayerComponent.TYPE_CLASSIC, false, 1.25f, true);
            int sprite = h.sprite(0, 5);
            int mapA1 = h.map(0, 7, false);
            int mapA2 = h.map(0, 3, true);
            int light = h.light(1, 4);
            int mapB1 = h.map(1, 9, false);
            h.prepareForSwap();

            h.history.execute(new ChangeLayerOrderCommand(h.layers, layerA, 1));

            h.assertLayerIndex(layerA, 1);
            h.assertLayerIndex(layerB, 0);
            h.assertContent(sprite, 1, 5);
            h.assertContent(mapA1, 1, 7);
            h.assertContent(mapA2, 1, 3);
            h.assertContent(light, 0, 4);
            h.assertContent(mapB1, 0, 9);
            h.assertOrderDirty(layerA, layerB, sprite, mapA1, mapA2, light, mapB1);
            h.assertCleanChunks(mapA1, mapA2, mapB1);
            h.assertCompositionLayer(mapA1, 1);
            h.assertCompositionLayer(mapA2, 1);
            h.assertCompositionLayer(mapB1, 0);
            assertTrue(Long.compareUnsigned(
                    h.compositionKey(mapB1), h.compositionKey(mapA1)) < 0);
            assertTrue(Long.compareUnsigned(
                    h.compositionKey(mapB1), h.compositionKey(mapA2)) < 0);
            assertTrue(h.layer(layerA).spatialEnabled);
            assertFalse(h.layer(layerB).spatialEnabled);
            assertEquals(0.75f, h.parallax(layerA).factorX, 0f);
            assertEquals(1.25f, h.parallax(layerB).factorX, 0f);
            assertFalse(h.visibility(layerA).visible);
            assertTrue(h.visibility(layerB).visible);

            h.dirty.clearAll();
            h.history.undo();
            h.assertContent(mapA1, 0, 7);
            h.assertContent(mapA2, 0, 3);
            h.assertContent(mapB1, 1, 9);
            h.assertOrderDirty(mapA1, mapA2, mapB1);
            h.assertCleanChunks(mapA1, mapA2, mapB1);
            assertTrue(Long.compareUnsigned(
                    h.compositionKey(mapA1), h.compositionKey(mapB1)) < 0);

            h.dirty.clearAll();
            h.history.redo();
            h.assertContent(mapA1, 1, 7);
            h.assertContent(mapA2, 1, 3);
            h.assertContent(mapB1, 0, 9);
            h.assertOrderDirty(mapA1, mapA2, mapB1);
            h.assertCleanChunks(mapA1, mapA2, mapB1);
            assertTrue(Long.compareUnsigned(
                    h.compositionKey(mapB1), h.compositionKey(mapA1)) < 0);
        } finally {
            h.dispose();
        }
    }

    private static final class Harness {
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(128);
        final World world = new World(new WorldConfiguration().setSystem(dirty));
        final HistoryManager history = new HistoryManager(16);
        final LayerService layers;

        Harness() {
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, new SceneMetaRuntime());
            layers = new LayerService(world, null, history.historyIds(), identities);
        }

        int layer(String name, int type, boolean spatial, float parallax, boolean visible) {
            int index = layers.addLayerTop(name);
            int entity = layers.getLayerEntity(index);
            LayerComponent layer = layer(entity);
            layer.type = type;
            layer.spatialEnabled = spatial;
            LayerParallaxComponent parallaxComponent = world.getMapper(
                    LayerParallaxComponent.class).create(entity);
            parallaxComponent.factorX = parallax;
            parallaxComponent.factorY = parallax;
            visibility(entity).visible = visible;
            return entity;
        }

        int sprite(int layerIndex, int zIndex) {
            int entity = content(layerIndex, zIndex);
            world.getMapper(TextureRegionComponent.class).create(entity);
            return entity;
        }

        int light(int layerIndex, int zIndex) {
            int entity = content(layerIndex, zIndex);
            world.getMapper(PointLightComponent.class).create(entity);
            return entity;
        }

        int map(int layerIndex, int zIndex, boolean spatial) {
            int entity = content(layerIndex, zIndex);
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.projection = TiledProjection.ORTHO;
            tiled.tileWidth = 16;
            tiled.tileHeight = 16;
            tiled.mapWidthCells = 4;
            tiled.mapHeightCells = 4;
            tiled.chunkSize = 4;
            tiled.spatialEnabled = spatial;
            tiled.data = tiled.createMapData();
            tiled.data.spatialEnabled = spatial;
            tiled.data.setTile(0, 0, 100 + entity);
            return entity;
        }

        int content(int layerIndex, int zIndex) {
            int entity = world.create();
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = layerIndex;
            index.zIndex = zIndex;
            return entity;
        }

        void prepareForSwap() {
            world.process();
            IntBag maps = world.getAspectSubscriptionManager()
                    .get(Aspect.all(TiledLayerComponent.class)).getEntities();
            int[] entities = maps.getData();
            for (int i = 0; i < maps.size(); i++) {
                markMapCompiled(entities[i]);
            }
            dirty.clearAll();
        }

        void markMapCompiled(int entity) {
            TiledLayerComponent tiled = tiled(entity);
            TileChunk chunk = tiled.data.getChunk(0, 0);
            chunk.dirtyState = TileChunk.DirtyState.CLEAN;
            chunk.contentDirty = false;
            chunk.collisionDirty = false;
            chunk.visibleLastFrame = true;
            tiled.data.visualBoundsDirty = false;
            tiled.data.hasPreviousChunkWindow = true;
        }

        void assertCleanChunks(int... entities) {
            for (int entity : entities) {
                TiledLayerComponent tiled = tiled(entity);
                TileChunk chunk = tiled.data.getChunk(0, 0);
                assertSame(TileChunk.DirtyState.CLEAN, chunk.dirtyState);
                assertFalse(chunk.contentDirty);
                assertFalse(chunk.collisionDirty);
                assertTrue(chunk.visibleLastFrame);
                assertFalse(tiled.data.visualBoundsDirty);
                assertTrue(tiled.data.hasPreviousChunkWindow);
            }
        }

        void assertOrderDirty(int... entities) {
            for (int entity : entities) {
                assertEquals(DirtyBits.LAYER | DirtyBits.ORDER, dirty.coarseBits(entity));
            }
        }

        void assertLayerIndex(int layerEntity, int expected) {
            assertEquals(expected, layer(layerEntity).layerIndex);
        }

        void assertContent(int entity, int layerIndex, int zIndex) {
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(entity);
            assertEquals(layerIndex, index.layerIndex);
            assertEquals(zIndex, index.zIndex);
        }

        void assertCompositionLayer(int mapEntity, int expectedLayer) {
            assertEquals(expectedLayer, SortKey64.unpackLayerOrdered(compositionKey(mapEntity)));
        }

        long compositionKey(int mapEntity) {
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(mapEntity);
            return SortKey64.packForBlend(
                    0, BlendMode.ALPHA.id, 0, index.layerIndex, index.zIndex, mapEntity);
        }

        LayerComponent layer(int entity) {
            return world.getMapper(LayerComponent.class).get(entity);
        }

        TiledLayerComponent tiled(int entity) {
            return world.getMapper(TiledLayerComponent.class).get(entity);
        }

        LayerParallaxComponent parallax(int entity) {
            return world.getMapper(LayerParallaxComponent.class).get(entity);
        }

        VisibilityComponent visibility(int entity) {
            VisibilityComponent visibility = world.getMapper(VisibilityComponent.class)
                    .getSafe(entity, null);
            return visibility != null ? visibility
                    : world.getMapper(VisibilityComponent.class).create(entity);
        }

        void dispose() {
            world.dispose();
        }
    }
}
