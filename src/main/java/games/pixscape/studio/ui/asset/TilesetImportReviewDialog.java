package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.asset.TilesetAssetMeta;
import games.pixscape.studio.service.asset.TilesetReimportService;
import games.pixscape.studio.ui.modal.StudioDialog;
import java.util.*;
import java.util.function.*;

/** Explicit update/new/cancel confirmation for a prepared tileset import. */
final class TilesetImportReviewDialog extends StudioDialog {
    private final IntFunction<TilesetReimportService.Plan> prepare;
    private final Consumer<TilesetReimportService.Plan> apply;
    private final VisLabel details = new VisLabel("");
    private final VisTextButton update = new VisTextButton("Update");
    private final VisSelectBox<String> candidates = new VisSelectBox<>();
    private final List<TilesetAssetMeta> sets;
    private TilesetReimportService.Plan plan;

    TilesetImportReviewDialog(List<TilesetAssetMeta> sets, IntFunction<TilesetReimportService.Plan> prepare,
                              Consumer<TilesetReimportService.Plan> apply) {
        super("Tileset import");
        this.sets = sets; this.prepare = prepare; this.apply = apply;
        setModal(true); addCloseButton();
        Array<String> names = new Array<>();
        if (sets.size() > 1) names.add("<choose an existing tileset>");
        for (TilesetAssetMeta set : sets) names.add(set.logicalPath() + " (ID " + set.id() + ")");
        candidates.setItems(names);
        if (sets.size() > 1) {
            getContentTable().add(new VisLabel("Multiple candidates: choose the tileset explicitly.")).left().row();
        }
        if (!sets.isEmpty()) getContentTable().add(candidates).growX().row();
        details.setWrap(true);
        getContentTable().add(new VisScrollPane(details)).width(700).height(260).grow().row();
        VisTextButton asNew = new VisTextButton(sets.isEmpty() ? "Import" : "Import as new");
        VisTextButton cancel = new VisTextButton("Cancel");
        if (!sets.isEmpty()) getButtonsTable().add(update).pad(6);
        getButtonsTable().add(asNew).pad(6); getButtonsTable().add(cancel).pad(6);
        candidates.addListener(change(this::refresh));
        update.addListener(change(() -> accept(plan)));
        asNew.addListener(change(() -> {
            try { accept(prepare.apply(-1)); } catch (RuntimeException failure) { error(failure); }
        }));
        cancel.addListener(change(this::fadeOut));
        refresh(); pack();
    }

    private int selectedId() {
        int index = candidates.getSelectedIndex();
        if (sets.size() > 1) index--;
        return index >= 0 && index < sets.size() ? sets.get(index).id() : -1;
    }
    private void refresh() {
        update.setDisabled(true); plan = null;
        if (sets.size() > 1 && selectedId() <= 0) {
            details.setText("Choose an existing tileset to update, or import as a new tileset."); return;
        }
        try {
            plan = prepare.apply(selectedId());
            details.setText(plan.summary());
            update.setDisabled(false);
        } catch (RuntimeException failure) { error(failure); }
    }
    private void accept(TilesetReimportService.Plan selected) {
        if (selected == null) return;
        try { apply.accept(selected); fadeOut(); }
        catch (RuntimeException failure) { error(failure); }
    }
    private void error(RuntimeException failure) { details.setText(ImportDialogApplySupport.userMessageFor(failure)); }

    private static ChangeListener change(Runnable action) {
        return new ChangeListener() { @Override public void changed(ChangeEvent event, Actor actor) { action.run(); } };
    }
}
