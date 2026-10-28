package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import games.pixscape.studio.service.hud.HudEditorSession;

import java.util.Objects;

/** Routes HUD TEST input through the native Stage while Studio owns its panels and overlays. */
public final class HudTestInputRouter extends InputAdapter {
    private final HudEditorSession session;
    private final Stage studioStage;
    private final Vector2 studioPoint = new Vector2();

    public HudTestInputRouter(HudEditorSession session, Stage studioStage) {
        this.session = Objects.requireNonNull(session, "session");
        this.studioStage = Objects.requireNonNull(studioStage, "studioStage");
    }

    @Override public boolean keyDown(int keycode) {
        Stage stage = session.testStage();
        if (stage == null || studioStage.getKeyboardFocus() != null) return false;
        stage.keyDown(keycode);
        return true;
    }

    @Override public boolean keyUp(int keycode) {
        Stage stage = session.testStage();
        if (stage == null || studioStage.getKeyboardFocus() != null) return false;
        stage.keyUp(keycode);
        return true;
    }

    @Override public boolean keyTyped(char character) {
        Stage stage = session.testStage();
        if (stage == null || studioStage.getKeyboardFocus() != null) return false;
        stage.keyTyped(character);
        return true;
    }

    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Stage stage = session.testStage();
        if (stage == null) return false;
        if (!canStartAt(screenX, screenY)) {
            endHudHover(stage);
            return false;
        }
        stage.getRoot().setTouchable(Touchable.enabled);
        studioStage.setKeyboardFocus(null);
        studioStage.setScrollFocus(null);
        stage.touchDown(screenX, screenY, pointer, button);
        return true; // Even empty HUD canvas belongs to TEST, not the editor.
    }

    @Override public boolean touchDragged(int screenX, int screenY, int pointer) {
        Stage stage = session.testStage();
        if (stage == null) return false;
        boolean handled = stage.touchDragged(screenX, screenY, pointer);
        if (canStartAt(screenX, screenY)) stage.getRoot().setTouchable(Touchable.enabled);
        else endHudHover(stage);
        return handled;
    }

    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        Stage stage = session.testStage();
        if (stage == null) return false;
        boolean handled = stage.touchUp(screenX, screenY, pointer, button);
        if (canStartAt(screenX, screenY)) stage.getRoot().setTouchable(Touchable.enabled);
        else endHudHover(stage);
        return handled;
    }

    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        Stage stage = session.testStage();
        if (stage != null) stage.touchCancelled(screenX, screenY, pointer, button);
        return false; // Studio must cancel any independent touch focus it owns as well.
    }

    @Override public boolean mouseMoved(int screenX, int screenY) {
        Stage stage = session.testStage();
        if (stage == null) return false;
        if (!canStartAt(screenX, screenY)) {
            endHudHover(stage);
            return false;
        }
        stage.getRoot().setTouchable(Touchable.enabled);
        return stage.mouseMoved(screenX, screenY);
    }

    @Override public boolean scrolled(float amountX, float amountY) {
        Stage stage = session.testStage();
        if (stage == null || studioStage.getScrollFocus() != null) return false;
        if (Gdx.input != null && !canStartAt(Gdx.input.getX(), Gdx.input.getY())) return false;
        stage.getRoot().setTouchable(Touchable.enabled);
        stage.scrolled(amountX, amountY);
        return true;
    }

    private boolean canStartAt(int screenX, int screenY) {
        if (Gdx.graphics == null || !session.testViewportContains(
                screenX, screenY, Gdx.graphics.getHeight())) return false;
        studioStage.screenToStageCoordinates(studioPoint.set(screenX, screenY));
        Actor hit = studioStage.hit(studioPoint.x, studioPoint.y, true);
        return hit == null || hit == studioStage.getRoot();
    }

    private static void endHudHover(Stage stage) {
        if (stage.getRoot().getTouchable() == Touchable.disabled) return;
        // mouseMoved stores coordinates; act emits exit. A modal Window can hit even outside
        // the viewport, so keep the native root out of hit testing until HUD input resumes.
        // Touch focus still receives drag/up while the root is non-touchable.
        stage.mouseMoved(-1, -1);
        stage.getRoot().setTouchable(Touchable.disabled);
        stage.act(0f);
    }
}
