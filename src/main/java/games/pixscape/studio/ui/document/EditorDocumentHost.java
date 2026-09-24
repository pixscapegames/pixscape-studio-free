package games.pixscape.studio.ui.document;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;
import games.pixscape.studio.document.EditorDocumentKey;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.ui.config.CommonLayout;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Center tab strip and content frame projected from {@link EditorDocumentManager}. */
public final class EditorDocumentHost extends VisTable {
    private final EditorDocumentManager manager;
    private final TabbedPane tabs = new TabbedPane("document-tabs");
    private final ObjectMap<EditorDocumentKey, EditorDocumentTab> tabsByKey = new ObjectMap<>();
    private final Consumer<Runnable> deferUiReconciliation;
    private boolean synchronizing;
    private boolean userRemovalInProgress;
    private boolean suppressVisUiFallbackActivation;
    private boolean reconciliationQueued;

    public EditorDocumentHost(EditorDocumentManager manager, Actor centerContent) {
        this(manager, centerContent, runnable -> {
            if (Gdx.app != null) Gdx.app.postRunnable(runnable);
            else centerContent.addAction(Actions.run(runnable));
        });
    }

    EditorDocumentHost(EditorDocumentManager manager,
                       Actor centerContent,
                       Consumer<Runnable> deferUiReconciliation) {
        this.manager = manager;
        this.deferUiReconciliation = Objects.requireNonNull(
                deferUiReconciliation, "deferUiReconciliation");
        setTouchable(Touchable.childrenOnly);
        tabs.getTabsPane().getHorizontalFlowGroup().setSpacing(CommonLayout.DOCUMENT_TAB_GAP);

        VisTable contentFrame = new VisTable();
        // The frame visually owns the center but is not a control. Leaving it touchable makes it
        // the uiStage hit target and hides the Stage background from Scene context-menu routing.
        contentFrame.setTouchable(Touchable.childrenOnly);
        contentFrame.add(centerContent).grow();

        add(tabs.getTable()).left().growX().row();
        add(contentFrame).grow().padTop(-1f).row();

        tabs.addListener(new TabbedPaneListener() {
            @Override public void switchedTab(Tab tab) {
                if (synchronizing || !(tab instanceof EditorDocumentTab documentTab)) return;
                if (suppressVisUiFallbackActivation) {
                    suppressVisUiFallbackActivation = false;
                    return;
                }
                if (!userRemovalInProgress) {
                    manager.activate(documentTab.document().key());
                }
            }

            @Override public void removedTab(Tab tab) {
                if (!synchronizing && tab instanceof EditorDocumentTab documentTab) {
                    boolean wasActive = tabs.getActiveTab() == tab;
                    boolean closed;
                    userRemovalInProgress = true;
                    try {
                        closed = manager.requestClose(documentTab.document().key());
                    } finally {
                        userRemovalInProgress = false;
                    }
                    if (wasActive) suppressVisUiFallbackActivation = tabs.getTabs().notEmpty();
                    if (wasActive || !closed) queueUiReconciliation();
                }
            }

            @Override public void removedAllTabs() {}
        });

        manager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentOpened(OpenEditorDocument document) { addFromManager(document); }
            @Override public void documentClosed(OpenEditorDocument document) { removeFromManager(document); }
            @Override public void documentTitleChanged(OpenEditorDocument document) {
                EditorDocumentTab tab = tabsByKey.get(document.key());
                if (tab != null && tab.getPane() == tabs) tabs.updateTabTitle(tab);
            }
            @Override public void documentActivated(OpenEditorDocument previous, OpenEditorDocument current) {
                if (!userRemovalInProgress) selectFromManager(current);
            }
        });
        rebuildTabs();
    }

    public TabbedPane tabbedPane() { return tabs; }
    public int documentTabCount() { return tabs.getTabs().size; }
    public EditorDocumentKey selectedKey() {
        Tab active = tabs.getActiveTab();
        return active instanceof EditorDocumentTab documentTab ? documentTab.document().key() : null;
    }

    /** Test seam matching a user selection without exposing the Tab as lifecycle authority. */
    public boolean requestActivation(EditorDocumentKey key) {
        EditorDocumentTab tab = tabsByKey.get(key);
        if (tab == null) return false;
        tabs.switchTab(tab);
        return true;
    }

    private void rebuildTabs() {
        synchronizing = true;
        try {
            tabs.removeAll();
            tabsByKey.clear();
            for (OpenEditorDocument document : manager.documents()) {
                EditorDocumentTab tab = new EditorDocumentTab(document);
                tabsByKey.put(document.key(), tab);
                tabs.add(tab);
            }
            selectFromManager(manager.activeDocument());
        } finally {
            synchronizing = false;
        }
    }

    private void addFromManager(OpenEditorDocument document) {
        if (tabsByKey.containsKey(document.key())) return;
        boolean wasSynchronizing = synchronizing;
        synchronizing = true;
        try {
            EditorDocumentTab tab = new EditorDocumentTab(document);
            tabsByKey.put(document.key(), tab);
            tabs.add(tab);
        } finally {
            synchronizing = wasSynchronizing;
        }
    }

    private void removeFromManager(OpenEditorDocument document) {
        EditorDocumentTab tab = tabsByKey.remove(document.key());
        if (tab == null || tab.getPane() != tabs) return;
        boolean wasSynchronizing = synchronizing;
        synchronizing = true;
        try {
            tabs.remove(tab);
        } finally {
            synchronizing = wasSynchronizing;
        }
    }

    private void queueUiReconciliation() {
        if (reconciliationQueued) return;
        reconciliationQueued = true;
        deferUiReconciliation.accept(() -> {
            reconciliationQueued = false;
            suppressVisUiFallbackActivation = false;
            if (tabProjectionMatchesManager()) selectFromManager(manager.activeDocument());
            else rebuildTabs();
        });
    }

    private boolean tabProjectionMatchesManager() {
        List<OpenEditorDocument> documents = manager.documents();
        if (tabs.getTabs().size != documents.size()) return false;
        for (int i = 0; i < documents.size(); i++) {
            Tab tab = tabs.getTabs().get(i);
            if (!(tab instanceof EditorDocumentTab documentTab)
                    || !documentTab.document().key().equals(documents.get(i).key())) return false;
        }
        return true;
    }

    private void selectFromManager(OpenEditorDocument document) {
        if (document == null) return;
        EditorDocumentTab tab = tabsByKey.get(document.key());
        if (tab == null || tabs.getActiveTab() == tab) return;
        boolean wasSynchronizing = synchronizing;
        synchronizing = true;
        try {
            tabs.switchTab(tab);
        } finally {
            synchronizing = wasSynchronizing;
        }
    }
}
