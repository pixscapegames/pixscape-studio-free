package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;

import java.util.Objects;

public final class ValidationHooks {

    private ValidationHooks() {
    }

    public static void installEnterAndFocusLostValidation(Actor field, Runnable validatorAction) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(validatorAction, "validatorAction");

        field.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    validatorAction.run();
                }
                return false;
            }
        });

        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (!focused) validatorAction.run();
            }
        });
    }
}
