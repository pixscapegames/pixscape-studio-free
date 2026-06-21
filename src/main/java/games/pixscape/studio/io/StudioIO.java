package games.pixscape.studio.io;

import com.badlogic.gdx.files.FileHandle;

import java.io.OutputStream;

public final class StudioIO {

    private StudioIO() {
    }

    @FunctionalInterface
    public interface OutputStreamWriter {
        void write(OutputStream out) throws Exception;
    }

    public static void ensureParentDir(FileHandle file) {
        if (file == null) throw new IllegalArgumentException("file is null");
        FileHandle parent = file.parent();
        if (parent != null && !parent.exists()) parent.mkdirs();
    }

    public static String readUtf8(FileHandle file) {
        if (file == null) throw new IllegalArgumentException("file is null");
        return file.readString("UTF-8");
    }

    public static void writeUtf8Atomic(FileHandle target, String content) {
        if (content == null) throw new IllegalArgumentException("content is null");

        writeAtomic(target, out -> {
            try (var writer = new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write(content);
                writer.flush();
            }
        });
    }

    public static void writeAtomic(FileHandle target, OutputStreamWriter writer) {
        if (target == null) throw new IllegalArgumentException("target is null");
        if (writer == null) throw new IllegalArgumentException("writer is null");

        ensureParentDir(target);

        FileHandle dir = target.parent();
        if (dir == null) {
            throw new IllegalArgumentException("target has no parent: " + target.path());
        }

        String tmpName = "." + target.name() + ".tmp";
        FileHandle tmp = dir.child(tmpName);

        if (tmp.exists()) tmp.delete();

        try (OutputStream out = tmp.write(false)) {
            writer.write(out);
            out.flush();
        } catch (Exception e) {
            try {
                if (tmp.exists()) tmp.delete();
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Atomic write failed: " + target.path(), e);
        }

        FileHandle bak = dir.child(target.name() + ".bak");
        try {
            if (bak.exists()) bak.delete();
            if (target.exists()) target.copyTo(bak);
        } catch (Exception ignored) {
        }

        if (target.exists()) target.delete();
        tmp.moveTo(target);

        if (!target.exists()) {
            throw new RuntimeException("Atomic replace failed: " + target.path());
        }
    }
}