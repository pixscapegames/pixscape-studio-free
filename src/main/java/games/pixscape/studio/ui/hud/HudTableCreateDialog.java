package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.Input;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.function.BiConsumer;

/** Small modal that captures TABLE dimensions before any authored mutation occurs. */
final class HudTableCreateDialog {
    static final int MAX_DIMENSION = 64;

    private HudTableCreateDialog() {
    }

    static void show(Stage stage, BiConsumer<Integer, Integer> confirmed) {
        if (stage == null || confirmed == null) return;
        VisTextField rows = positiveField();
        VisTextField columns = positiveField();
        StudioDialog dialog = new StudioDialog("New Table") {
            @Override protected void result(Object object) {
                if (!Boolean.TRUE.equals(object)) return;
                int rowCount = positive(rows.getText());
                int columnCount = positive(columns.getText());
                if (rowCount < 1 || columnCount < 1 || rowCount > MAX_DIMENSION
                        || columnCount > MAX_DIMENSION) {
                    cancel();
                    rows.setText(rowCount < 1 || rowCount > MAX_DIMENSION ? "" : String.valueOf(rowCount));
                    columns.setText(columnCount < 1 || columnCount > MAX_DIMENSION
                            ? "" : String.valueOf(columnCount));
                    return;
                }
                confirmed.accept(rowCount, columnCount);
            }
        };
        dialog.getContentTable().add(new VisLabel("Rows")).left();
        dialog.getContentTable().add(rows).width(96f).row();
        dialog.getContentTable().add(new VisLabel("Columns")).left();
        dialog.getContentTable().add(columns).width(96f).row();
        dialog.getContentTable().add(new VisLabel("Maximum: " + MAX_DIMENSION + " × " + MAX_DIMENSION))
                .colspan(2).left();
        dialog.button("Create", true);
        dialog.button("Cancel", false);
        dialog.key(Input.Keys.ENTER, true);
        dialog.key(Input.Keys.NUMPAD_ENTER, true);
        dialog.key(Input.Keys.ESCAPE, false);
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.pack();
        dialog.show(stage);
        stage.setKeyboardFocus(rows);
    }

    private static VisTextField positiveField() {
        VisTextField field = new VisTextField("1");
        field.setTextFieldFilter(new VisTextField.TextFieldFilter.DigitsOnlyFilter());
        return field;
    }

    private static int positive(String text) {
        try { return Integer.parseInt(text != null ? text.trim() : ""); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
