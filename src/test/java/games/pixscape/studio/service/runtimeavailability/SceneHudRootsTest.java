package games.pixscape.studio.service.runtimeavailability;

import games.pixscape.studio.configuration.SceneMeta;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SceneHudRootsTest {

    @Test
    public void nullSceneHasNoHudRoots() {
        assertTrue(SceneHudRoots.collect(null).isEmpty());
    }

    @Test
    public void nullOrBlankDefaultHudHasNoRoots() {
        SceneMeta scene = new SceneMeta("Main", "scene1.json");

        assertTrue(SceneHudRoots.collect(scene).isEmpty());

        scene.defaultHudScreenId = "  ";
        assertTrue(SceneHudRoots.collect(scene).isEmpty());
    }

    @Test
    public void directHudIsReturnedFirstAsItsCanonicalLogicalId() {
        SceneMeta scene = new SceneMeta("Main", "scene1.json");
        scene.defaultHudScreenId = "  hud\\status  ";

        assertEquals(List.of("hud/status"), SceneHudRoots.collect(scene));
        assertEquals(SceneHudRoots.collect(scene), SceneHudRoots.collect(scene));
    }

    @Test
    public void collectorDoesNotMutateMetadataAndReturnsImmutableRoots() {
        SceneMeta scene = new SceneMeta("Main", "scene1.json");
        scene.defaultHudScreenId = "status";

        List<String> roots = SceneHudRoots.collect(scene);

        assertEquals("status", scene.defaultHudScreenId);
        assertThrows(UnsupportedOperationException.class, () -> roots.add("hud/other"));
        scene.defaultHudScreenId = null;
        assertEquals(List.of("hud/status"), roots);
    }

    @Test
    public void malformedNonBlankHudIdIsRejectedByCanonicalPolicy() {
        SceneMeta scene = new SceneMeta("Main", "scene1.json");
        scene.defaultHudScreenId = "hud/nested/status";

        assertThrows(IllegalArgumentException.class, () -> SceneHudRoots.collect(scene));
    }
}
