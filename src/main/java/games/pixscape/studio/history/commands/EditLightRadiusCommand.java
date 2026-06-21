package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditLightRadiusCommand implements Command, HistoryManager.SupportsNoop {
    public static final float MIN_RADIUS = 1f;

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long historyId;
    private final float beforeRadius;
    private final float afterRadius;
    private final float beforeRotationRad;
    private final float afterRotationRad;
    private final boolean editRotation;
    private final DirtyTrackerSystem dirty;
    private final int sourceTag = EventFlow.tag(this);

    public EditLightRadiusCommand(World world, HistoryIdRegistry historyIds, int entityId, float beforeRadius, float afterRadius) {
        this.world = world;
        this.historyIds = historyIds;
        this.historyId = historyIds.ensureForEntity(entityId);
        this.beforeRadius = clamp(beforeRadius);
        this.afterRadius = clamp(afterRadius);
        this.beforeRotationRad = 0f;
        this.afterRotationRad = 0f;
        this.editRotation = false;
        this.dirty = world.getSystem(DirtyTrackerSystem.class);
    }

    public EditLightRadiusCommand(World world,
                                  HistoryIdRegistry historyIds,
                                  int entityId,
                                  float beforeRadius,
                                  float afterRadius,
                                  float beforeRotationRad,
                                  float afterRotationRad) {
        this.world = world;
        this.historyIds = historyIds;
        this.historyId = historyIds.ensureForEntity(entityId);
        this.beforeRadius = clamp(beforeRadius);
        this.afterRadius = clamp(afterRadius);
        this.beforeRotationRad = beforeRotationRad;
        this.afterRotationRad = afterRotationRad;
        this.editRotation = true;
        this.dirty = world.getSystem(DirtyTrackerSystem.class);
    }

    @Override
    public String label() {
        return "Edit Light Radius";
    }

    @Override
    public boolean isNoop() {
        if (Float.compare(beforeRadius, afterRadius) != 0) return false;
        return !editRotation || Float.compare(beforeRotationRad, afterRotationRad) == 0;
    }

    @Override
    public void undo() {
        apply(beforeRadius, beforeRotationRad);
    }

    @Override
    public void redo() {
        apply(afterRadius, afterRotationRad);
    }

    private void apply(float radius, float rotationRad) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return;

        boolean editedConeRotation = applyLightOverlayValues(
                world,
                entityId,
                radius,
                rotationRad,
                editRotation,
                dirty
        );
        if (editedConeRotation) {
            EventFlow.i().publish(new EventFlow.EntityChanged(entityId, TransformOp.ROTATE, sourceTag));
        }
        EventFlow.i().publish(new EventFlow.EntityChanged(entityId, TransformOp.SCALE, sourceTag));
    }

    public static float clamp(float radius) {
        return Math.max(MIN_RADIUS, radius);
    }

    public static boolean applyLightOverlayValues(World world,
                                                  int entityId,
                                                  float radius,
                                                  float rotationRad,
                                                  boolean editRotation,
                                                  DirtyTrackerSystem dirty) {
        return LightMutationSupport.applyRadiusAndOptionalRotation(
                world,
                entityId,
                radius,
                rotationRad,
                editRotation,
                dirty
        );
    }
}
