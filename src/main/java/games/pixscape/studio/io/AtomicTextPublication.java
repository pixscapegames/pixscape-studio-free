package games.pixscape.studio.io;

import com.badlogic.gdx.files.FileHandle;
import java.util.ArrayList;
import java.util.List;

/** Rollback boundary for a logical save comprising several atomic file replacements. */
public final class AtomicTextPublication {
    @FunctionalInterface public interface Writer { void write(FileHandle target, String content); }
    public record Entry(FileHandle target, String content) {}
    private record Previous(FileHandle target, byte[] bytes, FileHandle backup, byte[] backupBytes) {}
    private AtomicTextPublication() {}

    public static void publish(List<Entry> entries, Writer writer) {
        List<Previous> previous = new ArrayList<>();
        for (Entry entry : entries) {
            FileHandle backup = entry.target().parent().child(entry.target().name() + ".bak");
            previous.add(new Previous(entry.target(),
                    entry.target().exists() ? entry.target().readBytes() : null,
                    backup, backup.exists() ? backup.readBytes() : null));
        }
        int attempted = 0;
        try {
            for (Entry entry : entries) {
                attempted++;
                writer.write(entry.target(), entry.content());
            }
        } catch (RuntimeException failure) {
            // Include the failing write, which may throw after replacing its target.
            for (int i = attempted - 1; i >= 0; i--) {
                Previous old = previous.get(i);
                try {
                    if (old.bytes() != null) {
                        StudioIO.writeAtomic(old.target(), out -> out.write(old.bytes()));
                    } else if (old.target().exists() && !old.target().delete()) {
                        throw new IllegalStateException("Unable to roll back new file: " + old.target());
                    }
                    // StudioIO preserves a per-file .bak; rollback must restore that old state too.
                    if (old.backupBytes() != null) {
                        old.backup().writeBytes(old.backupBytes(), false);
                    } else if (old.backup().exists() && !old.backup().delete()) {
                        throw new IllegalStateException("Unable to remove rollback backup: " + old.backup());
                    }
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }
}
