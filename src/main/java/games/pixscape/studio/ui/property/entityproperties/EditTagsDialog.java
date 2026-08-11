package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.widget.ValidationHooks;

import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

public final class EditTagsDialog extends StudioDialog {

    private static final int MAX_TAGS = 16;
    private static final int MAX_LEN = 32;

    private final Array<Row> rows = new Array<>();
    private final Consumer<Array<String>> onApply;

    private final VisTable tagsTable = new VisTable(true);
    private final VisScrollPane scroll = new VisScrollPane(tagsTable);
    private final Button addButton = new Button(VisUI.getSkin(), "add");

    public EditTagsDialog(String title, List<String> initialTags, Consumer<Array<String>> onApply) {
        super(title);
        this.onApply = onApply;

        TableUtils.setSpacingDefaults(this);
        setResizable(true);
        setModal(true);

        scroll.setFadeScrollBars(false);
        installTooltip(addButton, "Add tag");
        addButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (rows.size >= MAX_TAGS) return;
                Row row = addRow("");
                refreshTagsTable();
                focusAndRevealRow(row);
            }
        });

        if (initialTags != null) {
            for (String tag : initialTags) {
                if (rows.size >= MAX_TAGS) break;
                addRow(tag != null ? tag : "");
            }
        }

        buildUi();
        refreshTagsTable();

        button("OK", true);
        button("Cancel", false);

        pack();
        centerWindow();
    }

    private void buildUi() {
        VisTable root = new VisTable(true);
        root.add(new VisLabel("Tags (max " + MAX_TAGS + ")")).left().row();
        root.add(scroll).grow().minHeight(180).minWidth(300).row();
        getContentTable().add(root).grow();
    }

    private Row addRow(String value) {
        Row row = new Row(value);
        row.tagField.addValidator(input -> isRowValueValid(row));
        ValidationHooks.installEnterAndFocusLostValidation(row.tagField, () -> validateRow(row));
        installTooltip(row.removeButton, "Delete tag");
        row.removeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                rows.removeValue(row, true);
                refreshTagsTable();
            }
        });
        rows.add(row);
        return row;
    }

    private void refreshTagsTable() {
        tagsTable.clearChildren();

        for (Row row : rows) {
            tagsTable.add(row.tagField).growX().pad(2);
            tagsTable.add(row.removeButton).right().pad(2).row();
        }

        addButton.setDisabled(rows.size >= MAX_TAGS);
        tagsTable.add().expandX();
        tagsTable.add(addButton).right().pad(2).row();
        tagsTable.invalidateHierarchy();
    }

    private void focusAndRevealRow(Row row) {
        Runnable reveal = () -> {
            tagsTable.validate();
            scroll.validate();
            scroll.setScrollPercentY(1f);
            scroll.updateVisualScroll();
            if (getStage() != null) getStage().setKeyboardFocus(row.tagField);
        };
        if (Gdx.app == null) {
            reveal.run();
        } else {
            Gdx.app.postRunnable(reveal);
        }
    }

    private boolean validateRow(Row row) {
        row.tagField.validateInput();
        return row.tagField.isInputValid() && isRowValueValid(row);
    }

    private boolean isRowValueValid(Row row) {
        String normalized = normalize(row.tagField.getText());
        if (normalized == null) return false;

        for (int i = 0; i < rows.size; i++) {
            Row candidate = rows.get(i);
            if (candidate == row) continue;
            if (normalized.equals(normalize(candidate.tagField.getText()))) return false;
        }
        return true;
    }

    private Array<String> validatedTags() {
        if (rows.size > MAX_TAGS) return null;

        boolean valid = true;
        for (int i = 0; i < rows.size; i++) {
            Row row = rows.get(i);
            if (!validateRow(row)) valid = false;
        }
        if (!valid) return null;

        Array<String> result = new Array<>(true, rows.size, String.class);
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size; i++) {
            Row row = rows.get(i);
            String normalized = normalize(row.tagField.getText());
            if (normalized == null || !seen.add(normalized)) return null;
            result.add(normalized);
        }
        return result;
    }

    private static void installTooltip(Actor target, String text) {
        Tooltip tooltip = new Tooltip.Builder(text).target(target).build();
        tooltip.setAppearDelayTime(0f);
    }

    /** Canonicalisation preserved from the original tag dialog. */
    private static String normalize(String raw) {
        if (raw == null) return null;

        String tag = raw.toLowerCase().trim();
        tag = tag.replaceAll("[\\x00-\\x1F]", "");
        if (tag.contains(" ")) tag = tag.replace(" ", "");

        if (tag.isEmpty()) return null;
        if (tag.length() > MAX_LEN) tag = tag.substring(0, MAX_LEN);
        return tag;
    }

    @Override
    protected void result(Object object) {
        boolean accepted = Boolean.TRUE.equals(object);
        if (accepted) {
            Array<String> validated = validatedTags();
            if (validated == null) {
                cancel();
                return;
            }
            if (onApply != null) onApply.accept(validated);
        }
        super.result(object);
    }

    private static final class Row {
        final VisValidatableTextField tagField;
        final Button removeButton = new Button(VisUI.getSkin(), "delete");

        Row(String value) {
            tagField = new VisValidatableTextField(value != null ? value : "");
            tagField.setMessageText("tag");
            tagField.setMaxLength(MAX_LEN);
        }
    }
}
