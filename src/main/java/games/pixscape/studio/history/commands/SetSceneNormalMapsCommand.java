package games.pixscape.studio.history.commands;

import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;

import java.util.function.Consumer;

/** One scene-owned value, shared by every light inspector and restored by history. */
public final class SetSceneNormalMapsCommand implements Command, HistoryManager.SupportsNoop {
    private final SceneMeta scene;
    private final boolean before,after;
    private final Consumer<Boolean> prepare;
    private final Runnable markSaveRequired;

    public SetSceneNormalMapsCommand(SceneMeta scene,boolean enabled,
                                     Consumer<Boolean> prepare,Runnable markSaveRequired) {
        this.scene=java.util.Objects.requireNonNull(scene,"scene");
        before=scene.useNormalMaps;after=enabled;
        this.prepare=java.util.Objects.requireNonNull(prepare,"prepare");
        this.markSaveRequired=markSaveRequired;
    }
    @Override public String label(){return "Use normal maps — all lights";}
    @Override public boolean isNoop(){return before==after;}
    @Override public void redo(){apply(after);}
    @Override public void undo(){apply(before);}
    private void apply(boolean enabled){
        prepare.accept(enabled);
        scene.useNormalMaps=enabled;
        if(markSaveRequired!=null)markSaveRequired.run();
        EventFlow.i().publish(new EventFlow.SceneNormalMapsChanged(scene,EventFlow.tag(this)));
    }
}
