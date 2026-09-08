package games.pixscape.studio.ui.property;

import com.badlogic.gdx.utils.IntArray;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PropertiesPanelPhysicsSelectionTest {
    @Test
    public void mapEntitySelectionKeepsExplicitBodyContext() {
        IntArray selection = new IntArray(new int[]{17});

        assertTrue(PropertiesPanel.selectionIsFocusedBody(selection, 17));
        assertFalse(PropertiesPanel.selectionIsFocusedBody(selection, -1));
        assertFalse(PropertiesPanel.selectionIsFocusedBody(new IntArray(new int[]{18}), 17));
        assertFalse(PropertiesPanel.selectionIsFocusedBody(new IntArray(new int[]{17, 18}), 17));
    }
}
