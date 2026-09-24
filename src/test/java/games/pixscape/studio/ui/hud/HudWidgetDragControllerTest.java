package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTextField;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import games.pixscape.studio.ui.modal.StudioDialog;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.runtime.hud.document.HudScrollPaneData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudDialogData;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Exercises the actual Scene2D source/target lifecycle, not a hand-built controller payload. */
public class HudWidgetDragControllerTest {
    @BeforeClass public static void bootGdx() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void tableDimensionDialogCancelsWithoutPublishingAndConfirmsEnteredGrid() {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(640, 480);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        int[] confirmed = new int[3];
        try {
            stage.getViewport().update(640, 480, true);
            HudTableCreateDialog.show(stage, (rows, columns) -> {
                confirmed[0]++;
                confirmed[1] = rows;
                confirmed[2] = columns;
            });
            StudioDialog dialog = (StudioDialog) stage.getActors().peek();
            dialogButton(dialog, "Cancel").fire(new ChangeListener.ChangeEvent());
            assertEquals(0, confirmed[0]);

            HudTableCreateDialog.show(stage, (rows, columns) -> {
                confirmed[0]++;
                confirmed[1] = rows;
                confirmed[2] = columns;
            });
            dialog = (StudioDialog) stage.getActors().peek();
            VisTextField rows = null;
            VisTextField columns = null;
            for (Actor actor : dialog.getContentTable().getChildren()) if (actor instanceof VisTextField field) {
                if (rows == null) rows = field;
                else columns = field;
            }
            assertNotNull(rows);
            assertNotNull(columns);
            rows.setText("3");
            columns.setText("4");
            dialogButton(dialog, "Create").fire(new ChangeListener.ChangeEvent());
            assertEquals(1, confirmed[0]);
            assertEquals(3, confirmed[1]);
            assertEquals(4, confirmed[2]);

            HudTableCreateDialog.show(stage, (rowsCount, columnsCount) -> confirmed[0]++);
            dialog = (StudioDialog) stage.getActors().peek();
            VisTextField invalidRows = null;
            for (Actor actor : dialog.getContentTable().getChildren())
                if (actor instanceof VisTextField field) { invalidRows = field; break; }
            assertNotNull(invalidRows);
            invalidRows.setText("0");
            dialogButton(dialog, "Create").fire(new ChangeListener.ChangeEvent());
            assertEquals(1, confirmed[0]);
            assertSame(stage, dialog.getStage());
        } finally {
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    private static VisTextButton dialogButton(StudioDialog dialog, String caption) {
        for (Actor actor : dialog.getButtonsTable().getChildren())
            if (actor instanceof VisTextButton button && caption.equals(button.getText().toString()))
                return button;
        throw new AssertionError("Missing dialog button " + caption);
    }

    @Test public void imageTextButtonNativeDragCreatesOnceAtRootDespiteAnIncompatibleSelectedContainer() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 240);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 240, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/main", "HUD", asset(), new HudDocumentV1(rootWithOccupiedContainer())));
            document.setSelectedNodeId("container");
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, Runnable::run);
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);

            Vector2 source = panel.imageTextButtonButton().localToStageCoordinates(new Vector2(8f, 8f));
            assertTrue(panel.imageTextButtonButton().isAscendantOf(stage.hit(source.x, source.y, true)));
            assertFalse(panel.imageTextButtonButton().isDisabled());
            assertTrue(panel.imageTextButtonButton().getCaptureListeners().size > 0);
            assertTrue(session.projects(document));
            assertSame(document, documents.activeDocument());
            assertNotNull(session.widgetDropTargetAt(70f, 70f));
            assertTrue(stageTouchDown(stage, source, panel.imageTextButtonButton()));
            stageTouchDragged(stage, new Vector2(70f, 70f));
            assertTrue(controller.isDragging());
            assertGhost(controller, 70f, 70f);
            assertSame(panel.imageTextButtonButton().getStyle().imageUp,
                    ((Image) controller.dragActor()).getDrawable());
            stageTouchUp(stage, new Vector2(70f, 70f));

            assertNull("DragAndDrop removes the ghost when the drag stops", controller.dragActor());

            assertEquals("one drag creates one history operation", 1, document.editSession().historySize());
            assertEquals(2, document.document().root.children.size());
            HudChild created = document.document().root.children.get(1);
            assertEquals(HudNodeKind.IMAGE_TEXT_BUTTON, created.node.kind);
            assertEquals("Button", created.node.imageTextButton.text);
            assertEquals(70f, created.free.offsetX, 0.01f);
            assertEquals(70f, created.free.offsetY, 0.01f);
            assertEquals("image-text-button-1", session.selectedNodeId());

            assertNull("an occupied Container with no eligible visible child is not a destination",
                    session.widgetDropTargetAt(10f, 10f));
            assertNotNull(session.widgetDropTargetAt(40f, 90f));
            assertFalse("drag must not also fire the button click", document.editSession().historySize() > 1);

            // A rejected target still keeps the generic Payload drag actor visible.
            assertTrue(stageTouchDown(stage, source, panel.imageTextButtonButton()));
            stageTouchDragged(stage, new Vector2(10f, 10f));
            assertTrue(controller.isDragging());
            assertGhost(controller, 10f, 10f);

            // Switching documents invalidates the captured payload without changing the ghost's lifecycle.
            HudScreenEditorDocument switched = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/other", "Other HUD", asset(), new HudDocumentV1(rootWithOccupiedContainer())));
            assertSame(switched, documents.activeDocument());
            assertGhost(controller, 10f, 10f);
            stageTouchUp(stage, new Vector2(10f, 10f));
            assertNull("DragAndDrop removes the ghost after a rejected, switched-document drop",
                    controller.dragActor());
            assertEquals(1, document.editSession().historySize());
            assertEquals(0, switched.editSession().historySize());

            documents.activate(document.key());
            assertTrue(stageTouchDown(stage, source, panel.imageTextButtonButton()));
            stageTouchDragged(stage, new Vector2(40f, 70f));
            assertGhost(controller, 40f, 70f);
            session.suspend();
            assertGhost(controller, 40f, 70f);
            stageTouchUp(stage, new Vector2(40f, 70f));
            assertNull("DragAndDrop removes the ghost after editor suspension", controller.dragActor());
            assertEquals(1, document.editSession().historySize());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeDragCreatesInsideTheVisuallyTargetedNestedGroup() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/nested-drag", "HUD", asset(), nestedDropDocument()));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, Runnable::run);
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);

            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(8f, 8f));
            fire(panel.groupButton(), stage, InputEvent.Type.touchDown, source);
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(15f, 15f));
            assertGhost(controller, 15f, 15f);
            fire(panel.groupButton(), stage, InputEvent.Type.touchUp, new Vector2(15f, 15f));

            assertEquals(1, document.editSession().historySize());
            HudChild created = relation(document.document(), "group-1");
            assertEquals("group", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), created.node.id));
            assertEquals(5f, created.free.offsetX, 0.01f);
            assertEquals(5f, created.free.offsetY, 0.01f);
            assertEquals("group-1", session.selectedNodeId());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragCreatesOneProgressBarAtTheResolvedDestination() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/progress-drag", "HUD", asset(),
                    new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, Runnable::run);
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);

            Vector2 source = panel.progressBarButton().localToStageCoordinates(new Vector2(8f, 8f));
            assertTrue(stageTouchDown(stage, source, panel.progressBarButton()));
            stageTouchDragged(stage, new Vector2(70f, 70f));
            assertGhost(controller, 70f, 70f);
            assertSame(panel.progressBarButton().getStyle().imageUp,
                    ((Image) controller.dragActor()).getDrawable());
            stageTouchUp(stage, new Vector2(70f, 70f));

            assertEquals(1, document.editSession().historySize());
            HudChild created = document.document().root.children.get(0);
            assertEquals(HudNodeKind.PROGRESS_BAR, created.node.kind);
            assertEquals(70f, created.free.offsetX, 0.01f);
            assertEquals(70f, created.free.offsetY, 0.01f);
            assertEquals("progress-bar-1", session.selectedNodeId());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeListDragCreatesOneAuthoredListAtTheResolvedDestination() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/list-drag", "HUD", asset(),
                    new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, Runnable::run);
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);

            Vector2 source = panel.listButton().localToStageCoordinates(new Vector2(8f, 8f));
            assertTrue(stageTouchDown(stage, source, panel.listButton()));
            stageTouchDragged(stage, new Vector2(70f, 70f));
            assertSame(panel.listButton().getStyle().imageUp,
                    ((Image) controller.dragActor()).getDrawable());
            stageTouchUp(stage, new Vector2(70f, 70f));

            assertEquals(1, document.editSession().historySize());
            HudChild created = document.document().root.children.get(0);
            assertEquals(HudNodeKind.LIST, created.node.kind);
            assertEquals(70f, created.free.offsetX, 0.01f);
            assertEquals(70f, created.free.offsetY, 0.01f);
            assertEquals("list-1", session.selectedNodeId());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeDragRejectsVisibleLabelsInZeroSizedGroupsAndClearsFeedback() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/zero-sized", "HUD", asset(), zeroSizedGroupsWithVisibleLabels()));
            document.setSelectedNodeId("valid");
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            assertTrue(session.materializedHud().actor("zero-both-label").getWidth() > 0f);
            assertTrue(session.materializedHud().actor("zero-both-label").getHeight() > 0f);

            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, Runnable::run);
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);

            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(8f, 8f));
            fire(panel.groupButton(), stage, InputEvent.Type.touchDown, source);
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(70f, 70f));
            assertTrue(hasDropFeedback(session));
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(12f, 12f));
            assertNull(session.widgetDropTargetAt(12f, 12f));
            assertFalse("a rejected target clears the preceding feedback", hasDropFeedback(session));
            assertGhost(controller, 12f, 12f);
            fire(panel.groupButton(), stage, InputEvent.Type.touchUp, new Vector2(12f, 12f));
            assertEquals(0, document.editSession().historySize());
            assertEquals("valid", session.selectedNodeId());

            assertNull("a Group with only its width at zero is not a target",
                    session.widgetDropTargetAt(12f, 42f));
            assertNull("a Group with only its height at zero is not a target",
                    session.widgetDropTargetAt(12f, 72f));

            fire(panel.groupButton(), stage, InputEvent.Type.touchDown, source);
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(70f, 70f));
            assertTrue(hasDropFeedback(session));
            session.materializedHud().actor("valid").setSize(0f, 0f);
            fire(panel.groupButton(), stage, InputEvent.Type.touchUp, new Vector2(70f, 70f));
            assertEquals("a target that loses its visible surface cannot publish", 0,
                    document.editSession().historySize());
            assertEquals("valid", session.selectedNodeId());
            assertFalse(hasDropFeedback(session));
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeDragUsesCapturedPreselectionBeforeTheRootFallback() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/preselection", "HUD", asset(), preselectionDocument()));
            document.setSelectedNodeId("selected-table");
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));

            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, Runnable::run);
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(8f, 8f));

            assertEquals("root", session.widgetDropTargetAt(40f, 40f).parentId());
            fire(panel.groupButton(), stage, InputEvent.Type.touchDown, source);
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(40f, 40f));
            assertEquals("Destination : selected-table — présélection", feedbackText(session));
            fire(panel.groupButton(), stage, InputEvent.Type.touchUp, new Vector2(40f, 40f));
            assertEquals(1, document.editSession().historySize());
            HudChild first = relation(document.document(), "group-1");
            assertEquals("selected-table", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), first.node.id));
            assertEquals(HudPlacementKind.CELL, first.placementKind);

            session.selectNode("selected-table");
            fire(panel.groupButton(), stage, InputEvent.Type.touchDown, source);
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(70f, 70f));
            assertEquals("Destination : direct-group", feedbackText(session));
            fire(panel.groupButton(), stage, InputEvent.Type.touchUp, new Vector2(70f, 70f));
            assertEquals(2, document.editSession().historySize());
            HudChild second = relation(document.document(), "group-2");
            assertEquals("direct-group", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), second.node.id));

            session.selectNode("occupied-container");
            fire(panel.groupButton(), stage, InputEvent.Type.touchDown, source);
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(15f, 75f));
            assertFalse("an occupied selected Container is not rescued through another branch",
                    hasDropFeedback(session));
            assertGhost(controller, 15f, 75f);
            fire(panel.groupButton(), stage, InputEvent.Type.touchUp, new Vector2(15f, 75f));
            assertEquals(2, document.editSession().historySize());
            assertEquals("occupied-container", session.selectedNodeId());

            session.selectNode("selected-table");
            fire(panel.groupButton(), stage, InputEvent.Type.touchDown, source);
            fire(panel.groupButton(), stage, InputEvent.Type.touchDragged, new Vector2(40f, 40f));
            assertTrue(hasDropFeedback(session));
            assertTrue(session.deleteNode("selected-table"));
            assertEquals(3, document.editSession().historySize());
            fire(panel.groupButton(), stage, InputEvent.Type.touchUp, new Vector2(40f, 40f));
            assertEquals("a deleted captured parent cannot receive a drop", 3,
                    document.editSession().historySize());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragUsesNestedPreselectionOnlyInsideItsNearestVisibleAncestor()
            throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/nested-preselection", "HUD", asset(), nestedPreselectionDocument()));
            document.setSelectedNodeId("selected-table");
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));

            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            panel.groupButton().remove();
            panel.groupButton().setPosition(110f, 80f);
            stage.addActor(panel.groupButton());
            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(
                    panel.groupButton().getWidth() * 0.5f, panel.groupButton().getHeight() * 0.5f));
            assertTrue(panel.groupButton().isAscendantOf(stage.hit(source.x, source.y, true)));
            assertFalse(panel.groupButton().isDisabled());
            assertTrue(panel.groupButton().getCaptureListeners().size > 0);
            assertTrue(session.projects(document));
            assertSame(document, documents.activeDocument());

            // A displayed rescue that loses its nearest visible ancestor cannot switch destination on release.
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(30f, 30f));
            assertEquals("Destination : selected-table — présélection", feedbackText(session));
            session.materializedHud().actor("visible-group").setSize(0f, 0f);
            stageTouchUp(stage, new Vector2(30f, 30f));
            assertEquals(0, document.editSession().historySize());
            assertFalse(hasDropFeedback(session));
            session.materializedHud().actor("visible-group").setSize(40f, 40f);

            // The direct resolver finds the Group, but its selected zero-sized Table wins within it.
            assertEquals("visible-group", session.widgetDropTargetAt(30f, 30f).parentId());
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(30f, 30f));
            assertTrue(controller.isDragging());
            assertEquals("Destination : selected-table — présélection", feedbackText(session));
            assertGhost(controller, 30f, 30f);
            stageTouchUp(stage, new Vector2(30f, 30f));
            assertEquals(1, document.editSession().historySize());
            assertEquals("selected-table", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), relation(document.document(), "group-1").node.id));
            assertTrue(document.editSession().undo());
            assertTrue(document.editSession().redo());

            // Leaving the closest visible ancestor refuses the rescue but preserves root's direct target.
            session.selectNode("selected-table");
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(95f, 95f));
            assertEquals("Destination : root", feedbackText(session));
            stageTouchUp(stage, new Vector2(95f, 95f));
            assertEquals(2, document.editSession().historySize());
            assertEquals("root", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), relation(document.document(), "group-2").node.id));

            // A clearly targeted sibling layout retains priority over the captured selection.
            session.selectNode("selected-table");
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(70f, 70f));
            assertEquals("Destination : other-layout", feedbackText(session));
            stageTouchUp(stage, new Vector2(70f, 70f));
            assertEquals(3, document.editSession().historySize());
            assertEquals("other-layout", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), relation(document.document(), "group-3").node.id));

            // Without a preselection, ordinary direct root placement is unchanged.
            session.selectNode(null);
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(90f, 20f));
            assertEquals("Destination : root", feedbackText(session));
            stageTouchUp(stage, new Vector2(90f, 20f));
            assertEquals(4, document.editSession().historySize());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragCreatesOneDirectScrollPaneChildAndCancelsCleanly()
            throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/scroll-pane", "HUD", asset(), emptyScrollPaneDocument()));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));

            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            panel.groupButton().remove();
            panel.groupButton().setPosition(110f, 80f);
            stage.addActor(panel.groupButton());
            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(
                    panel.groupButton().getWidth() * .5f, panel.groupButton().getHeight() * .5f));

            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(40f, 40f));
            assertEquals("Destination : pane", feedbackText(session));
            assertGhost(controller, 40f, 40f);
            stageTouchUp(stage, new Vector2(105f, 105f));
            assertEquals(0, document.editSession().historySize());
            assertFalse(hasDropFeedback(session));

            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(40f, 40f));
            assertEquals("Destination : pane", feedbackText(session));
            stageTouchUp(stage, new Vector2(40f, 40f));
            assertEquals(1, document.editSession().historySize());
            HudChild created = relation(document.document(), "group-1");
            assertEquals(HudPlacementKind.DIRECT, created.placementKind);
            assertEquals("pane", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), created.node.id));
            assertEquals("group-1", session.selectedNodeId());
            assertTrue(document.editSession().undo());
            assertTrue(document.editSession().redo());
            assertEquals(1, document.editSession().historySize());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragTargetsWindowContentButNeverItsTitle() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            HudNode root = new HudNode("window", HudNodeKind.WINDOW);
            root.window = new HudWindowData();
            emptyTable(root);
            root.actor.width = 100f;
            root.actor.height = 100f;
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/window", "HUD", asset(), new HudDocumentV1(root)));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            Window window = (Window) session.materializedHud().actor("window");
            assertNotNull(window);
            assertFalse("authoring suppresses native move", window.isMovable());
            assertFalse("authoring suppresses modal capture", window.isModal());

            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            assertNull(session.materializedHud().actor(window.getTitleLabel().getName()));
            panel.groupButton().remove();
            panel.groupButton().setPosition(110f, 80f);
            stage.addActor(panel.groupButton());
            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(
                    panel.groupButton().getWidth() * .5f, panel.groupButton().getHeight() * .5f));

            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(40f, 90f));
            assertFalse(hasDropFeedback(session));
            stageTouchUp(stage, new Vector2(40f, 90f));
            assertEquals(0, document.editSession().historySize());

            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(40f, 40f));
            assertEquals("Destination : window", feedbackText(session));
            Actor feedback = feedbackActor(session);
            assertEquals(window.getPadBottom(), feedback.getY(), 0.01f);
            assertEquals(window.getHeight() - window.getPadTop() - window.getPadBottom(),
                    feedback.getHeight(), 0.01f);
            stageTouchUp(stage, new Vector2(40f, 40f));
            assertEquals(1, document.editSession().historySize());
            HudChild created = relation(document.document(), "group-1");
            assertEquals(HudPlacementKind.CELL, created.placementKind);
            assertEquals("group-1", session.selectedNodeId());
            assertTrue(document.editSession().undo());
            assertTrue(document.editSession().redo());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragFindsDialogContentButNotItsTitle() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            HudNode root = new HudNode("root", HudNodeKind.GROUP);
            root.actor.width = 100f;
            root.actor.height = 100f;
            HudNode dialogNode = new HudNode("dialog", HudNodeKind.DIALOG);
            emptyTable(dialogNode);
            dialogNode.dialog = new HudDialogData();
            dialogNode.actor.width = 100f;
            dialogNode.actor.height = 100f;
            root.children.add(HudChild.free(dialogNode, new HudFreePlacement()));
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/dialog-content", "HUD", asset(), new HudDocumentV1(root)));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            Window dialog = (Window) session.materializedHud().actor("dialog");
            assertNotNull(dialog);
            assertFalse(dialog.isModal());
            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            panel.groupButton().remove();
            panel.groupButton().setPosition(110f, 80f);
            stage.addActor(panel.groupButton());
            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(
                    panel.groupButton().getWidth() * .5f, panel.groupButton().getHeight() * .5f));
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(40f, 90f));
            assertFalse(hasDropFeedback(session));
            stageTouchUp(stage, new Vector2(40f, 90f));
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(40f, 40f));
            assertEquals("Destination : dialog", feedbackText(session));
            stageTouchUp(stage, new Vector2(40f, 40f));
            assertEquals(HudPlacementKind.CELL,
                    relation(document.document(), "group-1").placementKind);
            assertEquals(1, document.editSession().historySize());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragPrefersVisibleLayoutInsideWindow() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            HudNode root = new HudNode("window", HudNodeKind.WINDOW);
            root.window = new HudWindowData();
            emptyTable(root);
            root.actor.width = 100f;
            root.actor.height = 100f;
            HudNode nested = new HudNode("nested", HudNodeKind.TABLE);
            emptyTable(nested);
            HudCellConstraints cell = new HudCellConstraints();
            cell.prefWidth = 40f;
            cell.prefHeight = 30f;
            root.table.rows.get(0).cells.get(0).content = nested;
            root.table.rows.get(0).cells.get(0).constraints = cell;
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/window-nested", "HUD", asset(), new HudDocumentV1(root)));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            Window window = (Window) session.materializedHud().actor("window");
            window.validate();
            Actor nestedActor = session.materializedHud().actor("nested");
            Vector2 destination = nestedActor.localToStageCoordinates(new Vector2(
                    nestedActor.getWidth() * .5f, nestedActor.getHeight() * .5f));
            assertEquals("nested", session.widgetDropTargetAt(destination.x, destination.y).parentId());

            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            panel.groupButton().remove();
            panel.groupButton().setPosition(110f, 80f);
            stage.addActor(panel.groupButton());
            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(
                    panel.groupButton().getWidth() * .5f, panel.groupButton().getHeight() * .5f));
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, destination);
            assertEquals("Destination : nested", feedbackText(session));
            stageTouchUp(stage, destination);
            assertEquals(1, document.editSession().historySize());
            assertEquals("nested", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), "group-1"));
            assertEquals(HudPlacementKind.CELL, relation(document.document(), "group-1").placementKind);
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragCreatesOneWindowWithUndoRedo() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            HudNode root = new HudNode("root", HudNodeKind.GROUP);
            root.actor.width = 100f;
            root.actor.height = 100f;
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/window-create", "HUD", asset(), new HudDocumentV1(root)));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            panel.windowButton().remove();
            panel.windowButton().setPosition(110f, 80f);
            stage.addActor(panel.windowButton());
            Vector2 source = panel.windowButton().localToStageCoordinates(new Vector2(
                    panel.windowButton().getWidth() * .5f, panel.windowButton().getHeight() * .5f));
            assertTrue(stageTouchDown(stage, source, panel.windowButton()));
            stageTouchDragged(stage, new Vector2(40f, 40f));
            assertEquals("Destination : root", feedbackText(session));
            assertGhost(controller, 40f, 40f);
            assertSame(panel.windowButton().getStyle().imageUp,
                    ((Image) controller.dragActor()).getDrawable());
            stageTouchUp(stage, new Vector2(40f, 40f));
            assertEquals(1, document.editSession().historySize());
            assertEquals("window-1", session.selectedNodeId());
            HudChild created = relation(document.document(), "window-1");
            assertEquals(HudNodeKind.WINDOW, created.node.kind);
            assertEquals(HudPlacementKind.FREE, created.placementKind);
            assertTrue(document.editSession().undo());
            assertTrue(document.editSession().redo());

            panel.scrollPaneButton().remove();
            panel.scrollPaneButton().setPosition(110f, 80f);
            stage.addActor(panel.scrollPaneButton());
            Vector2 paneSource = panel.scrollPaneButton().localToStageCoordinates(new Vector2(
                    panel.scrollPaneButton().getWidth() * .5f,
                    panel.scrollPaneButton().getHeight() * .5f));
            assertTrue(stageTouchDown(stage, paneSource, panel.scrollPaneButton()));
            stageTouchDragged(stage, new Vector2(105f, 105f));
            assertTrue(controller.isDragging());
            assertTrue(controller.dragActor() instanceof Image);
            assertSame(panel.scrollPaneButton().getStyle().imageUp,
                    ((Image) controller.dragActor()).getDrawable());
            stageTouchUp(stage, new Vector2(105f, 105f));
            assertEquals(1, document.editSession().historySize());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nativeStageDragCreatesDialogWithOneUndoableEdit() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            HudNode root = new HudNode("root", HudNodeKind.GROUP);
            root.actor.width = 100f;
            root.actor.height = 100f;
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/dialog-create", "HUD", asset(), new HudDocumentV1(root)));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            panel.dialogButton().remove();
            panel.dialogButton().setPosition(110f, 80f);
            stage.addActor(panel.dialogButton());
            Vector2 source = panel.dialogButton().localToStageCoordinates(new Vector2(
                    panel.dialogButton().getWidth() * .5f, panel.dialogButton().getHeight() * .5f));
            assertTrue(stageTouchDown(stage, source, panel.dialogButton()));
            stageTouchDragged(stage, new Vector2(40f, 40f));
            stageTouchUp(stage, new Vector2(40f, 40f));
            assertEquals(1, document.editSession().historySize());
            HudChild created = relation(document.document(), "dialog-1");
            assertEquals(HudNodeKind.DIALOG, created.node.kind);
            assertTrue(created.node.dialog.modal);
            assertEquals(HudPlacementKind.FREE, created.placementKind);
            Actor dialog = session.materializedHud().actor("dialog-1");
            assertNotNull(dialog);
            assertNotNull(dialog.getStage());
            assertTrue(dialog.isVisible());
            assertTrue(dialog.getWidth() > 0f);
            assertTrue(dialog.getHeight() > 0f);
            assertTrue(session.editNodeVisibility(document.screenId(), "dialog-1", false));
            assertFalse(relation(document.document(), "dialog-1").node.visible);
            Actor rebuilt = session.materializedHud().actor("dialog-1");
            assertNotNull(rebuilt.getStage());
            assertTrue("a closed-at-start Dialog remains visible in EDIT", rebuilt.isVisible());
            var target = session.selectionTargets().stream()
                    .filter(candidate -> "dialog-1".equals(candidate.nodeId()))
                    .findFirst().orElseThrow();
            assertTrue(target.width() > 0f);
            assertTrue(target.height() > 0f);
            session.selectNode("root");
            assertTrue(target.toString(), session.selectOverlayTargetAt(50f, 50f));
            assertEquals("dialog-1", session.selectedNodeId());
            assertTrue(document.editSession().undo());
            assertTrue(document.editSession().redo());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void scrolledWindowOnlyTargetsItsVisibleContentAndNestedLayout() throws Exception {
        HudEditorSession session = null;
        try {
            HudNode paneNode = new HudNode("pane", HudNodeKind.SCROLL_PANE);
            paneNode.scrollPane = new HudScrollPaneData();
            paneNode.actor.width = 100f;
            paneNode.actor.height = 100f;
            HudNode windowNode = new HudNode("window", HudNodeKind.WINDOW);
            windowNode.window = new HudWindowData();
            emptyTable(windowNode);
            windowNode.actor.width = 160f;
            windowNode.actor.height = 200f;
            HudNode nested = new HudNode("nested", HudNodeKind.TABLE);
            emptyTable(nested);
            HudCellConstraints cell = new HudCellConstraints();
            cell.prefWidth = 160f;
            cell.prefHeight = 180f;
            windowNode.table.rows.get(0).cells.get(0).content = nested;
            windowNode.table.rows.get(0).cells.get(0).constraints = cell;
            paneNode.children.add(HudChild.direct(windowNode));
            HudScreenEditorDocument document = new HudScreenEditorDocument(
                    "hud/scrolled-window", "HUD", asset(), new HudDocumentV1(paneNode));
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            ScrollPane pane = (ScrollPane) session.materializedHud().actor("pane");
            pane.validate();
            assertTrue(pane.getMaxY() > 0f);
            pane.setScrollY(pane.getMaxY() * .5f);
            pane.updateVisualScroll();
            pane.layout();
            Window window = (Window) session.materializedHud().actor("window");
            window.validate();
            Actor nestedActor = session.materializedHud().actor("nested");
            Rectangle visible = games.pixscape.studio.service.hud.HudOverlayGeometry
                    .visibleActorBoundsInOverlay(nestedActor, pane, new Rectangle());
            assertTrue(visible.width > 0f && visible.height > 0f);
            Vector2 inside = pane.localToStageCoordinates(new Vector2(
                    visible.x + visible.width * .5f, visible.y + visible.height * .5f));
            assertEquals("nested", session.widgetDropTargetAt(inside.x, inside.y).parentId());
            assertNull(session.widgetDropTargetAt(105f, inside.y));
        } finally {
            if (session != null) session.dispose();
        }
    }

    @Test public void paddedScrollPanePreselectionRejectsPaddingAndCreatesOnceInContent()
            throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = graphics(220, 120);
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        HudEditorSession session = null;
        HudWidgetDragController controller = null;
        try {
            stage.getViewport().update(220, 120, true);
            EditorDocumentManager documents = new EditorDocumentManager();
            HudScreenEditorDocument document = documents.openHudScreen(new HudScreenEditorDocument(
                    "hud/padded-pane", "HUD", asset(), emptyScrollPaneDocument()));
            document.setSelectedNodeId("pane");
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            padScrollPane((ScrollPane) session.materializedHud().actor("pane"));

            HudWidgetsPanel panel = new HudWidgetsPanel(session, documents, runnable -> { });
            panel.pack();
            panel.setPosition(110f, 0f);
            stage.addActor(panel);
            HudCanvasInputHost host = new HudCanvasInputHost(session);
            host.setBounds(0f, 0f, 100f, 100f);
            host.activateHudInput();
            stage.addActor(host);
            controller = new HudWidgetDragController(session, documents, panel, host);
            panel.groupButton().remove();
            panel.groupButton().setPosition(110f, 80f);
            stage.addActor(panel.groupButton());
            Vector2 source = panel.groupButton().localToStageCoordinates(new Vector2(
                    panel.groupButton().getWidth() * .5f, panel.groupButton().getHeight() * .5f));

            assertNull(session.widgetDropTargetAt(12f, 40f));
            assertNull(session.preselectedWidgetDropTargetAt("pane", 12f, 40f));
            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(12f, 40f));
            assertGhost(controller, 12f, 40f);
            assertFalse(hasDropFeedback(session));
            stageTouchUp(stage, new Vector2(12f, 40f));
            assertEquals(0, document.editSession().historySize());
            assertEquals("pane", session.selectedNodeId());
            assertTrue(document.document().root.children.get(0).node.children.isEmpty());

            assertTrue(stageTouchDown(stage, source, panel.groupButton()));
            stageTouchDragged(stage, new Vector2(40f, 40f));
            assertEquals("Destination : pane", feedbackText(session));
            Actor feedback = feedbackActor(session);
            assertTrue("feedback excludes the padded edge", feedback.getX() > 10f);
            assertTrue(feedback.getWidth() < 60f);
            stageTouchUp(stage, new Vector2(40f, 40f));
            assertEquals(1, document.editSession().historySize());
            assertEquals(HudPlacementKind.DIRECT,
                    relation(document.document(), "group-1").placementKind);
            assertEquals("pane", games.pixscape.studio.service.hud.HudLayoutAuthoring.parentId(
                    document.document(), "group-1"));
            assertTrue(document.editSession().undo());
            assertTrue(document.editSession().redo());
            assertEquals(1, document.editSession().historySize());
        } finally {
            if (controller != null) controller.close();
            if (session != null) session.dispose();
            stage.dispose();
            Gdx.graphics = previous;
        }
    }

    @Test public void nestedPreselectionCannotEscapePaddedScrollPaneContent() throws Exception {
        HudEditorSession session = null;
        try {
            HudScreenEditorDocument document = new HudScreenEditorDocument(
                    "hud/padded-descendant", "HUD", asset(), paddedDescendantDocument());
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));
            padScrollPane((ScrollPane) session.materializedHud().actor("pane"));

            assertNull(session.preselectedWidgetDropTargetAt("selected-table", 12f, 40f));
            HudEditorSession.WidgetDropTarget direct = session.widgetDropTargetAt(40f, 40f);
            assertEquals("content", direct.parentId());
            HudEditorSession.WidgetDropTarget rescue =
                    session.preselectedWidgetDropTargetAt("selected-table", 40f, 40f);
            assertNotNull(rescue);
            assertEquals("selected-table", rescue.parentId());
            assertEquals("content", rescue.feedbackParentId());
            session.showWidgetDropFeedback(rescue);
            assertEquals("Destination : selected-table — présélection", feedbackText(session));
            session.clearWidgetDropFeedback();
        } finally {
            if (session != null) session.dispose();
        }
    }

    @Test public void resolvesNestedLayoutsWithTheirExistingPlacementConventions() throws Exception {
        HudEditorSession session = null;
        try {
            HudScreenEditorDocument document = new HudScreenEditorDocument(
                    "hud/nested", "HUD", asset(), nestedDropDocument());
            session = new HudEditorSession(() -> null, HudWidgetDragControllerTest::inertBatch);
            session.open(Gdx.files.local("."), document);
            var configure = HudEditorSession.class.getDeclaredMethod("configurePreview", Rectangle.class);
            configure.setAccessible(true);
            configure.invoke(session, new Rectangle(0f, 0f, 100f, 100f));

            HudEditorSession.WidgetDropTarget root = session.widgetDropTargetAt(95f, 95f);
            HudEditorSession.WidgetDropTarget group = session.widgetDropTargetAt(15f, 15f);
            HudEditorSession.WidgetDropTarget table = session.widgetDropTargetAt(45f, 15f);
            HudEditorSession.WidgetDropTarget stack = session.widgetDropTargetAt(15f, 45f);
            HudEditorSession.WidgetDropTarget emptyContainer = session.widgetDropTargetAt(45f, 45f);
            HudEditorSession.WidgetDropTarget nestedTable = session.widgetDropTargetAt(75f, 45f);

            assertEquals("root", root.parentId());
            assertEquals("group", group.parentId());
            assertEquals(5f, group.placement().offsetX, 0.01f);
            assertEquals(5f, group.placement().offsetY, 0.01f);
            assertEquals("table", table.parentId());
            assertNull(table.placement());
            assertEquals("stack", stack.parentId());
            assertNull(stack.placement());
            assertEquals("empty-container", emptyContainer.parentId());
            assertNull(emptyContainer.placement());
            assertEquals("nested-table", nestedTable.parentId());
            assertNull("a Table inside an occupied Container remains reachable", nestedTable.placement());
            assertNull("an occupied Container with no visible eligible descendant must not fall through",
                    session.widgetDropTargetAt(75f, 15f));

            assertTrue(session.createWidgetAt(HudNodeKind.GROUP, group, document));
            assertTrue(session.createWidgetAt(HudNodeKind.GROUP, table, document));
            assertTrue(session.createWidgetAt(HudNodeKind.GROUP, stack, document));
            assertTrue(session.createWidgetAt(HudNodeKind.GROUP, emptyContainer, document));
            assertEquals(4, document.editSession().historySize());
            assertEquals("group-4", session.selectedNodeId());
            assertEquals(5f, relation(document.document(), "group-1").free.offsetX, 0.01f);
            assertNotNull(relation(document.document(), "group-2").cell);
            assertEquals(HudPlacementKind.DIRECT, relation(document.document(), "group-3").placementKind);
            assertEquals(HudPlacementKind.DIRECT, relation(document.document(), "group-4").placementKind);
        } finally {
            if (session != null) session.dispose();
        }
    }

    private static void assertGhost(HudWidgetDragController controller, float pointerX, float pointerY) {
        assertTrue(controller.dragActor() instanceof Image);
        Image ghost = (Image) controller.dragActor();
        assertEquals(32f, ghost.getWidth(), 0.01f);
        assertEquals(32f, ghost.getHeight(), 0.01f);
        assertEquals(0.65f, ghost.getColor().a, 0.01f);
        assertEquals(Touchable.disabled, ghost.getTouchable());
        assertEquals(pointerX + 12f, ghost.getX(), 0.01f);
        assertEquals(pointerY + 12f, ghost.getY(), 0.01f);
    }

    private static HudNode rootWithOccupiedContainer() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.actor.width = 100f;
        root.actor.height = 100f;
        HudNode container = new HudNode("container", HudNodeKind.CONTAINER);
        container.container = new games.pixscape.runtime.hud.document.HudContainerData();
        container.actor.width = 20f;
        container.actor.height = 20f;
        container.children.add(HudChild.direct(new HudNode("inside", HudNodeKind.GROUP)));
        root.children.add(HudChild.free(container, new HudFreePlacement()));
        return root;
    }

    private static HudDocumentV1 nestedDropDocument() {
        HudNode root = sized("root", HudNodeKind.GROUP, 100f, 100f);
        root.children.add(HudChild.free(sized("group", HudNodeKind.GROUP, 20f, 20f), free(10f, 10f)));
        root.children.add(HudChild.free(sized("table", HudNodeKind.TABLE, 20f, 20f), free(40f, 10f)));
        root.children.add(HudChild.free(sized("stack", HudNodeKind.STACK, 20f, 20f), free(10f, 40f)));

        HudNode emptyContainer = sized("empty-container", HudNodeKind.CONTAINER, 20f, 20f);
        emptyContainer.container = new games.pixscape.runtime.hud.document.HudContainerData();
        root.children.add(HudChild.free(emptyContainer, free(40f, 40f)));

        HudNode occupiedContainer = sized("occupied-container", HudNodeKind.CONTAINER, 20f, 20f);
        occupiedContainer.container = new games.pixscape.runtime.hud.document.HudContainerData();
        occupiedContainer.children.add(HudChild.direct(new HudNode("unpointable-child", HudNodeKind.GROUP)));
        root.children.add(HudChild.free(occupiedContainer, free(70f, 10f)));

        HudNode tableContainer = sized("table-container", HudNodeKind.CONTAINER, 20f, 20f);
        tableContainer.container = new games.pixscape.runtime.hud.document.HudContainerData();
        tableContainer.children.add(HudChild.direct(sized("nested-table", HudNodeKind.TABLE, 20f, 20f)));
        root.children.add(HudChild.free(tableContainer, free(70f, 40f)));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 emptyScrollPaneDocument() {
        HudNode root = sized("root", HudNodeKind.GROUP, 100f, 100f);
        HudNode pane = sized("pane", HudNodeKind.SCROLL_PANE, 60f, 60f);
        pane.scrollPane = new HudScrollPaneData();
        root.children.add(HudChild.free(pane, free(10f, 10f)));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 paddedDescendantDocument() {
        HudDocumentV1 document = emptyScrollPaneDocument();
        HudNode content = sized("content", HudNodeKind.GROUP, 60f, 120f);
        content.children.add(HudChild.free(
                sized("selected-table", HudNodeKind.TABLE, 0f, 0f), free(5f, 5f)));
        document.root.children.get(0).node.children.add(HudChild.direct(content));
        return document;
    }

    private static void padScrollPane(ScrollPane pane) {
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle(pane.getStyle());
        BaseDrawable background = new BaseDrawable();
        background.setLeftWidth(8f);
        background.setRightWidth(8f);
        background.setTopHeight(8f);
        background.setBottomHeight(8f);
        style.background = background;
        pane.setStyle(style);
        pane.validate();
        pane.layout();
    }

    private static HudDocumentV1 zeroSizedGroupsWithVisibleLabels() {
        HudNode root = sized("root", HudNodeKind.GROUP, 100f, 100f);
        root.children.add(HudChild.free(sized("valid", HudNodeKind.GROUP, 20f, 20f), free(60f, 60f)));
        root.children.add(HudChild.free(labelGroup("zero-both", 0f, 0f, "Both"), free(10f, 10f)));
        root.children.add(HudChild.free(labelGroup("zero-width", 0f, 20f, "Width"), free(10f, 40f)));
        root.children.add(HudChild.free(labelGroup("zero-height", 20f, 0f, "Height"), free(10f, 70f)));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 preselectionDocument() {
        HudNode root = sized("root", HudNodeKind.GROUP, 100f, 100f);
        HudNode selectedTable = sized("selected-table", HudNodeKind.TABLE, 0f, 0f);
        selectedTable.table = games.pixscape.studio.service.hud.HudLayoutAuthoring
                .newTableLayout(selectedTable, 1, 2, false);
        selectedTable.table.rows.get(0).cells.get(0).id = "cell-selected-table-1";
        selectedTable.table.rows.get(0).cells.get(1).id = "cell-selected-table-2";
        root.children.add(HudChild.free(selectedTable, free(10f, 10f)));
        root.children.add(HudChild.free(sized("direct-group", HudNodeKind.GROUP, 20f, 20f), free(60f, 60f)));
        HudNode occupied = sized("occupied-container", HudNodeKind.CONTAINER, 20f, 20f);
        occupied.container = new games.pixscape.runtime.hud.document.HudContainerData();
        occupied.children.add(HudChild.direct(new HudNode("occupied-child", HudNodeKind.GROUP)));
        root.children.add(HudChild.free(occupied, free(10f, 70f)));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 nestedPreselectionDocument() {
        HudNode root = sized("root", HudNodeKind.GROUP, 100f, 100f);
        HudNode visibleGroup = sized("visible-group", HudNodeKind.GROUP, 40f, 40f);
        visibleGroup.children.add(HudChild.free(sized("selected-table", HudNodeKind.TABLE, 0f, 0f),
                free(5f, 5f)));
        root.children.add(HudChild.free(visibleGroup, free(10f, 10f)));
        root.children.add(HudChild.free(sized("other-layout", HudNodeKind.GROUP, 20f, 20f),
                free(60f, 60f)));
        return new HudDocumentV1(root);
    }

    private static HudNode labelGroup(String id, float width, float height, String text) {
        HudNode group = sized(id, HudNodeKind.GROUP, width, height);
        HudNode label = new HudNode(id + "-label", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = text;
        group.children.add(HudChild.free(label, new HudFreePlacement()));
        return group;
    }

    private static HudNode sized(String id, HudNodeKind kind, float width, float height) {
        HudNode node = new HudNode(id, kind);
        if (kind == HudNodeKind.TABLE) emptyTable(node);
        node.actor.width = width;
        node.actor.height = height;
        return node;
    }

    private static HudFreePlacement free(float x, float y) {
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = x;
        placement.offsetY = y;
        return placement;
    }

    private static HudChild relation(HudDocumentV1 document, String nodeId) {
        HudChild child = games.pixscape.studio.service.hud.HudLayoutAuthoring.childRelation(document, nodeId);
        if (child != null) return child;
        var cell = games.pixscape.studio.service.hud.HudLayoutAuthoring.containingCell(document, nodeId);
        return cell != null ? HudChild.cell(cell.content, cell.constraints) : null;
    }

    private static void emptyTable(HudNode node) {
        node.table = games.pixscape.studio.service.hud.HudLayoutAuthoring.newTableLayout(node, 1, 1, false);
        node.table.rows.get(0).cells.get(0).id = "cell-" + node.id;
    }

    private static boolean hasDropFeedback(HudEditorSession session) throws Exception {
        Field authoring = HudEditorSession.class.getDeclaredField("authoringSession");
        authoring.setAccessible(true);
        Object authoringSession = authoring.get(session);
        Field overlay = authoringSession.getClass().getDeclaredField("selectionOverlay");
        overlay.setAccessible(true);
        Object selectionOverlay = overlay.get(authoringSession);
        Field feedback = selectionOverlay.getClass().getDeclaredField("dropFeedback");
        feedback.setAccessible(true);
        return feedback.get(selectionOverlay) != null;
    }

    private static Actor feedbackActor(HudEditorSession session) throws Exception {
        Field authoring = HudEditorSession.class.getDeclaredField("authoringSession");
        authoring.setAccessible(true);
        Object authoringSession = authoring.get(session);
        Field overlay = authoringSession.getClass().getDeclaredField("selectionOverlay");
        overlay.setAccessible(true);
        Object selectionOverlay = overlay.get(authoringSession);
        Field feedback = selectionOverlay.getClass().getDeclaredField("dropFeedback");
        feedback.setAccessible(true);
        return (Actor) feedback.get(selectionOverlay);
    }

    private static String feedbackText(HudEditorSession session) throws Exception {
        Field authoring = HudEditorSession.class.getDeclaredField("authoringSession");
        authoring.setAccessible(true);
        Object authoringSession = authoring.get(session);
        Field overlay = authoringSession.getClass().getDeclaredField("selectionOverlay");
        overlay.setAccessible(true);
        Object selectionOverlay = overlay.get(authoringSession);
        Field feedback = selectionOverlay.getClass().getDeclaredField("dropFeedback");
        feedback.setAccessible(true);
        Object feedbackActor = feedback.get(selectionOverlay);
        Field label = feedbackActor.getClass().getDeclaredField("label");
        label.setAccessible(true);
        return ((VisLabel) label.get(feedbackActor)).getText().toString();
    }

    private static HudScreenAsset asset() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.referenceWidth = 100;
        asset.referenceHeight = 100;
        asset.documentId = "hud/main.json";
        return asset;
    }

    private static void fire(com.badlogic.gdx.scenes.scene2d.Actor actor, Stage stage,
                             InputEvent.Type type, Vector2 stagePoint) {
        InputEvent event = new InputEvent();
        event.setType(type);
        event.setStage(stage);
        event.setStageX(stagePoint.x);
        event.setStageY(stagePoint.y);
        event.setPointer(0);
        event.setButton(0);
        actor.fire(event);
    }

    private static boolean stageTouchDown(Stage stage, Vector2 stagePoint,
                                          com.badlogic.gdx.scenes.scene2d.Actor expectedActor) {
        Vector2 screenPoint = stage.stageToScreenCoordinates(new Vector2(stagePoint));
        Vector2 routedPoint = stage.screenToStageCoordinates(new Vector2(screenPoint));
        assertEquals(stagePoint.x, routedPoint.x, 0.01f);
        assertEquals(stagePoint.y, routedPoint.y, 0.01f);
        int screenX = Math.round(screenPoint.x);
        int screenY = Math.round(screenPoint.y);
        Vector2 roundedStagePoint = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
        assertTrue(expectedActor.isAscendantOf(stage.hit(roundedStagePoint.x, roundedStagePoint.y, true)));
        return stage.touchDown(screenX, screenY, 0, 0);
    }

    private static void stageTouchDragged(Stage stage, Vector2 stagePoint) {
        Vector2 screenPoint = stage.stageToScreenCoordinates(new Vector2(stagePoint));
        stage.touchDragged(Math.round(screenPoint.x), Math.round(screenPoint.y), 0);
    }

    private static void stageTouchUp(Stage stage, Vector2 stagePoint) {
        Vector2 screenPoint = stage.stageToScreenCoordinates(new Vector2(stagePoint));
        stage.touchUp(Math.round(screenPoint.x), Math.round(screenPoint.y), 0, 0);
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (proxy, method, args) -> primitive(method.getReturnType()));
    }

    private static Graphics graphics(int width, int height) {
        return (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> width;
                    case "getHeight", "getBackBufferHeight" -> height;
                    default -> primitive(method.getReturnType());
                });
    }

    private static Object primitive(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
