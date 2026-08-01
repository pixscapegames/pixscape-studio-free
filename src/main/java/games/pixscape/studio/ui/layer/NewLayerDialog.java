package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.ui.modal.StudioDialog;

import java.util.function.Consumer;

public final class NewLayerDialog extends StudioDialog {

    private final VisTextField nameField = new VisTextField();
    private final VisSelectBox<String> typeBox = new VisSelectBox<>();
    private final VisLabel infoLabel = new VisLabel();

    private final VisTextField tiledWidthField = new VisTextField("256");
    private final VisTextField tiledHeightField = new VisTextField("256");
    private final VisLabel tiledWidthLabel = new VisLabel("Width (cells)");
    private final VisLabel tiledHeightLabel = new VisLabel("Height (cells)");

    private final String fallbackName;
    private final Consumer<NewLayerRequest> onCreate;

    public NewLayerDialog(Consumer<NewLayerRequest> onCreate) {
        super("New layer");

        this.fallbackName = "New Layer";
        this.onCreate = onCreate;

        TableUtils.setSpacingDefaults(this);
        setModal(true);
        setResizable(false);
        closeOnEscape();

        buildUi();
        rebuildLayerTypes();
        updateInfoLabel();

        button("Create", true);
        button("Cancel", false);

        pack();
        centerWindow();
    }

    @Override
    protected void result(Object object) {

        if (!Boolean.TRUE.equals(object)) return;

        String name = normalizeName(nameField.getText());
        int type = resolveLayerType(typeBox.getSelected());

        int width = parseIntSafe(tiledWidthField.getText(), 256);
        int height = parseIntSafe(tiledHeightField.getText(), 256);

        if (onCreate != null) {
            onCreate.accept(new NewLayerRequest(name, type, width, height));
        }
    }

    private int parseIntSafe(String text, int def) {
        try {
            int v = Integer.parseInt(text.trim());
            return v > 0 ? v : def;
        } catch (Exception e) {
            return def;
        }
    }

    // ---------------------------------------------------------------------

    private void rebuildLayerTypes() {

        SceneMeta meta = ProjectConfig.getInstance().getCurrentSceneMeta();
        if (meta == null) return;

        Array<String> types = new Array<>();
        types.add("Classic");
        types.add("Light"); // always allowed

        if (meta.physicsEnabled) {
            types.add("Physics");
        }

        if (meta.tiledEnabled) {
            types.add("Tiled");
        }

        String previous = typeBox.getSelected();
        typeBox.setItems(types);

        // Restore selection if still valid
        if (previous != null && types.contains(previous, false)) {
            typeBox.setSelected(previous);
        } else {
            typeBox.setSelected("Classic");
        }

        updateInfoLabel();
    }

    private void buildUi() {

        VisTable root = new VisTable(true);

        VisLabel nameLabel = new VisLabel("Name");
        nameField.setText(fallbackName);
        nameField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return keycode == Input.Keys.ENTER
                        || keycode == Input.Keys.NUMPAD_ENTER;
            }
        });

        VisLabel typeLabel = new VisLabel("Layer Type");
        typeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateTiledVisibility();
                updateInfoLabel();
            }
        });

        root.add(nameLabel).left();
        root.add(nameField).growX().row();

        root.add(typeLabel).left();
        root.add(typeBox).growX().row();

        root.add(tiledWidthLabel).left();
        root.add(tiledWidthField).growX().row();

        root.add(tiledHeightLabel).left();
        root.add(tiledHeightField).growX().row();

        root.add(infoLabel).center().colspan(2).padTop(4f).row();

        getContentTable().add(root).growX();
    }

    private void updateInfoLabel() {

        int type = resolveLayerType(typeBox.getSelected());

        String info = switch (type) {
            case LayerComponent.TYPE_PHYSICS -> "Physics only with scene parallax";

            case LayerComponent.TYPE_LIGHT -> "Lights only with no parallax";

            case LayerComponent.TYPE_TILED -> "Tiled Map only with parallax";

            default -> "Classic layer with parallax";
        };

        infoLabel.setText(info);
    }

    private void updateTiledVisibility() {
        boolean isTiled = "Tiled".equals(typeBox.getSelected());

        tiledWidthLabel.setVisible(isTiled);
        tiledWidthField.setVisible(isTiled);
        tiledHeightLabel.setVisible(isTiled);
        tiledHeightField.setVisible(isTiled);
    }

    private int resolveLayerType(String selected) {

        if ("Physics".equals(selected)) {
            return LayerComponent.TYPE_PHYSICS;
        }

        if ("Light".equals(selected)) {
            return LayerComponent.TYPE_LIGHT;
        }

        if ("Tiled".equals(selected)) {
            return LayerComponent.TYPE_TILED;
        }

        return LayerComponent.TYPE_CLASSIC;
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return fallbackName;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallbackName : trimmed;
    }
}
