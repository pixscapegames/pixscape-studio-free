package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.HudScreenAssetLoader;
import games.pixscape.runtime.hud.document.HudDocumentCodec;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.studio.io.StudioIO;
import games.pixscape.studio.io.AtomicTextPublication;
import games.pixscape.studio.document.HudScreenEditorDocument;

import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

/** Loads and atomically persists the authored state of one HUD editor document. */
public final class HudDocumentPersistenceService {
    @FunctionalInterface
    interface AtomicWriter { void write(FileHandle target, String content); }

    public record Loaded(HudScreenAsset asset, HudDocumentV1 document) {}

    private final HudScreenAssetLoader assetLoader;
    private final HudDocumentCodec codec;
    private final AtomicWriter writer;
    /** Shared by normal save and generated-metadata publication, even across service instances. */
    private static final Object PUBLICATION_LOCK = new Object();

    public HudDocumentPersistenceService() {
        this(new HudScreenAssetLoader(), new HudDocumentCodec(), StudioIO::writeUtf8Atomic);
    }

    HudDocumentPersistenceService(HudScreenAssetLoader assetLoader,
                                  HudDocumentCodec codec,
                                  AtomicWriter writer) {
        this.assetLoader = Objects.requireNonNull(assetLoader, "assetLoader");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public Loaded load(FileHandle projectDir, String screenId) {
        Objects.requireNonNull(projectDir, "projectDir");
        HudScreenAsset asset = assetLoader.load(projectDir, HudScreenAssetId.normalize(screenId));
        return new Loaded(asset, loadDocument(projectDir, asset));
    }

    public HudScreenAsset loadAsset(FileHandle projectDir, String screenId) {
        return assetLoader.load(projectDir, HudScreenAssetId.normalize(screenId));
    }

    public HudDocumentV1 loadDocument(FileHandle projectDir, HudScreenAsset asset) {
        HudDocumentV1 document = codec.read(projectDir.child(asset.documentId));
        HudValidationResult validation = new HudDocumentValidator().validate(document);
        if (!validation.isValid()) throw new HudEditRejectedException(validationMessage(validation));
        return document;
    }

    public void save(FileHandle projectDir, HudDocumentEditSession session) {
        save(projectDir, session, null);
    }

    public void save(FileHandle projectDir, HudScreenEditorDocument document) {
        Objects.requireNonNull(document, "document");
        save(projectDir, document.editSession(), document.screenId());
    }

    private void save(FileHandle projectDir, HudDocumentEditSession session, String screenId) {
        Objects.requireNonNull(projectDir, "projectDir");
        Objects.requireNonNull(session, "session");
        synchronized (PUBLICATION_LOCK) {
            HudDocumentV1 document = session.document();
            HudScreenAsset asset = session.asset();
            HudValidationResult validation = new HudDocumentValidator().validate(document);
            if (!validation.isValid()) throw new HudEditRejectedException(validationMessage(validation));
            List<AtomicTextPublication.Entry> entries = new ArrayList<>();
            entries.add(new AtomicTextPublication.Entry(projectDir.child(asset.documentId), codec.write(document)));
            if (screenId != null) {
                entries.add(new AtomicTextPublication.Entry(assetFile(projectDir, screenId), serializeAsset(asset)));
            }
            AtomicTextPublication.publish(entries, writer::write);
            session.markSaved();
        }
    }

    private static FileHandle assetFile(FileHandle projectDir, String screenId) {
        return projectDir.child(HudScreenAssetId.DIRECTORY)
                .child(HudScreenAssetId.assetName(screenId) + HudScreenAsset.EXTENSION);
    }

    private static String serializeAsset(HudScreenAsset asset) {
        Json json = new Json();
        json.setOutputType(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(false);
        json.setUsePrototypes(false);
        json.setTypeName(null);
        return json.prettyPrint(asset);
    }

    private static String validationMessage(HudValidationResult result) {
        StringBuilder out = new StringBuilder("HUD document validation failed:");
        result.issues().forEach(issue -> out.append("\n").append(issue.code())
                .append(" at ").append(issue.path()).append(": ").append(issue.message()));
        return out.toString();
    }
}
