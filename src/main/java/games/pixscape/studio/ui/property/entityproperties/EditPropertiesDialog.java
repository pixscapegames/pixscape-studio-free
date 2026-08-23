package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Copy-based generic editor for a {@link PropertySet}, including nested CLASS values.
 */
public final class EditPropertiesDialog extends StudioDialog {

    private final PropertySet workingCopy;
    private final Consumer<PropertySet> onApply;
    private final Array<PropertyRow> rows = new Array<PropertyRow>();
    private final VisTable rowsTable = new VisTable(true);
    private final VisLabel validationLabel = new VisLabel("");
    private final VisTextButton addButton = new VisTextButton("+ Add property");

    public EditPropertiesDialog(String title,
                                PropertySet source,
                                Consumer<PropertySet> onApply) {
        super(title);
        this.workingCopy = source != null ? source.copy() : new PropertySet();
        this.onApply = onApply;

        TableUtils.setSpacingDefaults(this);
        setResizable(true);
        setModal(true);

        loadRows(workingCopy);
        addButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                rows.add(new PropertyRow("", PropertyType.STRING, PropertyValue.ofString("")));
                refreshRows();
            }
        });

        buildUi();
        refreshRows();
        button("Cancel", false);
        button("Apply", true);
        pack();
        centerWindow();
    }

    private void loadRows(PropertySet source) {
        Array<String> names = new Array<String>();
        source.copyNamesTo(names);
        names.sort();
        for (int i = 0; i < names.size; i++) {
            String name = names.get(i);
            PropertyValue value = source.valueCopy(name);
            if (value != null) rows.add(new PropertyRow(name, value.type(), value));
        }
    }

    private void buildUi() {
        VisTable root = new VisTable(true);
        root.left().top();
        root.add(new VisLabel("Name")).width(120).left();
        root.add(new VisLabel("Type")).width(105).left();
        root.add(new VisLabel("Value")).width(260).left();
        root.add().width(28).row();

        VisScrollPane scroll = new VisScrollPane(rowsTable);
        scroll.setFadeScrollBars(false);
        root.add(scroll).colspan(4).grow().minWidth(540).minHeight(220).row();
        root.add(addButton).colspan(4).left().padTop(4).row();
        validationLabel.setColor(1f, 0.4f, 0.4f, 1f);
        root.add(validationLabel).colspan(4).growX().left().padTop(6).row();
        getContentTable().add(root).grow();
    }

    private void refreshRows() {
        rowsTable.clearChildren();
        rowsTable.left().top();
        rowsTable.defaults().left().pad(2);
        for (int i = 0; i < rows.size; i++) {
            PropertyRow row = rows.get(i);
            rowsTable.add(row.nameField).width(120).growX();
            rowsTable.add(row.typeBox).width(105).left();
            rowsTable.add(row.valueEditor()).width(260).growX();
            rowsTable.add(row.removeButton)
                    .size(row.removeButton.getPrefWidth(), row.removeButton.getPrefHeight())
                    .right().top().pad(2).row();
        }
        rowsTable.invalidateHierarchy();
    }

    private PropertySet buildValidatedPropertySet(String parentPath) {
        Set<String> names = new HashSet<String>();
        for (int i = 0; i < rows.size; i++) {
            PropertyRow row = rows.get(i);
            String name = PropertyAuthoringValidation.requireName(row.nameField.getText(), parentPath);
            String path = propertyPath(parentPath, name);
            if (!names.add(name)) {
                throw PropertyAuthoringValidation.invalid(path, "duplicate property name.");
            }
        }

        PropertySet result = new PropertySet(rows.size);
        for (int i = 0; i < rows.size; i++) {
            PropertyRow row = rows.get(i);
            String name = row.nameField.getText();
            String path = propertyPath(parentPath, name);
            PropertyType type = row.typeBox.getSelected();
            switch (type) {
                case STRING:
                    result.putString(name, row.stringField.getText());
                    break;
                case BOOLEAN:
                    result.putBoolean(name, row.booleanField.isChecked());
                    break;
                case INTEGER:
                    result.putInt(name, PropertyAuthoringValidation.parseInteger(row.numberField.getText(), path));
                    break;
                case FLOAT:
                    result.putFloat(name, PropertyAuthoringValidation.parseFloat(row.numberField.getText(), path));
                    break;
                case CLASS:
                    String className = PropertyAuthoringValidation.requireClassName(
                            row.classNameField.getText(), path);
                    PropertySet members = row.classMembers != null ? row.classMembers.copy() : new PropertySet();
                    members.validate();
                    result.putClass(name, className, members);
                    break;
                default:
                    throw PropertyAuthoringValidation.invalid(path, "unsupported property type.");
            }
        }
        result.validate();
        return result;
    }

    private static String propertyPath(String parentPath, String name) {
        return parentPath == null || parentPath.isEmpty() ? name : parentPath + "." + name;
    }

    @Override
    protected void result(Object object) {
        if (!Boolean.TRUE.equals(object)) {
            super.result(object);
            return;
        }
        try {
            PropertySet validated = buildValidatedPropertySet("");
            workingCopy.copyFrom(validated);
            validationLabel.setText("");
            if (onApply != null) onApply.accept(validated.copy());
            super.result(object);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            validationLabel.setText(ex.getMessage() != null ? ex.getMessage() : "Invalid property value.");
            validationLabel.invalidateHierarchy();
            pack();
            cancel();
        }
    }

    private final class PropertyRow {
        final VisTextField nameField;
        final VisSelectBox<PropertyType> typeBox = new VisSelectBox<PropertyType>();
        final VisTextArea stringField = new VisTextArea();
        final VisCheckBox booleanField = new VisCheckBox("");
        final VisTextField numberField = new VisTextField();
        final VisTextField classNameField = new VisTextField();
        final VisTextButton editClassButton = new VisTextButton("Edit…");
        final Button removeButton = new Button(VisUI.getSkin(), "delete");
        PropertySet classMembers = new PropertySet();
        private PropertyType displayedType;

        PropertyRow(String name, PropertyType type, PropertyValue value) {
            nameField = new VisTextField(name != null ? name : "");
            typeBox.setItems(PropertyType.values());
            typeBox.setSelected(type != null ? type : PropertyType.STRING);
            loadValue(value);
            displayedType = typeBox.getSelected();

            typeBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    PropertyType selected = typeBox.getSelected();
                    if (selected != displayedType) {
                        resetForType(selected);
                        displayedType = selected;
                        refreshRows();
                    }
                }
            });
            removeButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    rows.removeValue(PropertyRow.this, true);
                    refreshRows();
                }
            });
            editClassButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    openClassEditor(PropertyRow.this);
                }
            });
        }

        private void loadValue(PropertyValue value) {
            PropertyType type = typeBox.getSelected();
            if (value == null || value.type() != type) {
                resetForType(type);
                return;
            }
            switch (type) {
                case STRING:
                    stringField.setText(value.asString());
                    break;
                case BOOLEAN:
                    booleanField.setChecked(value.asBoolean());
                    break;
                case INTEGER:
                    numberField.setText(Integer.toString(value.asInt()));
                    break;
                case FLOAT:
                    numberField.setText(Float.toString(value.asFloat()));
                    break;
                case CLASS:
                    classNameField.setText(value.className());
                    classMembers = value.classPropertiesCopy();
                    break;
                default:
                    break;
            }
        }

        private void resetForType(PropertyType type) {
            stringField.setText("");
            booleanField.setChecked(false);
            numberField.setText(type == PropertyType.FLOAT ? "0.0" : "0");
            classNameField.setText("");
            classMembers = new PropertySet();
        }

        private Actor valueEditor() {
            switch (typeBox.getSelected()) {
                case STRING:
                    return stringField;
                case BOOLEAN:
                    return booleanField;
                case INTEGER:
                case FLOAT:
                    return numberField;
                case CLASS:
                    VisTable classEditor = new VisTable(true);
                    classEditor.add(classNameField).width(155).growX();
                    classEditor.add(editClassButton).right();
                    return classEditor;
                default:
                    return new VisLabel("");
            }
        }
    }

    private void openClassEditor(PropertyRow row) {
        String name = row.nameField.getText();
        String title = name == null || name.isEmpty() ? "Edit Class Properties" : "Edit " + name;
        EditPropertiesDialog nested = new EditPropertiesDialog(title, row.classMembers, members -> {
            row.classMembers = members.copy();
            validationLabel.setText("");
        });
        nested.show(getStage());
    }
}
