package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit ownership of both private generation directories; never owns a live output. */
final class PreparedSceneHudEnvironment implements AutoCloseable {
    final String projectRoot;
    final long projectEpoch;
    final String sceneTag;
    final long generation;
    final String stamp;
    final String physicalKey;
    final FileHandle workspace;
    final FileHandle atlasCandidate;
    final SceneHudAtlasBuilder.Result result;
    private final AtomicBoolean closed = new AtomicBoolean();

    PreparedSceneHudEnvironment(String root, long epoch, String sceneTag, long generation, String stamp,
                                String physicalKey,
                                FileHandle workspace, FileHandle atlasCandidate, SceneHudAtlasBuilder.Result result) {
        this.projectRoot = root;
        this.projectEpoch = epoch;
        this.sceneTag = sceneTag;
        this.generation = generation;
        this.stamp = stamp;
        this.physicalKey = physicalKey;
        this.workspace = workspace;
        this.atlasCandidate = atlasCandidate;
        this.result = result;
    }

    boolean empty() { return atlasCandidate == null; }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        try { if (atlasCandidate != null) AtomicDirectoryPublication.discardCandidate(atlasCandidate); }
        catch (RuntimeException cleanup) { failure = cleanup; }
        try { if (workspace != null) AtomicDirectoryPublication.discardCandidate(workspace); }
        catch (RuntimeException cleanup) {
            if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
        }
        if (failure != null) throw failure;
    }
}
