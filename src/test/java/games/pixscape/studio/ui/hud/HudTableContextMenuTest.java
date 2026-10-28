package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.*;

public class HudTableContextMenuTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void disabledActionsAndMergeUseOneTransactionWithUndoRedo() throws Exception {
        HudNode root = table(1, 2);
        String first = root.table.rows.get(0).cells.get(0).id;
        String second = root.table.rows.get(0).cells.get(1).id;
        Fixture fixture = fixture(root);
        fixture.session.selectCell(first);
        withStage(stage -> {
            HudTableContextMenu.show(stage, 50, 50, fixture.session, first, false);
            PopupMenu menu = HudTableContextMenu.activeMenu();
            assertTrue(item(menu, "Delete row").isDisabled());
            assertTrue(item(menu, "Merge selected cells").isDisabled());
            fixture.session.selectCellRange(second);
            HudTableContextMenu.closeIfStale(fixture.session);
            assertNull(HudTableContextMenu.activeMenu());

            HudTableContextMenu.show(stage, 50, 50, fixture.session, second, false);
            menu = HudTableContextMenu.activeMenu();
            assertFalse(item(menu, "Merge selected cells").isDisabled());
            click(item(menu, "Merge selected cells"));
            assertEquals(1, fixture.document.editSession().historySize());
            assertEquals(2, HudLayoutAuthoring.cell(fixture.document.document(), first).colspan);
            assertEquals(first, fixture.session.selectedCellId());
            assertTrue(fixture.document.editSession().undo());
            assertNotNull(HudLayoutAuthoring.cell(fixture.document.document(), second));
            assertEquals(List.of(first, second), fixture.session.selectedCellRange());
            assertTrue(fixture.document.editSession().redo());
            assertEquals(2, HudLayoutAuthoring.cell(fixture.document.document(), first).colspan);
        });
    }

    @Test
    public void mergedColumnSubmenuChoosesLogicalColumnWithoutLosingContent() throws Exception {
        HudNode root = table(2, 2);
        HudTableCell merged = root.table.rows.get(0).cells.get(0);
        merged.colspan = 2;
        merged.content = new HudNode("kept", HudNodeKind.GROUP);
        root.table.rows.get(0).cells.remove(1);
        root.table.rows.get(1).cells.get(1).content =
                new HudNode("blocking", HudNodeKind.GROUP);
        Fixture fixture = fixture(root);
        fixture.session.selectCell(merged.id);
        withStage(stage -> {
            HudTableContextMenu.show(stage, 50, 50, fixture.session, merged.id, false);
            PopupMenu columns = item(HudTableContextMenu.activeMenu(), "Delete column").getSubMenu();
            assertNotNull(columns);
            assertFalse(item(columns, "Column 1").isDisabled());
            assertTrue(item(columns, "Column 2").isDisabled());
            click(item(columns, "Column 1"));
            assertEquals(1, fixture.document.document().root.table.columns);
            HudTableCell kept = HudLayoutAuthoring.cell(fixture.document.document(), merged.id);
            assertEquals(1, kept.colspan);
            assertEquals("kept", kept.content.id);
            assertEquals("blocking", fixture.document.document().root.table.rows.get(1)
                    .cells.get(0).content.id);
        });
    }

    @Test
    public void staleSelectionInvalidatesCapturedCommandAndRowMenuOnlyHasRowActions()
            throws Exception {
        HudNode root = table(2, 2);
        String first = root.table.rows.get(0).cells.get(0).id;
        String second = root.table.rows.get(0).cells.get(1).id;
        Fixture fixture = fixture(root);
        fixture.session.selectCell(first);
        withStage(stage -> {
            HudTableContextMenu.show(stage, 50, 50, fixture.session, first, true);
            PopupMenu row = HudTableContextMenu.activeMenu();
            assertNotNull(item(row, "Insert row before"));
            assertNull(find(row, "Insert column before"));
            MenuItem delete = item(row, "Delete row");
            fixture.session.selectCell(second);
            click(delete);
            assertEquals(2, fixture.document.document().root.table.rows.size());
            assertEquals(0, fixture.document.editSession().historySize());
            HudTableContextMenu.closeIfStale(fixture.session);
            assertNull(HudTableContextMenu.activeMenu());
            HudTableContextMenu.show(stage, 50, 50, fixture.session, second, true);
            click(item(HudTableContextMenu.activeMenu(), "Insert row after"));
            assertEquals(3, fixture.document.document().root.table.rows.size());
            assertEquals(1, fixture.document.editSession().historySize());
            assertNotNull(fixture.session.selectedCellId());
            assertTrue(fixture.document.editSession().undo());
            assertEquals(2, fixture.document.document().root.table.rows.size());
            assertEquals(second, fixture.session.selectedCellId());
        });
    }

    @Test
    public void documentSwitchClosesMenuAndCannotMutateCapturedDocument() throws Exception {
        HudNode root = table(2, 2);
        Fixture fixture = fixture(root);
        String cellId = root.table.rows.get(0).cells.get(0).id;
        fixture.session.selectCell(cellId);
        withStage(stage -> {
            HudTableContextMenu.show(stage, 50, 50, fixture.session, cellId, false);
            MenuItem captured = item(HudTableContextMenu.activeMenu(), "Insert row before");
            HudScreenAsset asset = new HudScreenAsset();
            asset.documentId = "hud/other.json";
            HudScreenEditorDocument other = new HudScreenEditorDocument(
                    "hud/other", "Other", asset, new HudDocumentV1(table(1, 1)));
            HudPanelTestSupport.projectDocument(fixture.session, other);
            HudTableContextMenu.closeIfStale(fixture.session);
            assertNull(HudTableContextMenu.activeMenu());
            click(captured);
            assertEquals(2, fixture.document.document().root.table.rows.size());
            assertEquals(0, fixture.document.editSession().historySize());
        });
    }

    private static HudNode table(int rows, int columns) {
        HudNode root = new HudNode("table", HudNodeKind.TABLE);
        root.table = HudLayoutAuthoring.newTableLayout(root, rows, columns, false);
        return root;
    }

    private static Fixture fixture(HudNode root) throws Exception {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/table.json";
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/table", "HUD", asset, new HudDocumentV1(root));
        HudEditorSession session = HudPanelTestSupport.projectedSession(document);
        HudPanelTestSupport.projectHistoryNavigation(session, document);
        return new Fixture(document, session);
    }

    private static MenuItem item(PopupMenu menu, String text) {
        MenuItem found = find(menu, text);
        assertNotNull("Missing menu item " + text, found);
        return found;
    }

    private static MenuItem find(PopupMenu menu, String text) {
        for (var actor : menu.getChildren())
            if (actor instanceof MenuItem item && text.contentEquals(item.getText())) return item;
        return null;
    }

    private static void click(MenuItem item) {
        InputEvent event = new InputEvent();
        for (EventListener listener : item.getListeners())
            if (listener instanceof ClickListener click) click.clicked(event, 0f, 0f);
    }

    private static void withStage(StageAction action) throws Exception {
        Graphics prior = Gdx.graphics;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class}, (proxy, method, args) -> {
                    if (method.getName().contains("Width") || method.getName().contains("Height"))
                        return 400;
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
        Stage stage = new Stage(new ScreenViewport(), batch);
        try {
            stage.getViewport().update(400, 400, true);
            action.run(stage);
        } finally {
            stage.dispose();
            Gdx.graphics = prior;
        }
    }

    private record Fixture(HudScreenEditorDocument document, HudEditorSession session) { }
    @FunctionalInterface private interface StageAction { void run(Stage stage) throws Exception; }
}
