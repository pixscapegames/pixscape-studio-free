package games.pixscape.studio.configuration;

import com.badlogic.gdx.utils.Json;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;

public final class EditorSettings {
    public int msaaSamples = 8;
    public boolean autoRepackAtlases = true;
    public String lastProjectPath;
    public ArrayList<String> recentProjectPaths = new ArrayList<>();

    private static EditorSettings INSTANCE;

    private EditorSettings() {
    }

    public static EditorSettings get() {
        if (INSTANCE == null) INSTANCE = new EditorSettings();
        return INSTANCE;
    }

    public static void load() {
        Path dir = Paths.get(System.getProperty("user.home"), ".pixscape-studio");
        Path file = dir.resolve("pixscape-studio.json");
        if (!Files.exists(file)) {
            INSTANCE = new EditorSettings();
            return;
        }

        try {
            String jsonStr = Files.readString(file, StandardCharsets.UTF_8);
            Json json = new Json();
            EditorSettings loaded = json.fromJson(EditorSettings.class, jsonStr);
            INSTANCE = (loaded != null) ? loaded : new EditorSettings();
        } catch (Exception e) {
            // fallback: default settings
            INSTANCE = new EditorSettings();
        }
    }

    public static void save() {
        Path dir = Paths.get(System.getProperty("user.home"), ".pixscape-studio");
        Path file = dir.resolve("pixscape-studio.json");

        try {
            Files.createDirectories(dir);

            Json json = new Json();
            json.setUsePrototypes(false);
            String jsonStr = json.prettyPrint(get());
            byte[] bytes = jsonStr.getBytes(StandardCharsets.UTF_8);

            writeAtomic(file, bytes);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------------------
    // Atomic file write (same-dir temp + atomic move)
    // ---------------------------------------------------------------------

    private static void writeAtomic(Path target, byte[] bytes) throws Exception {
        Path dir = target.getParent();
        if (dir == null) throw new IllegalStateException("Target has no parent: " + target);

        Path tmp = dir.resolve(target.getFileName().toString() + ".tmp");
        Path bak = dir.resolve(target.getFileName() + ".bak");

        // 1) write tmp
        try (OutputStream out = Files.newOutputStream(
                tmp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            out.write(bytes);
            out.flush();
        }

        // 2) best-effort backup of current
        if (Files.exists(target)) {
            try {
                Files.move(target, bak, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(target, bak, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                // backup failed -> continue anyway (better to save than to fail)
            }
        }

        // 3) move tmp -> target (atomic if possible)
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }

    }
}
