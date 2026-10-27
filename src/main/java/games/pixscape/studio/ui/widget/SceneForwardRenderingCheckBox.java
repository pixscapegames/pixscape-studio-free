package games.pixscape.studio.ui.widget;

import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.SetSceneRendererCommand;

/** Scoped inspector view of the scene choice and command-time eligibility. */
public final class SceneForwardRenderingCheckBox extends VisCheckBox {
    public static final String AVAILABLE="Uses the experimental Forward renderer. Supports at most one active light.\nPerformance depends on the scene. Restart an open Preview after changing\nthis setting.";
    public static final String UNAVAILABLE="Forward rendering supports at most one active light. Disable additional\nlights to enable it.";
    private final SceneMeta scene;
    private final HistoryManager history;
    private final VisLabel explanation=new VisLabel(AVAILABLE);
    private boolean refreshRequired;

    public SceneForwardRenderingCheckBox(World world,SceneMeta scene,HistoryManager history){
        super("Forward rendering (experimental)");
        this.scene=scene;this.history=history;setName("scene-forward-rendering");
        new Tooltip.Builder(explanation).target(this).build();refresh();
        addListener(new ChangeListener(){@Override public void changed(ChangeEvent event,Actor actor){
            if(scene==null || isDisabled())return;
            try {history.execute(new SetSceneRendererCommand(world,history.historyIds(),scene,isChecked()));}
            catch(RuntimeException failure){
                if(getStage()==null)throw failure;
                com.kotcrab.vis.ui.util.dialog.Dialogs.showOKDialog(getStage(),"Forward rendering",failure.getMessage());
            }finally{refresh();}
        }});
        EventFlow.i().subscribe(EventFlow.SceneRendererChanged.class,event->{if(event.scene()==scene)refreshRequired=true;});
    }
    public String explanation(){return explanation.getText().toString();}
    public void refresh(){
        setProgrammaticChangeEvents(false);
        setChecked(scene!=null && scene.forwardLighting);setDisabled(scene==null || history.activeLightCount()>1);
        explanation.setText(isDisabled()?UNAVAILABLE:AVAILABLE);
        setProgrammaticChangeEvents(true);refreshRequired=false;
    }
    @Override public void act(float delta){super.act(delta);if(refreshRequired)refresh();}
}
