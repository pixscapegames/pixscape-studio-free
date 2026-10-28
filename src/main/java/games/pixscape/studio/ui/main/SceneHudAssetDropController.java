package games.pixscape.studio.ui.main;

import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.hud.SceneHudAssociationService;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragPayload;

import java.util.Objects;

/** Captures and revalidates the Scene destination for a shared HUD Screen drag. */
final class SceneHudAssetDropController {
    private final SceneHudAssociationService associations;
    private final EditorDocumentManager documents;
    private DragPayload observedPayload;
    private SceneEditorDocument capturedDocument;

    SceneHudAssetDropController(SceneHudAssociationService associations,
                                EditorDocumentManager documents) {
        this.associations = Objects.requireNonNull(associations);
        this.documents = Objects.requireNonNull(documents);
    }

    void observeActiveDocument() {
        DragPayload payload = DragContext.get().peek();
        if (!isHudScreenPayload(payload)) {
            forget();
            return;
        }
        SceneEditorDocument active = activeScene();
        if (payload != observedPayload) {
            observedPayload = payload;
            capturedDocument = active;
        } else if (active != capturedDocument) {
            disposePayload(payload);
            DragContext.get().cancel();
            forget();
        }
    }

    boolean canAccept(DragPayload payload, SceneEditorContext canvasContext) {
        SceneEditorDocument active = activeScene();
        return isHudScreenPayload(payload) && payload == observedPayload
                && capturedDocument != null && capturedDocument == active
                && capturedDocument.context() == canvasContext
                && associations.canAssociate(capturedDocument, payload.path);
    }

    boolean drop(DragPayload payload, SceneEditorContext canvasContext) {
        if (!canAccept(payload, canvasContext)) return false;
        associations.associate(capturedDocument, payload.path);
        forget();
        return true;
    }

    static boolean isHudScreenPayload(DragPayload payload) {
        return payload != null && "hud-screen".equals(payload.type)
                && payload.path != null && !payload.path.isBlank();
    }

    private SceneEditorDocument activeScene() {
        return documents.activeDocument() instanceof SceneEditorDocument scene ? scene : null;
    }

    private void forget() {
        observedPayload = null;
        capturedDocument = null;
    }

    private static void disposePayload(DragPayload payload) {
        if (payload != null && payload.ghostPixmap != null) {
            payload.ghostPixmap.dispose();
            payload.ghostPixmap = null;
        }
    }
}
