package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;

import java.util.function.BooleanSupplier;

/** Prevents operating-system key repeat from deleting newly selected HUD parents. */
final class HudDeleteKeyGate {
    private boolean deletionHandledWhileHeld;

    boolean keyDown(int keycode, BooleanSupplier deleteAction) {
        if (!isDeleteKey(keycode)) return false;
        if (deletionHandledWhileHeld) return true;
        if (!deleteAction.getAsBoolean()) return false;
        deletionHandledWhileHeld = true;
        return true;
    }

    void keyUp(int keycode) {
        if (isDeleteKey(keycode)) deletionHandledWhileHeld = false;
    }

    static boolean isDeleteKey(int keycode) {
        return keycode == Input.Keys.DEL || keycode == Input.Keys.FORWARD_DEL;
    }
}
