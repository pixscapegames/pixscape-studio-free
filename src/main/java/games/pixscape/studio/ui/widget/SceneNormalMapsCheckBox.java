package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.SetSceneNormalMapsCommand;

import java.util.function.Consumer;

/** Scene-owned light setting; all inspectors read the same authored value after notification. */
public final class SceneNormalMapsCheckBox extends VisCheckBox {
    private final SceneMeta scene;
    private boolean refreshRequired;

    public SceneNormalMapsCheckBox(SceneMeta scene,HistoryManager history,Consumer<Boolean> prepare,Runnable markSaveRequired) {
        super("Use normal maps — all lights");
        this.scene=scene;
        setName("scene-use-normal-maps");setDisabled(scene==null);refresh();
        new Tooltip.Builder("Applies to all lights in this scene. Restart an open Preview after changing this setting.")
                .target(this).build();
        addListener(new ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){
            if(scene==null)return;
            try {history.execute(new SetSceneNormalMapsCommand(scene,isChecked(),prepare,markSaveRequired));}
            finally {refresh();}
        }});
        EventFlow.i().subscribe(EventFlow.SceneNormalMapsChanged.class,event->{
            if(event.scene()==scene)refreshRequired=true;
        });
    }
    public void refresh(){
        setProgrammaticChangeEvents(false);
        setChecked(scene==null || scene.useNormalMaps);
        setProgrammaticChangeEvents(true);refreshRequired=false;
    }
    @Override public void act(float delta){
        super.act(delta);
        if(refreshRequired)refresh();
    }
}
