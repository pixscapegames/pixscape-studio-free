package games.pixscape.studio.service.gameobject;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.gameobject.GameObjectAssetId;

public record GameObjectAssetItem(String name, FileHandle gameObjectFile, FileHandle previewFile) {
    public String logicalAssetId() {
        return GameObjectAssetId.normalize(name);
    }
}
