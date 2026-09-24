package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HudDeleteKeyGateTest {
    @Test public void consumesOnlyHandledHudDeletesAndSuppressesRepeatUntilKeyUp() {
        HudDeleteKeyGate gate = new HudDeleteKeyGate();
        AtomicInteger deletions = new AtomicInteger();

        assertFalse(gate.keyDown(Input.Keys.DEL, () -> false));
        assertTrue(gate.keyDown(Input.Keys.DEL, () -> {
            deletions.incrementAndGet();
            return true;
        }));
        assertTrue(gate.keyDown(Input.Keys.DEL, () -> {
            deletions.incrementAndGet();
            return true;
        }));
        assertEquals(1, deletions.get());

        gate.keyUp(Input.Keys.DEL);
        assertTrue(gate.keyDown(Input.Keys.DEL, () -> {
            deletions.incrementAndGet();
            return true;
        }));
        assertEquals(2, deletions.get());
    }
}
