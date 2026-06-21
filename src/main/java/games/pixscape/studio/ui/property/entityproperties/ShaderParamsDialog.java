package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.ValidationHooks;

public class ShaderParamsDialog extends VisWindow {

    private static final float NAME_COL_WIDTH = 140f;
    private static final float VALUE_COL_WIDTH = 80f;

    private final World world;
    private final int entityId;
    private final String shaderName;

    private final ComponentMapper<ShaderParamsComponent> mParams;

    private final VisTable paramsTable;
    private final VisTextButton addButton;
    private final VisTextButton okButton;
    private final VisTextButton cancelButton;

    private static final class Row {
        VisValidatableTextField nameField;
        VisValidatableTextField valueField;
        VisTextButton removeButton;
    }

    private final Array<Row> rows = new Array<>();

    public ShaderParamsDialog(World world, int entityId, String shaderName) {
        super("Shader parameters - " + shaderName);

        this.world = world;
        this.entityId = entityId;
        this.shaderName = shaderName;

        this.mParams = world.getMapper(ShaderParamsComponent.class);

        setModal(true);
        setMovable(true);
        setResizable(true);
        addCloseButton();
        closeOnEscape();

        VisTable root = new VisTable(true);
        root.pad(8);

        root.add(new VisLabel("Uniform parameters (float)")).left().colspan(2).row();

        paramsTable = new VisTable(true);
        paramsTable.top();

        paramsTable.add(new VisLabel("Name")).width(NAME_COL_WIDTH).left();
        paramsTable.add(new VisLabel("Value")).width(VALUE_COL_WIDTH).left();
        paramsTable.add().width(30f);
        paramsTable.row();

        ScrollPane scroll = new VisScrollPane(paramsTable);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        root.add(scroll).growX().height(180f).width(300f).colspan(2).row();

        addButton = new VisTextButton("Add parameter");
        addButton.setColor(CommonLayout.BUTTON_COLOR);
        okButton = new VisTextButton("OK");
        okButton.setColor(CommonLayout.BUTTON_COLOR);
        cancelButton = new VisTextButton("Cancel");
        cancelButton.setColor(CommonLayout.BUTTON_COLOR);

        VisTable buttons = new VisTable(true);
        buttons.add(addButton);
        buttons.add(okButton);
        buttons.add(cancelButton);

        root.add(buttons).right().colspan(2).padTop(6);

        add(root).grow();

        buildInitialRowsFromComponentOrPreset();
        hookListeners();

        pack();
        centerWindow();
    }

    private void buildInitialRowsFromComponentOrPreset() {
        Array<ShaderFloatParam> source = new Array<>();

        ShaderParamsComponent comp = mParams.get(entityId);

        if (comp != null && comp.floats != null && comp.floats.size > 0) {
            copyShaderFloats(comp.floats, source);
        } else {
            Array<ShaderFloatParam> defaults = ShaderRegistry.getDefaultUniforms(shaderName);
            copyShaderFloats(defaults, source);
        }

        source.sort((a, b) -> {
            String an = a != null && a.name != null ? a.name : "";
            String bn = b != null && b.name != null ? b.name : "";
            return an.compareTo(bn);
        });

        for (ShaderFloatParam param : source) {
            if (param == null || param.name == null || param.name.isEmpty()) {
                continue;
            }

            addRow(param.name, Float.toString(param.value));
        }
    }

    private static void copyShaderFloats(Array<ShaderFloatParam> source,
                                         Array<ShaderFloatParam> target) {
        if (source == null) return;

        for (ShaderFloatParam param : source) {
            if (param == null || param.name == null || param.name.isEmpty()) {
                continue;
            }

            target.add(new ShaderFloatParam(param.name, param.value));
        }
    }

    private void addRow(String uniformName, String valueStr) {
        Row row = new Row();
        row.nameField = new VisValidatableTextField(uniformName != null ? uniformName : "");
        row.valueField = new VisValidatableTextField(valueStr != null ? valueStr : "0.0");
        row.removeButton = new VisTextButton("X");
        row.removeButton.setColor(CommonLayout.BUTTON_COLOR);

        row.nameField.setMessageText("u_param");
        row.valueField.setMessageText("0.0");

        row.nameField.addValidator(input -> ShaderParamValidation.isNameValid(input, row.valueField.getText()));
        row.valueField.addValidator(input -> ShaderParamValidation.isValueValid(row.nameField.getText(), input));

        ValidationHooks.installEnterAndFocusLostValidation(row.nameField, () -> validateRow(row));
        ValidationHooks.installEnterAndFocusLostValidation(row.valueField, () -> validateRow(row));

        paramsTable.row();
        paramsTable.add(row.nameField).width(NAME_COL_WIDTH).left();
        paramsTable.add(row.valueField).width(VALUE_COL_WIDTH).left();
        paramsTable.add(row.removeButton).width(30f).left();

        rows.add(row);

        row.removeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                removeRow(row);
            }
        });
    }

    private void removeRow(Row row) {
        rows.removeValue(row, true);
        paramsTable.clearChildren();

        paramsTable.add(new VisLabel("Name")).width(NAME_COL_WIDTH).left();
        paramsTable.add(new VisLabel("Value")).width(VALUE_COL_WIDTH).left();
        paramsTable.add().width(30f);
        paramsTable.row();

        for (Row r : rows) {
            paramsTable.row();
            paramsTable.add(r.nameField).width(NAME_COL_WIDTH).left();
            paramsTable.add(r.valueField).width(VALUE_COL_WIDTH).left();
            paramsTable.add(r.removeButton).width(30f).left();
        }
    }

    private void hookListeners() {
        addButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                addRow("", "0.0");
            }
        });

        okButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyAndClose();
            }
        });

        cancelButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                fadeOut();
            }
        });
    }

    private void applyAndClose() {
        if (!world.getEntityManager().isActive(entityId)) {
            fadeOut();
            return;
        }

        boolean hasInvalidRow = false;

        for (Row row : rows) {
            if (!validateRow(row)) {
                hasInvalidRow = true;
            }
        }

        if (hasInvalidRow) return;

        ShaderParamsComponent comp = mParams.get(entityId);
        if (comp == null) {
            comp = mParams.create(entityId);
        }

        if (comp.floats == null) {
            comp.floats = new Array<>();
        } else {
            comp.floats.clear();
        }

        for (Row row : rows) {
            String name = row.nameField.getText().trim();
            String valStr = row.valueField.getText().trim();

            if (name.isEmpty() && valStr.isEmpty()) continue;

            float v = Float.parseFloat(valStr);
            comp.floats.add(new ShaderFloatParam(name, v));
        }

        fadeOut();
    }

    private boolean validateRow(Row row) {
        row.nameField.validateInput();
        row.valueField.validateInput();

        return row.nameField.isInputValid()
                && row.valueField.isInputValid()
                && ShaderParamValidation.isRowValid(row.nameField.getText(), row.valueField.getText());
    }
}