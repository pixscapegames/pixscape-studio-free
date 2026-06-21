package games.pixscape.studio.system;

import org.junit.Assert;
import org.junit.Test;

public class PickingSystemScaleClampTest {

    @Test
    public void clampScaleAwayFromZero_allowsNegativeScale() {
        Assert.assertEquals(-2f, PickingSystem.clampScaleAwayFromZero(-2f, 1f, 0.01f), 0f);
        Assert.assertEquals(-0.01f, PickingSystem.clampScaleAwayFromZero(-0.005f, 1f, 0.01f), 0f);
    }

    @Test
    public void clampScaleAwayFromZero_keepsScaleNonZero() {
        Assert.assertEquals(0.01f, PickingSystem.clampScaleAwayFromZero(0f, 1f, 0.01f), 0f);
        Assert.assertEquals(-0.01f, PickingSystem.clampScaleAwayFromZero(0f, -1f, 0.01f), 0f);
        Assert.assertEquals(0.01f, PickingSystem.clampScaleAwayFromZero(0.005f, -1f, 0.01f), 0f);
    }
}
