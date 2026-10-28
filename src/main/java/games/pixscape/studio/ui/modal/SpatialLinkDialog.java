package games.pixscape.studio.ui.modal;

import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.SpatialBlockLinkCommand;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialLinkProposal;
import games.pixscape.studio.ui.widget.SimpleSelectBox;

/** Native modal authoring preview; cancel/hide only discard detached data. */
public final class SpatialLinkDialog extends StudioDialog {
    private final World world;
    private final HistoryManager history;
    private final SpatialBlockSelectionService selection;
    private final int map, first, second;
    private final boolean unlink;
    private final VisLabel details = new VisLabel("");
    private final VisTextButton apply;
    private final SimpleSelectBox<Integer> reference = new SimpleSelectBox<>();
    private SpatialLinkProposal proposal;

    public SpatialLinkDialog(World world, HistoryManager history, SpatialBlockSelectionService selection,
                             int map, int first, int second, boolean unlink) {
        super(unlink ? "Délier" : "Lier - aperçu");
        this.world = world; this.history = history; this.selection = selection;
        this.map = map; this.first = first; this.second = second; this.unlink = unlink;
        getContentTable().defaults().pad(5).left();
        if (!unlink) {
            reference.setItems(first, second);
            reference.setSelected(first);
            reference.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) { refreshProposal(); }
            });
            getContentTable().add(new VisLabel("Fixed reference:"));
            getContentTable().add(reference).row();
        }
        getContentTable().add(new VisLabel("Cyan: fixed ensemble / Orange: adapted ensemble")).colspan(2).row();
        // Native scrolling is needed for a complete multi-member dimensional preview.
        VisScrollPane scroll = new VisScrollPane(details);
        scroll.setFadeScrollBars(false);
        getContentTable().add(scroll).colspan(2).width(560).height(260).row();
        getButtonsTable().defaults().pad(5);
        button("Cancel", false);
        apply = new VisTextButton(unlink ? "Délier" : "Lier");
        button(apply, true);
        closeOnEscape();
        refreshProposal();
    }

    private void refreshProposal() {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(map, null);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(map, null);
        if (tiled == null || blocks == null) { apply.setDisabled(true); return; }
        int fixed = unlink ? second : reference.getSelected();
        int selected = fixed == first ? second : first;
        proposal = SpatialLinkProposal.create(blocks, tiled.data, selected, fixed, unlink, tiled.defaultTileHeight);
        selection.setLinkPreview(proposal);
        StringBuilder text = new StringBuilder();
        text.append("Reference ensemble: ").append(proposal.reference).append("\nAdapted ensemble: ").append(proposal.affected).append('\n');
        if (!proposal.valid) text.append("Conflict: ").append(proposal.diagnostic).append('\n');
        if (unlink && proposal.valid) {
            int a = structure(proposal.after, selected), b = structure(proposal.after, fixed);
            text.append(a == b ? "Another connection path remains: same ensemble.\n" : "The ensembles will separate.\n");
            text.append("Dimensions stay unchanged.\n");
        }
        text.append(proposal.describe());
        details.setText(text.toString());
        apply.setDisabled(!proposal.valid);
    }

    @Override protected void result(Object result) {
        if (Boolean.TRUE.equals(result) && proposal != null && proposal.valid) {
            SpatialBlockLinkCommand command = new SpatialBlockLinkCommand(world, history.historyIds(), map, proposal);
            history.execute(command);
            if (!command.wasApplied())
                Dialogs.showOKDialog(getStage(), unlink ? "Délier" : "Lier",
                        "The preview is stale or geometry/physics validation rejected the change. Reopen the preview. Nothing was modified.");
        }
        selection.setLinkPreview(null);
    }

    @Override public void hide() { selection.setLinkPreview(null); super.hide(); }
    private static SpatialBlockData find(SpatialBlocksComponent component, int id) {
        for (SpatialBlockData wall : component.blocks) if (wall.id == id) return wall;
        return null;
    }
    private static int structure(SpatialBlocksComponent component, int id) { return find(component, id).structureId; }
}
