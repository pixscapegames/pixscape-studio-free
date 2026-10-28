package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;

/** One scene-owned choice; GPU preparation occurs at the existing canvas boundary. */
public final class SetSceneRendererCommand implements Command, HistoryManager.SupportsNoop {
    private final World world;
    private final games.pixscape.studio.history.HistoryIdRegistry ids;
    private final SceneMeta scene;
    private final boolean before,after;
    public SetSceneRendererCommand(World world,games.pixscape.studio.history.HistoryIdRegistry ids,SceneMeta scene,boolean forward){
        this.world=world;this.ids=ids;this.scene=scene;before=scene.forwardLighting;after=forward;
    }
    @Override public String label(){return "Forward rendering (experimental)";}
    @Override public boolean selectsLegacyBeforeLights(){return !after;}
    @Override public boolean isNoop(){return before==after;}
    @Override public void redo(){apply(after);}
    @Override public void undo(){apply(before);}
    private void apply(boolean forward){
        if(forward && ids.activeLightCount(world)>1)throw new IllegalArgumentException("Forward rendering supports at most one active light.");
        games.pixscape.studio.system.StudioRenderSubmitSystem submit=world.getSystem(games.pixscape.studio.system.StudioRenderSubmitSystem.class);
        if(submit!=null)submit.prepareRendererSelection(forward,scene.useNormalMaps);
        scene.forwardLighting=forward;
        EventFlow.i().publish(new EventFlow.SceneRendererChanged(scene,EventFlow.tag(this)));
    }
}
