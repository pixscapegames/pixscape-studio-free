package games.pixscape.studio.ui.main;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class RulerActorProjectionTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void leavingHudProjectionRestoresSceneCamera() {
        OrthographicCamera sceneCamera = new OrthographicCamera();
        OrthographicCamera hudCamera = new OrthographicCamera();
        RulerActor ruler = new RulerActor(
                RulerActor.Orientation.TOP, sceneCamera, null, null, null);

        ruler.setProjection(hudCamera, new ScreenViewport());
        assertSame(hudCamera, ruler.activeCamera());

        ruler.clearProjection();
        assertSame(sceneCamera, ruler.activeCamera());
    }
}
