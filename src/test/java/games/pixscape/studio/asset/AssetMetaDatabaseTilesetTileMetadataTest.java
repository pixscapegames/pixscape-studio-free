package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
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
        tileset.referenceCellWidth = 24;
        tileset.referenceCellHeight = 20;
        tileset.projection = SceneMetaRuntime.TiledProjection.ISO;
        tileset.anchor = TilesetAnchor.TOP_LEFT;
        tileset.offsetX = -3;
        tileset.offsetY = 5;
        tileset.renderSize = TilesetRenderSize.NATIVE;

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
        assertTrue(assetsJson.contains("\"referenceCellWidth\": 24"));
        assertTrue(assetsJson.contains("\"referenceCellHeight\": 20"));
        assertTrue(assetsJson.contains("\"projection\": \"isometric\""));
        assertTrue(assetsJson.contains("\"anchor\": \"top-left\""));
        assertTrue(assetsJson.contains("\"offsetX\": -3"));
        assertTrue(assetsJson.contains("\"offsetY\": 5"));
        assertTrue(assetsJson.contains("\"renderSize\": \"native\""));
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
        assertEquals(24, loadedTileset.referenceCellWidth);
        assertEquals(20, loadedTileset.referenceCellHeight);
        assertSame(SceneMetaRuntime.TiledProjection.ISO, loadedTileset.projection);
        assertSame(TilesetAnchor.TOP_LEFT, loadedTileset.anchor);
        assertEquals(-3, loadedTileset.offsetX);
        assertEquals(5, loadedTileset.offsetY);
        assertSame(TilesetRenderSize.NATIVE, loadedTileset.renderSize);

        assertEquals(tileset.id, loadedTile.tilesetId);
        assertEquals(10, loadedTile.sheetIndex);
        assertEquals(2, loadedTile.cellX);
        assertEquals(1, loadedTile.cellY);
    }

    @Test
    public void save_writesProfileFieldsAndCurrentVersion() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();

        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/water",
                "orig/tiles/water.png",
                AssetMeta.AssetScope.USER
        );
        tileset.tileWidth = 48;
        tileset.tileHeight = 32;
        tileset.referenceCellWidth = 64;
        tileset.referenceCellHeight = 36;
        tileset.projection = SceneMetaRuntime.TiledProjection.ISO;
        tileset.anchor = TilesetAnchor.BOTTOM_LEFT;
        tileset.offsetX = 8;
        tileset.offsetY = -6;
        tileset.renderSize = TilesetRenderSize.NATIVE;

        Path tmp = Files.createTempFile("asset-meta-db-profile", ".json");
        db.save(new FileHandle(tmp.toFile()));

        String assetsJson = Files.readString(tmp);
        assertTrue(assetsJson.contains("\"version\": 3"));
        assertTrue(assetsJson.contains("\"referenceCellWidth\": 64"));
        assertTrue(assetsJson.contains("\"referenceCellHeight\": 36"));
        assertTrue(assetsJson.contains("\"projection\": \"isometric\""));
        assertTrue(assetsJson.contains("\"anchor\": \"bottom-left\""));
        assertTrue(assetsJson.contains("\"offsetX\": 8"));
        assertTrue(assetsJson.contains("\"offsetY\": -6"));
        assertTrue(assetsJson.contains("\"renderSize\": \"native\""));
    }

    @Test
    public void load_oldTilesetMetadataDefaultsProfileFields() throws Exception {
        Path tmp = Files.createTempFile("asset-meta-db-old", ".json");
        Files.writeString(tmp, """
                {
                  "version": 2,
                  "nextId": 2,
                  "assets": [
                    {
                      "id": 1,
                      "type": "tileset",
                      "logicalPath": "tiles/old",
                      "sourceRelPath": "orig/tiles/old.png",
                      "scope": "USER",
                      "imageWidth": 90,
                      "imageHeight": 120,
                      "tileWidth": 18,
                      "tileHeight": 24,
                      "columns": 5,
                      "rows": 5,
                      "spacing": 1,
                      "margin": 2
                    }
                  ]
                }
                """);

        AssetMetaDatabase loaded = AssetMetaDatabase.load(new FileHandle(tmp.toFile()));
        TilesetAssetMeta tileset = (TilesetAssetMeta) loaded.findByLogicalPath("tiles/old");

        assertEquals(18, tileset.referenceCellWidth);
        assertEquals(24, tileset.referenceCellHeight);
        assertSame(SceneMetaRuntime.TiledProjection.ORTHO, tileset.projection);
        assertSame(TilesetAnchor.BOTTOM_CENTER, tileset.anchor);
        assertEquals(0, tileset.offsetX);
        assertEquals(0, tileset.offsetY);
        assertSame(TilesetRenderSize.NATIVE, tileset.renderSize);
    }

    @Test
    public void load_invalidProfileEnumsDefaultSafely() throws Exception {
        Path tmp = Files.createTempFile("asset-meta-db-invalid-profile", ".json");
        Files.writeString(tmp, """
                {
                  "version": 3,
                  "nextId": 2,
                  "assets": [
                    {
                      "id": 1,
                      "type": "tileset",
                      "logicalPath": "tiles/bad-profile",
                      "sourceRelPath": "orig/tiles/bad-profile.png",
                      "scope": "USER",
                      "tileWidth": 0,
                      "tileHeight": -4,
                      "referenceCellWidth": -1,
                      "referenceCellHeight": 0,
                      "projection": "hexagonal",
                      "anchor": "middle-right",
                      "offsetX": 2,
                      "offsetY": -9,
                      "renderSize": "scaled"
                    }
                  ]
                }
                """);

        AssetMetaDatabase loaded = AssetMetaDatabase.load(new FileHandle(tmp.toFile()));
        TilesetAssetMeta tileset = (TilesetAssetMeta) loaded.findByLogicalPath("tiles/bad-profile");

        assertEquals(32, tileset.referenceCellWidth);
        assertEquals(32, tileset.referenceCellHeight);
        assertSame(SceneMetaRuntime.TiledProjection.ORTHO, tileset.projection);
        assertSame(TilesetAnchor.BOTTOM_CENTER, tileset.anchor);
        assertEquals(2, tileset.offsetX);
        assertEquals(-9, tileset.offsetY);
        assertSame(TilesetRenderSize.NATIVE, tileset.renderSize);
    }

    @Test
    public void save_tileMetadataDoesNotReceiveTilesetProfileFields() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();

        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/terrain/grass",
                "orig/tiles/terrain/grass.png",
                AssetMeta.AssetScope.USER
        );
        tile.tilesetId = 7;
        tile.sheetIndex = 3;
        tile.cellX = 1;
        tile.cellY = 2;

        Path tmp = Files.createTempFile("asset-meta-db-tile-only", ".json");
        FileHandle file = new FileHandle(tmp.toFile());
        db.save(file);

        String assetsJson = Files.readString(tmp);
        assertTrue(assetsJson.contains("\"tilesetId\": 7"));
        assertTrue(assetsJson.contains("\"sheetIndex\": 3"));
        assertTrue(assetsJson.contains("\"cellX\": 1"));
        assertTrue(assetsJson.contains("\"cellY\": 2"));
        assertFalse(assetsJson.contains("\"referenceCellWidth\""));
        assertFalse(assetsJson.contains("\"referenceCellHeight\""));
        assertFalse(assetsJson.contains("\"projection\""));
        assertFalse(assetsJson.contains("\"anchor\""));
        assertFalse(assetsJson.contains("\"offsetX\""));
        assertFalse(assetsJson.contains("\"offsetY\""));
        assertFalse(assetsJson.contains("\"renderSize\""));

        TileAssetMeta loadedTile = (TileAssetMeta) AssetMetaDatabase.load(file).findByLogicalPath("tiles/terrain/grass");
        assertEquals(7, loadedTile.tilesetId);
        assertEquals(3, loadedTile.sheetIndex);
        assertEquals(1, loadedTile.cellX);
        assertEquals(2, loadedTile.cellY);
    }
}
