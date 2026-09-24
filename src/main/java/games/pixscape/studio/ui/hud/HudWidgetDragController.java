package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;

import java.util.Objects;

/** Native Scene2D toolbox-to-canvas DnD for HUD layout authoring. */
public final class HudWidgetDragController implements AutoCloseable {
    private static final float GHOST_SIZE = 32f;
    private static final float GHOST_ALPHA = 0.65f;
    private static final float GHOST_POINTER_GAP = 12f;

    private final HudEditorSession session;
    private final EditorDocumentManager documents;
    private final HudCanvasInputHost canvasHost;
    private final DragAndDrop dragAndDrop = new DragAndDrop();
    private final Vector2 stagePoint = new Vector2();

    public HudWidgetDragController(HudEditorSession session, EditorDocumentManager documents,
                                   HudWidgetsPanel toolbox, HudCanvasInputHost canvasHost) {
        this.session = Objects.requireNonNull(session, "session");
        this.documents = Objects.requireNonNull(documents, "documents");
        this.canvasHost = Objects.requireNonNull(canvasHost, "canvasHost");
        Objects.requireNonNull(toolbox, "toolbox");
        dragAndDrop.setTapSquareSize(HudEditorSession.TRANSFORM_DRAG_THRESHOLD_LOGICAL_PIXELS);
        dragAndDrop.setDragTime(0);
        dragAndDrop.setDragActorPosition(GHOST_SIZE + GHOST_POINTER_GAP, GHOST_POINTER_GAP);
        addSource(toolbox.groupButton(), HudNodeKind.GROUP);
        addSource(toolbox.tableButton(), HudNodeKind.TABLE);
        addSource(toolbox.stackButton(), HudNodeKind.STACK);
        addSource(toolbox.containerButton(), HudNodeKind.CONTAINER);
        addSource(toolbox.labelButton(), HudNodeKind.LABEL);
        addSource(toolbox.textraLabelButton(), HudNodeKind.TEXTRA_LABEL);
        addSource(toolbox.textButtonButton(), HudNodeKind.TEXT_BUTTON);
        addSource(toolbox.imageButtonButton(), HudNodeKind.IMAGE_BUTTON);
        addSource(toolbox.imageTextButtonButton(), HudNodeKind.IMAGE_TEXT_BUTTON);
        addSource(toolbox.textFieldButton(), HudNodeKind.TEXT_FIELD);
        addSource(toolbox.selectBoxButton(), HudNodeKind.SELECT_BOX);
        addSource(toolbox.listButton(), HudNodeKind.LIST);
        addSource(toolbox.checkBoxButton(), HudNodeKind.CHECK_BOX);
        addSource(toolbox.sliderButton(), HudNodeKind.SLIDER);
        addSource(toolbox.progressBarButton(), HudNodeKind.PROGRESS_BAR);
        addSource(toolbox.scrollPaneButton(), HudNodeKind.SCROLL_PANE);
        addSource(toolbox.windowButton(), HudNodeKind.WINDOW);
        addSource(toolbox.dialogButton(), HudNodeKind.DIALOG);
        dragAndDrop.addTarget(new CanvasTarget(canvasHost));
    }

    private void addSource(ImageButton actor, HudNodeKind kind) {
        dragAndDrop.addSource(new DragAndDrop.Source(actor) {
            @Override public DragAndDrop.Payload dragStart(InputEvent event, float x, float y, int pointer) {
                HudScreenEditorDocument active = activeHudDocument();
                if (active == null || !session.projects(active) || !session.isWidgetAvailable(kind)) return null;
                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(new WidgetDrag(kind, active, session.selectedNodeId()));
                payload.setDragActor(createGhost(actor));
                session.beginToolboxWidgetDrag();
                return payload;
            }

            @Override public void dragStop(InputEvent event, float x, float y, int pointer,
                                           DragAndDrop.Payload payload, DragAndDrop.Target target) {
                session.endToolboxWidgetDrag();
            }
        });
    }

    /** A separate, non-pickable actor keeps the source button and its shared drawable untouched. */
    private static Image createGhost(ImageButton source) {
        Image ghost = new Image(source.getStyle().imageUp);
        ghost.setSize(GHOST_SIZE, GHOST_SIZE);
        ghost.setColor(1f, 1f, 1f, GHOST_ALPHA);
        ghost.setTouchable(Touchable.disabled);
        return ghost;
    }

    private final class CanvasTarget extends DragAndDrop.Target {
        private HudEditorSession.WidgetDropTarget lastResolvedTarget;
        private float lastResolvedX = Float.NaN;
        private float lastResolvedY = Float.NaN;

        private CanvasTarget(Actor actor) { super(actor); }

        @Override public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload,
                                      float x, float y, int pointer) {
            HudEditorSession.WidgetDropTarget target = resolve(payload, x, y);
            if (samePointer(x, y) && lastResolvedTarget != null
                    && (target == null || !sameDestination(lastResolvedTarget, target))) {
                clearResolution();
                session.clearWidgetDropFeedback();
                return false;
            }
            lastResolvedTarget = target;
            lastResolvedX = x;
            lastResolvedY = y;
            if (target == null) {
                session.clearWidgetDropFeedback();
                return false;
            }
            session.showWidgetDropFeedback(target);
            return true;
        }

        @Override public void reset(DragAndDrop.Source source, DragAndDrop.Payload payload) {
            clearResolution();
            session.clearWidgetDropFeedback();
        }

        @Override public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload,
                                   float x, float y, int pointer) {
            WidgetDrag drag = widgetDrag(payload);
            HudEditorSession.WidgetDropTarget target = resolve(payload, x, y);
            try {
                if (drag != null && target != null && lastResolvedTarget != null
                        && sameDestination(lastResolvedTarget, target)) {
                    if (drag.kind() == HudNodeKind.TABLE) {
                        HudEditorSession.WidgetDropTarget capturedTarget = target;
                        HudTableCreateDialog.show(canvasHost.getStage(), (rows, columns) ->
                                session.createTableAt(rows, columns, capturedTarget, drag.document()));
                    } else {
                        session.createWidgetAt(drag.kind(), target, drag.document());
                    }
                }
            } finally {
                session.clearWidgetDropFeedback();
            }
        }

        private boolean samePointer(float x, float y) {
            return Float.compare(x, lastResolvedX) == 0 && Float.compare(y, lastResolvedY) == 0;
        }

        private static boolean sameDestination(HudEditorSession.WidgetDropTarget first,
                                               HudEditorSession.WidgetDropTarget second) {
            return first.parentId().equals(second.parentId())
                    && first.preselected() == second.preselected()
                    && first.feedbackParentId().equals(second.feedbackParentId())
                    && java.util.Objects.equals(first.cellId(), second.cellId());
        }

        private void clearResolution() {
            lastResolvedTarget = null;
            lastResolvedX = Float.NaN;
            lastResolvedY = Float.NaN;
        }
    }

    private HudEditorSession.WidgetDropTarget resolve(DragAndDrop.Payload payload, float localX, float localY) {
        WidgetDrag drag = widgetDrag(payload);
        if (drag == null || activeHudDocument() != drag.document() || !session.projects(drag.document())
                || !session.isWidgetAvailable(drag.kind())) return null;
        stagePoint.set(localX, localY);
        canvasHost.localToStageCoordinates(stagePoint);
        HudEditorSession.WidgetDropTarget target =
                session.widgetDropTargetAt(stagePoint.x, stagePoint.y);
        if (target == null || session.widgetDropTargetMayYieldToPreselection(target, drag.selectedParentId())) {
            HudEditorSession.WidgetDropTarget preselected = session.preselectedWidgetDropTargetAt(drag.selectedParentId(),
                    stagePoint.x, stagePoint.y);
            if (preselected != null) target = preselected;
        }
        return target != null && session.canCreateWidgetAt(drag.kind(), target.parentId()) ? target : null;
    }

    private WidgetDrag widgetDrag(DragAndDrop.Payload payload) {
        return payload != null && payload.getObject() instanceof WidgetDrag drag ? drag : null;
    }

    private HudScreenEditorDocument activeHudDocument() {
        return documents.activeDocument() instanceof HudScreenEditorDocument hud ? hud : null;
    }

    boolean isDragging() { return dragAndDrop.isDragging(); }
    Actor dragActor() { return dragAndDrop.getDragActor(); }

    @Override public void close() {
        dragAndDrop.clear();
        session.endToolboxWidgetDrag();
    }

    private record WidgetDrag(HudNodeKind kind, HudScreenEditorDocument document,
                              String selectedParentId) { }
}
