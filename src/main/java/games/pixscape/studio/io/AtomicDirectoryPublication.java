package games.pixscape.studio.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Directory candidate/backup lifecycle extracted from HUD profile atlas preparation.
 * Callers own the contents and serialize publications to the same target. */
public final class AtomicDirectoryPublication {
    private AtomicDirectoryPublication() {}

    @FunctionalInterface
    public interface Publisher {
        void publish(FileHandle candidate, FileHandle target);
    }

    @FunctionalInterface
    interface EmptyCandidateParentDeleter {
        void delete(Path parent) throws IOException;
    }

    public static FileHandle createCandidate(FileHandle target) {
        Path live = directoryPath(target);
        Path temp = live.getParent().resolve(".tmp");
        rejectSymlinks(temp);
        try {
            Files.createDirectories(temp);
            return new FileHandle(Files.createDirectory(temp.resolve(live.getFileName() + "-" + UUID.randomUUID())).toFile());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot create directory candidate for " + target, failure);
        }
    }

    public static Published publish(FileHandle candidate, FileHandle target) {
        return publish(candidate, target, AtomicDirectoryPublication::move);
    }

    /** Serialized by the same caller as publication. Move-away is strict; obsolete cleanup is optional. */
    public static void retire(FileHandle target) {
        retire(target, AtomicDirectoryPublication::move);
    }

    static void retire(FileHandle target, Publisher mover) {
        Path live = directoryPath(target);
        if (!Files.exists(live)) return;
        Path retired = live.resolveSibling(".retired-" + live.getFileName() + "-" + UUID.randomUUID());
        mover.publish(target, new FileHandle(retired.toFile()));
        try { deleteTree(retired); }
        catch (RuntimeException failure) {
            if (Gdx.app != null) Gdx.app.error("AtomicDirectoryPublication", "Retired directory cleanup failed", failure);
        }
    }

    public static Published publish(FileHandle candidate, FileHandle target, Publisher publisher) {
        Path live = directoryPath(target);
        Path staged = directoryPath(candidate);
        if (!staged.getParent().equals(live.getParent().resolve(".tmp")) || !Files.isDirectory(staged))
            throw new IllegalArgumentException("Candidate must be a directory in the target parent's .tmp directory.");
        Path backup = live.resolveSibling(".backup-" + live.getFileName() + "-" + UUID.randomUUID());
        boolean movedLive = false;
        boolean attempted = false;
        try {
            Files.createDirectories(live.getParent());
            if (Files.exists(live)) {
                move(target, new FileHandle(backup.toFile()));
                movedLive = true;
            }
            attempted = true;
            publisher.publish(candidate, target);
            if (!Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Directory publisher produced no target directory.");
            return new Published(live, movedLive ? backup : null);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                if (attempted) deleteTree(live);
                if (movedLive) move(new FileHandle(backup.toFile()), target);
            } catch (RuntimeException | Error rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw new IllegalStateException("Directory publication failed: " + target, failure);
        }
    }

    /** Backup survives until the caller has accepted the new contents. Closing without commit rolls back. */
    public static final class Published implements AutoCloseable {
        private final Path live;
        private final Path backup;
        private boolean finished;
        private Published(Path live, Path backup) { this.live = live; this.backup = backup; }
        public void commit() {
            finished = true;
            // Publication is accepted even if obsolete backup cleanup fails (existing HUD policy).
            try { if (backup != null) deleteTree(backup); }
            catch (RuntimeException failure) {
                if (Gdx.app != null) Gdx.app.error("AtomicDirectoryPublication", "Directory backup cleanup failed", failure);
            }
        }
        @Override public void close() {
            if (finished) return;
            deleteTree(live);
            if (backup != null) move(new FileHandle(backup.toFile()), new FileHandle(live.toFile()));
            finished = true;
        }
    }

    public static void discardCandidate(FileHandle candidate) {
        discardCandidate(candidate, parent -> Files.deleteIfExists(parent));
    }

    /** Package-private seam keeps the optional container cleanup deterministic in filesystem tests. */
    static void discardCandidate(FileHandle candidate, EmptyCandidateParentDeleter parentDeleter) {
        Path path = candidate.file().toPath().toAbsolutePath().normalize();
        if (path.getParent() == null || !path.getParent().getFileName().toString().equals(".tmp"))
            throw new IllegalArgumentException("Not a directory candidate: " + candidate);
        rejectSymlinks(path.getParent());
        // This is the required cleanup: failure must remain visible to the caller.
        deleteTree(path);
        try {
            // Nonrecursive: a concurrent candidate must never be removed.
            parentDeleter.delete(path.getParent());
        } catch (java.nio.file.DirectoryNotEmptyException ignored) {
        } catch (IOException failure) {
            // The candidate is already gone. A locked empty container cannot invalidate a live publication.
            if (Gdx.app != null) Gdx.app.error("AtomicDirectoryPublication",
                    "Optional candidate parent cleanup failed", failure);
        }
    }

    public static void move(FileHandle source, FileHandle target) {
        try {
            try { Files.move(source.file().toPath(), target.file().toPath(), StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(source.file().toPath(), target.file().toPath()); }
        } catch (IOException failure) { throw new IllegalStateException("Directory move failed: " + source + " -> " + target, failure); }
    }

    /** Generated-directory operations reject symbolic-link ancestors rather than following them. */
    public static void rejectSymlinks(Path path) {
        for (Path part = path.toAbsolutePath().normalize(); part != null; part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("Generated directory crosses a symbolic link: " + part);
    }

    private static Path directoryPath(FileHandle handle) {
        if (handle == null) throw new IllegalArgumentException("Directory is required.");
        Path path = handle.file().toPath().toAbsolutePath().normalize();
        if (path.getParent() == null) throw new IllegalArgumentException("Filesystem root is not a generated directory.");
        if (path.getFileName().toString().equals(".tmp")) throw new IllegalArgumentException("Candidate container cannot be a publication target.");
        rejectSymlinks(path);
        if (Files.exists(path) && !Files.isDirectory(path)) throw new IllegalArgumentException("Not a directory: " + path);
        return path;
    }

    private static void deleteTree(Path path) {
        // Walk never follows links, including links inside previously generated output.
        try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.delete(dir); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) { throw new IllegalStateException("Cannot discard generated directory: " + path, failure); }
    }
}
