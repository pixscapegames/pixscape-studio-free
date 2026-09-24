package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudSelectionTarget;

import java.util.Objects;

/** Gives editor overlay targets precedence over authored HUD Actors and the world canvas. */
public final class HudCanvasSelectionInputListener extends InputListener {
    private final HudEditorSession session;
    private int contextPointer = -1;
    private String contextNodeId;
    private String contextCellId;
    private int pendingMovePointer = -1;
    private float pendingMoveStageX;
    private float pendingMoveStageY;
    private boolean pendingMoveResolved;
    private int cellDragPointer = -1;
    private float cellDragStageX;
    private float cellDragStageY;
    private boolean cellDragStarted;

    public HudCanvasSelectionInputListener(HudEditorSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    @Override public boolean touchDown(InputEvent event, float x, float y,
                                       int pointer, int button) {
        requestKeyboardFocus(event);
        if (button == Input.Buttons.RIGHT) {
            contextPointer = -1;
            contextNodeId = null;
            contextCellId = null;
            HudSelectionTarget target = session.overlayTargetAt(event.getStageX(), event.getStageY());
            if (target == null) return false;
            if (target.type() == HudSelectionTarget.Type.CELL) {
                if (!session.selectedCellRange().contains(target.nodeId())) session.selectCell(target.nodeId());
                contextCellId = target.nodeId();
            } else {
                session.selectNode(target.nodeId());
                contextNodeId = target.nodeId();
            }
            contextPointer = pointer;
            return true;
        }
        if (button != Input.Buttons.LEFT) return false;
        if (shiftPressed()) {
            boolean selected = session.selectCellRangeAt(event.getStageX(), event.getStageY());
            if (selected) event.stop();
            return selected;
        }
        resetStalePendingMove();
        if (cellDragPointer != -1 && !session.hasCellContentGesture()) clearCellDragState();
        if (session.beginTransformHandleGesture(event.getStageX(), event.getStageY())) {
            session.updateTransformCursorAt(event.getStageX(), event.getStageY());
            event.stop();
            return true;
        }
        if (session.beginPendingMoveGesture(event.getStageX(), event.getStageY())) {
            pendingMovePointer = pointer;
            pendingMoveStageX = event.getStageX();
            pendingMoveStageY = event.getStageY();
            pendingMoveResolved = false;
            event.stop();
            return true;
        }
        if (session.beginCellContentGesture(event.getStageX(), event.getStageY())) {
            cellDragPointer = pointer;
            cellDragStageX = event.getStageX();
            cellDragStageY = event.getStageY();
            cellDragStarted = false;
            event.stop();
            return true;
        }
        boolean handled = session.selectOverlayTargetAt(event.getStageX(), event.getStageY());
        if (handled) event.stop();
        return handled;
    }

    @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
        if (pointer == pendingMovePointer) {
            if (!pendingMoveResolved && crossedDragThreshold(event.getStageX(), event.getStageY())) {
                pendingMoveResolved = true;
                session.startPendingMoveGesture(event.getStageX(), event.getStageY());
            } else if (pendingMoveResolved) {
                session.updateTransformGesture(event.getStageX(), event.getStageY());
            }
            session.updateTransformCursorAt(event.getStageX(), event.getStageY());
            event.stop();
            return;
        }
        if (pointer == cellDragPointer) {
            if (!cellDragStarted && crossedCellDragThreshold(event.getStageX(), event.getStageY())) {
                cellDragStarted = session.startCellContentGesture(event.getStageX(), event.getStageY(),
                        pointableOnHost(event));
            } else if (cellDragStarted) {
                session.updateCellContentGesture(event.getStageX(), event.getStageY(), pointableOnHost(event));
            }
            event.stop();
            return;
        }
        if (session.updateTransformGesture(event.getStageX(), event.getStageY())) {
            session.updateTransformCursorAt(event.getStageX(), event.getStageY());
            event.stop();
        }
    }

    @Override public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
        if (event.isTouchFocusCancel()) {
            contextPointer = -1;
            contextNodeId = null;
            contextCellId = null;
            clearPendingMoveState();
            clearCellDragState();
            session.cancelCellContentGesture();
            session.cancelTransformGesture();
            event.stop();
            return;
        }
        if (button == Input.Buttons.RIGHT && pointer == contextPointer) {
            String nodeId = contextNodeId;
            String cellId = contextCellId;
            contextPointer = -1;
            contextNodeId = null;
            contextCellId = null;
            if (cellId != null) {
                HudTableContextMenu.show(event.getStage(), event.getStageX(), event.getStageY(),
                        session, cellId, false);
                event.handle();
            } else if (nodeId != null) {
                HudNodeContextMenu.show(event.getStage(), event.getStageX(), event.getStageY(),
                        session, nodeId);
                event.handle();
            }
            return;
        }
        if (button == Input.Buttons.LEFT && pointer == pendingMovePointer) {
            boolean resolved = pendingMoveResolved;
            boolean deferredClickStillValid = session.hasPendingMoveGesture();
            clearPendingMoveState();
            if (resolved) {
                if (session.hasActiveTransformGesture()) {
                    session.endTransformGesture(event.getStageX(), event.getStageY());
                } else {
                    session.cancelPendingMoveGesture();
                }
            } else {
                session.cancelPendingMoveGesture();
                if (deferredClickStillValid) {
                    session.selectOverlayTargetAt(event.getStageX(), event.getStageY());
                }
            }
            session.updateTransformCursorAt(event.getStageX(), event.getStageY());
            event.stop();
            return;
        }
        if (button == Input.Buttons.LEFT && pointer == cellDragPointer) {
            session.endCellContentGesture(event.getStageX(), event.getStageY(), pointableOnHost(event));
            clearCellDragState();
            event.stop();
            return;
        }
        if (button == Input.Buttons.LEFT && session.endTransformGesture(event.getStageX(), event.getStageY())) {
            session.updateTransformCursorAt(event.getStageX(), event.getStageY());
            event.stop();
        }
    }

    @Override public boolean mouseMoved(InputEvent event, float x, float y) {
        session.updateHoveredOverlayTargetAt(event.getStageX(), event.getStageY());
        session.updateTransformCursorAt(event.getStageX(), event.getStageY());
        return false;
    }

    @Override public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
        if (pointer == -1 && event.getStage() != null && event.getListenerActor() != null) {
            event.getStage().setScrollFocus(event.getListenerActor());
        }
    }

    @Override public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
        if (pointer == -1) releaseOwnedScrollFocus(event.getStage(), event.getListenerActor());
        if (!session.hasActiveTransformGesture()) session.clearHoveredOverlayTarget();
        if (!session.hasActiveResizeGesture()) {
            session.clearTransformCursor();
        }
    }

    static void releaseOwnedScrollFocus(Stage stage, Actor owner) {
        if (stage != null && stage.getScrollFocus() == owner) stage.setScrollFocus(null);
    }

    @Override public boolean scrolled(InputEvent event, float x, float y,
                                      float amountX, float amountY) {
        boolean handled = session.scrollHudAt(event.getStageX(), event.getStageY(), amountX, amountY);
        if (handled) event.stop();
        return handled;
    }

    private static void requestKeyboardFocus(InputEvent event) {
        if (event.getStage() != null && event.getListenerActor() != null) {
            event.getStage().setKeyboardFocus(event.getListenerActor());
        }
    }

    private boolean crossedDragThreshold(float stageX, float stageY) {
        float deltaX = stageX - pendingMoveStageX;
        float deltaY = stageY - pendingMoveStageY;
        return deltaX * deltaX + deltaY * deltaY
                >= HudEditorSession.TRANSFORM_DRAG_THRESHOLD_LOGICAL_PIXELS
                * HudEditorSession.TRANSFORM_DRAG_THRESHOLD_LOGICAL_PIXELS;
    }

    private void resetStalePendingMove() {
        if (pendingMovePointer != -1 && !session.hasPendingMoveGesture()
                && !session.hasActiveTransformGesture()) clearPendingMoveState();
    }

    private void clearPendingMoveState() {
        pendingMovePointer = -1;
        pendingMoveResolved = false;
    }

    private boolean crossedCellDragThreshold(float stageX, float stageY) {
        float dx = stageX - cellDragStageX;
        float dy = stageY - cellDragStageY;
        float threshold = HudEditorSession.TRANSFORM_DRAG_THRESHOLD_LOGICAL_PIXELS;
        return dx * dx + dy * dy >= threshold * threshold;
    }

    private static boolean pointableOnHost(InputEvent event) {
        Stage stage = event.getStage();
        return stage == null || stage.hit(event.getStageX(), event.getStageY(), true)
                == event.getListenerActor();
    }

    private void clearCellDragState() {
        cellDragPointer = -1;
        cellDragStarted = false;
    }

    private static boolean shiftPressed() {
        return Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT));
    }
}
