package games.pixscape.studio.configuration;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.studio.service.SceneSaveTestSupport;

final class RuntimeExportTestSceneSupport {
    private RuntimeExportTestSceneSupport() {
    }

    static void writeTiledScene(FileHandle file, SceneMetaRuntime meta, int... tileAssetIds) {
        writeScene(file, meta, null, tileAssetIds);
    }

    static void writeSpatialScene(FileHandle file, SceneMetaRuntime meta) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 5;
        block.structureId = 1;
        block.name = "North wall";
        block.x = 1f; block.y = 2f; block.width = 3f; block.depth = 1f;
        block.altitude = 4f; block.height = 24f;
        block.addLinkedTileRef(1, 2, 101);
        block.addLinkedTileRef(2, 2, 102);
        writeScene(file, meta, block, new int[0]);
    }

    static void writeSpatialLayerScene(FileHandle file, SceneMetaRuntime meta) {
        writeScene(file, meta, null, new int[0]);
    }

    private static void writeScene(FileHandle file, SceneMetaRuntime meta,
                                   SpatialBlockData block, int... tileAssetIds) {
        meta.nextEntityStableId = 2;
        if (meta.nextPhysicsShapeId <= 0) meta.nextPhysicsShapeId = 1;
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        try {
            int entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 1;
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.type = LayerComponent.TYPE_TILED;
            layer.spatialEnabled = block != null || tileAssetIds.length == 0;
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
            tiled.mapWidthCells = block != null || tileAssetIds.length == 0 ? 4 : tileAssetIds.length;
            tiled.mapHeightCells = block != null || tileAssetIds.length == 0 ? 4 : 1;
            tiled.spatialEnabled = layer.spatialEnabled;
            if (block != null) { tiled.defaultTileAltitude = 2f; tiled.defaultTileHeight = 16f; }
            if (block == null && tileAssetIds.length == 0) { tiled.defaultTileAltitude = 2.5f; tiled.defaultTileHeight = 16f; }
            for (int index = 0; index < tileAssetIds.length; index++) {
                tiled.tileXs.add(index);
                tiled.tileYs.add(0);
                tiled.tileAssetIds.add(tileAssetIds[index]);
                tiled.tileTransformFlags.add((byte) 0);
            }
            if (block != null) {
                SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(entity);
                blocks.nextSpatialBlockId = 6;
                blocks.blocks.add(block);
            }
            world.process();
            SceneSaveTestSupport.save(world, file, meta);
        } finally {
            world.dispose();
        }
    }
}
