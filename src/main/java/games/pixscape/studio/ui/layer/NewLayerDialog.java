package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.Objects;
import java.util.function.Consumer;

/** Creates one universal Pixscape Layer. Tiled Maps are added as layer content separately. */
public final class NewLayerDialog extends StudioDialog {
    private final VisTextField nameField = new VisTextField("New Layer");
    private final Consumer<NewLayerRequest> onCreate;

    public NewLayerDialog(Consumer<NewLayerRequest> onCreate) {
        super("New Layer");
        this.onCreate = Objects.requireNonNull(onCreate, "onCreate");
        TableUtils.setSpacingDefaults(this);
        setModal(true);
        setResizable(false);
        closeOnEscape();

        nameField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER;
            }
        });
        VisTable form = new VisTable(true);
        form.add(new VisLabel("Name:")).left();
        form.add(nameField).width(180f).left();
        getContentTable().add(form).pad(8f);
        button("Create", true);
        button("Cancel", false);
        pack();
        centerWindow();
    }

    @Override
    protected void result(Object object) {
        if (!Boolean.TRUE.equals(object)) return;
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        onCreate.accept(new NewLayerRequest(name.isEmpty() ? "New Layer" : name));
    }
}
