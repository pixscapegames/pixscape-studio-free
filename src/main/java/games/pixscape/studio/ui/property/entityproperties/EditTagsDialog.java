package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.List;
import java.util.function.Consumer;

public final class EditTagsDialog extends StudioDialog {

    private static final int MAX_TAGS = 16;
    private static final int MAX_LEN = 32;

    private final Array<String> tags = new Array<>();
    private final Consumer<Array<String>> onApply;

    private final VisTable listTable = new VisTable(true);
    private final VisTextField addField = new VisTextField();
    private final VisTextButton addBtn = new VisTextButton("Add");

    public EditTagsDialog(String title, List<String> initialTags, Consumer<Array<String>> onApply) {
        super(title);
        addBtn.setColor(CommonLayout.BUTTON_COLOR);
        this.onApply = onApply;

        if (initialTags != null) {
            for (String t : initialTags) addTagInternal(t);
        }

        TableUtils.setSpacingDefaults(this);
        setResizable(true);
        setModal(true);

        buildUi();
        refreshList();
        refreshAddEnabled();

        button("OK", true);
        button("Cancel", false);

        pack();
        centerWindow();
    }

    private void buildUi() {
        VisTable root = new VisTable(true);

        addField.setMessageText("new tag…");
        addField.setMaxLength(MAX_LEN);

        // ENTER in the field => Add
        addField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    tryAddFromField();
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                // refresh enablement au fil de la frappe
                refreshAddEnabled();
                return false;
            }
        });

        addBtn.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                tryAddFromField();
            }
        });

        VisTable addRow = new VisTable(true);
        addRow.add(addField).growX();
        addRow.add(addBtn).right();

        VisScrollPane scroll = new VisScrollPane(listTable);
        scroll.setFadeScrollBars(false);

        root.add(new VisLabel("Tags (max " + MAX_TAGS + ")")).left().row();
        root.add(addRow).growX().row();
        root.add(scroll).grow().minHeight(180).row();

        getContentTable().add(root).grow();
    }

    private void tryAddFromField() {
        String raw = addField.getText();
        if (addTagInternal(raw)) {
            addField.setText("");
            refreshList();
        }
        refreshAddEnabled();
    }

    private void refreshList() {
        listTable.clear();

        if (tags.isEmpty()) {
            listTable.add(new VisLabel("(no tags)")).left().pad(4).row();
        } else {
            for (int i = 0; i < tags.size; i++) {
                final int idx = i;
                String t = tags.get(i);

                VisLabel label = new VisLabel(t);
                label.setAlignment(Align.left);

                VisTextButton remove = new VisTextButton("X");
                remove.setColor(CommonLayout.BUTTON_COLOR);
                remove.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        if (idx >= 0 && idx < tags.size) {
                            tags.removeIndex(idx);
                            refreshList();
                            refreshAddEnabled();
                        }
                    }
                });

                listTable.add(label).growX().left().pad(2);
                listTable.add(remove).right().pad(2).row();
            }
        }
    }

    private void refreshAddEnabled() {
        if (tags.size >= MAX_TAGS) {
            addBtn.setDisabled(true);
            return;
        }
        String t = normalize(addField.getText());
        if (t == null) {
            addBtn.setDisabled(true);
            return;
        }
        // disable if duplicate
        for (int i = 0; i < tags.size; i++) {
            if (tags.get(i).equals(t)) {
                addBtn.setDisabled(true);
                return;
            }
        }
        addBtn.setDisabled(false);
    }

    /**
     * @return true if added
     */
    private boolean addTagInternal(String raw) {
        String t = normalize(raw);
        if (t == null) return false;
        if (tags.size >= MAX_TAGS) return false;

        for (int i = 0; i < tags.size; i++) {
            if (tags.get(i).equals(t)) return false;
        }

        tags.add(t);
        return true;
    }

    /**
     * Canonicalisation du tag (v1: identifier friendly)
     */
    private static String normalize(String raw) {
        if (raw == null) return null;

        String t = raw.toLowerCase().trim();

        // remove controls
        t = t.replaceAll("[\\x00-\\x1F]", "");

        // option: interdire espaces internes (tags = identifiants)
        if (t.contains(" ")) t = t.replace(" ", "");

        if (t.isEmpty()) return null;
        if (t.length() > MAX_LEN) t = t.substring(0, MAX_LEN);

        return t;
    }

    @Override
    protected void result(Object object) {
        boolean ok = Boolean.TRUE.equals(object);
        if (ok && onApply != null) {
            onApply.accept(new Array<>(tags));
        }
        super.result(object);
    }
}
