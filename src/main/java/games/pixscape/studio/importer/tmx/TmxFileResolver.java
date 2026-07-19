package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;

import java.nio.file.Path;

public final class TmxFileResolver {

    public FileHandle resolveRelative(FileHandle declaringFile, String source) {
        if (declaringFile == null || source == null || source.isBlank()) {
            return null;
        }

        String normalizedSource = source.replace('\\', '/');
        Path sourcePath = Path.of(normalizedSource);
        if (sourcePath.isAbsolute()) {
            return new FileHandle(sourcePath.normalize().toFile());
        }

        Path parent = declaringFile.file().toPath().toAbsolutePath().getParent();
        if (parent == null) {
            parent = Path.of("").toAbsolutePath();
        }
        return new FileHandle(parent.resolve(normalizedSource).normalize().toFile());
    }
}
