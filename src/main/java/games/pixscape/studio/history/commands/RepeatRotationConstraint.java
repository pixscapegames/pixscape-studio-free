package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;

final class RepeatRotationConstraint {
    static final float ZERO_ROTATION_EPSILON_RAD = 0.0001f;

    private RepeatRotationConstraint() {
    }

    static boolean isZeroRotation(float rotationRad) {
        return Math.abs(rotationRad) <= ZERO_ROTATION_EPSILON_RAD;
    }

    static EditRenderRepeatCommand.Snapshot captureRepeat(World world, int entityId) {
        if (world == null || entityId < 0) return EditRenderRepeatCommand.Snapshot.disabled();
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).getSafe(entityId, null);
        return EditRenderRepeatCommand.Snapshot.capture(repeat);
    }

    static EditRenderRepeatCommand.Snapshot repeatAfterRotation(EditRenderRepeatCommand.Snapshot beforeRepeat,
                                                                float rotationRad) {
        if (beforeRepeat == null || !beforeRepeat.enabled() || isZeroRotation(rotationRad)) {
            return beforeRepeat != null ? beforeRepeat : EditRenderRepeatCommand.Snapshot.disabled();
        }
        return EditRenderRepeatCommand.Snapshot.disabled();
    }

    static boolean applyRepeat(World world,
                               int entityId,
                               EditRenderRepeatCommand.Snapshot snapshot,
                               int sourceTag) {
        if (world == null || entityId < 0 || snapshot == null) return false;

        ComponentMapper<RenderRepeatComponent> mapper = world.getMapper(RenderRepeatComponent.class);
        EditRenderRepeatCommand.Snapshot before = EditRenderRepeatCommand.Snapshot.capture(mapper.getSafe(entityId, null));
        if (before.sameAs(snapshot)) return false;

        if (!snapshot.enabled()) {
            if (mapper.has(entityId)) {
                mapper.remove(entityId);
            }
        } else {
            RenderRepeatComponent component = mapper.has(entityId)
                    ? mapper.get(entityId)
                    : mapper.create(entityId);
            component.repeatX = snapshot.repeatX();
            component.repeatY = snapshot.repeatY();
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.material(entityId);
        }
        EventFlow.i().publish(new EventFlow.RenderRepeatChanged(entityId, sourceTag));
        return true;
    }
}
