package games.pixscape.studio.service.asset;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAssetMeta;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetAtlasImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetDirectoryImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetImportResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TilesetAssetImportServiceTest {

    private static HeadlessApplication app;

    @BeforeClass
    public static void startGdx() {
        if (Gdx.app == null) {
            app = new HeadlessApplication(new ApplicationAdapter() {
            }, new HeadlessApplicationConfiguration());
        }
    }

    @AfterClass
    public static void stopGdx() {
        if (app != null) {
            app.exit();
            app = null;
        }
    }

    @Test
    public void importAtlasRegistersTilesetAndTileMetadata() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-atlas-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("terrain.png");
        writePng(source, 32, 32, 0.1f, 0.6f, 0.2f, 1f);

        TilesetImportResult result = service.importAtlas(
                new TilesetAtlasImportRequest(source, tilesRoot, 16, 16)
        );

        assertEquals(1, result.importedCount());
        assertEquals("tiles/terrain", result.tilesetLogicalPath());

        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/terrain"));
        assertEquals(tileset.id, result.tilesetAssetId());
        assertEquals(32, tileset.imageWidth);
        assertEquals(32, tileset.imageHeight);
        assertEquals(16, tileset.tileWidth);
        assertEquals(16, tileset.tileHeight);
        assertEquals(2, tileset.columns);
        assertEquals(2, tileset.rows);
        assertEquals(0, tileset.spacing);
        assertEquals(0, tileset.margin);
        assertEquals("orig/tiles/terrain/terrain__a" + tileset.id + ".png", tileset.sourceRelPath);
        assertTrue(projectDir.child(tileset.sourceRelPath).exists());

        assertEquals(4, result.localTileAssetIds().size());
        for (int i = 0; i < 4; i++) {
            TileAssetMeta tile = requireTile(db.findByLogicalPath("tiles/terrain/" + i));
            assertEquals(tileset.id, tile.tilesetId);
            assertEquals(i, tile.sheetIndex);
            assertEquals(i % 2, tile.cellX);
            assertEquals(i / 2, tile.cellY);
            assertEquals(Integer.valueOf(tile.id), result.localTileAssetIds().get(i));
            assertEquals("orig/tiles/terrain/" + i + "__a" + tile.id + ".png", tile.sourceRelPath);
            assertTrue(projectDir.child(tile.sourceRelPath).exists());
        }
    }

    @Test
    public void importDirectoryRegistersTilesInNaturalOrder() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-folder-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle sourceDir = projectDir.child("terrain");
        sourceDir.mkdirs();

        writePng(sourceDir.child("tile10.png"), 10, 10, 0.9f, 0.1f, 0.1f, 1f);
        writePng(sourceDir.child("tile2.png"), 12, 12, 0.1f, 0.9f, 0.1f, 1f);
        writePng(sourceDir.child("tile1.png"), 14, 14, 0.1f, 0.1f, 0.9f, 1f);

        TilesetImportResult result = service.importDirectory(
                new TilesetDirectoryImportRequest(sourceDir, tilesRoot)
        );

        assertEquals(1, result.importedCount());
        assertEquals("tiles/terrain", result.tilesetLogicalPath());

        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/terrain"));
        assertEquals(tileset.id, result.tilesetAssetId());
        assertEquals(0, tileset.imageWidth);
        assertEquals(0, tileset.imageHeight);
        assertNull(tileset.sourceRelPath);
        assertEquals(10, tileset.tileWidth);
        assertEquals(10, tileset.tileHeight);
        assertEquals(3, tileset.columns);
        assertEquals(1, tileset.rows);
        assertEquals(0, tileset.spacing);
        assertEquals(0, tileset.margin);

        assertEquals(3, result.localTileAssetIds().size());
        Map<Integer, Integer> expectedSizes = Map.of(
                0, 14,
                1, 12,
                2, 10
        );

        for (int i = 0; i < 3; i++) {
            TileAssetMeta tile = requireTile(db.findByLogicalPath("tiles/terrain/" + i));
            assertEquals(tileset.id, tile.tilesetId);
            assertEquals(i, tile.sheetIndex);
            assertEquals(i, tile.cellX);
            assertEquals(0, tile.cellY);
            assertEquals(Integer.valueOf(tile.id), result.localTileAssetIds().get(i));
            assertEquals("orig/tiles/terrain/" + i + "__a" + tile.id + ".png", tile.sourceRelPath);

            FileHandle generated = projectDir.child(tile.sourceRelPath);
            assertTrue(generated.exists());
            assertPngSize(generated, expectedSizes.get(i), expectedSizes.get(i));
        }
    }

    private static TilesetAssetMeta requireTileset(AssetMeta meta) {
        assertNotNull(meta);
        assertTrue(meta instanceof TilesetAssetMeta);
        return (TilesetAssetMeta) meta;
    }

    private static TileAssetMeta requireTile(AssetMeta meta) {
        assertNotNull(meta);
        assertTrue(meta instanceof TileAssetMeta);
        return (TileAssetMeta) meta;
    }

    private static void writePng(FileHandle file,
                                 int width,
                                 int height,
                                 float r,
                                 float g,
                                 float b,
                                 float a) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(r, g, b, a);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static void assertPngSize(FileHandle file, int width, int height) {
        Pixmap pixmap = new Pixmap(file);
        try {
            assertEquals(width, pixmap.getWidth());
            assertEquals(height, pixmap.getHeight());
        } finally {
            pixmap.dispose();
        }
    }
}
