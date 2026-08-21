package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisImage;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.kotcrab.vis.ui.widget.color.ColorPickerListener;
import games.pixscape.studio.ui.StudioColorPickerFactory;
import games.pixscape.studio.ui.config.CommonLayout;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Bouton color picker simple (non ECS).
 */
public final class ColorPickerField extends VisTable {

    private final String title;
    private final VisTextButton button;
    private final Color current = new Color(Color.WHITE);
    private VisImage colorSwatch;

    private Supplier<Color> reader;
    private Consumer<Color> applier;
    private boolean internalRefresh = false;
    private boolean allowAlpha = true;

    public ColorPickerField(String title, String buttonText) {
        this.title = title;
        this.button = new VisTextButton(buttonText);
        button.setColor(CommonLayout.BUTTON_COLOR);
        add(button).left();

        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showPicker();
            }
        });
    }

    public ColorPickerField allowAlpha(boolean allow) {
        this.allowAlpha = allow;
        return this;
    }

    public ColorPickerField useColorSwatch(float width, float height) {
        if (colorSwatch == null) {
            colorSwatch = new VisImage(VisUI.getSkin().getDrawable("white"));
            button.clearChildren();
            button.setText("");
            button.add(colorSwatch).width(width).height(height);
        }
        updateSwatchColor();
        return this;
    }

    public ColorPickerField bind(Supplier<Color> reader, Consumer<Color> applier) {
        this.reader = reader;
        this.applier = applier;
        refresh();
        return this;
    }

    public void refresh() {
        if (reader == null) return;
        internalRefresh = true;
        try {
            Color value = reader.get();
            if (value != null) current.set(value);
            updateSwatchColor();
        } finally {
            internalRefresh = false;
        }
    }

    public void setDisabled(boolean disabled) {
        button.setDisabled(disabled);
    }

    public Color getCurrentColor() {
        return current;
    }

    private void showPicker() {
        ColorPicker picker = StudioColorPickerFactory.create(title);
        picker.setColor(current);
        picker.setAllowAlphaEdit(allowAlpha);
        picker.setListener(new ColorPickerListener() {
            @Override
            public void changed(Color newColor) {
                applyColor(newColor);
            }

            @Override
            public void canceled(Color oldColor) {
            }

            @Override
            public void reset(Color previousColor, Color newColor) {
            }

            @Override
            public void finished(Color newColor) {
            }
        });

        if (getStage() != null) {
            getStage().addActor(picker.fadeIn());
        }
    }

    private void applyColor(Color color) {
        if (internalRefresh) return;
        if (color == null) return;
        current.set(color);
        updateSwatchColor();
        if (applier != null) applier.accept(new Color(current));
    }

    private void updateSwatchColor() {
        if (colorSwatch != null) {
            colorSwatch.setColor(current);
        }
    }
}
