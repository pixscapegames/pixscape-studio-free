package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * One scene-owned value, shared by every light inspector and restored by history.
 */
public final class SetSceneLightingCommand implements Command, OutcomeAwareCommand, HistoryManager.SupportsNoop {
    private final World world;
    private final SceneMeta scene;
    private final boolean before, after;
    private final Consumer<Boolean> prepare;
    private final Runnable markSaveRequired;

    public SetSceneLightingCommand(World world, SceneMeta scene, boolean enabled,
                                   Consumer<Boolean> prepare, Runnable markSaveRequired) {
        this.world = Objects.requireNonNull(world, "world");
        this.scene = Objects.requireNonNull(scene, "scene");
        before = scene.lightingEnabled;
        after = enabled;
        this.prepare = Objects.requireNonNull(prepare, "prepare");
        this.markSaveRequired = markSaveRequired;
    }

    @Override
    public String label() {
        return "Enable lighting";
    }

    @Override
    public boolean isNoop() {
        return before == after;
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
        return apply(after);
    }

    @Override
    public CommandOutcome redoOutcome() {
        return apply(after);
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(before);
    }

    private CommandOutcome apply(boolean enabled) {
        if (scene.lightingEnabled == enabled) return CommandOutcome.NO_CHANGE;
        if (enabled) {
            String reason = SceneBlendAdmission.lightingRefusal(world, scene.forwardLighting);
            if (reason != null) {
                EventFlow.i().publish(new EventFlow.SceneLightingRefused(scene, reason));
                return CommandOutcome.REJECTED;
            }
        }
        prepare.accept(enabled);
        scene.lightingEnabled = enabled;
        if (markSaveRequired != null) markSaveRequired.run();
        EventFlow.i().publish(new EventFlow.SceneLightingChanged(scene, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }
}
