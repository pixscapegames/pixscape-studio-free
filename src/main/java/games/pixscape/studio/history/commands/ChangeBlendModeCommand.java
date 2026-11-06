package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;

public final class ChangeBlendModeCommand implements Command, OutcomeAwareCommand {
    private final World world;
    private final SceneMeta scene;
    private final HistoryIdRegistry ids;
    private final long target;
    private final int before, after;

    public ChangeBlendModeCommand(World world, SceneMeta scene, HistoryIdRegistry ids, int entity, BlendMode mode) {
        this.world = world;
        this.scene = scene;
        this.ids = ids;
        target = ids.ensureForEntity(entity);
        before = world.getMapper(RenderMaterialComponent.class).get(entity).blendModeId;
        after = mode.id;
    }

    @Override public String label() { return "Change blend mode"; }
    @Override public void redo() { redoOutcome(); }
    @Override public void undo() { undoOutcome(); }
    @Override public CommandOutcome executeOutcome() { return apply(after); }
    @Override public CommandOutcome redoOutcome() { return apply(after); }
    @Override public CommandOutcome undoOutcome() { return apply(before); }

    private CommandOutcome apply(int blend) {
        int entity = ids.entityOfHistoryId(target);
        if (entity < 0 || !world.getEntityManager().isActive(entity)) return CommandOutcome.REJECTED;
        var material = world.getMapper(RenderMaterialComponent.class).getSafe(entity, null);
        if (material == null) return CommandOutcome.REJECTED;
        if (material.blendModeId == blend) return CommandOutcome.NO_CHANGE;
        String refusal = scene != null && scene.lightingEnabled
                ? SceneBlendAdmission.refusal(world, entity, blend, SceneBlendAdmission.usesForward(world, scene.forwardLighting))
                : null;
        if (refusal == null) {
            material.blendModeId = blend;
            var dirty = world.getSystem(DirtyTrackerSystem.class);
            if (dirty != null) dirty.material(entity);
        }
        EventFlow.i().publish(new EventFlow.MaterialBlendResult(world, entity, refusal));
        return refusal == null ? CommandOutcome.APPLIED : CommandOutcome.REJECTED;
    }
}
