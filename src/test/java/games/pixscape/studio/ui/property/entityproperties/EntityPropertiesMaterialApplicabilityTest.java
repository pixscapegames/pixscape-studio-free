package games.pixscape.studio.ui.property.entityproperties;

import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityPropertiesMaterialApplicabilityTest {

    @Test
    public void materialRequiresTheRenderMaterialComponentForRenderedSprites() {
        assertTrue(EntityProperties.isMaterialApplicable(EntityKind.SPRITE, true));
        assertTrue(EntityProperties.isMaterialApplicable(EntityKind.ANIMATION, true));
        assertFalse(EntityProperties.isMaterialApplicable(EntityKind.TILED_RECTANGLE, false));
        assertFalse(EntityProperties.isMaterialApplicable(EntityKind.TILED_POINT, false));
        assertFalse(EntityProperties.isMaterialApplicable(EntityKind.UNKNOWN, false));
    }

    @Test
    public void particlesRemainExcludedEvenIfTheyHaveMaterialState() {
        assertFalse(EntityProperties.isMaterialApplicable(EntityKind.PARTICLE, true));
        assertFalse(EntityProperties.isMaterialApplicable(EntityKind.PARTICLE, false));
    }
}
