package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import games.pixscape.studio.io.StudioFs;

import java.io.File;
import java.io.IOException;

public final class ProjectRenameService {

    private ProjectRenameService() {
    }

    public static FileHandle renameProjectFile(FileHandle projectDir, ProjectConfig cfg, String newProjectFileName) {
        if (projectDir == null) throw new GdxRuntimeException("projectDir is null");
        if (cfg == null) throw new GdxRuntimeException("cfg is null");
        if (newProjectFileName == null || newProjectFileName.isBlank()) {
            throw new GdxRuntimeException("Project file name cannot be blank.");
        }

        String before = StudioFs.requireProjectFileName(cfg);
        String after = newProjectFileName;

        FileHandle beforeFile = projectDir.child(before + StudioFs.EXT_JSON);
        if (!beforeFile.exists()) {
            throw new GdxRuntimeException("Missing project file: " + beforeFile.path());
        }

        FileHandle afterFile = projectDir.child(after + StudioFs.EXT_JSON);
        if (!beforeFile.path().equals(afterFile.path())) {
            if (afterFile.exists()) {
                throw new GdxRuntimeException("A project file with the same name already exists: " + afterFile.path());
            }
            beforeFile.moveTo(afterFile);
        }

        cfg.projectFileName = after;
        return projectDir;
    }

    public static void moveProjectDirectory(FileHandle sourceDir, FileHandle targetDir) {
        if (sourceDir == null) throw new GdxRuntimeException("sourceDir is null");
        if (targetDir == null) throw new GdxRuntimeException("targetDir is null");
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new GdxRuntimeException("Project directory is missing: " + sourceDir.path());
        }
        if (sourceDir.path().equals(targetDir.path())) {
            return;
        }
        if (targetDir.exists() && !targetDir.isDirectory()) {
            throw new GdxRuntimeException("Project directory target is not a directory: " + targetDir.path());
        }
        if (targetDir.exists() && targetDir.list().length > 0) {
            throw new GdxRuntimeException("Project directory target must be empty: " + targetDir.path());
        }
        if (isSameOrChild(targetDir.file(), sourceDir.file())) {
            throw new GdxRuntimeException("Project directory target cannot be inside the current project directory: " + targetDir.path());
        }

        targetDir.mkdirs();
        for (FileHandle child : sourceDir.list()) {
            child.moveTo(targetDir.child(child.name()));
        }
        sourceDir.deleteDirectory();
    }

    public static void copyProjectDirectory(FileHandle sourceDir, FileHandle targetDir) {
        if (sourceDir == null) throw new GdxRuntimeException("sourceDir is null");
        if (targetDir == null) throw new GdxRuntimeException("targetDir is null");
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new GdxRuntimeException("Project directory is missing: " + sourceDir.path());
        }
        if (sourceDir.path().equals(targetDir.path())) {
            return;
        }
        if (targetDir.exists() && !targetDir.isDirectory()) {
            throw new GdxRuntimeException("Project directory target is not a directory: " + targetDir.path());
        }
        if (targetDir.exists() && targetDir.list().length > 0) {
            throw new GdxRuntimeException("Project directory target must be empty: " + targetDir.path());
        }
        if (isSameOrChild(targetDir.file(), sourceDir.file())) {
            throw new GdxRuntimeException("Project directory target cannot be inside the current project directory: " + targetDir.path());
        }

        targetDir.mkdirs();
        for (FileHandle child : sourceDir.list()) {
            child.copyTo(targetDir.child(child.name()));
        }
    }

    private static boolean isSameOrChild(File childOrSame, File parent) {
        try {
            String childPath = childOrSame.getCanonicalPath();
            String parentPath = parent.getCanonicalPath();
            if (childPath.equals(parentPath)) return true;
            String prefix = parentPath.endsWith(File.separator) ? parentPath : parentPath + File.separator;
            return childPath.startsWith(prefix);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to resolve canonical project paths.", e);
        }
    }

}
