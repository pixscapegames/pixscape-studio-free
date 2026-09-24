package games.pixscape.studio.ui.asset;

import org.junit.Assert;
import org.junit.Test;

public class HudScreenCreationDialogValidationTest {
    @Test
    public void parsesOnlyPositiveWholeDimensions() {
        Assert.assertEquals(1920, AssetsPanel.parsePositiveDimension("1920", "Width"));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> AssetsPanel.parsePositiveDimension("0", "Width"));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> AssetsPanel.parsePositiveDimension("-1", "Width"));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> AssetsPanel.parsePositiveDimension("abc", "Width"));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> AssetsPanel.parsePositiveDimension("", "Width"));
    }
}
