package games.pixscape.studio.history.commands;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.World;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialActorAdmission;

/** Changes the selected footprint and prepares missing visual representations during activation. */
public final class ToggleSpatialActorCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry ids;
    private final long id;
    private final boolean enable;
    private final EditorDocumentType documentType;
    private final SpatialFootprintComponent before, after;
    private final boolean noop;
    private final SpatialRepresentationInitialization representations;

    public ToggleSpatialActorCommand(World world, HistoryIdRegistry ids,
            int entity, boolean enable, boolean eligible, SpatialFootprintComponent initial) {
        this(world, ids, entity, enable, eligible, initial, true, EditorDocumentType.SCENE);
    }

    public ToggleSpatialActorCommand(World world, HistoryIdRegistry ids,
            int entity, boolean enable, boolean eligible, SpatialFootprintComponent initial, EditorDocumentType documentType) {
        this(world, ids, entity, enable, eligible, initial, true, documentType);
    }

    ToggleSpatialActorCommand(World world, HistoryIdRegistry ids,
            int entity, boolean enable, boolean eligible, SpatialFootprintComponent initial,
            boolean initializeVisuals, EditorDocumentType documentType) {
        this.documentType = java.util.Objects.requireNonNull(documentType, "documentType");
        this.world = world;
        this.ids = ids;
        this.enable = enable;
        if (enable) {
            TiledEntityRole.requireActorCapability(world, entity, "Footprint");
        }
        boolean valid = world != null && ids != null && entity >= 0 && world.getEntityManager().isActive(entity)
                && (!enable || activationRejection(world, entity, documentType) == null);
        id = valid ? ids.ensureForEntity(entity) : -1;
        SpatialFootprintComponent current = valid ? world.getMapper(SpatialFootprintComponent.class).getSafe(entity, null) : null;
        before = current == null ? null : current.copy();
        after = before != null ? before.copy() : initial == null ? null : initial.copy();
        noop = !valid || enable && (!eligible || after == null || before != null)
                || !enable && before == null;
        if (after != null) {
            after.validate();
        }
        representations = new SpatialRepresentationInitialization(world, ids);
        if (enable && !noop && initializeVisuals) {
            representations.prepareSubtree(entity);
        }
    }

    @Override
    public String label() {
        return enable ? "Add own footprint" : "Remove own footprint";
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
    public void undo() {
        undoOutcome();
    }
    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }
    @Override
    public CommandOutcome redoOutcome() {
        if (enable && !noop) {
            int entity = ids.entityOfHistoryId(id);
            if (activationRejection(world, entity, documentType) != null) {
                return CommandOutcome.REJECTED;
            }
        }
        return apply(enable ? after : null);
    }
    @Override
    public CommandOutcome undoOutcome() {
        return apply(before);
    }

    /** Cold command preflight; ownership remains independent of participation. */
    public static String activationRejection(World world, int entity) {
        return activationRejection(world, entity, EditorDocumentType.SCENE);
    }

    public static String activationRejection(World world, int entity, EditorDocumentType documentType) {
        if (world == null || entity < 0 || !world.getEntityManager().isActive(entity)) {
            return "Object is unavailable.";
        }
        try {
            if (documentType == EditorDocumentType.GAME_OBJECT) {
                return null;
            }
            int layer = games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations.layerIndex(world, entity);
            return SpatialActorAdmission.layerRejection(world, layer);
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    private CommandOutcome apply(SpatialFootprintComponent footprint) {
        int entity = noop ? -1 : ids.entityOfHistoryId(id);
        if (entity < 0 || !world.getEntityManager().isActive(entity)) {
            return CommandOutcome.NO_CHANGE;
        }
        var hierarchy = world.getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class);
        if (hierarchy != null) {
            hierarchy.topologyForRemoval();
        }
        if (enable) {
            representations.apply(footprint == null);
        }
        if (footprint == null) {
            world.getMapper(SpatialFootprintComponent.class).remove(entity);
        } else {
            world.getMapper(SpatialFootprintComponent.class).create(entity).set(footprint);
        }
        var composition = world.getSystem(games.pixscape.runtime.system.GameObjectCompositionSystem.class);
        if (composition != null) {
            composition.refreshSpatialOwners(entity);
        }
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.order(entity);
        }
        EventFlow.i().publish(new EventFlow.SpatialActorChanged(entity, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }
}
