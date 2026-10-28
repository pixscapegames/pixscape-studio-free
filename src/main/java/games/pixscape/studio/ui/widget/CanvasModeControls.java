package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.studio.service.StudioEditingModeService;

import java.util.Objects;

/** Owns the mode label and the stable HUD test action as separate sibling actors. */
public final class CanvasModeControls extends VisTable implements Disposable {
    static final float CONTROL_GAP = 8f;

    private final CanvasModeIndicator indicator;
    private final VisTextButton hudTestToggle = new VisTextButton("");
    private final StudioEditingModeService modeService;
    private final Runnable modeListener = this::refresh;

    public CanvasModeControls(CanvasModeIndicator indicator,
                              StudioEditingModeService modeService) {
        this.indicator = Objects.requireNonNull(indicator, "indicator");
        this.modeService = Objects.requireNonNull(modeService, "modeService");
        setTouchable(Touchable.childrenOnly);
        hudTestToggle.setTouchable(Touchable.enabled);
        hudTestToggle.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                modeService.toggleHudTestMode();
                refresh();
            }
        });
        rebuildContent();
        modeService.addListener(modeListener);
        refresh();
    }

    /** Restores the stable actors after DockManager rebuilds its ruler-aware overlay cells. */
    public void rebuildContent() {
        clearChildren();
        add(indicator);
        if (modeService.hasActiveHudDocument()) {
            add(hudTestToggle).padLeft(CONTROL_GAP);
        }
        invalidateHierarchy();
    }

    private void refresh() {
        boolean hud = modeService.hasActiveHudDocument();
        if (hud != (hudTestToggle.getParent() == this)) {
            rebuildContent();
        }
        boolean test = modeService.isHudTestMode();
        hudTestToggle.setText(test ? "Switch to EDIT mode" : "Switch to TEST mode");
        hudTestToggle.setDisabled(!test && !modeService.canEnterHudTestMode());
        invalidateHierarchy();
    }

    public CanvasModeIndicator indicator() {
        return indicator;
    }

    public VisTextButton hudTestToggle() {
        return hudTestToggle;
    }

    @Override public void dispose() {
        modeService.removeListener(modeListener);
    }
}
