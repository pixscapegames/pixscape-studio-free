package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Group;
import games.pixscape.studio.service.hud.HudEditorSession;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class HudCanvasInputHostCoverageTest {
    @Test public void followsTheResolvedHudViewportBeyondItsLayoutParent() {
        Group layoutParent = new Group();
        layoutParent.setBounds(100f, 80f, 300f, 400f);
        HudCanvasInputHost host = new HudCanvasInputHost(new HudEditorSession());
        host.activateHudInput();
        layoutParent.addActor(host);

        host.coverStageBounds(new Rectangle(140f, 100f, 620f, 360f));

        assertSame(host, layoutParent.hit(659f, 200f, true));
    }
}
