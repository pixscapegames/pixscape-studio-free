package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;

/** Indexed resolver for map ownership and the temporary TYPE_TILED compatibility host. */
public final class TiledMapHostResolver {
    private final World world;
    private final ComponentMapper<LayerComponent> layers;
    private final ComponentMapper<EntityIndexComponent> indexes;
    private final EntitySubscription maps;
    private final IntMap<IntArray> mapsByLayerIndex = new IntMap<>();
    private boolean dirty = true;

    public TiledMapHostResolver(World world) {
        this.world = world;
        this.layers = world.getMapper(LayerComponent.class);
        this.indexes = world.getMapper(EntityIndexComponent.class);
        this.maps = world.getAspectSubscriptionManager().get(
                Aspect.all(TiledLayerComponent.class, EntityIndexComponent.class)
                        .exclude(LayerComponent.class));
        this.maps.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                dirty = true;
            }

            @Override
            public void removed(IntBag entities) {
                dirty = true;
            }
        });
    }

    /** Marks the index stale after an authored layer-index change. */
    public void invalidate() {
        dirty = true;
    }

    /** Publishes a newly materialized map before the next Artemis subscription synchronization. */
    public void register(int mapEntityId) {
        rebuildIfDirty();
        EntityIndexComponent index = indexes.getSafe(mapEntityId, null);
        if (index == null) return;
        IntArray matches = mapsByLayerIndex.get(index.layerIndex);
        if (matches == null) {
            matches = new IntArray();
            mapsByLayerIndex.put(index.layerIndex, matches);
        }
        if (!matches.contains(mapEntityId)) matches.add(mapEntityId);
    }

    public int findForHost(int hostLayerEntityId) {
        LayerComponent host = layers.getSafe(hostLayerEntityId, null);
        if (host == null || host.type != LayerComponent.TYPE_TILED) return -1;
        return findForLayerIndex(host.layerIndex);
    }

    public int requireForHost(int hostLayerEntityId) {
        int mapEntityId = findForHost(hostLayerEntityId);
        if (mapEntityId < 0) {
            throw new IllegalStateException(
                    "TYPE_TILED host entity " + hostLayerEntityId
                            + " must own exactly one Tiled map.");
        }
        return mapEntityId;
    }

    public int findForLayerIndex(int layerIndex) {
        rebuildIfDirty();
        IntArray matches = mapsByLayerIndex.get(layerIndex);
        if (matches == null || matches.size == 0) return -1;
        if (matches.size > 1) {
            throw new IllegalStateException(
                    "TYPE_TILED host layerIndex=" + layerIndex
                            + " owns " + matches.size + " Tiled maps; expected exactly one.");
        }
        return matches.get(0);
    }

    /** Validates mixed ownership: ordinary layers own zero/many maps, TYPE_TILED owns exactly one. */
    public void validateWorld() {
        rebuildIfDirty();
        IntBag hosts = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class).exclude(EntityIndexComponent.class)).getEntities();
        int[] hostData = hosts.getData();
        IntMap<Integer> layersByIndex = new IntMap<>();
        IntMap<Integer> tiledHosts = new IntMap<>();
        for (int i = 0; i < hosts.size(); i++) {
            int entityId = hostData[i];
            LayerComponent layer = layers.get(entityId);
            if (layersByIndex.containsKey(layer.layerIndex)) {
                throw new IllegalStateException(
                        "Multiple Pixscape Layers use layerIndex=" + layer.layerIndex + ".");
            }
            layersByIndex.put(layer.layerIndex, entityId);
            if (layer.type == LayerComponent.TYPE_TILED) tiledHosts.put(layer.layerIndex, entityId);
        }

        for (IntMap.Entry<IntArray> entry : mapsByLayerIndex) {
            if (!layersByIndex.containsKey(entry.key)) {
                throw new IllegalStateException(
                        "Tiled map does not belong to a Pixscape Layer layerIndex=" + entry.key + ".");
            }
        }
        for (IntMap.Entry<Integer> host : tiledHosts) {
            IntArray ownedMaps = mapsByLayerIndex.get(host.key);
            if (ownedMaps == null || ownedMaps.size != 1) {
                throw new IllegalStateException(
                        "TYPE_TILED host entity " + host.value + " at layerIndex=" + host.key
                                + " must own exactly one Tiled map.");
            }
        }
    }

    private void rebuildIfDirty() {
        if (!dirty) return;
        dirty = false;
        mapsByLayerIndex.clear();
        IntBag entities = maps.getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = data[i];
            if (!world.getEntityManager().isActive(entityId)) continue;
            EntityIndexComponent index = indexes.get(entityId);
            IntArray matches = mapsByLayerIndex.get(index.layerIndex);
            if (matches == null) {
                matches = new IntArray();
                mapsByLayerIndex.put(index.layerIndex, matches);
            }
            matches.add(entityId);
        }
    }
}
