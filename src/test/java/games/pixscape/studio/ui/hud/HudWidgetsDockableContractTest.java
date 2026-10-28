package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.kotcrab.vis.ui.widget.VisImageButton;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.ui.docking.DockablePanel;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HudWidgetsDockableContractTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void widgetsHasTheTwoStandardDockablePanelHeaderControls() {
        HudWidgetsPanel widgets = new HudWidgetsPanel(
                new HudEditorSession(), new EditorDocumentManager(), Runnable::run);

        assertTrue(widgets instanceof DockablePanel);
        assertTrue(widgets.getChildren().first() instanceof Group);
        assertEquals(2, count(widgets.getChildren().first(), VisImageButton.class));
    }

    private static int count(Actor actor, Class<? extends Actor> type) {
        int result = type.isInstance(actor) ? 1 : 0;
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) result += count(child, type);
        }
        return result;
    }
}
