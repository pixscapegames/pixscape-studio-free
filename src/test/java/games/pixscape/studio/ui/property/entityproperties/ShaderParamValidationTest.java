package games.pixscape.studio.ui.property.entityproperties;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShaderParamValidationTest {

    @Test
    public void rowValidation_invalidValue_isRejectedForEnterAndFocusLostPaths() {
        String name = "u_strength";
        String invalid = "not-a-float";

        boolean enterPath = ShaderParamValidation.isRowValid(name, invalid);
        boolean focusLostPath = ShaderParamValidation.isRowValid(name, invalid);

        assertFalse(enterPath);
        assertFalse(focusLostPath);
    }

    @Test
    public void rowValidation_missingPair_isRejected() {
        assertFalse(ShaderParamValidation.isRowValid("u_strength", ""));
        assertFalse(ShaderParamValidation.isRowValid("", "1.0"));
    }

    @Test
    public void rowValidation_validPair_passesAndWouldClearInvalidState() {
        assertTrue(ShaderParamValidation.isRowValid("u_strength", "1.25"));
    }
}
