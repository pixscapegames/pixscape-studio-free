package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.runtime.property.EnumDefinition;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.widget.ColorPickerField;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Copy-based generic editor for a {@link PropertySet}, including nested CLASS/LIST and enum values.
 */
public final class EditPropertiesDialog extends StudioDialog {

    private final PropertySet workingCopy;
    private final Consumer<PropertySet> onApply;
    private final World world;
    private final boolean tileDefinition;
    private final boolean listMode;
    private final Consumer<Array<PropertyValue>> onListApply;
    private final Array<ObjectChoice> objectChoices = new Array<ObjectChoice>();
    private final Array<PropertyRow> rows = new Array<PropertyRow>();
    private final VisTable rowsTable = new VisTable(true);
    private final VisLabel validationLabel = new VisLabel("");
    private final VisTextButton addButton = new VisTextButton("+ Add property");

    public EditPropertiesDialog(String title,
                                PropertySet source,
                                Consumer<PropertySet> onApply) {
        this(title, source, null, onApply);
    }

    public EditPropertiesDialog(String title,
                                PropertySet source,
                                World world,
                                Consumer<PropertySet> onApply) {
        this(title, source, world, onApply, false);
    }

    /**
     * Tile definitions share the typed editor; OBJECT choices are limited to None.
     */
    public EditPropertiesDialog(String title, PropertySet source, Consumer<PropertySet> onApply, boolean tileDefinition) {
        this(title, source, null, onApply, tileDefinition);
    }

    private EditPropertiesDialog(String title, PropertySet source, World world,
                                 Consumer<PropertySet> onApply, boolean tileDefinition) {
        this(title, source, world, onApply, tileDefinition, null, null);
    }

    private EditPropertiesDialog(String title, PropertySet source, World world, Consumer<PropertySet> onApply,
                                 boolean tileDefinition, Array<PropertyValue> list, Consumer<Array<PropertyValue>> onListApply) {
        super(title);
        this.listMode = list != null;
        this.onListApply = onListApply;
        this.tileDefinition = tileDefinition;
        this.workingCopy = source != null ? source.copy() : new PropertySet();
        this.world = world;
        this.onApply = onApply;
        populateObjectChoices();

        TableUtils.setSpacingDefaults(this);
        setResizable(true);
        setModal(true);
        closeOnEscape();

        if (listMode) {
            addButton.setText("+ Add item");
            for (int i = 0; i < list.size; i++) {
                PropertyValue value = list.get(i);
                rows.add(new PropertyRow("[" + i + "]", value.type(), value));
            }
        } else {
            loadRows(workingCopy);
        }
        addButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                rows.add(new PropertyRow("", PropertyType.STRING, null));
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
        root.add(new VisLabel(listMode ? "Index" : "Name")).minWidth(120).prefWidth(120).growX().left();
        root.add(new VisLabel("Type")).width(105).left();
        root.add(new VisLabel("Value")).minWidth(260).prefWidth(260).growX().left();
        root.add().width(listMode ? 110 : 28).row();

        VisScrollPane scroll = new VisScrollPane(rowsTable);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        root.add(scroll).colspan(4).grow().minWidth(listMode ? 680 : 580).minHeight(140).prefHeight(300).row();
        root.add(addButton).colspan(4).left().padTop(4).row();
        if (tileDefinition) {
            VisLabel notice = new VisLabel("Tile definitions cannot reference scene entities. Preserved unsupported metadata is read-only.");
            notice.setWrap(true);
            root.add(notice).colspan(4).growX().left().padTop(6).row();
        }
        validationLabel.setWrap(true);
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
            if (listMode) {
                row.nameField.setText("[" + i + "]");
            }
            rowsTable.add(row.nameField).minWidth(120).prefWidth(120).growX();
            rowsTable.add(row.typeBox).width(105).left();
            rowsTable.add(row.valueEditor()).minWidth(260).prefWidth(260).growX();
            VisTable actions = new VisTable(true);
            if (listMode) {
                actions.add(actionButton("+", () -> {
                    rows.insert(rows.indexOf(row, true), new PropertyRow("", PropertyType.STRING, null));
                    refreshRows();
                }));
                Button up = new Button(VisUI.getSkin(), "up");
                Button down = new Button(VisUI.getSkin(), "down");
                up.setName("property-list-up");
                down.setName("property-list-down");
                bindAction(up, () -> move(row, -1));
                bindAction(down, () -> move(row, 1));
                new Tooltip.Builder("Move item up").target(up).build();
                new Tooltip.Builder("Move item down").target(down).build();
                up.setDisabled(i == 0);
                down.setDisabled(i == rows.size - 1);
                actions.add(up);
                actions.add(down);
            }
            actions.add(row.removeButton);
            rowsTable.add(actions).right().top().pad(2).row();
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
            result.put(name, row.value(path));
        }
        result.validate();
        return result;
    }

    private static VisTextButton actionButton(String label, Runnable action) {
        VisTextButton button = new VisTextButton(label);
        bindAction(button, action);
        return button;
    }

    private static void bindAction(Button button, Runnable action) {
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
    }

    private void move(PropertyRow row, int delta) {
        int index = rows.indexOf(row, true);
        int destination = index + delta;
        if (destination >= 0 && destination < rows.size) {
            rows.swap(index, destination);
            refreshRows();
        }
    }

    private Array<PropertyValue> buildValidatedList() {
        Array<PropertyValue> result = new Array<>(rows.size);
        for (int i = 0; i < rows.size; i++) {
            result.add(rows.get(i).value(getTitleLabel().getText() + "[" + i + "]"));
        }
        return PropertyValue.ofList(result).listValuesCopy();
    }

    private static String propertyPath(String parentPath, String name) {
        return parentPath == null || parentPath.isEmpty() ? name : parentPath + "." + name;
    }

    private void populateObjectChoices() {
        objectChoices.add(new ObjectChoice(-1, "None"));
        if (world == null) {
            return;
        }
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, PixscapeIdentityComponent.class))
                .getEntities();
        for (int i = 0; i < entities.size(); i++) {
            int entityId = entities.get(i);
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).getSafe(entityId, null);
            if (identity == null || identity.stableId <= 0) continue;
            String name = identity.name == null || identity.name.isBlank() ? "unnamed" : identity.name;
            objectChoices.add(new ObjectChoice(identity.stableId,
                    name + " (#" + identity.stableId + ")"));
        }
        objectChoices.sort((first, second) -> Integer.compare(first.stableId, second.stableId));
    }

    private ObjectChoice objectChoice(int stableId) {
        for (int i = 0; i < objectChoices.size; i++) {
            ObjectChoice choice = objectChoices.get(i);
            if (choice.stableId == stableId) return choice;
        }
        ObjectChoice missing = new ObjectChoice(stableId, "Missing entity (#" + stableId + ")");
        objectChoices.add(missing);
        return missing;
    }

    @Override
    protected void result(Object object) {
        if (!Boolean.TRUE.equals(object)) {
            super.result(object);
            return;
        }
        try {
            if (listMode) {
                Array<PropertyValue> validated = buildValidatedList();
                if (onListApply != null) {
                    onListApply.accept(validated);
                }
            } else {
                PropertySet validated = buildValidatedPropertySet("");
                workingCopy.copyFrom(validated);
                if (onApply != null) {
                    onApply.accept(validated.copy());
                }
            }
            validationLabel.setText("");
            super.result(object);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            validationLabel.setText(ex.getMessage() != null ? ex.getMessage() : "Invalid property value.");
            validationLabel.invalidateHierarchy();
            setSize(Math.max(getWidth(), getMinWidth()), Math.max(getHeight(), getMinHeight()));
            cancel();
        }
    }

    private final class PropertyRow {
        final boolean readOnly;
        final PropertyValue preservedValue;
        final VisTextField nameField;
        final VisSelectBox<PropertyType> typeBox = new VisSelectBox<PropertyType>();
        final VisTextArea stringField = new VisTextArea();
        final VisCheckBox booleanField = new VisCheckBox("");
        final VisTextField numberField = new VisTextField();
        final VisTextField classNameField = new VisTextField();
        final VisTextButton editClassButton = new VisTextButton("Edit…");
        final Button removeButton = new Button(VisUI.getSkin(), "delete");
        final Color colorValue = new Color(0f, 0f, 0f, 0f);
        final ColorPickerField colorField = new ColorPickerField(
                "Property color", "Choose color…")
                .allowAlpha(true)
                .useColorSwatch(22f, 22f)
                .bind(() -> colorValue, colorValue::set);
        final VisSelectBox<ObjectChoice> objectBox = new VisSelectBox<ObjectChoice>();
        PropertySet classMembers = new PropertySet();
        private PropertyType displayedType;
        private boolean selectingType;
        private boolean authoredValue;
        private Array<PropertyValue> listValues = new Array<>();
        private PropertyValue enumValue;
        private final VisTextButton editListButton = new VisTextButton("Edit…");

        PropertyRow(String name, PropertyType type, PropertyValue value) {
            authoredValue = value != null;
            readOnly = type == PropertyType.IMPORTED;
            preservedValue = readOnly ? value.copy() : null;
            nameField = new VisTextField(name != null ? name : "");
            if (readOnly) {
                typeBox.setItems(type);
            } else {
                typeBox.setItems(PropertyType.STRING, PropertyType.BOOLEAN, PropertyType.INTEGER, PropertyType.FLOAT,
                        PropertyType.COLOR, PropertyType.OBJECT, PropertyType.CLASS, PropertyType.LIST, PropertyType.ENUM);
            }
            typeBox.setSelected(type != null ? type : PropertyType.STRING);
            objectBox.setItems(objectChoices);
            loadValue(value);
            displayedType = typeBox.getSelected();
            nameField.setDisabled(readOnly || listMode);
            typeBox.setDisabled(readOnly);
            removeButton.setDisabled(readOnly);

            typeBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    PropertyType selected = typeBox.getSelected();
                    if (!selectingType && selected != displayedType) {
                        selectingType = true;
                        typeBox.setSelected(displayedType);
                        selectingType = false;
                        Runnable replace = () -> {
                            if (selected == PropertyType.ENUM) {
                                chooseEnumDefinition(definition -> {
                                    PropertyValue candidate = definition.storageType() == PropertyType.STRING
                                            ? PropertyValue.ofEnum(definition, definition.isFlags() ? "" : definition.choiceAt(0))
                                            : PropertyValue.ofEnum(definition, 0);
                                    resetForType(selected);
                                    enumValue = candidate;
                                    selectType(selected);
                                });
                            } else {
                                resetForType(selected);
                                selectType(selected);
                            }
                        };
                        if (hasAuthoredValue()) {
                            confirmTypeReplacement(replace);
                        } else {
                            replace.run();
                        }
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
            editListButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    new EditPropertiesDialog(nestedTitle(PropertyRow.this), null, world, null, tileDefinition,
                            listValues, values -> {
                                listValues = PropertyValue.ofList(values).listValuesCopy();
                                refreshRows();
                            }).show(getStage());
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
                    numberField.setText(Double.toString(value.asDouble()));
                    break;
                case COLOR:
                    Color.rgba8888ToColor(colorValue, value.asColorRgba8888());
                    colorField.refresh();
                    break;
                case OBJECT:
                    ObjectChoice choice = objectChoice(value.asObjectStableId());
                    // VisSelectBox keeps its own item array. A dangling reference adds its
                    // synthetic choice lazily, so refresh before selecting it.
                    objectBox.setItems(objectChoices);
                    objectBox.setSelected(choice);
                    break;
                case LIST:
                    listValues = value.listValuesCopy();
                    break;
                case ENUM:
                    enumValue = value.copy();
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
            colorValue.set(0f, 0f, 0f, 0f);
            colorField.refresh();
            objectBox.setSelected(objectChoice(-1));
            classNameField.setText("");
            classMembers = new PropertySet();
            listValues = new Array<>();
            enumValue = null;
        }

        private PropertyValue value(String path) {
            if (readOnly) {
                return preservedValue.copy();
            }
            return switch (displayedType) {
                case STRING -> PropertyValue.ofString(stringField.getText());
                case BOOLEAN -> PropertyValue.ofBoolean(booleanField.isChecked());
                case INTEGER -> PropertyValue.ofInt(PropertyAuthoringValidation.parseInteger(numberField.getText(), path));
                case FLOAT -> PropertyValue.ofFloat(PropertyAuthoringValidation.parseFloat(numberField.getText(), path));
                case COLOR -> PropertyValue.ofColorRgba8888(Color.rgba8888(colorValue));
                case OBJECT -> PropertyValue.ofObjectStableId(objectBox.getSelected().stableId);
                case CLASS -> PropertyValue.ofClass(PropertyAuthoringValidation.requireClassName(classNameField.getText(), path), classMembers);
                case LIST -> PropertyValue.ofList(listValues);
                case ENUM -> {
                    if (enumValue == null) {
                        throw PropertyAuthoringValidation.invalid(path, "select an enum definition.");
                    }
                    yield enumValue.copy();
                }
                default -> throw PropertyAuthoringValidation.invalid(path, "unsupported property type.");
            };
        }

        private boolean hasAuthoredValue() {
            if (authoredValue) {
                return true;
            }
            return switch (displayedType) {
                case STRING -> !stringField.getText().isEmpty();
                case BOOLEAN -> booleanField.isChecked();
                case INTEGER, FLOAT -> !numberField.getText().matches("0(?:[.,]0+)?");
                case OBJECT -> objectBox.getSelected().stableId != -1;
                case CLASS -> !classMembers.isEmpty() || !classNameField.getText().isEmpty();
                case LIST -> listValues.size > 0;
                case ENUM -> enumValue != null;
                default -> true;
            };
        }

        private void selectType(PropertyType type) {
            authoredValue = true;
            selectingType = true;
            displayedType = type;
            typeBox.setSelected(type);
            selectingType = false;
            refreshRows();
        }

        private Actor enumEditor() {
            if (enumValue == null) {
                return new VisLabel("Select an enum definition");
            }
            var definition = enumValue.enumDefinitionCopy();
            var view = enumValue.asEnum();
            VisTable table = new VisTable(true);
            table.add(new VisLabel(view.typeName())).left().row();
            if (!view.isFlags()) {
                VisSelectBox<String> choice = new VisSelectBox<>();
                Array<String> names = new Array<>();
                for (int i = 0; i < view.choiceCount(); i++) {
                    names.add(view.choiceAt(i));
                }
                choice.setItems(names);
                for (int i = 0; i < view.choiceCount(); i++) {
                    if (view.isSelected(i)) {
                        choice.setSelectedIndex(i);
                    }
                }
                choice.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        enumValue = definition.storageType() == PropertyType.STRING
                                ? PropertyValue.ofEnum(definition, choice.getSelected())
                                : PropertyValue.ofEnum(definition, choice.getSelectedIndex());
                    }
                });
                table.add(choice).growX();
            } else {
                VisTable choices = new VisTable(true);
                Array<VisCheckBox> boxes = new Array<>();
                for (int i = 0; i < view.choiceCount(); i++) {
                    VisCheckBox box = new VisCheckBox(view.choiceAt(i));
                    box.setChecked(view.isSelected(i));
                    boxes.add(box);
                    choices.add(box).left().row();
                    box.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            int mask = 0;
                            StringBuilder names = new StringBuilder();
                            for (int j = 0; j < boxes.size; j++) {
                                if (boxes.get(j).isChecked()) {
                                    mask |= 1 << j;
                                    if (names.length() > 0) {
                                        names.append(',');
                                    }
                                    names.append(definition.choiceAt(j));
                                }
                            }
                            enumValue = definition.storageType() == PropertyType.STRING
                                    ? PropertyValue.ofEnum(definition, names.toString()) : PropertyValue.ofEnum(definition, mask);
                        }
                    });
                }
                VisScrollPane scroll = new VisScrollPane(choices);
                scroll.setFadeScrollBars(false);
                table.add(scroll).height(Math.min(120, Math.max(28, view.choiceCount() * 26))).growX();
            }
            return table;
        }

        private Actor valueEditor() {
            if (readOnly) {
                String description = preservedValue.type() == PropertyType.IMPORTED
                        ? preservedValue.asImported().typeName() + " " + preservedValue.asImported().customTypeName()
                        : preservedValue.type().name();
                VisLabel label = new VisLabel(description + " (read-only)");
                if (preservedValue.type() == PropertyType.IMPORTED)
                    new Tooltip.Builder(preservedValue.asImported().sourceXml()).target(label).build();
                else
                    new Tooltip.Builder(new com.badlogic.gdx.utils.Json().prettyPrint(preservedValue)).target(label).build();
                return label;
            }
            switch (typeBox.getSelected()) {
                case STRING:
                    return stringField;
                case BOOLEAN:
                    return booleanField;
                case INTEGER:
                case FLOAT:
                    return numberField;
                case COLOR:
                    return colorField;
                case OBJECT:
                    return objectBox;
                case LIST:
                    VisTable listEditor = new VisTable(true);
                    listEditor.add(new VisLabel(listValues.size + " items")).growX().left();
                    listEditor.add(editListButton);
                    return listEditor;
                case ENUM:
                    return enumEditor();
                case CLASS:
                    VisTable classEditor = new VisTable(true);
                    classEditor.add(classNameField).minWidth(100).prefWidth(155).growX();
                    classEditor.add(editClassButton).right();
                    return classEditor;
                default:
                    return new VisLabel("");
            }
        }
    }

    private void openClassEditor(PropertyRow row) {
        EditPropertiesDialog nested = new EditPropertiesDialog(nestedTitle(row), row.classMembers, world, members -> {
            row.classMembers = members.copy();
            validationLabel.setText("");
        }, tileDefinition);
        nested.show(getStage());
    }

    private String nestedTitle(PropertyRow row) {
        return getTitleLabel().getText() + " / " + row.nameField.getText();
    }

    private void confirmTypeReplacement(Runnable replace) {
        StudioDialog dialog = new StudioDialog("Change property type") {
            @Override
            protected void result(Object result) {
                if (Boolean.TRUE.equals(result)) {
                    replace.run();
                }
            }
        };
        dialog.text("Replace this value with a different type? The current value will be discarded.");
        dialog.button("Cancel", false).button("OK", true);
        dialog.closeOnEscape();
        dialog.show(getStage());
    }

    private void chooseEnumDefinition(Consumer<EnumDefinition> selected) {
        games.pixscape.studio.ui.importer.TiledProjectChooser.show(getStage(), null, file -> {
            try {
                var definitions = games.pixscape.studio.importer.tmx.TiledPropertyTypes.load(file).enums();
                if (definitions.isEmpty()) {
                    throw new IllegalArgumentException("This project defines no enums.");
                }
                VisSelectBox<String> types = new VisSelectBox<>();
                Array<String> names = new Array<>();
                for (var definition : definitions) {
                    names.add(definition.name());
                }
                types.setItems(names);
                StudioDialog dialog = new StudioDialog("Choose enum type") {
                    @Override
                    protected void result(Object result) {
                        if (Boolean.TRUE.equals(result)) {
                            try {
                                selected.accept(definitions.get(types.getSelectedIndex()));
                            }
                            catch (RuntimeException ex) {
                                validationLabel.setText(ex.getMessage());
                            }
                        }
                    }
                };
                dialog.getContentTable().add(types).width(280);
                dialog.button("Cancel", false).button("OK", true);
                dialog.closeOnEscape();
                dialog.show(getStage());
            } catch (RuntimeException ex) {
                validationLabel.setText(ex.getMessage());
            }
        });
    }

    private static final class ObjectChoice {
        final int stableId;
        final String label;

        ObjectChoice(int stableId, String label) {
            this.stableId = stableId;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
