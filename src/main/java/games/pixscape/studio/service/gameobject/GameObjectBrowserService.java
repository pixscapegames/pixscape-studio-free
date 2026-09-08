package games.pixscape.studio.service.gameobject;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;

public final class GameObjectBrowserService {

    public FileHandle requireGameObjectsDir(ProjectConfig cfg) {
        return StudioFs.requireGameObjectsDir(cfg);
    }

    public Array<GameObjectAssetItem> scan(ProjectConfig cfg) {
        FileHandle dir = requireGameObjectsDir(cfg);
        Array<GameObjectAssetItem> out = new Array<>();

        for (FileHandle file : dir.list()) {
            if (file == null || file.isDirectory()) continue;
            if (!file.name().endsWith(GameObjectAsset.EXTENSION)) continue;

            String baseName = StudioFs.removeExtension(file.name());
            FileHandle preview = dir.child(baseName + ".preview.png");

            out.add(new GameObjectAssetItem(baseName, file, preview));
        }

        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    public void deleteGameObject(GameObjectAssetItem item) {
        if (item == null) return;

        if (item.gameObjectFile() != null && item.gameObjectFile().exists()) {
            item.gameObjectFile().delete();
        }

        if (item.previewFile() != null && item.previewFile().exists()) {
            item.previewFile().delete();
        }
    }
}
