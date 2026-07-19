package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class RuntimeExportPaths {
    private RuntimeExportPaths() {
    }

    public static Path userRootPath(ProjectConfig cfg) {
        if (cfg == null || cfg.exportRootPathDir == null || cfg.exportRootPathDir.isBlank()) {
            return null;
        }
        return userRootPath(Path.of(cfg.exportRootPathDir));
    }

    public static Path userRootPath(Path configuredExportRoot) {
        if (configuredExportRoot == null) {
            return null;
        }
        Path normalized = configuredExportRoot.normalize();
        Path fileName = normalized.getFileName();
        if (fileName != null && RuntimeExport.RUNTIME_DIR_NAME.equals(fileName.toString())) {
            Path parent = normalized.getParent();
            if (parent != null) {
                return parent;
            }
        }
        return normalized;
    }

    public static Path runtimeRootPath(ProjectConfig cfg) {
        Path userRoot = userRootPath(cfg);
        return userRoot != null ? userRoot.resolve(RuntimeExport.RUNTIME_DIR_NAME) : null;
    }

    public static FileHandle userRootFileHandle(ProjectConfig cfg) {
        Path userRoot = userRootPath(cfg);
        return userRoot != null ? new FileHandle(userRoot.toFile()) : null;
    }

    public static String normalizeExportRootPath(String exportRootPath) {
        if (exportRootPath == null || exportRootPath.isBlank()) {
            return exportRootPath;
        }
        try {
            return userRootPath(Path.of(exportRootPath)).toString();
        } catch (InvalidPathException ex) {
            return exportRootPath;
        }
    }
}
