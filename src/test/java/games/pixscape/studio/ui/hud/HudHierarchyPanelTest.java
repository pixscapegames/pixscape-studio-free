package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.MenuItem;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudContainerData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.runtime.hud.document.HudDialogResultButton;
import games.pixscape.runtime.hud.document.HudTextButtonData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import games.pixscape.studio.service.hud.HudOverlayGeometry;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HudHierarchyPanelTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void projectsRealParentChildTreeAndStringIdIndex() throws Exception {
        Fixture fixture = fixture(structuredDocument());
        HudVisTree tree = fixture.panel().tree();
        HudTreeNode root = tree.findNode("root");
        HudTreeNode group = tree.findNode("group-1");
        HudTreeNode table = tree.findNode("table-1");
        HudTreeNode stack = tree.findNode("stack-1");
        HudTreeNode container = tree.findNode("container-1");

        assertSame(root, tree.getRootNodes().first());
        assertSame(group, root.getChildren().get(0));
        assertSame(container, root.getChildren().get(1));
        assertSame(table, group.getChildren().first());
        HudTreeNode row = (HudTreeNode) table.getChildren().first();
        HudTreeNode cell = (HudTreeNode) row.getChildren().first();
        assertEquals(HudTreeNode.Type.ROW, row.type());
        assertEquals(HudTreeNode.Type.CELL, cell.type());
        assertSame(stack, cell.getChildren().first());
        assertNull(tree.findNode("missing"));
        assertEquals(HudNodeKind.STACK, stack.kind());
    }

    @Test
    public void nativeSelectionIsExclusiveAndRoutesBothDirections() throws Exception {
        Fixture fixture = fixture(structuredDocument());
        HudVisTree tree = fixture.panel().tree();
        HudTreeNode root = tree.findNode("root");
        HudTreeNode child = tree.findNode("container-1");

        tree.getSelection().set(root);
        assertEquals("root", fixture.session().selectedNodeId());
        tree.getSelection().set(child);
        assertEquals("container-1", fixture.session().selectedNodeId());
        assertSame(child, tree.getSelectedNode());
        assertEquals(1, tree.getSelection().size());
        assertFalse(tree.getSelection().contains(root));

        AtomicInteger modelNotifications = new AtomicInteger();
        fixture.session().addListener(modelNotifications::incrementAndGet);
        fixture.session().selectNode("stack-1");
        assertEquals(1, modelNotifications.get());
        fixture.deferred().runAll();
        assertEquals(1, modelNotifications.get());
        assertSame(fixture.panel().tree().findNode("stack-1"),
                fixture.panel().tree().getSelectedNode());
        assertEquals(1, fixture.panel().tree().getSelection().size());
    }

    @Test
    public void projectsEmptyMergedAndOccupiedCellsForTableWindowAndDialog() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode table = new HudNode("table", HudNodeKind.TABLE);
        table.table = HudLayoutAuthoring.newTableLayout(root, 2, 3, false);
        String empty = table.table.rows.get(1).cells.get(0).id;
        var merged = table.table.rows.get(0).cells.get(1);
        merged.colspan = 2;
        merged.content = new HudNode("content", HudNodeKind.GROUP);
        table.table.rows.get(0).cells.remove(2);
        root.children.add(HudChild.free(table, new HudFreePlacement()));
        HudNode window = new HudNode("window", HudNodeKind.WINDOW);
        window.window = new HudWindowData();
        window.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        root.children.add(HudChild.free(window, new HudFreePlacement()));
        HudNode dialog = new HudNode("dialog", HudNodeKind.DIALOG);
        dialog.dialog = new HudDialogData();
        dialog.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        HudNode result = new HudNode("result", HudNodeKind.TEXT_BUTTON);
        result.textButton = new HudTextButtonData();
        result.textButton.text = "OK";
        dialog.dialog.resultButtons.add(new HudDialogResultButton(result, "ok", true));
        root.children.add(HudChild.free(dialog, new HudFreePlacement()));
        Fixture fixture = fixture(new HudDocumentV1(root));
        HudVisTree tree = fixture.panel().tree();

        HudTreeNode tableNode = tree.findNode("table");
        assertEquals(2, tableNode.getChildren().size);
        HudTreeNode mergedNode = tree.findCell(merged.id);
        assertEquals("Cell 2–3", mergedNode.label().getText().toString());
        assertSame(tree.findNode("content"), mergedNode.getChildren().first());
        assertEquals("Cell 1 — empty", tree.findCell(empty).label().getText().toString());
        assertEquals(HudTreeNode.Type.ROW,
                ((HudTreeNode) tree.findNode("window").getChildren().first()).type());
        HudTreeNode dialogNode = tree.findNode("dialog");
        assertEquals(HudTreeNode.Type.RESULTS,
                ((HudTreeNode) dialogNode.getChildren().first()).type());
        HudTreeNode results = (HudTreeNode) dialogNode.getChildren().first();
        assertSame(tree.findNode("result"), results.getChildren().first());
        assertEquals(HudTreeNode.Type.ROW,
                ((HudTreeNode) dialogNode.getChildren().get(1)).type());

        tree.getSelection().set(tree.findCell(empty));
        assertEquals(empty, fixture.session().selectedCellId());
        assertNull(fixture.session().selectedNodeId());
        assertFalse(fixture.session().deleteSelectedNode());
        tree.getSelection().set(tree.findNode("content"));
        assertEquals("content", fixture.session().selectedNodeId());
        assertNull(fixture.session().selectedCellId());
    }

    @Test
    public void shiftClickInItemsSelectsCellRangeWithoutAnEdit() throws Exception {
        HudNode root = new HudNode("table", HudNodeKind.TABLE);
        root.table = HudLayoutAuthoring.newTableLayout(root, 1, 2, false);
        String first = root.table.rows.get(0).cells.get(0).id;
        String second = root.table.rows.get(0).cells.get(1).id;
        Fixture fixture = fixture(new HudDocumentV1(root));
        fixture.session().selectCell(first);
        fixture.deferred().runAll();
        Graphics previousGraphics = Gdx.graphics;
        Input previousInput = Gdx.input;
        Gdx.graphics = logicalGraphics(400, 400);
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed"))
                        return (int) args[0] == Input.Keys.SHIFT_LEFT;
                    if (method.getReturnType() == long.class) return 0L;
                    return primitiveDefault(method.getReturnType());
                });
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(400, 400, true);
            stage.addActor(fixture.panel());
            layout(fixture.panel(), 400, 400);
            HudTreeNode target = fixture.panel().tree().findCell(second);
            Vector2 point = target.getActor().localToStageCoordinates(new Vector2(30f, 10f));
            stage.touchDown(Math.round(point.x), Math.round(400f - point.y),
                    0, Input.Buttons.LEFT);
            stage.touchUp(Math.round(point.x), Math.round(400f - point.y),
                    0, Input.Buttons.LEFT);
            assertEquals(List.of(first, second), fixture.session().selectedCellRange());
            assertEquals(0, fixture.document().editSession().historySize());
            stage.touchDown(Math.round(point.x), Math.round(400f - point.y),
                    0, Input.Buttons.RIGHT);
            stage.touchUp(Math.round(point.x), Math.round(400f - point.y),
                    0, Input.Buttons.RIGHT);
            assertEquals(List.of(first, second), fixture.session().selectedCellRange());
            assertNotNull(HudTableContextMenu.activeMenu());
        } finally {
            fixture.session().selectNode("table");
            HudTableContextMenu.closeIfStale(fixture.session());
            stage.dispose();
            Gdx.input = previousInput;
            Gdx.graphics = previousGraphics;
        }
    }

    @Test
    public void stageShiftSelectionPreservesExpandedTreeAndScroll() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode table = new HudNode("table", HudNodeKind.TABLE);
        table.table = HudLayoutAuthoring.newTableLayout(root, 2, 4, false);
        table.table.rows.get(0).cells.get(2).content = new HudNode("child", HudNodeKind.GROUP);
        table.table.rows.get(0).cells.get(2).colspan = 2;
        table.table.rows.get(0).cells.remove(3);
        root.children.add(HudChild.free(table, new HudFreePlacement()));
        String first = table.table.rows.get(0).cells.get(0).id;
        String second = table.table.rows.get(0).cells.get(1).id;
        String third = table.table.rows.get(0).cells.get(2).id;
        String nextRow = table.table.rows.get(1).cells.get(0).id;
        Fixture fixture = fixture(new HudDocumentV1(root));
        HudPanelTestSupport.projectHistoryNavigation(fixture.session(), fixture.document());
        Graphics previousGraphics = Gdx.graphics;
        Input previousInput = Gdx.input;
        AtomicBoolean shift = new AtomicBoolean();
        Gdx.graphics = logicalGraphics(400, 240);
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed"))
                        return shift.get() && (int) args[0] == Input.Keys.SHIFT_LEFT;
                    if (method.getReturnType() == long.class) return 0L;
                    return primitiveDefault(method.getReturnType());
                });
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(400, 240, true);
            stage.addActor(fixture.panel());
            layout(fixture.panel(), 190f, 130f);
            stage.getRoot().addCaptureListener(new InputListener() {
                @Override public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.SHIFT_LEFT) shift.set(true);
                    return false;
                }
                @Override public boolean keyUp(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.SHIFT_LEFT) shift.set(false);
                    return false;
                }
            });
            click(stage, fixture.panel().tree().findCell(first), 12f, Input.Buttons.LEFT, 240);
            fixture.deferred().runAll();
            layout(fixture.panel(), 190f, 130f);
            assertEquals(first, fixture.session().selectedCellId());
            assertTrue(fixture.panel().scroller().getMaxY() > 0f);
            fixture.panel().scroller().setScrollY(8f);
            fixture.panel().scroller().updateVisualScroll();
            float scrollBefore = fixture.panel().scroller().getScrollY();
            int projectionBefore = fixture.panel().projectionCount();
            assertTrue(fixture.panel().tree().findNode("root").isExpanded());
            assertTrue(fixture.panel().tree().findNode("table").isExpanded());

            stage.keyDown(Input.Keys.SHIFT_LEFT);
            fixture.deferred().runAll();
            assertTrue(shift.get());
            assertEquals(List.of(first), fixture.session().selectedCellRange());
            assertEquals(projectionBefore, fixture.panel().projectionCount());
            assertTrue(fixture.panel().tree().findNode("table").isExpanded());

            click(stage, fixture.panel().tree().findCell(second), 12f, Input.Buttons.LEFT, 240);
            assertEquals(List.of(first, second), fixture.session().selectedCellRange());
            fixture.deferred().runAll();
            assertEquals(2, fixture.panel().tree().getSelection().size());
            assertTrue(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findCell(first)));
            assertTrue(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findCell(second)));
            assertFalse(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findNode("root")));
            assertFalse(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findNode("table")));
            assertFalse(fixture.panel().tree().getSelection().contains(
                    (HudTreeNode) fixture.panel().tree().findNode("table").getChildren().first()));
            assertFalse(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findNode("child")));
            assertTrue(fixture.panel().tree().findNode("root").isExpanded());
            assertTrue(fixture.panel().tree().findNode("table").isExpanded());
            assertEquals(scrollBefore, fixture.panel().scroller().getScrollY(), 0.01f);
            assertEquals(projectionBefore, fixture.panel().projectionCount());

            HudTreeNode arrowCell = fixture.panel().tree().findCell(third);
            assertTrue(arrowCell.isExpanded());
            click(stage, arrowCell, -8f, Input.Buttons.LEFT, 240);
            fixture.deferred().runAll();
            assertFalse(fixture.panel().tree().findCell(third).isExpanded());
            assertEquals(List.of(first, second), fixture.session().selectedCellRange());
            fixture.panel().tree().validate();
            fixture.panel().scroller().layout();
            click(stage, fixture.panel().tree().findCell(third), -8f, Input.Buttons.LEFT, 240);
            assertTrue(fixture.panel().tree().findCell(third).isExpanded());
            click(stage, fixture.panel().tree().findCell(third), 12f, Input.Buttons.LEFT, 240);
            assertEquals(List.of(first, second, third), fixture.session().selectedCellRange());
            fixture.deferred().runAll();
            assertEquals(3, fixture.panel().tree().getSelection().size());
            assertTrue(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findCell(third)));
            assertFalse(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findNode("child")));
            click(stage, fixture.panel().tree().findCell(second), 12f, Input.Buttons.LEFT, 240);
            assertEquals(List.of(first, second), fixture.session().selectedCellRange());
            fixture.deferred().runAll();
            assertEquals(2, fixture.panel().tree().getSelection().size());
            stage.keyUp(Input.Keys.SHIFT_LEFT);
            assertFalse(shift.get());

            click(stage, fixture.panel().tree().findCell(second), 12f, Input.Buttons.RIGHT, 240);
            assertEquals(List.of(first, second), fixture.session().selectedCellRange());
            MenuItem merge = menuItem(HudTableContextMenu.activeMenu(), "Merge selected cells");
            assertFalse(merge.isDisabled());
            click(stage, merge, 240);
            fixture.deferred().runAll();
            assertEquals(2, HudLayoutAuthoring.cell(fixture.document().document(), first).colspan);
            assertEquals(List.of(first), fixture.session().selectedCellRange());
            assertEquals(1, fixture.panel().tree().getSelection().size());
            assertTrue(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findCell(first)));
            assertTrue(fixture.panel().tree().findNode("root").isExpanded());
            assertTrue(fixture.panel().tree().findNode("table").isExpanded());
            assertTrue(fixture.panel().tree().findCell(third).isExpanded());
            assertTrue(fixture.document().editSession().undo());
            fixture.deferred().runAll();
            assertEquals(List.of(first, second), fixture.session().selectedCellRange());
            assertEquals(2, fixture.panel().tree().getSelection().size());
            assertTrue(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findCell(second)));
            assertTrue(fixture.panel().tree().findNode("table").isExpanded());
            assertTrue(fixture.panel().tree().findCell(third).isExpanded());
            assertTrue(fixture.document().editSession().redo());
            fixture.deferred().runAll();
            assertEquals(List.of(first), fixture.session().selectedCellRange());
            assertEquals(1, fixture.panel().tree().getSelection().size());
            assertTrue(fixture.document().editSession().undo());
            fixture.deferred().runAll();
            layout(fixture.panel(), 190f, 220f);
            fixture.panel().scroller().setScrollY(0f);
            fixture.panel().scroller().updateVisualScroll();
            click(stage, fixture.panel().tree().findCell(second), 12f, Input.Buttons.LEFT, 240);
            fixture.deferred().runAll();
            assertEquals(List.of(second), fixture.session().selectedCellRange());
            assertEquals(1, fixture.panel().tree().getSelection().size());
            assertTrue(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findCell(second)));

            click(stage, fixture.panel().tree().findCell(nextRow), 12f, Input.Buttons.LEFT, 240);
            assertEquals(List.of(nextRow), fixture.session().selectedCellRange());
            fixture.deferred().runAll();
            assertEquals(1, fixture.panel().tree().getSelection().size());
            assertTrue(fixture.panel().tree().getSelection().contains(
                    fixture.panel().tree().findCell(nextRow)));
        } finally {
            stage.dispose();
            Gdx.input = previousInput;
            Gdx.graphics = previousGraphics;
        }
    }

    @Test
    public void shiftClickInAnotherTableStartsSimpleSelection() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode firstTable = new HudNode("first-table", HudNodeKind.TABLE);
        firstTable.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        root.children.add(HudChild.free(firstTable, new HudFreePlacement()));
        HudNode secondTable = new HudNode("second-table", HudNodeKind.TABLE);
        secondTable.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        root.children.add(HudChild.free(secondTable, new HudFreePlacement()));
        String first = firstTable.table.rows.get(0).cells.get(0).id;
        String second = secondTable.table.rows.get(0).cells.get(0).id;
        Fixture fixture = fixture(new HudDocumentV1(root));
        Graphics previousGraphics = Gdx.graphics;
        Input previousInput = Gdx.input;
        AtomicBoolean shift = new AtomicBoolean();
        Gdx.graphics = logicalGraphics(400, 400);
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed"))
                        return shift.get() && (int) args[0] == Input.Keys.SHIFT_LEFT;
                    if (method.getReturnType() == long.class) return 0L;
                    return primitiveDefault(method.getReturnType());
                });
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(400, 400, true);
            stage.addActor(fixture.panel());
            layout(fixture.panel(), 300f, 350f);
            stage.getRoot().addCaptureListener(new InputListener() {
                @Override public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.SHIFT_LEFT) shift.set(true);
                    return false;
                }
            });
            click(stage, fixture.panel().tree().findCell(first), 12f, Input.Buttons.LEFT, 400);
            fixture.deferred().runAll();
            stage.keyDown(Input.Keys.SHIFT_LEFT);
            click(stage, fixture.panel().tree().findCell(second), 12f, Input.Buttons.LEFT, 400);
            assertEquals(List.of(second), fixture.session().selectedCellRange());
            fixture.deferred().runAll();
            assertTrue(fixture.panel().tree().findNode("first-table").isExpanded());
            assertTrue(fixture.panel().tree().findNode("second-table").isExpanded());
        } finally {
            stage.dispose();
            Gdx.input = previousInput;
            Gdx.graphics = previousGraphics;
        }
    }

    @Test
    public void canvasShiftRangeProjectsEveryCellIntoItems() throws Exception {
        HudNode table = new HudNode("table", HudNodeKind.TABLE);
        table.actor.width = 100f;
        table.actor.height = 100f;
        table.table = HudLayoutAuthoring.newTableLayout(table, 1, 2, false);
        for (var cell : table.table.rows.get(0).cells) {
            cell.constraints.minWidth = 40f;
            cell.constraints.minHeight = 40f;
        }
        String first = table.table.rows.get(0).cells.get(0).id;
        String second = table.table.rows.get(0).cells.get(1).id;
        HudScreenAsset asset = asset("hud/canvas");
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument document = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/canvas", "Canvas HUD", asset, new HudDocumentV1(table)));
        Graphics previousGraphics = Gdx.graphics;
        Input previousInput = Gdx.input;
        Gdx.graphics = logicalGraphics(100, 100);
        AtomicBoolean shift = new AtomicBoolean();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                new Class<?>[]{Input.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed"))
                        return shift.get() && (int) args[0] == Input.Keys.SHIFT_LEFT;
                    if (method.getReturnType() == long.class) return 0L;
                    return primitiveDefault(method.getReturnType());
                });
        HudEditorSession session = new HudEditorSession(AssetMetaDatabase::new,
                HudHierarchyPanelTest::inertBatch);
        Stage stage = null;
        try {
            session.open(Gdx.files.absolute(temporary.getRoot().getAbsolutePath()), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            assertTrue((Boolean) configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f)));
            HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
            HudHierarchyPanel hierarchy = new HudHierarchyPanel(session, manager, deferred::post);
            stage = new Stage(new ScreenViewport(), inertBatch());
            stage.getViewport().update(100, 100, true);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);

            session.selectCell(first);
            deferred.runAll();
            Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(
                    session.materializedHud().cell(second),
                    session.materializedHud().actor("table"), new Rectangle());
            Vector2 point = session.materializedHud().actor("table").localToStageCoordinates(
                    new Vector2(bounds.x + bounds.width * 0.5f,
                            bounds.y + bounds.height * 0.5f));
            shift.set(true);
            stage.touchDown(Math.round(point.x), Math.round(100f - point.y),
                    0, Input.Buttons.LEFT);
            stage.touchUp(Math.round(point.x), Math.round(100f - point.y),
                    0, Input.Buttons.LEFT);
            assertEquals(List.of(first, second), session.selectedCellRange());
            deferred.runAll();
            assertEquals(2, hierarchy.tree().getSelection().size());
            assertTrue(hierarchy.tree().getSelection().contains(hierarchy.tree().findCell(first)));
            assertTrue(hierarchy.tree().getSelection().contains(hierarchy.tree().findCell(second)));
            session.selectCell(second);
            deferred.runAll();
            Rectangle firstBounds = HudOverlayGeometry.visibleCellBoundsInOverlay(
                    session.materializedHud().cell(first),
                    session.materializedHud().actor("table"), new Rectangle());
            Vector2 firstPoint = session.materializedHud().actor("table").localToStageCoordinates(
                    new Vector2(firstBounds.x + firstBounds.width * 0.5f,
                            firstBounds.y + firstBounds.height * 0.5f));
            stage.touchDown(Math.round(firstPoint.x), Math.round(100f - firstPoint.y),
                    0, Input.Buttons.LEFT);
            stage.touchUp(Math.round(firstPoint.x), Math.round(100f - firstPoint.y),
                    0, Input.Buttons.LEFT);
            deferred.runAll();
            assertEquals(second, session.selectedCellId());
            assertEquals(List.of(first, second), session.selectedCellRange());
            assertSame(hierarchy.tree().findCell(second), hierarchy.tree().getSelectedNode());
            assertEquals(2, hierarchy.tree().getSelection().size());
        } finally {
            if (stage != null) stage.dispose();
            session.dispose();
            Gdx.input = previousInput;
            Gdx.graphics = previousGraphics;
        }
    }

    private static void click(Stage stage, HudTreeNode node, float localX, int button,
                              int stageHeight) {
        Vector2 point = node.getActor().localToStageCoordinates(
                new Vector2(localX, node.getActor().getHeight() * 0.5f));
        int x = Math.round(point.x), y = Math.round(stageHeight - point.y);
        stage.touchDown(x, y, 0, button);
        stage.touchUp(x, y, 0, button);
    }

    private static MenuItem menuItem(com.kotcrab.vis.ui.widget.PopupMenu menu, String text) {
        for (Actor actor : menu.getChildren())
            if (actor instanceof MenuItem item && text.contentEquals(item.getText())) return item;
        throw new AssertionError("Missing menu item: " + text);
    }

    private static void click(Stage stage, Actor actor, int stageHeight) {
        Vector2 point = actor.localToStageCoordinates(
                new Vector2(actor.getWidth() * 0.5f, actor.getHeight() * 0.5f));
        int x = Math.round(point.x), y = Math.round(stageHeight - point.y);
        stage.touchDown(x, y, 0, Input.Buttons.LEFT);
        stage.touchUp(x, y, 0, Input.Buttons.LEFT);
    }

    @Test
    public void treeSelectionThenDeleteUsesTheSelectedChildAndDoesNotCascadeToItsParent()
            throws Exception {
        Fixture fixture = fixture(structuredDocument());
        fixture.session().selectNode("table-1"); // Parent A was selected on the canvas.
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(200, 200);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        AtomicInteger deleteCalls = new AtomicInteger();
        try {
            stage.getViewport().update(200, 200, true);
            fixture.panel().setBounds(0f, 0f, 200f, 200f);
            stage.addActor(fixture.panel());
            fixture.panel().validate();
            fixture.panel().tree().validate();
            fixture.panel().scroller().layout();
            stage.getRoot().addCaptureListener(new InputListener() {
                @Override public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode != Input.Keys.DEL || !fixture.panel().ownsKeyboardFocus(
                            event.getStage().getKeyboardFocus())) return false;
                    if (!fixture.session().deleteSelectedNode()) return false;
                    deleteCalls.incrementAndGet();
                    event.stop();
                    return true;
                }
            });

            HudTreeNode child = fixture.panel().tree().findNode("stack-1");
            Vector2 point = child.getActor().localToStageCoordinates(new Vector2(
                    child.getActor().getWidth() * 0.5f,
                    child.getActor().getHeight() * 0.5f));
            stage.touchDown(Math.round(point.x), Math.round(200f - point.y),
                    0, Input.Buttons.LEFT);
            stage.touchUp(Math.round(point.x), Math.round(200f - point.y),
                    0, Input.Buttons.LEFT);
            assertEquals("stack-1", fixture.session().selectedNodeId());
            assertSame(fixture.panel().tree(), stage.getKeyboardFocus());
            assertTrue(fixture.panel().ownsKeyboardFocus(stage.getKeyboardFocus()));

            assertTrue(stage.keyDown(Input.Keys.DEL));
            stage.keyUp(Input.Keys.DEL);
            assertEquals(1, deleteCalls.get());
            assertNull(find(fixture.document().document().root, "stack-1"));
            assertTrue(find(fixture.document().document().root, "table-1") != null);
            assertEquals("table-1", fixture.session().selectedNodeId());

            assertTrue(fixture.document().editSession().undo());
            assertTrue(find(fixture.document().document().root, "stack-1") != null);

            HudNodeContextMenu.show(stage, 100f, 100f, fixture.session(), "stack-1");
            MenuItem contextDelete = findActor(stage.getRoot(), MenuItem.class);
            assertNotNull(contextDelete);
            Vector2 deletePoint = contextDelete.localToStageCoordinates(new Vector2(
                    contextDelete.getWidth() * 0.5f, contextDelete.getHeight() * 0.5f));
            stage.touchDown(Math.round(deletePoint.x), Math.round(200f - deletePoint.y),
                    0, Input.Buttons.LEFT);
            stage.touchUp(Math.round(deletePoint.x), Math.round(200f - deletePoint.y),
                    0, Input.Buttons.LEFT);
            assertNull(find(fixture.document().document().root, "stack-1"));
            assertTrue(find(fixture.document().document().root, "table-1") != null);
        } finally {
            stage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test
    public void collapseSurvivesRefreshAndModelSelectionExpandsAncestors() throws Exception {
        Fixture fixture = fixture(structuredDocument());
        HudTreeNode group = fixture.panel().tree().findNode("group-1");
        group.setExpanded(false);

        fixture.panel().requestRefresh();
        fixture.deferred().runAll();
        group = fixture.panel().tree().findNode("group-1");
        assertFalse(group.isExpanded());

        fixture.session().selectNode("stack-1");
        fixture.deferred().runAll();
        assertTrue(fixture.panel().tree().findNode("group-1").isExpanded());
        assertTrue(fixture.panel().tree().findNode("table-1").isExpanded());
        assertSame(fixture.panel().tree().findNode("stack-1"),
                fixture.panel().tree().getSelectedNode());
    }

    @Test
    public void authoringSelectsRealChildAndUndoRemovesItWithoutStaleSelection()
            throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        Fixture fixture = fixture(new HudDocumentV1(root));
        fixture.session().selectNode("root");
        fixture.deferred().runAll();
        HudWidgetsPanel widgets = new HudWidgetsPanel(
                fixture.session(), fixture.manager(), fixture.deferred()::post);

        assertTrue(fixture.session().createSelectedTable(1, 1, fixture.document(), "root"));
        fixture.deferred().runAll();
        HudTreeNode created = fixture.panel().tree().findNode("table-1");
        assertTrue(created != null);
        assertEquals("table-1", fixture.session().selectedNodeId());
        assertSame(created, fixture.panel().tree().getSelectedNode());

        assertTrue(fixture.document().editSession().undo());
        fixture.panel().requestRefresh();
        fixture.deferred().runAll();
        assertNull(fixture.panel().tree().findNode("table-1"));
        assertNull(fixture.session().selectedNodeId());
        assertTrue(fixture.panel().tree().getSelection().isEmpty());
    }

    @Test
    public void switchingHudDocumentsDoesNotLeakNodesSelectionOrExpansion() throws Exception {
        Fixture fixture = fixture(structuredDocument());
        fixture.session().selectNode("group-1");
        fixture.deferred().runAll();
        fixture.panel().tree().findNode("group-1").setExpanded(false);

        HudNode otherRoot = new HudNode("other-root", HudNodeKind.GROUP);
        otherRoot.children.add(HudChild.free(
                new HudNode("other-child", HudNodeKind.GROUP), new HudFreePlacement()));
        HudScreenEditorDocument other = new HudScreenEditorDocument(
                "hud/other", "Other HUD", asset("hud/other"), new HudDocumentV1(otherRoot));
        other.setSelectedNodeId("other-child");
        HudPanelTestSupport.projectDocument(fixture.session(), other);
        fixture.manager().openHudScreen(other);
        fixture.deferred().runAll();

        assertNull(fixture.panel().tree().findNode("group-1"));
        assertSame(fixture.panel().tree().findNode("other-child"),
                fixture.panel().tree().getSelectedNode());

        HudPanelTestSupport.projectDocument(fixture.session(), fixture.document());
        fixture.manager().activate(fixture.document().key());
        fixture.deferred().runAll();
        assertNull(fixture.panel().tree().findNode("other-child"));
        assertSame(fixture.panel().tree().findNode("group-1"),
                fixture.panel().tree().getSelectedNode());
        assertFalse(fixture.panel().tree().findNode("group-1").isExpanded());
    }

    @Test
    public void tallHierarchyHasRealVerticalScrollRange() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        for (int i = 1; i <= 40; i++) {
            root.children.add(HudChild.free(
                    new HudNode("group-" + i, HudNodeKind.GROUP), new HudFreePlacement()));
        }
        Fixture fixture = fixture(new HudDocumentV1(root));
        layout(fixture.panel(), 180f, 100f);

        assertTrue(fixture.panel().scroller().getMaxY() > 0f);
        assertFalse(fixture.panel().scroller().isScrollingDisabledY());
    }

    @Test
    public void deepRealTreeHasHorizontalRangeAndSelectionAfterScroll() throws Exception {
        HudNode root = new HudNode("root", HudNodeKind.STACK);
        HudNode parent = root;
        for (int i = 1; i <= 20; i++) {
            HudNode child = new HudNode("deep-stack-" + i, HudNodeKind.STACK);
            parent.children.add(HudChild.direct(child));
            parent = child;
        }
        Fixture fixture = fixture(new HudDocumentV1(root));
        layout(fixture.panel(), 150f, 110f);

        assertTrue(fixture.panel().tree().getPrefWidth() > fixture.panel().scroller().getWidth());
        assertTrue(fixture.panel().scroller().getMaxX() > 0f);
        assertFalse(fixture.panel().scroller().isScrollingDisabledX());
        fixture.panel().scroller().setScrollX(fixture.panel().scroller().getMaxX());
        fixture.panel().scroller().setScrollY(fixture.panel().scroller().getMaxY());
        fixture.panel().tree().getSelection().set(
                fixture.panel().tree().findNode("deep-stack-20"));
        assertEquals("deep-stack-20", fixture.session().selectedNodeId());

        fixture.panel().requestRefresh();
        fixture.deferred().runAll();
        layout(fixture.panel(), 150f, 110f);
        assertTrue(fixture.panel().scroller().getMaxX() > 0f);
        assertTrue(fixture.panel().scroller().getMaxY() > 0f);
    }

    @Test
    public void redundantRefreshesCoalesceWithoutSynchronousTreeMutation() throws Exception {
        Fixture fixture = fixture(new HudDocumentV1(
                new HudNode("root", HudNodeKind.GROUP)));
        int before = fixture.panel().projectionCount();

        fixture.panel().requestRefresh();
        fixture.panel().requestRefresh();

        assertEquals(1, fixture.deferred().size());
        fixture.deferred().runAll();
        assertEquals(before + 1, fixture.panel().projectionCount());
    }

    private static HudDocumentV1 structuredDocument() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode group = new HudNode("group-1", HudNodeKind.GROUP);
        HudNode table = new HudNode("table-1", HudNodeKind.TABLE);
        table.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        HudNode stack = new HudNode("stack-1", HudNodeKind.STACK);
        HudNode container = new HudNode("container-1", HudNodeKind.CONTAINER);
        container.container = new HudContainerData();
        table.table.rows.get(0).cells.get(0).content = stack;
        group.children.add(HudChild.free(table, new HudFreePlacement()));
        root.children.add(HudChild.free(group, new HudFreePlacement()));
        root.children.add(HudChild.free(container, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static Fixture fixture(HudDocumentV1 document) throws Exception {
        EditorDocumentManager manager = new EditorDocumentManager();
        HudScreenEditorDocument hud = manager.openHudScreen(new HudScreenEditorDocument(
                "hud/main", "HUD", asset("hud/main"), document));
        HudEditorSession session = HudPanelTestSupport.projectedSession(hud);
        HudPanelTestSupport.DeferredUi deferred = new HudPanelTestSupport.DeferredUi();
        return new Fixture(manager, hud, session,
                new HudHierarchyPanel(session, manager, deferred::post), deferred);
    }

    private static HudScreenAsset asset(String screenId) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = screenId + ".json";
        return asset;
    }

    private static void layout(HudHierarchyPanel panel, float width, float height) {
        panel.setSize(width, height);
        panel.invalidateHierarchy();
        panel.validate();
        panel.tree().validate();
        panel.scroller().layout();
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
    }

    private static HudNode find(HudNode node, String nodeId) {
        if (node == null) return null;
        if (nodeId.equals(node.id)) return node;
        for (HudChild child : node.children) {
            HudNode found = find(child.node, nodeId);
            if (found != null) return found;
        }
        if (node.table != null) for (var row : node.table.rows)
            for (var cell : row.cells) if (cell.content != null) {
                HudNode found = find(cell.content, nodeId);
                if (found != null) return found;
            }
        return null;
    }

    private static <T extends Actor> T findActor(Actor actor, Class<T> type) {
        if (type.isInstance(actor)) return type.cast(actor);
        if (!(actor instanceof Group group)) return null;
        for (Actor child : group.getChildren()) {
            T found = findActor(child, type);
            if (found != null) return found;
        }
        return null;
    }

    private static Graphics logicalGraphics(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> width;
                    case "getHeight", "getBackBufferHeight" -> height;
                    case "getDeltaTime" -> 1f / 60f;
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        return null;
    }

    private record Fixture(EditorDocumentManager manager, HudScreenEditorDocument document,
                           HudEditorSession session, HudHierarchyPanel panel,
                           HudPanelTestSupport.DeferredUi deferred) {}
}
