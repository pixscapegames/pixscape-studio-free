package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.HudScreenAssetLoader;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.io.AtomicTextPublication;
import games.pixscape.studio.io.StudioIO;

/** Creates one valid current-format HUD screen and its structural root document. */
public final class HudScreenAssetAuthoringService {
    private final HudScreenAssetLoader loader = new HudScreenAssetLoader();
    private final HudDocumentCodec documentCodec = new HudDocumentCodec();

    public String create(FileHandle projectDir, String name,
                         int referenceWidth, int referenceHeight) {
        if (projectDir == null) throw new IllegalArgumentException("Project directory is required.");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("HUD screen name cannot be empty.");
        }
        if (referenceWidth <= 0 || referenceHeight <= 0) {
            throw new IllegalArgumentException("HUD reference width and height must be positive.");
        }

        String screenId = HudScreenAssetId.normalize(name);
        String assetName = HudScreenAssetId.assetName(screenId);
        if (assetName.endsWith(HudScreenAsset.EXTENSION)) {
            throw new IllegalArgumentException("HUD screen name must not include the .hudscreen extension.");
        }
        FileHandle target = projectDir.child(HudScreenAssetId.DIRECTORY)
                .child(assetName + HudScreenAsset.EXTENSION);
        String documentId = HudScreenAssetId.DIRECTORY + "/" + assetName + ".json";
        FileHandle documentTarget = projectDir.child(documentId);
        if (target.exists() || documentTarget.exists()) {
            throw new IllegalArgumentException("A HUD screen named '" + assetName + "' already exists.");
        }

        HudScreenAsset asset = new HudScreenAsset();
        asset.referenceWidth = referenceWidth;
        asset.referenceHeight = referenceHeight;
        asset.documentId = documentId;
        asset.validate();
        HudDocumentV1 document = new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));

        try {
            AtomicTextPublication.publish(java.util.List.of(
                    new AtomicTextPublication.Entry(documentTarget, documentCodec.write(document)),
                    new AtomicTextPublication.Entry(target, serialize(asset))), StudioIO::writeUtf8Atomic);
            loader.load(projectDir, screenId);
            return screenId;
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Unable to create HUD screen '" + assetName
                    + "': " + failure.getMessage(), failure);
        }
    }

    private static String serialize(HudScreenAsset asset) {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        json.setTypeName(null);
        return json.prettyPrint(asset);
    }
}
