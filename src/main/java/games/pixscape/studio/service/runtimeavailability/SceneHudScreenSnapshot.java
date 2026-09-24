package games.pixscape.studio.service.runtimeavailability;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.OpenEditorDocument;

import java.util.ArrayList;
import java.util.List;

/** Immutable Studio-thread snapshot used only by Scene-local HUD preparation workers. */
public record SceneHudScreenSnapshot(String screenId, HudScreenAsset asset, HudDocumentV1 document) {
    public SceneHudScreenSnapshot {
        screenId = HudScreenAssetId.normalize(screenId);
        asset = copyAsset(asset);
        document = copyDocument(document);
    }

    @Override public HudScreenAsset asset() { return copyAsset(asset); }
    @Override public HudDocumentV1 document() { return copyDocument(document); }

    public static List<SceneHudScreenSnapshot> snapshotOpen(List<OpenEditorDocument> documents) {
        List<SceneHudScreenSnapshot> result = new ArrayList<>();
        if (documents != null) for (OpenEditorDocument open : documents) {
            if (open instanceof HudScreenEditorDocument hud) {
                result.add(new SceneHudScreenSnapshot(hud.screenId(), hud.asset(), hud.document()));
            }
        }
        return List.copyOf(result);
    }

    private static HudScreenAsset copyAsset(HudScreenAsset source) {
        if (source == null) return null;
        HudScreenAsset copy = new HudScreenAsset();
        copy.schemaVersion = source.schemaVersion;
        copy.referenceWidth = source.referenceWidth;
        copy.referenceHeight = source.referenceHeight;
        copy.documentId = source.documentId;
        copy.skinId = source.skinId;
        copy.atlasId = source.atlasId;
        copy.textureProfileId = source.textureProfileId;
        return copy;
    }

    private static HudDocumentV1 copyDocument(HudDocumentV1 source) {
        return source == null ? null : new HudDocumentCodec().read(new HudDocumentCodec().write(source));
    }
}
