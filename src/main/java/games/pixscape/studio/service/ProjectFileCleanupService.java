package games.pixscape.studio.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.io.StudioFs;

public final class ProjectFileCleanupService {

    private static final String TAG = "ProjectFileCleanupService";

    private ProjectFileCleanupService() {
    }

    public static void deleteSceneAtlasFiles(FileHandle projectDir, String sceneTag) {
        if (projectDir == null || sceneTag == null || sceneTag.isBlank()) return;

        FileHandle atlasesDir = projectDir.child(StudioFs.DIR_ATLASES);
        if (!atlasesDir.exists() || !atlasesDir.isDirectory()) return;

        for (FileHandle child : atlasesDir.list()) {
            if (child == null || child.isDirectory()) continue;

            if (isSceneAtlasFile(sceneTag, child.name())) {
                deleteFileAndBackups(child);
            }
        }
    }

    public static void deleteSceneAtlasInput(FileHandle projectDir, String sceneTag) {
        if (projectDir == null || sceneTag == null || sceneTag.isBlank()) return;

        FileHandle inputDir = projectDir
                .child(StudioFs.DIR_ATLASES)
                .child(StudioFs.DIR_INPUT)
                .child(sceneTag);

        if (inputDir.exists()) {
            deleteDirectoryQuietly(inputDir);
        }
    }

    public static void deleteFileAndBackups(FileHandle file) {
        if (file == null) return;

        deleteFileQuietly(file);
        deleteFileQuietly(file.sibling(file.name() + ".bak"));
        deleteFileQuietly(file.sibling(file.name() + ".back"));
    }

    public static boolean isSceneAtlasFile(String sceneTag, String fileName) {
        if (sceneTag == null || sceneTag.isBlank()) return false;
        if (fileName == null || fileName.isBlank()) return false;

        if (fileName.equals(sceneTag + ".atlas")) {
            return true;
        }

        if (fileName.equals(sceneTag + ".png")) {
            return true;
        }

        if (!fileName.startsWith(sceneTag + "-")) {
            return false;
        }

        if (!fileName.endsWith(".png")) {
            return false;
        }

        String pagePart = fileName.substring(
                sceneTag.length() + 1,
                fileName.length() - ".png".length()
        );

        if (pagePart.isBlank()) {
            return false;
        }

        for (int i = 0; i < pagePart.length(); i++) {
            if (!Character.isDigit(pagePart.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean shouldSkipRuntimeExportFile(FileHandle file) {
        if (file == null) return true;

        String name = file.name();
        if (name == null || name.isBlank()) return true;

        return name.endsWith(".bak")
                || name.endsWith(".back");
    }

    public static boolean shouldSkipRuntimeExportDirectory(FileHandle dir) {
        if (dir == null) return true;

        String name = dir.name();
        if (name == null || name.isBlank()) return true;

        return StudioFs.DIR_INPUT.equals(name)
                || ".tmp".equals(name);
    }

    private static void deleteFileQuietly(FileHandle file) {
        if (file == null) return;

        try {
            if (file.exists() && !file.isDirectory()) {
                file.delete();
                Gdx.app.log(TAG, "Deleted file: " + file.path());
            }
        } catch (Exception ex) {
            Gdx.app.error(TAG, "Failed to delete file: " + file.path(), ex);
        }
    }

    private static void deleteDirectoryQuietly(FileHandle dir) {
        if (dir == null) return;

        try {
            if (dir.exists() && dir.isDirectory()) {
                dir.deleteDirectory();
                Gdx.app.log(TAG, "Deleted directory: " + dir.path());
            }
        } catch (Exception ex) {
            Gdx.app.error(TAG, "Failed to delete directory: " + dir.path(), ex);
        }
    }

    public static Array<FileSnapshotEntry> snapshotAtlasPages(FileHandle atlasDir, String sceneTag) {
        if (atlasDir == null || !atlasDir.exists() || !atlasDir.isDirectory()) return null;
        if (sceneTag == null || sceneTag.isBlank()) return null;

        Array<FileSnapshotEntry> out = new Array<>();

        for (FileHandle child : atlasDir.list()) {
            if (child == null || child.isDirectory()) continue;
            if (!isSceneAtlasFile(sceneTag, child.name())) continue;

            out.add(new FileSnapshotEntry(child.name(), child.readBytes()));
        }

        return out;
    }

    public static Array<FileSnapshotEntry> snapshotDirectory(FileHandle dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;

        Array<FileSnapshotEntry> out = new Array<>();
        collectDirectorySnapshot(dir, dir, out);
        return out;
    }

    private static void collectDirectorySnapshot(FileHandle root,
                                                 FileHandle current,
                                                 Array<FileSnapshotEntry> out) {
        if (root == null || current == null || out == null) return;
        if (!current.exists()) return;

        for (FileHandle child : current.list()) {
            if (child == null) continue;

            if (child.isDirectory()) {
                collectDirectorySnapshot(root, child, out);
                continue;
            }

            String relativePath = root.file().toPath()
                    .relativize(child.file().toPath())
                    .toString()
                    .replace('\\', '/');

            out.add(new FileSnapshotEntry(relativePath, child.readBytes()));
        }
    }

    public static void restoreAtlasPagesFromSnapshot(FileHandle atlasDir,
                                                     Array<FileSnapshotEntry> snapshot) {
        if (atlasDir == null || snapshot == null) return;

        atlasDir.mkdirs();

        for (FileSnapshotEntry entry : snapshot) {
            if (entry == null || entry.relativePath == null || entry.relativePath.isBlank()) {
                continue;
            }

            FileHandle file = atlasDir.child(entry.relativePath);
            file.parent().mkdirs();
            file.writeBytes(entry.bytes != null ? entry.bytes : new byte[0], false);
        }
    }

    public static void restoreDirectoryFromSnapshot(FileHandle dir,
                                                    Array<FileSnapshotEntry> snapshot) {
        if (dir == null) return;

        if (dir.exists()) {
            dir.deleteDirectory();
        }

        if (snapshot == null) return;

        dir.mkdirs();

        for (FileSnapshotEntry entry : snapshot) {
            if (entry == null || entry.relativePath == null || entry.relativePath.isBlank()) {
                continue;
            }

            FileHandle file = dir.child(entry.relativePath);
            file.parent().mkdirs();
            file.writeBytes(entry.bytes != null ? entry.bytes : new byte[0], false);
        }
    }

    public static byte[] snapshotFile(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return null;
        return file.readBytes();
    }

    public static void restoreFileFromSnapshot(FileHandle file, byte[] snapshot) {
        if (file == null) return;

        if (snapshot == null) {
            deleteFileAndBackups(file);
            return;
        }

        file.parent().mkdirs();
        file.writeBytes(snapshot, false);
    }

    public record FileSnapshotEntry(String relativePath, byte[] bytes) {
    }
}