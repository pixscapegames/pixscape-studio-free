package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;

public record TmxSceneImportRequest(FileHandle tmxFile,
                                    String requestedSceneName,
                                    boolean packSceneAtlas,
                                    FileHandle tiledProject) {

    public TmxSceneImportRequest(FileHandle file, String name, boolean pack) {
        this(file, name, pack, null);
    }

    public TmxSceneImportRequest(FileHandle tmxFile) {
        this(tmxFile, null, true);
    }

    public TmxSceneImportRequest(FileHandle tmxFile, String requestedSceneName) {
        this(tmxFile, requestedSceneName, true);
    }
}
