package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudTableRow;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.ui.tree.StudioTreeUiSupport;

import java.util.Objects;
import java.util.function.Consumer;

/** Persistent native tree projection of the active HUD in the Studio Items area. */
public final class HudHierarchyPanel extends VisTable {
    private final HudEditorSession session;
    private final Consumer<Runnable> deferUi;
    private final HudVisTree tree = new HudVisTree();
    private final VisScrollPane scroller = StudioTreeUiSupport.createScrollPane(tree);
    private final ObjectMap<String, Array<String>> expandedNodeIdsByScreen = new ObjectMap<>();
    private boolean suppressTreeSelectionEvents;
    private boolean refreshPending;
    private boolean refreshing;
    private int projectionCount;
    private String projectedScreenId;
    private String projectedSelectionKey;
    private HudDocumentV1 projectedDocument;
    private boolean rebuildPending;

    public HudHierarchyPanel(HudEditorSession session, EditorDocumentManager documentManager) {
        this(session, documentManager, runnable -> {
            if (Gdx.app == null) {
                throw new IllegalStateException("HUD hierarchy refresh requires the Studio UI loop.");
            }
            Gdx.app.postRunnable(runnable);
        });
    }

    HudHierarchyPanel(HudEditorSession session, EditorDocumentManager documentManager,
                      Consumer<Runnable> deferUi) {
        this.session = session;
        this.deferUi = deferUi;
        top().left();
        addCaptureListener(testModeGate());
        tree.setIndentSpacing(25f);
        tree.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (suppressTreeSelectionEvents) return;
                HudTreeNode selected = tree.getSelectedNode();
                if (tree.getStage() != null) tree.getStage().setKeyboardFocus(tree);
                if (selected != null && selected.cellId() != null) session.selectCell(selected.cellId());
                else if (selected != null && selected.nodeId() != null) session.selectNode(selected.nodeId());
            }
        });
        tree.addCaptureListener(new InputListener() {
            private int contextPointer = -1;
            private HudTreeNode contextNode;

            @Override public boolean touchDown(InputEvent event, float x, float y,
                                               int pointer, int button) {
                Stage stage = event.getStage();
                if (stage != null && (button == Input.Buttons.LEFT || button == Input.Buttons.RIGHT)) {
                    stage.setKeyboardFocus(tree);
                }
                HudTreeNode node = tree.getNodeAt(y);
                if (button == Input.Buttons.LEFT && node != null && node.cellId() != null
                        && (node.getChildren().isEmpty() || x >= node.getActor().getX())
                        && Gdx.input != null && (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                        || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT))) {
                    session.selectCellRange(node.cellId());
                    event.stop();
                    return true;
                }
                if (button != Input.Buttons.RIGHT) return false;
                contextPointer = -1;
                contextNode = null;
                if (node == null) return false;
                if (node.cellId() != null) {
                    if (!session.selectedCellRange().contains(node.cellId())) session.selectCell(node.cellId());
                } else if (node.rowAnchorCellId() != null) {
                    session.selectCell(node.rowAnchorCellId());
                } else if (node.nodeId() != null) session.selectNode(node.nodeId());
                else return false;
                contextPointer = pointer;
                contextNode = node;
                return true;
            }

            @Override public void touchUp(InputEvent event, float x, float y,
                                          int pointer, int button) {
                if (button != Input.Buttons.RIGHT || pointer != contextPointer) return;
                HudTreeNode node = contextNode;
                contextPointer = -1;
                contextNode = null;
                if (node == null) return;
                if (node.cellId() != null || node.rowAnchorCellId() != null) {
                    HudTableContextMenu.show(event.getStage(), event.getStageX(), event.getStageY(),
                            session, node.cellId() != null ? node.cellId() : node.rowAnchorCellId(),
                            node.type() == HudTreeNode.Type.ROW);
                } else if (node.nodeId() != null) {
                    HudNodeContextMenu.show(event.getStage(), event.getStageX(), event.getStageY(),
                            session, node.nodeId());
                }
                event.handle();
            }
        });
        add(scroller).grow();

        session.addListener(this::requestSessionRefresh);
        documentManager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentActivated(OpenEditorDocument previous,
                                                     OpenEditorDocument current) {
                requestRefresh();
            }

            @Override public void documentTitleChanged(OpenEditorDocument document) {
                if (document == documentManager.activeDocument()) requestRefresh();
            }
        });
        rebuildProjection();
    }

    private InputListener testModeGate() {
        return new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y,
                                               int pointer, int button) {
                if (!session.isTestMode()) return false;
                event.stop();
                return true;
            }
            @Override public boolean keyDown(InputEvent event, int keycode) {
                if (!session.isTestMode()) return false;
                event.stop();
                return true;
            }
            @Override public boolean scrolled(InputEvent event, float x, float y,
                                              float amountX, float amountY) {
                return session.isTestMode();
            }
        };
    }

    void requestRefresh() {
        scheduleRefresh(true);
    }

    private void requestSessionRefresh() {
        scheduleRefresh(false);
    }

    private void scheduleRefresh(boolean rebuild) {
        HudTableContextMenu.closeIfStale(session);
        rebuildPending |= rebuild;
        if (refreshPending) return;
        refreshPending = true;
        deferUi.accept(() -> {
            if (!refreshPending) return;
            refreshPending = false;
            boolean needsRebuild = rebuildPending || session.document() != projectedDocument
                    || !Objects.equals(session.screenId(), projectedScreenId);
            rebuildPending = false;
            if (needsRebuild) rebuildProjection();
            else projectSelectionOnly();
        });
    }

    private void projectSelectionOnly() {
        suppressTreeSelectionEvents = true;
        tree.getSelection().setProgrammaticChangeEvents(false);
        try {
            tree.getSelection().clear();
            projectSelection();
        } finally {
            tree.getSelection().setProgrammaticChangeEvents(true);
            suppressTreeSelectionEvents = false;
        }
    }

    private void rebuildProjection() {
        if (refreshing) {
            requestRefresh();
            return;
        }
        refreshing = true;
        suppressTreeSelectionEvents = true;
        try {
            projectionCount++;
            captureExpandedNodes();
            tree.getSelection().setProgrammaticChangeEvents(false);
            tree.getSelection().clear();
            tree.clearNodes();

            HudDocumentV1 document = session.document();
            if (document != null && document.root != null) {
                tree.add(buildNode(document.root));
                restoreExpandedNodes(session.screenId());
            }
            projectSelection();
            projectedScreenId = session.screenId();
            projectedDocument = document;
        } finally {
            tree.getSelection().setProgrammaticChangeEvents(true);
            suppressTreeSelectionEvents = false;
            refreshing = false;
        }
    }

    private HudTreeNode buildNode(HudNode authoredNode) {
        HudTreeNode projected = new HudTreeNode(authoredNode.id, authoredNode.kind);
        tree.registerNode(projected);
        if (authoredNode.dialog != null && authoredNode.dialog.resultButtons != null
                && !authoredNode.dialog.resultButtons.isEmpty()) {
            HudTreeNode results = HudTreeNode.results(authoredNode.id);
            tree.registerNode(results);
            for (var entry : authoredNode.dialog.resultButtons)
                if (entry != null && entry.button != null) results.add(buildNode(entry.button));
            projected.add(results);
        }
        if (authoredNode.children != null) {
            for (HudChild child : authoredNode.children) {
                if (child != null && child.node != null) projected.add(buildNode(child.node));
            }
        }
        if (authoredNode.table != null && authoredNode.table.rows != null) {
            for (int rowIndex = 0; rowIndex < authoredNode.table.rows.size(); rowIndex++) {
                HudTableRow row = authoredNode.table.rows.get(rowIndex);
                if (row == null || row.cells == null || row.cells.isEmpty()) continue;
                HudTreeNode rowNode = HudTreeNode.row(rowIndex, row.cells.get(0).id);
                tree.registerNode(rowNode);
                int column = 1;
                for (HudTableCell cell : row.cells) {
                    if (cell == null) continue;
                    int end = column + cell.colspan - 1;
                    String label = "Cell " + column + (end > column ? "–" + end : "")
                            + (cell.content == null ? " — empty" : "");
                    HudTreeNode cellNode = HudTreeNode.cell(cell.id, label);
                    tree.registerNode(cellNode);
                    if (cell.content != null) cellNode.add(buildNode(cell.content));
                    rowNode.add(cellNode);
                    column = end + 1;
                }
                projected.add(rowNode);
            }
        }
        return projected;
    }

    private void captureExpandedNodes() {
        if (projectedScreenId == null || tree.getRootNodes().isEmpty()) return;
        Array<String> expanded = new Array<>();
        tree.findExpandedValues(expanded);
        expandedNodeIdsByScreen.put(projectedScreenId, expanded);
    }

    private void restoreExpandedNodes(String screenId) {
        Array<String> expanded = screenId != null ? expandedNodeIdsByScreen.get(screenId) : null;
        if (expanded == null) tree.expandAll();
        else tree.restoreExpandedValues(expanded);
    }

    private void projectSelection() {
        String selectedNodeId = session.selectedNodeId();
        String selectedCellId = session.selectedCellId();
        HudTreeNode selected = selectedCellId != null
                ? tree.findCell(selectedCellId) : tree.findNode(selectedNodeId);
        String selectionKey = session.screenId() + "\u0000" + selectedNodeId + "\u0000" + selectedCellId;
        boolean selectionChanged = !Objects.equals(selectionKey, projectedSelectionKey);
        projectedSelectionKey = selectionKey;

        if (selected != null) {
            if (selectionChanged) selected.expandTo();
            // Keep the session anchor first for native Tree#getSelectedNode.
            tree.getSelection().add(selected);
            if (selectedCellId != null) {
                for (String cellId : session.selectedCellRange()) {
                    if (cellId.equals(selectedCellId)) continue;
                    HudTreeNode rangeCell = tree.findCell(cellId);
                    if (rangeCell != null) tree.getSelection().add(rangeCell);
                }
            }
            if (selectionChanged) {
                StudioTreeUiSupport.scrollToNode(scroller, tree, selected.getActor(), true);
            }
        }
    }

    HudVisTree tree() { return tree; }
    VisScrollPane scroller() { return scroller; }
    int projectionCount() { return projectionCount; }
    boolean refreshPending() { return refreshPending; }

    public boolean ownsKeyboardFocus(Actor focus) {
        for (Actor actor = focus; actor != null; actor = actor.getParent()) {
            if (actor == this) return true;
        }
        return false;
    }

}
