package games.pixscape.studio.document;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.gameobject.GameObjectAssetId;
import games.pixscape.studio.scene.SceneEditorContext;

import java.util.Objects;

/**
 * An isolated, in-memory editing context for one Game Object asset.
 *
 * <p>The context is never a project Scene. Its only persisted output is the asset file when
 * the document is explicitly saved.</p>
 */
public final class GameObjectEditorDocument extends OpenEditorDocument implements AutoCloseable {
    private final SceneEditorContext context;
    private final FileHandle assetFile;
    private final int rootEntityId;
    private boolean closed;

    public GameObjectEditorDocument(String assetId, String title, FileHandle assetFile,
                                    SceneEditorContext context, int rootEntityId) {
        super(new EditorDocumentKey(EditorDocumentType.GAME_OBJECT,
                GameObjectAssetId.normalize(assetId)), title);
        this.assetFile = Objects.requireNonNull(assetFile, "assetFile");
        this.context = Objects.requireNonNull(context, "context");
        if (rootEntityId < 0) throw new IllegalArgumentException("Game Object root entity is required.");
        this.rootEntityId = rootEntityId;
    }

    public String assetId() { return key().domainId(); }
    public FileHandle assetFile() { return assetFile; }
    public SceneEditorContext context() { return context; }
    public int rootEntityId() { return rootEntityId; }
    public boolean isClosed() { return closed; }

    @Override public boolean isDirty() { return !closed && context.isDirty(); }
    @Override public boolean closeable() { return true; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        context.dispose();
    }
}
