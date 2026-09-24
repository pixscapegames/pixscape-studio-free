package games.pixscape.studio.service.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.runtime.hud.document.HudScrollPaneData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.ui.hud.HudCanvasInputHost;
import games.pixscape.studio.ui.hud.HudCanvasSelectionInputListener;
import games.pixscape.studio.event.GetScrollListener;
import games.pixscape.studio.event.LoseScroolListener;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HudSelectionOverlayTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private HudEditorSession openSession;
    private static GL20 previousGl;
    private static GL20 previousGl20;

    @BeforeClass public static void bootGdx() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        if (Gdx.gl == null) {
            Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
                    new Class<?>[]{GL20.class}, (proxy, method, args) -> {
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) return false;
                        if (type == int.class) return 0;
                        if (type == float.class) return 0f;
                        return null;
                    });
            Gdx.gl20 = Gdx.gl;
        }
    }

    @AfterClass public static void restoreGl() {
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    @After public void disposeSession() {
        if (openSession != null) openSession.dispose();
    }

    @Test public void selectedRootExposesOnlyItsDirectChildrenAsCanvasTargets() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");

        assertTarget(f.session.selectionTargets(), "child", HudSelectionTarget.Type.ACTOR, false);
        assertNull(target(f.session.selectionTargets(), "root"));
        assertNull(target(f.session.selectionTargets(), "grandchild"));
    }

    @Test public void unselectedDocumentOffersRootChildrenButNeverTheRootAsCanvasTargets() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");

        assertEquals(1, f.session.selectionTargets().size());
        assertTarget(f.session.selectionTargets(), "child", HudSelectionTarget.Type.ACTOR, false);
        assertNull(target(f.session.selectionTargets(), "root"));
        assertTrue(f.session.selectOverlayTargetAt(15f, 15f));
        assertEquals("child", f.session.selectedNodeId());
    }

    @Test public void emptyCellUsesAllocatedCellRegionInsteadOfInnerActor() throws Exception {
        HudDocumentV1 document = tableDocument();
        document.root.table.rows.get(0).cells.get(0).content = null;
        Fixture f = open(document, "hud/table");
        f.session.selectNode("table");

        HudSelectionTarget target = target(f.session.selectionTargets(),
                f.document.document().root.table.rows.get(0).cells.get(0).id);
        assertEquals(HudSelectionTarget.Type.CELL, target.type());
        assertEquals(30f, target.width(), 0.001f);
        assertEquals(20f, target.height(), 0.001f);
    }

    @Test public void childClickSelectsStableIdWithoutHistoryOrDirtyState() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        int history = f.document.editSession().historySize();

        assertTrue(f.session.selectOverlayTargetAt(15f, 15f));
        assertEquals("child", f.session.selectedNodeId());
        assertEquals("child", f.document.selectedNodeId());
        assertEquals(history, f.document.editSession().historySize());
        assertFalse(f.document.isDirty());
    }

    @Test public void rootIsNotEmittedAsAParentFallbackForItsDirectChild() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("child");

        assertNull(target(f.session.selectionTargets(), "root"));
        assertFalse(f.session.selectOverlayTargetAt(90f, 90f));
        assertEquals("child", f.session.selectedNodeId());
    }

    @Test public void nonRootParentFallbackStillNavigatesOneLevelUp() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("grandchild");

        HudSelectionTarget parent = target(f.session.selectionTargets(), "child");
        assertTarget(f.session.selectionTargets(), "child", HudSelectionTarget.Type.PARENT, false);
        assertEquals(10f, parent.x(), 0.001f);
        assertEquals(10f, parent.y(), 0.001f);
        assertEquals(20f, parent.width(), 0.001f);
        assertEquals(20f, parent.height(), 0.001f);
        assertTrue(f.session.selectOverlayTargetAt(25f, 25f));
        assertEquals("child", f.session.selectedNodeId());
    }

    @Test public void childActorTargetBeatsTheOverlappingParentBackTarget() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("child");

        assertTrue(f.session.selectOverlayTargetAt(13f, 14f));
        assertEquals("grandchild", f.session.selectedNodeId());
    }

    @Test public void cellTargetBeatsTheOverlappingParentBackTarget() throws Exception {
        Fixture f = open(nestedTableDocument(), "hud/nested-table");
        f.session.selectNode("table");
        HudSelectionTarget cell = target(f.session.selectionTargets(), "cell-child");

        assertTrue(f.session.selectOverlayTargetAt(
                cell.x() + cell.width() / 2f, cell.y() + cell.height() / 2f));
        assertEquals("cell-child", f.session.selectedNodeId());
    }

    @Test public void canvasRightClickTargetsTheHitNodeBeforeItsContextAction() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        HudCanvasSelectionInputListener listener = new HudCanvasSelectionInputListener(f.session);
        InputEvent right = eventAt(15f, 15f);
        assertTrue(listener.touchDown(right, 0f, 0f, 0, Input.Buttons.RIGHT));
        assertEquals("child", f.session.selectedNodeId());
        assertTrue(f.session.deleteNode("child"));
        assertNull(find(f.session.document().root, "child"));

        InputEvent empty = eventAt(90f, 90f);
        assertFalse(listener.touchDown(empty, 0f, 0f, 0, Input.Buttons.RIGHT));
        assertEquals("root", f.session.selectedNodeId());
    }

    @Test public void canvasCaptureListenerDoesNotConsumeWhenNoHudPreviewIsActive() {
        HudCanvasSelectionInputListener listener = new HudCanvasSelectionInputListener(
                new HudEditorSession());
        InputEvent click = eventAt(15f, 15f);

        assertFalse(listener.touchDown(click, 0f, 0f, 0, Input.Buttons.LEFT));
        assertFalse(click.isStopped());
    }

    @Test public void mouseMoveUsesTheClickTargetAndDoesNotChangeSelectionOrDocument() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        int history = f.document.editSession().historySize();
        HudCanvasSelectionInputListener listener = new HudCanvasSelectionInputListener(f.session);

        assertFalse(listener.mouseMoved(eventAt(15f, 15f), 0f, 0f));
        HudSelectionTarget child = target(f.session.selectionTargets(), "child");
        assertEquals(child, f.session.hoveredSelectionTarget());
        assertVisual(f.session, child, HudSelectionOverlay.VisualState.HOVERED);
        assertEquals("root", f.session.selectedNodeId());
        assertFalse(f.document.isDirty());
        assertEquals(history, f.document.editSession().historySize());
    }

    @Test public void hoverUsesTheFullCellTargetBeforeTheOverlappingParentTarget() throws Exception {
        Fixture f = open(nestedTableDocument(), "hud/nested-table");
        f.session.selectNode("table");
        HudSelectionTarget cell = target(f.session.selectionTargets(), "cell-child");

        assertTrue(f.session.updateHoveredOverlayTargetAt(
                cell.x() + cell.width() / 2f, cell.y() + cell.height() / 2f));
        assertEquals(cell, f.session.hoveredSelectionTarget());
        assertVisual(f.session, cell, HudSelectionOverlay.VisualState.HOVERED);
    }

    @Test public void hoverChangesOnlyWhenItsCanonicalTargetChangesAndClearsOutsideCanvas() throws Exception {
        Fixture f = open(twoChildrenDocument(), "hud/two-children");
        f.session.selectNode("root");
        HudSelectionTarget left = target(f.session.selectionTargets(), "left");
        HudSelectionTarget right = target(f.session.selectionTargets(), "right");

        assertTrue(f.session.updateHoveredOverlayTargetAt(15f, 15f));
        assertEquals(left, f.session.hoveredSelectionTarget());
        assertFalse(f.session.updateHoveredOverlayTargetAt(15f, 15f));
        assertTrue(f.session.updateHoveredOverlayTargetAt(45f, 15f));
        assertEquals(right, f.session.hoveredSelectionTarget());
        assertTrue(f.session.updateHoveredOverlayTargetAt(99f, 99f));
        assertNull(f.session.hoveredSelectionTarget());
    }

    @Test public void canvasInputSelectsASiblingDirectlyAfterSelectingAnotherSibling() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(1920, 1080);
        Fixture f = open(twoChildrenDocument(), "hud/direct-sibling");
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().update(1920, 1080, true);
            HudCanvasInputHost inputHost = new HudCanvasInputHost(f.session);
            inputHost.setBounds(0f, 0f, 1920f, 1080f);
            inputHost.activateHudInput();
            studioStage.addActor(inputHost);
            assertTrue(f.session.configurePreview(new Rectangle(0f, 0f, 1920f, 1080f)));

            f.session.selectNode("left");
            HudSelectionTarget right = target(f.session.selectionTargets(), "right");
            assertTarget(f.session.selectionTargets(), "right", HudSelectionTarget.Type.ACTOR, false);

            Vector2 stagePoint = f.session.hudViewport().project(new Vector2(45f, 15f));
            int rawX = Math.round(stagePoint.x);
            int rawY = Math.round(1080f - stagePoint.y);
            studioStage.mouseMoved(rawX, rawY);
            assertEquals(right, f.session.hoveredSelectionTarget());
            studioStage.touchDown(rawX, rawY, 0, Input.Buttons.LEFT);
            studioStage.touchUp(rawX, rawY, 0, Input.Buttons.LEFT);
            assertEquals("right", f.session.selectedNodeId());
        } finally {
            studioStage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void canvasInputSelectsAuthoredWindowFromItsTitle() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(1920, 1080);
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode window = sized(new HudNode("window", HudNodeKind.WINDOW), 80f, 80f);
        window.window = new HudWindowData();
        emptyTable(window);
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 10f;
        placement.offsetY = 10f;
        root.children.add(HudChild.free(window, placement));
        Fixture f = open(new HudDocumentV1(root), "hud/window-title-selection");
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().update(1920, 1080, true);
            HudCanvasInputHost inputHost = new HudCanvasInputHost(f.session);
            inputHost.setBounds(0f, 0f, 1920f, 1080f);
            inputHost.activateHudInput();
            studioStage.addActor(inputHost);
            assertTrue(f.session.configurePreview(new Rectangle(0f, 0f, 1920f, 1080f)));

            Vector2 stagePoint = f.session.hudViewport().project(new Vector2(20f, 80f));
            int rawX = Math.round(stagePoint.x);
            int rawY = Math.round(1080f - stagePoint.y);
            studioStage.mouseMoved(rawX, rawY);
            studioStage.touchDown(rawX, rawY, 0, Input.Buttons.LEFT);
            studioStage.touchUp(rawX, rawY, 0, Input.Buttons.LEFT);
            assertEquals("window", f.session.selectedNodeId());
        } finally {
            studioStage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void invisibleBranchesRemainInItemsButNotCanvasPickingOrDrop() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(1920, 1080);
        HudDocumentV1 authored = nestedDocument();
        authored.root.children.get(0).node.visible = false;
        Fixture f = open(authored, "hud/hidden-branch");
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().update(1920, 1080, true);
            HudCanvasInputHost host = new HudCanvasInputHost(f.session);
            host.setBounds(0f, 0f, 1920f, 1080f);
            host.activateHudInput();
            studioStage.addActor(host);
            assertTrue(f.session.configurePreview(new Rectangle(0f, 0f, 1920f, 1080f)));
            f.session.selectNode("root");
            assertFalse(f.session.selectionTargets().stream().anyMatch(target ->
                    "child".equals(target.nodeId()) || "grandchild".equals(target.nodeId())));

            Vector2 point = f.session.hudViewport().project(new Vector2(15f, 15f));
            int x = Math.round(point.x), y = Math.round(1080f - point.y);
            studioStage.mouseMoved(x, y);
            studioStage.touchDown(x, y, 0, Input.Buttons.LEFT);
            studioStage.touchUp(x, y, 0, Input.Buttons.LEFT);
            assertEquals("root", f.session.selectedNodeId());
            assertNull(f.session.preselectedWidgetDropTargetAt("child", point.x, point.y));
            assertEquals("root", f.session.widgetDropTargetAt(point.x, point.y).parentId());

            f.session.selectNode("grandchild"); // Items can still inspect an authored hidden descendant.
            assertEquals("grandchild", f.session.selectedNodeId());
            assertFalse(f.session.selectionTargets().stream().anyMatch(HudSelectionTarget::selected));
        } finally {
            studioStage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void invisibleRootExcludesEveryCanvasAndDropTarget() throws Exception {
        HudDocumentV1 authored = nestedDocument();
        authored.root.visible = false;
        Fixture f = open(authored, "hud/hidden-root");
        f.session.selectNode("child");
        assertEquals("child", f.session.selectedNodeId());
        assertTrue(f.session.selectionTargets().isEmpty());
        assertNull(f.session.widgetDropTargetAt(15f, 15f));
        assertNull(f.session.preselectedWidgetDropTargetAt("child", 15f, 15f));
        assertNull(f.session.imageDropTargetAt(15, 85, 100));
    }

    @Test public void visibilityEditReprojectsAndUndoRedoRestoreTheAuthoredActor() throws Exception {
        Fixture f = open(nestedDocument(), "hud/visibility-history");
        f.session.selectNode("child");
        assertTrue(f.session.editNodeVisibility(f.session.screenId(), "child", false));
        assertFalse(f.document.document().root.children.get(0).node.visible);
        assertFalse(f.session.materializedHud().actor("child").isVisible());
        assertEquals("child", f.session.selectedNodeId());
        assertEquals(1, f.document.editSession().historySize());

        assertTrue(f.document.editSession().undo());
        assertTrue(f.session.materializedHud().actor("child").isVisible());
        assertTrue(f.document.editSession().redo());
        assertFalse(f.session.materializedHud().actor("child").isVisible());
        assertEquals(1, f.document.editSession().historySize());
        assertFalse(f.session.editNodeVisibility("hud/another", "child", true));
        assertFalse(f.document.document().root.children.get(0).node.visible);
    }

    @Test public void transientPreviewVisibilityDoesNotChangeAuthoredState() throws Exception {
        Fixture f = open(nestedDocument(), "hud/transient-visibility");
        f.session.materializedHud().actor("child").setVisible(false);
        assertTrue(f.document.document().root.children.get(0).node.visible);
        assertEquals(0, f.document.editSession().historySize());

        f.document.editSession().edit("Resize root", candidate -> {
            candidate.root.actor.width = 101f;
            return candidate;
        });
        assertTrue(f.session.materializedHud().actor("child").isVisible());
        assertTrue(f.document.document().root.children.get(0).node.visible);
    }

    @Test public void inputHostExitAndInactiveHudClearOrRefuseHover() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        HudCanvasSelectionInputListener listener = new HudCanvasSelectionInputListener(f.session);
        listener.mouseMoved(eventAt(15f, 15f), 0f, 0f);
        assertNotNull(f.session.hoveredSelectionTarget());

        listener.exit(eventAt(15f, 15f), 0f, 0f, -1, null);
        assertNull(f.session.hoveredSelectionTarget());

        HudEditorSession inactive = new HudEditorSession();
        assertFalse(inactive.updateHoveredOverlayTargetAt(15f, 15f));
        assertNull(inactive.hoveredSelectionTarget());
    }

    @Test public void rematerializationClearsEphemeralHoverAndLayoutBoundsDoesNotChangeVisualState()
            throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        HudSelectionTarget child = target(f.session.selectionTargets(), "child");
        assertTrue(f.session.updateHoveredOverlayTargetAt(15f, 15f));
        f.session.setShowLayoutBounds(true);
        assertVisual(f.session, child, HudSelectionOverlay.VisualState.HOVERED);
        f.session.setShowLayoutBounds(false);
        assertVisual(f.session, child, HudSelectionOverlay.VisualState.HOVERED);

        f.document.editSession().edit("Resize child", candidate -> {
            candidate.root.children.get(0).node.actor.width = 30f;
            return candidate;
        });
        assertNull(f.session.hoveredSelectionTarget());
    }

    @Test public void selectedAndHoveredTargetUsesOneSelectedVisualState() {
        HudSelectionTarget selected = new HudSelectionTarget("root", HudSelectionTarget.Type.ACTOR,
                0f, 0f, 10f, 10f, true);
        HudSelectionOverlay overlay = new HudSelectionOverlay();
        overlay.rebuild(List.of(selected));

        assertTrue(overlay.setHoveredTarget(selected));
        assertEquals(HudSelectionOverlay.VisualState.SELECTED, overlay.visualState(selected));
    }

    @Test public void selectedFreeChildShowsTransformGizmoButRootAndCellDoNot() throws Exception {
        Fixture free = open(nestedDocument(), "hud/free");
        free.session.selectNode("child");
        assertTrue(free.session.isShowingTransformGizmo());
        HudTransformGeometry.Bounds bounds = free.session.transformGizmoBounds();
        assertEquals(10f, bounds.x(), 0.001f);
        assertEquals(10f, bounds.y(), 0.001f);

        free.session.selectNode("root");
        assertFalse(free.session.isShowingTransformGizmo());

        Fixture cell = open(tableDocument(), "hud/cell");
        cell.session.selectNode("cell-child");
        assertFalse(cell.session.isShowingTransformGizmo());

        Fixture direct = open(directDocument(), "hud/direct");
        direct.session.selectNode("direct-child");
        assertFalse(direct.session.isShowingTransformGizmo());
    }

    @Test public void moveGesturePreviewsThenPublishesOneHistoryEditWithUndoAndRedo() throws Exception {
        Fixture f = open(nestedDocument(), "hud/move");
        f.session.selectNode("child");
        int history = f.document.editSession().historySize();
        Actor before = f.session.materializedHud().actor("child");

        assertTrue(f.session.beginTransformGesture(25f, 25f));
        assertTrue(f.session.updateTransformGesture(35f, 25f));
        assertEquals(history, f.document.editSession().historySize());
        assertEquals(20f, before.getX(), 0.001f);
        assertTrue(f.session.endTransformGesture(35f, 25f));

        assertEquals(history + 1, f.document.editSession().historySize());
        assertEquals(20f, f.document.document().root.children.get(0).free.offsetX, 0.001f);
        assertEquals(10f, f.document.document().root.children.get(0).free.offsetY, 0.001f);
        assertEquals("child", f.session.selectedNodeId());
        assertNotSame(before, f.session.materializedHud().actor("child"));
        assertTrue(f.document.editSession().undo());
        assertEquals(10f, f.document.document().root.children.get(0).free.offsetX, 0.001f);
        assertTrue(f.document.editSession().redo());
        assertEquals(20f, f.document.document().root.children.get(0).free.offsetX, 0.001f);
    }

    @Test public void eastResizeWritesOnlyWidthAndKeepsTheLeftEdgeAndOffsetFixed() throws Exception {
        Fixture f = open(nestedDocument(), "hud/resize");
        f.session.selectNode("child");
        int history = f.document.editSession().historySize();

        assertTrue(f.session.beginTransformGesture(30f, 20f));
        assertTrue(f.session.updateTransformGesture(40f, 20f));
        assertTrue(f.session.endTransformGesture(40f, 20f));

        HudChild child = f.document.document().root.children.get(0);
        assertEquals(history + 1, f.document.editSession().historySize());
        assertEquals(30f, child.node.actor.width, 0.001f);
        assertEquals(20f, child.node.actor.height, 0.001f);
        assertEquals(10f, child.free.offsetX, 0.001f);
        assertEquals(10f, child.free.offsetY, 0.001f);
    }

    @Test public void clickOrSubThresholdNoiseDoesNotCreateHistory() throws Exception {
        Fixture f = open(nestedDocument(), "hud/noise");
        f.session.selectNode("child");
        int history = f.document.editSession().historySize();

        assertTrue(f.session.beginTransformGesture(25f, 25f));
        assertTrue(f.session.updateTransformGesture(27f, 25f));
        assertTrue(f.session.endTransformGesture(27f, 25f));

        assertEquals(history, f.document.editSession().historySize());
        assertEquals(10f, f.document.document().root.children.get(0).free.offsetX, 0.001f);
    }

    @Test public void manyDragUpdatesPublishOnlyOneHistoryEntryAndSuppressHover() throws Exception {
        Fixture f = open(twoChildrenDocument(), "hud/many-events");
        f.session.selectNode("left");
        int history = f.document.editSession().historySize();

        assertTrue(f.session.beginTransformGesture(25f, 15f));
        for (int i = 1; i <= 20; i++) {
            assertTrue(f.session.updateTransformGesture(25f + i, 15f));
            assertFalse(f.session.updateHoveredOverlayTargetAt(45f, 15f));
            assertNull(f.session.hoveredSelectionTarget());
        }
        assertTrue(f.session.endTransformGesture(45f, 15f));

        assertEquals(history + 1, f.document.editSession().historySize());
        assertEquals(30f, f.document.document().root.children.get(0).free.offsetX, 0.001f);
        assertFalse(f.session.hasActiveTransformGesture());
    }

    @Test public void cancelledGestureRestoresPreviewWithoutHistory() throws Exception {
        Fixture f = open(nestedDocument(), "hud/cancel");
        f.session.selectNode("child");
        int history = f.document.editSession().historySize();
        Actor actor = f.session.materializedHud().actor("child");

        assertTrue(f.session.beginTransformGesture(25f, 25f));
        assertTrue(f.session.updateTransformGesture(35f, 25f));
        assertEquals(20f, actor.getX(), 0.001f);
        assertTrue(f.session.cancelTransformGesture());

        assertEquals(history, f.document.editSession().historySize());
        assertEquals(10f, actor.getX(), 0.001f);
        assertTrue(f.session.isShowingTransformGizmo());
        assertFalse(f.session.hasActiveTransformGesture());
    }

    @Test public void canvasListenerCapturesAndEndsAFreeTransformOnMouseRelease() throws Exception {
        Fixture f = open(nestedDocument(), "hud/listener-transform");
        f.session.selectNode("child");
        HudCanvasSelectionInputListener listener = new HudCanvasSelectionInputListener(f.session);

        InputEvent down = eventAt(25f, 25f);
        assertTrue(listener.touchDown(down, 0f, 0f, 0, Input.Buttons.LEFT));
        assertTrue(f.session.hasPendingMoveGesture());
        assertFalse(f.session.hasActiveTransformGesture());
        InputEvent drag = eventAt(35f, 25f);
        listener.touchDragged(drag, 0f, 0f, 0);
        assertTrue(f.session.hasActiveTransformGesture());
        InputEvent up = eventAt(35f, 25f);
        listener.touchUp(up, 0f, 0f, 0, Input.Buttons.LEFT);

        assertTrue(up.isStopped());
        assertFalse(f.session.hasActiveTransformGesture());
        assertEquals(20f, f.document.document().root.children.get(0).free.offsetX, 0.001f);
    }

    @Test public void closedAtStartDialogCanBeSelectedMovedAndResizedInEdit() throws Exception {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode dialog = sized(new HudNode("dialog", HudNodeKind.DIALOG), 50f, 40f);
        dialog.dialog = new HudDialogData();
        emptyTable(dialog);
        dialog.visible = false;
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 10f;
        placement.offsetY = 10f;
        root.children.add(HudChild.free(dialog, placement));
        Fixture f = open(new HudDocumentV1(root), "hud/dialog-transform");

        assertTrue(f.session.selectOverlayTargetAt(35f, 30f));
        assertEquals("dialog", f.session.selectedNodeId());
        assertTrue(f.session.beginTransformGesture(35f, 30f));
        assertTrue(f.session.updateTransformGesture(45f, 30f));
        assertEquals(20f, f.session.materializedHud().actor("dialog")
                .localToStageCoordinates(new Vector2()).x, .001f);
        assertTrue(f.session.endTransformGesture(45f, 30f));
        assertEquals(20f, f.document.document().root.children.get(0).free.offsetX, .001f);
        assertTrue(f.session.materializedHud().actor("dialog").isVisible());

        assertTrue(f.session.beginTransformGesture(70f, 30f));
        assertTrue(f.session.hasActiveResizeGesture());
        assertTrue(f.session.updateTransformGesture(80f, 30f));
        assertTrue(f.session.endTransformGesture(80f, 30f));
        assertEquals(60f, f.document.document().root.children.get(0).node.actor.width, .001f);
        assertFalse(f.document.document().root.children.get(0).node.visible);
    }

    @Test public void occupiedFreeTableAndContainerDragThroughStageWithoutMovingTheirImages()
            throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(100, 100);
        Fixture f = open(occupiedImageContainersDocument(), "hud/occupied-parent-move",
                imageDatabase("filled__a1", 40, 30));
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().update(100, 100, true);
            HudCanvasInputHost inputHost = new HudCanvasInputHost(f.session);
            inputHost.setBounds(0f, 0f, 100f, 100f);
            inputHost.activateHudInput();
            studioStage.addActor(inputHost);

            assertOccupiedParentMove(studioStage, f, "table", "table-image", 25f, 25f);
            assertOccupiedParentMove(studioStage, f, "container", "container-image", 75f, 25f);
        } finally {
            studioStage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void occupiedParentClickSelectsChildWhileHandleAndCancellationKeepPriority()
            throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(100, 100);
        Fixture f = open(occupiedImageContainersDocument(), "hud/occupied-parent-click",
                imageDatabase("filled__a1", 40, 30));
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().update(100, 100, true);
            HudCanvasInputHost inputHost = new HudCanvasInputHost(f.session);
            inputHost.setBounds(0f, 0f, 100f, 100f);
            inputHost.activateHudInput();
            studioStage.addActor(inputHost);

            f.session.selectNode("table");
            int history = f.document.editSession().historySize();
            stageTouchDown(studioStage, f.session, 25f, 25f);
            assertTrue(f.session.hasPendingMoveGesture());
            stageTouchUp(studioStage, f.session, 25f, 25f);
            assertEquals("table-image", f.session.selectedNodeId());
            assertEquals(history, f.document.editSession().historySize());
            assertEquals(10f, relation(f.session.document(), "table").free.offsetX, 0.001f);

            f.session.selectNode("table");
            stageTouchDown(studioStage, f.session, 50f, 25f);
            assertTrue("transform handle must bypass the pending body gesture",
                    f.session.hasActiveResizeGesture());
            assertFalse(f.session.hasPendingMoveGesture());
            stageTouchUp(studioStage, f.session, 50f, 25f);

            f.session.selectNode("container");
            stageTouchDown(studioStage, f.session, 75f, 25f);
            assertTrue(f.session.hasPendingMoveGesture());
            studioStage.cancelTouchFocus(inputHost);
            assertFalse(f.session.hasPendingMoveGesture());
            assertFalse(f.session.hasActiveTransformGesture());

            stageTouchDown(studioStage, f.session, 75f, 25f);
            stageTouchDragged(studioStage, f.session, 85f, 25f);
            assertTrue(f.session.hasActiveTransformGesture());
            studioStage.cancelTouchFocus(inputHost);
            assertFalse(f.session.hasPendingMoveGesture());
            assertFalse(f.session.hasActiveTransformGesture());
            assertEquals(60f, relation(f.session.document(), "container").free.offsetX, 0.001f);
            assertEquals(history, f.document.editSession().historySize());
        } finally {
            studioStage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void heldMiddlePanReversesImmediatelyAfterClampingAtTheHudBoundary()
            throws Exception {
        HudScreenAsset asset = new HudScreenAsset();
        asset.referenceWidth = 1920;
        asset.referenceHeight = 1080;
        asset.documentId = "hud/pan-boundary.json";
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/pan-boundary", "hud/pan-boundary", asset, nestedDocument());
        Fixture f = open(document);
        assertTrue(f.session.configurePreview(new Rectangle(20f, 30f, 100f, 100f)));
        f.session.hudCamera().position.set(50f, 50f, 0f);
        f.session.hudCamera().update();
        HudCanvasSelectionInputListener listener = new HudCanvasSelectionInputListener(f.session);

        assertTrue(listener.touchDown(eventAt(70f, 80f), 0f, 0f, 0, Input.Buttons.MIDDLE));
        listener.touchDragged(eventAt(110f, 80f), 0f, 0f, 0);
        assertEquals(50f, f.session.hudCameraX(), 0.001f);

        listener.touchDragged(eventAt(100f, 80f), 0f, 0f, 0);
        assertEquals(60f, f.session.hudCameraX(), 0.001f);
    }

    @Test public void transformCursorUsesTheGestureHandleAndNeverEditsTheDocumentOnHover()
            throws Exception {
        Fixture f = open(nestedDocument(), "hud/cursor");
        f.session.selectNode("child");
        int history = f.document.editSession().historySize();

        assertEquals(HudTransformHandle.E, f.session.transformHandleAt(30f, 20f));
        assertNull(f.session.transformHandleAt(25f, 25f));
        assertEquals(history, f.document.editSession().historySize());
        assertFalse(f.document.isDirty());

        assertTrue(f.session.beginTransformGesture(30f, 20f));
        assertTrue(f.session.hasActiveResizeGesture());
        // The active resize handle remains authoritative even far outside its hit square.
        assertEquals(HudTransformHandle.E, f.session.transformCursorHandleAt(95f, 95f));
        assertTrue(f.session.cancelTransformGesture());
        assertFalse(f.session.hasActiveResizeGesture());
        assertNull(f.session.transformCursorHandleAt(95f, 95f));
        assertEquals(history, f.document.editSession().historySize());
        assertFalse(f.document.isDirty());
    }

    @Test public void studioStageCaptureRoutesAnOtherwiseEmptyCanvasClickToTheHudSelection() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        int history = f.document.editSession().historySize();
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().setWorldSize(200f, 200f);
            studioStage.getViewport().setScreenBounds(0, 0, 200, 200);
            assertTrue(f.session.configurePreview(new Rectangle(20f, 30f, 100f, 100f)));
            HudCanvasInputHost canvasInputHost = new HudCanvasInputHost(f.session);
            canvasInputHost.setBounds(0f, 0f, 200f, 200f);
            canvasInputHost.activateHudInput();
            studioStage.addActor(canvasInputHost);

            // Studio-stage (35, 45) maps through the HUD viewport to overlay (15, 15).
            assertSame(canvasInputHost, studioStage.hit(35f, 45f, true));
            InputEvent click = eventAt(35f, 45f);
            click.setType(InputEvent.Type.touchDown);
            click.setPointer(0);
            click.setButton(Input.Buttons.LEFT);
            canvasInputHost.fire(click);
            assertTrue(click.isStopped());
            assertEquals("child", f.session.selectedNodeId());
            assertFalse(f.document.isDirty());
            assertEquals(history, f.document.editSession().historySize());
        } finally {
            studioStage.dispose();
        }
    }

    @Test public void hudCanvasInputHostStartsOutsideHitTestingBeforeAnyHudIsOpened() {
        HudEditorSession unopenedSession = new HudEditorSession();
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().setWorldSize(200f, 200f);
            studioStage.getViewport().setScreenBounds(0, 0, 200, 200);
            HudCanvasInputHost canvasInputHost = new HudCanvasInputHost(unopenedSession);
            canvasInputHost.setBounds(0f, 0f, 200f, 200f);
            studioStage.addActor(canvasInputHost);

            Actor normalUiControl = new Actor();
            normalUiControl.setBounds(0f, 0f, 20f, 20f);
            studioStage.addActor(normalUiControl);

            assertNull(studioStage.hit(100f, 100f, true));
            assertSame(normalUiControl, studioStage.hit(10f, 10f, true));
        } finally {
            unopenedSession.dispose();
            studioStage.dispose();
        }
    }

    @Test public void hudCanvasInputHostParticipatesInHitsOnlyWhileHudIsActive() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().setWorldSize(200f, 200f);
            studioStage.getViewport().setScreenBounds(0, 0, 200, 200);
            HudCanvasInputHost canvasInputHost = new HudCanvasInputHost(f.session);
            canvasInputHost.setBounds(0f, 0f, 200f, 200f);
            studioStage.addActor(canvasInputHost);

            // Initial Scene workspace: the persistent transparent host cannot classify the canvas as UI.
            assertNull(studioStage.hit(100f, 100f, true));

            canvasInputHost.activateHudInput();
            assertSame(canvasInputHost, studioStage.hit(100f, 100f, true));

            AtomicBoolean touchFocusCancelled = new AtomicBoolean();
            com.badlogic.gdx.scenes.scene2d.InputListener focusListener =
                    new com.badlogic.gdx.scenes.scene2d.InputListener() {
                        @Override public void touchUp(InputEvent event, float x, float y,
                                                      int pointer, int button) {
                            touchFocusCancelled.set(event.isTouchFocusCancel());
                        }
                    };
            studioStage.addTouchFocus(
                    focusListener, canvasInputHost, canvasInputHost, 0, Input.Buttons.LEFT);

            canvasInputHost.suspendHudInput();
            assertTrue(touchFocusCancelled.get());
            assertNull(studioStage.hit(100f, 100f, true));

            canvasInputHost.activateHudInput();
            assertSame(canvasInputHost, studioStage.hit(100f, 100f, true));
        } finally {
            studioStage.dispose();
        }
    }

    @Test public void modelSelectionImmediatelyRebuildsCanvasOverlay() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("grandchild");

        assertTarget(f.session.selectionTargets(), "grandchild", HudSelectionTarget.Type.ACTOR, true);
        assertTarget(f.session.selectionTargets(), "child", HudSelectionTarget.Type.PARENT, false);
        assertNull(target(f.session.selectionTargets(), "root"));
    }

    @Test public void rematerializationUsesNewActorsAndPreservesStableSelection() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("child");
        Actor previous = f.session.materializedHud().actor("child");

        f.document.editSession().edit("Resize child", candidate -> {
            candidate.root.children.get(0).node.actor.width = 30f;
            return candidate;
        });

        assertEquals("child", f.session.selectedNodeId());
        assertNotSame(previous, f.session.materializedHud().actor("child"));
        assertEquals(30f, target(f.session.selectionTargets(), "child").width(), 0.001f);
    }

    @Test public void undoAndRedoRebuildTargetsAroundEachNewMaterialization() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("child");
        f.document.editSession().edit("Resize child", candidate -> {
            candidate.root.children.get(0).node.actor.width = 30f;
            return candidate;
        });
        Actor edited = f.session.materializedHud().actor("child");

        assertTrue(f.document.editSession().undo());
        assertEquals("child", f.session.selectedNodeId());
        assertEquals(20f, target(f.session.selectionTargets(), "child").width(), 0.001f);
        assertNotSame(edited, f.session.materializedHud().actor("child"));

        assertTrue(f.document.editSession().redo());
        assertEquals("child", f.session.selectedNodeId());
        assertEquals(30f, target(f.session.selectionTargets(), "child").width(), 0.001f);
    }

    @Test public void switchingDocumentsClearsOldTargetsWithoutCreatingARootCanvasTarget() throws Exception {
        Fixture first = open(nestedDocument(), "hud/first");
        first.session.selectNode("child");
        HudNode otherRoot = sized(new HudNode("other-root", HudNodeKind.GROUP), 100f, 100f);
        HudScreenEditorDocument other = document("hud/other", new HudDocumentV1(otherRoot));

        first.session.open(Gdx.files.absolute(temporary.getRoot().getAbsolutePath()), other);
        first.session.configurePreview(new Rectangle(0f, 0f, 100f, 100f));

        assertNull(target(first.session.selectionTargets(), "child"));
        assertNull(target(first.session.selectionTargets(), "other-root"));
        assertTrue(first.session.selectionTargets().isEmpty());
    }

    @Test public void layoutBoundsToggleDoesNotChangeTargetsOrClickBehavior() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        List<HudSelectionTarget> before = f.session.selectionTargets();

        f.session.setShowLayoutBounds(true);
        assertEquals(before, f.session.selectionTargets());
        assertTrue(f.session.selectOverlayTargetAt(15f, 15f));
        assertEquals("child", f.session.selectedNodeId());

        f.session.setShowLayoutBounds(false);
        assertFalse(f.session.selectOverlayTargetAt(90f, 90f));
        assertEquals("child", f.session.selectedNodeId());
    }

    @Test public void viewportMovesAndResizesWithoutAccumulatingOverlayOffset() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        HudSelectionTarget before = target(f.session.selectionTargets(), "child");

        assertTrue(f.session.configurePreview(new Rectangle(20f, 30f, 200f, 200f)));
        f.session.hudCamera().position.set(100f, 100f, 0f);
        f.session.hudCamera().update();
        HudSelectionTarget after = target(f.session.selectionTargets(), "child");
        assertEquals(before.x(), after.x(), 0.001f);
        assertEquals(before.y(), after.y(), 0.001f);
        assertTrue(f.session.selectOverlayTargetAt(35f, 45f));
        assertEquals("child", f.session.selectedNodeId());

        // The visible east handle remains hittable at its projected Stage position after translation.
        assertEquals(HudTransformHandle.E, f.session.transformHandleAt(50f, 50f));
        assertTrue(f.session.beginTransformGesture(50f, 50f));
        assertTrue(f.session.hasActiveResizeGesture());
        assertTrue(f.session.cancelTransformGesture());
    }

    @Test public void translatedViewportPicksBothRenderedHalvesAndDisplayedSideHandles() throws Exception {
        HudScreenAsset asset = new HudScreenAsset();
        asset.referenceWidth = 1920;
        asset.referenceHeight = 1080;
        asset.documentId = "hud/projected-pick.json";
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 1920f, 1080f);
        HudNode child = sized(new HudNode("child", HudNodeKind.GROUP), 200f, 120f);
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 850f;
        placement.offsetY = 420f;
        root.children.add(HudChild.free(child, placement));
        Fixture f = open(new HudScreenEditorDocument("hud/projected-pick", "hud/projected-pick",
                asset, new HudDocumentV1(root)));
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(1920, 1080);
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().update(1920, 1080, true);
            HudCanvasInputHost inputHost = new HudCanvasInputHost(f.session);
            inputHost.setBounds(280f, 160f, 900f, 600f);
            inputHost.activateHudInput();
            studioStage.addActor(inputHost);

            assertTrue(f.session.configurePreview(new Rectangle(280f, 160f, 900f, 600f)));
            // Default zoom before pan. Both side handles are independently projected through
            // the HUD camera and routed through the Studio Stage/input host.
            f.session.selectNode("child");
            assertDisplayedSideHandles(studioStage, f.session);

            // Horizontal and vertical pan; the two selectable halves still enter through
            // Stage.touchDown rather than a directly constructed InputEvent.
            f.session.hudCamera().position.set(1040f, 520f, 0f);
            f.session.hudCamera().update();
            f.session.act(0f);
            f.session.selectNode("root");

            assertStageClickSelects(studioStage, f.session, 875f, 480f, "child");
            f.session.selectNode("root");
            assertStageClickSelects(studioStage, f.session, 1025f, 480f, "child");
            f.session.selectNode("root");
            assertStageEmptySpaceDoesNotSelect(studioStage, f.session, 1200f, 700f);

            f.session.selectNode("child");
            assertDisplayedSideHandles(studioStage, f.session);

            // Resizing retains a non-zero canvas origin and changes the actual viewport size.
            inputHost.setBounds(280f, 160f, 780f, 540f);
            assertTrue(f.session.configurePreview(new Rectangle(280f, 160f, 780f, 540f)));
            f.session.selectNode("child");
            assertDisplayedSideHandles(studioStage, f.session);

            // Visible rulers trim the HUD viewport but not the full-center input host.
            inputHost.setBounds(280f, 160f, 900f, 600f);
            assertTrue(f.session.configurePreview(new Rectangle(320f, 160f, 860f, 575f)));
            f.session.selectNode("child");
            assertDisplayedSideHandles(studioStage, f.session);

            // Hiding rulers restores the full canvas viewport with the same host bounds.
            assertTrue(f.session.configurePreview(new Rectangle(280f, 160f, 900f, 600f)));
            f.session.selectNode("child");
            assertDisplayedSideHandles(studioStage, f.session);

            // ScreenViewport projection changes at non-unit zoom; the input path must still
            // recover the same HUD handle coordinates through Viewport.unproject.
            f.session.hudCamera().zoom = 1.5f;
            f.session.hudCamera().update();
            assertDisplayedSideHandles(studioStage, f.session);
        } finally {
            studioStage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void authoringSessionOwnsOneOverlaySiblingAboveAuthoredHost() {
        HudAuthoringSession authoring = new HudAuthoringSession(inertBatch());
        try {
            assertSame(authoring.authoredHost(), authoring.stage().getRoot().getChildren().get(0));
            assertSame(authoring.selectionOverlayHost(), authoring.stage().getRoot().getChildren().get(1));
            assertEquals(2, authoring.stage().getRoot().getChildren().size);
        } finally {
            authoring.dispose();
        }
    }

    @Test public void clearingPreviewKeepsOverlayLayerButRemovesItsTargets() throws Exception {
        Fixture f = open(nestedDocument(), "hud/main");
        f.session.selectNode("root");
        f.session.suspend();

        assertTrue(f.session.selectionTargets().isEmpty());
        assertEquals(HudEditorSession.Status.CLOSED, f.session.status());
    }

    @Test public void imageDropPrefersDeepestEligibleGroupAndClampsItsSourceSize() throws Exception {
        Fixture f = open(nestedDocument(), "hud/image-drop");

        HudEditorSession.ImageDropTarget target =
                f.session.imageDropTargetAt(19, 81, 100, 10f, 10f);

        assertNotNull(target);
        assertEquals("child", target.parentId());
        assertNotNull(target.placement());
        assertEquals(9f, target.placement().offsetX, 0.001f);
        assertEquals(9f, target.placement().offsetY, 0.001f);
        assertEquals(10f, target.imageWidth(), 0.001f);
        assertEquals(10f, target.imageHeight(), 0.001f);
    }

    @Test public void occupiedSingleChildContainerDoesNotFallThroughToRootDrop() throws Exception {
        Fixture f = open(directDocument(), "hud/occupied-drop");

        assertNull(f.session.imageDropTargetAt(80, 20, 100, 10f, 10f));
        assertNull(f.session.imageDropTargetAt(101, 50, 100, 10f, 10f));
    }

    @Test public void scrolledContentUsesNativeViewportForStagePickingOverlaysAndWheelInput()
            throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Application previousApp = Gdx.app;
        Gdx.graphics = logicalGraphics(1920, 1080);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, args) ->
                        method.getName().equals("getType") ? Application.ApplicationType.Desktop
                                : method.invoke(previousApp, args));
        Fixture f = open(scrolledPaneDocument(), "hud/scrolled-pane");
        Stage studioStage = new Stage(new ScreenViewport(), inertBatch());
        try {
            studioStage.getViewport().update(1920, 1080, true);
            HudCanvasInputHost inputHost = new HudCanvasInputHost(f.session);
            inputHost.setBounds(0f, 0f, 1920f, 1080f);
            inputHost.activateHudInput();
            studioStage.addActor(inputHost);
            assertTrue(f.session.configurePreview(new Rectangle(0f, 0f, 1920f, 1080f)));

            ScrollPane pane = (ScrollPane) f.session.materializedHud().actor("pane");
            pane.validate();
            pane.setScrollPercentY(1f);
            pane.updateVisualScroll();
            pane.layout();
            f.session.selectNode("content");

            assertNotNull(target(f.session.selectionTargets(), "lower-table"));
            assertNull("the scrolled-out child must not get an overlay target",
                    target(f.session.selectionTargets(), "upper-table"));

            Actor lower = f.session.materializedHud().actor("lower-table");
            Vector2 lowerHud = lower.localToStageCoordinates(
                    new Vector2(lower.getWidth() * .5f, lower.getHeight() * .5f));
            Vector2 lowerStudio = f.session.hudViewport().project(new Vector2(lowerHud));
            HudEditorSession.WidgetDropTarget widgetTarget =
                    f.session.widgetDropTargetAt(lowerStudio.x, lowerStudio.y);
            assertNotNull(widgetTarget);
            assertEquals("lower-table", widgetTarget.parentId());

            int lowerRawX = Math.round(lowerStudio.x);
            int lowerRawY = Math.round(1080f - lowerStudio.y);
            studioStage.touchDown(lowerRawX, lowerRawY, 0, Input.Buttons.LEFT);
            studioStage.touchUp(lowerRawX, lowerRawY, 0, Input.Buttons.LEFT);
            assertEquals("lower-table", f.session.selectedNodeId());

            Actor upper = f.session.materializedHud().actor("upper-table");
            Vector2 upperHud = upper.localToStageCoordinates(
                    new Vector2(upper.getWidth() * .5f, upper.getHeight() * .5f));
            Vector2 upperStudio = f.session.hudViewport().project(new Vector2(upperHud));
            HudEditorSession.WidgetDropTarget hiddenTarget =
                    f.session.widgetDropTargetAt(upperStudio.x, upperStudio.y);
            assertTrue(hiddenTarget == null || !"upper-table".equals(hiddenTarget.parentId()));
            HudEditorSession.ImageDropTarget hiddenImageTarget = f.session.imageDropTargetAt(
                    Math.round(upperStudio.x), Math.round(1080f - upperStudio.y), 1080, 8f, 8f);
            assertTrue(hiddenImageTarget == null
                    || !"upper-table".equals(hiddenImageTarget.parentId()));
            studioStage.touchDown(Math.round(upperStudio.x), Math.round(1080f - upperStudio.y),
                    0, Input.Buttons.LEFT);
            studioStage.touchUp(Math.round(upperStudio.x), Math.round(1080f - upperStudio.y),
                    0, Input.Buttons.LEFT);
            assertFalse("a scrolled-out child must not become selected",
                    "upper-table".equals(f.session.selectedNodeId()));

            float beforeWheel = pane.getScrollY();
            Vector2 paneHud = pane.localToStageCoordinates(new Vector2(30f, 30f));
            Vector2 paneStudio = f.session.hudViewport().project(new Vector2(paneHud));
            studioStage.mouseMoved(Math.round(paneStudio.x), Math.round(1080f - paneStudio.y));
            studioStage.act(0f);
            assertSame(inputHost, studioStage.getScrollFocus());
            float wheelDirection = beforeWheel > 0f ? -1f : 1f;
            assertTrue(studioStage.scrolled(0f, wheelDirection));
            assertTrue("native ScrollPane amount must change without a mirrored editor value: before="
                            + beforeWheel + ", after=" + pane.getScrollY() + ", max=" + pane.getMaxY(),
                    pane.getScrollY() != beforeWheel);
            assertEquals(0, f.document.editSession().historySize());

            Actor panelContent = new Actor();
            panelContent.setSize(200f, 200f);
            ScrollPane overlayPanel = new ScrollPane(panelContent, new ScrollPane.ScrollPaneStyle());
            overlayPanel.setBounds(paneStudio.x - 20f, paneStudio.y - 20f, 40f, 40f);
            overlayPanel.addListener(new GetScrollListener(overlayPanel));
            overlayPanel.addListener(new LoseScroolListener());
            overlayPanel.validate();
            studioStage.addActor(overlayPanel);
            studioStage.mouseMoved(Math.round(paneStudio.x), Math.round(1080f - paneStudio.y));
            studioStage.act(0f);
            assertSame("an overlapping panel owns the wheel", overlayPanel,
                    studioStage.getScrollFocus());
            float hudScrollBeforePanelWheel = pane.getScrollY();
            assertTrue(studioStage.scrolled(0f, 1f));
            assertEquals(hudScrollBeforePanelWheel, pane.getScrollY(), 0f);

            studioStage.mouseMoved(Math.round(paneStudio.x + 100f),
                    Math.round(1080f - paneStudio.y));
            studioStage.act(0f);
            assertSame(inputHost, studioStage.getScrollFocus());
            float outsideScroll = pane.getScrollY();
            assertFalse("outside a ScrollPane the canvas leaves the wheel unhandled",
                    studioStage.scrolled(0f, 1f));
            assertEquals(outsideScroll, pane.getScrollY(), 0f);
            studioStage.mouseMoved(2200, 100);
            studioStage.act(0f);
            assertNull("leaving the canvas releases its own scroll focus",
                    studioStage.getScrollFocus());
            studioStage.mouseMoved(Math.round(paneStudio.x + 100f),
                    Math.round(1080f - paneStudio.y));
            studioStage.act(0f);
            assertSame(inputHost, studioStage.getScrollFocus());
            inputHost.suspendHudInput();
            assertNull(studioStage.getScrollFocus());
            inputHost.activateHudInput();
            studioStage.mouseMoved(2200, 100);
            studioStage.act(0f);
            studioStage.mouseMoved(Math.round(paneStudio.x + 100f),
                    Math.round(1080f - paneStudio.y));
            studioStage.act(0f);
            assertSame(inputHost, studioStage.getScrollFocus());
            studioStage.setScrollFocus(overlayPanel);
            studioStage.mouseMoved(2200, 100);
            studioStage.act(0f);
            assertSame("exiting HUD must not clear another control's focus",
                    overlayPanel, studioStage.getScrollFocus());
            inputHost.suspendHudInput();
            assertSame("suspending HUD must not clear a newer panel owner",
                    overlayPanel, studioStage.getScrollFocus());
        } finally {
            studioStage.dispose();
            Gdx.graphics = previousGraphics;
            Gdx.app = previousApp;
        }
    }

    @Test public void scrollPaneDropCapacityBlocksReplacementButKeepsVisibleLayoutReachable()
            throws Exception {
        Fixture empty = open(emptyPaneDocument(), "hud/empty-pane");
        ScrollPane emptyPane = (ScrollPane) empty.session.materializedHud().actor("pane");
        Rectangle emptyViewport = HudOverlayGeometry.scrollPaneContentBoundsInOverlay(
                emptyPane, empty.session.materializedHud().root(), new Rectangle());
        HudEditorSession.WidgetDropTarget emptyTarget = empty.session.widgetDropTargetAt(
                emptyViewport.x + emptyViewport.width * .5f,
                emptyViewport.y + emptyViewport.height * .5f);
        assertNotNull(emptyTarget);
        assertEquals("pane", emptyTarget.parentId());
        assertNull(emptyTarget.placement());

        openSession.dispose();
        openSession = null;
        Fixture occupiedLeaf = open(occupiedPaneDocument(false), "hud/occupied-pane-leaf");
        assertNull(occupiedLeaf.session.widgetDropTargetAt(40f, 40f));
        assertNull(occupiedLeaf.session.imageDropTargetAt(40, 60, 100, 8f, 8f));

        openSession.dispose();
        openSession = null;
        Fixture occupiedLayout = open(occupiedPaneDocument(true), "hud/occupied-pane-layout");
        HudEditorSession.WidgetDropTarget nested =
                occupiedLayout.session.widgetDropTargetAt(40f, 40f);
        assertNotNull(nested);
        assertEquals("content", nested.parentId());
        HudEditorSession.ImageDropTarget image =
                occupiedLayout.session.imageDropTargetAt(40, 60, 100, 8f, 8f);
        assertNotNull(image);
        assertEquals("content", image.parentId());
    }

    @Test public void deletingHudSubtreeRestoresItsRelationAndSelectionThroughUndoRedo() throws Exception {
        Fixture f = open(deletableDocument(), "hud/delete");
        f.session.selectNode("victim");

        assertTrue(f.session.deleteSelectedNode());
        assertEquals("root", f.session.selectedNodeId());
        assertEquals(List.of("before", "after"), f.session.document().root.children.stream()
                .map(child -> child.node.id).toList());
        assertNull(find(f.session.document().root, "victim"));
        assertFalse(f.session.deleteNode("root"));

        assertTrue(f.document.editSession().undo());
        assertEquals("victim", f.session.selectedNodeId());
        HudChild restored = f.session.document().root.children.get(1);
        assertEquals("victim", restored.node.id);
        assertEquals(31f, restored.free.offsetX, 0.001f);
        assertEquals(17f, restored.free.offsetY, 0.001f);
        games.pixscape.runtime.hud.document.HudTableCell restoredCell =
                restored.node.table.rows.get(0).cells.get(0);
        assertEquals(42f, restoredCell.constraints.prefWidth, 0.001f);
        assertEquals("stack", restoredCell.content.id);
        assertEquals("leaf", restoredCell.content.children.get(0).node.id);

        assertTrue(f.document.editSession().redo());
        assertEquals("root", f.session.selectedNodeId());
        assertEquals(List.of("before", "after"), f.session.document().root.children.stream()
                .map(child -> child.node.id).toList());
    }

    private Fixture open(HudDocumentV1 document, String id) throws Exception {
        return open(document(id, document));
    }

    private Fixture open(HudScreenEditorDocument editor) throws Exception {
        return open(editor, null);
    }

    @Test public void stageDragMovesAndSwapsDirectCellWidgetsWithOneHistoryStep() throws Exception {
        if (!com.kotcrab.vis.ui.VisUI.isLoaded()) com.kotcrab.vis.ui.VisUI.load();
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(100, 100);
        HudNode table = sized(new HudNode("table", HudNodeKind.TABLE), 100f, 60f);
        table.table = HudLayoutAuthoring.newTableLayout(table, 1, 2, false);
        var first = table.table.rows.get(0).cells.get(0);
        var second = table.table.rows.get(0).cells.get(1);
        first.constraints.minWidth = 45f;
        first.constraints.minHeight = 40f;
        second.constraints.minWidth = 45f;
        second.constraints.minHeight = 40f;
        first.content = sized(new HudNode("first", HudNodeKind.GROUP), 20f, 20f);
        String firstCellId = first.id, secondCellId = second.id;
        Fixture f = open(new HudDocumentV1(table), "hud/cell-move");
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(100, 100, true);
            HudCanvasInputHost host = new HudCanvasInputHost(f.session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            Vector2 source = f.session.materializedHud().actor("first")
                    .localToStageCoordinates(new Vector2(10f, 10f));
            Rectangle target = HudOverlayGeometry.visibleCellBoundsInOverlay(
                    f.session.materializedHud().cell(secondCellId),
                    f.session.materializedHud().actor("table"), new Rectangle());
            Vector2 destination = f.session.materializedHud().actor("table").localToStageCoordinates(
                    new Vector2(target.x + target.width * 0.5f, target.y + target.height * 0.5f));
            int initialHistory = f.document.editSession().historySize();
            stageTouchDown(stage, f.session, source.x, source.y);
            stageTouchUp(stage, f.session, source.x, source.y);
            assertEquals("first", f.session.selectedNodeId());
            assertEquals(initialHistory, f.document.editSession().historySize());

            stageTouchDown(stage, f.session, source.x, source.y);
            stageTouchDragged(stage, f.session, destination.x, destination.y);
            stageTouchUp(stage, f.session, destination.x, destination.y);
            assertNull(HudLayoutAuthoring.cell(f.session.document(), firstCellId).content);
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), secondCellId).content.id);
            assertEquals(initialHistory + 1, f.document.editSession().historySize());
            assertEquals("first", f.session.selectedNodeId());
            assertTrue(f.document.editSession().undo());
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), firstCellId).content.id);
            assertNull(HudLayoutAuthoring.cell(f.session.document(), secondCellId).content);
            assertTrue(f.document.editSession().redo());
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), secondCellId).content.id);

            f.document.editSession().edit("Fill other cell", candidate -> {
                HudLayoutAuthoring.cell(candidate, firstCellId).content =
                        sized(new HudNode("other", HudNodeKind.GROUP), 20f, 20f);
                return candidate;
            });
            int beforeSwap = f.document.editSession().historySize();
            Vector2 moveFrom = f.session.materializedHud().actor("first")
                    .localToStageCoordinates(new Vector2(10f, 10f));
            Vector2 moveTo = f.session.materializedHud().actor("other")
                    .localToStageCoordinates(new Vector2(10f, 10f));
            stageTouchDown(stage, f.session, moveFrom.x, moveFrom.y);
            stageTouchDragged(stage, f.session, moveTo.x, moveTo.y);
            stageTouchUp(stage, f.session, moveTo.x, moveTo.y);
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), firstCellId).content.id);
            assertEquals("other", HudLayoutAuthoring.cell(f.session.document(), secondCellId).content.id);
            assertEquals(beforeSwap + 1, f.document.editSession().historySize());
            assertEquals("first", f.session.selectedNodeId());
            assertTrue(f.document.editSession().undo());
            assertEquals("other", HudLayoutAuthoring.cell(f.session.document(), firstCellId).content.id);
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), secondCellId).content.id);
            assertTrue(f.document.editSession().redo());
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), firstCellId).content.id);

            Vector2 current = f.session.materializedHud().actor("first")
                    .localToStageCoordinates(new Vector2(10f, 10f));
            int afterSwap = f.document.editSession().historySize();
            stageTouchDown(stage, f.session, current.x, current.y);
            stageTouchDragged(stage, f.session, current.x + 8f, current.y);
            stageTouchUp(stage, f.session, current.x + 8f, current.y);
            assertEquals(afterSwap, f.document.editSession().historySize());
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), firstCellId).content.id);

            stageTouchDown(stage, f.session, current.x, current.y);
            stageTouchDragged(stage, f.session, 120f, 50f);
            stageTouchUp(stage, f.session, 120f, 50f);
            assertEquals(afterSwap, f.document.editSession().historySize());

            Actor panel = new Actor();
            panel.setBounds(moveFrom.x - 10f, moveFrom.y - 10f, 20f, 20f);
            stage.addActor(panel);
            stageTouchDown(stage, f.session, current.x, current.y);
            stageTouchDragged(stage, f.session, moveFrom.x, moveFrom.y);
            stageTouchUp(stage, f.session, moveFrom.x, moveFrom.y);
            assertEquals(afterSwap, f.document.editSession().historySize());
            panel.remove();

            stageTouchDown(stage, f.session, current.x, current.y);
            stageTouchDragged(stage, f.session, moveFrom.x, moveFrom.y);
            f.document.editSession().edit("Change cell minimum", candidate -> {
                HudLayoutAuthoring.cell(candidate, firstCellId).constraints.minWidth = 46f;
                return candidate;
            });
            stageTouchUp(stage, f.session, moveFrom.x, moveFrom.y);
            assertEquals(afterSwap + 1, f.document.editSession().historySize());
            assertEquals("first", HudLayoutAuthoring.cell(f.session.document(), firstCellId).content.id);
        } finally {
            stage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void stageDragUsesNativeContentCellsOfWindowAndDialog() throws Exception {
        if (!com.kotcrab.vis.ui.VisUI.isLoaded()) com.kotcrab.vis.ui.VisUI.load();
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(200, 200);
        try {
            for (HudNodeKind kind : List.of(HudNodeKind.WINDOW, HudNodeKind.DIALOG)) {
                if (openSession != null) { openSession.dispose(); openSession = null; }
                HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 200f, 200f);
                HudNode owner = sized(new HudNode("owner", kind), 150f, 150f);
                if (kind == HudNodeKind.WINDOW) owner.window = new HudWindowData();
                else owner.dialog = new HudDialogData();
                owner.table = HudLayoutAuthoring.newTableLayout(root, 1, 2, false);
                var source = owner.table.rows.get(0).cells.get(0);
                var destination = owner.table.rows.get(0).cells.get(1);
                source.constraints.minWidth = destination.constraints.minWidth = 30f;
                source.constraints.minHeight = destination.constraints.minHeight = 30f;
                source.content = sized(new HudNode("widget", HudNodeKind.GROUP), 20f, 20f);
                HudFreePlacement placement = new HudFreePlacement();
                placement.offsetX = 25f;
                placement.offsetY = 25f;
                root.children.add(HudChild.free(owner, placement));
                String id = "hud/cell-" + kind.name().toLowerCase();
                HudScreenAsset asset = new HudScreenAsset();
                asset.referenceWidth = 200;
                asset.referenceHeight = 200;
                asset.documentId = id + ".json";
                HudScreenEditorDocument editor = new HudScreenEditorDocument(id, id, asset,
                        new HudDocumentV1(root));
                Fixture f = open(editor);
                Stage stage = new Stage(new ScreenViewport(), inertBatch());
                try {
                    stage.getViewport().update(200, 200, true);
                    HudCanvasInputHost host = new HudCanvasInputHost(f.session);
                    host.setBounds(0f, 0f, 200f, 200f);
                    host.activateHudInput();
                    stage.addActor(host);
                    f.session.configurePreview(new Rectangle(0f, 0f, 200f, 200f));
                    f.session.selectNode("widget");
                    ((com.badlogic.gdx.scenes.scene2d.utils.Layout)
                            f.session.materializedHud().actor("owner")).validate();
                    Vector2 from = f.session.materializedHud().actor("widget")
                            .localToStageCoordinates(new Vector2(10f, 10f));
                    Rectangle target = HudOverlayGeometry.visibleCellBoundsInOverlay(
                            f.session.materializedHud().cell(destination.id),
                            f.session.materializedHud().actor("owner"), new Rectangle());
                    Vector2 to = f.session.materializedHud().actor("owner").localToStageCoordinates(
                            new Vector2(target.x + target.width * 0.5f,
                                    target.y + target.height * 0.5f));
                    stageTouchDown(stage, f.session, from.x, from.y);
                    assertTrue(kind.name(), f.session.hasCellContentGesture());
                    stageTouchDragged(stage, f.session, to.x, to.y);
                    stageTouchUp(stage, f.session, to.x, to.y);
                    assertNull(kind.name(), HudLayoutAuthoring.cell(f.session.document(), source.id).content);
                    assertEquals(kind.name(), "widget",
                            HudLayoutAuthoring.cell(f.session.document(), destination.id).content.id);
                } finally {
                    stage.dispose();
                }
            }
        } finally {
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void stageDragRejectsAnotherTableWithoutChangingHistory() throws Exception {
        if (!com.kotcrab.vis.ui.VisUI.isLoaded()) com.kotcrab.vis.ui.VisUI.load();
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(100, 100);
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode sourceTable = sized(new HudNode("source-table", HudNodeKind.TABLE), 40f, 40f);
        HudNode otherTable = sized(new HudNode("other-table", HudNodeKind.TABLE), 40f, 40f);
        sourceTable.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        otherTable.table = HudLayoutAuthoring.newTableLayout(sourceTable, 1, 1, false);
        sourceTable.table.rows.get(0).cells.get(0).constraints.minWidth = 30f;
        sourceTable.table.rows.get(0).cells.get(0).constraints.minHeight = 30f;
        otherTable.table.rows.get(0).cells.get(0).constraints.minWidth = 30f;
        otherTable.table.rows.get(0).cells.get(0).constraints.minHeight = 30f;
        sourceTable.table.rows.get(0).cells.get(0).content =
                sized(new HudNode("widget", HudNodeKind.GROUP), 20f, 20f);
        root.children.add(HudChild.free(sourceTable, new HudFreePlacement()));
        HudFreePlacement otherPlacement = new HudFreePlacement();
        otherPlacement.offsetX = 50f;
        root.children.add(HudChild.free(otherTable, otherPlacement));
        Fixture f = open(new HudDocumentV1(root), "hud/inter-table-rejection");
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(100, 100, true);
            HudCanvasInputHost host = new HudCanvasInputHost(f.session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            f.session.selectNode("widget");
            Vector2 from = f.session.materializedHud().actor("widget")
                    .localToStageCoordinates(new Vector2(10f, 10f));
            Vector2 to = f.session.materializedHud().actor("other-table")
                    .localToStageCoordinates(new Vector2(20f, 20f));
            int history = f.document.editSession().historySize();
            stageTouchDown(stage, f.session, from.x, from.y);
            stageTouchDragged(stage, f.session, to.x, to.y);
            stageTouchUp(stage, f.session, to.x, to.y);
            assertEquals(history, f.document.editSession().historySize());
            assertEquals("widget", HudLayoutAuthoring.cell(f.session.document(),
                    sourceTable.table.rows.get(0).cells.get(0).id).content.id);
            assertNull(HudLayoutAuthoring.cell(f.session.document(),
                    otherTable.table.rows.get(0).cells.get(0).id).content);
        } finally {
            stage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void stageDragMovesLayoutSubtreeOnlyFromItsOwnSurface() throws Exception {
        if (!com.kotcrab.vis.ui.VisUI.isLoaded()) com.kotcrab.vis.ui.VisUI.load();
        Graphics previousGraphics = Gdx.graphics;
        Gdx.graphics = logicalGraphics(100, 100);
        HudNode table = sized(new HudNode("table", HudNodeKind.TABLE), 100f, 60f);
        table.table = HudLayoutAuthoring.newTableLayout(table, 1, 2, false);
        var source = table.table.rows.get(0).cells.get(0);
        var destination = table.table.rows.get(0).cells.get(1);
        source.constraints.minWidth = destination.constraints.minWidth = 45f;
        source.constraints.minHeight = destination.constraints.minHeight = 40f;
        HudNode layout = sized(new HudNode("layout", HudNodeKind.GROUP), 40f, 30f);
        layout.children.add(HudChild.free(sized(new HudNode("leaf", HudNodeKind.GROUP),
                10f, 10f), new HudFreePlacement()));
        source.content = layout;
        Fixture f = open(new HudDocumentV1(table), "hud/layout-cell-move");
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(100, 100, true);
            HudCanvasInputHost host = new HudCanvasInputHost(f.session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            f.session.selectNode("layout");
            Vector2 child = f.session.materializedHud().actor("leaf")
                    .localToStageCoordinates(new Vector2(5f, 5f));
            Vector2 parent = f.session.materializedHud().actor("layout")
                    .localToStageCoordinates(new Vector2(30f, 20f));
            Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(
                    f.session.materializedHud().cell(destination.id),
                    f.session.materializedHud().actor("table"), new Rectangle());
            Vector2 to = f.session.materializedHud().actor("table").localToStageCoordinates(
                    new Vector2(bounds.x + bounds.width * 0.5f,
                            bounds.y + bounds.height * 0.5f));
            int history = f.document.editSession().historySize();
            stageTouchDown(stage, f.session, child.x, child.y);
            assertFalse(f.session.hasCellContentGesture());
            stageTouchDragged(stage, f.session, to.x, to.y);
            stageTouchUp(stage, f.session, to.x, to.y);
            assertEquals(history, f.document.editSession().historySize());
            f.session.selectNode("layout");

            stageTouchDown(stage, f.session, parent.x, parent.y);
            assertTrue(f.session.hasCellContentGesture());
            stageTouchDragged(stage, f.session, to.x, to.y);
            stageTouchUp(stage, f.session, to.x, to.y);
            assertNull(HudLayoutAuthoring.cell(f.session.document(), source.id).content);
            HudNode moved = HudLayoutAuthoring.cell(f.session.document(), destination.id).content;
            assertEquals("layout", moved.id);
            assertEquals("leaf", moved.children.get(0).node.id);
            assertEquals(history + 1, f.document.editSession().historySize());
        } finally {
            stage.dispose();
            Gdx.graphics = previousGraphics;
        }
    }

    @Test public void shiftClickSelectsNativeCellRangeWithoutStartingWidgetDrag() throws Exception {
        Graphics previousGraphics = Gdx.graphics;
        Input previousInput = Gdx.input;
        Gdx.graphics = logicalGraphics(100, 100);
        HudNode table = sized(new HudNode("table", HudNodeKind.TABLE), 100f, 100f);
        table.table = HudLayoutAuthoring.newTableLayout(table, 2, 3, false);
        for (var row : table.table.rows) for (var cell : row.cells) {
            cell.constraints.minWidth = 30f;
            cell.constraints.minHeight = 40f;
        }
        String first = table.table.rows.get(0).cells.get(0).id;
        String middle = table.table.rows.get(0).cells.get(1).id;
        String last = table.table.rows.get(0).cells.get(2).id;
        Fixture f = open(new HudDocumentV1(table), "hud/shift-range");
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        try {
            stage.getViewport().update(100, 100, true);
            HudCanvasInputHost host = new HudCanvasInputHost(f.session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            f.session.selectCell(last);
            Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(
                    f.session.materializedHud().cell(first),
                    f.session.materializedHud().actor("table"), new Rectangle());
            Vector2 point = f.session.materializedHud().actor("table").localToStageCoordinates(
                    new Vector2(bounds.x + bounds.width * 0.5f,
                            bounds.y + bounds.height * 0.5f));
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(),
                    new Class<?>[]{Input.class}, (proxy, method, args) -> {
                        if (method.getName().equals("isKeyPressed"))
                            return (int) args[0] == Input.Keys.SHIFT_LEFT;
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) return false;
                        if (type == int.class) return 0;
                        if (type == float.class) return 0f;
                        return null;
                    });
            stageTouchDown(stage, f.session, point.x, point.y);
            stageTouchUp(stage, f.session, point.x, point.y);
            assertEquals(List.of(first, middle, last), f.session.selectedCellRange());

            assertEquals(last, f.session.selectedCellId());
            assertFalse(f.session.hasCellContentGesture());
            assertEquals(0, f.document.editSession().historySize());
            assertTrue(f.session.editSelectedTableStructure(
                    HudLayoutAuthoring.StructureAction.MERGE, 2));
            assertEquals(3, HudLayoutAuthoring.cell(f.session.document(), first).colspan);
            assertTrue(f.document.editSession().undo());
            assertEquals(last, f.session.selectedCellId());
            assertEquals(List.of(first, middle, last), f.session.selectedCellRange());

            String otherRow = table.table.rows.get(1).cells.get(1).id;
            Rectangle otherBounds = HudOverlayGeometry.visibleCellBoundsInOverlay(
                    f.session.materializedHud().cell(otherRow),
                    f.session.materializedHud().actor("table"), new Rectangle());
            Vector2 otherPoint = f.session.materializedHud().actor("table").localToStageCoordinates(
                    new Vector2(otherBounds.x + otherBounds.width * 0.5f,
                            otherBounds.y + otherBounds.height * 0.5f));
            stageTouchDown(stage, f.session, otherPoint.x, otherPoint.y);
            stageTouchUp(stage, f.session, otherPoint.x, otherPoint.y);
            assertEquals(otherRow, f.session.selectedCellId());
            assertEquals(List.of(otherRow), f.session.selectedCellRange());

            f.session.selectCell(last);
            f.session.hudCamera().zoom = 1.5f;
            f.session.hudCamera().position.set(55f, 50f, 0f);
            f.session.hudCamera().update();
            stageTouchDown(stage, f.session, point.x, point.y);
            stageTouchUp(stage, f.session, point.x, point.y);
            assertEquals(List.of(first, middle, last), f.session.selectedCellRange());
        } finally {
            stage.dispose();
            Gdx.input = previousInput;
            Gdx.graphics = previousGraphics;
        }
    }

    private Fixture open(HudDocumentV1 document, String id, AssetMetaDatabase database)
            throws Exception {
        return open(document(id, document), database);
    }

    private Fixture open(HudScreenEditorDocument editor, AssetMetaDatabase database) throws Exception {
        openSession = new HudEditorSession(() -> database, HudSelectionOverlayTest::inertBatch);
        openSession.open(Gdx.files.absolute(temporary.getRoot().getAbsolutePath()), editor);
        assertEquals(HudEditorSession.Status.READY, openSession.status());
        assertTrue(openSession.configurePreview(new Rectangle(0f, 0f, 100f, 100f)));
        return new Fixture(openSession, editor);
    }

    private AssetMetaDatabase imageDatabase(String resourceName, int width, int height) {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta image = database.registerIfAbsent(AssetType.IMAGE, "images/filled",
                "orig/images/" + resourceName + ".png", AssetMeta.AssetScope.USER);
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixels.setColor(1f, 1f, 1f, 1f);
            pixels.fill();
            var source = Gdx.files.absolute(temporary.getRoot().getAbsolutePath())
                    .child(image.sourceRelPath());
            source.parent().mkdirs();
            PixmapIO.writePNG(source, pixels);
        } finally {
            pixels.dispose();
        }
        return database;
    }

    private static HudScreenEditorDocument document(String id, HudDocumentV1 document) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.referenceWidth = 100;
        asset.referenceHeight = 100;
        asset.documentId = id + ".json";
        return new HudScreenEditorDocument(id, id, asset, document);
    }

    private static HudDocumentV1 nestedDocument() {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode child = sized(new HudNode("child", HudNodeKind.GROUP), 20f, 20f);
        HudNode grandchild = sized(new HudNode("grandchild", HudNodeKind.GROUP), 5f, 5f);
        HudFreePlacement childPlacement = new HudFreePlacement();
        childPlacement.offsetX = 10f;
        childPlacement.offsetY = 10f;
        HudFreePlacement grandchildPlacement = new HudFreePlacement();
        grandchildPlacement.offsetX = 2f;
        grandchildPlacement.offsetY = 3f;
        child.children.add(HudChild.free(grandchild, grandchildPlacement));
        root.children.add(HudChild.free(child, childPlacement));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 tableDocument() {
        HudNode table = sized(new HudNode("table", HudNodeKind.TABLE), 100f, 100f);
        HudNode child = sized(new HudNode("cell-child", HudNodeKind.GROUP), 8f, 7f);
        HudCellConstraints constraints = new HudCellConstraints();
        constraints.prefWidth = 30f;
        constraints.prefHeight = 20f;
        tableCell(table, child, constraints);
        return new HudDocumentV1(table);
    }

    private static HudDocumentV1 nestedTableDocument() {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode table = sized(new HudNode("table", HudNodeKind.TABLE), 100f, 100f);
        HudNode child = sized(new HudNode("cell-child", HudNodeKind.GROUP), 8f, 7f);
        HudCellConstraints constraints = new HudCellConstraints();
        constraints.prefWidth = 30f;
        constraints.prefHeight = 20f;
        tableCell(table, child, constraints);
        root.children.add(HudChild.free(table, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 twoChildrenDocument() {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode left = sized(new HudNode("left", HudNodeKind.GROUP), 20f, 20f);
        HudNode right = sized(new HudNode("right", HudNodeKind.GROUP), 20f, 20f);
        HudFreePlacement leftPlacement = new HudFreePlacement();
        leftPlacement.offsetX = 10f;
        leftPlacement.offsetY = 10f;
        HudFreePlacement rightPlacement = new HudFreePlacement();
        rightPlacement.offsetX = 40f;
        rightPlacement.offsetY = 10f;
        root.children.add(HudChild.free(left, leftPlacement));
        root.children.add(HudChild.free(right, rightPlacement));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 directDocument() {
        HudNode root = sized(new HudNode("direct-root", HudNodeKind.GROUP), 100f, 100f);
        HudNode container = sized(new HudNode("direct-container", HudNodeKind.CONTAINER), 100f, 100f);
        container.container = new games.pixscape.runtime.hud.document.HudContainerData();
        HudNode child = sized(new HudNode("direct-child", HudNodeKind.GROUP), 20f, 20f);
        container.children.add(HudChild.direct(child));
        root.children.add(HudChild.free(container, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 scrolledPaneDocument() {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode pane = sized(new HudNode("pane", HudNodeKind.SCROLL_PANE), 60f, 60f);
        pane.scrollPane = new HudScrollPaneData();
        HudNode content = sized(new HudNode("content", HudNodeKind.GROUP), 58f, 160f);
        HudNode lower = sized(new HudNode("lower-table", HudNodeKind.TABLE), 40f, 20f);
        HudNode upper = sized(new HudNode("upper-table", HudNodeKind.TABLE), 40f, 20f);
        emptyTable(lower);
        emptyTable(upper);
        lower.table.rows.get(0).cells.get(0).id = "cell-lower";
        upper.table.rows.get(0).cells.get(0).id = "cell-upper";
        HudFreePlacement lowerPlacement = new HudFreePlacement();
        lowerPlacement.offsetX = 5f;
        lowerPlacement.offsetY = 5f;
        HudFreePlacement upperPlacement = new HudFreePlacement();
        upperPlacement.offsetX = 5f;
        upperPlacement.offsetY = 130f;
        content.children.add(HudChild.free(lower, lowerPlacement));
        content.children.add(HudChild.free(upper, upperPlacement));
        pane.children.add(HudChild.direct(content));
        HudFreePlacement panePlacement = new HudFreePlacement();
        panePlacement.offsetX = 10f;
        panePlacement.offsetY = 10f;
        root.children.add(HudChild.free(pane, panePlacement));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 emptyPaneDocument() {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode pane = sized(new HudNode("pane", HudNodeKind.SCROLL_PANE), 60f, 60f);
        pane.scrollPane = new HudScrollPaneData();
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = 10f;
        placement.offsetY = 10f;
        root.children.add(HudChild.free(pane, placement));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 occupiedPaneDocument(boolean layoutContent) {
        HudDocumentV1 document = emptyPaneDocument();
        HudNode pane = document.root.children.get(0).node;
        HudNode content = sized(new HudNode("content",
                layoutContent ? HudNodeKind.GROUP : HudNodeKind.LABEL), 58f, 58f);
        if (!layoutContent) {
            content.label = new games.pixscape.runtime.hud.document.HudLabelData();
            content.label.text = "Occupied";
        }
        pane.children.add(HudChild.direct(content));
        return document;
    }

    private static HudDocumentV1 occupiedImageContainersDocument() {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);

        HudNode table = sized(new HudNode("table", HudNodeKind.TABLE), 40f, 30f);
        HudNode tableImage = image("table-image", "filled__a1", 40f, 30f);
        HudCellConstraints cell = new HudCellConstraints();
        cell.prefWidth = 40f;
        cell.prefHeight = 30f;
        cell.fillX = true;
        cell.fillY = true;
        cell.expandX = true;
        cell.expandY = true;
        tableCell(table, tableImage, cell);
        HudFreePlacement tablePlacement = new HudFreePlacement();
        tablePlacement.offsetX = 10f;
        tablePlacement.offsetY = 10f;
        root.children.add(HudChild.free(table, tablePlacement));

        HudNode container = sized(new HudNode("container", HudNodeKind.CONTAINER), 30f, 30f);
        container.container = new games.pixscape.runtime.hud.document.HudContainerData();
        container.children.add(HudChild.direct(
                image("container-image", "filled__a1", 30f, 30f)));
        HudFreePlacement containerPlacement = new HudFreePlacement();
        containerPlacement.offsetX = 60f;
        containerPlacement.offsetY = 10f;
        root.children.add(HudChild.free(container, containerPlacement));
        return new HudDocumentV1(root);
    }

    private static HudNode image(String id, String resourceName, float width, float height) {
        HudNode image = sized(new HudNode(id, HudNodeKind.IMAGE), width, height);
        image.image = new HudImageData();
        image.image.source = HudImageSource.REGION;
        image.image.resourceName = resourceName;
        return image;
    }

    private static HudDocumentV1 deletableDocument() {
        HudNode root = sized(new HudNode("root", HudNodeKind.GROUP), 100f, 100f);
        HudNode before = sized(new HudNode("before", HudNodeKind.GROUP), 10f, 10f);
        HudNode victim = sized(new HudNode("victim", HudNodeKind.TABLE), 30f, 25f);
        HudNode stack = sized(new HudNode("stack", HudNodeKind.STACK), 20f, 15f);
        HudNode leaf = sized(new HudNode("leaf", HudNodeKind.GROUP), 5f, 4f);
        HudNode after = sized(new HudNode("after", HudNodeKind.GROUP), 10f, 10f);
        HudFreePlacement victimPlacement = new HudFreePlacement();
        victimPlacement.offsetX = 31f;
        victimPlacement.offsetY = 17f;
        HudCellConstraints cell = new HudCellConstraints();
        cell.prefWidth = 42f;
        cell.prefHeight = 19f;
        stack.children.add(HudChild.direct(leaf));
        tableCell(victim, stack, cell);
        root.children.add(HudChild.free(before, new HudFreePlacement()));
        root.children.add(HudChild.free(victim, victimPlacement));
        root.children.add(HudChild.free(after, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudNode sized(HudNode node, float width, float height) {
        node.actor.width = width;
        node.actor.height = height;
        return node;
    }

    private static void emptyTable(HudNode node) {
        node.table = HudLayoutAuthoring.newTableLayout(node, 1, 1, false);
    }

    private static void tableCell(HudNode node, HudNode content, HudCellConstraints constraints) {
        emptyTable(node);
        node.table.rows.get(0).cells.get(0).content = content;
        node.table.rows.get(0).cells.get(0).constraints = constraints;
    }

    private static void assertOccupiedParentMove(Stage studioStage, Fixture fixture,
                                                 String parentId, String imageId,
                                                 float hudX, float hudY) {
        fixture.session.selectNode(parentId);
        HudChild parentBefore = relation(fixture.session.document(), parentId);
        float initialX = parentBefore.free.offsetX;
        HudChild imageBefore = relation(fixture.session.document(), imageId);
        HudPlacementKind imagePlacement = imageBefore.placementKind;
        float imagePrefWidth = imageBefore.cell != null ? imageBefore.cell.prefWidth : Float.NaN;
        float imagePrefHeight = imageBefore.cell != null ? imageBefore.cell.prefHeight : Float.NaN;
        int history = fixture.document.editSession().historySize();
        assertEquals("the occupied child must own the press surface", imageId,
                fixture.session.overlayTargetAt(hudX, hudY).nodeId());

        stageTouchDown(studioStage, fixture.session, hudX, hudY);
        assertTrue(fixture.session.hasPendingMoveGesture());
        assertFalse(fixture.session.hasActiveTransformGesture());
        assertEquals(parentId, fixture.session.selectedNodeId());

        stageTouchDragged(studioStage, fixture.session, hudX + 2f, hudY);
        assertTrue("sub-threshold motion must remain pending",
                fixture.session.hasPendingMoveGesture());
        assertEquals(initialX, relation(fixture.session.document(), parentId).free.offsetX, 0.001f);

        stageTouchDragged(studioStage, fixture.session, hudX + 10f, hudY);
        assertTrue(fixture.session.hasActiveTransformGesture());
        assertFalse(fixture.session.hasPendingMoveGesture());
        assertEquals(parentId, fixture.session.selectedNodeId());
        stageTouchUp(studioStage, fixture.session, hudX + 10f, hudY);

        assertFalse(fixture.session.hasActiveTransformGesture());
        assertEquals(parentId, fixture.session.selectedNodeId());
        assertEquals(initialX + 10f,
                relation(fixture.session.document(), parentId).free.offsetX, 0.001f);
        assertEquals(imagePlacement,
                relation(fixture.session.document(), imageId).placementKind);
        if (imageBefore.cell != null) {
            assertEquals(imagePrefWidth,
                    relation(fixture.session.document(), imageId).cell.prefWidth, 0.001f);
            assertEquals(imagePrefHeight,
                    relation(fixture.session.document(), imageId).cell.prefHeight, 0.001f);
        }
        assertEquals(history + 1, fixture.document.editSession().historySize());

        assertTrue(fixture.document.editSession().undo());
        assertEquals(initialX, relation(fixture.session.document(), parentId).free.offsetX, 0.001f);
        assertEquals(parentId, fixture.session.selectedNodeId());
        assertEquals(imagePlacement,
                relation(fixture.session.document(), imageId).placementKind);
        assertTrue(fixture.document.editSession().redo());
        assertEquals(initialX + 10f,
                relation(fixture.session.document(), parentId).free.offsetX, 0.001f);
        assertEquals(parentId, fixture.session.selectedNodeId());
    }

    private static HudChild relation(HudDocumentV1 document, String nodeId) {
        HudChild relation = findRelation(document.root, nodeId);
        assertNotNull("Missing relation for " + nodeId, relation);
        return relation;
    }

    private static HudChild findRelation(HudNode parent, String nodeId) {
        if (parent == null) return null;
        for (HudChild child : parent.children) {
            if (nodeId.equals(child.node.id)) return child;
            HudChild nested = findRelation(child.node, nodeId);
            if (nested != null) return nested;
        }
        if (parent.table != null) for (games.pixscape.runtime.hud.document.HudTableRow row : parent.table.rows)
            for (games.pixscape.runtime.hud.document.HudTableCell cell : row.cells)
                if (cell.content != null) {
                    if (nodeId.equals(cell.content.id)) return HudChild.cell(cell.content, cell.constraints);
                    HudChild nested = findRelation(cell.content, nodeId);
                    if (nested != null) return nested;
                }
        return null;
    }

    private static void stageTouchDown(Stage stage, HudEditorSession session,
                                       float hudX, float hudY) {
        Vector2 point = session.hudViewport().project(new Vector2(hudX, hudY));
        stage.touchDown(Math.round(point.x), Math.round(Gdx.graphics.getHeight() - point.y),
                0, Input.Buttons.LEFT);
    }

    private static void stageTouchDragged(Stage stage, HudEditorSession session,
                                          float hudX, float hudY) {
        Vector2 point = session.hudViewport().project(new Vector2(hudX, hudY));
        stage.touchDragged(Math.round(point.x), Math.round(Gdx.graphics.getHeight() - point.y), 0);
    }

    private static void stageTouchUp(Stage stage, HudEditorSession session,
                                     float hudX, float hudY) {
        Vector2 point = session.hudViewport().project(new Vector2(hudX, hudY));
        stage.touchUp(Math.round(point.x), Math.round(Gdx.graphics.getHeight() - point.y),
                0, Input.Buttons.LEFT);
    }

    private static HudSelectionTarget target(List<HudSelectionTarget> targets, String nodeId) {
        return targets.stream().filter(target -> target.nodeId().equals(nodeId)).findFirst().orElse(null);
    }

    private static InputEvent eventAt(float x, float y) {
        InputEvent event = new InputEvent();
        event.setStageX(x);
        event.setStageY(y);
        return event;
    }

    private static void assertStageClickSelects(Stage studioStage, HudEditorSession session,
                                                float hudX, float hudY, String expectedNodeId) {
        Vector2 stagePoint = session.hudViewport().project(new Vector2(hudX, hudY));
        studioStage.touchDown(Math.round(stagePoint.x), Math.round(1080f - stagePoint.y),
                0, Input.Buttons.LEFT);
        studioStage.touchUp(Math.round(stagePoint.x), Math.round(1080f - stagePoint.y),
                0, Input.Buttons.LEFT);
        assertEquals(expectedNodeId, session.selectedNodeId());
    }

    private static HudNode find(HudNode node, String nodeId) {
        if (node == null) return null;
        if (nodeId.equals(node.id)) return node;
        for (HudChild child : node.children) {
            HudNode found = find(child.node, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private static void assertStageHandleStartsResize(Stage studioStage, HudEditorSession session,
                                                      float hudX, float hudY,
                                                      HudTransformHandle expected) {
        Vector2 stagePoint = session.hudViewport().project(new Vector2(hudX, hudY));
        Vector2 restored = new Vector2();
        assertTrue(session.hudPointAt(stagePoint.x, stagePoint.y, restored));
        assertEquals(hudX, restored.x, 0.001f);
        assertEquals(hudY, restored.y, 0.001f);
        int rawX = Math.round(stagePoint.x);
        int rawY = Math.round(1080f - stagePoint.y);
        studioStage.mouseMoved(rawX, rawY);
        assertEquals(expected, session.transformHandleAt(stagePoint.x, stagePoint.y));
        studioStage.touchDown(rawX, rawY, 0, Input.Buttons.LEFT);
        assertTrue(session.hasActiveResizeGesture());
        studioStage.touchUp(rawX, rawY, 0, Input.Buttons.LEFT);
        assertFalse(session.hasActiveTransformGesture());
    }

    private static void assertDisplayedSideHandles(Stage studioStage, HudEditorSession session) {
        assertStageHandleStartsResize(studioStage, session, 850f, 480f, HudTransformHandle.W);
        assertStageHandleStartsResize(studioStage, session, 1050f, 480f, HudTransformHandle.E);
    }

    private static void assertStageEmptySpaceDoesNotSelect(Stage studioStage,
                                                            HudEditorSession session,
                                                            float hudX, float hudY) {
        Vector2 stagePoint = session.hudViewport().project(new Vector2(hudX, hudY));
        int rawX = Math.round(stagePoint.x);
        int rawY = Math.round(1080f - stagePoint.y);
        studioStage.mouseMoved(rawX, rawY);
        assertNull(session.hoveredSelectionTarget());
        studioStage.touchDown(rawX, rawY, 0, Input.Buttons.LEFT);
        studioStage.touchUp(rawX, rawY, 0, Input.Buttons.LEFT);
        assertEquals("root", session.selectedNodeId());
    }

    private static void assertTarget(List<HudSelectionTarget> targets, String nodeId,
                                     HudSelectionTarget.Type type, boolean selected) {
        HudSelectionTarget target = target(targets, nodeId);
        assertTrue("Missing target " + nodeId, target != null);
        assertEquals(type, target.type());
        assertEquals(selected, target.selected());
    }

    private static void assertVisual(HudEditorSession session, HudSelectionTarget target,
                                     HudSelectionOverlay.VisualState expected) {
        assertEquals(expected, session.selectionVisualState(target));
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

    private static Graphics logicalGraphics(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class[]{Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> width;
                    case "getHeight", "getBackBufferHeight" -> height;
                    case "getDeltaTime" -> 1f / 60f;
                    default -> primitiveDefault(method.getReturnType());
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private record Fixture(HudEditorSession session, HudScreenEditorDocument document) {}
}
