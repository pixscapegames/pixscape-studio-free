package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.runtime.property.EnumDefinition;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.studio.importer.tmx.TiledPropertyTypes;
import games.pixscape.studio.ui.importer.TiledProjectChooser;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Prepares an embedded enum definition without modifying the parent property's working copy until accepted. */
final class EnumDefinitionDialog extends StudioDialog {
    // Unbound form fields are read together on Create; the bound wrappers commit individual fields.
    private final VisTextField nameField = new VisTextField();
    private final VisTextArea valuesField = new VisTextArea();
    private final VisLabel validationLabel = new VisLabel("");
    private final Consumer<EnumDefinition> selected;

    EnumDefinitionDialog(Consumer<EnumDefinition> selected) {
        super("Create enum");
        this.selected = selected;
        setResizable(true);
        closeOnEscape();
        var content = getContentTable();
        content.defaults().pad(4).left();
        content.add(new VisLabel("Enum name")).row();
        content.add(nameField).minWidth(360).growX().row();
        content.add(new VisLabel("Values (one per line)")).row();
        content.add(valuesField).minHeight(100).prefHeight(180).grow().row();
        validationLabel.setWrap(true);
        validationLabel.setColor(1f, 0.4f, 0.4f, 1f);
        content.add(validationLabel).growX().row();
        VisTextButton importButton = new VisTextButton("Import from Tiled…");
        importButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                TiledProjectChooser.show(getStage(), null, EnumDefinitionDialog.this::importDefinitions);
            }
        });
        content.add(importButton).row();
        button("Cancel", false);
        button("Create", true);
        pack();
    }

    @Override
    protected void result(Object result) {
        if (Boolean.TRUE.equals(result)) {
            try {
                selected.accept(createDefinition());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showValidation(ex);
                cancel();
            }
        }
    }

    private EnumDefinition createDefinition() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Enter an enum name.");
        }
        String text = valuesField.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one value, one per line.");
        }
        String[] values = text.split("\\R", -1);
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
            if (values[i].isEmpty()) {
                throw new IllegalArgumentException("Value on line " + (i + 1) + " is empty.");
            }
            if (!unique.add(values[i])) {
                throw new IllegalArgumentException("Duplicate enum value: '" + values[i] + "'.");
            }
        }
        return new EnumDefinition(name, PropertyType.STRING, false, values);
    }

    private void importDefinitions(FileHandle file) {
        try {
            var definitions = TiledPropertyTypes.load(file).enums();
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
                            EnumDefinitionDialog.this.hide();
                        } catch (RuntimeException ex) {
                            showValidation(ex);
                        }
                    }
                }
            };
            dialog.getContentTable().add(types).width(280);
            dialog.button("Cancel", false).button("OK", true);
            dialog.closeOnEscape();
            dialog.show(getStage());
        } catch (RuntimeException ex) {
            showValidation(ex);
        }
    }

    private void showValidation(RuntimeException ex) {
        validationLabel.setText(ex.getMessage() != null ? ex.getMessage() : "Invalid enum definition.");
        validationLabel.invalidateHierarchy();
        setSize(Math.max(getWidth(), getMinWidth()), Math.max(getHeight(), getMinHeight()));
    }
}
