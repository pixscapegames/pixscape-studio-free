package games.pixscape.studio.configuration;

import com.badlogic.gdx.utils.Json;
import org.junit.Test;

import static org.junit.Assert.*;

public class LightingSelectionTest {
    @Test public void missingUsesLegacyAndSceneChoiceSurvivesRoundTrip() {
        Json json=new Json();json.setUsePrototypes(false);
        assertFalse(json.fromJson(SceneMeta.class,"{}").forwardLighting);
        SceneMeta forward=json.fromJson(SceneMeta.class,"{forwardLighting:true}");
        assertTrue(json.fromJson(SceneMeta.class,json.toJson(forward)).forwardLighting);
        assertFalse(json.fromJson(SceneMeta.class,"{forwardLighting:false}").forwardLighting);
        assertFalse(json.toJson(EditorSettings.get()).contains("forwardLighting"));
    }
    @Test public void explicitProcessSelectionDoesNotMutateScene() {
        String old=System.getProperty("pixscape.lighting");SceneMeta scene=new SceneMeta();
        try {
            System.clearProperty("pixscape.lighting");assertNull(EditorSettings.forwardLightingOverride());
            System.setProperty("pixscape.lighting","forward");assertTrue(EditorSettings.forwardLightingOverride());assertFalse(scene.forwardLighting);
            System.setProperty("pixscape.lighting","legacy");assertFalse(EditorSettings.forwardLightingOverride());
            System.setProperty("pixscape.lighting","invalid");assertThrows(IllegalArgumentException.class,EditorSettings::forwardLightingOverride);
        }finally{restore("pixscape.lighting",old);}
    }
    private static void restore(String name, String value) {
        if(value == null) System.clearProperty(name); else System.setProperty(name,value);
    }
}
