package games.pixscape.studio.service.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import games.pixscape.runtime.hud.HudMaterializer;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.MaterializedHud;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudFontReferences;
import games.pixscape.runtime.hud.document.HudTextButtonData;
import games.pixscape.runtime.hud.document.HudCheckBoxData;
import games.pixscape.runtime.hud.document.HudSliderData;
import games.pixscape.runtime.hud.document.HudProgressBarData;
import games.pixscape.runtime.hud.document.HudScrollPaneData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.hud.document.HudSliderOrientation;
import games.pixscape.runtime.hud.document.HudImageButtonData;
import games.pixscape.runtime.hud.document.HudImageTextButtonData;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetDisplayInfo;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.service.atlas.HudImageAssetRef;
import games.pixscape.studio.document.HudScreenEditorDocument;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.OrthographicCamera;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import space.earlygrey.shapedrawer.ShapeDrawer;
import com.github.tommyettinger.textra.TypingLabel;

/** Active-only native authoring preview and navigation projection for the selected HUD document. */
public final class HudEditorSession implements Disposable {
    public static final float TRANSFORM_DRAG_THRESHOLD_LOGICAL_PIXELS = 4f;
    private static final String DEFAULT_LABEL_TEXT = "Label";
    private static final String DEFAULT_TEXTRA_LABEL_TEXT = "Text";
    private static final String DEFAULT_TEXT_BUTTON_TEXT = "Button";
    public enum ImageButtonImageSlot {
        UP("Image"), DOWN("Down image"), OVER("Over image"), DISABLED("Disabled image"),
        CHECKED("Checked image"), CHECKED_DOWN("Checked down image"),
        CHECKED_OVER("Checked over image");

        private final String label;
        ImageButtonImageSlot(String label) { this.label = label; }
        public String label() { return label; }
        public HudImageData get(HudImageButtonData data) {
            return switch (this) {
                case UP -> data.imageUp;
                case DOWN -> data.imageDown;
                case OVER -> data.imageOver;
                case DISABLED -> data.imageDisabled;
                case CHECKED -> data.imageChecked;
                case CHECKED_DOWN -> data.imageCheckedDown;
                case CHECKED_OVER -> data.imageCheckedOver;
            };
        }
        public void set(HudImageButtonData data, HudImageData image) {
            switch (this) {
                case UP -> data.imageUp = image;
                case DOWN -> data.imageDown = image;
                case OVER -> data.imageOver = image;
                case DISABLED -> data.imageDisabled = image;
                case CHECKED -> data.imageChecked = image;
                case CHECKED_DOWN -> data.imageCheckedDown = image;
                case CHECKED_OVER -> data.imageCheckedOver = image;
            }
        }
        public HudImageData get(HudImageTextButtonData data) {
            return switch (this) {
                case UP -> data.imageUp;
                case DOWN -> data.imageDown;
                case OVER -> data.imageOver;
                case DISABLED -> data.imageDisabled;
                case CHECKED -> data.imageChecked;
                case CHECKED_DOWN -> data.imageCheckedDown;
                case CHECKED_OVER -> data.imageCheckedOver;
            };
        }
    }
    public record ImageResourceOption(String resourceName, String label) { }
    public record FontResourceOption(Integer assetId, String label) { }
    public record SkinResourceOption(int assetId, String skinId, String label) { }
    public enum Status { CLOSED, READY, ERROR }
    public record ImageDropTarget(String parentId,
                                  games.pixscape.runtime.hud.document.HudFreePlacement placement,
                                  float imageWidth, float imageHeight,
                                  String imageButtonNodeId) {
        public ImageDropTarget(String parentId,
                               games.pixscape.runtime.hud.document.HudFreePlacement placement) {
            this(parentId, placement, 0f, 0f, null);
        }
        public ImageDropTarget(String parentId,
                               games.pixscape.runtime.hud.document.HudFreePlacement placement,
                               float imageWidth, float imageHeight) {
            this(parentId, placement, imageWidth, imageHeight, null);
        }
        public boolean targetsImageButton() { return imageButtonNodeId != null; }
    }
    /** A toolbox-widget destination resolved from the live authoring preview. */
    public record WidgetDropTarget(String parentId, HudFreePlacement placement,
                                   boolean preselected, boolean fallback, String feedbackParentId,
                                   String cellId) {
        public WidgetDropTarget(String parentId, HudFreePlacement placement) {
            this(parentId, placement, false, false, parentId, null);
        }
        public WidgetDropTarget(String parentId, HudFreePlacement placement, boolean preselected,
                                boolean fallback, String feedbackParentId) {
            this(parentId, placement, preselected, fallback, feedbackParentId, null);
        }
    }

    private final List<Runnable> listeners = new ArrayList<>();
    private final HudDocumentEditSession.ActivePreview previewBinding = this::installCandidate;
    private final HudDocumentEditSession.HistoryNavigationListener historyNavigationListener =
            this::restoreDeletionSelectionAfterHistoryNavigation;
    private final Map<Long, DeletionSelection> deletionsByRevision = new HashMap<>();
    private final Map<Long, CreationSelection> creationsByRevision = new HashMap<>();
    private final Map<Long, CellMoveSelection> cellMovesByRevision = new HashMap<>();
    private final Map<Long, StructureSelection> structuresByRevision = new HashMap<>();
    private final Supplier<AssetMetaDatabase> assetDatabase;
    private final Supplier<Batch> batch;
    private final Supplier<ShapeDrawer> drawer;
    private HudScreenEditorDocument editorDocument;
    private FileHandle projectDir;
    private HudAuthoringSession authoringSession;
    private MaterializedHud materialized;
    private String screenId;
    private HudScreenAsset asset;
    private HudDocumentV1 document;
    private HudValidationResult validation;
    private String selectedNodeId;
    private String selectedCellId;
    private List<String> selectedCellRange = List.of();
    private String cellRangeEndId;
    private String errorMessage;
    private String testModeErrorMessage;
    private Status status = Status.CLOSED;
    private int previewX = Integer.MIN_VALUE;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private boolean resourcesStale;
    private boolean showLayoutBounds = true;
    private TransformGesture activeTransform;
    private PendingMoveGesture pendingMove;
    private CellContentGesture cellContentGesture;
    private final HudTransformCursor transformCursor = new HudTransformCursor();
    private boolean toolboxWidgetDragActive;
    private HudInteractiveTestSession interactiveTestSession;

    private HudAuthoringResources currentResources;
    public HudEditorSession() { this(() -> null, () -> null, () -> null); }
    public HudEditorSession(Supplier<AssetMetaDatabase> assetDatabase) {
        this(assetDatabase, () -> null, () -> null);
    }
    public HudEditorSession(Supplier<AssetMetaDatabase> assetDatabase, Supplier<Batch> batch) {
        this(assetDatabase, batch, () -> null);
    }
    public HudEditorSession(Supplier<AssetMetaDatabase> assetDatabase, Supplier<Batch> batch,
                            Supplier<ShapeDrawer> drawer) {
        this.assetDatabase = Objects.requireNonNull(assetDatabase);
        this.batch = Objects.requireNonNull(batch);
        this.drawer = Objects.requireNonNull(drawer);
    }

    /** Projects the document's current in-memory authored state; never reloads it from disk. */
    public void open(FileHandle projectDir, HudScreenEditorDocument requestedDocument) {
        suspend();
        this.projectDir = projectDir;
        editorDocument = requestedDocument;
        deletionsByRevision.clear();
        creationsByRevision.clear();
        cellMovesByRevision.clear();
        structuresByRevision.clear();
        requestedDocument.editSession().addHistoryNavigationListener(historyNavigationListener);
        screenId = requestedDocument.screenId();
        asset = requestedDocument.asset();
        document = requestedDocument.document();
        selectedNodeId = requestedDocument.selectedNodeId();
        selectedCellId = requestedDocument.selectedCellId();
        selectedCellRange = selectedCellId == null ? List.of() : List.of(selectedCellId);
        cellRangeEndId = selectedCellId;
        errorMessage = null;
        testModeErrorMessage = null;
        requestedDocument.editSession().bindActivePreview(previewBinding);
        try {
            installInitial(document);
            status = Status.READY;
        } catch (RuntimeException failure) {
            closePreview();
            status = Status.ERROR;
            errorMessage = message(failure);
        }
        resolveSelection();
        rebuildSelectionOverlay();
        notifyListeners();
    }

    /** Detaches all active Runtime/GL state while retaining lightweight values for document capture. */
    public void suspend() {
        exitTestMode();
        if (editorDocument != null) {
            editorDocument.editSession().unbindActivePreview(previewBinding);
            editorDocument.editSession().removeHistoryNavigationListener(historyNavigationListener);
        }
        cancelTransformGesture();
        cancelCellContentGesture();
        transformCursor.clear();
        toolboxWidgetDragActive = false;
        closePreview();
        editorDocument = null;
        deletionsByRevision.clear();
        creationsByRevision.clear();
        cellMovesByRevision.clear();
        structuresByRevision.clear();
        projectDir = null;
        testModeErrorMessage = null;
        if (status != Status.CLOSED) {
            status = Status.CLOSED;
            notifyListeners();
        }
    }

    public void close() {
        suspend();
        status = Status.CLOSED;
        screenId = null;
        asset = null;
        document = null;
        validation = null;
        selectedNodeId = null;
        selectedCellId = null;
        selectedCellRange = List.of();
        cellRangeEndId = null;
        errorMessage = null;
        testModeErrorMessage = null;
        notifyListeners();
    }

    public void selectNode(String nodeId) {
        if (isTestMode()) return;
        String next = status == Status.READY && validation != null && validation.isValid()
                && validation.validatedDocument().node(nodeId) != null ? nodeId : null;
        if (java.util.Objects.equals(next, selectedNodeId) && selectedCellId == null) return;
        cancelTransformGesture();
        cancelCellContentGesture();
        transformCursor.clear();
        selectedNodeId = next;
        selectedCellId = null;
        selectedCellRange = List.of();
        cellRangeEndId = null;
        if (editorDocument != null) editorDocument.setSelectedNodeId(next);
        rebuildSelectionOverlay();
        notifyListeners();
    }

    /** Selects an explicit native cell without introducing an authored Actor. */
    public void selectCell(String cellId) {
        if (isTestMode() || status != Status.READY || (materialized == null
                ? HudLayoutAuthoring.cell(document, cellId) == null
                : materialized.cell(cellId) == null)) return;
        if (Objects.equals(selectedCellId, cellId) && selectedNodeId == null
                && selectedCellRange.size() == 1) return;
        cancelTransformGesture();
        cancelCellContentGesture();
        transformCursor.clear();
        selectedNodeId = null;
        selectedCellId = cellId;
        selectedCellRange = List.of(cellId);
        cellRangeEndId = cellId;
        if (editorDocument != null) editorDocument.setSelectedCellId(cellId);
        rebuildSelectionOverlay();
        notifyListeners();
    }

    /** Extends the current cell selection across one authored row, including merged cells. */
    public void selectCellRange(String otherCellId) {
        if (isTestMode() || document == null || otherCellId == null) return;
        HudLayoutAuthoring.CellPosition start = HudLayoutAuthoring.cellPosition(document, selectedCellId);
        HudLayoutAuthoring.CellPosition end = HudLayoutAuthoring.cellPosition(document, otherCellId);
        if (start == null || end == null || !start.tableId().equals(end.tableId())
                || start.row() != end.row()) { selectCell(otherCellId); return; }
        HudNode table = HudLayoutAuthoring.node(document, start.tableId());
        List<String> range = new ArrayList<>();
        for (int index = Math.min(start.cellIndex(), end.cellIndex());
             index <= Math.max(start.cellIndex(), end.cellIndex()); index++)
            range.add(table.table.rows.get(start.row()).cells.get(index).id);
        if (range.equals(selectedCellRange)) return;
        cancelCellContentGesture();
        selectedCellRange = List.copyOf(range);
        cellRangeEndId = otherCellId;
        rebuildSelectionOverlay();
        notifyListeners();
    }

    public List<String> selectedCellRange() { return selectedCellRange; }

    /** Shift-click resolves the native cell under the pointer, including occupied cells. */
    public boolean selectCellRangeAt(float stageX, float stageY) {
        if (isTestMode() || materialized == null || document == null || authoringSession == null)
            return false;
        Vector2 point = new Vector2();
        if (!hudPointAt(stageX, stageY, point) || !insideHudSurface(point)) return false;
        Vector2 overlayPoint = authoringSession.selectionOverlayActor()
                .stageToLocalCoordinates(new Vector2(point));
        Actor leaf = deepestVisibleAuthoredActorAt(authoringSession.stage().getRoot(), point);
        String visualId = leaf != null ? leaf.getName() : null;
        String found = null;
        int depth = -1;
        for (Map.Entry<String, Cell<?>> entry : materialized.cellById().entrySet()) {
            HudTableCell cell = HudLayoutAuthoring.cell(document, entry.getKey());
            HudNode owner = HudLayoutAuthoring.tableOwner(document, entry.getKey());
            if (cell == null || owner == null) continue;
            Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(entry.getValue(),
                    authoringSession.selectionOverlayActor(), new Rectangle());
            if (bounds.width <= 0f || bounds.height <= 0f || !bounds.contains(overlayPoint)
                    || !(owner.id.equals(visualId)
                    || cell.content != null && isAuthoredAncestor(cell.content.id, visualId)))
                continue;
            int candidateDepth = 0;
            for (String id = owner.id; id != null; id = HudLayoutAuthoring.parentId(document, id))
                candidateDepth++;
            if (candidateDepth > depth) { depth = candidateDepth; found = cell.id; }
        }
        if (found == null) return false;
        selectCellRange(found);
        return true;
    }

    /** Runs one validated structural mutation through the document's atomic edit history. */
    public boolean editSelectedTableStructure(HudLayoutAuthoring.StructureAction action,
                                              int logicalColumn) {
        return editTableStructure(action, selectedCellId, cellRangeEndId, logicalColumn);
    }

    /** Executes an explicitly targeted table edit through the existing atomic history boundary. */
    public boolean editTableStructure(HudLayoutAuthoring.StructureAction action,
                                      String anchorId, String otherId, int logicalColumn) {
        if (isTestMode() || status != Status.READY || editorDocument == null
                || anchorId == null || action == null) return false;
        String rejection = HudLayoutAuthoring.structureRejection(document, action,
                anchorId, otherId, logicalColumn);
        if (rejection != null) return false;
        String before = selectedCellId;
        List<String> beforeRange = selectedCellRange;
        String beforeEnd = cellRangeEndId;
        String[] selected = new String[1];
        HudDocumentEditSession edits = editorDocument.editSession();
        long revision = edits.currentRevision();
        cancelCellContentGesture();
        try {
            edits.edit("Edit HUD table structure", candidate -> {
                selected[0] = HudLayoutAuthoring.editStructure(candidate, action,
                        anchorId, otherId, logicalColumn);
                return candidate;
            });
        } catch (HudEditRejectedException rejected) {
            return false;
        }
        if (edits.currentRevision() == revision) return false;
        structuresByRevision.put(edits.currentRevision(),
                new StructureSelection(before, beforeRange, beforeEnd, selected[0]));
        selectCell(selected[0]);
        return true;
    }

    public String selectedStructureRejection(HudLayoutAuthoring.StructureAction action,
                                             int logicalColumn) {
        if (isTestMode()) return "Structure editing is unavailable in TEST mode.";
        String other = cellRangeEndId;
        return HudLayoutAuthoring.structureRejection(document, action,
                selectedCellId, other, logicalColumn);
    }

    /** True only for a live non-root node in the currently projected HUD document. */
    public boolean canDeleteNode(String nodeId) {
        if (isTestMode() || status != Status.READY || editorDocument == null || nodeId == null) return false;
        HudDocumentV1 current = editorDocument.document();
        return current != null && current.root != null && !nodeId.equals(current.root.id)
                && HudLayoutAuthoring.parentId(current, nodeId) != null;
    }

    /** Deletes a live non-root HUD subtree in one edit-session transaction. */
    public boolean deleteNode(String nodeId) {
        if (!canDeleteNode(nodeId)) return false;
        cancelTransformGesture();
        String[] parentId = new String[1];
        editorDocument.editSession().edit("Delete HUD node", candidate -> {
            parentId[0] = HudLayoutAuthoring.removeChild(candidate, nodeId);
            if (parentId[0] == null) {
                throw new HudEditRejectedException("HUD node is no longer a deletable child.");
            }
            return candidate;
        });
        if (parentId[0] == null) return false;
        deletionsByRevision.put(editorDocument.editSession().currentRevision(),
                new DeletionSelection(nodeId, parentId[0]));
        selectNode(parentId[0]);
        return true;
    }

    /** Deletes the current selection when it remains a live non-root node. */
    public boolean deleteSelectedNode() {
        return deleteNode(selectedNodeId);
    }

    public boolean addDialogResultButton(String dialogId, HudNodeKind kind) {
        if (isTestMode() || status != Status.READY || editorDocument == null
                || !Objects.equals(selectedNodeId, dialogId)) return false;
        String[] created = new String[1];
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit("Add HUD Dialog result button", candidate -> {
            created[0] = HudLayoutAuthoring.addDialogResultButton(candidate, dialogId, kind);
            if (created[0] == null) throw new HudEditRejectedException("Dialog is no longer available.");
            return candidate;
        });
        if (revision == editorDocument.editSession().currentRevision()) return false;
        creationsByRevision.put(editorDocument.editSession().currentRevision(),
                new CreationSelection(created[0], dialogId));
        selectNode(created[0]);
        return true;
    }

    public boolean moveDialogResultButton(String dialogId, String buttonId, int offset) {
        if (isTestMode() || status != Status.READY || editorDocument == null
                || !Objects.equals(selectedNodeId, dialogId)) return false;
        HudNode owner = HudLayoutAuthoring.resultButtonOwner(document, buttonId);
        if (owner == null || !owner.id.equals(dialogId)) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit("Reorder HUD Dialog result button", candidate -> {
            if (!HudLayoutAuthoring.moveDialogResultButton(candidate, buttonId, offset))
                throw new HudEditRejectedException("Result button cannot move in that direction.");
            return candidate;
        });
        return revision != editorDocument.editSession().currentRevision();
    }

    public boolean editSelectedDialogResultButton(String label,
            java.util.function.Consumer<games.pixscape.runtime.hud.document.HudDialogResultButton> mutation) {
        if (isTestMode() || status != Status.READY || editorDocument == null
                || selectedNodeId == null || mutation == null) return false;
        String buttonId = selectedNodeId;
        if (HudLayoutAuthoring.resultButton(document, buttonId) == null) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit(label, candidate -> {
            var entry = HudLayoutAuthoring.resultButton(candidate, buttonId);
            if (entry == null) throw new HudEditRejectedException("Dialog result button is no longer available.");
            mutation.accept(entry);
            return candidate;
        });
        return revision != editorDocument.editSession().currentRevision();
    }

    void rememberCreatedNode(long revision, String createdNodeId, String previousNodeId) {
        creationsByRevision.put(revision, new CreationSelection(createdNodeId, previousNodeId));
    }

    /** Applies one property mutation to the current selected node through the HUD document history. */
    public boolean editSelectedNode(String label, BiConsumer<HudNode, HudChild> mutation) {
        if (isTestMode() || status != Status.READY || editorDocument == null || selectedNodeId == null
                || mutation == null) return false;
        String nodeId = selectedNodeId;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit(label, candidate -> {
            HudNode node = HudLayoutAuthoring.node(candidate, nodeId);
            if (node == null) {
                throw new HudEditRejectedException("Selected HUD node is no longer available.");
            }
            mutation.accept(node, HudLayoutAuthoring.childRelation(candidate, nodeId));
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    /** Applies one cell-constraint edit through the same atomic document history. */
    public boolean editSelectedCell(String label, Consumer<HudTableCell> mutation) {
        if (isTestMode() || status != Status.READY || editorDocument == null || selectedCellId == null
                || mutation == null) return false;
        String cellId = selectedCellId;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit(label, candidate -> {
            HudTableCell cell = HudLayoutAuthoring.cell(candidate, cellId);
            if (cell == null) throw new HudEditRejectedException("Selected HUD cell is no longer available.");
            mutation.accept(cell);
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    /** Edits initial visibility only for the document and node captured by the inspector. */
    public boolean editNodeVisibility(String originScreenId, String nodeId, boolean visible) {
        if (isTestMode() || status != Status.READY || editorDocument == null || nodeId == null
                || !Objects.equals(screenId, originScreenId)
                || !Objects.equals(selectedNodeId, nodeId)) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit("Edit HUD node visibility", candidate -> {
            HudNode node = HudLayoutAuthoring.node(candidate, nodeId);
            if (node == null) throw new HudEditRejectedException("HUD node is no longer available.");
            node.visible = visible;
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    /** Label styles that resolve through the active HUD's authoring Skin. */
    public List<String> labelStyleNames() {
        return labelStyleNames(false);
    }
    public List<String> labelStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.labelStyleNames(hasFontOverride) : List.of();
    }

    public List<FontResourceOption> fontResourceOptions() {
        AssetMetaDatabase database = assetDatabase.get();
        if (database == null) return List.of();
        List<FontResourceOption> options = new ArrayList<>();
        for (int index = 0; index < database.size(); index++) {
            AssetMeta candidate = database.assetAt(index);
            if (candidate.type() == AssetType.FONT && candidate.isUserVisible()) {
                options.add(new FontResourceOption(candidate.id(), candidate.logicalPath()));
            }
        }
        options.sort(java.util.Comparator.comparing(FontResourceOption::label));
        return List.copyOf(options);
    }

    public List<SkinResourceOption> skinResourceOptions() {
        AssetMetaDatabase database = assetDatabase.get();
        if (database == null) return List.of();
        List<AssetMeta> skins = new ArrayList<>();
        Map<String, Integer> names = new HashMap<>();
        for (int index = 0; index < database.size(); index++) {
            AssetMeta candidate = database.assetAt(index);
            if (candidate.type() != AssetType.SKIN || !candidate.isUserVisible()) continue;
            skins.add(candidate);
            String name = AssetDisplayInfo.from(candidate).displayName();
            names.put(name, names.getOrDefault(name, 0) + 1);
        }
        List<SkinResourceOption> options = new ArrayList<>();
        for (AssetMeta candidate : skins) {
            String name = AssetDisplayInfo.from(candidate).displayName();
            String label = names.get(name) > 1
                    ? name + " — " + candidate.logicalPath() + " (#" + candidate.id() + ")"
                    : name;
            options.add(new SkinResourceOption(candidate.id(), candidate.sourceRelPath(), label));
        }
        options.sort(java.util.Comparator.comparing(SkinResourceOption::label)
                .thenComparingInt(SkinResourceOption::assetId));
        return List.copyOf(options);
    }

    public List<String> textTooltipStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.textTooltipStyleNames(hasFontOverride) : List.of();
    }

    public boolean editTooltip(String originScreenId, String nodeId, String label,
                               Consumer<HudNode> mutation) {
        if (isTestMode() || status != Status.READY || editorDocument == null
                || !Objects.equals(screenId, originScreenId) || nodeId == null) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit(label, candidate -> {
            HudNode node = HudLayoutAuthoring.node(candidate, nodeId);
            if (node == null) throw new HudEditRejectedException("Target HUD node is no longer available.");
            mutation.accept(node);
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    /** Rebuilds resource-backed property choices without changing the HUD document or history. */
    public void refreshAssetOptions() { notifyListeners(); }

    public boolean assignFont(String screenId, String nodeId, Integer assetId) {
        if (isTestMode() || editorDocument == null || !Objects.equals(this.screenId, screenId)
                || nodeId == null) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit(assetId == null ? "Use HUD widget style font"
                : "Assign HUD widget font", candidate -> {
            HudNode node = HudLayoutAuthoring.node(candidate, nodeId);
            if (node == null || !HudFontReferences.supports(node.kind)) {
                throw new HudEditRejectedException(
                        "Target HUD node no longer supports a font override.");
            }
            HudFontReferences.setAssetId(node, assetId);
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    public boolean assignSkin(String screenId, Integer assetId) {
        if (isTestMode() || editorDocument == null || !Objects.equals(this.screenId, screenId)) return false;
        String skinId = null;
        if (assetId != null) {
            AssetMetaDatabase database = assetDatabase.get();
            AssetMeta selected = database != null ? database.findById(assetId) : null;
            if (selected == null || selected.type() != AssetType.SKIN
                    || selected.sourceRelPath() == null || selected.sourceRelPath().isBlank()) {
                throw new HudEditRejectedException(
                        "The selected Skin Asset is no longer available in this project.");
            }
            skinId = selected.sourceRelPath();
        }
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().editSkin(
                skinId == null ? "Remove HUD Screen Skin" : "Assign HUD Screen Skin", skinId);
        return editorDocument.editSession().currentRevision() != revision;
    }

    /** TextButton styles that resolve through the active HUD's authoring Skin. */
    public List<String> textButtonStyleNames() {
        return textButtonStyleNames(false);
    }
    public List<String> textButtonStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.textButtonStyleNames(hasFontOverride) : List.of();
    }

    /** ImageButton styles that resolve through the active HUD's authoring Skin. */
    public List<String> imageButtonStyleNames() {
        return currentResources != null ? currentResources.imageButtonStyleNames() : List.of();
    }

    /** ImageTextButton styles that resolve through the active HUD's authoring Skin. */
    public List<String> imageTextButtonStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.imageTextButtonStyleNames(hasFontOverride) : List.of();
    }

    /** TextField styles that resolve through the active HUD's authoring Skin. */
    public List<String> textFieldStyleNames() {
        return textFieldStyleNames(false);
    }
    public List<String> textFieldStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.textFieldStyleNames(hasFontOverride) : List.of();
    }

    /** SelectBox styles that resolve through the active HUD's authoring Skin. */
    public List<String> selectBoxStyleNames() {
        return selectBoxStyleNames(false);
    }
    public List<String> selectBoxStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.selectBoxStyleNames(hasFontOverride) : List.of();
    }

    public List<String> listStyleNames(boolean hasFontOverride) {
        return currentResources != null ? currentResources.listStyleNames(hasFontOverride) : List.of();
    }

    /** CheckBox styles that resolve through the active HUD's authoring Skin. */
    public List<String> checkBoxStyleNames() {
        return checkBoxStyleNames(false);
    }
    public List<String> checkBoxStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.checkBoxStyleNames(hasFontOverride) : List.of();
    }

    /** Slider styles that resolve through the active HUD's authoring Skin. */
    public List<String> sliderStyleNames() {
        return currentResources != null ? currentResources.sliderStyleNames() : List.of();
    }

    /** ProgressBar styles that resolve through the active HUD's authoring Skin. */
    public List<String> progressBarStyleNames() {
        return currentResources != null ? currentResources.progressBarStyleNames() : List.of();
    }

    public List<String> scrollPaneStyleNames() {
        return currentResources != null ? currentResources.scrollPaneStyleNames() : List.of();
    }

    public List<String> windowStyleNames(boolean hasFontOverride) {
        return currentResources != null
                ? currentResources.windowStyleNames(hasFontOverride) : List.of();
    }

    public boolean editWindow(String originScreenId, String nodeId, String label,
                              Consumer<HudWindowData> mutation) {
        if (isTestMode() || status != Status.READY || editorDocument == null || currentResources == null
                || !Objects.equals(screenId, originScreenId) || nodeId == null) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit(label, candidate -> {
            HudNode node = HudLayoutAuthoring.node(candidate, nodeId);
            if (node == null || node.window == null && node.dialog == null) {
                throw new HudEditRejectedException("Target HUD Window or Dialog is no longer available.");
            }
            var data = node.window != null ? node.window : node.dialog;
            mutation.accept(data);
            if (!currentResources.hasUsableWindowStyle(data.styleName,
                    data.fontAssetId != null)) {
                throw new HudEditRejectedException(
                        "The selected Window style is no longer available or usable.");
            }
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    /** Existing importable Image assets that can provide an ImageButton native state override. */
    public List<ImageResourceOption> imageResourceOptions() {
        AssetMetaDatabase database = assetDatabase.get();
        if (database == null || projectDir == null) return List.of();
        List<ImageResourceOption> options = new ArrayList<>();
        for (int index = 0; index < database.size(); index++) {
            AssetMeta asset = database.assetAt(index);
            if (asset.type() != AssetType.IMAGE) continue;
            HudImageAssetRef reference = HudImageAssetRef.fromAssetId(asset.id(), database, projectDir);
            if (reference != null) options.add(new ImageResourceOption(reference.resourceName(),
                    asset.logicalPath() != null ? asset.logicalPath() : reference.resourceName()));
        }
        options.sort(java.util.Comparator.comparing(ImageResourceOption::label));
        return List.copyOf(options);
    }

    /** Availability is independent from selection so a toolbox drag may target the root. */
    public boolean isWidgetAvailable(HudNodeKind kind) {
        if (isTestMode() || kind == null || status != Status.READY || editorDocument == null) return false;
        return switch (kind) {
            case GROUP, TABLE, STACK, CONTAINER -> true;
            case SCROLL_PANE -> currentResources != null && currentResources.defaultScrollPane() != null;
            case WINDOW, DIALOG -> currentResources != null && currentResources.hasDefaultWindow();
            case LABEL, TEXTRA_LABEL -> currentResources != null && currentResources.defaultLabel() != null;
            case TEXT_BUTTON -> currentResources != null && currentResources.defaultTextButton() != null;
            case IMAGE_BUTTON -> currentResources != null && currentResources.defaultImageButton() != null;
            case IMAGE_TEXT_BUTTON -> currentResources != null && currentResources.defaultImageTextButton() != null;
            case TEXT_FIELD -> currentResources != null && currentResources.defaultTextField() != null;
            case SELECT_BOX -> currentResources != null && currentResources.defaultSelectBox() != null;
            case LIST -> currentResources != null && currentResources.hasDefaultList();
            case CHECK_BOX -> currentResources != null && currentResources.defaultCheckBox() != null;
            case SLIDER -> currentResources != null && currentResources.defaultSlider() != null;
            case PROGRESS_BAR -> currentResources != null && currentResources.defaultProgressBar() != null;
            default -> false;
        };
    }

    public boolean canCreateWidgetAt(HudNodeKind kind, String parentId) {
        return isWidgetAvailable(kind) && parentId != null
                && HudLayoutAuthoring.canAddChild(editorDocument.document(), parentId);
    }

    private String selectedWidgetParentId() {
        if (selectedCellId == null) return selectedNodeId;
        HudTableCell cell = HudLayoutAuthoring.cell(document, selectedCellId);
        HudNode owner = HudLayoutAuthoring.tableOwner(document, selectedCellId);
        return cell != null && cell.content == null && owner != null ? owner.id : null;
    }

    /** Captures the current TABLE destination, including an explicitly selected empty cell. */
    public WidgetDropTarget selectedTableTarget() {
        String parentId = selectedWidgetParentId();
        return parentId != null ? new WidgetDropTarget(parentId, null, false, false,
                parentId, selectedCellId) : null;
    }

    /** Preserves the selected-parent click flow while sharing the widget factories with DnD. */
    public boolean createSelectedWidget(HudNodeKind kind) {
        if (selectedCellId != null) {
            HudNode owner = HudLayoutAuthoring.tableOwner(document, selectedCellId);
            return owner != null && createWidget(kind, owner.id, null, editorDocument, false, selectedCellId);
        }
        return createWidget(kind, selectedNodeId, null, editorDocument, false, null);
    }

    /** Publishes one resolved widget after the controller has revalidated its drop. */
    public boolean createWidgetAt(HudNodeKind kind, WidgetDropTarget target,
                                   HudScreenEditorDocument capturedDocument) {
        return target != null && createWidget(kind, target.parentId(), target.placement(),
                capturedDocument, true, target.cellId());
    }

    /** Commits a confirmed TABLE grid against the destination captured before its dimension dialog. */
    public boolean createTableAt(int rows, int columns, WidgetDropTarget target,
                                 HudScreenEditorDocument capturedDocument) {
        if (rows < 1 || columns < 1 || rows > 64 || columns > 64 || target == null
                || editorDocument == null || editorDocument != capturedDocument
                || !isWidgetAvailable(HudNodeKind.TABLE)
                || !canCreateWidgetAt(HudNodeKind.TABLE, target.parentId())) return false;
        if (target.cellId() != null && !availableCellInParent(document, target.parentId(), target.cellId()))
            return false;
        String previousNodeId = selectedNodeId;
        String[] createdId = new String[1];
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit("Add Table child", candidate -> {
            createdId[0] = HudLayoutAuthoring.addTable(candidate, target.parentId(), rows, columns);
            if (createdId[0] == null) throw new HudEditRejectedException(
                    "The captured HUD destination can no longer accept a table.");
            if (target.cellId() != null) {
                HudTableCell source = HudLayoutAuthoring.containingCell(candidate, createdId[0]);
                HudTableCell destination = HudLayoutAuthoring.cell(candidate, target.cellId());
                HudNode owner = HudLayoutAuthoring.tableOwner(candidate, target.cellId());
                if (source == null || destination == null || source != destination && destination.content != null
                        || owner == null || !target.parentId().equals(owner.id)) {
                    throw new HudEditRejectedException("The captured HUD cell is no longer empty.");
                }
                if (source != destination) {
                    destination.content = source.content;
                    source.content = null;
                }
            }
            if (target.placement() != null) {
                HudChild relation = HudLayoutAuthoring.childRelation(candidate, createdId[0]);
                if (relation != null && relation.free != null) relation.free = target.placement();
            }
            return candidate;
        });
        if (createdId[0] == null || editorDocument.editSession().currentRevision() == revision) return false;
        rememberCreatedNode(editorDocument.editSession().currentRevision(), createdId[0], previousNodeId);
        selectNode(createdId[0]);
        return true;
    }

    /** Confirms a TABLE dialog opened from the toolbox against its originally selected parent. */
    public boolean createSelectedTable(int rows, int columns, HudScreenEditorDocument capturedDocument,
                                       String capturedParentId) {
        return capturedParentId != null && createTableAt(rows, columns,
                new WidgetDropTarget(capturedParentId, null), capturedDocument);
    }

    private boolean createWidget(HudNodeKind kind, String parentId, HudFreePlacement placement,
                                 HudScreenEditorDocument capturedDocument, boolean requireCapturedDocument) {
        return createWidget(kind, parentId, placement, capturedDocument, requireCapturedDocument, null);
    }

    private boolean createWidget(HudNodeKind kind, String parentId, HudFreePlacement placement,
                                 HudScreenEditorDocument capturedDocument, boolean requireCapturedDocument,
                                 String targetCellId) {
        if (kind == null || editorDocument == null || !isWidgetAvailable(kind)
                || !canCreateWidgetAt(kind, parentId)
                || requireCapturedDocument && editorDocument != capturedDocument) return false;
        if (targetCellId != null && !availableCellInParent(document, parentId, targetCellId)) return false;
        String previousNodeId = selectedNodeId;
        String[] createdId = new String[1];
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit("Add " + display(kind) + " child", candidate -> {
            createdId[0] = addWidget(candidate, parentId, kind);
            if (createdId[0] == null) {
                throw new HudEditRejectedException("The HUD destination can no longer accept this widget.");
            }
            if (targetCellId != null) {
                HudTableCell source = HudLayoutAuthoring.containingCell(candidate, createdId[0]);
                HudTableCell target = HudLayoutAuthoring.cell(candidate, targetCellId);
                HudNode owner = HudLayoutAuthoring.tableOwner(candidate, targetCellId);
                if (source == null || target == null || source != target && target.content != null
                        || owner == null || !parentId.equals(owner.id)) {
                    throw new HudEditRejectedException("The selected HUD cell is no longer empty.");
                }
                if (source != target) {
                    target.content = source.content;
                    source.content = null;
                }
            }
            if (placement != null) {
                HudChild relation = HudLayoutAuthoring.childRelation(candidate, createdId[0]);
                if (relation != null && relation.free != null) relation.free = placement;
            }
            return candidate;
        });
        if (createdId[0] == null || editorDocument.editSession().currentRevision() == revision) return false;
        rememberCreatedNode(editorDocument.editSession().currentRevision(), createdId[0], previousNodeId);
        selectNode(createdId[0]);
        return true;
    }

    private static boolean availableCellInParent(HudDocumentV1 document, String parentId, String cellId) {
        HudTableCell cell = HudLayoutAuthoring.cell(document, cellId);
        HudNode owner = HudLayoutAuthoring.tableOwner(document, cellId);
        return cell != null && cell.content == null && owner != null && parentId.equals(owner.id);
    }

    private String addWidget(HudDocumentV1 candidate, String parentId, HudNodeKind kind) {
        return switch (kind) {
            case GROUP, TABLE, STACK, CONTAINER, SCROLL_PANE, WINDOW, DIALOG ->
                    HudLayoutAuthoring.addChild(candidate, parentId, kind);
            case LABEL -> HudLayoutAuthoring.addLabel(candidate, parentId, DEFAULT_LABEL_TEXT,
                    currentResources.defaultLabel().styleName());
            case TEXTRA_LABEL -> HudLayoutAuthoring.addTextraLabel(candidate, parentId,
                    DEFAULT_TEXTRA_LABEL_TEXT, currentResources.defaultLabel().styleName());
            case TEXT_BUTTON -> HudLayoutAuthoring.addTextButton(candidate, parentId,
                    DEFAULT_TEXT_BUTTON_TEXT, currentResources.defaultTextButton().styleName());
            case IMAGE_BUTTON -> HudLayoutAuthoring.addImageButton(candidate, parentId,
                    currentResources.defaultImageButton().styleName());
            case IMAGE_TEXT_BUTTON -> HudLayoutAuthoring.addImageTextButton(candidate, parentId,
                    DEFAULT_TEXT_BUTTON_TEXT, currentResources.defaultImageTextButton().styleName());
            case TEXT_FIELD -> HudLayoutAuthoring.addTextField(candidate, parentId,
                    currentResources.defaultTextField().styleName());
            case SELECT_BOX -> HudLayoutAuthoring.addSelectBox(candidate, parentId,
                    currentResources.defaultSelectBox().styleName());
            case LIST -> HudLayoutAuthoring.addList(candidate, parentId, null);
            case CHECK_BOX -> HudLayoutAuthoring.addCheckBox(candidate, parentId,
                    currentResources.defaultCheckBox().styleName());
            case SLIDER -> HudLayoutAuthoring.addSlider(candidate, parentId,
                    currentResources.defaultSlider().styleName());
            case PROGRESS_BAR -> HudLayoutAuthoring.addProgressBar(candidate, parentId,
                    currentResources.defaultProgressBar().styleName());
            default -> null;
        };
    }

    private static String display(HudNodeKind kind) {
        return switch (kind) {
            case TEXT_BUTTON -> "Text Button";
            case TEXTRA_LABEL -> "Textra Label";
            case IMAGE_BUTTON -> "Image Button";
            case IMAGE_TEXT_BUTTON -> "Image Text Button";
            case TEXT_FIELD -> "Text Field";
            case SELECT_BOX -> "Select Box";
            case LIST -> "List";
            case CHECK_BOX -> "Check Box";
            case PROGRESS_BAR -> "Progress Bar";
            case SCROLL_PANE -> "Scroll Pane";
            case WINDOW -> "Window";
            case DIALOG -> "Dialog";
            default -> kind.name().charAt(0) + kind.name().substring(1).toLowerCase();
        };
    }

    public boolean canCreateLabel() {
        return canCreateWidgetAt(HudNodeKind.LABEL, selectedWidgetParentId());
    }

    public boolean canCreateProgressBar() {
        return canCreateWidgetAt(HudNodeKind.PROGRESS_BAR, selectedWidgetParentId());
    }

    /** Creates and selects one native non-interactive ProgressBar through normal HUD history. */
    public boolean createProgressBar() {
        return canCreateProgressBar() && createSelectedWidget(HudNodeKind.PROGRESS_BAR);
    }

    public boolean editSelectedScrollPaneStyle(String styleName) {
        return editSelectedScrollPane("Edit HUD ScrollPane style", data -> data.styleName = styleName);
    }

    public boolean editSelectedScrollPaneScrollingDisabledX(boolean disabled) {
        return editSelectedScrollPane("Edit HUD ScrollPane horizontal scrolling",
                data -> data.scrollingDisabledX = disabled);
    }

    public boolean editSelectedScrollPaneScrollingDisabledY(boolean disabled) {
        return editSelectedScrollPane("Edit HUD ScrollPane vertical scrolling",
                data -> data.scrollingDisabledY = disabled);
    }

    public boolean editSelectedScrollPaneFadeScrollBars(boolean fade) {
        return editSelectedScrollPane("Edit HUD ScrollPane fade scroll bars",
                data -> data.fadeScrollBars = fade);
    }

    public boolean editSelectedScrollPaneFlickScroll(boolean flick) {
        return editSelectedScrollPane("Edit HUD ScrollPane flick scroll",
                data -> data.flickScroll = flick);
    }

    public boolean editSelectedScrollPaneSmoothScrolling(boolean smooth) {
        return editSelectedScrollPane("Edit HUD ScrollPane smooth scrolling",
                data -> data.smoothScrolling = smooth);
    }

    public boolean editSelectedScrollPaneOverscrollX(boolean overscroll) {
        return editSelectedScrollPane("Edit HUD ScrollPane horizontal overscroll",
                data -> data.overscrollX = overscroll);
    }

    public boolean editSelectedScrollPaneOverscrollY(boolean overscroll) {
        return editSelectedScrollPane("Edit HUD ScrollPane vertical overscroll",
                data -> data.overscrollY = overscroll);
    }

    private boolean editSelectedScrollPane(String label, Consumer<HudScrollPaneData> mutation) {
        HudNode selected = selectedNode();
        if (selected == null || selected.scrollPane == null || currentResources == null) return false;
        return editSelectedNode(label, (node, relation) -> {
            mutation.accept(node.scrollPane);
            if (!currentResources.hasUsableScrollPaneStyle(node.scrollPane.styleName)) {
                throw new HudEditRejectedException("The selected ScrollPane style is no longer available or usable.");
            }
        });
    }

    /** Creates and selects one Label through the active HUD document's normal history pipeline. */
    public boolean createLabel() {
        if (!canCreateLabel()) return false;
        return createSelectedWidget(HudNodeKind.LABEL);
    }

    public boolean canCreateTextraLabel() {
        return canCreateWidgetAt(HudNodeKind.TEXTRA_LABEL, selectedWidgetParentId());
    }

    /** Creates and selects one native Textra TypingLabel through normal HUD history. */
    public boolean createTextraLabel() {
        if (!canCreateTextraLabel()) return false;
        return createSelectedWidget(HudNodeKind.TEXTRA_LABEL);
    }

    /** Restarts only the live authoring actor; this transient preview never edits history. */
    public boolean previewSelectedTextraTyping() {
        HudNode selected = selectedNode();
        if (selected == null || selected.textraLabel == null
                || !selected.textraLabel.typingEnabled || materialized == null) return false;
        Actor actor = materialized.actor(selected.id);
        if (!(actor instanceof TypingLabel label)) return false;
        label.restart();
        return true;
    }

    public boolean canCreateTextButton() {
        return canCreateWidgetAt(HudNodeKind.TEXT_BUTTON, selectedWidgetParentId());
    }

    /** Creates and selects one TextButton through the active HUD document's history pipeline. */
    public boolean createTextButton() {
        if (!canCreateTextButton()) return false;
        return createSelectedWidget(HudNodeKind.TEXT_BUTTON);
    }

    public boolean canCreateImageButton() {
        return canCreateWidgetAt(HudNodeKind.IMAGE_BUTTON, selectedWidgetParentId());
    }

    /** Creates and selects one ImageButton through the active HUD document's history pipeline. */
    public boolean createImageButton() {
        if (!canCreateImageButton()) return false;
        return createSelectedWidget(HudNodeKind.IMAGE_BUTTON);
    }

    public boolean canCreateImageTextButton() {
        return canCreateWidgetAt(HudNodeKind.IMAGE_TEXT_BUTTON, selectedWidgetParentId());
    }

    /** Creates and selects one ImageTextButton through the active HUD document's history pipeline. */
    public boolean createImageTextButton() {
        if (!canCreateImageTextButton()) return false;
        return createSelectedWidget(HudNodeKind.IMAGE_TEXT_BUTTON);
    }

    public boolean canCreateTextField() {
        return canCreateWidgetAt(HudNodeKind.TEXT_FIELD, selectedWidgetParentId());
    }

    /** Creates and selects one TextField through the active HUD document's normal history pipeline. */
    public boolean createTextField() {
        if (!canCreateTextField()) return false;
        return createSelectedWidget(HudNodeKind.TEXT_FIELD);
    }

    public boolean canCreateSelectBox() {
        return canCreateWidgetAt(HudNodeKind.SELECT_BOX, selectedWidgetParentId());
    }

    /** Creates and selects one native SelectBox through the active document history pipeline. */
    public boolean createSelectBox() {
        if (!canCreateSelectBox()) return false;
        return createSelectedWidget(HudNodeKind.SELECT_BOX);
    }

    public boolean canCreateList() {
        return canCreateWidgetAt(HudNodeKind.LIST, selectedWidgetParentId());
    }

    public boolean createList() {
        return canCreateList() && createSelectedWidget(HudNodeKind.LIST);
    }

    public boolean canCreateCheckBox() {
        return canCreateWidgetAt(HudNodeKind.CHECK_BOX, selectedWidgetParentId());
    }

    /** Creates and selects one native CheckBox through the active document history pipeline. */
    public boolean createCheckBox() {
        if (!canCreateCheckBox()) return false;
        return createSelectedWidget(HudNodeKind.CHECK_BOX);
    }

    public boolean canCreateSlider() {
        return canCreateWidgetAt(HudNodeKind.SLIDER, selectedWidgetParentId());
    }

    /** Creates and selects one native Slider through the active document history pipeline. */
    public boolean createSlider() {
        if (!canCreateSlider()) return false;
        return createSelectedWidget(HudNodeKind.SLIDER);
    }

    public boolean editSelectedTextButtonText(String text) {
        if (text == null) return false;
        return editSelectedTextButton("Edit HUD button text", data -> data.text = text);
    }

    public boolean editSelectedTextButtonStyle(String styleName) {
        return editSelectedTextButton("Edit HUD button style", data -> data.styleName = styleName);
    }

    private boolean editSelectedTextButton(
            String label, Consumer<HudTextButtonData> mutation) {
        HudNode selected = selectedNode();
        if (selected == null || selected.textButton == null || currentResources == null) return false;
        return editSelectedNode(label, (node, relation) -> {
            mutation.accept(node.textButton);
            if (!currentResources.hasUsableTextButtonStyle(node.textButton.styleName,
                    node.textButton.fontAssetId != null)) {
                throw new HudEditRejectedException(
                    "The selected TextButton style is no longer available.");
            }
        });
    }

    public boolean editSelectedImageButtonStyle(String styleName) {
        HudNode selected = selectedNode();
        if (selected == null || selected.imageButton == null || currentResources == null) return false;
        return editSelectedNode("Edit HUD ImageButton style", (node, relation) -> {
            node.imageButton.styleName = styleName;
            if (!currentResources.hasUsableImageButtonStyle(styleName)) {
                throw new HudEditRejectedException("The selected ImageButton style is no longer available.");
            }
        });
    }

    public boolean editSelectedImageTextButtonText(String text) {
        if (text == null) return false;
        return editSelectedImageTextButton("Edit HUD ImageTextButton text", data -> data.text = text);
    }

    public boolean editSelectedImageTextButtonStyle(String styleName) {
        return editSelectedImageTextButton("Edit HUD ImageTextButton style",
                data -> data.styleName = styleName);
    }

    private boolean editSelectedImageTextButton(
            String label, Consumer<HudImageTextButtonData> mutation) {
        HudNode selected = selectedNode();
        if (selected == null || selected.imageTextButton == null || currentResources == null) return false;
        return editSelectedNode(label, (node, relation) -> {
            mutation.accept(node.imageTextButton);
            if (!currentResources.hasUsableImageTextButtonStyle(node.imageTextButton.styleName,
                    node.imageTextButton.fontAssetId != null)) {
                throw new HudEditRejectedException(
                        "The selected ImageTextButton style is no longer available.");
            }
        });
    }

    public boolean editSelectedTextFieldStyle(String styleName) {
        HudNode selected = selectedNode();
        if (selected == null || selected.textField == null || currentResources == null) return false;
        return editSelectedNode("Edit HUD TextField style", (node, relation) -> {
            node.textField.styleName = styleName;
            if (!currentResources.hasUsableTextFieldStyle(styleName,
                    node.textField.fontAssetId != null)) {
                throw new HudEditRejectedException(
                        "The selected TextField style must provide a font and fontColor.");
            }
        });
    }

    public boolean editSelectedSelectBoxStyle(String styleName) {
        HudNode selected = selectedNode();
        if (selected == null || selected.selectBox == null || currentResources == null) return false;
        return editSelectedNode("Edit HUD SelectBox style", (node, relation) -> {
            node.selectBox.styleName = styleName;
            if (!currentResources.hasUsableSelectBoxStyle(styleName,
                    node.selectBox.fontAssetId != null)) {
                throw new HudEditRejectedException(
                        "The selected SelectBox style must provide complete SelectBox, List and ScrollPane styles.");
            }
        });
    }

    public boolean editSelectedListStyle(String styleName) {
        HudNode selected = selectedNode();
        if (selected == null || selected.list == null || currentResources == null) return false;
        return editSelectedNode("Edit HUD List style", (node, relation) -> {
            node.list.styleName = styleName;
            if (!currentResources.hasUsableListStyle(styleName, node.list.fontAssetId != null)) {
                throw new HudEditRejectedException("The selected List style must provide a font and selection drawable.");
            }
        });
    }

    public boolean editSelectedCheckBoxText(String text) {
        if (text == null) return false;
        return editSelectedCheckBox("Edit HUD CheckBox text", data -> data.text = text);
    }

    public boolean editSelectedCheckBoxStyle(String styleName) {
        return editSelectedCheckBox("Edit HUD CheckBox style", data -> data.styleName = styleName);
    }

    private boolean editSelectedCheckBox(String label, Consumer<HudCheckBoxData> mutation) {
        HudNode selected = selectedNode();
        if (selected == null || selected.checkBox == null || currentResources == null) return false;
        return editSelectedNode(label, (node, relation) -> {
            mutation.accept(node.checkBox);
            if (!currentResources.hasUsableCheckBoxStyle(node.checkBox.styleName,
                    node.checkBox.fontAssetId != null)) {
                throw new HudEditRejectedException(
                        "The selected CheckBox style must provide a font, checkboxOn and checkboxOff.");
            }
        });
    }

    public boolean editSelectedSliderOrientation(HudSliderOrientation orientation) {
        if (orientation == null) return false;
        return editSelectedSlider("Edit HUD Slider orientation", data -> data.orientation = orientation);
    }

    public boolean editSelectedSliderMin(float min) {
        return editSelectedSlider("Edit HUD Slider minimum", data -> data.min = min);
    }

    public boolean editSelectedSliderMax(float max) {
        return editSelectedSlider("Edit HUD Slider maximum", data -> data.max = max);
    }

    public boolean editSelectedSliderStepSize(float stepSize) {
        return editSelectedSlider("Edit HUD Slider step", data -> data.stepSize = stepSize);
    }

    public boolean editSelectedSliderValue(float value) {
        return editSelectedSlider("Edit HUD Slider value", data -> data.value = value);
    }

    public boolean editSelectedSliderStyle(String styleName) {
        return editSelectedSlider("Edit HUD Slider style", data -> data.styleName = styleName);
    }

    public boolean editSelectedSliderDisabled(boolean disabled) {
        return editSelectedSlider("Edit HUD Slider disabled", data -> data.disabled = disabled);
    }

    public boolean editSelectedProgressBarOrientation(HudSliderOrientation orientation) {
        if (orientation == null) return false;
        return editSelectedProgressBar("Edit HUD ProgressBar orientation", data -> data.orientation = orientation);
    }

    public boolean editSelectedProgressBarMin(float min) {
        return editSelectedProgressBar("Edit HUD ProgressBar minimum", data -> data.min = min);
    }

    public boolean editSelectedProgressBarMax(float max) {
        return editSelectedProgressBar("Edit HUD ProgressBar maximum", data -> data.max = max);
    }

    public boolean editSelectedProgressBarStepSize(float stepSize) {
        return editSelectedProgressBar("Edit HUD ProgressBar step", data -> data.stepSize = stepSize);
    }

    public boolean editSelectedProgressBarValue(float value) {
        return editSelectedProgressBar("Edit HUD ProgressBar value", data -> data.value = value);
    }

    public boolean editSelectedProgressBarStyle(String styleName) {
        return editSelectedProgressBar("Edit HUD ProgressBar style", data -> data.styleName = styleName);
    }

    public boolean editSelectedProgressBarDisabled(boolean disabled) {
        return editSelectedProgressBar("Edit HUD ProgressBar disabled", data -> data.disabled = disabled);
    }

    private boolean editSelectedSlider(String label, Consumer<HudSliderData> mutation) {
        HudNode selected = selectedNode();
        if (selected == null || selected.slider == null || currentResources == null) return false;
        return editSelectedNode(label, (node, relation) -> {
            mutation.accept(node.slider);
            if (!currentResources.hasUsableSliderStyle(node.slider.styleName)) {
                throw new HudEditRejectedException(
                        "The selected Slider style is no longer available or usable.");
            }
        });
    }

    private boolean editSelectedProgressBar(String label, Consumer<HudProgressBarData> mutation) {
        HudNode selected = selectedNode();
        if (selected == null || selected.progressBar == null || currentResources == null) return false;
        return editSelectedNode(label, (node, relation) -> {
            mutation.accept(node.progressBar);
            if (!currentResources.hasUsableProgressBarStyle(node.progressBar.styleName)) {
                throw new HudEditRejectedException(
                        "The selected ProgressBar style is no longer available or usable.");
            }
        });
    }

    /** Applies or removes one native ImageButton image override in a single history operation. */
    public boolean editImageButtonImage(String nodeId, ImageButtonImageSlot slot, String resourceName) {
        if (isTestMode() || status != Status.READY || editorDocument == null || nodeId == null || slot == null) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit("Edit HUD ImageButton " + slot.label(), candidate -> {
            HudNode node = HudLayoutAuthoring.node(candidate, nodeId);
            if (node == null || node.imageButton == null) {
                throw new HudEditRejectedException("HUD ImageButton is no longer available.");
            }
            HudImageData image = null;
            if (resourceName != null && !resourceName.isBlank()) {
                image = new HudImageData();
                image.source = HudImageSource.REGION;
                image.resourceName = resourceName;
            }
            slot.set(node.imageButton, image);
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    /** Applies or removes one native ImageTextButton image override in a single history operation. */
    public boolean editImageTextButtonImage(String nodeId, ImageButtonImageSlot slot, String resourceName) {
        if (isTestMode() || status != Status.READY || editorDocument == null || nodeId == null || slot == null) return false;
        long revision = editorDocument.editSession().currentRevision();
        editorDocument.editSession().edit("Edit HUD ImageTextButton " + slot.label(), candidate -> {
            HudNode node = HudLayoutAuthoring.node(candidate, nodeId);
            if (node == null || node.imageTextButton == null) {
                throw new HudEditRejectedException("HUD ImageTextButton is no longer available.");
            }
            HudImageData image = null;
            if (resourceName != null && !resourceName.isBlank()) {
                image = new HudImageData();
                image.source = HudImageSource.REGION;
                image.resourceName = resourceName;
            }
            switch (slot) {
                case UP -> node.imageTextButton.imageUp = image;
                case DOWN -> node.imageTextButton.imageDown = image;
                case OVER -> node.imageTextButton.imageOver = image;
                case DISABLED -> node.imageTextButton.imageDisabled = image;
                case CHECKED -> node.imageTextButton.imageChecked = image;
                case CHECKED_DOWN -> node.imageTextButton.imageCheckedDown = image;
                case CHECKED_OVER -> node.imageTextButton.imageCheckedOver = image;
            }
            return candidate;
        });
        return editorDocument.editSession().currentRevision() != revision;
    }

    private void restoreDeletionSelectionAfterHistoryNavigation(
            HudDocumentEditSession.HistoryNavigationEvent event) {
        StructureSelection structure = event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO
                ? structuresByRevision.get(event.fromRevision())
                : structuresByRevision.get(event.toRevision());
        if (structure != null) {
            if (event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO) {
                selectCell(structure.beforeCellId());
                if (structure.beforeRange().size() > 1)
                    selectCellRange(structure.beforeEndId());
            } else selectCell(structure.afterCellId());
            return;
        }
        CellMoveSelection moved = event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO
                ? cellMovesByRevision.get(event.fromRevision()) : cellMovesByRevision.get(event.toRevision());
        if (moved != null) {
            selectNode(event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO
                    ? moved.beforeNodeId() : moved.widgetId());
            return;
        }
        DeletionSelection selection = event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO
                ? deletionsByRevision.get(event.fromRevision())
                : deletionsByRevision.get(event.toRevision());
        if (selection != null) {
            selectNode(event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO
                    ? selection.deletedNodeId() : selection.parentNodeId());
            return;
        }
        CreationSelection creation = event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO
                ? creationsByRevision.get(event.fromRevision())
                : creationsByRevision.get(event.toRevision());
        if (creation != null) {
            selectNode(event.navigation() == HudDocumentEditSession.HistoryNavigation.UNDO
                    ? creation.previousNodeId() : creation.createdNodeId());
        }
    }

    public void act(float delta) {
        if (interactiveTestSession != null) interactiveTestSession.act(delta);
        else if (authoringSession != null) authoringSession.act(delta);
    }

    /** Supplies logical Studio center bounds to the authoring HUD viewport and draws it. */
    public void draw(Rectangle centerBoundsLogical) {
        if (!configurePreview(centerBoundsLogical)) return;
        if (interactiveTestSession != null) interactiveTestSession.draw();
        else authoringSession.draw();
    }

    boolean configurePreview(Rectangle centerBoundsLogical) {
        if (authoringSession == null || centerBoundsLogical == null) return false;
        int x = Math.round(centerBoundsLogical.x);
        int y = Math.round(centerBoundsLogical.y);
        int width = Math.round(centerBoundsLogical.width);
        int height = Math.round(centerBoundsLogical.height);
        if (width <= 0 || height <= 0) return false;
        if (x != previewX || y != previewY || width != previewWidth || height != previewHeight) {
            authoringSession.configure(asset, x, y, width, height);
            if (materialized != null) authoringSession.prepare(materialized.root());
            previewX = x;
            previewY = y;
            previewWidth = width;
            previewHeight = height;
            rebuildSelectionOverlay();
        }
        if (interactiveTestSession != null) {
            interactiveTestSession.configure(x, y, width, height);
        }
        return true;
    }

    public boolean canEnterTestMode() {
        return interactiveTestSession == null && status == Status.READY
                && editorDocument != null && projectDir != null && asset != null && document != null;
    }

    /** Builds a fresh native Scene2D graph from the active document's defensive in-memory snapshot. */
    public boolean enterTestMode() {
        if (!canEnterTestMode()) return false;
        testModeErrorMessage = null;
        HudInteractiveTestSession candidate = null;
        try {
            candidate = HudInteractiveTestSession.create(projectDir, assetDatabase.get(), batch.get(),
                    editorDocument.asset(), editorDocument.document());
            if (previewX != Integer.MIN_VALUE) {
                candidate.configure(previewX, previewY, previewWidth, previewHeight);
            }
            cancelTransformGesture();
            cancelCellContentGesture();
            selectedCellRange = selectedCellId == null ? List.of() : List.of(selectedCellId);
            cellRangeEndId = selectedCellId;
            rebuildSelectionOverlay();
            transformCursor.clear();
            toolboxWidgetDragActive = false;
            interactiveTestSession = candidate;
            notifyListeners();
            return true;
        } catch (RuntimeException failure) {
            if (candidate != null) {
                try { candidate.dispose(); }
                catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            logCleanupFailure("HUD interactive test mode could not be entered.", failure);
            testModeErrorMessage = message(failure);
            notifyListeners();
            return false;
        }
    }

    /** Cancels native focus/gestures and destroys only the resources owned by the test instance. */
    public void exitTestMode() {
        HudInteractiveTestSession previous = interactiveTestSession;
        if (previous == null) return;
        interactiveTestSession = null;
        try { previous.dispose(); }
        catch (RuntimeException failure) {
            logCleanupFailure("Failed to dispose HUD interactive test mode.", failure);
        }
        notifyListeners();
    }

    public boolean isTestMode() { return interactiveTestSession != null; }

    public com.badlogic.gdx.scenes.scene2d.Stage testStage() {
        return interactiveTestSession != null ? interactiveTestSession.stage() : null;
    }

    public Actor testActor(String nodeId) {
        return interactiveTestSession != null ? interactiveTestSession.actor(nodeId) : null;
    }

    public boolean testViewportContains(int screenX, int screenY, int logicalWindowHeight) {
        return interactiveTestSession != null
                && interactiveTestSession.containsScreenPoint(screenX, screenY, logicalWindowHeight);
    }

    /** Routes a logical Studio-stage click through the HUD viewport to the editor overlay. */
    public boolean selectOverlayTargetAt(float studioStageX, float studioStageY) {
        if (isTestMode()) return false;
        HudSelectionTarget target = findOverlayTargetAt(studioStageX, studioStageY);
        if (target == null) return false;
        if (target.type() == HudSelectionTarget.Type.CELL) selectCell(target.nodeId());
        else selectNode(target.nodeId());
        return true;
    }

    /** Returns the current canvas target without changing selection. */
    public HudSelectionTarget overlayTargetAt(float studioStageX, float studioStageY) {
        return findOverlayTargetAt(studioStageX, studioStageY);
    }

    /** Captures a directly pointed authored cell widget before the Stage drag threshold. */
    public boolean beginCellContentGesture(float stageX, float stageY) {
        if (isTestMode() || toolboxWidgetDragActive || status != Status.READY
                || editorDocument == null || materialized == null || cellContentGesture != null
                || activeTransform != null || pendingMove != null) return false;
        Vector2 point = new Vector2();
        if (!hudPointAt(stageX, stageY, point) || !insideHudSurface(point))
            return false;
        // Dialog.show() reparents the visible Dialog to the authoring Stage.
        Actor leaf = deepestVisibleAuthoredActorAt(authoringSession.stage().getRoot(), point);
        if (leaf == null || leaf.getName() == null) return false;
        String widgetId = leaf.getName();
        HudSelectionTarget target = findOverlayTargetAt(stageX, stageY);
        if (!widgetId.equals(selectedNodeId)
                && (target == null || !widgetId.equals(target.nodeId()))) return false;
        HudTableCell cell = HudLayoutAuthoring.containingCell(document, widgetId);
        if (cell == null || cell.content == null || !widgetId.equals(cell.content.id)) return false;
        HudNode owner = HudLayoutAuthoring.tableOwner(document, cell.id);
        if (owner == null || !containsVisible(leaf, point)) return false;
        cellContentGesture = new CellContentGesture(editorDocument, editorDocument.editSession(),
                editorDocument.editSession().currentRevision(), owner.id, cell.id, widgetId,
                selectedNodeId);
        return true;
    }

    public boolean hasCellContentGesture() { return cellContentGesture != null; }

    public boolean startCellContentGesture(float stageX, float stageY, boolean pointable) {
        if (!cellGestureValid()) { cancelCellContentGesture(); return false; }
        cellContentGesture.dragging = true;
        clearHoveredOverlayTarget();
        updateCellContentGesture(stageX, stageY, pointable);
        return true;
    }

    public void updateCellContentGesture(float stageX, float stageY, boolean pointable) {
        if (!cellGestureValid()) { cancelCellContentGesture(); return; }
        CellContentGesture gesture = cellContentGesture;
        if (!gesture.dragging) return;
        gesture.destinationCellId = pointable ? cellDestinationAt(gesture, stageX, stageY) : null;
        if (gesture.destinationCellId == null) {
            authoringSession.clearDropFeedback();
        } else {
            HudTableCell destination = HudLayoutAuthoring.cell(document, gesture.destinationCellId);
            Cell<?> nativeCell = materialized.cell(gesture.destinationCellId);
            Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(nativeCell,
                    authoringSession.selectionOverlayActor(), new Rectangle());
            authoringSession.showDropFeedback(destination.content == null ? "Déplacer" : "Échanger", bounds);
        }
        Vector2 point = studioToHud(stageX, stageY, new Vector2());
        if (point != null) authoringSession.showCellDragGhost(gesture.widgetId, point.x, point.y);
    }

    public boolean endCellContentGesture(float stageX, float stageY, boolean pointable) {
        if (cellContentGesture == null) return false;
        if (!cellGestureValid()) { cancelCellContentGesture(); return true; }
        updateCellContentGesture(stageX, stageY, pointable);
        CellContentGesture gesture = cellContentGesture;
        cancelCellContentGesture();
        if (gesture == null || !gesture.dragging || gesture.destinationCellId == null) {
            if (gesture != null && !gesture.dragging) selectNode(gesture.widgetId);
            return true;
        }
        String destinationId = gesture.destinationCellId;
        HudDocumentV1 current = editorDocument.document();
        HudTableCell source = HudLayoutAuthoring.cell(current, gesture.sourceCellId);
        HudTableCell destination = HudLayoutAuthoring.cell(current, destinationId);
        HudNode sourceOwner = HudLayoutAuthoring.tableOwner(current, gesture.sourceCellId);
        HudNode destinationOwner = HudLayoutAuthoring.tableOwner(current, destinationId);
        if (source == null || destination == null || source.content == null
                || !gesture.widgetId.equals(source.content.id) || sourceOwner == null
                || destinationOwner == null || !gesture.tableId.equals(sourceOwner.id)
                || !gesture.tableId.equals(destinationOwner.id)) return true;
        try {
            gesture.editSession.edit(destination.content == null ? "Move HUD cell content"
                    : "Swap HUD cell contents", candidate -> {
                if (!HudLayoutAuthoring.moveCellContent(candidate, gesture.tableId,
                        gesture.sourceCellId, gesture.widgetId, destinationId))
                    throw new HudEditRejectedException("HUD cell contents changed during the gesture.");
                return candidate;
            });
            cellMovesByRevision.put(gesture.editSession.currentRevision(),
                    new CellMoveSelection(gesture.previousSelectionId, gesture.widgetId));
            selectNode(gesture.widgetId);
        } catch (HudEditRejectedException ignored) {
            // A rejected edit never publishes a document or history state.
        }
        return true;
    }

    public void cancelCellContentGesture() {
        cellContentGesture = null;
        if (authoringSession != null) {
            authoringSession.clearDropFeedback();
            authoringSession.clearCellDragGhost();
        }
    }

    private boolean cellGestureValid() {
        CellContentGesture gesture = cellContentGesture;
        return gesture != null && !isTestMode() && status == Status.READY
                && gesture.editorDocument == editorDocument
                && gesture.editSession == editorDocument.editSession()
                && gesture.revision == gesture.editSession.currentRevision();
    }

    private String cellDestinationAt(CellContentGesture gesture, float stageX, float stageY) {
        Vector2 point = new Vector2();
        if (!hudPointAt(stageX, stageY, point) || !insideHudSurface(point)) return null;
        HudNode table = HudLayoutAuthoring.node(document, gesture.tableId);
        if (table == null || table.table == null) return null;
        Vector2 overlayPoint = authoringSession.selectionOverlayActor()
                .stageToLocalCoordinates(new Vector2(point));
        Actor visualLeaf = deepestVisibleAuthoredActorAt(authoringSession.stage().getRoot(), point);
        String visualId = visualLeaf != null ? visualLeaf.getName() : null;
        for (var row : table.table.rows) for (HudTableCell cell : row.cells) {
            if (cell == null || cell.id.equals(gesture.sourceCellId)) continue;
            Cell<?> nativeCell = materialized.cell(cell.id);
            if (nativeCell == null) continue;
            Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(nativeCell,
                    authoringSession.selectionOverlayActor(), new Rectangle());
            if (bounds.width > 0f && bounds.height > 0f && bounds.contains(overlayPoint)
                    && (gesture.tableId.equals(visualId)
                    || cell.content != null && isAuthoredAncestor(cell.content.id, visualId)))
                return cell.id;
        }
        return null;
    }

    /** Starts an editor-only transform gesture when a selected FREE node owns the pointed gizmo region. */
    public boolean beginTransformGesture(float studioStageX, float studioStageY) {
        if (isTestMode()) return false;
        return beginTransformGesture(studioStageX, studioStageY, false);
    }

    /** Starts only a transform-handle gesture so body movement can defer click resolution. */
    public boolean beginTransformHandleGesture(float studioStageX, float studioStageY) {
        if (isTestMode()) return false;
        return beginTransformGesture(studioStageX, studioStageY, true);
    }

    private boolean beginTransformGesture(float studioStageX, float studioStageY,
                                          boolean handleOnly) {
        if (activeTransform != null) return true;
        cancelPendingMoveGesture();
        TransformContext context = selectedFreeTransformContext();
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        if (context == null || hudPoint == null) return false;

        HudTransformHandle handle = authoringSession.transformHandleAt(hudPoint.x, hudPoint.y);
        if (handleOnly && handle == null) return false;
        boolean move = handle == null && authoringSession.containsTransformBounds(hudPoint.x, hudPoint.y);
        if (!move && handle == null) return false;

        // Child targets remain selectable: body dragging only takes priority in otherwise-empty/parent space.
        if (move) {
            HudSelectionTarget target = authoringSession.selectionTargetAt(hudPoint.x, hudPoint.y);
            if (target != null && !target.nodeId().equals(selectedNodeId)
                    && target.type() != HudSelectionTarget.Type.PARENT) return false;
        }
        activeTransform = new TransformGesture(context, handle, hudPoint.x, hudPoint.y);
        clearHoveredOverlayTarget();
        transformCursor.showFor(handle);
        return true;
    }

    /**
     * Captures the selected FREE node and its original transform without editing or previewing it.
     * The caller resolves the drag threshold in Studio logical-pixel coordinates.
     */
    public boolean beginPendingMoveGesture(float studioStageX, float studioStageY) {
        if (isTestMode()) return false;
        if (activeTransform != null || pendingMove != null) return false;
        TransformContext context = selectedFreeTransformContext();
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        if (context == null || hudPoint == null
                || authoringSession.transformHandleAt(hudPoint.x, hudPoint.y) != null
                || !authoringSession.containsTransformBounds(hudPoint.x, hudPoint.y)) return false;
        pendingMove = new PendingMoveGesture(editorDocument, document, context,
                hudPoint.x, hudPoint.y);
        clearHoveredOverlayTarget();
        transformCursor.showFor(null);
        return true;
    }

    /** Starts the existing move preview from the original press point after the UI threshold is crossed. */
    public boolean startPendingMoveGesture(float studioStageX, float studioStageY) {
        PendingMoveGesture pending = pendingMove;
        pendingMove = null;
        if (!isPendingMoveValid(pending)) {
            transformCursor.clear();
            return false;
        }
        activeTransform = new TransformGesture(pending.context, null,
                pending.startHudX, pending.startHudY);
        activeTransform.dragging = true;
        updateTransformGesture(studioStageX, studioStageY);
        return true;
    }

    public boolean cancelPendingMoveGesture() {
        if (pendingMove == null) return false;
        pendingMove = null;
        transformCursor.clear();
        return true;
    }

    public boolean hasPendingMoveGesture() { return pendingMove != null; }

    private boolean isPendingMoveValid(PendingMoveGesture pending) {
        if (pending == null || pending.editorDocument != editorDocument
                || pending.document != document
                || !Objects.equals(pending.context.nodeId, selectedNodeId)) return false;
        TransformContext current = selectedFreeTransformContext();
        return current != null && current.actor == pending.context.actor;
    }

    /** Updates only transient Scene2D/overlay bounds. The document and history remain untouched. */
    public boolean updateTransformGesture(float studioStageX, float studioStageY) {
        if (isTestMode()) return false;
        if (activeTransform == null) return false;
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        if (hudPoint == null) return true;
        float deltaX = hudPoint.x - activeTransform.startX;
        float deltaY = hudPoint.y - activeTransform.startY;
        if (!activeTransform.dragging
                && deltaX * deltaX + deltaY * deltaY
                < transformDragThreshold() * transformDragThreshold()) return true;
        activeTransform.dragging = true;
        activeTransform.currentOverlayBounds = activeTransform.handle == null
                ? HudTransformGeometry.move(activeTransform.context.overlayBounds, deltaX, deltaY)
                : HudTransformGeometry.resize(activeTransform.context.overlayBounds, activeTransform.handle,
                deltaX, deltaY);
        activeTransform.currentOverlayBounds = HudTransformGeometry.clampToSurface(
                activeTransform.currentOverlayBounds,
                 authoringSession.surfaceWidth(), authoringSession.surfaceHeight());
        Actor actor = materialized != null ? materialized.actor(activeTransform.context.nodeId) : null;
        if (authoringSession != null) {
            authoringSession.previewAuthoredActorBounds(actor, activeTransform.currentOverlayBounds);
            authoringSession.previewTransformBounds(activeTransform.currentOverlayBounds);
        }
        return true;
    }

    /** Commits one document edit for a completed drag; click/noise gestures restore their preview without history. */
    public boolean endTransformGesture(float studioStageX, float studioStageY) {
        if (isTestMode()) return false;
        if (activeTransform == null) return false;
        updateTransformGesture(studioStageX, studioStageY);
        TransformGesture completed = activeTransform;
        activeTransform = null;
        if (!completed.dragging) {
            restoreTransformPreview(completed.context);
            refreshTransformGizmo();
            return true;
        }
        HudTransformGeometry.Bounds localBounds = overlayToParentBounds(completed.context,
                completed.currentOverlayBounds);
        HudFreeTransform.Result result = HudFreeTransform.resolve(completed.context.snapshot, localBounds,
                completed.changesWidth(), completed.changesHeight());
        try {
            editorDocument.editSession().edit(completed.handle == null ? "Move HUD node" : "Resize HUD node",
                    candidate -> {
                        if (!HudFreeTransformAuthoring.apply(candidate, completed.context.nodeId, result,
                                completed.changesWidth(), completed.changesHeight())) {
                            throw new HudEditRejectedException("Selected HUD node is no longer a FREE child.");
                        }
                        return candidate;
                    });
        } catch (RuntimeException failure) {
            restoreTransformPreview(completed.context);
            refreshTransformGizmo();
            transformCursor.clear();
            throw failure;
        }
        transformCursor.clear();
        return true;
    }

    /** Restores the transient preview without publishing a document/history change. */
    public boolean cancelTransformGesture() {
        boolean cancelledPending = cancelPendingMoveGesture();
        if (activeTransform == null) return cancelledPending;
        TransformGesture cancelled = activeTransform;
        activeTransform = null;
        restoreTransformPreview(cancelled.context);
        refreshTransformGizmo();
        transformCursor.clear();
        return true;
    }

    public boolean hasActiveTransformGesture() { return activeTransform != null; }
    public boolean hasActiveResizeGesture() {
        return activeTransform != null && activeTransform.handle != null;
    }

    /** Resolves the resize cursor from the same gizmo handle hit-test used to begin resize. */
    public void updateTransformCursorAt(float studioStageX, float studioStageY) {
        if (activeTransform != null) {
            transformCursor.showFor(activeTransform.handle);
            return;
        }
        transformCursor.showFor(transformHandleAt(studioStageX, studioStageY));
    }

    /** Relinquishes HUD cursor ownership; it never forces Arrow if HUD owns nothing. */
    public void clearTransformCursor() { transformCursor.clear(); }

    /** Updates only the editor-only overlay hover state; it never changes HUD selection. */
    public boolean updateHoveredOverlayTargetAt(float studioStageX, float studioStageY) {
        if (toolboxWidgetDragActive || activeTransform != null) return false;
        HudSelectionTarget target = findOverlayTargetAt(studioStageX, studioStageY);
        return authoringSession != null && authoringSession.setHoveredSelectionTarget(target);
    }

    /** Clears transient canvas hover when the pointer leaves the Studio input host. */
    public boolean clearHoveredOverlayTarget() {
        if (activeTransform != null) return false;
        return authoringSession != null && authoringSession.clearHoveredSelectionTarget();
    }

    public void addListener(Runnable listener) { listeners.add(listener); }
    public boolean isShowingLayoutBounds() { return showLayoutBounds; }
    public void setShowLayoutBounds(boolean show) {
        if (showLayoutBounds == show) return;
        showLayoutBounds = show;
        if (authoringSession != null) authoringSession.setShowLayoutBounds(show);
        notifyListeners();
    }
    public void refreshDocumentMetadata(HudScreenEditorDocument document) {
        if (editorDocument != document) return;
        HudScreenAsset updated = document.asset();
        boolean resourcesChanged = asset == null || !Objects.equals(asset.atlasId, updated.atlasId)
                || !Objects.equals(asset.skinId, updated.skinId)
                || !Objects.equals(asset.textureProfileId, updated.textureProfileId);
        if (resourcesChanged) exitTestMode();
        if (resourcesChanged) {
            resourcesStale = true;
        }
        asset = updated;
        notifyListeners();
    }
    /** Keeps current live resources usable, but forces transactional replacement on next install. */
    public void markResourcesStale() {
        exitTestMode();
        resourcesStale = true;
    }
    /** Rebuilds the active preview from its current in-memory document after an owned resource changes. */
    public void reloadResources() {
        exitTestMode();
        if (editorDocument == null || projectDir == null) return;
        HudScreenAsset currentAsset = editorDocument.asset();
        HudDocumentV1 currentDocument = editorDocument.document();
        try {
            HudValidationResult structural = new HudDocumentValidator().validate(currentDocument);
            requireValid(structural);
            installCandidate(currentAsset, currentDocument, structural);
        } catch (RuntimeException failure) {
            resourcesStale = true; // Existing projection/resources remain live for a later retry.
            throw failure;
        }
    }
    public boolean resourcesStale() { return resourcesStale; }
    public boolean projects(HudScreenEditorDocument document) { return editorDocument == document; }
    public Status status() { return status; }
    public String screenId() { return screenId; }
    public HudScreenAsset asset() { return asset; }
    public HudDocumentV1 document() { return document; }
    public HudValidationResult validation() { return validation; }
    public String selectedNodeId() { return selectedNodeId; }
    public String selectedCellId() { return selectedCellId; }
    public String errorMessage() { return errorMessage; }
    public String testModeErrorMessage() { return testModeErrorMessage; }
    public HudNode selectedNode() {
        return validation != null && validation.isValid() && selectedNodeId != null
                ? validation.validatedDocument().node(selectedNodeId) : null;
    }
    public MaterializedHud materializedHud() { return materialized; }
    public List<HudSelectionTarget> selectionTargets() {
        return authoringSession != null ? authoringSession.selectionTargets() : List.of();
    }
    HudSelectionTarget hoveredSelectionTarget() {
        return authoringSession != null ? authoringSession.hoveredSelectionTarget() : null;
    }
    HudSelectionOverlay.VisualState selectionVisualState(HudSelectionTarget target) {
        return authoringSession != null ? authoringSession.selectionVisualState(target)
                : HudSelectionOverlay.VisualState.IDLE;
    }
    HudTransformHandle transformHandleAt(float studioStageX, float studioStageY) {
        if (status != Status.READY || authoringSession == null || materialized == null
                || previewX == Integer.MIN_VALUE) return null;
        var viewport = authoringSession.viewport();
        if (studioStageX < viewport.getScreenX()
                || studioStageX >= viewport.getScreenX() + viewport.getScreenWidth()
                || studioStageY < viewport.getScreenY()
                || studioStageY >= viewport.getScreenY() + viewport.getScreenHeight()) return null;
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        return hudPoint != null ? authoringSession.transformHandleAt(hudPoint.x, hudPoint.y) : null;
    }
    HudTransformHandle transformCursorHandleAt(float studioStageX, float studioStageY) {
        return activeTransform != null ? activeTransform.handle : transformHandleAt(studioStageX, studioStageY);
    }
    com.badlogic.gdx.graphics.Cursor.SystemCursor ownedTransformCursor() {
        return transformCursor.ownedCursor();
    }
    boolean isShowingTransformGizmo() {
        return authoringSession != null && authoringSession.isShowingTransformGizmo();
    }
    HudTransformGeometry.Bounds transformGizmoBounds() {
        return authoringSession != null ? authoringSession.transformBounds() : null;
    }
    public HudTableCell selectedCell() {
        return selectedCellId != null && document != null ? HudLayoutAuthoring.cell(document, selectedCellId) : null;
    }

    /** Resolves the top visual authored branch to its deepest compatible layout parent. */
    public WidgetDropTarget widgetDropTargetAt(float studioStageX, float studioStageY) {
        if (status != Status.READY || authoringSession == null || materialized == null
                || document == null || document.root == null || previewX == Integer.MIN_VALUE) return null;
        var viewport = authoringSession.viewport();
        if (studioStageX < viewport.getScreenX()
                || studioStageX >= viewport.getScreenX() + viewport.getScreenWidth()
                || studioStageY < viewport.getScreenY()
                || studioStageY >= viewport.getScreenY() + viewport.getScreenHeight()) return null;
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        if (hudPoint == null || !insideHudSurface(hudPoint)) return null;
        HudSelectionTarget cellTarget = authoringSession.selectionTargetAt(hudPoint.x, hudPoint.y);
        if (cellTarget != null && cellTarget.type() == HudSelectionTarget.Type.CELL) {
            HudTableCell cell = HudLayoutAuthoring.cell(document, cellTarget.nodeId());
            HudNode owner = HudLayoutAuthoring.tableOwner(document, cellTarget.nodeId());
            if (cell != null && cell.content == null && owner != null
                    && HudLayoutAuthoring.canAddChild(document, owner.id)) {
                return new WidgetDropTarget(owner.id, null, false,
                        owner.id.equals(document.root.id), owner.id, cell.id);
            }
        }
        Actor rootActor = materialized.actor(document.root.id);
        Actor visualLeaf = deepestVisibleAuthoredActorAt(rootActor, hudPoint);
        if (visualLeaf == null) return null;

        String nodeId = visualLeaf.getName();
        while (nodeId != null) {
            HudNode node = validation.validatedDocument().node(nodeId);
            Actor actor = materialized.actor(nodeId);
            if (node == null || actor == null) return null;
            if (HudLayoutAuthoring.canAddChild(document, nodeId)) {
                if (!hasVisibleSurface(actor) || !acceptsDropAt(node, actor, hudPoint)) return null;
                return new WidgetDropTarget(nodeId, freePlacementAt(node, actor, hudPoint),
                        false, nodeId.equals(document.root.id), nodeId);
            }

            String parentId = HudLayoutAuthoring.parentId(document, nodeId);
            if (node.kind == HudNodeKind.CONTAINER || node.kind == HudNodeKind.SCROLL_PANE
                    || parentId == null) return null;
            nodeId = parentId;
        }
        return null;
    }

    /** Resolves an explicitly captured selection only inside its nearest visible authored ancestor. */
    public WidgetDropTarget preselectedWidgetDropTargetAt(String selectedParentId,
                                                           float studioStageX, float studioStageY) {
        if (selectedParentId == null || status != Status.READY || authoringSession == null
                || materialized == null || document == null || document.root == null
                || previewX == Integer.MIN_VALUE || !HudLayoutAuthoring.canAddChild(document, selectedParentId)) {
            return null;
        }
        var viewport = authoringSession.viewport();
        if (studioStageX < viewport.getScreenX()
                || studioStageX >= viewport.getScreenX() + viewport.getScreenWidth()
                || studioStageY < viewport.getScreenY()
                || studioStageY >= viewport.getScreenY() + viewport.getScreenHeight()) return null;
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        if (hudPoint == null || !insideHudSurface(hudPoint)) return null;

        Actor selectedActor = materialized.actor(selectedParentId);
        HudNode selected = validation.validatedDocument().node(selectedParentId);
        Actor visualLeaf = deepestVisibleAuthoredActorAt(materialized.actor(document.root.id), hudPoint);
        if (selectedActor == null || !HudOverlayGeometry.isEffectivelyVisible(selectedActor)
                || selected == null || visualLeaf == null
                || !sameAuthoredBranch(visualLeaf.getName(), selectedParentId)) return null;

        String feedbackParentId = nearestVisibleAncestorId(selectedParentId);
        if (feedbackParentId == null) return null;
        Actor feedbackParent = materialized.actor(feedbackParentId);
        if (feedbackParent == null || !containsVisible(feedbackParent, hudPoint)) return null;
        return new WidgetDropTarget(selectedParentId,
                freePlacementAt(selected, selectedActor, hudPoint), true, false, feedbackParentId);
    }

    /** Whether a direct ancestor target may yield to the captured selected layout. */
    public boolean widgetDropTargetMayYieldToPreselection(WidgetDropTarget target,
                                                           String selectedParentId) {
        return target != null && selectedParentId != null
                && !target.parentId().equals(selectedParentId)
                && (target.fallback() || isAuthoredAncestor(target.parentId(), selectedParentId));
    }

    private static HudFreePlacement freePlacementAt(HudNode parent, Actor actor, Vector2 hudPoint) {
        if (parent.kind != HudNodeKind.GROUP) return null;
        Vector2 local = actor.stageToLocalCoordinates(new Vector2(hudPoint));
        HudFreePlacement placement = new HudFreePlacement();
        placement.offsetX = Math.max(0f, Math.min(actor.getWidth(), local.x));
        placement.offsetY = Math.max(0f, Math.min(actor.getHeight(), local.y));
        return placement;
    }

    /** Shows transient DnD feedback without changing authored selection or document state. */
    public void showWidgetDropFeedback(WidgetDropTarget target) {
        if (target == null || authoringSession == null || materialized == null) {
            clearWidgetDropFeedback();
            return;
        }
        Actor destinationActor = materialized.actor(target.feedbackParentId());
        if (destinationActor == null) {
            clearWidgetDropFeedback();
            return;
        }
        Rectangle bounds = visibleBoundsInOverlay(destinationActor,
                authoringSession.selectionOverlayActor());
        if (bounds.width <= 0f || bounds.height <= 0f) {
            clearWidgetDropFeedback();
            return;
        }
        authoringSession.showDropFeedback(target.preselected()
                ? target.parentId() + " — présélection" : target.parentId(), bounds);
    }

    public void clearWidgetDropFeedback() {
        if (authoringSession != null) authoringSession.clearDropFeedback();
    }

    public void beginToolboxWidgetDrag() {
        if (isTestMode()) return;
        cancelCellContentGesture();
        toolboxWidgetDragActive = true;
        clearHoveredOverlayTarget();
        if (authoringSession != null) authoringSession.clearTransformGizmo();
        transformCursor.clear();
    }

    public void endToolboxWidgetDrag() {
        if (!toolboxWidgetDragActive) return;
        toolboxWidgetDragActive = false;
        clearWidgetDropFeedback();
        rebuildSelectionOverlay();
    }

    /** Resolves a screen pointer through the persistent authoring viewport to the nearest valid parent. */
    public ImageDropTarget imageDropTargetAt(int screenX, int screenY, int logicalWindowHeight) {
        return imageDropTargetAt(screenX, screenY, logicalWindowHeight, 0f, 0f);
    }

    public ImageDropTarget imageDropTargetAt(int screenX, int screenY, int logicalWindowHeight,
                                             float imageWidth, float imageHeight) {
        if (status != Status.READY || authoringSession == null || materialized == null
                || logicalWindowHeight <= 0) return null;
        var viewport = authoringSession.viewport();
        int top = logicalWindowHeight - viewport.getScreenY() - viewport.getScreenHeight();
        if (screenX < viewport.getScreenX()
                || screenX >= viewport.getScreenX() + viewport.getScreenWidth()
                || screenY < top || screenY >= top + viewport.getScreenHeight()) return null;

        Vector2 hudPoint = screenToHud(viewport, logicalWindowHeight, screenX, screenY, new Vector2());
        if (!insideHudSurface(hudPoint)) return null;
        Actor actor = deepestDropActorAt(materialized.root(), hudPoint);
        while (actor != null) {
            String nodeId = actor.getName();
            if (nodeId != null && materialized.actor(nodeId) == actor) {
                HudNode node = validation.validatedDocument().node(nodeId);
                if (node != null && node.kind == games.pixscape.runtime.hud.document.HudNodeKind.IMAGE) {
                    return null;
                }
                if (node != null && (node.kind == games.pixscape.runtime.hud.document.HudNodeKind.IMAGE_BUTTON
                        || node.kind == games.pixscape.runtime.hud.document.HudNodeKind.IMAGE_TEXT_BUTTON)) {
                    return new ImageDropTarget(null, null, 0f, 0f, nodeId);
                }
                if (node != null && node.kind == games.pixscape.runtime.hud.document.HudNodeKind.CONTAINER
                        && !HudLayoutAuthoring.canAddChild(document, nodeId)) return null;
                if (node != null && (node.kind == HudNodeKind.SCROLL_PANE
                        || node.kind == HudNodeKind.WINDOW || node.kind == HudNodeKind.DIALOG)
                        && (!HudLayoutAuthoring.canAddChild(document, nodeId)
                        || !acceptsDropAt(node, actor, hudPoint))) return null;
                if (!HudLayoutAuthoring.canAddChild(document, nodeId)) {
                    actor = actor.getParent();
                    continue;
                }
                games.pixscape.runtime.hud.document.HudFreePlacement placement = null;
                if (node != null && node.kind == games.pixscape.runtime.hud.document.HudNodeKind.GROUP) {
                    DropGeometry geometry = freeDropGeometry(actor, hudPoint, imageWidth, imageHeight);
                    placement = new games.pixscape.runtime.hud.document.HudFreePlacement();
                    placement.offsetX = geometry.localX();
                    placement.offsetY = geometry.localY();
                    return new ImageDropTarget(nodeId, placement,
                            geometry.localWidth(), geometry.localHeight());
                }
                return new ImageDropTarget(nodeId, placement,
                        positiveDimension(imageWidth), positiveDimension(imageHeight));
            }
            actor = actor.getParent();
        }
        HudNode root = document.root;
        Actor rootActor = root != null ? materialized.actor(root.id) : null;
        if (rootActor != null && rootActor.isVisible()
                && HudLayoutAuthoring.canAddChild(document, root.id)
                && acceptsDropAt(root, rootActor, hudPoint)) {
            if (root.kind != games.pixscape.runtime.hud.document.HudNodeKind.GROUP) {
                return new ImageDropTarget(root.id, null,
                        positiveDimension(imageWidth), positiveDimension(imageHeight));
            }
            DropGeometry geometry = freeDropGeometry(rootActor, hudPoint, imageWidth, imageHeight);
            games.pixscape.runtime.hud.document.HudFreePlacement placement =
                    new games.pixscape.runtime.hud.document.HudFreePlacement();
            placement.offsetX = geometry.localX();
            placement.offsetY = geometry.localY();
            return new ImageDropTarget(root.id, placement,
                    geometry.localWidth(), geometry.localHeight());
        }
        return null;
    }

    /** Finds the topmost materialized node actor without relying on layout-group touchability. */
    private Actor deepestDropActorAt(Actor actor, Vector2 stagePoint) {
        return deepestVisibleAuthoredActorAt(actor, stagePoint);
    }

    /** Uses native actor bounds, clipping and draw order without relying on touchability. */
    private Actor deepestVisibleAuthoredActorAt(Actor actor, Vector2 stagePoint) {
        if (actor == null || !actor.isVisible()) return null;
        String nodeId = actor.getName();
        HudNode node = nodeId != null && materialized.actor(nodeId) == actor
                ? validation.validatedDocument().node(nodeId) : null;
        boolean insideChildClip = !clipsChildren(actor) || containsChildClip(actor, stagePoint);
        if (actor instanceof Group group) {
            if (insideChildClip) {
                var children = group.getChildren();
                for (int index = children.size - 1; index >= 0; index--) {
                    Actor found = deepestVisibleAuthoredActorAt(children.get(index), stagePoint);
                    if (found != null) return found;
                }
            }
        }
        return node != null && contains(actor, stagePoint) ? actor : null;
    }

    private static boolean clipsChildren(Actor actor) {
        return actor instanceof Container<?> container && container.getClip()
                || actor instanceof Table table && table.getClip()
                || actor instanceof ScrollPane;
    }

    private boolean hasVisibleSurface(Actor actor) {
        if (authoringSession == null) return false;
        Rectangle bounds = visibleBoundsInOverlay(actor, authoringSession.selectionOverlayActor());
        return bounds.width > 0f && bounds.height > 0f;
    }

    private boolean containsVisible(Actor actor, Vector2 stagePoint) {
        if (!HudOverlayGeometry.isEffectivelyVisible(actor)) return false;
        if (actor instanceof ScrollPane || actor instanceof Window) {
            if (!containsChildClip(actor, stagePoint)) return false;
        } else if (!contains(actor, stagePoint)) return false;
        for (Actor ancestor = actor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (clipsChildren(ancestor) && !containsChildClip(ancestor, stagePoint)) return false;
        }
        return true;
    }

    private boolean sameAuthoredBranch(String firstId, String secondId) {
        return isAuthoredAncestor(firstId, secondId) || isAuthoredAncestor(secondId, firstId);
    }

    private String nearestVisibleAncestorId(String nodeId) {
        for (String ancestorId = nodeId; ancestorId != null;
             ancestorId = HudLayoutAuthoring.parentId(document, ancestorId)) {
            Actor ancestor = materialized.actor(ancestorId);
            if (ancestor != null && hasVisibleSurface(ancestor)) return ancestorId;
        }
        return null;
    }

    private boolean isAuthoredAncestor(String ancestorId, String nodeId) {
        if (ancestorId == null || nodeId == null) return false;
        for (String currentId = nodeId; currentId != null;
             currentId = HudLayoutAuthoring.parentId(document, currentId)) {
            if (ancestorId.equals(currentId)) return true;
        }
        return false;
    }

    private static Rectangle visibleBoundsInOverlay(Actor actor, Actor overlay) {
        if (!HudOverlayGeometry.isEffectivelyVisible(actor)) return new Rectangle();
        Rectangle visible = actor instanceof ScrollPane pane
                ? HudOverlayGeometry.scrollPaneContentBoundsInOverlay(pane, overlay, new Rectangle())
                : actor instanceof Window window
                ? HudOverlayGeometry.windowContentBoundsInOverlay(window, overlay, new Rectangle())
                : HudOverlayGeometry.actorBoundsInOverlay(actor, overlay, new Rectangle());
        for (Actor ancestor = actor.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (!clipsChildren(ancestor)) continue;
            Rectangle clip = ancestor instanceof ScrollPane pane
                    ? HudOverlayGeometry.scrollPaneContentBoundsInOverlay(pane, overlay, new Rectangle())
                    : ancestor instanceof Window window
                    ? HudOverlayGeometry.windowContentBoundsInOverlay(window, overlay, new Rectangle())
                    : HudOverlayGeometry.actorBoundsInOverlay(ancestor, overlay, new Rectangle());
            float minX = Math.max(visible.x, clip.x);
            float minY = Math.max(visible.y, clip.y);
            float maxX = Math.min(visible.x + visible.width, clip.x + clip.width);
            float maxY = Math.min(visible.y + visible.height, clip.y + clip.height);
            visible.set(minX, minY, Math.max(0f, maxX - minX), Math.max(0f, maxY - minY));
        }
        return visible;
    }

    private static boolean contains(Actor actor, Vector2 stagePoint) {
        Vector2 local = actor.stageToLocalCoordinates(new Vector2(stagePoint));
        return local.x >= 0f && local.x <= actor.getWidth()
                && local.y >= 0f && local.y <= actor.getHeight();
    }

    private static boolean containsChildClip(Actor actor, Vector2 stagePoint) {
        if (actor instanceof ScrollPane pane) {
            Vector2 local = pane.stageToLocalCoordinates(new Vector2(stagePoint));
            return HudOverlayGeometry.scrollPaneContentBoundsLocal(pane, new Rectangle()).contains(local);
        }
        if (actor instanceof Window window) {
            Vector2 local = window.stageToLocalCoordinates(new Vector2(stagePoint));
            return HudOverlayGeometry.windowContentBoundsLocal(window, new Rectangle()).contains(local);
        }
        return contains(actor, stagePoint);
    }

    private static boolean acceptsDropAt(HudNode node, Actor actor, Vector2 stagePoint) {
        return (node.kind != HudNodeKind.SCROLL_PANE && node.kind != HudNodeKind.WINDOW
                && node.kind != HudNodeKind.DIALOG)
                || containsChildClip(actor, stagePoint);
    }

    /** Routes one editor wheel event through the materialized native Scene2D actor tree. */
    public boolean scrollHudAt(float studioStageX, float studioStageY, float amountX, float amountY) {
        if (status != Status.READY || authoringSession == null || materialized == null) return false;
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        if (hudPoint == null || !insideHudSurface(hudPoint)) return false;
        Actor target = deepestVisibleAuthoredActorAt(materialized.root(), hudPoint);
        if (target == null) return false;
        ScrollPane pane = null;
        for (Actor current = target; current != null; current = current.getParent()) {
            if (current instanceof ScrollPane scrollPane) {
                pane = scrollPane;
                break;
            }
        }
        if (pane == null || !containsChildClip(pane, hudPoint)) return false;
        InputEvent event = new InputEvent();
        event.setType(InputEvent.Type.scrolled);
        event.setStage(authoringSession.stage());
        event.setStageX(hudPoint.x);
        event.setStageY(hudPoint.y);
        event.setScrollAmountX(amountX);
        event.setScrollAmountY(amountY);
        target.fire(event);
        if (!event.isHandled()) return false;
        pane.updateVisualScroll();
        pane.layout();
        rebuildSelectionOverlay();
        return true;
    }

    private DropGeometry freeDropGeometry(Actor parent, Vector2 hudPoint,
                                          float imageWidth, float imageHeight) {
        if (imageWidth <= 0f || imageHeight <= 0f) {
            Vector2 local = parent.stageToLocalCoordinates(new Vector2(hudPoint));
            return new DropGeometry(local.x, local.y, 0f, 0f);
        }
        HudTransformGeometry.Bounds clamped = HudTransformGeometry.clampToSurface(
                new HudTransformGeometry.Bounds(hudPoint.x, hudPoint.y, imageWidth, imageHeight),
                 authoringSession.surfaceWidth(), authoringSession.surfaceHeight());
        Vector2 lower = parent.stageToLocalCoordinates(new Vector2(clamped.x(), clamped.y()));
        Vector2 upper = parent.stageToLocalCoordinates(new Vector2(clamped.right(), clamped.top()));
        return new DropGeometry(lower.x, lower.y,
                Math.abs(upper.x - lower.x), Math.abs(upper.y - lower.y));
    }

    private static float positiveDimension(float value) { return value > 0f ? value : 0f; }

    static Vector2 screenToHud(com.badlogic.gdx.utils.viewport.Viewport viewport,
                               int logicalWindowHeight, int screenX, int screenY, Vector2 out) {
        float bottomY = logicalWindowHeight - screenY;
        viewportPointToHud(viewport, screenX, bottomY, out);
        return out;
    }
    public List<HudHierarchyEntry> hierarchy() {
        return status == Status.READY ? HudHierarchyProjection.from(document) : List.of();
    }

    private void installInitial(HudDocumentV1 candidate) {
        HudValidationResult structuralValidation = new HudDocumentValidator().validate(candidate);
        requireValid(structuralValidation);
        HudAuthoringResources candidateResources = prepareResources(structuralValidation, asset);
        HudValidationResult resourceValidation;
        MaterializedHud candidateProjection = null;
        try {
            resourceValidation = new HudDocumentValidator().validate(candidate, candidateResources);
            requireValid(resourceValidation);
            candidateProjection = materialize(resourceValidation, candidateResources);
            revealTextraLabels(resourceValidation, candidateProjection);
        } catch (RuntimeException failure) {
            disposeFailedCandidate(candidateProjection, candidateResources, failure);
            throw failure;
        }
        try {
            ensureContext();
            authoringSession.prepare(candidateProjection.root());
            authoringSession.replace(null, candidateProjection.root());
        } catch (RuntimeException failure) {
            disposeFailedCandidate(candidateProjection, candidateResources, failure);
            throw failure;
        }
        currentResources = candidateResources;
        materialized = candidateProjection;
        validation = resourceValidation;
        document = candidate;
        resourcesStale = false;
    }

    /** Called before the edit session publishes its candidate/history cursor. */
    private void installCandidate(HudScreenAsset candidateAsset, HudDocumentV1 candidate,
                                  HudValidationResult structuralValidation) {
        if (editorDocument == null) throw new IllegalStateException("HUD preview is not active.");
        cancelTransformGesture();
        cancelCellContentGesture();
        HudAuthoringResources candidateResources = null;
        MaterializedHud candidateProjection = null;
        HudValidationResult resourceValidation;
        try {
            candidateResources = prepareResources(structuralValidation, candidateAsset);
            resourceValidation = new HudDocumentValidator().validate(
                    candidate, candidateResources);
            requireValid(resourceValidation);
            candidateProjection = materialize(resourceValidation, candidateResources);
            revealTextraLabels(resourceValidation, candidateProjection);
            ensureContext();
            authoringSession.prepare(candidateProjection.root());
        } catch (RuntimeException failure) {
            disposeFailedCandidate(candidateProjection, candidateResources, failure);
            throw failure;
        }

        HudAuthoringResources previousResources = currentResources;
        Actor previousRoot = materialized != null ? materialized.root() : null;
        try {
            authoringSession.replace(previousRoot, candidateProjection.root());
        } catch (RuntimeException failure) {
            disposeFailedCandidate(candidateProjection, candidateResources, failure);
            throw failure;
        }
        MaterializedHud previousProjection = materialized;
        currentResources = candidateResources;
        asset = candidateAsset;
        resourcesStale = false;
        materialized = candidateProjection;
        validation = resourceValidation;
        document = candidate;
        status = Status.READY;
        errorMessage = null;
        selectedCellRange = selectedCellId == null ? List.of() : List.of(selectedCellId);
        cellRangeEndId = selectedCellId;
        resolveSelection();
        rebuildSelectionOverlay();

        // Publication is complete. Cleanup can be reported, but can no longer roll it back.
        disposeReplaced(previousProjection, previousResources);
        notifyListeners();
    }

    private void resolveSelection() {
        if (selectedNodeId != null && (validation == null || !validation.isValid()
                || validation.validatedDocument().node(selectedNodeId) == null)) {
            selectedNodeId = null;
            if (editorDocument != null) editorDocument.setSelectedNodeId(null);
        }
        if (selectedCellId != null && (materialized == null || materialized.cell(selectedCellId) == null)) {
            selectedCellId = null;
            selectedCellRange = List.of();
            cellRangeEndId = null;
            if (editorDocument != null) editorDocument.setSelectedCellId(null);
        }
        if (selectedCellId != null && selectedCellRange.stream().anyMatch(id -> materialized.cell(id) == null))
            selectedCellRange = List.of(selectedCellId);
        if (selectedCellRange.size() <= 1) cellRangeEndId = selectedCellId;
    }

    private void rebuildSelectionOverlay() {
        if (authoringSession == null) return;
        if (status != Status.READY || document == null || materialized == null) {
            authoringSession.setSelectionTargets(List.of());
            authoringSession.clearTransformGizmo();
            transformCursor.clear();
            return;
        }
        authoringSession.setSelectionTargets(HudSelectionOverlayProjection.from(
                document, materialized.actorById(), materialized.cellById(), selectedNodeId, selectedCellId,
                authoringSession.selectionOverlayActor()));
        List<Rectangle> rangeBounds = new ArrayList<>();
        if (selectedCellRange.size() > 1) for (String cellId : selectedCellRange) {
            Cell<?> cell = materialized.cell(cellId);
            if (cell == null) continue;
            Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(cell,
                    authoringSession.selectionOverlayActor(), new Rectangle());
            if (bounds.width > 0f && bounds.height > 0f) rangeBounds.add(bounds);
        }
        authoringSession.showCellRange(rangeBounds);
        refreshTransformGizmo();
    }

    private void refreshTransformGizmo() {
        if (authoringSession == null || activeTransform != null) return;
        TransformContext context = selectedFreeTransformContext();
        if (context == null) {
            authoringSession.clearTransformGizmo();
            transformCursor.clear();
        }
        else authoringSession.showTransformGizmo(context.overlayBounds);
    }

    private TransformContext selectedFreeTransformContext() {
        if (status != Status.READY || document == null || materialized == null || authoringSession == null
                || selectedNodeId == null) return null;
        HudFreeTransformAuthoring.FreeNode freeNode = HudFreeTransformAuthoring.find(document, selectedNodeId);
        Actor actor = materialized.actor(selectedNodeId);
        Actor parent = freeNode != null ? materialized.actor(freeNode.parentId()) : null;
        HudSelectionTarget target = authoringSession.selectionTargets().stream()
                .filter(HudSelectionTarget::selected).findFirst().orElse(null);
        if (freeNode == null || actor == null || parent == null || target == null) return null;
        // A Dialog's authored FREE placement belongs to its stable slot. Native show/hide
        // may reparent the Dialog itself, so editor transforms must move the slot instead.
        if (freeNode.child().node.kind == HudNodeKind.DIALOG) {
            actor = actor.getParent();
            if (actor == null || actor.getParent() != parent) return null;
        }
        HudTransformGeometry.Bounds overlayBounds = new HudTransformGeometry.Bounds(
                target.x(), target.y(), target.width(), target.height());
        HudTransformGeometry.Bounds localBounds = new HudTransformGeometry.Bounds(
                actor.getX(), actor.getY(), actor.getWidth(), actor.getHeight());
        return new TransformContext(selectedNodeId, actor, HudFreeTransform.snapshot(freeNode.child().free,
                freeNode.child().node.actor.width, freeNode.child().node.actor.height,
                parent.getWidth(), parent.getHeight(), localBounds), overlayBounds);
    }

    private HudTransformGeometry.Bounds overlayToParentBounds(TransformContext context,
                                                               HudTransformGeometry.Bounds overlayBounds) {
        Actor parent = context.actor.getParent();
        if (parent == null || authoringSession == null) return context.snapshot.visualBounds();
        Vector2 lower = authoringSession.selectionOverlayActor().localToStageCoordinates(
                new Vector2(overlayBounds.x(), overlayBounds.y()));
        Vector2 upper = authoringSession.selectionOverlayActor().localToStageCoordinates(
                new Vector2(overlayBounds.right(), overlayBounds.top()));
        parent.stageToLocalCoordinates(lower);
        parent.stageToLocalCoordinates(upper);
        return new HudTransformGeometry.Bounds(lower.x, lower.y, upper.x - lower.x, upper.y - lower.y);
    }

    /** Resolves the sole current target through the same viewport and overlay hit-test for click and hover. */
    private HudSelectionTarget findOverlayTargetAt(float studioStageX, float studioStageY) {
        if (status != Status.READY || authoringSession == null || materialized == null
                || previewX == Integer.MIN_VALUE) return null;
        var viewport = authoringSession.viewport();
        if (studioStageX < viewport.getScreenX()
                || studioStageX >= viewport.getScreenX() + viewport.getScreenWidth()
                || studioStageY < viewport.getScreenY()
                || studioStageY >= viewport.getScreenY() + viewport.getScreenHeight()) return null;
        Vector2 hudPoint = studioToHud(studioStageX, studioStageY, new Vector2());
        return hudPoint == null ? null : authoringSession.selectionTargetAt(hudPoint.x, hudPoint.y);
    }

    private Vector2 studioToHud(float studioStageX, float studioStageY, Vector2 out) {
        if (authoringSession == null || previewX == Integer.MIN_VALUE) return null;
        var viewport = authoringSession.viewport();
        if (viewport.getScreenWidth() <= 0 || viewport.getScreenHeight() <= 0) return null;
        viewportPointToHud(viewport, studioStageX, studioStageY, out);
        return out;
    }

    /** Resolves a logical Studio-stage point inside the HUD canvas. */
    public boolean hudPointAt(float studioStageX, float studioStageY, Vector2 out) {
        if (out == null || authoringSession == null || previewX == Integer.MIN_VALUE) return false;
        var viewport = authoringSession.viewport();
        if (studioStageX < viewport.getScreenX()
                || studioStageX >= viewport.getScreenX() + viewport.getScreenWidth()
                || studioStageY < viewport.getScreenY()
                || studioStageY >= viewport.getScreenY() + viewport.getScreenHeight()) return false;
        return studioToHud(studioStageX, studioStageY, out) != null;
    }

    public OrthographicCamera hudCamera() {
        return authoringSession == null ? null : (OrthographicCamera) authoringSession.viewport().getCamera();
    }

    public com.badlogic.gdx.utils.viewport.Viewport hudViewport() {
        return authoringSession == null ? null : authoringSession.viewport();
    }

    private boolean insideHudSurface(Vector2 point) {
        return point.x >= 0f && point.x <= authoringSession.surfaceWidth()
                && point.y >= 0f && point.y <= authoringSession.surfaceHeight();
    }

    private static void viewportPointToHud(com.badlogic.gdx.utils.viewport.Viewport viewport,
                                           float viewportX, float viewportY, Vector2 out) {
        out.x = viewportX - viewport.getScreenX();
        out.y = viewportY - viewport.getScreenY();
    }

    private float transformDragThreshold() {
        return authoringSession == null ? TRANSFORM_DRAG_THRESHOLD_LOGICAL_PIXELS
                : authoringSession.hudUnitsForLogicalPixels(TRANSFORM_DRAG_THRESHOLD_LOGICAL_PIXELS);
    }

    private void restoreTransformPreview(TransformContext context) {
        if (authoringSession == null) return;
        authoringSession.restoreAuthoredActorBounds(context.actor, context.overlayBounds);
        authoringSession.restoreTransformPreview();
    }

    private record TransformContext(String nodeId, Actor actor, HudFreeTransform.Snapshot snapshot,
                                    HudTransformGeometry.Bounds overlayBounds) {}
    private record PendingMoveGesture(HudScreenEditorDocument editorDocument,
                                      HudDocumentV1 document,
                                      TransformContext context,
                                      float startHudX, float startHudY) {}
    private record DeletionSelection(String deletedNodeId, String parentNodeId) {}
    private record CreationSelection(String createdNodeId, String previousNodeId) {}
    private record CellMoveSelection(String beforeNodeId, String widgetId) {}
    private record StructureSelection(String beforeCellId, List<String> beforeRange, String beforeEndId,
                                      String afterCellId) {}
    private static final class CellContentGesture {
        final HudScreenEditorDocument editorDocument;
        final HudDocumentEditSession editSession;
        final long revision;
        final String tableId, sourceCellId, widgetId, previousSelectionId;
        String destinationCellId;
        boolean dragging;
        CellContentGesture(HudScreenEditorDocument editorDocument, HudDocumentEditSession editSession,
                           long revision, String tableId, String sourceCellId, String widgetId,
                           String previousSelectionId) {
            this.editorDocument = editorDocument;
            this.editSession = editSession;
            this.revision = revision;
            this.tableId = tableId;
            this.sourceCellId = sourceCellId;
            this.widgetId = widgetId;
            this.previousSelectionId = previousSelectionId;
        }
    }
    private record DropGeometry(float localX, float localY, float localWidth, float localHeight) {}
    private static final class TransformGesture {
        private final TransformContext context;
        private final HudTransformHandle handle;
        private final float startX;
        private final float startY;
        private HudTransformGeometry.Bounds currentOverlayBounds;
        private boolean dragging;

        private TransformGesture(TransformContext context, HudTransformHandle handle,
                                 float startX, float startY) {
            this.context = context;
            this.handle = handle;
            this.startX = startX;
            this.startY = startY;
            currentOverlayBounds = context.overlayBounds;
        }
        private boolean changesWidth() { return handle != null && handle.changesWidth(); }
        private boolean changesHeight() { return handle != null && handle.changesHeight(); }
    }

    private static void requireValid(HudValidationResult result) {
        if (!result.isValid()) throw new HudEditRejectedException(validationMessage(result));
    }
    private static void disposeFailedCandidate(Disposable disposable, RuntimeException failure) {
        if (disposable == null) return;
        try {
            disposable.dispose();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
    private static void disposeFailedCandidate(Disposable first, Disposable second,
                                               RuntimeException failure) {
        disposeFailedCandidate(first, failure);
        disposeFailedCandidate(second, failure);
    }
    private HudAuthoringResources prepareResources(HudValidationResult validation,
                                                   HudScreenAsset resourceAsset) {
        return HudAuthoringResources.prepare(validation.validatedDocument(), projectDir,
                assetDatabase.get(), resourceAsset.skinId);
    }
    private static MaterializedHud materialize(HudValidationResult validation, HudAuthoringResources resources) {
        return new HudMaterializer().materialize(validation.validatedDocument(), resources, false);
    }
    private void ensureContext() {
        if (authoringSession == null) {
            authoringSession = new HudAuthoringSession(batch.get(), drawer.get());
            authoringSession.setShowLayoutBounds(showLayoutBounds);
        }
    }
    private static void disposeReplaced(MaterializedHud projection,
                                        HudAuthoringResources resources) {
        try {
            if (projection != null) projection.dispose();
        } catch (RuntimeException cleanupFailure) {
            logCleanupFailure("Failed to dispose replaced HUD session.", cleanupFailure);
        }
        try {
            if (resources != null) resources.dispose();
        } catch (RuntimeException cleanupFailure) {
            logCleanupFailure("Failed to dispose replaced HUD resources.", cleanupFailure);
        }
    }
    private static void logCleanupFailure(String message, RuntimeException failure) {
        if (Gdx.app != null) Gdx.app.error("HudEditorSession", message, failure);
    }
    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) { logCleanupFailure("HUD projection listener failed.", failure); }
        }
    }
    private void closePreview() {
        if (authoringSession != null) authoringSession.clear();
        try {
            if (materialized != null) materialized.dispose();
        } catch (RuntimeException cleanupFailure) {
            logCleanupFailure("Failed to dispose closed HUD projection.", cleanupFailure);
        }
        materialized = null;
        try {
            if (currentResources != null) currentResources.dispose();
        } catch (RuntimeException cleanupFailure) {
            logCleanupFailure("Failed to dispose closed HUD resources.", cleanupFailure);
        }
        currentResources = null;
        resourcesStale = false;
        previewX = Integer.MIN_VALUE;
    }

    private static void revealTextraLabels(HudValidationResult validation,
                                           MaterializedHud projection) {
        for (HudNode node : validation.validatedDocument().nodeIndex().values()) {
            if (node.textraLabel == null) continue;
            Actor actor = projection.actor(node.id);
            if (actor instanceof TypingLabel label) label.skipToTheEnd(true, false);
        }
    }
    private static String validationMessage(HudValidationResult result) {
        StringBuilder out = new StringBuilder("HUD document validation failed:");
        result.issues().forEach(issue -> out.append("\n").append(issue.code())
                .append(" at ").append(issue.path()).append(": ").append(issue.message()));
        return out.toString();
    }
    private static String message(Throwable failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
    }
    @Override public void dispose() { close(); if (authoringSession != null) authoringSession.dispose(); authoringSession = null; }
}
