package games.pixscape.studio.service;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.commands.DeleteGameObjectHierarchyCommand;
import games.pixscape.studio.history.initializer.LayerInitializer;
import games.pixscape.studio.history.initializer.TiledMapInitializer;
import games.pixscape.studio.service.tiled.TiledAllocatorService;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Single source of truth for layers.
 * Invariant: layerEntities.get(i) == entityId of the layer entity d'index i (0..N-1).
 * Update only on user actions (add/up/down/remove). No per-frame update.
 */
public final class LayerService {
    private final World world;

    private final ComponentMapper<LayerComponent> mL;
    private final ComponentMapper<LayerMetaComponent> mMeta;
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

    /**
     * Current authored ownership for UI actions/commands, O(ancestry depth), never a scene scan.
     * -1 explicitly means unavailable (including a broken hierarchy), not the child's stored index.
     */
    public int getEffectiveLayerIndex(int entityId) {
        try {
            return CurrentGameObjectRelations.layerIndex(world, entityId);
        } catch (IllegalArgumentException unavailable) {
            return -1;
        }
    }

    public int getEffectiveLayerEntity(int entityId) {
        return getLayerEntity(getEffectiveLayerIndex(entityId));
    }

    /**
     * O(1) ownership for picking/render readers of the last ECS publication. No topology refresh.
     */
    public int getPublishedEffectiveLayerIndex(int entityId) {
        return CurrentGameObjectRelations.publishedLayerIndex(world, entityId);
    }

    public String describeEffectiveLayer(int entityId) {
        int layer = getEffectiveLayerEntity(entityId);
        if (layer < 0) return "Unavailable (invalid layer ownership)";
        String name = getName(layer);
        return world.getMapper(GameObjectMemberComponent.class).has(entityId)
                ? name + "\n(inherited)" : name;
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

    /**
     * Materializes one map as normal content of an existing Pixscape layer.
     */
    public int insertTiledMap(TiledMapInitializer initializer, long historyId) {
        Objects.requireNonNull(initializer, "initializer");
        int mapEntityId = world.create();
        try {
            initializer.init(mapEntityId);
            identityRegistry.ensureStableId(mapEntityId);
            if (historyId > 0L) historyIds.bind(mapEntityId, historyId);
            else historyIds.ensureForEntity(mapEntityId);
            EventFlow.i().publish(new EventFlow.TiledMapContentChanged(mapEntityId, MY_TAG));
            return mapEntityId;
        } catch (RuntimeException failure) {
            TiledLayerComponent tiled = mTiled.getSafe(mapEntityId, null);
            if (tiled != null && tiledAllocatorService != null) tiledAllocatorService.freeLayer(tiled);
            IdentityRegistry.unindexEntityImmediately(world, mapEntityId);
            historyIds.unbindEntity(mapEntityId);
            world.delete(mapEntityId);
            throw failure;
        }
    }

    void rebuildFromWorld() {
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
    private int getIndexForEntity(int layerEntityId) {
        if (layerEntityId == -1) return 0;
        LayerComponent li = mL.get(layerEntityId);
        return li != null ? li.layerIndex : 0;
    }

    /**
     * Returns whether the entity is a real Pixscape Layer.
     */
    public boolean isLayerEntity(int layerEntityId) {
        return mL.getSafe(layerEntityId, null) != null;
    }

    /**
     * Returns whether another ordinary layer already owns the Spatial actor slot.
     */
    public boolean hasOtherSpatialActorLayer(int currentLayerEntityId) {
        rebuildIfDirty();
        for (int i = 0; i < layerEntities.size; i++) {
            int layerEntity = layerEntities.get(i);
            if (layerEntity == currentLayerEntityId) continue;
            LayerComponent layer = mL.getSafe(layerEntity, null);
            if (isSpatialActorLayer(layer)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSpatialActorLayer(LayerComponent layer) {
        return layer != null && layer.spatialEnabled;
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

    // ---------- Mutations (actions utilisateur) ----------

    /**
     * Adds a layer at the top, returns its index.
     */
    public int addLayerTop(String name) {
        int idx = layerEntities.size;
        String effectiveName = (name != null ? name : "Layer " + idx);
        LayerInitializer initializer = new LayerInitializer(world)
                .configureNewLayer(effectiveName, idx);
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
        Objects.requireNonNull(snapshot, "snapshot");
        int layerEntityId = insertLayerAt(index, snapshot.layerInitializer);
        historyIds.bind(layerEntityId, snapshot.layerHistoryId);
        if (snapshot.contents != null) snapshot.contents.undo();
        return layerEntityId;
    }

    public LayerSnapshot snapshotLayer(int index) {
        return snapshotLayer(index, null);
    }

    /**
     * Cold command preparation; current identities include creations before ECS publication.
     */
    public LayerSnapshot snapshotLayer(int index, IntConsumer onRestoredEntity) {
        int layerEntity = getLayerEntity(index);
        if (layerEntity < 0) throw new IllegalArgumentException("Layer deletion requires an existing scene layer.");
        IntArray entities = new IntArray();
        identityRegistry.collectIndexedEntityIds(entities);
        IntBag validationEntities = new IntBag(entities.size);
        for (int i = 0; i < entities.size; i++) validationEntities.add(entities.get(i));
        new games.pixscape.runtime.hierarchy.GameObjectHierarchyValidator(world, identityRegistry)
                .validateEntities(validationEntities);
        IntArray roots = new IntArray();
        var members = world.getMapper(GameObjectMemberComponent.class);
        for (int i = 0; i < entities.size; i++) {
            int entity = entities.get(i);
            if (!mEntityIndex.has(entity) || members.has(entity)) continue;
            // Roots determine membership; the hierarchy command captures their descendants once.
            if (CurrentGameObjectRelations.layerIndex(world, entity) == index) roots.add(entity);
        }
        LayerInitializer initializer = new LayerInitializer(world);
        initializer.syncFrom(layerEntity);
        var contents = roots.isEmpty() ? null : new DeleteGameObjectHierarchyCommand(
                world, historyIds, roots, onRestoredEntity, tiledAllocatorService);
        return new LayerSnapshot(index, historyIds.ensureForEntity(layerEntity), initializer, contents);
    }

    public void removeLayerCascade(int index) {
        removeLayerSnapshot(snapshotLayer(index));
    }

    /**
     * Applies exactly the prepared set, including dependent joints, without recapturing on redo.
     */
    public void removeLayerSnapshot(LayerSnapshot snapshot) {
        int entity = historyIds.entityOfHistoryId(snapshot.layerHistoryId);
        int index = indexOfLayerEntity(entity);
        if (index != snapshot.index || entity < 0) {
            throw new IllegalStateException("Layer deletion target changed since capture.");
        }
        if (snapshot.contents != null) snapshot.contents.redo();
        IdentityRegistry.unindexEntityImmediately(world, entity);
        historyIds.unbindEntity(entity);
        world.delete(entity);
        layerEntities.removeIndex(index);
        shiftItemsForRemove(index);
        renumberLayerIndices();
        dirty = false; // The explicit cache is already current before the next ECS publication.
        EventFlow.i().publish(new EventFlow.LayerOrderChanged(MY_TAG));
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

    private void shiftItemsForInsert(int index) {
        shiftRootLayerIndices(index, 1);
    }

    private void shiftItemsForRemove(int index) {
        shiftRootLayerIndices(index + 1, -1);
    }

    private void shiftRootLayerIndices(int firstIndex, int delta) {
        IntArray entities = new IntArray();
        identityRegistry.collectIndexedEntityIds(entities);
        var members = world.getMapper(GameObjectMemberComponent.class);
        for (int i = 0; i < entities.size; i++) {
            int entity = entities.get(i);
            // Descendant indices are historical data, not scene-layer references.
            if (members.has(entity)) continue;
            EntityIndexComponent index = mEntityIndex.getSafe(entity, null);
            if (index == null || index.layerIndex < firstIndex) continue;
            index.layerIndex += delta;
            if (dirtyTracker != null) {
                dirtyTracker.layer(entity);
                dirtyTracker.order(entity);
            }
            TiledLayerComponent tiled = mTiled.getSafe(entity, null);
            if (tiled != null && tiled.data != null) tiled.data.markAllChunksContentDirty();
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
            boolean spatialEnabled = lc != null && lc.spatialEnabled;
            list.add(new LayerUI(e, name, description, i, spatialEnabled, visible, locked));
        }
        return list;
    }

    /**
     * Returns the Tiled Map content currently owned by one Layer for editor display.
     * This is a cold UI rebuild query; map ownership itself remains EntityIndexComponent.
     */
    public Array<TiledMapUI> getTiledMapUIs(int layerEntityId) {
        int layerIndex = indexOfLayerEntity(layerEntityId);
        Array<TiledMapUI> maps = new Array<TiledMapUI>();
        if (layerIndex < 0) return maps;
        IntBag entities = world.getAspectSubscriptionManager().get(
                Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)).getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = entities.get(i);
            EntityIndexComponent index = mEntityIndex.get(entityId);
            if (index.layerIndex != layerIndex) continue;
            TiledLayerComponent tiled = mTiled.get(entityId);
            maps.add(new TiledMapUI(entityId, index.zIndex, tiled.projection,
                    tiled.mapWidthCells, tiled.mapHeightCells));
        }
        maps.sort(Comparator.comparingInt(TiledMapUI::zIndex)
                .thenComparingInt(TiledMapUI::mapEntityId));
        return maps;
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

    public void reset() {
        layerEntities.clear();
        dirty = true;
    }

    public record LayerUI(int layerEntityId, String name, String description, int index,
                          boolean spatialEnabled, boolean visible, boolean locked) {
    }

    public record TiledMapUI(int mapEntityId, int zIndex,
                             games.pixscape.runtime.tiled.TiledProjection projection,
                             int widthCells, int heightCells) {
    }

    public record LayerSnapshot(int index, long layerHistoryId, LayerInitializer layerInitializer,
                                DeleteGameObjectHierarchyCommand contents) {
    }
}
