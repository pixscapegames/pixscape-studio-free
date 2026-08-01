package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.spinner.IntSpinnerModel;
import com.kotcrab.vis.ui.widget.spinner.Spinner;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.HashSet;
import java.util.Objects;

public final class AnimationClipsDialog extends StudioDialog {

    private final AnimationComponent anim;
    private final int frameMax;

    private final VisTable listTable = new VisTable(true);
    private final VisScrollPane scroll;

    private final Runnable onApplied;

    private final Array<Row> rows = new Array<>();

    public AnimationClipsDialog(AnimationComponent anim, Runnable onApplied) {
        this(anim, onApplied, -1);
    }

    public AnimationClipsDialog(AnimationComponent anim, Runnable onApplied, int frameMaxOverride) {
        super("Edit Animation Clips");
        this.anim = Objects.requireNonNull(anim, "anim");
        this.onApplied = onApplied;

        int max = 0;
        if (anim.clips != null) {
            for (ObjectMap.Entry<String, AnimationComponent.Clip> e : anim.clips) {
                if (e == null || e.value == null) continue;
                max = Math.max(max, Math.max(e.value.start, e.value.end));
            }
        }
        this.frameMax = Math.max(0, frameMaxOverride >= 0 ? frameMaxOverride : max);

        TableUtils.setSpacingDefaults(this);
        setModal(true);
        setResizable(true);

        scroll = new VisScrollPane(listTable);
        scroll.setFadeScrollBars(false);

        buildUi();
        loadFromComponent();

        button("OK", true);
        button("Cancel", false);

        pack();
        centerWindow();
    }

    private void buildUi() {
        VisTable root = new VisTable(true);

        // Header
        root.add(new VisLabel("Frame range: 0 .. " + frameMax)).left().row();

        // Add button
        VisTextButton addBtn = new VisTextButton("Add clip");
        addBtn.setColor(CommonLayout.BUTTON_COLOR);
        addBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                addRow("clip" + (rows.size + 1), 0, frameMax);
                refreshList();
            }
        });
        root.add(addBtn).left().row();

        // List
        root.add(scroll).grow().minHeight(220).row();

        getContentTable().add(root).grow();
    }

    private void loadFromComponent() {
        rows.clear();
        if (anim.clips != null) {
            for (ObjectMap.Entry<String, AnimationComponent.Clip> e : anim.clips) {
                String name = e.key;
                AnimationComponent.Clip c = e.value;
                if (name == null || name.isBlank() || c == null) continue;
                addRow(name, c.start, c.end);
            }
        }
        if (rows.size == 0) {
            addRow("default", 0, frameMax);
        }
        refreshList();
    }

    private void addRow(String name, int start, int end) {
        Row r = new Row(frameMax);
        r.nameField.setText(name != null ? name : "");
        r.startModel.setValue(clamp(start));
        r.endModel.setValue(clamp(end));
        rows.add(r);
    }

    private void refreshList() {
        listTable.clear();

        // table header
        listTable.add(new VisLabel("Name")).left().pad(2);
        listTable.add(new VisLabel("Start")).left().pad(2);
        listTable.add(new VisLabel("End")).left().pad(2);
        listTable.add(new VisLabel("")).right().pad(2).row();

        for (int i = 0; i < rows.size; i++) {
            final int idx = i;
            Row r = rows.get(i);

            VisTextButton removeBtn = new VisTextButton("X");
            removeBtn.setColor(CommonLayout.BUTTON_COLOR);
            removeBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (idx >= 0 && idx < rows.size) {
                        rows.removeIndex(idx);
                        if (rows.size == 0) addRow("default", 0, frameMax);
                        refreshList();
                    }
                }
            });

            listTable.add(r.nameField).growX().pad(2);
            listTable.add(r.startSpinner).width(110).pad(2);
            listTable.add(r.endSpinner).width(110).pad(2);
            listTable.add(removeBtn).right().pad(2).row();
        }

        listTable.invalidateHierarchy();
    }

    @Override
    protected void result(Object object) {
        boolean ok = Boolean.TRUE.equals(object);
        if (ok) {
            applyToComponent();
            if (onApplied != null) onApplied.run();
        }
        super.result(object);
    }

    private void applyToComponent() {
        if (anim.clips == null) anim.clips = new ObjectMap<>();
        anim.clips.clear();

        HashSet<String> used = new HashSet<>();

        for (int i = 0; i < rows.size; i++) {
            Row r = rows.get(i);

            String rawName = r.nameField.getText();
            String name = normalizeName(rawName);
            if (name == null) continue;

            // unique
            if (used.contains(name)) continue;
            used.add(name);

            int start = clamp(r.startModel.getValue());
            int end = clamp(r.endModel.getValue());

            AnimationComponent.Clip clip = new AnimationComponent.Clip(start, end);
            anim.clips.put(name, clip);
        }

        // Guarantee at least one clip
        if (anim.clips.size == 0) {
            anim.clips.put("default", new AnimationComponent.Clip(0, frameMax));
            anim.currentClip = "default";
        } else {
            // currentClip must exist
            if (anim.currentClip == null || anim.currentClip.isBlank() || !anim.clips.containsKey(anim.currentClip)) {
                // pick first key
                String first = anim.clips.keys().next();
                anim.currentClip = first != null ? first : "";
            }
        }

        // Force refresh next tick
        anim.stateTime = 0f;
        anim.frame = -1;
    }

    private int clamp(int v) {
        if (v < 0) return 0;
        return Math.min(v, frameMax);
    }

    private static String normalizeName(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        // no internal spaces (optional but convenient)
        s = s.replace(" ", "");
        return s;
    }

    // ---- row struct ----
    private static final class Row {
        final VisTextField nameField = new VisTextField();

        final IntSpinnerModel startModel;
        final IntSpinnerModel endModel;

        final Spinner startSpinner;
        final Spinner endSpinner;

        Row(int frameMax) {
            nameField.setMessageText("clip");

            startModel = new IntSpinnerModel(0, 0, frameMax, 1);
            endModel = new IntSpinnerModel(frameMax, 0, frameMax, 1);

            startSpinner = new Spinner("", startModel);
            endSpinner = new Spinner("", endModel);
        }
    }
}
