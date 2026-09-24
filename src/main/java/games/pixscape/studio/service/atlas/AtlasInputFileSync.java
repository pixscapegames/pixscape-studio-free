package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Gdx;
import games.pixscape.studio.helper.InternalAssets;

import java.util.HashSet;
import java.util.Set;

/** Shared low-level file projection used by Scene and HUD atlas membership collectors. */
final class AtlasInputFileSync {
    private static final String INTERNAL_DIR = "__pixscape_internal__";

    private AtlasInputFileSync() {
    }

    static AtlasInputSyncResult sync(FileHandle projectDir, FileHandle inputDir,
                                     Set<String> requiredProjectRelativePaths) {
        return sync(projectDir, inputDir, requiredProjectRelativePaths, true);
    }

    static AtlasInputSyncResult sync(FileHandle projectDir, FileHandle inputDir,
                                     Set<String> requiredProjectRelativePaths,
                                     boolean failOnMissingSource) {
        return sync(projectDir, inputDir, requiredProjectRelativePaths, failOnMissingSource, false);
    }

    /** HUD workers compare content, including same-size/same-timestamp source replacements. */
    static AtlasInputSyncResult sync(FileHandle projectDir, FileHandle inputDir,
                                     Set<String> requiredProjectRelativePaths,
                                     boolean failOnMissingSource, boolean compareContent) {
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");
        if (inputDir == null) throw new IllegalArgumentException("inputDir is null");
        Set<String> required = requiredProjectRelativePaths != null
                ? requiredProjectRelativePaths : Set.of();

        inputDir.mkdirs();
        ensureInternalWhitePixel(inputDir);
        int deleted = cleanupUnusedInputFiles(inputDir, requiredFileNames(required));
        int copied = 0;
        for (String relPath : required) {
            if (relPath == null || relPath.isBlank()) continue;
            FileHandle source = projectDir.child(relPath);
            if (!source.exists() || source.isDirectory()) {
                String message = "Missing atlas input source: " + relPath + ".";
                if (failOnMissingSource) throw new IllegalArgumentException(message);
                if (Gdx.app != null) Gdx.app.error("AtlasInputFileSync", message);
                continue;
            }
            FileHandle dest = inputDir.child(source.name());
            if (compareContent) {
                try {
                    if (!dest.exists() || java.nio.file.Files.mismatch(
                            source.file().toPath(), dest.file().toPath()) != -1) {
                        source.copyTo(dest);
                        copied++;
                    }
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException("Unable to compare HUD atlas input: " + relPath, failure);
                }
            } else if (copyIfDifferent(source, dest)) copied++;
        }
        return new AtlasInputSyncResult(deleted > 0 || copied > 0, copied, deleted);
    }

    static void ensureInternalWhitePixel(FileHandle inputDir) {
        FileHandle whitePixel = inputDir.child(INTERNAL_DIR)
                .child(InternalAssets.WHITE_PIXEL_FILE);
        if (!whitePixel.exists()) InternalAssets.copyWhitePixelTo(whitePixel);
    }

    static boolean copyIfDifferent(FileHandle source, FileHandle dest) {
        return games.pixscape.studio.io.StudioIO.copyIfDifferent(source, dest);
    }

    private static Set<String> requiredFileNames(Set<String> requiredPaths) {
        Set<String> names = new HashSet<>();
        for (String path : requiredPaths) {
            String name = fileName(path);
            if (!name.isBlank() && !names.add(name)) {
                throw new IllegalArgumentException(
                        "Atlas inputs must have unique file names; duplicate: " + name + ".");
            }
        }
        return names;
    }

    private static int cleanupUnusedInputFiles(FileHandle inputDir, Set<String> requiredNames) {
        if (!inputDir.exists()) return 0;
        int deleted = 0;
        for (FileHandle child : inputDir.list()) {
            if (child == null) continue;
            if (child.isDirectory()) {
                if (!INTERNAL_DIR.equals(child.name())) {
                    child.deleteDirectory();
                    deleted++;
                }
            } else if (games.pixscape.studio.io.StudioFs.isImageFile(child.name())
                    && !requiredNames.contains(child.name())) {
                child.delete();
                deleted++;
            }
        }
        return deleted;
    }

    private static String fileName(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
