package games.pixscape.studio.system;

import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PickingSystemAnchorMathTest {

    @Test
    public void worldPointToLocalAnchorMeters_noRotation() {
        Vector2 out = new Vector2();
        PickingSystem.worldPointToLocalAnchorMeters(
                110f, 220f,
                100f, 200f,
                0f,
                10f,
                out
        );

        assertEquals(1f, out.x, 0.0001f);
        assertEquals(2f, out.y, 0.0001f);
    }

    @Test
    public void worldPointToLocalAnchorMeters_withRotation() {
        Vector2 out = new Vector2();
        float angle = (float) (Math.PI * 0.5);

        PickingSystem.worldPointToLocalAnchorMeters(
                100f, 210f,
                100f, 200f,
                angle,
                10f,
                out
        );

        assertEquals(1f, out.x, 0.0001f);
        assertEquals(0f, out.y, 0.0001f);
    }
}
