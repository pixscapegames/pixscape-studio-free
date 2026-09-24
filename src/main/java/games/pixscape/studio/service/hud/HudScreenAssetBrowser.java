package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.studio.ui.asset.AssetNode;

import java.util.Arrays;
import java.util.Comparator;

/** Minimal deterministic project-browser projection for Runtime HUD screen assets. */
public final class HudScreenAssetBrowser {
    private HudScreenAssetBrowser() {}

    public static Array<AssetNode> scan(FileHandle projectDir) {
        Array<AssetNode> result = new Array<>();
        FileHandle directory = projectDir.child(HudScreenAssetId.DIRECTORY);
        if (!directory.exists()) return result;
        FileHandle[] files = directory.list(HudScreenAsset.EXTENSION.substring(1));
        Arrays.sort(files, Comparator.comparing(FileHandle::name));
        for (FileHandle file : files) {
            String name = file.nameWithoutExtension();
            result.add(new AssetNode(AssetNode.Kind.HUD_SCREEN, AssetNode.Root.HUD,
                    HudScreenAssetId.DIRECTORY + "/" + name, name, null));
        }
        return result;
    }
}
