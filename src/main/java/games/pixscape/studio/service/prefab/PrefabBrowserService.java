package games.pixscape.studio.service.prefab;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;

public final class PrefabBrowserService {

    public FileHandle requirePrefabsDir(ProjectConfig cfg) {
        return StudioFs.requirePrefabsDir(cfg);
    }

    public Array<PrefabAssetItem> scan(ProjectConfig cfg) {
        FileHandle dir = requirePrefabsDir(cfg);
        Array<PrefabAssetItem> out = new Array<>();

        for (FileHandle file : dir.list()) {
            if (file == null || file.isDirectory()) continue;
            if (!file.name().endsWith(StudioFs.EXT_PREFAB)) continue;

            String baseName = StudioFs.removeExtension(file.name());
            FileHandle preview = dir.child(baseName + ".preview.png");

            out.add(new PrefabAssetItem(baseName, file, preview));
        }

        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    public void deletePrefab(PrefabAssetItem item) {
        if (item == null) return;

        if (item.prefabFile() != null && item.prefabFile().exists()) {
            item.prefabFile().delete();
        }

        if (item.previewFile() != null && item.previewFile().exists()) {
            item.previewFile().delete();
        }
    }
}
