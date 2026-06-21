package games.pixscape.studio.history.commands;

import org.junit.Assert;
import org.junit.Test;

public class TransformOpTest {

    @Test
    public void includesOriginOperation() {
        Assert.assertEquals(TransformOp.ORIGIN, TransformOp.valueOf("ORIGIN"));
    }
}
