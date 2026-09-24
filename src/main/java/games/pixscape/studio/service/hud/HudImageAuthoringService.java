package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.atlas.HudImageAssetRef;
import games.pixscape.studio.ui.asset.AssetNode;
import games.pixscape.runtime.hud.document.HudFreePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Performs immediate Image authoring; Runtime preparation is observed separately. */
public final class HudImageAuthoringService implements AutoCloseable {
    private final Supplier<FileHandle> projectDir;
    private final Supplier<AssetMetaDatabase> assetDatabase;
    private final EditorDocumentManager documentManager;
    private final HudEditorSession preview;
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean closed;

    public HudImageAuthoringService(Supplier<FileHandle> projectDir, Supplier<AssetMetaDatabase> assetDatabase,
                                    EditorDocumentManager documentManager, HudEditorSession preview) {
        this.projectDir = Objects.requireNonNull(projectDir, "projectDir");
        this.assetDatabase = Objects.requireNonNull(assetDatabase, "assetDatabase");
        this.documentManager = Objects.requireNonNull(documentManager, "documentManager");
        this.preview = Objects.requireNonNull(preview, "preview");
    }

    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener, "listener")); }

    public boolean canAdd(AssetNode selectedAsset) {
        if (!canAddContext(preview.selectedNodeId())) return false;
        try {
            return HudImageAssetRef.fromSelected(
                    selectedAsset, assetDatabase.get(), projectDir.get()) != null;
        } catch (RuntimeException invalid) { return false; }
    }

    public boolean canAdd(int assetId, String parentId) {
        if (!canAddContext(parentId)) return false;
        try {
            return HudImageAssetRef.fromAssetId(assetId, assetDatabase.get(), projectDir.get()) != null;
        } catch (RuntimeException invalid) { return false; }
    }

    public boolean canAssignImageButton(int assetId, String nodeId) {
        if (closed || nodeId == null) return false;
        HudScreenEditorDocument active = activeDocument();
        if (active == null || active.document() == null || !preview.projects(active)) return false;
        var node = HudLayoutAuthoring.node(active.document(), nodeId);
        if (node == null || (node.imageButton == null && node.imageTextButton == null)) return false;
        try {
            return HudImageAssetRef.fromAssetId(assetId, assetDatabase.get(), projectDir.get()) != null;
        } catch (RuntimeException invalid) { return false; }
    }

    private boolean canAddContext(String parentId) {
        if (closed) return false;
        HudScreenEditorDocument active = activeDocument();
        return active != null && active.document() != null && preview.projects(active)
                && HudLayoutAuthoring.canAddChild(active.document(), parentId);
    }

    /** Publishes the authored Image now; the document-publication observer prepares Runtime later. */
    public String add(AssetNode selectedAsset) {
        if (!canAdd(selectedAsset)) return null;
        return add(selectedAsset != null ? selectedAsset.assetId : -1,
                preview.selectedNodeId(), null, activeDocument());
    }

    /** DnD entry point; capturedDocument prevents a release from crossing an activation change. */
    public String add(int assetId, String parentId, HudFreePlacement placement,
                      HudScreenEditorDocument capturedDocument) {
        return add(assetId, new HudEditorSession.ImageDropTarget(parentId, placement), capturedDocument);
    }

    /** DnD entry point carrying the resolved parent-local placement and supported source size. */
    public String add(int assetId, HudEditorSession.ImageDropTarget dropTarget,
                      HudScreenEditorDocument capturedDocument) {
        if (dropTarget == null) return null;
        String parentId = dropTarget.parentId();
        if (activeDocument() != capturedDocument || !canAdd(assetId, parentId)) return null;
        HudScreenEditorDocument target = activeDocument();
        HudImageAssetRef image = HudImageAssetRef.fromAssetId(assetId, assetDatabase.get(), projectDir.get());
        String imageId = HudLayoutAuthoring.nextImageId(target.document());
        String previousSelection = target.selectedNodeId();
        target.editSession().edit("Add Image", candidate -> {
            if (!imageId.equals(HudLayoutAuthoring.addImage(
                    candidate, parentId, imageId, image.resourceName(), dropTarget.placement(),
                    dropTarget.imageWidth(), dropTarget.imageHeight())))
                throw new HudEditRejectedException("HUD Image was not added to its captured parent.");
            return candidate;
        });
        if (preview.projects(target)) {
            preview.rememberCreatedNode(target.editSession().currentRevision(), imageId, previousSelection);
        }
        target.setSelectedNodeId(imageId);
        if (preview.projects(target)) preview.selectNode(imageId);
        changed();
        return imageId;
    }

    /** Applies an Image drag to the native ImageButton imageUp state without changing selection. */
    public boolean assignImageButton(int assetId, String nodeId, HudScreenEditorDocument capturedDocument) {
        if (activeDocument() != capturedDocument || !canAssignImageButton(assetId, nodeId)) return false;
        HudImageAssetRef image = HudImageAssetRef.fromAssetId(assetId, assetDatabase.get(), projectDir.get());
        var node = HudLayoutAuthoring.node(capturedDocument.document(), nodeId);
        boolean changed = node != null && node.imageTextButton != null
                ? preview.editImageTextButtonImage(nodeId, HudEditorSession.ImageButtonImageSlot.UP,
                        image.resourceName())
                : preview.editImageButtonImage(nodeId, HudEditorSession.ImageButtonImageSlot.UP,
                        image.resourceName());
        if (!changed) return false;
        changed();
        return true;
    }

    private void changed() {
        for (Runnable listener : List.copyOf(listeners)) {
            try { listener.run(); }
            catch (RuntimeException failure) {
                if (com.badlogic.gdx.Gdx.app != null)
                    com.badlogic.gdx.Gdx.app.error("HudImageAuthoring", "HUD authoring status observer failed", failure);
            }
        }
    }

    private HudScreenEditorDocument activeDocument() {
        return documentManager.activeDocument() instanceof HudScreenEditorDocument hud ? hud : null;
    }

    @Override public void close() {
        closed = true;
        listeners.clear();
    }
}
