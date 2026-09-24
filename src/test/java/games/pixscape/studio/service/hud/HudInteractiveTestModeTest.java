package games.pixscape.studio.service.hud;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Tooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.MaterializedHud;
import games.pixscape.runtime.hud.HudDialog;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudListData;
import games.pixscape.runtime.hud.document.HudTextButtonData;
import games.pixscape.runtime.hud.document.HudTooltipData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.runtime.hud.document.HudDialogResultButton;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudWindowAction;
import games.pixscape.runtime.hud.document.HudWindowActionKind;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.ui.hud.HudTestInputRouter;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

public class HudInteractiveTestModeTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getWidth", "getHeight", "getBackBufferWidth", "getBackBufferHeight" -> 800;
                        case "getDeltaTime" -> 1f / 60f;
                        case "getFramesPerSecond" -> 60;
                        default -> {
                            Class<?> type = method.getReturnType();
                            if (type == boolean.class) yield false;
                            if (type == int.class) yield 0;
                            if (type == float.class) yield 0f;
                            yield null;
                        }
                    };
                });
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

    @Test public void freshNativeInstanceUsesUnsavedDocumentAndCannotMutateAuthoringState()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("project"));
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode button = new HudNode("play", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Unsaved caption";
        root.children.add(HudChild.direct(button));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(), new HudDocumentV1(root));
        document.setSelectedNodeId("play");
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(10f, 20f, 800f, 600f)));
            String authored = new HudDocumentCodec().write(document.document());
            int history = document.editSession().historySize();
            boolean dirty = document.isDirty();

            assertTrue(session.enterTestMode());
            Actor candidate = session.testActor("play");
            assertTrue(candidate instanceof TextButton);
            assertEquals("Unsaved caption", ((TextButton) candidate).getText().toString());
            candidate.setVisible(false);
            candidate.setPosition(73f, 91f);

            assertFalse(session.editSelectedNode("Forbidden", (node, relation) ->
                    node.textButton.text = "Mutated"));
            assertFalse(session.deleteSelectedNode());
            assertEquals(authored, new HudDocumentCodec().write(document.document()));
            assertEquals(history, document.editSession().historySize());
            assertEquals(dirty, document.isDirty());

            session.exitTestMode();
            assertFalse(session.isTestMode());
            assertNull(session.testActor("play"));
            assertEquals("play", session.selectedNodeId());

            assertTrue(session.enterTestMode());
            assertTrue(session.testActor("play").isVisible());
            assertNotSame(candidate, session.testActor("play"));
        } finally {
            session.dispose();
        }
    }

    @Test public void testModeFollowsCanvasResizeWithoutScalingFreeWidgets() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("resize-mode"));
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode badge = new HudNode("badge", HudNodeKind.GROUP);
        badge.actor.width = 50f;
        badge.actor.height = 20f;
        HudFreePlacement placement = new HudFreePlacement();
        placement.horizontalAnchor = games.pixscape.runtime.hud.document.HudHorizontalAnchor.RIGHT;
        placement.verticalAnchor = games.pixscape.runtime.hud.document.HudVerticalAnchor.TOP;
        placement.pivotX = 1f;
        placement.pivotY = 1f;
        placement.offsetX = -10f;
        placement.offsetY = -10f;
        root.children.add(HudChild.free(badge, placement));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/resize", "Resize", asset(), new HudDocumentV1(root));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(20f, 30f, 800f, 600f)));
            assertTrue(session.enterTestMode());
            assertEquals(800f, session.testActor("root").getWidth(), .001f);
            assertEquals(740f, session.testActor("badge").getX(), .001f);
            assertEquals(50f, session.testActor("badge").getWidth(), .001f);

            assertTrue(session.configurePreview(new Rectangle(20f, 30f, 400f, 300f)));
            assertEquals(400f, session.testActor("root").getWidth(), .001f);
            assertEquals(340f, session.testActor("badge").getX(), .001f);
            assertEquals(270f, session.testActor("badge").getY(), .001f);
            assertEquals(50f, session.testActor("badge").getWidth(), .001f);
            assertTrue(session.configurePreview(new Rectangle(20f, 30f, 800f, 600f)));
            assertEquals(740f, session.testActor("badge").getX(), .001f);
        } finally {
            session.dispose();
        }
    }

    @Test public void listSelectionAndItemsStayTransientAcrossTestSessions() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("list-mode"));
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode list = new HudNode("list", HudNodeKind.LIST);
        list.list = new HudListData();
        list.list.items.add("First");
        list.list.items.add("Second");
        list.list.selectedIndex = 0;
        root.children.add(HudChild.free(list, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(), new HudDocumentV1(root));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            assertNotNull(session.materializedHud().list("list"));
            String authored = new HudDocumentCodec().write(document.document());
            assertTrue(session.enterTestMode());
            @SuppressWarnings("unchecked") com.badlogic.gdx.scenes.scene2d.ui.List<String> first =
                    (com.badlogic.gdx.scenes.scene2d.ui.List<String>) session.testActor("list");
            first.setSelectedIndex(1);
            first.setItems("Runtime");
            assertEquals(authored, new HudDocumentCodec().write(document.document()));
            session.exitTestMode();
            assertTrue(session.enterTestMode());
            @SuppressWarnings("unchecked") com.badlogic.gdx.scenes.scene2d.ui.List<String> second =
                    (com.badlogic.gdx.scenes.scene2d.ui.List<String>) session.testActor("list");
            assertNotSame(first, second);
            assertEquals(0, second.getSelectedIndex());
            assertEquals("First", second.getItems().first());
        } finally {
            session.dispose();
        }
    }

    @Test public void resultDialogIsEditableInEditAndFreshlyClosedInTest() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("dialog-results"));
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialog = new HudNode("dialog", HudNodeKind.DIALOG);
        dialog.dialog = new HudDialogData();
        dialog.actor.width = 240f;
        dialog.actor.height = 160f;
        emptyTable(dialog);
        HudNode result = new HudNode("confirm", HudNodeKind.TEXT_BUTTON);
        result.textButton = new HudTextButtonData();
        result.textButton.text = "Valider";
        dialog.dialog.resultButtons.add(new HudDialogResultButton(result, "confirm", true));
        root.children.add(HudChild.free(dialog, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            var materializedField = HudEditorSession.class.getDeclaredField("materialized");
            materializedField.setAccessible(true);
            HudDialog editDialog = ((MaterializedHud) materializedField.get(session)).dialog("dialog");
            assertTrue(editDialog.isVisible());
            assertFalse(editDialog.isOpen());
            assertTrue(session.enterTestMode());
            HudDialog testDialog = (HudDialog) session.testActor("dialog");
            assertNotSame(editDialog, testDialog);
            assertFalse(testDialog.isOpen());
            assertFalse(testDialog.isVisible());
            assertFalse(session.addDialogResultButton("dialog", HudNodeKind.TEXT_BUTTON));
            testDialog.open();
            testDialog.validate();
            Actor button = session.testActor("confirm");
            Vector2 point = button.localToStageCoordinates(new Vector2(
                    button.getWidth() * .5f, button.getHeight() * .5f));
            Actor hit = session.testStage().hit(point.x, point.y, true);
            assertTrue("Hit: " + hit, hit == button || hit != null && hit.isDescendantOf(button));
            int x = Math.round(point.x), y = Math.round(800f - point.y);
            assertTrue(session.testStage().touchDown(x, y, 0, 0));
            session.testStage().touchUp(x, y, 0, 0);
            assertFalse(testDialog.isOpen());
            session.exitTestMode();
            assertTrue(editDialog.isVisible());
            assertEquals(0, document.editSession().historySize());
        } finally {
            session.dispose();
        }
    }

    @Test public void dialogIsVisibleInEditAndStartsClosedOnEveryTestSession()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("dialog-mode"));
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        emptyTable(dialogNode);
        dialogNode.actor.width = 240f;
        dialogNode.actor.height = 160f;
        root.children.add(HudChild.free(dialogNode, new HudFreePlacement()));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(), new HudDocumentV1(root));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            Dialog edited = (Dialog) session.materializedHud().actor("dialog");
            assertTrue(edited.isVisible());
            assertFalse(edited.isModal());
            assertFalse(edited.isMovable());
            assertTrue(session.enterTestMode());
            Dialog first = (Dialog) session.testActor("dialog");
            assertTrue(first.isModal());
            assertFalse(first.isVisible());
            assertNotSame(session.testStage().getRoot(), first.getParent());
            assertNull(session.testStage().getKeyboardFocus());
            session.exitTestMode();
            assertTrue(dialogNode.visible);
            assertSame(edited, session.materializedHud().actor("dialog"));
            assertTrue(session.enterTestMode());
            Dialog second = (Dialog) session.testActor("dialog");
            assertNotSame(first, second);
            assertFalse(second.isVisible());
            assertNull(session.testStage().getKeyboardFocus());
            assertEquals(240f, second.getWidth(), 0.01f);
            assertEquals(160f, second.getHeight(), 0.01f);
        } finally {
            session.dispose();
        }
    }

    @Test public void authoredDialogActionsUseProductionTestInputRouter() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("dialog-actions"));
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode opener = new HudNode("opener", HudNodeKind.TEXT_BUTTON);
        opener.textButton = new HudTextButtonData();
        opener.textButton.text = "Open";
        opener.actor.width = 100f;
        opener.actor.height = 40f;
        opener.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.SHOW));
        HudFreePlacement openerPlacement = new HudFreePlacement();
        openerPlacement.offsetX = 300f;
        openerPlacement.offsetY = 300f;
        root.children.add(HudChild.free(opener, openerPlacement));
        HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
        dialogNode.dialog = new HudDialogData();
        emptyTable(dialogNode);
        dialogNode.visible = false;
        dialogNode.actor.width = 220f;
        dialogNode.actor.height = 160f;
        HudNode closer = new HudNode("closer", HudNodeKind.TEXT_BUTTON);
        closer.textButton = new HudTextButtonData();
        closer.textButton.text = "Close";
        closer.windowActions.add(new HudWindowAction("dialog", HudWindowActionKind.HIDE));
        dialogNode.table.rows.get(0).cells.get(0).content = closer;
        HudFreePlacement dialogPlacement = new HudFreePlacement();
        dialogPlacement.offsetX = 300f;
        dialogPlacement.offsetY = 300f;
        root.children.add(HudChild.free(dialogNode, dialogPlacement));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(), new HudDocumentV1(root));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        Stage studio = new Stage(new ScreenViewport(), inertBatch());
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            Dialog edited = (Dialog) session.materializedHud().actor("dialog");
            assertNotNull(edited.getStage());
            assertTrue(edited.isVisible());
            assertFalse(edited.isModal());
            assertEquals(220f, edited.getWidth(), .001f);
            assertEquals(160f, edited.getHeight(), .001f);
            assertTrue(session.enterTestMode());
            studio.getViewport().update(800, 800, true);
            studio.getRoot().setSize(800f, 800f);
            InputMultiplexer inputs = new InputMultiplexer(new HudTestInputRouter(session, studio), studio);
            Dialog dialog = (Dialog) session.testActor("dialog");
            assertFalse(dialog.isVisible());
            clickHud(inputs, session.testStage(), session.testActor("opener"));
            assertSame(session.testStage().getRoot(), dialog.getParent());
            dialog.validate();
            clickHud(inputs, session.testStage(), session.testActor("closer"));
            assertNull(dialog.getStage());
            assertFalse(dialogNode.visible);
            clickHud(inputs, session.testStage(), session.testActor("opener"));
            assertSame(session.testStage().getRoot(), dialog.getParent());
            session.exitTestMode();
            assertSame(edited, session.materializedHud().actor("dialog"));
            assertTrue(edited.isVisible());
            assertNotNull(edited.getStage());
            session.selectNode("dialog");
            assertTrue(session.isShowingTransformGizmo());
            assertTrue(session.enterTestMode());
            assertFalse(((Dialog) session.testActor("dialog")).isVisible());
        } finally {
            studio.dispose();
            session.dispose();
        }
    }

    private static void clickHud(InputMultiplexer inputs, Stage stage, Actor actor) {
        Vector2 point = actor.localToStageCoordinates(new Vector2(
                actor.getWidth() * .5f, actor.getHeight() * .5f));
        stage.getViewport().project(point);
        int x = Math.round(point.x);
        int y = Gdx.graphics.getHeight() - Math.round(point.y);
        assertTrue(inputs.touchDown(x, y, 0, 0));
        inputs.touchUp(x, y, 0, 0);
    }

    @Test public void resourceInvalidationAndDocumentSuspensionDestroyTheTestStage() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("lifecycle"));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(),
                new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.enterTestMode());
            assertNotNull(session.testStage());
            session.markResourcesStale();
            assertFalse(session.isTestMode());

            session.reloadResources();
            assertTrue(session.enterTestMode());
            session.suspend();
            assertFalse(session.isTestMode());
            assertNull(session.testStage());
        } finally {
            session.dispose();
        }
    }

    @Test public void studioOverlayWinsNewInputAndCapturedHudDragEndsOutsideCanvas() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("input"));
        HudNode button = new HudNode("play", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Play";
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", asset(), new HudDocumentV1(button));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        Stage studio = new Stage(new ScreenViewport(), inertBatch());
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            assertTrue(session.enterTestMode());
            TextButton testedButton = (TextButton) session.testActor("play");
            testedButton.setTouchable(Touchable.enabled);
            testedButton.setBounds(350f, 250f, 120f, 100f);
            int[] hudDown = {0};
            int[] hudUp = {0};
            testedButton.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y,
                                                   int pointer, int mouseButton) {
                    hudDown[0]++;
                    return true;
                }
                @Override public void touchUp(InputEvent event, float x, float y,
                                              int pointer, int mouseButton) {
                    hudUp[0]++;
                }
            });

            studio.getViewport().update(800, 600, true);
            studio.getRoot().setSize(800f, 600f);
            Actor overlay = new Actor();
            overlay.setBounds(0f, 0f, 800f, 600f);
            overlay.setTouchable(Touchable.enabled);
            int[] overlayDown = {0};
            overlay.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y,
                                                   int pointer, int mouseButton) {
                    overlayDown[0]++;
                    return true;
                }
            });
            studio.addActor(overlay);
            HudTestInputRouter router = new HudTestInputRouter(session, studio);
            InputMultiplexer inputs = new InputMultiplexer(router, studio);

            assertTrue(inputs.touchDown(400, 500, 0, 0));
            assertEquals(1, overlayDown[0]);
            assertEquals(0, hudDown[0]);

            overlay.setTouchable(Touchable.disabled);
            assertTrue(inputs.touchDown(400, 500, 1, 0));
            assertEquals(1, hudDown[0]);
            assertTrue(inputs.touchDragged(900, 700, 1));
            assertTrue(inputs.touchUp(900, 700, 1, 0));
            assertEquals(1, hudUp[0]);
        } finally {
            studio.dispose();
            session.dispose();
        }
    }

    @Test public void testMaterializesNativeTooltipAndRootWindowWithoutChangingAuthoredData()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("native-widgets"));
        HudNode root = new HudNode("window", HudNodeKind.WINDOW);
        emptyTable(root);
        root.window = new HudWindowData();
        root.window.title = "Interactive";
        root.window.movable = true;
        root.window.resizable = true;
        root.window.modal = true;
        root.window.keepWithinStage = true;
        root.tooltip = new HudTooltipData();
        root.tooltip.text = "Window help";
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            String authored = new HudDocumentCodec().write(document.document());
            var materializedField = HudEditorSession.class.getDeclaredField("materialized");
            materializedField.setAccessible(true);
            MaterializedHud authoring = (MaterializedHud) materializedField.get(session);
            assertFalse(hasTooltip(authoring.actor("window")));

            assertTrue(session.enterTestMode());
            Window window = (Window) session.testActor("window");
            assertTrue(hasTooltip(window));
            assertTrue(window.isMovable());
            assertTrue(window.isResizable());
            assertTrue(window.isModal());
            assertSame(session.testStage().getRoot(), window.getParent());

            window.setSize(180f, 120f);
            window.setPosition(-70f, -40f);
            window.keepWithinStage();
            assertEquals(0f, window.getX(), 0.01f);
            assertEquals(0f, window.getY(), 0.01f);
            assertEquals(authored, new HudDocumentCodec().write(document.document()));
        } finally {
            session.dispose();
        }
    }

    @Test public void nestedWindowKeepsItsParentRelativeCoordinates() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("nested-window"));
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode child = new HudNode("nested", HudNodeKind.WINDOW);
        child.window = new HudWindowData();
        emptyTable(child);
        child.window.keepWithinStage = true;
        root.children.add(HudChild.direct(child));
        HudScreenEditorDocument document = new HudScreenEditorDocument(
                "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root));
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        try {
            session.open(project, document);
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            String authored = new HudDocumentCodec().write(document.document());
            assertTrue(session.enterTestMode());
            Window window = (Window) session.testActor("nested");
            assertSame(session.testActor("root"), window.getParent());
            window.setSize(180f, 120f);
            window.setPosition(-70f, -40f);
            window.keepWithinStage();
            assertEquals(-70f, window.getX(), 0.01f);
            assertEquals(-40f, window.getY(), 0.01f);
            assertEquals(authored, new HudDocumentCodec().write(document.document()));
        } finally {
            session.dispose();
        }
    }

    @Test public void rightReleaseCannotEndLeftHudDragAndStudioCannotStealItsCompletion()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("multi-button"));
        HudNode root = new HudNode("play", HudNodeKind.TEXT_BUTTON);
        root.textButton = new HudTextButtonData();
        root.textButton.text = "Play";
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        Stage studio = new Stage(new ScreenViewport(), inertBatch());
        try {
            session.open(project, new HudScreenEditorDocument(
                    "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root)));
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            assertTrue(session.enterTestMode());
            TextButton button = (TextButton) session.testActor("play");
            int[] dragged = {0};
            int[] released = {0};
            button.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y,
                                                   int pointer, int mouseButton) {
                    return mouseButton == 0;
                }
                @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    dragged[0]++;
                }
                @Override public void touchUp(InputEvent event, float x, float y,
                                              int pointer, int mouseButton) {
                    released[0]++;
                }
            });
            studio.getViewport().update(800, 800, true);
            studio.getRoot().setSize(800f, 800f);
            Actor overlay = new Actor();
            overlay.setBounds(0f, 0f, 800f, 800f);
            overlay.setTouchable(Touchable.disabled);
            int[] overlayDrags = {0};
            overlay.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y,
                                                   int pointer, int mouseButton) {
                    return mouseButton == 1;
                }
                @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    overlayDrags[0]++;
                }
            });
            studio.addActor(overlay);
            HudTestInputRouter router = new HudTestInputRouter(session, studio);
            InputMultiplexer inputs = new InputMultiplexer(router, studio);

            assertTrue(studio.setKeyboardFocus(overlay));
            assertTrue(inputs.touchDown(400, 400, 0, 0));
            assertNull(studio.getKeyboardFocus());
            overlay.setTouchable(Touchable.enabled);
            assertTrue(inputs.touchDown(400, 400, 0, 1));
            assertFalse(inputs.mouseMoved(400, 400));
            desktopAct(session, 0f);
            assertTrue(inputs.touchDragged(430, 410, 0));
            assertTrue(inputs.touchUp(430, 410, 0, 1));
            assertTrue(inputs.touchDragged(900, 700, 0));
            assertTrue(inputs.touchUp(900, 700, 0, 0));
            assertEquals(2, dragged[0]);
            assertEquals(1, released[0]);
            assertEquals(0, overlayDrags[0]);
        } finally {
            studio.dispose();
            session.dispose();
        }
    }

    @Test public void rightButtonReleaseOnPointerZeroKeepsTheLeftNativeTouchFocus()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("same-pointer-buttons"));
        HudNode root = new HudNode("play", HudNodeKind.TEXT_BUTTON);
        root.textButton = new HudTextButtonData();
        root.textButton.text = "Play";
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        Stage studio = new Stage(new ScreenViewport(), inertBatch());
        try {
            session.open(project, new HudScreenEditorDocument(
                    "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root)));
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            assertTrue(session.enterTestMode());
            int[] leftDrags = {0};
            int[] leftUps = {0};
            session.testActor("play").addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y,
                                                   int pointer, int button) {
                    return true;
                }
                @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    leftDrags[0]++;
                }
                @Override public void touchUp(InputEvent event, float x, float y,
                                              int pointer, int button) {
                    if (button == 0) leftUps[0]++;
                }
            });
            HudTestInputRouter router = new HudTestInputRouter(session, studio);
            InputMultiplexer inputs = new InputMultiplexer(router, studio);

            assertTrue(inputs.touchDown(400, 400, 0, 0));
            assertTrue(inputs.touchDown(400, 400, 0, 1));
            assertTrue(inputs.touchUp(400, 400, 0, 1));
            assertTrue(inputs.touchDragged(900, 700, 0));
            assertTrue(inputs.touchUp(900, 700, 0, 0));
            assertEquals(1, leftDrags[0]);
            assertEquals(1, leftUps[0]);
        } finally {
            studio.dispose();
            session.dispose();
        }
    }

    @Test public void leavingCanvasOrEnteringStudioOverlayEndsNativeHoverAndTooltip()
            throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("hover-exit"));
        HudNode root = new HudNode("play", HudNodeKind.TEXT_BUTTON);
        root.textButton = new HudTextButtonData();
        root.textButton.text = "Play";
        root.tooltip = new HudTooltipData();
        root.tooltip.text = "Help";
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        Stage studio = new Stage(new ScreenViewport(), inertBatch());
        try {
            session.open(project, new HudScreenEditorDocument(
                    "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root)));
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            assertTrue(session.enterTestMode());
            TextButton button = (TextButton) session.testActor("play");
            Tooltip<?> tooltip = tooltip(button);
            assertNotNull(tooltip);
            tooltip.getManager().initialTime = 0f;
            tooltip.getManager().hideAll();
            int[] exits = {0};
            button.addListener(new InputListener() {
                @Override public void exit(InputEvent event, float x, float y,
                                           int pointer, Actor toActor) {
                    if (pointer == -1) exits[0]++;
                }
            });
            studio.getViewport().update(800, 800, true);
            studio.getRoot().setSize(800f, 800f);
            Actor overlay = new Actor();
            overlay.setBounds(0f, 0f, 800f, 800f);
            overlay.setTouchable(Touchable.disabled);
            overlay.addListener(new InputListener() {
                @Override public boolean mouseMoved(InputEvent event, float x, float y) {
                    return true;
                }
            });
            studio.addActor(overlay);
            HudTestInputRouter router = new HudTestInputRouter(session, studio);
            InputMultiplexer inputs = new InputMultiplexer(router, studio);

            assertTrue(inputs.mouseMoved(400, 400));
            desktopAct(session, 0.3f);
            assertNotNull(tooltip.getContainer().getParent());

            assertFalse(inputs.mouseMoved(900, 700));
            desktopAct(session, 0.3f);
            assertEquals(1, exits[0]);
            desktopAct(session, 0.3f);
            assertNull(tooltip.getContainer().getParent());

            assertTrue(inputs.mouseMoved(400, 400));
            desktopAct(session, 0.3f);
            assertNotNull(tooltip.getContainer().getParent());
            overlay.setTouchable(Touchable.enabled);
            assertTrue(inputs.mouseMoved(400, 400));
            desktopAct(session, 0.3f);
            assertEquals(2, exits[0]);
            desktopAct(session, 0.3f);
            assertNull(tooltip.getContainer().getParent());
        } finally {
            studio.dispose();
            session.dispose();
        }
    }

    @Test public void modalWindowCannotKeepNativeHoverAfterCanvasExit() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("modal-hover-exit"));
        HudNode root = new HudNode("window", HudNodeKind.WINDOW);
        emptyTable(root);
        root.window = new HudWindowData();
        root.window.modal = true;
        root.tooltip = new HudTooltipData();
        root.tooltip.text = "Help";
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        Stage studio = new Stage(new ScreenViewport(), inertBatch());
        try {
            session.open(project, new HudScreenEditorDocument(
                    "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root)));
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            assertTrue(session.enterTestMode());
            Window window = (Window) session.testActor("window");
            Tooltip<?> tooltip = tooltip(window);
            assertNotNull(tooltip);
            tooltip.getManager().initialTime = 0f;
            tooltip.getManager().hideAll();
            int[] exits = {0};
            window.addListener(new InputListener() {
                @Override public void exit(InputEvent event, float x, float y,
                                           int pointer, Actor toActor) {
                    if (pointer == -1) exits[0]++;
                }
            });
            HudTestInputRouter router = new HudTestInputRouter(session, studio);
            assertTrue(router.mouseMoved(400, 400));
            desktopAct(session, 0.3f);
            assertNotNull(tooltip.getContainer().getParent());

            assertFalse(desktopMouseMoved(router, 900, 700));
            desktopAct(session, 0.3f);
            assertEquals(1, exits[0]);
            desktopAct(session, 0.3f);
            assertNull(tooltip.getContainer().getParent());

            assertTrue(router.mouseMoved(400, 400));
            desktopAct(session, 0.3f);
            assertTrue(router.touchDown(400, 400, 0, 0));
            assertTrue(router.touchDragged(900, 700, 0));
            desktopAct(session, 0f);
            assertEquals(2, exits[0]);
            assertTrue(router.touchUp(900, 700, 0, 0));

            assertTrue(router.mouseMoved(400, 400));
            desktopAct(session, 0.3f);
            assertTrue(router.touchDown(400, 400, 0, 0));
            assertTrue(router.touchUp(900, 700, 0, 0));
            desktopAct(session, 0f);
            assertEquals(3, exits[0]);
        } finally {
            studio.dispose();
            session.dispose();
        }
    }

    @Test public void reopeningTestModeDoesNotRetainThePreviousNativeGesture() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("gesture-lifecycle"));
        HudNode root = new HudNode("play", HudNodeKind.TEXT_BUTTON);
        root.textButton = new HudTextButtonData();
        root.textButton.text = "Play";
        HudEditorSession session = new HudEditorSession(
                AssetMetaDatabase::new, HudInteractiveTestModeTest::inertBatch);
        Stage studio = new Stage(new ScreenViewport(), inertBatch());
        try {
            session.open(project, new HudScreenEditorDocument(
                    "hud/main", "Main", canvasSizedAsset(), new HudDocumentV1(root)));
            assertTrue(session.configurePreview(new Rectangle(0f, 0f, 800f, 600f)));
            assertTrue(session.enterTestMode());
            HudTestInputRouter router = new HudTestInputRouter(session, studio);
            assertTrue(router.touchDown(400, 400, 0, 0));
            session.exitTestMode();
            assertTrue(session.enterTestMode());
            assertFalse(router.touchDragged(900, 700, 0));
            assertFalse(router.touchUp(900, 700, 0, 0));
            assertTrue(router.touchDown(400, 400, 0, 0));
            assertTrue(router.touchUp(400, 400, 0, 0));
        } finally {
            studio.dispose();
            session.dispose();
        }
    }

    private static HudScreenAsset canvasSizedAsset() {
        HudScreenAsset asset = asset();
        return asset;
    }

    private static boolean hasTooltip(Actor actor) {
        return tooltip(actor) != null;
    }

    private static Tooltip<?> tooltip(Actor actor) {
        for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners()) {
            if (listener instanceof Tooltip<?> tooltip) return tooltip;
        }
        return null;
    }

    private static boolean desktopMouseMoved(HudTestInputRouter router, int x, int y)
            throws Exception {
        return withDesktopApplication(() -> router.mouseMoved(x, y));
    }

    private static void desktopAct(HudEditorSession session, float delta) throws Exception {
        withDesktopApplication(() -> { session.act(delta); return null; });
    }

    private static <T> T withDesktopApplication(Callable<T> action) throws Exception {
        Application previous = Gdx.app;
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getType")) return Application.ApplicationType.Desktop;
                    try { return method.invoke(previous, args); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        try { return action.call(); }
        finally { Gdx.app = previous; }
    }

    private static HudScreenAsset asset() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/main.json";
        return asset;
    }

    private static void emptyTable(HudNode node) {
        node.table = HudLayoutAuthoring.newTableLayout(node, 1, 1, false);
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
}
