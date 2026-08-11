package games.pixscape.studio.ui.property.entityproperties;

import games.pixscape.runtime.component.PixscapeIdentityComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EntityPropertiesIdentityPresentationTest {

    @Test
    public void labelAndTooltipClarifyPersistentEntityIdentity() {
        assertEquals("ID:", EntityProperties.ENTITY_ID_LABEL);
        assertEquals(
                "Persistent ID of this entity.\nThis is not an Asset ID or an ECS entity ID.",
                EntityProperties.ENTITY_ID_TOOLTIP
        );
        assertFalse(EntityProperties.ENTITY_ID_LABEL.contains("Internal"));
    }

    @Test
    public void displayedValueComesFromStableIdNotArtemisEntityId() {
        PixscapeIdentityComponent identity = new PixscapeIdentityComponent();
        identity.stableId = 812;
        int artemisEntityId = 17;

        assertEquals(812, EntityProperties.displayedPersistentId(identity));
        assertFalse(artemisEntityId == EntityProperties.displayedPersistentId(identity));
    }
}
