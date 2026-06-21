package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ValidationHooksTest {

    @Test
    public void installEnterAndFocusLostValidation_runsOnceOnEnterAndOnceOnFocusLost() {
        Actor actor = new Actor();
        AtomicInteger calls = new AtomicInteger(0);

        ValidationHooks.installEnterAndFocusLostValidation(actor, calls::incrementAndGet);

        for (var listener : actor.getListeners()) {
            if (listener instanceof InputListener inputListener) {
                inputListener.keyDown(null, Input.Keys.ENTER);
            }
        }
        for (var listener : actor.getListeners()) {
            if (listener instanceof FocusListener focusListener) {
                focusListener.keyboardFocusChanged(null, actor, false);
            }
        }

        assertEquals(2, calls.get());
    }

    @Test
    public void installEnterAndFocusLostValidation_ignoresOtherKeysAndFocusGain() {
        Actor actor = new Actor();
        AtomicInteger calls = new AtomicInteger(0);

        ValidationHooks.installEnterAndFocusLostValidation(actor, calls::incrementAndGet);

        for (var listener : actor.getListeners()) {
            if (listener instanceof InputListener inputListener) {
                inputListener.keyDown(null, Input.Keys.A);
            }
            if (listener instanceof FocusListener focusListener) {
                focusListener.keyboardFocusChanged(null, actor, true);
            }
        }

        assertEquals(0, calls.get());
    }
}
