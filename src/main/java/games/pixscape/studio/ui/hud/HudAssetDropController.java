package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Cursor;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.service.hud.HudImageAuthoringService;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragCursors;
import games.pixscape.studio.ui.asset.dnd.DragPayload;

import java.util.Objects;
import java.util.function.Consumer;

/** HUD target for the shared Assets drag payload and release lifecycle. */
public final class HudAssetDropController implements AutoCloseable {
    private final HudEditorSession session;
    private final HudImageAuthoringService imageAuthoring;
    private final EditorDocumentManager documentManager;
    private final Consumer<RuntimeException> failureHandler;
    private DragPayload observedPayload;
    private HudScreenEditorDocument capturedDocument;
    private boolean outsideHudTarget;
    private Cursor cursor;
    private boolean cursorForbidden;

    public HudAssetDropController(HudEditorSession session,
                                  HudImageAuthoringService imageAuthoring,
                                  EditorDocumentManager documentManager,
                                  Consumer<RuntimeException> failureHandler) {
        this.session = Objects.requireNonNull(session, "session");
        this.imageAuthoring = Objects.requireNonNull(imageAuthoring, "imageAuthoring");
        this.documentManager = Objects.requireNonNull(documentManager, "documentManager");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    public void update(int logicalWindowHeight, boolean targetAvailable) {
        updateAt(Gdx.input.getX(), Gdx.input.getY(), logicalWindowHeight, targetAvailable);
    }

    /** Same frame route with an explicit pointer, used by deterministic interaction tests. */
    void updateAt(int screenX, int screenY, int logicalWindowHeight, boolean targetAvailable) {
        DragPayload payload = DragContext.get().peek();
        if (payload == null) {
            forgetDrag();
            return;
        }
        observeActiveDocument();
        if (DragContext.get().peek() != payload) return;
        outsideHudTarget = false;

        HudEditorSession.ImageDropTarget target = targetAvailable
                ? session.imageDropTargetAt(screenX, screenY, logicalWindowHeight,
                payload.imageWidth, payload.imageHeight)
                : null;
        HudScreenEditorDocument active = activeHudDocument();
        boolean allowed = canAcceptObserved(payload, target, active);
        setCursor(payload, !allowed);

        if (!DragContext.get().releasePending()) return;
        DragPayload released = DragContext.get().consumeReleased();
        try {
            if (released != null && allowed) drop(released, target, capturedDocument, active);
        } catch (RuntimeException failure) {
            failureHandler.accept(failure);
        } finally {
            disposePayload(released);
            forgetDrag();
        }
    }

    boolean canAccept(DragPayload payload, HudEditorSession.ImageDropTarget target,
                      HudScreenEditorDocument captured, HudScreenEditorDocument active) {
        if (!isImagePayload(payload) || target == null || captured == null || captured != active
                || !session.projects(active)) return false;
        return target.targetsImageButton()
                ? imageAuthoring.canAssignImageButton(payload.assetId, target.imageButtonNodeId())
                : imageAuthoring.canAdd(payload.assetId, target.parentId());
    }

    boolean canAcceptObserved(DragPayload payload, HudEditorSession.ImageDropTarget target,
                              HudScreenEditorDocument active) {
        return canAccept(payload, target, capturedDocument, active);
    }

    boolean drop(DragPayload payload, HudEditorSession.ImageDropTarget target,
                 HudScreenEditorDocument captured, HudScreenEditorDocument active) {
        if (!canAccept(payload, target, captured, active)) return false;
        return target.targetsImageButton()
                ? imageAuthoring.assignImageButton(payload.assetId, target.imageButtonNodeId(), captured)
                : imageAuthoring.add(payload.assetId, target, captured) != null;
    }

    static boolean isImagePayload(DragPayload payload) {
        return payload != null && "image-file".equals(payload.type)
                && payload.assetId > 0 && (payload.paths == null || payload.paths.size <= 1);
    }

    /** Captures the editor document at drag discovery and permanently invalidates cross-document drags. */
    public void observeActiveDocument() {
        DragPayload payload = DragContext.get().peek();
        if (payload == null) {
            forgetDrag();
            return;
        }
        HudScreenEditorDocument active = activeHudDocument();
        if (payload != observedPayload) {
            clearCursor();
            disposePayload(observedPayload);
            observedPayload = payload;
            capturedDocument = active;
            outsideHudTarget = false;
        } else if (active != capturedDocument) {
            DragContext.get().cancel();
            forgetDrag();
        }
    }

    /** Releases HUD-only drag state while leaving the shared payload for another editor target. */
    public void leaveHudTarget() {
        if (outsideHudTarget) return;
        outsideHudTarget = true;
        clearCursor();
    }

    boolean isShowingForbiddenFeedback() { return cursorForbidden; }

    private HudScreenEditorDocument activeHudDocument() {
        return documentManager.activeDocument() instanceof HudScreenEditorDocument hud ? hud : null;
    }

    private void setCursor(DragPayload payload, boolean forbidden) {
        if (cursor != null && cursorForbidden == forbidden) {
            Gdx.graphics.setCursor(cursor);
            return;
        }
        clearCursor();
        cursor = forbidden ? DragCursors.makeForbiddenCursor()
                : DragCursors.makeGhostCursor(payload.ghostPixmap, payload.hotspotX, payload.hotspotY);
        cursorForbidden = forbidden;
        if (cursor != null) Gdx.graphics.setCursor(cursor);
    }

    private void forgetDrag() {
        disposePayload(observedPayload);
        observedPayload = null;
        capturedDocument = null;
        outsideHudTarget = false;
        clearCursor();
    }

    private void clearCursor() {
        boolean hadCursor = cursor != null;
        if (cursor != null) cursor.dispose();
        cursor = null;
        cursorForbidden = false;
        if (hadCursor && Gdx.graphics != null)
            Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow);
    }

    private static void disposePayload(DragPayload payload) {
        if (payload != null && payload.ghostPixmap != null) {
            payload.ghostPixmap.dispose();
            payload.ghostPixmap = null;
        }
    }

    @Override public void close() { forgetDrag(); }
}
