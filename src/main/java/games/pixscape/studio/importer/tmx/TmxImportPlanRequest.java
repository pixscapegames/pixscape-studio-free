package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;

public record TmxImportPlanRequest(FileHandle tmxFile, FileHandle tiledProject) {
    public TmxImportPlanRequest(FileHandle tmxFile) {
        this(tmxFile, null);
    }
}
