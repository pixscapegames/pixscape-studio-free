package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.util.TableUtils;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;

import java.util.Objects;
import java.util.function.Consumer;

public final class NewLayerDialog extends StudioDialog {

    private static final float CONTENT_WIDTH = 300f;
    private static final float LABEL_WIDTH = 80f;
    private static final float FIELD_WIDTH = 100f;

    private final VisTextField nameField = new VisTextField();
    private final VisSelectBox<String> typeBox = new VisSelectBox<>();
    private final VisLabel infoLabel = new VisLabel();

    private final VisTextField tiledWidthField = new VisTextField("256");
    private final VisTextField tiledHeightField = new VisTextField("256");
    private final VisLabel tiledWidthLabel = new VisLabel("Width (cells):");
    private final VisLabel tiledHeightLabel = new VisLabel("Height (cells):");
    private final CollapsibleVisTable tiledOptions = new CollapsibleVisTable(true, true);

    private final String fallbackName;
    private final LayerService layerService;
    private final Consumer<NewLayerRequest> onCreate;

    public NewLayerDialog(LayerService layerService, Consumer<NewLayerRequest> onCreate) {
        super("New layer");

        this.fallbackName = "New Layer";
        this.layerService = Objects.requireNonNull(layerService, "layerService");
        this.onCreate = onCreate;

        TableUtils.setSpacingDefaults(this);
        setModal(true);
        setResizable(false);
        closeOnEscape();

        buildUi();
        rebuildLayerTypes();

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
            onCreate.accept(new NewLayerRequest(
                    name, type, isSpatialSelection(typeBox.getSelected()), width, height));
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
        boolean hasSpatialActorLayer = layerService.hasSpatialActorLayer();
        Array<String> types = availableLayerTypes(meta, hasSpatialActorLayer);

        String previous = typeBox.getSelected();
        typeBox.setItems(types);

        // Restore selection if still valid
        if (previous != null && types.contains(previous, false)) {
            typeBox.setSelected(previous);
        } else {
            typeBox.setSelected("Classic");
        }

        updateSelectedTypeLayout(false);
    }

    private void buildUi() {

        VisTable root = new VisTable(true);
        VisTable form = new VisTable(true);

        VisLabel nameLabel = new VisLabel("Name:");
        nameField.setText(fallbackName);
        nameField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return keycode == Input.Keys.ENTER
                        || keycode == Input.Keys.NUMPAD_ENTER;
            }
        });

        VisLabel typeLabel = new VisLabel("Layer Type:");
        typeBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                updateSelectedTypeLayout(true);
            }
        });

        form.add(nameLabel).width(LABEL_WIDTH).left();
        form.add(nameField).width(FIELD_WIDTH).left().row();

        form.add(typeLabel).width(LABEL_WIDTH).left();
        form.add(typeBox).width(FIELD_WIDTH).left().row();

        VisTable tiledContent = tiledOptions.content();
        tiledContent.add(tiledWidthLabel).width(LABEL_WIDTH).left();
        tiledContent.add(tiledWidthField).width(FIELD_WIDTH).left().row();

        tiledContent.add(tiledHeightLabel).width(LABEL_WIDTH).left();
        tiledContent.add(tiledHeightField).width(FIELD_WIDTH).left().row();

        form.add(tiledOptions).colspan(2).fillX().row();

        root.add(form).left().row();

        infoLabel.setWrap(true);
        infoLabel.setAlignment(Align.center);
        root.add(infoLabel).center().width(CONTENT_WIDTH).fillX().padTop(4f).row();

        getContentTable().add(root).width(CONTENT_WIDTH).growX();
    }

    private void updateInfoLabel() {

        if (isSpatialSelection(typeBox.getSelected())) {
            infoLabel.setText("Single actor layer with Spatial depth ordering");
            return;
        }

        int type = resolveLayerType(typeBox.getSelected());

        String info = switch (type) {
            case LayerComponent.TYPE_PHYSICS -> "Physics only with scene parallax";

            case LayerComponent.TYPE_LIGHT -> "Lights only with no parallax";

            case LayerComponent.TYPE_TILED -> "Tiled Map only with parallax";

            default -> "Classic layer with parallax";
        };

        infoLabel.setText(info);
    }

    private void updateSelectedTypeLayout(boolean recenterIfSizeChanged) {
        float previousWidth = getWidth();
        float previousHeight = getHeight();
        boolean isTiled = "Tiled".equals(typeBox.getSelected());

        tiledOptions.show(isTiled, false);
        updateInfoLabel();

        getContentTable().invalidateHierarchy();
        invalidateHierarchy();
        pack();
        validate();

        boolean sizeChanged = previousWidth != getWidth() || previousHeight != getHeight();
        if (recenterIfSizeChanged && sizeChanged && getStage() != null) {
            centerWindow();
        }
    }

    private int resolveLayerType(String selected) {

        if ("Physics".equals(selected)) {
            return LayerComponent.TYPE_PHYSICS;
        }

        if (isSpatialSelection(selected)) {
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

    static Array<String> availableLayerTypes(SceneMeta meta, boolean hasSpatialActorLayer) {
        Array<String> types = new Array<>();
        types.add("Classic");
        types.add("Light");
        if (meta != null && meta.physicsEnabled) {
            types.add("Physics");
            if (!hasSpatialActorLayer) {
                types.add("Spatial");
            }
        }
        if (meta != null && meta.tiledEnabled) {
            types.add("Tiled");
        }
        return types;
    }

    private static boolean isSpatialSelection(String selected) {
        return "Spatial".equals(selected);
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return fallbackName;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallbackName : trimmed;
    }
}
