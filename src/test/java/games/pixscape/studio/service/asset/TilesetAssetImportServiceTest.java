package games.pixscape.studio.service.asset;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAnchor;
import games.pixscape.studio.asset.TilesetAssetMeta;
import games.pixscape.studio.asset.TilesetRenderSize;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.asset.TilesetAssetImportService.ImageCollectionTileSource;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetAtlasImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetDirectoryImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetImageCollectionImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetImportResult;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetProfileImportSettings;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
                new TilesetAtlasImportRequest(source, tilesRoot, 16, 16, 0, 0)
        );

        assertEquals(1, result.importedCount());
        assertEquals("tiles/terrain", result.tilesetLogicalPath());

        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/terrain"));
        assertEquals(tileset.id(), result.tilesetAssetId());
        assertEquals(32, tileset.imageWidth);
        assertEquals(32, tileset.imageHeight);
        assertEquals(16, tileset.tileWidth);
        assertEquals(16, tileset.tileHeight);
        assertEquals(2, tileset.columns);
        assertEquals(2, tileset.rows);
        assertEquals(0, tileset.spacing);
        assertEquals(0, tileset.margin);
        assertEquals(16, tileset.referenceCellWidth);
        assertEquals(16, tileset.referenceCellHeight);
        assertEquals(SceneMetaRuntime.TiledProjection.ORTHO, tileset.projection);
        assertEquals(TilesetAnchor.TOP_CENTER, tileset.anchor);
        assertEquals(0, tileset.offsetX);
        assertEquals(0, tileset.offsetY);
        assertEquals(TilesetRenderSize.NATIVE, tileset.renderSize);
        assertEquals("orig/tiles/terrain/terrain__a" + tileset.id() + ".png", tileset.sourceRelPath());
        assertTrue(projectDir.child(tileset.sourceRelPath()).exists());

        assertEquals(4, result.localTileAssetIds().size());
        for (int i = 0; i < 4; i++) {
            TileAssetMeta tile = requireTile(db.findByLogicalPath("tiles/terrain/" + i));
            assertEquals(tileset.id(), tile.tilesetId);
            assertEquals(i, tile.sheetIndex);
            assertEquals(i % 2, tile.cellX);
            assertEquals(i / 2, tile.cellY);
            assertEquals(Integer.valueOf(tile.id()), result.localTileAssetIds().get(i));
            assertEquals("orig/tiles/terrain/" + i + "__a" + tile.id() + ".png", tile.sourceRelPath());
            assertTrue(projectDir.child(tile.sourceRelPath()).exists());
        }
    }

    @Test
    public void importLargeAtlasRegistersUniqueIdsPathsAndIndexedLookups() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-large-atlas-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("large.png");
        writePng(source, 20, 20, 0.3f, 0.5f, 0.7f, 1f);

        TilesetImportResult result = service.importAtlas(
                new TilesetAtlasImportRequest(source, tilesRoot, 1, 1, 0, 0)
        );

        assertEquals(400, result.localTileAssetIds().size());
        assertEquals(401, db.size());
        for (int i = 0; i < 400; i++) {
            TileAssetMeta tile = requireTile(db.findByLogicalPath("tiles/large/" + i));
            assertEquals(i + 2, tile.id());
            assertEquals(i, tile.sheetIndex);
            assertEquals(Integer.valueOf(tile.id()), result.localTileAssetIds().get(i));
            assertEquals(tile.id(), db.getIdBySourceRelPath(tile.sourceRelPath()));
        }
    }

    @Test
    public void importAtlasStoresSelectedTilesetProfileMetadata() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-atlas-profile-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("terrain.png");
        writePng(source, 32, 32, 0.2f, 0.3f, 0.8f, 1f);

        service.importAtlas(
                new TilesetAtlasImportRequest(
                        source,
                        tilesRoot,
                        16,
                        16,
                        0,
                        0,
                        null,
                        new TilesetProfileImportSettings(
                                64,
                                32,
                                SceneMetaRuntime.TiledProjection.ISO,
                                TilesetAnchor.BOTTOM_LEFT,
                                -6,
                                9,
                                TilesetRenderSize.NATIVE
                        )
                )
        );

        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/terrain"));
        assertEquals(64, tileset.referenceCellWidth);
        assertEquals(32, tileset.referenceCellHeight);
        assertEquals(SceneMetaRuntime.TiledProjection.ISO, tileset.projection);
        assertEquals(TilesetAnchor.BOTTOM_LEFT, tileset.anchor);
        assertEquals(-6, tileset.offsetX);
        assertEquals(9, tileset.offsetY);
        assertEquals(TilesetRenderSize.NATIVE, tileset.renderSize);
    }

    @Test
    public void importAtlasPersistsSelectedTilesetProfileMetadata() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-atlas-profile-persist");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("terrain.png");
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        writePng(source, 32, 32, 0.2f, 0.3f, 0.8f, 1f);

        service.importAtlas(
                new TilesetAtlasImportRequest(
                        source,
                        tilesRoot,
                        16,
                        16,
                        0,
                        0,
                        null,
                        new TilesetProfileImportSettings(
                                64,
                                32,
                                SceneMetaRuntime.TiledProjection.ISO,
                                TilesetAnchor.TOP_CENTER,
                                -6,
                                9,
                                TilesetRenderSize.NATIVE
                        )
                )
        );

        db.save(assetsFile);

        String assetsJson = assetsFile.readString(StandardCharsets.UTF_8.name());
        assertTrue(assetsJson.contains("\"referenceCellWidth\": 64"));
        assertTrue(assetsJson.contains("\"referenceCellHeight\": 32"));
        assertTrue(assetsJson.contains("\"projection\": \"isometric\""));
        assertTrue(assetsJson.contains("\"anchor\": \"top-center\""));
        assertTrue(assetsJson.contains("\"offsetX\": -6"));
        assertTrue(assetsJson.contains("\"offsetY\": 9"));
        assertTrue(assetsJson.contains("\"renderSize\": \"native\""));

        TilesetAssetMeta loaded = requireTileset(
                AssetMetaDatabase.load(assetsFile).findByLogicalPath("tiles/terrain")
        );
        assertEquals(64, loaded.referenceCellWidth);
        assertEquals(32, loaded.referenceCellHeight);
        assertEquals(SceneMetaRuntime.TiledProjection.ISO, loaded.projection);
        assertEquals(TilesetAnchor.TOP_CENTER, loaded.anchor);
        assertEquals(-6, loaded.offsetX);
        assertEquals(9, loaded.offsetY);
        assertEquals(TilesetRenderSize.NATIVE, loaded.renderSize);
    }

    @Test
    public void importAtlasRejectsInvalidTilesetProfileMetadata() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-atlas-invalid-profile-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("terrain.png");
        writePng(source, 32, 32, 0.2f, 0.3f, 0.8f, 1f);

        try {
            service.importAtlas(
                    new TilesetAtlasImportRequest(
                            source,
                            tilesRoot,
                            16,
                            16,
                            0,
                            0,
                            null,
                            new TilesetProfileImportSettings(
                                    0,
                                    32,
                                    SceneMetaRuntime.TiledProjection.ORTHO,
                                    TilesetAnchor.BOTTOM_CENTER,
                                    0,
                                    0,
                                    TilesetRenderSize.NATIVE
                            )
                    )
            );
            fail("Expected invalid profile failure");
        } catch (IllegalArgumentException ex) {
            assertEquals("Reference cell size must be > 0", ex.getMessage());
        }
    }

    @Test
    public void importAtlasHonorsMarginAndSpacingWhenSlicing() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-atlas-import-spaced");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("terrain.png");

        int[] tileColors = {
                0xFF0000FF,
                0x00FF00FF,
                0x0000FFFF,
                0xFFFF00FF
        };
        writeSpacedAtlas(source, 8, 8, 2, 2, 1, 1, tileColors);

        TilesetImportResult result = service.importAtlas(
                new TilesetAtlasImportRequest(source, tilesRoot, 2, 2, 1, 1)
        );

        assertEquals(1, result.importedCount());
        assertEquals(4, result.localTileAssetIds().size());

        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/terrain"));
        assertEquals(8, tileset.imageWidth);
        assertEquals(8, tileset.imageHeight);
        assertEquals(2, tileset.tileWidth);
        assertEquals(2, tileset.tileHeight);
        assertEquals(2, tileset.columns);
        assertEquals(2, tileset.rows);
        assertEquals(1, tileset.spacing);
        assertEquals(1, tileset.margin);

        for (int i = 0; i < 4; i++) {
            TileAssetMeta tile = requireTile(db.findByLogicalPath("tiles/terrain/" + i));
            assertEquals(tileset.id(), tile.tilesetId);
            assertEquals(i, tile.sheetIndex);
            assertEquals(i % 2, tile.cellX);
            assertEquals(i / 2, tile.cellY);
            assertEquals(Integer.valueOf(tile.id()), result.localTileAssetIds().get(i));
            assertPngSize(projectDir.child(tile.sourceRelPath()), 2, 2);
            assertPngPixels(projectDir.child(tile.sourceRelPath()), tileColors[i]);
        }
    }

    @Test
    public void importTsxTilesetParsesDescriptorAndImportsAtlas() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-tsx-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("gfx").child("terrain.png");
        writeSpacedAtlas(source, 8, 8, 2, 2, 1, 1, new int[]{
                0xFF0000FF,
                0x00FF00FF,
                0x0000FFFF,
                0xFFFF00FF
        });
        FileHandle tsx = projectDir.child("tilesets").child("terrain.tsx");
        writeString(tsx, """
                <tileset name="Terrain TSX" tilewidth="2" tileheight="2" spacing="1" margin="1" tilecount="4" columns="2">
                  <image source="../gfx/terrain.png" width="8" height="8"/>
                </tileset>
                """);

        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(tsx);
        TilesetImportResult result = service.importAtlas(new TilesetAtlasImportRequest(
                descriptor.imageFile(),
                tilesRoot,
                descriptor.tileWidth(),
                descriptor.tileHeight(),
                descriptor.spacing(),
                descriptor.margin(),
                descriptor.name()
        ));

        assertEquals(1, result.importedCount());
        assertEquals("tiles/Terrain TSX", result.tilesetLogicalPath());
        assertEquals(4, result.localTileAssetIds().size());
        for (int i = 0; i < 4; i++) {
            assertTrue(result.localTileAssetIds().containsKey(i));
        }

        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/Terrain TSX"));
        assertEquals(8, tileset.imageWidth);
        assertEquals(8, tileset.imageHeight);
        assertEquals(2, tileset.tileWidth);
        assertEquals(2, tileset.tileHeight);
        assertEquals(2, tileset.columns);
        assertEquals(2, tileset.rows);
        assertEquals(1, tileset.spacing);
        assertEquals(1, tileset.margin);
        assertEquals(2, tileset.referenceCellWidth);
        assertEquals(2, tileset.referenceCellHeight);
        assertEquals(SceneMetaRuntime.TiledProjection.ORTHO, tileset.projection);
        assertEquals(TilesetAnchor.TOP_CENTER, tileset.anchor);
        assertEquals(0, tileset.offsetX);
        assertEquals(0, tileset.offsetY);
        assertEquals(TilesetRenderSize.NATIVE, tileset.renderSize);
    }

    @Test
    public void importTsxTilesetPersistsProfileDefaults() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-tsx-profile-persist");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle source = projectDir.child("gfx").child("terrain.png");
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        writePng(source, 16, 16, 0.2f, 0.3f, 0.8f, 1f);
        FileHandle tsx = projectDir.child("tilesets").child("terrain.tsx");
        writeString(tsx, """
                <tileset name="Terrain TSX" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                  <image source="../gfx/terrain.png" width="16" height="16"/>
                </tileset>
                """);

        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(tsx);
        service.importAtlas(new TilesetAtlasImportRequest(
                descriptor.imageFile(),
                tilesRoot,
                descriptor.tileWidth(),
                descriptor.tileHeight(),
                descriptor.spacing(),
                descriptor.margin(),
                descriptor.name()
        ));
        db.save(assetsFile);

        String assetsJson = assetsFile.readString(StandardCharsets.UTF_8.name());
        assertTrue(assetsJson.contains("\"referenceCellWidth\": 16"));
        assertTrue(assetsJson.contains("\"referenceCellHeight\": 16"));
        assertTrue(assetsJson.contains("\"projection\": \"orthogonal\""));
        assertTrue(assetsJson.contains("\"anchor\": \"top-center\""));
        assertTrue(assetsJson.contains("\"renderSize\": \"native\""));
    }

    @Test
    public void parseTsxMissingImageFailsWithClearMessage() throws Exception {
        Path temp = Files.createTempDirectory("tileset-tsx-missing-image");
        FileHandle tsx = new FileHandle(temp.resolve("terrain.tsx").toFile());
        writeString(tsx, """
                <tileset name="Terrain" tilewidth="16" tileheight="16">
                  <image source="missing.png" width="16" height="16"/>
                </tileset>
                """);

        assertTsxImportError(tsx, "TSX tileset image is missing: missing.png");
    }

    @Test
    public void parseTsxImageCollectionReadsPerTileImages() throws Exception {
        Path temp = Files.createTempDirectory("tileset-tsx-image-collection");
        FileHandle tsx = new FileHandle(temp.resolve("terrain.tsx").toFile());
        writePng(new FileHandle(temp.resolve("tile0.png").toFile()), 16, 32, 0.9f, 0.1f, 0.1f, 1f);
        writeString(tsx, """
                <tileset name="Terrain" tilewidth="16" tileheight="16">
                  <tile id="0"><image source="tile0.png" width="16" height="16"/></tile>
                </tileset>
                """);

        TsxTilesetDescriptor descriptor = new TsxTilesetImportParser().parse(tsx);

        assertTrue(descriptor.imageCollection());
        assertEquals(1, descriptor.tileCount());
        assertEquals(1, descriptor.imageCollectionTiles().size());
        assertEquals(0, descriptor.imageCollectionTiles().get(0).localTileId());
        assertEquals("tile0.png", descriptor.imageCollectionTiles().get(0).imageSource());
    }

    @Test
    public void parseInvalidTsxFailsWithClearMessage() throws Exception {
        Path temp = Files.createTempDirectory("tileset-tsx-invalid");
        FileHandle wrongRoot = new FileHandle(temp.resolve("wrong.tsx").toFile());
        writeString(wrongRoot, "<map></map>");
        assertTsxImportError(wrongRoot, "TSX file is invalid.");

        FileHandle invalidXml = new FileHandle(temp.resolve("invalid.tsx").toFile());
        writeString(invalidXml, "<tileset>");
        assertTsxImportError(invalidXml, "TSX file is invalid.");
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
        assertEquals(tileset.id(), result.tilesetAssetId());
        assertEquals(0, tileset.imageWidth);
        assertEquals(0, tileset.imageHeight);
        assertNull(tileset.sourceRelPath());
        assertEquals(10, tileset.tileWidth);
        assertEquals(10, tileset.tileHeight);
        assertEquals(3, tileset.columns);
        assertEquals(1, tileset.rows);
        assertEquals(0, tileset.spacing);
        assertEquals(0, tileset.margin);
        assertEquals(10, tileset.referenceCellWidth);
        assertEquals(10, tileset.referenceCellHeight);
        assertEquals(SceneMetaRuntime.TiledProjection.ORTHO, tileset.projection);
        assertEquals(TilesetAnchor.TOP_CENTER, tileset.anchor);
        assertEquals(0, tileset.offsetX);
        assertEquals(0, tileset.offsetY);
        assertEquals(TilesetRenderSize.NATIVE, tileset.renderSize);

        assertEquals(3, result.localTileAssetIds().size());
        Map<Integer, Integer> expectedSizes = Map.of(
                0, 14,
                1, 12,
                2, 10
        );

        for (int i = 0; i < 3; i++) {
            TileAssetMeta tile = requireTile(db.findByLogicalPath("tiles/terrain/" + i));
            assertEquals(tileset.id(), tile.tilesetId);
            assertEquals(i, tile.sheetIndex);
            assertEquals(i, tile.cellX);
            assertEquals(0, tile.cellY);
            assertEquals(Integer.valueOf(tile.id()), result.localTileAssetIds().get(i));
            assertEquals("orig/tiles/terrain/" + i + "__a" + tile.id() + ".png", tile.sourceRelPath());

            FileHandle generated = projectDir.child(tile.sourceRelPath());
            assertTrue(generated.exists());
            assertPngSize(generated, expectedSizes.get(i), expectedSizes.get(i));
        }
    }

    @Test
    public void importDirectoryStoresExplicitProfileSettings() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-folder-profile-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle sourceDir = projectDir.child("iso-terrain");
        sourceDir.mkdirs();

        writePng(sourceDir.child("tile0.png"), 256, 512, 0.9f, 0.1f, 0.1f, 1f);

        TilesetImportResult result = service.importDirectory(
                new TilesetDirectoryImportRequest(
                        sourceDir,
                        tilesRoot,
                        new TilesetProfileImportSettings(
                                256,
                                128,
                                SceneMetaRuntime.TiledProjection.ISO,
                                TilesetAnchor.BOTTOM_LEFT,
                                12,
                                -8,
                                TilesetRenderSize.NATIVE
                        )
                )
        );

        assertEquals(1, result.importedCount());
        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/iso-terrain"));
        assertEquals(256, tileset.tileWidth);
        assertEquals(512, tileset.tileHeight);
        assertEquals(256, tileset.referenceCellWidth);
        assertEquals(128, tileset.referenceCellHeight);
        assertEquals(SceneMetaRuntime.TiledProjection.ISO, tileset.projection);
        assertEquals(TilesetAnchor.BOTTOM_LEFT, tileset.anchor);
        assertEquals(12, tileset.offsetX);
        assertEquals(-8, tileset.offsetY);
        assertEquals(TilesetRenderSize.NATIVE, tileset.renderSize);
    }

    @Test
    public void importImageCollectionRegistersLocalTileImageAssetsAndDeduplicatesSources() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-image-collection-import");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle tree = projectDir.child("tree.png");
        FileHandle rock = projectDir.child("rock.png");
        writePng(tree, 16, 32, 0.1f, 0.6f, 0.2f, 1f);
        writePng(rock, 16, 16, 0.3f, 0.3f, 0.3f, 1f);

        TilesetImportResult result = service.importImageCollection(new TilesetImageCollectionImportRequest(
                "props",
                tilesRoot,
                16,
                16,
                3,
                List.of(
                        new ImageCollectionTileSource(0, tree, "tree.png", 16, 32),
                        new ImageCollectionTileSource(1, rock, "rock.png", 16, 16),
                        new ImageCollectionTileSource(2, tree, "tree.png", 16, 32)
                ),
                new TilesetProfileImportSettings(
                        16,
                        16,
                        SceneMetaRuntime.TiledProjection.ORTHO,
                        TilesetAnchor.BOTTOM_CENTER,
                        0,
                        0,
                        TilesetRenderSize.NATIVE
                )
        ));

        assertEquals(1, result.importedCount());
        assertEquals(3, result.localTileAssetIds().size());
        assertEquals(result.localTileAssetIds().get(0), result.localTileAssetIds().get(2));

        TilesetAssetMeta tileset = requireTileset(db.findByLogicalPath("tiles/props"));
        assertEquals(16, tileset.tileWidth);
        assertEquals(16, tileset.tileHeight);
        assertEquals(3, tileset.columns);
        assertEquals(1, tileset.rows);
        assertEquals(16, tileset.referenceCellWidth);
        assertEquals(16, tileset.referenceCellHeight);
        assertEquals(TilesetAnchor.BOTTOM_CENTER, tileset.anchor);

        TileAssetMeta treeTile = requireTile(db.findByLogicalPath("tiles/props/0"));
        TileAssetMeta rockTile = requireTile(db.findByLogicalPath("tiles/props/1"));
        assertEquals(tileset.id(), treeTile.tilesetId);
        assertEquals(0, treeTile.sheetIndex);
        assertEquals(tileset.id(), rockTile.tilesetId);
        assertEquals(1, rockTile.sheetIndex);
        assertPngSize(projectDir.child(treeTile.sourceRelPath()), 16, 32);
        assertPngSize(projectDir.child(rockTile.sourceRelPath()), 16, 16);
    }

    @Test
    public void importDirectoryPersistsExplicitProfileSettings() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetImportService service = new TilesetAssetImportService(db);

        Path temp = Files.createTempDirectory("tileset-folder-profile-persist");
        FileHandle projectDir = new FileHandle(temp.toFile());
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        FileHandle sourceDir = projectDir.child("iso-terrain");
        FileHandle assetsFile = projectDir.child(StudioFs.FILE_ASSETS_JSON);
        sourceDir.mkdirs();

        writePng(sourceDir.child("tile0.png"), 256, 512, 0.9f, 0.1f, 0.1f, 1f);

        service.importDirectory(
                new TilesetDirectoryImportRequest(
                        sourceDir,
                        tilesRoot,
                        new TilesetProfileImportSettings(
                                256,
                                128,
                                SceneMetaRuntime.TiledProjection.ISO,
                                TilesetAnchor.BOTTOM_CENTER,
                                12,
                                -8,
                                TilesetRenderSize.NATIVE
                        )
                )
        );
        db.save(assetsFile);

        String assetsJson = assetsFile.readString(StandardCharsets.UTF_8.name());
        assertTrue(assetsJson.contains("\"referenceCellWidth\": 256"));
        assertTrue(assetsJson.contains("\"referenceCellHeight\": 128"));
        assertTrue(assetsJson.contains("\"projection\": \"isometric\""));
        assertTrue(assetsJson.contains("\"anchor\": \"bottom-center\""));
        assertTrue(assetsJson.contains("\"offsetX\": 12"));
        assertTrue(assetsJson.contains("\"offsetY\": -8"));
        assertTrue(assetsJson.contains("\"renderSize\": \"native\""));
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

    private static void writeString(FileHandle file, String text) {
        file.parent().mkdirs();
        file.writeString(text, false, StandardCharsets.UTF_8.name());
    }

    private static void assertTsxImportError(FileHandle tsx, String expectedMessage) {
        try {
            new TsxTilesetImportParser().parse(tsx);
            fail("Expected TSX parser failure");
        } catch (IllegalArgumentException ex) {
            assertEquals(expectedMessage, ex.getMessage());
        }
    }

    private static void writeSpacedAtlas(FileHandle file,
                                         int width,
                                         int height,
                                         int tileWidth,
                                         int tileHeight,
                                         int spacing,
                                         int margin,
                                         int[] tileColors) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(0xFF00FFFF);
            pixmap.fill();
            int index = 0;
            for (int y = margin; y <= height - tileHeight; y += tileHeight + spacing) {
                for (int x = margin; x <= width - tileWidth; x += tileWidth + spacing) {
                    pixmap.setColor(tileColors[index++]);
                    pixmap.fillRectangle(x, y, tileWidth, tileHeight);
                }
            }
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

    private static void assertPngPixels(FileHandle file, int expectedRgba) {
        Pixmap pixmap = new Pixmap(file);
        try {
            for (int y = 0; y < pixmap.getHeight(); y++) {
                for (int x = 0; x < pixmap.getWidth(); x++) {
                    assertEquals(expectedRgba, pixmap.getPixel(x, y));
                }
            }
        } finally {
            pixmap.dispose();
        }
    }
}
