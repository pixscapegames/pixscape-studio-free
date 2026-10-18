package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.asset.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TileNormalMapBrowserTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    private static TileAssetMeta tile(AssetMetaDatabase db, String name) {
        return (TileAssetMeta) db.registerIfAbsent(AssetType.TILE, "tiles/test/" + name,
                "orig/tiles/test/" + name + ".png", AssetMeta.AssetScope.USER);
    }

    @Test public void savedAssociationSuppliesPreviewResourceWithoutCreatingAnAsset() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TileAssetMeta tile = tile(db, "wall");
        tile.normalSourceRelPath = "orig/tiles/test/normals/wall.png";
        FileHandle file = new FileHandle(temporary.newFile("assets.json"));
        db.save(file);
        Array<AssetNode> nodes = new Array<>();
        AssetsFolderScanner.appendNormalMaps(AssetMetaDatabase.load(file), "test/normals", nodes);
        assertEquals(1, nodes.size);
        AssetNode normal = nodes.first();
        assertEquals(AssetNode.Kind.NORMAL_MAP, normal.kind);
        assertEquals("test/normals/wall.png", normal.path);
        assertEquals(-1, normal.assetId);
        assertEquals(-1, AssetsPanel.tileAssetIdForSelection(normal));
    }

    @Test public void associationChangesAreReadAgainOnRefresh() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TileAssetMeta tile = tile(db, "wall");
        Array<AssetNode> nodes = new Array<>();
        AssetsFolderScanner.appendNormalMaps(db, "test/normals", nodes);
        assertEquals(0, nodes.size);
        tile.normalSourceRelPath = "orig/tiles/test/normals/first.png";
        AssetsFolderScanner.appendNormalMaps(db, "test/normals", nodes);
        assertEquals("test/normals/first.png", nodes.first().path);
        nodes.clear();
        tile.normalSourceRelPath = "orig/tiles/test/normals/replacement.png";
        AssetsFolderScanner.appendNormalMaps(db, "test/normals", nodes);
        assertEquals("test/normals/replacement.png", nodes.first().path);
        nodes.clear();
        tile.normalSourceRelPath = null;
        AssetsFolderScanner.appendNormalMaps(db, "test/normals", nodes);
        assertEquals(0, nodes.size);
    }

    @Test public void missingSourceRemainsVisibleAndSharedAssociationsAreDeduplicated() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        tile(db, "wall").normalSourceRelPath = "orig/tiles/test/normals/missing.png";
        tile(db, "other").normalSourceRelPath = "orig/tiles/test/normals/missing.png";
        Array<AssetNode> nodes = new Array<>();
        AssetsFolderScanner.appendNormalMaps(db, "test/normals", nodes);
        assertEquals(1, nodes.size);
        assertEquals("missing.png", nodes.first().name);
    }

    @Test public void directoryBoundariesExcludeOtherTilesets() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        tile(db, "wall").normalSourceRelPath = "orig/tiles/test/normals/wall.png";
        tile(db, "other").normalSourceRelPath = "orig/tiles/test/normals-other/wall.png";
        Array<AssetNode> nodes = new Array<>();
        AssetsFolderScanner.appendNormalMaps(db, "test/normals", nodes);
        assertEquals(1, nodes.size);
    }
}
