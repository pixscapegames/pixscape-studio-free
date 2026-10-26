package games.pixscape.studio.configuration;

import com.badlogic.gdx.utils.Json;
import org.junit.Test;

import static org.junit.Assert.*;

public class LightingSelectionTest {
    @Test public void missingUsesForwardAndSavedLegacySurvivesRoundTrip() {
        Json json = new Json();
        assertTrue(json.fromJson(EditorSettings.class, "{}").forwardLighting);
        EditorSettings legacy = json.fromJson(EditorSettings.class, "{forwardLighting:false}");
        assertFalse(legacy.forwardLighting);
        json.setUsePrototypes(false);
        assertFalse(json.fromJson(EditorSettings.class, json.toJson(legacy)).forwardLighting);
        assertTrue(json.fromJson(EditorSettings.class, "{forwardLighting:true}").forwardLighting);
    }

    @Test public void explicitProcessSelectionDoesNotMutatePreferences() {
        String old = System.getProperty("pixscape.lighting");
        String poc = System.getProperty("pixscape.poc.forwardLighting");
        try {
            System.clearProperty("pixscape.poc.forwardLighting");
            System.clearProperty("pixscape.lighting");
            EditorSettings settings = new Json().fromJson(EditorSettings.class, "{forwardLighting:false}");
            assertFalse(settings.useForwardLighting());
            System.setProperty("pixscape.lighting", "forward");
            assertTrue(settings.useForwardLighting());
            assertFalse(settings.forwardLighting);
            settings.forwardLighting = true;
            System.setProperty("pixscape.lighting", "legacy");
            assertFalse(settings.useForwardLighting());
            assertTrue(settings.forwardLighting);
            System.setProperty("pixscape.lighting", "invalid");
            assertThrows(IllegalArgumentException.class, settings::useForwardLighting);
            System.clearProperty("pixscape.lighting");
            System.setProperty("pixscape.poc.forwardLighting", "false");
            assertFalse(settings.useForwardLighting());
        } finally {
            restore("pixscape.lighting", old);
            restore("pixscape.poc.forwardLighting", poc);
        }
    }
    private static void restore(String name, String value) {
        if(value == null) System.clearProperty(name); else System.setProperty(name,value);
    }
}
