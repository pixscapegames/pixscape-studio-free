package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.service.BlockPhysicsBindingRepository;
import games.pixscape.runtime.service.WorldBlockMutationService;
import games.pixscape.runtime.service.WorldBlockOwnerSnapshot;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** Undoable bind/unbind which restores exact detached Runtime owner snapshots on replay. */
public final class ToggleSpatialBlockCollisionCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final WorldBlockMutationService mutations;
    private final BlockPhysicsBindingRepository repository;
    private final long ownerHistoryId;
    private final int blockId;
    private final boolean enabled;
    private WorldBlockOwnerSnapshot before;
    private WorldBlockOwnerSnapshot after;
    private CommandOutcome outcome = CommandOutcome.APPLIED;

    public ToggleSpatialBlockCollisionCommand(World world, HistoryIdRegistry historyIds,
                                               WorldBlockMutationService mutations,
                                               BlockPhysicsBindingRepository repository,
                                               int ownerEntityId, int blockId, boolean enabled) {
        this.world = world;
        this.historyIds = historyIds;
        this.mutations = mutations;
        this.repository = repository;
        this.ownerHistoryId = historyIds != null ? historyIds.ensureForEntity(ownerEntityId) : -1L;
        this.blockId = blockId;
        this.enabled = enabled;
        if (world == null || historyIds == null || mutations == null || repository == null
                || ownerHistoryId <= 0 || blockId <= 0) outcome = CommandOutcome.REJECTED;
    }

    @Override public String label() { return enabled ? "Enable Spatial Wall Collision" : "Disable Spatial Wall Collision"; }
    @Override public boolean isNoop() { return outcome != CommandOutcome.APPLIED; }
    @Override public void redo() { redoOutcome(); }
    @Override public void undo() { undoOutcome(); }

    @Override
    public CommandOutcome executeOutcome() {
        if (outcome != CommandOutcome.APPLIED) return outcome;
        int stableId = ownerStableId();
        if (stableId <= 0) return outcome = CommandOutcome.REJECTED;
        if (repository.hasBinding(stableId, blockId) == enabled) return outcome = CommandOutcome.NO_CHANGE;
        before = mutations.captureOwnerState(stableId);
        if (enabled) mutations.bindBlockCollision(stableId, blockId);
        else mutations.removeBlockCollision(stableId, blockId);
        after = mutations.captureOwnerState(stableId);
        return CommandOutcome.APPLIED;
    }

    @Override
    public CommandOutcome redoOutcome() {
        if (after == null) return executeOutcome();
        mutations.restoreOwnerState(after);
        return CommandOutcome.APPLIED;
    }

    @Override
    public CommandOutcome undoOutcome() {
        if (before == null) return CommandOutcome.NO_CHANGE;
        mutations.restoreOwnerState(before);
        return CommandOutcome.APPLIED;
    }

    private int ownerStableId() {
        int entityId = historyIds.entityOfHistoryId(ownerHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return -1;
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                .getSafe(entityId, null);
        return identity != null ? identity.stableId : -1;
    }
}
