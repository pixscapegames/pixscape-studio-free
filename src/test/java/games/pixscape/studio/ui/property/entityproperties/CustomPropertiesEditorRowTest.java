package games.pixscape.studio.ui.property.entityproperties;

import org.junit.Assert;
import org.junit.Test;

public class CustomPropertiesEditorRowTest {

    @Test
    public void summaryUsesCompactSingularAndPluralWording() {
        Assert.assertEquals("0 properties", CustomPropertiesEditorRow.summaryFor(0));
        Assert.assertEquals("1 property", CustomPropertiesEditorRow.summaryFor(1));
        Assert.assertEquals("4 properties", CustomPropertiesEditorRow.summaryFor(4));
    }
}
