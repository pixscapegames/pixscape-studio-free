package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.ui.docking.DockManager;
import games.pixscape.studio.ui.docking.DockablePanel;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Selects document-specific panels through the ordinary public docking lifecycle. */
final class DocumentDockPanelCoordinator {
    private enum Presentation { DOCKED, FLOATING, HIDDEN }

    private final DockManager docks;
    private final DockablePanel scenePanel;
    private final DockablePanel hudPanel;
    private final Supplier<Stage> studioStage;
    private final Consumer<Runnable> deferUi;
    private Presentation scenePresentation = Presentation.DOCKED;
    private Presentation hudPresentation = Presentation.DOCKED;
    private EditorDocumentType activeType;
    private boolean projected;
    private boolean applying;
    // View-menu visibility notifications are still being dispatched when reconciliation runs.
    private boolean reconciliationPending;

    DocumentDockPanelCoordinator(EditorDocumentManager documents,
                                 DockManager docks,
                                 DockablePanel scenePanel,
                                 DockablePanel hudPanel,
                                 Supplier<Stage> studioStage) {
        this(documents, docks, scenePanel, hudPanel, studioStage, runnable -> {
            if (Gdx.app != null) Gdx.app.postRunnable(runnable);
            else runnable.run();
        });
    }

    DocumentDockPanelCoordinator(EditorDocumentManager documents,
                                 DockManager docks,
                                 DockablePanel scenePanel,
                                 DockablePanel hudPanel,
                                 Supplier<Stage> studioStage,
                                 Consumer<Runnable> deferUi) {
        this.docks = Objects.requireNonNull(docks, "docks");
        this.scenePanel = Objects.requireNonNull(scenePanel, "scenePanel");
        this.hudPanel = Objects.requireNonNull(hudPanel, "hudPanel");
        this.studioStage = Objects.requireNonNull(studioStage, "studioStage");
        this.deferUi = Objects.requireNonNull(deferUi, "deferUi");

        documents.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentActivated(OpenEditorDocument previous,
                                                     OpenEditorDocument current) {
                project(current != null ? current.type() : null);
            }
        });
        docks.addListener(this::panelVisibilityChanged);
        OpenEditorDocument activeDocument = documents.activeDocument();
        if (activeDocument != null) project(activeDocument.type());
    }

    private void project(EditorDocumentType type) {
        if (projected && type == activeType) return;
        reconcilePendingPresentation();
        captureActivePresentation();
        activeType = type;
        projected = true;
        applyCurrentPresentation();
    }

    private void captureActivePresentation() {
        if (!projected) return;
        if (activeType == EditorDocumentType.SCENE) {
            scenePresentation = observe(scenePanel);
        } else if (activeType == EditorDocumentType.HUD_SCREEN) {
            hudPresentation = observe(hudPanel);
        }
    }

    private void panelVisibilityChanged(DockablePanel panel, boolean visible) {
        if (applying || panel != scenePanel && panel != hudPanel) return;

        if (!isActive(panel)) {
            if (visible) requestReconciliation();
            return;
        }

        Presentation presentation = visible ? observe(panel) : Presentation.HIDDEN;
        if (panel == scenePanel) scenePresentation = presentation;
        else hudPresentation = presentation;
    }

    private void requestReconciliation() {
        if (reconciliationPending) return;
        reconciliationPending = true;
        deferUi.accept(() -> {
            if (!reconciliationPending) return;
            reconciliationPending = false;
            applyCurrentPresentation();
        });
    }

    private void reconcilePendingPresentation() {
        if (!reconciliationPending) return;
        reconciliationPending = false;
        applyCurrentPresentation();
    }

    private void applyCurrentPresentation() {
        apply(scenePanel, scenePresentation, activeType == EditorDocumentType.SCENE);
        apply(hudPanel, hudPresentation, activeType == EditorDocumentType.HUD_SCREEN);
    }

    private boolean isActive(DockablePanel panel) {
        return panel == scenePanel && activeType == EditorDocumentType.SCENE
                || panel == hudPanel && activeType == EditorDocumentType.HUD_SCREEN;
    }

    private Presentation observe(DockablePanel panel) {
        if (!panel.isVisible() || panel.getParent() == null) return Presentation.HIDDEN;
        Stage panelStage = panel.getStage();
        Stage mainStage = studioStage.get();
        return panelStage != null && panelStage != mainStage
                ? Presentation.FLOATING : Presentation.DOCKED;
    }

    private void apply(DockablePanel panel, Presentation presentation, boolean active) {
        applying = true;
        try {
            if (!active || presentation == Presentation.HIDDEN) {
                if (panel.isVisible() || panel.getParent() != null) docks.hide(panel);
            } else if (presentation == Presentation.FLOATING) {
                docks.undock(panel);
            } else {
                docks.dockToDefault(panel);
            }
        } finally {
            applying = false;
        }
    }
}
