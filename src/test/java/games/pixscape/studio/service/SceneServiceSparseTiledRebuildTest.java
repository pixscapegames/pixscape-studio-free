package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.component.LayerMetaComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class SceneServiceSparseTiledRebuildTest {
    @Test
    public void rebuildsOrdinaryLayerMapFromMapOwnedStateWithoutLayerComponent() {
        World world = new World(new WorldConfiguration());
        try {
            int layerEntity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
            layer.layerIndex = 0;
            layer.spatialEnabled = true;
            world.getMapper(LayerMetaComponent.class).create(layerEntity).name = "Universal";

            int mapEntity = world.create();
            world.getMapper(EntityIndexComponent.class).create(mapEntity).layerIndex = 0;
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(mapEntity);
            tiled.projection = TiledProjection.ORTHO;
            tiled.tileWidth = 16;
            tiled.tileHeight = 16;
            tiled.mapWidthCells = 8;
            tiled.mapHeightCells = 8;
            tiled.chunkSize = 4;
            tiled.data = tiled.createMapData();
            tiled.data.spatialEnabled = false;
            tiled.data.defaultTileAltitude = 3f;
            tiled.data.defaultTileHeight = 12f;
            tiled.data.setTile(2, 3, 77);

            tiled.spatialEnabled = true;
            tiled.defaultTileAltitude = -1f;
            tiled.defaultTileHeight = -1f;
            tiled.tileXs.add(7);
            tiled.tileYs.add(7);
            tiled.tileAssetIds.add(99);
            tiled.tileTransformFlags.add((byte) 0);
            world.process();

            SceneService.rebuildSparseFromDense(world);

            assertFalse(world.getMapper(LayerComponent.class).has(mapEntity));
            assertFalse(tiled.spatialEnabled);
            assertEquals(3f, tiled.defaultTileAltitude, 0f);
            assertEquals(12f, tiled.defaultTileHeight, 0f);
            assertEquals(1, tiled.tileXs.size);
            assertEquals(2, tiled.tileXs.first());
            assertEquals(3, tiled.tileYs.first());
            assertEquals(77, tiled.tileAssetIds.first());
            assertEquals(1, tiled.tileTransformFlags.size);
        } finally {
            world.dispose();
        }
    }
}
