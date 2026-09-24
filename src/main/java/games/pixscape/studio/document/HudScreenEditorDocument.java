package games.pixscape.studio.document;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.service.hud.HudDocumentEditSession;
import games.pixscape.studio.service.hud.HudEditorSession;

/** Authoritative per-tab HUD authoring/history state, lightweight when its preview is detached. */
public final class HudScreenEditorDocument extends OpenEditorDocument implements AutoCloseable {
    private final HudDocumentEditSession editSession;
    private HudEditorSession.Status loadingStatus = HudEditorSession.Status.CLOSED;
    private String selectedNodeId;
    private String selectedCellId;

    public HudScreenEditorDocument(String screenId, String title) {
        this(screenId, title, defaultAsset(screenId), defaultDocument());
    }

    public HudScreenEditorDocument(String screenId, String title,
                                   HudScreenAsset asset, HudDocumentV1 document) {
        super(new EditorDocumentKey(EditorDocumentType.HUD_SCREEN,
                HudScreenAssetId.normalize(screenId)), title);
        editSession = new HudDocumentEditSession(asset, document);
        editSession.addListener(this::resolveSelection);
    }

    public String screenId() { return key().domainId(); }
    public HudScreenAsset asset() { return editSession.asset(); }
    public HudDocumentV1 document() { return editSession.document(); }
    public HudDocumentEditSession editSession() { return editSession; }
    public HudEditorSession.Status loadingStatus() { return loadingStatus; }
    public String selectedNodeId() { return selectedNodeId; }
    public String selectedCellId() { return selectedCellId; }
    public void capture(HudEditorSession session) {
        if (session == null || !screenId().equals(session.screenId())) return;
        loadingStatus = session.status();
        selectedNodeId = session.selectedNodeId();
        selectedCellId = session.selectedCellId();
    }

    public void setSelectedNodeId(String selectedNodeId) {
        this.selectedNodeId = selectedNodeId;
        if (selectedNodeId != null) selectedCellId = null;
        resolveSelection();
    }

    public void setSelectedCellId(String selectedCellId) {
        this.selectedCellId = selectedCellId;
        if (selectedCellId != null) selectedNodeId = null;
    }


    private void resolveSelection() {
        HudDocumentV1 current = editSession.document();
        if (selectedNodeId == null || current == null) {
            selectedNodeId = null;
            return;
        }
        var validation = new HudDocumentValidator().validate(current);
        if (!validation.isValid() || validation.validatedDocument().node(selectedNodeId) == null) {
            selectedNodeId = null;
        }
    }

    @Override public boolean isDirty() { return editSession.isDirty(); }

    @Override public boolean closeable() { return true; }
    @Override public void close() { editSession.close(); }

    private static HudScreenAsset defaultAsset(String screenId) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = HudScreenAssetId.DIRECTORY + "/"
                + HudScreenAssetId.assetName(HudScreenAssetId.normalize(screenId)) + ".json";
        return asset;
    }

    private static HudDocumentV1 defaultDocument() {
        return new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));
    }
}
