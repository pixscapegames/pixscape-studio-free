package games.pixscape.studio.ui.tree;

import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ItemTreeDocumentProjectionTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void documentTypeSelectsSceneItemsHudHierarchyOrNoProjection() {
        VisTable sceneItems = new VisTable();
        VisTable hudHierarchy = new VisTable();

        assertSame(sceneItems, ItemTreePanel.projectionForDocument(
                EditorDocumentType.SCENE, sceneItems, hudHierarchy));
        assertSame(sceneItems, ItemTreePanel.projectionForDocument(
                EditorDocumentType.GAME_OBJECT, sceneItems, hudHierarchy));
        assertSame(hudHierarchy, ItemTreePanel.projectionForDocument(
                EditorDocumentType.HUD_SCREEN, sceneItems, hudHierarchy));
        assertNull(ItemTreePanel.projectionForDocument(null, sceneItems, hudHierarchy));
    }
}
