package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;

public record TmxPreflightRequest(FileHandle tmxFile, FileHandle tiledProject) {
    public TmxPreflightRequest(FileHandle tmxFile) {
        this(tmxFile, null);
    }
}
