package games.pixscape.studio.service.prefab;

import com.badlogic.gdx.files.FileHandle;

public record PrefabAssetItem(String name, FileHandle prefabFile, FileHandle previewFile) {
}
