package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import games.pixscape.studio.service.hud.HudEditorSession;

/** HUD-node actions shared by the canvas and hierarchy context menus. */
final class HudNodeContextMenu {
    private HudNodeContextMenu() {
    }

    static void show(Stage stage, float stageX, float stageY,
                     HudEditorSession session, String nodeId) {
        if (stage == null || !session.canDeleteNode(nodeId)) return;
        PopupMenu menu = new PopupMenu();
        MenuItem delete = new MenuItem("Delete");
        delete.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                session.deleteNode(nodeId);
                event.handle();
            }
        });
        menu.addItem(delete);
        menu.showMenu(stage, stageX, stageY);
    }
}
