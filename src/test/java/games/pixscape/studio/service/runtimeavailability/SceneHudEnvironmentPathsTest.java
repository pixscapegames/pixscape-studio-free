package games.pixscape.studio.service.runtimeavailability;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SceneHudEnvironmentPathsTest {
    @Test
    public void defaultProfileNameIsAnOrdinaryValidSceneTag() {
        assertEquals("default", SceneHudEnvironmentPaths.sceneTag("default"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void windowsReservedSceneTagIsRejected() {
        SceneHudEnvironmentPaths.sceneTag("CON");
    }
}
