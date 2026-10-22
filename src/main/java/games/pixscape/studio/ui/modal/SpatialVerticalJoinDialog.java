package games.pixscape.studio.ui.modal;

import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.SpatialVerticalJoinCommand;
import games.pixscape.studio.service.spatial.*;
import games.pixscape.studio.ui.widget.SimpleSelectBox;

/** Native choice and detached complete-ensemble preview. Closing discards every preview. */
public final class SpatialVerticalJoinDialog extends StudioDialog {
    private final World world;
    private final HistoryManager history;
    private final SpatialBlockSelectionService selection;
    private final int seed;
    private final SpatialVerticalJoinProposal.Search search;
    private final SimpleSelectBox<SpatialVerticalJoinProposal.Candidate> references=new SimpleSelectBox<>();
    private final VisLabel details=new VisLabel("");
    private final VisTextButton apply=new VisTextButton("Connect to wall");
    private SpatialVerticalJoinProposal proposal;
    public SpatialVerticalJoinDialog(World world,HistoryManager history,SpatialBlockSelectionService selection,int map,int seed) {
        super("Connect to wall - preview");this.world=world;this.history=history;this.selection=selection;this.seed=seed;
        search=SpatialVerticalJoinProposal.search(world,map,seed);
        getContentTable().defaults().pad(5).left();getButtonsTable().defaults().pad(5);
        getContentTable().add(new VisLabel("Fixed reference:")).row();
        if(search.candidates.size>0) {
            references.setItems(search.candidates);getContentTable().add(references).width(560).row();
            references.addListener(new ChangeListener() {@Override public void changed(ChangeEvent event,Actor actor) {refresh();}});
            getContentTable().add(new VisLabel("Cyan: fixed ensemble / Orange: adapted ensemble / Red: conflict")).row();
        }
        // A complete ensemble needs native scrolling; no custom layout or capture system.
        VisScrollPane scroll=new VisScrollPane(details);scroll.setFadeScrollBars(false);
        getContentTable().add(scroll).width(620).height(300).row();
        button("Cancel",false);button(apply,true);closeOnEscape();refresh();
    }
    private void refresh() {
        if(search.candidates.size==0) {details.setText(search.diagnostic);apply.setDisabled(true);selection.setVerticalJoinPreview(null);return;}
        proposal=SpatialVerticalJoinProposal.create(world,search.selected,seed,references.getSelected());
        details.setText(proposal.describe());apply.setDisabled(!proposal.valid);selection.setVerticalJoinPreview(proposal);
    }
    @Override protected void result(Object result) {
        if(Boolean.TRUE.equals(result) && proposal!=null && proposal.valid) {
            if(selection.verticalJoinPreview()!=proposal || selection.getEditingMapEntityId()!=proposal.selected.entity
                    || selection.getSelectedBlockId()!=seed || selection.selectedBlockCount()!=1) {
                Dialogs.showOKDialog(getStage(),"Connect to wall","The editing selection changed. Reopen Connect to wall. Nothing was modified.");
                selection.setVerticalJoinPreview(null);return;
            }
            SpatialVerticalJoinCommand command=new SpatialVerticalJoinCommand(world,history.historyIds(),proposal);history.execute(command);
            if(!command.wasApplied() && !command.isNoop())Dialogs.showOKDialog(getStage(),"Connect to wall",command.rejectionReason());
        }
        selection.setVerticalJoinPreview(null);
    }
    @Override public void hide() {selection.setVerticalJoinPreview(null);super.hide();}
}
