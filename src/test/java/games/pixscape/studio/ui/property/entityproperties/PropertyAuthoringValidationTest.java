package games.pixscape.studio.ui.property.entityproperties;

import org.junit.Assert;
import org.junit.Test;

public class PropertyAuthoringValidationTest {

    @Test
    public void namesPreserveCaseAndRejectEmptyWhitespaceAndDuplicatesAtTheCallerBoundary() {
        Assert.assertEquals("Health", PropertyAuthoringValidation.requireName("Health", "Health"));
        Assert.assertEquals("health", PropertyAuthoringValidation.requireName("health", "health"));
        assertInvalid(() -> PropertyAuthoringValidation.requireName("", "name"));
        assertInvalid(() -> PropertyAuthoringValidation.requireName(" \t", "name"));
    }

    @Test
    public void integerValidationAcceptsOnlySignedThirtyTwoBitValues() {
        Assert.assertEquals(0, PropertyAuthoringValidation.parseInteger("0", "health"));
        Assert.assertEquals(-12, PropertyAuthoringValidation.parseInteger("-12", "health"));
        Assert.assertEquals(Integer.MIN_VALUE,
                PropertyAuthoringValidation.parseInteger("-2147483648", "health"));
        Assert.assertEquals(Integer.MAX_VALUE,
                PropertyAuthoringValidation.parseInteger("2147483647", "health"));
        assertInvalid(() -> PropertyAuthoringValidation.parseInteger("", "health"));
        assertInvalid(() -> PropertyAuthoringValidation.parseInteger("12.5", "health"));
        assertInvalid(() -> PropertyAuthoringValidation.parseInteger("abc", "health"));
        assertInvalid(() -> PropertyAuthoringValidation.parseInteger("2147483648", "health"));
        assertInvalid(() -> PropertyAuthoringValidation.parseInteger("-2147483649", "health"));
    }

    @Test
    public void floatValidationAcceptsFiniteValuesAndRejectsNonFiniteOrMalformedText() {
        Assert.assertEquals(0f, PropertyAuthoringValidation.parseFloat("0", "speed"), 0f);
        Assert.assertEquals(-1.25f, PropertyAuthoringValidation.parseFloat("-1.25", "speed"), 0f);
        Assert.assertEquals(1000f, PropertyAuthoringValidation.parseFloat("1e3", "speed"), 0f);
        assertInvalid(() -> PropertyAuthoringValidation.parseFloat("", "speed"));
        assertInvalid(() -> PropertyAuthoringValidation.parseFloat("oops", "speed"));
        assertInvalid(() -> PropertyAuthoringValidation.parseFloat("NaN", "speed"));
        assertInvalid(() -> PropertyAuthoringValidation.parseFloat("Infinity", "speed"));
        assertInvalid(() -> PropertyAuthoringValidation.parseFloat("-Infinity", "speed"));
        assertInvalid(() -> PropertyAuthoringValidation.parseFloat("3.5e39", "speed"));
    }

    @Test
    public void classNamesMustBeNonBlankWithoutCanonicalizingValidValues() {
        Assert.assertEquals(" Attack ", PropertyAuthoringValidation.requireClassName(" Attack ", "attack"));
        assertInvalid(() -> PropertyAuthoringValidation.requireClassName("", "attack"));
        assertInvalid(() -> PropertyAuthoringValidation.requireClassName("  ", "attack"));
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected validation to reject the value.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().startsWith("Property '"));
        }
    }
}
