package games.pixscape.studio.ui.property.entityproperties;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TintComponent;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.ShaderOrigin;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.studio.history.commands.ChangeShaderCommand;
import games.pixscape.studio.ui.StudioColorPickerFactory;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.ui.widget.UiBinders;

public final class MaterialPanel extends CollapsibleWidget {

    private final EntityPropertiesContext ctx;
    private final VisTable root = new VisTable(true);

    private final VisSelectBox<String> shaderBox = new VisSelectBox<>();
    private final VisSelectBox<BlendMode> blendBox = new VisSelectBox<>();

    private final ColorPicker picker;
    private final VisImage colorImage = new VisImage(VisUI.getSkin().getDrawable("white"));
    private final VisTextButton buttonPicker = new VisTextButton("");
    private final VisCheckBox showExamplesBox;

    private final UiBinders.SelectBoxBinder<String> shaderBinder;
    private final UiBinders.SelectBoxBinder<BlendMode> blendBinder;
    private final UiBinders.ColorPickerBinder pickerBinder;

    private int entityId = -1;
    private boolean internalMaterialUiUpdate = false;

    public MaterialPanel(EntityPropertiesContext ctx) {
        super();
        buttonPicker.setColor(CommonLayout.BUTTON_COLOR);
        this.ctx = ctx;

        setTable(root);
        root.left().top().pad(5);
        root.defaults().left();

        buttonPicker.setColor(Color.WHITE);
        buttonPicker.add(colorImage).width(50).height(25);

        picker = StudioColorPickerFactory.create("Sprite color chooser");
        pickerBinder = new UiBinders.ColorPickerBinder(
                ctx.world,
                picker,
                ctx.mTint::has,
                (int e) -> ctx.mTint.get(e).getRgba(),
                (Integer e, Integer before, Integer after) -> {
                    TintComponent c = ctx.mTint.get(e);
                    if (c != null && c.rgba != after) {
                        c.rgba = after;
                        if (ctx.dirtyTracker != null) ctx.dirtyTracker.color(e);
                    }
                },
                colorImage::setColor
        );

        buttonPicker.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (getStage() != null) getStage().addActor(picker.fadeIn());
            }
        });

        shaderBinder = new UiBinders.SelectBoxBinder<>(
                ctx.world,
                shaderBox,
                ctx.mMat::has,
                (int e) -> ShaderRegistry.getName(ctx.mMat.get(e).getShaderIdx()),
                (Integer e, String oldShader, String newShader) -> {
                    if (internalMaterialUiUpdate) return;
                    if (e == null || e < 0) return;
                    if (newShader == null || newShader.isEmpty()) return;

                    int beforeIdx = ctx.mMat.get(e).getShaderIdx();
                    int afterIdx = ShaderRegistry.indexOf(newShader);
                    if (beforeIdx == afterIdx) return;

                    ctx.history.execute(new ChangeShaderCommand(ctx.world, e, beforeIdx, afterIdx));
                }
        );

        blendBinder = new UiBinders.SelectBoxBinder<>(
                ctx.world,
                blendBox,
                ctx.mMat::has,
                (int e) -> {
                    var m = ctx.mMat.get(e);
                    if (m == null) return BlendMode.ALPHA;

                    BlendMode mode = BlendMode.fromId(m.getBlendModeId());
                    return (mode != null) ? mode : BlendMode.ALPHA;
                },
                (Integer e, BlendMode oldMode, BlendMode newMode) -> {
                    var m = ctx.mMat.get(e);
                    if (m == null) return;

                    BlendMode mode = (newMode != null) ? newMode : BlendMode.ALPHA;
                    int newId = mode.id;

                    if (m.blendModeId != newId) {
                        m.blendModeId = newId;
                        if (ctx.dirtyTracker != null) ctx.dirtyTracker.material(e);
                    }
                }
        );

        showExamplesBox = new VisCheckBox("Show example shaders");
        showExamplesBox.setChecked(false);
        showExamplesBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (internalMaterialUiUpdate) return;
                refreshShaderList();
            }
        });

        blendBox.setItems(BlendMode.values());

        VisTextButton shaderParamsButton = new VisTextButton("Parameters");
        shaderParamsButton.setColor(CommonLayout.BUTTON_COLOR);
        shaderParamsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (entityId < 0) return;

                RenderMaterialComponent mat = ctx.mMat.get(entityId);
                if (mat == null) return;

                int shaderIdx = mat.getShaderIdx();
                String shaderName = ShaderRegistry.getName(shaderIdx);

                ShaderParamsDialog dlg = new ShaderParamsDialog(ctx.world, entityId, shaderName);
                if (getStage() != null) getStage().addActor(dlg.fadeIn());
            }
        });

        root.add(new VisLabel("Shader:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(shaderBox).width(CommonLayout.FIELD_WIDTH).left().growX();
        root.add(shaderParamsButton).right().row();
        root.add(showExamplesBox).left().colspan(2).row();

        root.add(new VisLabel("Blend:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(blendBox).width(CommonLayout.FIELD_WIDTH).left().colspan(2).growX().row();
        root.add(new VisLabel("Color:")).width(CommonLayout.LABEL_WIDTH).left();
        root.add(buttonPicker).left().colspan(2).row();

        refreshShaderList();
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        if (entityId < 0) return;

        String currentShader = getCurrentShaderName(entityId);
        boolean currentShaderIsExample = isExampleShader(currentShader);

        internalMaterialUiUpdate = true;
        try {
            showExamplesBox.setChecked(currentShaderIsExample);
            refreshShaderListKeepingSelection(currentShader, currentShaderIsExample);
        } finally {
            internalMaterialUiUpdate = false;
        }

        shaderBinder.setEntityId(entityId);
        blendBinder.setEntityId(entityId);
        pickerBinder.setEntityId(entityId);
    }

    public void refreshShaderList() {
        String currentShader = getCurrentShaderName(entityId);
        refreshShaderListKeepingSelection(currentShader, showExamplesBox.isChecked());
    }

    private void refreshShaderListKeepingSelection(String currentShader, boolean showExamples) {
        Array<String> all = ShaderRegistry.getMainNamesForMode(ShaderMode.TEXTURE_ARRAY);
        Array<String> result = new Array<>();

        for (String name : all) {
            boolean isExample = isExampleShader(name);

            if (!showExamples && isExample && !name.equals(currentShader)) {
                continue;
            }

            result.add(name);
        }

        result.sort(String::compareTo);
        shaderBox.setItems(result);

        if (currentShader != null && result.contains(currentShader, false)) {
            shaderBox.setSelected(currentShader);
        }
    }

    private String getCurrentShaderName(int entityId) {
        if (entityId < 0 || !ctx.mMat.has(entityId)) {
            return null;
        }

        RenderMaterialComponent mat = ctx.mMat.get(entityId);
        if (mat == null) {
            return null;
        }

        return ShaderRegistry.getName(mat.getShaderIdx());
    }

    private boolean isExampleShader(String name) {
        return name != null && ShaderRegistry.getOrigin(name) == ShaderOrigin.EXAMPLE;
    }
}
