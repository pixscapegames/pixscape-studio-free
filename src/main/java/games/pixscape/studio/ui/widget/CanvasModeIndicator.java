package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Disposable;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;

/** Stable, non-interactive canvas overlay showing the authoritative Studio editing mode. */
public final class CanvasModeIndicator extends VisTable implements Disposable {
    private static final String NORMAL_BACKGROUND = "canvas-mode-background-normal";
    private static final String SPECIALIZED_BACKGROUND = "canvas-mode-background";

    private final VisTable content = new VisTable();
    private final VisLabel label = new VisLabel("", "canvas-mode-label");
    private StudioEditingMode mode;
    private boolean tiledCursorValid;
    private int tiledCursorGX;
    private int tiledCursorGY;
    private final EventFlow.Listener<EventFlow.StudioEditingModeChanged> modeListener =
            event -> update(event.mode());
    private final EventFlow.Listener<EventFlow.TiledCursorChanged> tiledCursorListener =
            event -> updateTiledCursor(event.valid(), event.gx(), event.gy());

    public CanvasModeIndicator(StudioEditingModeService modeService) {
        setTouchable(Touchable.disabled);
        content.setTouchable(Touchable.disabled);
        label.setTouchable(Touchable.disabled);

        setBackground(VisUI.getSkin().getDrawable("canvas-mode-border"));
        pad(1f);
        content.add(label).pad(3f, 10f, 3f, 10f);
        add(content);

        update(modeService.getCurrentMode());
        EventFlow.i().subscribe(EventFlow.StudioEditingModeChanged.class, modeListener);
        EventFlow.i().subscribe(EventFlow.TiledCursorChanged.class, tiledCursorListener);
    }

    private void update(StudioEditingMode mode) {
        if (this.mode != mode) tiledCursorValid = false;
        this.mode = mode;
        updateText();
        String background = mode == StudioEditingMode.NORMAL
                ? NORMAL_BACKGROUND
                : SPECIALIZED_BACKGROUND;
        content.setBackground(VisUI.getSkin().getDrawable(background));
        invalidateHierarchy();
    }

    private void updateTiledCursor(boolean valid, int gx, int gy) {
        if (tiledCursorValid == valid && (!valid || tiledCursorGX == gx && tiledCursorGY == gy)) return;
        tiledCursorValid = valid;
        tiledCursorGX = gx;
        tiledCursorGY = gy;
        if (mode == StudioEditingMode.TILED) {
            updateText();
            invalidateHierarchy();
        }
    }

    private void updateText() {
        if (mode != StudioEditingMode.TILED) {
            label.setText("Mode: " + displayName(mode));
        } else if (tiledCursorValid) {
            label.setText("Mode: Tiled    Tile: (" + tiledCursorGX + ", " + tiledCursorGY + ")");
        } else {
            label.setText("Mode: Tiled    Tile: (-, -)");
        }
    }

    public static String displayName(StudioEditingMode mode) {
        return switch (mode) {
            case NORMAL -> "Normal";
            case PHYSICS -> "Physics";
            case SPATIAL -> "Spatial";
            case TILED -> "Tiled";
            case LIGHTS -> "Lights";
        };
    }

    public String getDisplayedText() {
        return label.getText().toString();
    }

    public boolean isUsingNormalBackground() {
        return content.getBackground() == VisUI.getSkin().getDrawable(NORMAL_BACKGROUND);
    }

    @Override
    public void dispose() {
        EventFlow.i().unsubscribe(EventFlow.StudioEditingModeChanged.class, modeListener);
        EventFlow.i().unsubscribe(EventFlow.TiledCursorChanged.class, tiledCursorListener);
    }
}
