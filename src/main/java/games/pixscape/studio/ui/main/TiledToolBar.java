package games.pixscape.studio.ui.main;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.service.tiled.TiledToolService;

public final class TiledToolBar extends VisTable {

    private final ImageButton brush;
    private final ImageButton fill;
    private final ImageButton erase;
    private final ImageButton rect;

    private final ImageButton flipH;
    private final ImageButton flipV;
    private final ImageButton rotL;
    private final ImageButton rotR;
    private final ImageButton reset;

    private boolean disabled = false;
    private final TiledToolService toolService;

    public TiledToolBar(TiledToolService toolService) {
        this.toolService = toolService;
        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);
        group.setUncheckLast(true);

        brush = createToolButton("brush", "Brush", this::onBrush);
        fill = createToolButton("fill", "Fill", this::onFill);
        erase = createToolButton("erase", "Erase", this::onErase);
        rect = createToolButton("rect", "Rectangle", this::onRect);

        flipH = createToolButton("flip-horizontally", "Flip horizontally", this::onFlipH);
        flipV = createToolButton("flip-vertically", "Flip vertically", this::onFlipV);
        rotL = createToolButton("rotate-left", "Rotate left", this::onRotCCW);
        rotR = createToolButton("rotate-right", "Rotate right", this::onRotCW);
        reset = createToolButton("reset", "Reset", this::onReset);


        group.add(brush, rect, erase, fill);
        brush.setChecked(true);


        add(brush).padRight(1f);
        add(fill).padRight(3f);
        add(erase).padRight(4f);
        add(rect);

        addSeparator(true).pad(7f);
        add(flipH).padRight(6f);
        add(flipV).padRight(6f);
        add(rotL).padRight(6f);
        add(rotR).padRight(6f);
        add(reset);
    }

    private ImageButton createToolButton(String style, String tooltipText, Runnable action) {
        ImageButton button = new ImageButton(VisUI.getSkin(), style);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        });
        Tooltip tip = new Tooltip.Builder(tooltipText)
                .target(button)
                .build();
        tip.setAppearDelayTime(0f);

        return button;
    }

    private void onBrush() {
        if (disabled) return;

        if (brush.isChecked()) {
            toolService.setMode(TiledToolService.Mode.BRUSH);
        }
        if (rect.isChecked()) {
            toolService.setMode(TiledToolService.Mode.RECT);
        }
        if (erase.isChecked()) {
            toolService.setMode(TiledToolService.Mode.ERASE);
        }
        if (fill.isChecked()) {
            toolService.setMode(TiledToolService.Mode.FILL);
        }
    }

    private void onRect() {
        if (disabled) return;
        if (rect.isChecked()) {
            toolService.setMode(TiledToolService.Mode.RECT);
        }
    }

    private void onErase() {
        if (disabled) return;
        if (erase.isChecked()) {
            toolService.setMode(TiledToolService.Mode.ERASE);
        }
    }

    private void onFill() {
        if (disabled) return;
        if (fill.isChecked()) {
            toolService.setMode(TiledToolService.Mode.FILL);
        }
    }


    private void onFlipH() {
        if (disabled) return;
        toolService.flipH();
    }

    private void onFlipV() {
        if (disabled) return;
        toolService.flipV();
    }

    private void onRotCW() {
        if (disabled) return;
        toolService.rotateCW();
    }

    private void onRotCCW() {
        if (disabled) return;
        toolService.rotateCCW();
    }

    private void onReset() {
        if (disabled) return;
        toolService.resetTransform();
    }


    public void setDisabled(boolean disabled) {
        this.disabled = disabled;

        brush.setDisabled(disabled);
        fill.setDisabled(disabled);
        erase.setDisabled(disabled);
        rect.setDisabled(disabled);
        flipH.setDisabled(disabled);
        flipV.setDisabled(disabled);
        rotL.setDisabled(disabled);
        rotR.setDisabled(disabled);
        reset.setDisabled(disabled);
    }

    public boolean isDisabled() {
        return disabled;
    }
}