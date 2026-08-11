package games.pixscape.studio.service;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.LayerInitializer;
import games.pixscape.studio.service.tiled.TiledAllocatorService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Single source of truth for layers.
 * Invariant: layerEntities.get(i) == entityId of the layer entity d'index i (0..N-1).
 * Update only on user actions (add/up/down/remove). No per-frame update.
 */
public final class LayerService {
    private final World world;

    private final ComponentMapper<LayerComponent> mL;
    private final ComponentMapper<LayerMetaComponent> mMeta;
    private final ComponentMapper<LayerParallaxComponent> mPar;
    private final ComponentMapper<VisibilityComponent> mVis;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final DirtyTrackerSystem dirtyTracker;
    private final EntitySubscription layerSub;
    private final HistoryIdRegistry historyIds;
    private final IdentityRegistry identityRegistry;
    private final TiledAllocatorService tiledAllocatorService;
    private boolean dirty = true;

    /**
     * index -> entityId (dense, sorted by index)
     */
    private final IntArray layerEntities = new IntArray(true, 8);
    private final int MY_TAG = EventFlow.tag(this);

    public LayerService(World world, TiledAllocatorService tiledAllocatorService,
                        HistoryIdRegistry historyIds, IdentityRegistry identityRegistry) {
        this.world = world;
        this.tiledAllocatorService = tiledAllocatorService;
        this.historyIds = historyIds;
        this.identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
        this.mL = world.getMapper(LayerComponent.class);
        this.mMeta = world.getMapper(LayerMetaComponent.class);
        this.mPar = world.getMapper(LayerParallaxComponent.class);
        this.mVis = world.getMapper(VisibilityComponent.class);
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);

        AspectSubscriptionManager asm = world.getAspectSubscriptionManager();
        this.layerSub = asm.get(Aspect.all(LayerComponent.class, LayerMetaComponent.class));

        layerSub.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
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

    public World getWorld() {
        return world;
    }

    public HistoryIdRegistry historyIds() {
        return historyIds;
    }

    private void rebuildIfDirty() {
        if (!dirty) return;
        dirty = false;

        layerEntities.clear();

        IntBag bag = layerSub.getEntities();
        int[] data = bag.getData();
        int n = bag.size();

        // 1) Trouver le layerIndex max
        int maxIndex = -1;
        for (int i = 0; i < n; i++) {
            int e = data[i];
            int idx = mL.get(e).layerIndex;
            if (idx > maxIndex) maxIndex = idx;
        }
        if (maxIndex < 0) return; // no layer

        // 2) Prepare an array indexed by layerIndex (0..maxIndex)
        layerEntities.ensureCapacity(maxIndex + 1);
        layerEntities.clear();
        for (int i = 0; i <= maxIndex; i++) {
            layerEntities.add(-1); // -1 = slot vide
        }

        // 3) Remplir : layerEntities[layerIndex] = entityId
        for (int i = 0; i < n; i++) {
            int e = data[i];
            int idx = mL.get(e).layerIndex;
            layerEntities.set(idx, e);
        }
    }

    public TiledAllocatorService getTiledAllocatorService() {
        return tiledAllocatorService;
    }

    public void rebuildFromWorld() {
        dirty = true;
        rebuildIfDirty();
    }

    // ---------- O(1) read access ----------

    public int count() {
        rebuildIfDirty();
        // Si tu garantis layerIndex dense (0..N-1), tu peux retourner layerEntities.size
        // Sinon, tu peux compter les >=0
        int max = layerEntities.size;
        int c = 0;
        for (int i = 0; i < max; i++) {
            if (layerEntities.get(i) != -1) c++;
        }
        return c;
    }

    /**
     * Renvoie l'entityId du layer i (O(1)).
     */
    public int getLayerEntity(int index) {
        rebuildIfDirty();
        if (index < 0 || index >= layerEntities.size) return -1;
        return layerEntities.get(index);
    }

    /**
     * Returns the logical index of a layer from its entityId.
     */
    public int getIndexForEntity(int layerEntityId) {
        if (layerEntityId == -1) return 0;
        LayerComponent li = mL.get(layerEntityId);
        return li != null ? li.layerIndex : 0;
    }

    public int getLayerTypeByIndex(int index) {
        int e = getLayerEntity(index);
        if (e == -1) return LayerComponent.TYPE_CLASSIC;
        LayerComponent lc = mL.getSafe(e, null);
        return (lc != null) ? lc.type : LayerComponent.TYPE_CLASSIC;
    }

    public int getLayerTypeByEntity(int layerEntityId) {
        if (layerEntityId == -1) return LayerComponent.TYPE_CLASSIC;
        LayerComponent lc = mL.getSafe(layerEntityId, null);
        return (lc != null) ? lc.type : LayerComponent.TYPE_CLASSIC;
    }

    /** Returns whether the scene already contains its single actor Spatial layer. */
    public boolean hasSpatialActorLayer() {
        rebuildIfDirty();
        for (int i = 0; i < layerEntities.size; i++) {
            int layerEntity = layerEntities.get(i);
            LayerComponent layer = mL.getSafe(layerEntity, null);
            if (isSpatialActorLayer(layer)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSpatialActorLayer(LayerComponent layer) {
        return layer != null && isSpatialActorLayer(layer.type, layer.spatialEnabled);
    }

    public static boolean isSpatialActorLayer(int type, boolean spatialEnabled) {
        return type == LayerComponent.TYPE_PHYSICS && spatialEnabled;
    }

    public LayerMetaComponent meta(int index) {
        rebuildIfDirty();
        int e = layerEntities.get(index);
        return e >= 0 ? mMeta.get(e) : null;
    }

    /**
     * Returns the first existing layer, or -1 if there is none.
     */
    public int getFirstLayerEntity() {
        rebuildIfDirty();
        for (int i = 0; i < layerEntities.size; i++) {
            int layerEntity = layerEntities.get(i);
            if (layerEntity != -1) {
                return layerEntity;
            }
        }
        return -1;
    }

    public int indexOfLayerEntity(int entityId) {
        if (entityId == -1) {
            return -1;
        }
        for (int i = 0; i < layerEntities.size; i++) {
            if (layerEntities.get(i) == entityId) {
                return i;
            }
        }
        return -1;
    }

    public LayerParallaxComponent parallax(int index) {
        int e = getLayerEntity(index);
        return e != -1 ? mPar.getSafe(e, null) : null;
    }

    // ---------- Mutations (actions utilisateur) ----------

    /**
     * Adds a layer at the top, returns its index.
     */
    public int addLayerTop(String name) {
        return addLayerTop(name, LayerComponent.TYPE_CLASSIC);
    }

    public int addLayerTop(String name, int type) {
        int idx = layerEntities.size;
        String effectiveName = (name != null ? name : "Layer " + idx);
        LayerInitializer initializer = new LayerInitializer(world, tiledAllocatorService).configureNewLayer(effectiveName, idx, type);
        insertLayerAt(idx, initializer);
        return idx;
    }

    public int insertLayerAt(int index, LayerInitializer initializer) {
        int clampedIndex = Math.max(0, Math.min(index, layerEntities.size));
        int e = world.create();

        try {
            historyIds.ensureForEntity(e);
            initializer.init(e);
            identityRegistry.ensureStableId(e);
        } catch (RuntimeException failure) {
            TiledLayerComponent tiled = mTiled.getSafe(e, null);
            if (tiled != null && tiledAllocatorService != null) tiledAllocatorService.freeLayer(tiled);
            IdentityRegistry.unindexEntityImmediately(world, e);
            historyIds.unbindEntity(e);
            world.delete(e);
            throw failure;
        }

        if (clampedIndex < layerEntities.size) {
            shiftItemsForInsert(clampedIndex);
        }

        layerEntities.insert(clampedIndex, e);
        renumberLayerIndices();

        // IMPORTANT : a coherent cache has just been built manually.
        // Do not let rebuildIfDirty() discard it before the subscription is up to date.
        dirty = false;

        EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
        return e;
    }


    public int insertLayerSnapshot(int index, LayerSnapshot snapshot) {
        if (snapshot == null) {
            return -1;
        }
        int layerEntityId = insertLayerAt(index, snapshot.layerInitializer);
        historyIds.bind(layerEntityId, snapshot.layerHistoryId);

        for (DrawableSnapshot drawable : snapshot.drawables) {
            GenericEntityInitializer init = drawable.initializer;
            init.overrideLayerIndex(index);

            int e = world.create();
            init.init(e);
            historyIds.bind(e, drawable.historyId);
        }

        return layerEntityId;
    }

    public LayerSnapshot snapshotLayer(int index) {
        if (index < 0 || index >= layerEntities.size) {
            return null;
        }

        int layerEntityId = layerEntities.get(index);
        long layerHistoryId = historyIds.ensureForEntity(layerEntityId);

        LayerInitializer initializer = new LayerInitializer(world, tiledAllocatorService);
        initializer.syncFrom(layerEntityId);

        List<DrawableSnapshot> drawables = new ArrayList<>();

        // micro-opt: if layerSub or a dedicated LayerIndex subscription already exists, reuse it.
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class))
                .getEntities();

        int[] data = bag.getData();
        int n = bag.size();

        for (int i = 0; i < n; i++) {
            int e = data[i];

            EntityIndexComponent ei = mEntityIndex.getSafe(e, null);
            if (ei == null || ei.getLayerIndex() != index) {
                continue;
            }

            long drawableHistoryId = historyIds.ensureForEntity(e); // ✅ e, not layerEntityId

            GenericEntityInitializer init = new GenericEntityInitializer(world);
            init.syncFrom(e);

            int z = ei.getZIndex();
            drawables.add(new DrawableSnapshot(drawableHistoryId, init, z, e));
        }

        drawables.sort(Comparator
                .comparingInt((DrawableSnapshot d) -> d.z)
                .thenComparingLong(d -> d.historyId)
                .thenComparingInt(d -> d.entityId));

        return new LayerSnapshot(index, layerHistoryId, initializer, drawables);
    }


    /**
     * Supprime le layer (cascade) :
     * 1) deletes all entities that reference this layer through EntityIndexComponent,
     * 2) deletes the layer entity,
     * 3) recalcule les indices des layers restants (LayerComponent + tableau).
     */
    public void removeLayerCascade(int index) {
        if (index < 0 || index >= layerEntities.size) return;

        int eLayer = layerEntities.get(index);

        // 0) If this is a tiled layer -> release BEFORE deletion
        if (mTiled.has(eLayer)) {
            TiledLayerComponent tiled = mTiled.get(eLayer);
            // Allocator release + slot deactivation
            tiledAllocatorService.freeLayer(tiled);
        }

        // 1) Delete all entities drawables de ce layer
        deleteDrawablesAtLayerIndex(index);

        // 2) Delete the layer entity
        IdentityRegistry.unindexEntityImmediately(world, eLayer);
        world.delete(eLayer);
        historyIds.unbindEntity(eLayer);

        // 3) Compacter
        layerEntities.removeIndex(index);
        shiftItemsForRemove(index);
        renumberLayerIndices();

        // 4) Forcer rebuild GPU des autres tiled layers
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            TiledLayerComponent t = mTiled.get(data[i]);
            if (t != null && t.data != null) {
                t.data.markAllChunksContentDirty();
            }
        }

        EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
    }

    /**
     * Moves the layer up one step (swap with index+1).
     */
    public void moveLayerUp(int index) {
        moveLayer(index, index + 1);
    }

    /**
     * Moves the layer down one step (swap with index-1).
     */
    public void moveLayerDown(int index) {
        moveLayer(index, index - 1);
    }

    /**
     * Moves a layer from fromIndex to toIndex (target index clamped to [0, size-1]).
     *
     * @return true if a structural move was applied, false otherwise.
     */
    public boolean moveLayer(int fromIndex, int toIndex) {
        int size = layerEntities.size;
        if (fromIndex < 0 || fromIndex >= size || size <= 1) {
            return false;
        }

        int target = Math.max(0, Math.min(toIndex, size - 1));
        if (target == fromIndex) {
            return false;
        }

        int current = fromIndex;
        while (current < target) {
            swapLayers(current, current + 1, false);
            current++;
        }
        while (current > target) {
            swapLayers(current - 1, current, false);
            current--;
        }

        EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
        return true;
    }

    private void swapLayers(int i, int j, boolean publishEvent) {
        if (i == j) return;

        int eI = layerEntities.get(i);
        int eJ = layerEntities.get(j);

        // Swap indices logiques (LayerComponent)
        LayerComponent li = mL.get(eI);
        LayerComponent lj = mL.get(eJ);

        // 1) swap des indices logiques via getters/setters
        int idxI = li.layerIndex;
        int idxJ = lj.layerIndex;

        if (li.layerIndex != idxJ) {
            li.layerIndex = idxJ;
            if (dirtyTracker != null) {
                dirtyTracker.layer(eI);
                dirtyTracker.order(eI);
            }
        }
        if (lj.layerIndex != idxI) {
            lj.layerIndex = idxI;
            if (dirtyTracker != null) {
                dirtyTracker.layer(eJ);
                dirtyTracker.order(eJ);
            }
        }
        if (li.type == LayerComponent.TYPE_TILED) {
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(eI);
            if (tiled != null && tiled.data != null)
                tiled.data.markAllChunksContentDirty();
        }

        if (lj.type == LayerComponent.TYPE_TILED) {
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(eJ);
            if (tiled != null && tiled.data != null)
                tiled.data.markAllChunksContentDirty();
        }

        swapItemsLayerIndices(idxI, idxJ);
        markLayerContentsDirty(idxI);
        markLayerContentsDirty(idxJ);

        // 2) swap in the array so list order reflects the swap
        layerEntities.set(i, eJ);
        layerEntities.set(j, eI);

        if (publishEvent) {
            EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
        }
    }


    /**
     * Renumbers LayerComponent values to match layerEntities array order.
     */
    private void renumberLayerIndices() {
        for (int i = 0; i < layerEntities.size; i++) {
            int e = layerEntities.get(i);
            LayerComponent li = mL.get(e);
            if (li != null) {
                if (li.layerIndex != i) {
                    li.layerIndex = i;
                    if (dirtyTracker != null) {
                        dirtyTracker.layer(e);
                        dirtyTracker.order(e);
                    }
                }
                markLayerContentsDirty(i);
            }
        }
    }

    private void shiftItemsForInsert(int insertIndex) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class))
                .getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            EntityIndexComponent index = mEntityIndex.getSafe(e, null);
            if (index != null && index.getLayerIndex() >= insertIndex) {
                index.layerIndex = index.getLayerIndex() + 1;
                if (dirtyTracker != null) {
                    dirtyTracker.layer(e);
                    dirtyTracker.order(e);
                }
            }
        }
    }

    private void shiftItemsForRemove(int removedIndex) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class))
                .getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            EntityIndexComponent index = mEntityIndex.getSafe(e, null);
            if (index != null && index.getLayerIndex() > removedIndex) {
                index.layerIndex = index.getLayerIndex() - 1;
                if (dirtyTracker != null) {
                    dirtyTracker.layer(e);
                    dirtyTracker.order(e);
                }
            }
        }
    }

    private void swapItemsLayerIndices(int indexA, int indexB) {
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class))
                .getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            EntityIndexComponent index = mEntityIndex.getSafe(e, null);
            if (index == null) continue;
            int current = index.getLayerIndex();
            if (current == indexA) {
                index.layerIndex = indexB;
                if (dirtyTracker != null) {
                    dirtyTracker.layer(e);
                    dirtyTracker.order(e);
                }
            } else if (current == indexB) {
                index.layerIndex = indexA;
                if (dirtyTracker != null) {
                    dirtyTracker.layer(e);
                    dirtyTracker.order(e);
                }
            }
        }
    }

    private void markLayerContentsDirty(int layerIndex) {
        if (layerIndex < 0) {
            return;
        }
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class))
                .getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            EntityIndexComponent index = mEntityIndex.getSafe(e, null);
            if (index != null && index.getLayerIndex() == layerIndex) {
                if (dirtyTracker != null) {
                    dirtyTracker.layer(e);
                    dirtyTracker.order(e);
                }
            }
        }
    }

    // ------------------------------------------------------------
// TILED block rules
// ------------------------------------------------------------

    // ---------- Helpers drawables ----------

    /**
     * Deletes all entities that reference this layer through EntityIndexComponent.
     * Cascade semantics: layer content is destroyed with the layer.
     */
    private void deleteDrawablesAtLayerIndex(int layerIndex) {
        if (layerIndex < 0) return;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class))
                .getEntities();
        int[] data = bag.getData();
        int n = bag.size();

        for (int i = 0; i < n; i++) {
            int e = data[i];
            EntityIndexComponent index = mEntityIndex.getSafe(e, null);
            if (index != null && index.getLayerIndex() == layerIndex) {
                IdentityRegistry.unindexEntityImmediately(world, e);
                historyIds.unbindEntity(e);
                world.delete(e);
            }
        }
    }

    // ---------- UI bridge (for LayersPanel, etc.) ----------

    /**
     * Builds the LayerUI list for display in the editor.
     * Pure read, no side effects.
     */
    public Array<LayerUI> getLayerUIs() {
        Array<LayerUI> list = new Array<>(layerEntities.size);
        for (int i = 0; i < layerEntities.size; i++) {
            int e = layerEntities.get(i);
            LayerMetaComponent meta = mMeta.getSafe(e, null);
            LayerComponent lc = mL.getSafe(e, null);
            VisibilityComponent visibleC = mVis.getSafe(e, null);
            String name = (meta != null && meta.name != null) ? meta.name : "unnamed layer";
            String description = (meta != null && meta.description != null) ? meta.description : "";
            boolean visible = visibleC != null && visibleC.isVisible();
            boolean locked = meta != null && meta.locked;
            int type = lc != null ? lc.type : LayerComponent.TYPE_CLASSIC;
            boolean spatialEnabled = lc != null && lc.spatialEnabled;
            list.add(new LayerUI(e, name, description, i, type, spatialEnabled, visible, locked));
        }
        return list;
    }

    /**
     * Returns the readable layer name from its entityId.
     * If there is no explicit name, returns "Layer X" with its logical index.
     */
    public String getName(int layerEntityId) {
        if (layerEntityId < 0) {
            return "";
        }
        LayerMetaComponent meta = mMeta.getSafe(layerEntityId, null);
        if (meta != null && meta.name != null && !meta.name.isEmpty()) {
            return meta.name;
        }
        int idx = getIndexForEntity(layerEntityId);
        return "Layer " + idx;
    }

    /**
     * Variante pratique : nom par index logique.
     */
    public String getNameByIndex(int index) {
        int e = getLayerEntity(index);
        return getName(e);
    }


    /**
     * Changes visibility for the layer matching this entityId.
     */
    public void setLayerVisible(int layerId, boolean visible) {
        if (layerId == -1) return;
        VisibilityComponent vis = mVis.has(layerId) ? mVis.get(layerId) : mVis.create(layerId);
        if (vis.visible != visible) {
            vis.visible = visible;
        }
    }

    /**
     * Changes the layer locked state (editing).
     */
    public void setLayerLocked(int entityId, boolean locked) {
        LayerMetaComponent meta = mMeta.getSafe(entityId, null);
        if (meta == null) return;
        if (meta.locked == locked) return;
        meta.locked = locked;
        EventFlow.i().publish(new EventFlow.LayerLockChanged(entityId, locked, 0));
    }

    public boolean isLayerLocked(int entityId) {
        LayerMetaComponent meta = mMeta.getSafe(entityId, null);
        return meta.locked;
    }

    public void setLayerType(int layerEntityId, int type) {
        if (layerEntityId == -1) return;

        LayerComponent lc = mL.getSafe(layerEntityId, null);
        if (lc == null) return;

        int norm = normalizeLayerType(type);
        if (lc.type == norm) return;

        lc.type = norm;

        if (dirtyTracker != null) {
            dirtyTracker.layer(layerEntityId);
            dirtyTracker.order(layerEntityId);
        }

        dirty = true;

        EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
    }

    private static int normalizeLayerType(int type) {
        return switch (type) {
            case LayerComponent.TYPE_CLASSIC,
                 LayerComponent.TYPE_PHYSICS,
                 LayerComponent.TYPE_LIGHT,
                 LayerComponent.TYPE_TILED -> type;
            default -> LayerComponent.TYPE_CLASSIC;
        };
    }


    public void reset() {
        layerEntities.clear();
        dirty = true;
    }

    public static String typeDisplayName(int type) {
        return typeDisplayName(type, false);
    }

    public static String typeDisplayName(int type, boolean spatialEnabled) {
        if (isSpatialActorLayer(type, spatialEnabled)) {
            return "Spatial";
        }
        return switch (type) {
            case LayerComponent.TYPE_PHYSICS -> "Physics";
            case LayerComponent.TYPE_LIGHT -> "Light";
            case LayerComponent.TYPE_TILED -> "Tiled";
            default -> "Classic";
        };
    }

    public static String typeSuffixLabel(int type) {
        return typeSuffixLabel(type, false);
    }

    public static String typeSuffixLabel(int type, boolean spatialEnabled) {
        if (type == LayerComponent.TYPE_CLASSIC) {
            return "";
        }
        return "(" + typeDisplayName(type, spatialEnabled) + ")";
    }

    public record LayerUI(int layerEntityId, String name, String description, int index, int type,
                          boolean spatialEnabled, boolean visible, boolean locked) {
    }

    public record LayerSnapshot(int index, long layerHistoryId, LayerInitializer layerInitializer,
                                List<DrawableSnapshot> drawables) {
        public LayerSnapshot(int index, long layerHistoryId, LayerInitializer layerInitializer, List<DrawableSnapshot> drawables) {
            this.index = index;
            this.layerHistoryId = layerHistoryId;
            this.layerInitializer = layerInitializer;
            this.drawables = List.copyOf(drawables);
        }
    }

    public static final class DrawableSnapshot {
        public final long historyId;
        final GenericEntityInitializer initializer;
        final int z;
        final int entityId;

        DrawableSnapshot(long historyId, GenericEntityInitializer initializer, int z, int entityId) {
            this.historyId = historyId;
            this.initializer = initializer;
            this.z = z;
            this.entityId = entityId;
        }
    }
}
