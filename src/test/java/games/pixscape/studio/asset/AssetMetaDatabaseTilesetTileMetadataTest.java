package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AssetMetaDatabaseTilesetTileMetadataTest {

    @Test
    public void saveAndLoad_preservesTilesetAndTileDedicatedMetadata() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();

        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/terrain",
                "orig/tiles/terrain/terrain__a12.png",
                AssetMeta.AssetScope.USER
        );
        tileset.imageWidth = 128;
        tileset.imageHeight = 64;
        tileset.tileWidth = 16;
        tileset.tileHeight = 16;
        tileset.columns = 8;
        tileset.rows = 4;
        tileset.spacing = 1;
        tileset.margin = 2;

        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/terrain_2_1",
                "orig/tiles/terrain/terrain_2_1__a13.png",
                AssetMeta.AssetScope.USER
        );
        tile.tilesetId = tileset.id;
        tile.sheetIndex = 10;
        tile.cellX = 2;
        tile.cellY = 1;

        Path tmp = Files.createTempFile("asset-meta-db", ".json");
        FileHandle file = new FileHandle(tmp.toFile());
        db.save(file);

        String assetsJson = Files.readString(tmp);
        assertTrue(assetsJson.contains("\"class\": \"games.pixscape.studio.asset.TilesetAssetMeta\""));
        assertTrue(assetsJson.contains("\"class\": \"games.pixscape.studio.asset.TileAssetMeta\""));
        assertTrue(assetsJson.contains("\"type\": \"TILESET\""));
        assertTrue(assetsJson.contains("\"type\": \"TILE\""));
        assertTrue(assetsJson.contains("\"tilesetId\": " + tileset.id));
        assertTrue(assetsJson.contains("\"sheetIndex\": 10"));
        assertTrue(assetsJson.contains("\"cellX\": 2"));
        assertTrue(assetsJson.contains("\"cellY\": 1"));
        assertTrue(assetsJson.contains("\"imageWidth\": 128"));
        assertTrue(assetsJson.contains("\"imageHeight\": 64"));
        assertTrue(assetsJson.contains("\"tileWidth\": 16"));
        assertTrue(assetsJson.contains("\"tileHeight\": 16"));
        assertTrue(assetsJson.contains("\"columns\": 8"));
        assertTrue(assetsJson.contains("\"rows\": 4"));
        assertTrue(assetsJson.contains("\"spacing\": 1"));
        assertTrue(assetsJson.contains("\"margin\": 2"));
        assertFalse(assetsJson.contains("\"class\": \"games.pixscape.studio.asset.AssetMeta\""));

        AssetMetaDatabase loaded = AssetMetaDatabase.load(file);
        assertTrue(loaded.findByLogicalPath("tiles/terrain") instanceof TilesetAssetMeta);
        assertTrue(loaded.findByLogicalPath("tiles/terrain/terrain_2_1") instanceof TileAssetMeta);

        TilesetAssetMeta loadedTileset = (TilesetAssetMeta) loaded.findByLogicalPath("tiles/terrain");
        TileAssetMeta loadedTile = (TileAssetMeta) loaded.findByLogicalPath("tiles/terrain/terrain_2_1");

        assertEquals(128, loadedTileset.imageWidth);
        assertEquals(64, loadedTileset.imageHeight);
        assertEquals(16, loadedTileset.tileWidth);
        assertEquals(16, loadedTileset.tileHeight);
        assertEquals(8, loadedTileset.columns);
        assertEquals(4, loadedTileset.rows);
        assertEquals(1, loadedTileset.spacing);
        assertEquals(2, loadedTileset.margin);

        assertEquals(tileset.id, loadedTile.tilesetId);
        assertEquals(10, loadedTile.sheetIndex);
        assertEquals(2, loadedTile.cellX);
        assertEquals(1, loadedTile.cellY);
    }
}
