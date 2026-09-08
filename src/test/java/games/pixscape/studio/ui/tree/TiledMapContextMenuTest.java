package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TiledMapContextMenuTest {
    @BeforeClass
    public static void loadVisUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void deleteMapMenuItemDispatchesOnlyTheMapDeletionAction() {
        int[] deletions = new int[1];
        PopupMenu menu = ItemTreePanel.buildTiledMapContextMenu(() -> deletions[0]++);

        MenuItem deleteMap = find(menu, "Delete map");
        assertNotNull(deleteMap);
        fireClick(deleteMap);

        assertEquals(1, deletions[0]);
    }

    private static MenuItem find(PopupMenu menu, String text) {
        for (Actor child : menu.getChildren()) {
            if (child instanceof MenuItem item && text.contentEquals(item.getText())) {
                return item;
            }
        }
        return null;
    }

    private static void fireClick(MenuItem item) {
        InputEvent event = new InputEvent();
        for (EventListener listener : item.getListeners()) {
            if (listener instanceof ClickListener click) {
                click.clicked(event, 0f, 0f);
            }
        }
    }
}
