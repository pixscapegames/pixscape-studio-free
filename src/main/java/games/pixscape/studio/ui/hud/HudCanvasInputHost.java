package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.studio.service.hud.HudEditorSession;

/** Transparent Studio-stage input surface owned exclusively by the active HUD workspace. */
public final class HudCanvasInputHost extends Actor {
    private final Vector2 lower = new Vector2();
    private final Vector2 upper = new Vector2();

    public HudCanvasInputHost(HudEditorSession session) {
        setName("__pixscape-hud-canvas-input-host");
        setTouchable(Touchable.disabled);
        addCaptureListener(new HudCanvasSelectionInputListener(session));
    }

    public void activateHudInput() {
        setTouchable(Touchable.enabled);
    }

    public void suspendHudInput() {
        setTouchable(Touchable.disabled);
        Stage stage = getStage();
        if (stage != null) {
            HudCanvasSelectionInputListener.releaseOwnedScrollFocus(stage, this);
            stage.cancelTouchFocus(this);
        }
    }

    /** Matches the input surface to the already-resolved HUD viewport in Studio-stage coordinates. */
    public void coverStageBounds(Rectangle stageBounds) {
        Group parent = getParent();
        if (parent == null || stageBounds == null) return;
        lower.set(stageBounds.x, stageBounds.y);
        upper.set(stageBounds.x + stageBounds.width, stageBounds.y + stageBounds.height);
        parent.stageToLocalCoordinates(lower);
        parent.stageToLocalCoordinates(upper);
        setBounds(lower.x, lower.y, upper.x - lower.x, upper.y - lower.y);
    }
}
