package games.pixscape.studio.history.initializer;

import com.artemis.Component;
import com.artemis.World;
import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TiledMapComponent;
import games.pixscape.runtime.component.TiledMapLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;

/** Detached authored components for history and clipboard; never retains grids or a World. */
public final class TiledEntitySnapshot {
    public TiledMapComponent map;
    public TiledMapLayerComponent layer;
    public TiledLayerComponent tiles;
    public LayerParallaxComponent parallax;
    public SpatialBlocksComponent blocks;

    public static TiledEntitySnapshot capture(World world, int entity) {
        if (!world.getMapper(TiledMapComponent.class).has(entity)
                && !world.getMapper(TiledMapLayerComponent.class).has(entity)) {
            return null;
        }
        TiledEntitySnapshot result = new TiledEntitySnapshot();
        result.map = world.getMapper(TiledMapComponent.class).getSafe(entity, null);
        result.layer = world.getMapper(TiledMapLayerComponent.class).getSafe(entity, null);
        result.tiles = world.getMapper(TiledLayerComponent.class).getSafe(entity, null);
        result.parallax = world.getMapper(LayerParallaxComponent.class).getSafe(entity, null);
        result.blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(entity, null);
        return result.copy();
    }

    public TiledEntitySnapshot copy() {
        Json json = new Json();
        return json.fromJson(TiledEntitySnapshot.class, json.toJson(this));
    }

    public void restore(World world, int entity) {
        add(world, entity, map);
        add(world, entity, layer);
        add(world, entity, parallax);
        add(world, entity, blocks);
        if (tiles != null) {
            TiledLayerComponent tiled = add(world, entity, tiles);
            tiled.data = tiled.createLayerData();
            tiled.ensureSparseTileStorageConsistency();
            tiled.data.beginContentMutation();
            try {
                for (int i = 0; i < tiled.tileAssetIds.size; i++) {
                    int x = tiled.tileXs.get(i), y = tiled.tileYs.get(i);
                    tiled.data.setTile(x, y, tiled.tileAssetIds.get(i), tiled.tileTransformFlags.get(i));
                    if (tiled.hasSparseSpatialOverride(i)) {
                        tiled.data.setTileSpatialOverride(x, y, tiled.sparseTileAltitude(i),
                                tiled.sparseTileHeight(i), tiled.sparseTileSpatialFlags(i));
                    }
                }
            } finally {
                tiled.data.endContentMutation();
            }
            var animations = world.getSystem(games.pixscape.runtime.system.TiledAnimationSystem.class);
            if (animations != null) {
                animations.prepareLayer(tiled);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T add(World world, int entity, T component) {
        if (component == null) {
            return null;
        }
        T target = world.edit(entity).create((Class<T>) component.getClass());
        Json json = new Json();
        json.readFields(target, new com.badlogic.gdx.utils.JsonReader().parse(json.toJson(component)));
        return target;
    }
}
