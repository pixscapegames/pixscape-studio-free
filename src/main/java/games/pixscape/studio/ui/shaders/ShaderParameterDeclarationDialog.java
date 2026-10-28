package games.pixscape.studio.ui.shaders;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.render.batch.ShaderParameterLayout;
import games.pixscape.studio.ui.modal.Dialogs;
import games.pixscape.studio.ui.modal.StudioModalWindow;

import java.util.function.Consumer;

/** Edits the ordered declaration; entity overrides are edited in Material > Parameters. */
final class ShaderParameterDeclarationDialog extends StudioModalWindow {
    private static final class Row {
        final VisTextField name = new VisTextField();
        final VisTextField value = new VisTextField();
        final VisTextButton remove = new VisTextButton("Remove");
    }

    private final Array<Row> rows = new Array<>();
    private final VisTable table = new VisTable(true);
    private final String shaderName;
    private final Consumer<Array<ShaderFloatParam>> onApply;

    ShaderParameterDeclarationDialog(String shaderName, Array<ShaderFloatParam> initial,
                                     Consumer<Array<ShaderFloatParam>> onApply) {
        super("Declare shader parameters - " + shaderName);
        this.shaderName = shaderName;
        this.onApply = onApply;
        setModal(true);
        setMovable(true);
        closeOnEscape();

        VisTable root = new VisTable(true);
        root.pad(8);
        root.add(new VisLabel("Up to 16 float parameters. GLSL uses PIXSCAPE_PARAM_<name>."))
                .left().row();
        root.add(new VisLabel("Existing entity values are keyed by name. Rows keep their shown order."))
                .left().row();
        VisScrollPane scroll = new VisScrollPane(table);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).width(570).height(330).growX().row();

        VisTextButton add = new VisTextButton("Add parameter");
        VisTextButton apply = new VisTextButton("Apply");
        VisTextButton cancel = new VisTextButton("Cancel");
        VisTable actions = new VisTable(true);
        actions.add(add).left().expandX();
        actions.add(apply);
        actions.add(cancel);
        root.add(actions).growX().row();
        add(root).grow();

        if (initial != null) for (ShaderFloatParam parameter : initial) addRow(parameter.name, parameter.value);
        rebuild();
        add.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (rows.size >= ShaderParameterLayout.MAX_FLOATS) {
                    Dialogs.showErrorDialog(getStage(), "Maximum 16 float parameters per shader.");
                    return;
                }
                addRow("", 0f);
                rebuild();
            }
        });
        apply.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { applyRows(); }
        });
        cancel.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { fadeOut(); }
        });
        pack();
        centerWindow();
    }

    private void addRow(String name, float value) {
        Row row = new Row();
        row.name.setText(name == null ? "" : name);
        row.value.setText(Float.toString(value));
        row.name.setMessageText("u_gain");
        row.remove.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                rows.removeValue(row, true);
                rebuild();
            }
        });
        rows.add(row);
    }

    private void rebuild() {
        table.clearChildren();
        table.add(new VisLabel("Slot")).width(45);
        table.add(new VisLabel("Name")).width(230);
        table.add(new VisLabel("Default")).width(100);
        table.add().row();
        for (int i = 0; i < rows.size; i++) {
            Row row = rows.get(i);
            table.add(new VisLabel(Integer.toString(i))).left();
            table.add(row.name).growX();
            table.add(row.value).width(100);
            table.add(row.remove).row();
        }
        table.invalidateHierarchy();
    }

    private void applyRows() {
        Array<ShaderFloatParam> result = new Array<>();
        try {
            for (Row row : rows) {
                String name = row.name.getText().trim();
                float value = Float.parseFloat(row.value.getText().trim());
                result.add(new ShaderFloatParam(name, value));
            }
            new ShaderParameterLayout(shaderName, result);
        } catch (RuntimeException invalid) {
            Dialogs.showErrorDialog(getStage(), "Invalid declaration: names must be unique GLSL identifiers, "
                    + "and defaults must be finite floats (maximum 16).");
            return;
        }
        onApply.accept(result);
        fadeOut();
    }
}
