package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.tiled.TiledEntityRole;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.system.StudioRenderSubmitSystem;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditRenderRepeatCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    public static String activationRejection(World world, int entity) {
        if (world == null || entity < 0 || !world.getEntityManager().isActive(entity)) {
            return "Select a sprite.";
        }
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).getSafe(entity, null);
        if (meta == null || meta.kind != EntityKind.SPRITE
                || TiledEntityRole.of(world, entity) != TiledEntityRole.NONE
                || world.getMapper(AnimationComponent.class).has(entity)
                || !world.getMapper(TransformComponent.class).has(entity)
                || !world.getMapper(TextureRegionComponent.class).has(entity)
                || !world.getMapper(RenderMaterialComponent.class).has(entity)) {
            return "Repeat requires a sprite with an image and material.";
        }
        QuadDeformComponent quad = world.getMapper(QuadDeformComponent.class).getSafe(entity, null);
        if (quad != null && (quad.blX != 0 || quad.blY != 0 || quad.brX != 0 || quad.brY != 0
                || quad.trX != 0 || quad.trY != 0 || quad.tlX != 0 || quad.tlY != 0)) {
            return "Repeat is incompatible with quad deformation.";
        }
        StudioRenderSubmitSystem render = world.getSystem(StudioRenderSubmitSystem.class);
        return render != null && render.isForwardEffective() ? "Repeat is unavailable with Forward lighting." : null;
    }
    public record Snapshot(boolean repeatX, boolean repeatY) {
        public static Snapshot capture(RenderRepeatComponent component) {
            if (component == null) return disabled();
            return new Snapshot(component.repeatX, component.repeatY);
        }

        public static Snapshot disabled() {
            return new Snapshot(false, false);
        }

        public boolean enabled() {
            return repeatX || repeatY;
        }

        public boolean sameAs(Snapshot other) {
            return other != null
                    && repeatX == other.repeatX
                    && repeatY == other.repeatY;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long entityHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final EditTransformCommand.Snapshot beforeTransform;
    private final EditTransformCommand.Snapshot afterTransform;
    private final Runnable markCurrentSceneSaveRequired;
    private final boolean noop;

    public EditRenderRepeatCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   int entityId,
                                   Snapshot before,
                                   Snapshot after) {
        this(world, historyIds, entityId, before, after, null);
    }

    public EditRenderRepeatCommand(World world,
                                   HistoryIdRegistry historyIds,
                                   int entityId,
                                   Snapshot before,
                                   Snapshot after,
                                   Runnable markCurrentSceneSaveRequired) {
        this.world = world;
        this.historyIds = historyIds;
        this.before = before != null ? before : Snapshot.disabled();
        this.after = after != null ? after : Snapshot.disabled();
        boolean active = world != null && entityId >= 0 && world.getEntityManager().isActive(entityId);
        boolean admitted = active && (!this.after.enabled() || activationRejection(world, entityId) == null);
        this.entityHistoryId = historyIds != null && admitted ? historyIds.ensureForEntity(entityId) : -1L;
        this.markCurrentSceneSaveRequired = markCurrentSceneSaveRequired;

        TransformComponent transform = active
                ? world.getMapper(TransformComponent.class).getSafe(entityId, null)
                : null;
        this.beforeTransform = EditTransformCommand.Snapshot.capture(transform);
        this.afterTransform = this.after.enabled()
                && beforeTransform != null
                && !RepeatRotationConstraint.isZeroRotation(beforeTransform.rotationRad())
                ? beforeTransform.withRotationRad(0f)
                : beforeTransform;

        this.noop = world == null
                || historyIds == null
                || entityHistoryId <= 0L
                || (this.before.sameAs(this.after) && transformSnapshotsSame(this.beforeTransform, this.afterTransform));
    }

    @Override
    public String label() {
        return "Edit Repeatable";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        apply(after, afterTransform);
    }

    @Override
    public void undo() {
        apply(before, beforeTransform);
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        return apply(after, afterTransform);
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(before, beforeTransform);
    }

    private CommandOutcome apply(Snapshot snapshot, EditTransformCommand.Snapshot transformSnapshot) {
        if (noop || snapshot == null) {
            return CommandOutcome.NO_CHANGE;
        }
        int entityId = resolveEntityId();
        if (entityId < 0) {
            return CommandOutcome.NO_CHANGE;
        }
        if (snapshot.enabled() && activationRejection(world, entityId) != null) {
            return CommandOutcome.REJECTED;
        }

        boolean geometryChanged = false;
        if (transformSnapshot != null) {
            ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
            TransformComponent transform = mTransform.getSafe(entityId, null);
            if (transform != null) {
                EditTransformCommand.Snapshot current = EditTransformCommand.Snapshot.capture(transform);
                if (!transformSnapshot.sameAs(current)) {
                    transformSnapshot.apply(transform);
                    geometryChanged = true;
                }
            }
        }

        RepeatRotationConstraint.applyRepeat(world, entityId, snapshot, EventFlow.tag(this));

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.material(entityId);
            if (geometryChanged) {
                dirty.geometry(entityId, GeometryDirty.ALL);
            }
        }
        if (geometryChanged) {
            EventFlow.i().publish(new EventFlow.EntityChanged(entityId, TransformOp.ROTATE, EventFlow.tag(this)));
        }
        if (markCurrentSceneSaveRequired != null) {
            markCurrentSceneSaveRequired.run();
        }
        return CommandOutcome.APPLIED;
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }

    private static boolean transformSnapshotsSame(EditTransformCommand.Snapshot a,
                                                  EditTransformCommand.Snapshot b) {
        if (a == null) return b == null;
        return a.sameAs(b);
    }
}
