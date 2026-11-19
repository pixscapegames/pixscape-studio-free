package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.Aspect;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;

/**
 * Toggles participation of one ordinary layer in Spatial actor ordering.
 */
public final class ToggleSpatialActorLayerCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final LayerService layerService;
    private final long layerHistoryId;
    private final boolean before;
    private final boolean after;
    private final boolean noop;
    private final long[] footprintIds;
    private final SpatialFootprintComponent[] footprints;

    public ToggleSpatialActorLayerCommand(
            World world,
            HistoryIdRegistry historyIds,
            LayerService layerService,
            int layerEntityId,
            boolean enabled) {
        this.world = world;
        this.historyIds = historyIds;
        this.layerService = layerService;

        LayerComponent layer = validOrdinaryLayer(layerEntityId);
        this.layerHistoryId = layer != null
                ? historyIds.ensureForEntity(layerEntityId)
                : -1L;
        this.before = layer != null && layer.spatialEnabled;
        this.after = enabled;
        this.noop = layer == null || layerHistoryId <= 0L || before == after;
        IntArray owners = !noop && !enabled ? footprintOwners(world, layer.layerIndex) : new IntArray();
        footprints = new SpatialFootprintComponent[owners.size];
        footprintIds = new long[owners.size];
        // Prepare all copies before any component mutation. History identities survive entity restore.
        for (int i = 0; i < owners.size; i++) {
            footprints[i] = world.getMapper(SpatialFootprintComponent.class).get(owners.get(i)).copy();
            footprintIds[i] = historyIds.ensureForEntity(owners.get(i));
        }
    }

    /** Cold confirmation/command boundary: includes invisible owners, never inherited copies. */
    public static IntArray footprintOwners(World world, int layerIndex) {
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        if (hierarchy != null) {
            hierarchy.topologyForRemoval();
        }
        IntArray result = new IntArray();
        var entities = world.getAspectSubscriptionManager().get(Aspect.all(SpatialFootprintComponent.class)).getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            if (world.getEntityManager().isActive(entity)
                    && world.getMapper(SpatialFootprintComponent.class).has(entity)
                    && CurrentGameObjectRelations.publishedLayerIndex(world, entity) == layerIndex) {
                result.add(entity);
            }
        }
        return result;
    }

    @Override
    public String label() {
        return after ? "Enable Layer Spatial" : "Disable Layer Spatial";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return apply(after);
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(before);
    }

    private CommandOutcome apply(boolean enabled) {
        if (noop) {
            return CommandOutcome.NO_CHANGE;
        }

        int layerEntityId = resolveLayerEntity();
        LayerComponent layer = validOrdinaryLayer(layerEntityId);
        if (layer == null || layer.spatialEnabled == enabled) {
            return CommandOutcome.NO_CHANGE;
        }
        if (enabled && layerService.hasOtherSpatialActorLayer(layerEntityId)) {
            return CommandOutcome.REJECTED;
        }

        int[] owners = new int[footprintIds.length];
        IntSet roots = new IntSet();
        var hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        var topology = hierarchy == null ? null : hierarchy.topologyForRemoval();
        var mapper = world.getMapper(SpatialFootprintComponent.class);
        for (int i = 0; i < owners.length; i++) {
            int owner = historyIds.entityOfHistoryId(footprintIds[i]);
            if (owner < 0 || !world.getEntityManager().isActive(owner)
                    || CurrentGameObjectRelations.publishedLayerIndex(world, owner) != layer.layerIndex
                    || mapper.has(owner) == enabled) {
                return CommandOutcome.REJECTED;
            }
            owners[i] = owner;
            roots.add(topology != null && owner < topology.getEntityCapacity()
                    && topology.parented[owner] ? topology.rootEntityId[owner] : owner);
        }
        if (!after && !enabled && footprintOwners(world, layer.layerIndex).size != owners.length) {
            return CommandOutcome.REJECTED;
        }
        // Everything is resolved and checked before publishing the one history operation.
        for (int i = 0; i < owners.length; i++) {
            if (enabled) {
                mapper.create(owners[i]).set(footprints[i]);
            } else {
                mapper.remove(owners[i]);
            }
        }
        layer.spatialEnabled = enabled;
        var composition = world.getSystem(GameObjectCompositionSystem.class);
        if (composition != null) {
            var iterator = roots.iterator();
            while (iterator.hasNext) {
                composition.refreshSpatialOwners(iterator.next());
            }
        }
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(layerEntityId);
            dirty.order(layerEntityId);
            for (int owner : owners) {
                dirty.order(owner);
            }
        }
        EventFlow.i().publish(
                new EventFlow.LayerSpatialDepthChanged(layerEntityId, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }

    private LayerComponent validOrdinaryLayer(int entityId) {
        if (world == null || historyIds == null || layerService == null
                || entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return null;
        }
        return world.getMapper(LayerComponent.class).getSafe(entityId, null);
    }

    private int resolveLayerEntity() {
        if (historyIds == null || layerHistoryId <= 0L) {
            return -1;
        }
        return historyIds.entityOfHistoryId(layerHistoryId);
    }
}
