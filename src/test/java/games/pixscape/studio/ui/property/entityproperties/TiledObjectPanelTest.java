package games.pixscape.studio.ui.property.entityproperties;

import org.junit.Test;
import games.pixscape.studio.component.TiledObjectComponent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TiledObjectPanelTest {

    @Test
    public void displaysModernTiledClassReadOnlyAndUsesNeutralEmptyValue() {
        assertEquals("Enemy", TiledObjectPanel.displayClassName("Enemy"));
        assertEquals("  Boss Enemy.v2  ", TiledObjectPanel.displayClassName("  Boss Enemy.v2  "));
        assertEquals(TiledObjectPanel.NO_CLASS, TiledObjectPanel.displayClassName(""));
        assertEquals(TiledObjectPanel.NO_CLASS, TiledObjectPanel.displayClassName(null));
    }

    @Test
    public void isApplicableOnlyForEntitiesWithTiledObjectMetadata() {
        assertTrue(TiledObjectPanel.isApplicable(new TiledObjectComponent()));
        assertFalse(TiledObjectPanel.isApplicable(null));
    }
}
