package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.TilesetAnchor;
import games.pixscape.studio.asset.TilesetRenderSize;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class ImportDialogValidationTest {

    @Test
    public void importItem_dimensionCaching_resolvesOnlyOnce() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("sheet.png"));
        final int[] calls = {0};

        ImportDialogValidation.DimensionReader reader = file -> {
            calls[0]++;
            return new int[]{128, 64};
        };

        item.resolveDimensionsIfNeeded(reader);
        item.resolveDimensionsIfNeeded(reader);

        assertEquals(1, calls[0]);
        assertEquals(128, item.imageWidth);
        assertEquals(64, item.imageHeight);
    }

    @Test
    public void importItem_tilesetSlicingDefaultsToCompactGrid() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));

        assertEquals(32, item.tileWidth);
        assertEquals(32, item.tileHeight);
        assertEquals(0, item.tileMargin);
        assertEquals(0, item.tileSpacing);
        assertEquals(32, item.referenceCellWidth);
        assertEquals(32, item.referenceCellHeight);
        assertSame(SceneMetaRuntime.TiledProjection.ORTHO, item.projection);
        assertSame(TilesetAnchor.TOP_CENTER, item.anchor);
        assertEquals(0, item.offsetX);
        assertEquals(0, item.offsetY);
        assertSame(TilesetRenderSize.NATIVE, item.renderSize);
    }

    @Test
    public void importValidation_tsxIsSupportedButNotImageOrParticle() throws Exception {
        File file = File.createTempFile("tileset", ".tsx");
        file.deleteOnExit();
        FileHandle handle = new FileHandle(file);

        assertTrue(ImportDialogValidation.isSupportedImportFile(handle));
        assertTrue(ImportDialogValidation.isTsxFile(handle));
        assertFalse(ImportDialogValidation.isSupportedImage(handle));
        assertFalse(ImportDialogValidation.isParticleFile(handle));
    }

    @Test
    public void importItem_applySlicingSettingsUpdatesTilesetValues() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));

        item.applySlicingSettings(16, 24, 2, 3);

        assertEquals(16, item.tileWidth);
        assertEquals(24, item.tileHeight);
        assertEquals(2, item.tileMargin);
        assertEquals(3, item.tileSpacing);
        assertEquals(16, item.referenceCellWidth);
        assertEquals(24, item.referenceCellHeight);
    }

    @Test
    public void importItem_applySlicingSettingsPreservesManualReferenceCell() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.referenceCellWidth = 40;
        item.referenceCellHeight = 44;

        item.applySlicingSettings(16, 24, 2, 3);

        assertEquals(40, item.referenceCellWidth);
        assertEquals(44, item.referenceCellHeight);
    }

    @Test
    public void importItem_profileSettingsReflectSelectedProfile() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));

        item.applyTilesetProfileSettings(
                16,
                24,
                2,
                3,
                64,
                32,
                SceneMetaRuntime.TiledProjection.ISO,
                TilesetAnchor.TOP_LEFT,
                -5,
                7,
                TilesetRenderSize.NATIVE
        );

        assertEquals(16, item.tileWidth);
        assertEquals(24, item.tileHeight);
        assertEquals(2, item.tileMargin);
        assertEquals(3, item.tileSpacing);
        assertEquals(64, item.tilesetProfileSettings().referenceCellWidth());
        assertEquals(32, item.tilesetProfileSettings().referenceCellHeight());
        assertSame(SceneMetaRuntime.TiledProjection.ISO, item.tilesetProfileSettings().projection());
        assertSame(TilesetAnchor.TOP_LEFT, item.tilesetProfileSettings().anchor());
        assertEquals(-5, item.tilesetProfileSettings().offsetX());
        assertEquals(7, item.tilesetProfileSettings().offsetY());
        assertSame(TilesetRenderSize.NATIVE, item.tilesetProfileSettings().renderSize());
    }

    @Test
    public void importItem_tilesetCompactProfileSummaryOmitsInspectorDetails() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.tileWidth = 32;
        item.tileHeight = 24;
        item.tileMargin = 2;
        item.tileSpacing = 3;
        item.referenceCellWidth = 48;
        item.referenceCellHeight = 32;
        item.projection = SceneMetaRuntime.TiledProjection.ISO;
        item.anchor = TilesetAnchor.TOP_LEFT;
        item.offsetX = -5;
        item.offsetY = 7;

        String summary = ImportDialog.formatTilesetCompactProfileSummary(item);

        assertEquals("Tile 32×24 · Ref 48×32", summary);
        assertFalse(summary.contains("margin"));
        assertFalse(summary.contains("spacing"));
        assertFalse(summary.contains("Isometric"));
        assertFalse(summary.contains("Top left"));
        assertFalse(summary.contains("offset"));
        assertFalse(summary.contains("native"));
    }

    @Test
    public void importItem_tilesetCompactProfileSummaryShowsShortInvalidWarning() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;
        item.referenceCellWidth = 0;

        assertEquals("Invalid profile", ImportDialog.formatTilesetCompactProfileSummary(item));
    }

    @Test
    public void spinnerValidation_positiveIntegerRule() {
        assertTrue(ImportDialogValidation.isPositiveInteger("1"));
        assertTrue(ImportDialogValidation.isPositiveInteger("32"));
        assertTrue(ImportDialogValidation.isPositiveInteger(" 7 "));
        assertFalse(ImportDialogValidation.isPositiveInteger("0"));
        assertFalse(ImportDialogValidation.isPositiveInteger("-2"));
        assertFalse(ImportDialogValidation.isPositiveInteger("abc"));
    }

    @Test
    public void spinnerValidation_maxSizeRule() {
        assertTrue(ImportDialogValidation.isPositiveIntegerWithinMaxSize("1"));
        assertTrue(ImportDialogValidation.isPositiveIntegerWithinMaxSize("2048"));
        assertFalse(ImportDialogValidation.isPositiveIntegerWithinMaxSize("2049"));
        assertFalse(ImportDialogValidation.isPositiveIntegerWithinMaxSize("abc"));
    }

    @Test
    public void spinnerValidation_divisibilityForSheets() {
        assertTrue(ImportDialogValidation.isDivisibleForType(ImportDialog.ImportType.TILESET, 96, "32"));
        assertTrue(ImportDialogValidation.isDivisibleForType(ImportDialog.ImportType.TILESET, 100, "32"));
        assertFalse(ImportDialogValidation.isDivisibleForType(ImportDialog.ImportType.SPRITESHEET, 100, "32"));
        assertTrue(ImportDialogValidation.isDivisibleForType(ImportDialog.ImportType.IMAGE, 100, "32"));
    }

    @Test
    public void importValidation_divisibilityIssueDetectedForSheetItems() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("sheet.png"));
        item.type = ImportDialog.ImportType.SPRITESHEET;
        item.imageWidth = 100;
        item.imageHeight = 64;
        item.tileWidth = 32;
        item.tileHeight = 16;

        assertTrue(ImportDialogValidation.hasDivisibilityIssue(item));

        item.tileWidth = 20;
        assertFalse(ImportDialogValidation.hasDivisibilityIssue(item));
    }

    @Test
    public void importValidation_noDivisibilityIssueWhenDimensionsUnknown() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("sheet.png"));
        item.type = ImportDialog.ImportType.TILESET;
        item.imageWidth = -1;
        item.imageHeight = -1;
        item.tileWidth = 17;
        item.tileHeight = 19;

        assertFalse(ImportDialogValidation.hasDivisibilityIssue(item));
    }

    @Test
    public void importValidation_tilesetAllowsNonDivisibleImageWhenTilesFit() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;
        item.imageWidth = 8;
        item.imageHeight = 8;
        item.tileWidth = 2;
        item.tileHeight = 2;
        item.tileMargin = 1;
        item.tileSpacing = 1;

        ImportDialogValidation.TilesetSlicingPreview preview = ImportDialogValidation.calculateTilesetSlicing(item);

        assertFalse(ImportDialogValidation.hasDivisibilityIssue(item));
        assertFalse(ImportDialogValidation.hasInvalidTilesetSlicingSettings(item));
        assertFalse(ImportDialogValidation.hasTilesetSlicingIssue(item));
        assertEquals(2, preview.columns());
        assertEquals(2, preview.rows());
        assertEquals(4, preview.tileCount());
        assertEquals(2, preview.unusedRightPixels());
        assertEquals(2, preview.unusedBottomPixels());
    }

    @Test
    public void importValidation_spritesheetStillRequiresDivisibility() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("sheet.png"));
        item.type = ImportDialog.ImportType.SPRITESHEET;
        item.imageWidth = 100;
        item.imageHeight = 64;
        item.tileWidth = 32;
        item.tileHeight = 16;

        assertTrue(ImportDialogValidation.hasDivisibilityIssue(item));
    }

    @Test
    public void importValidation_tilesetRejectsInvalidSlicingSettings() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;
        item.imageWidth = 8;
        item.imageHeight = 8;
        item.tileWidth = 2;
        item.tileHeight = 2;

        item.tileMargin = -1;
        assertTrue(ImportDialogValidation.hasInvalidTilesetSlicingSettings(item));

        item.tileMargin = 0;
        item.tileSpacing = -1;
        assertTrue(ImportDialogValidation.hasInvalidTilesetSlicingSettings(item));

        item.tileSpacing = 0;
        item.tileWidth = ImportDialogValidation.MAX_IMAGE_SIZE + 1;
        assertTrue(ImportDialogValidation.hasInvalidTilesetSlicingSettings(item));
    }

    @Test
    public void importValidation_tilesetRejectsInvalidProfileSettings() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;

        assertFalse(ImportDialogValidation.hasInvalidTilesetProfileSettings(item));

        item.referenceCellWidth = 0;
        assertTrue(ImportDialogValidation.hasInvalidTilesetProfileSettings(item));

        item.referenceCellWidth = 16;
        item.referenceCellHeight = ImportDialogValidation.MAX_IMAGE_SIZE + 1;
        assertFalse(ImportDialogValidation.hasInvalidTilesetProfileSettings(item));

        item.referenceCellHeight = 16;
        item.offsetX = -ImportDialogValidation.MAX_TILESET_OFFSET;
        item.offsetY = ImportDialogValidation.MAX_TILESET_OFFSET;
        assertFalse(ImportDialogValidation.hasInvalidTilesetProfileSettings(item));

        item.offsetX = -ImportDialogValidation.MAX_TILESET_OFFSET - 1;
        assertTrue(ImportDialogValidation.hasInvalidTilesetProfileSettings(item));

        item.offsetX = 0;
        item.projection = null;
        assertTrue(ImportDialogValidation.hasInvalidTilesetProfileSettings(item));

        item.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        item.anchor = null;
        assertTrue(ImportDialogValidation.hasInvalidTilesetProfileSettings(item));
    }

    @Test
    public void spinnerValidation_offsetRangeAllowsNegativeValues() {
        assertTrue(ImportDialogValidation.isIntegerWithinTilesetOffsetRange("-4096"));
        assertTrue(ImportDialogValidation.isIntegerWithinTilesetOffsetRange("0"));
        assertTrue(ImportDialogValidation.isIntegerWithinTilesetOffsetRange("4096"));
        assertFalse(ImportDialogValidation.isIntegerWithinTilesetOffsetRange("-4097"));
        assertFalse(ImportDialogValidation.isIntegerWithinTilesetOffsetRange("4097"));
        assertFalse(ImportDialogValidation.isIntegerWithinTilesetOffsetRange("abc"));
    }

    @Test
    public void importValidation_tilesetRejectsWhenNoTileFits() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("tiles.png"));
        item.type = ImportDialog.ImportType.TILESET;
        item.imageWidth = 8;
        item.imageHeight = 8;
        item.tileWidth = 9;
        item.tileHeight = 2;
        item.tileMargin = 0;
        item.tileSpacing = 0;

        assertFalse(ImportDialogValidation.hasInvalidTilesetSlicingSettings(item));
        assertTrue(ImportDialogValidation.hasTilesetSlicingIssue(item));
        assertFalse(ImportDialogValidation.calculateTilesetSlicing(item).hasTiles());
    }

    @Test
    public void importValidation_sheetSourceMayExceedMaxWhenGeneratedCellsFit() throws Exception {
        ImportDialog.ImportItem item = pngItem("large-sheet");
        item.type = ImportDialog.ImportType.TILESET;
        item.imageWidth = 4096;
        item.imageHeight = 4096;
        item.tileWidth = 32;
        item.tileHeight = 32;

        assertFalse(ImportDialogValidation.exceedsMaxImageSize(item));
    }

    @Test
    public void importValidation_sheetGeneratedCellCannotExceedMax() throws Exception {
        ImportDialog.ImportItem item = pngItem("large-tile");
        item.type = ImportDialog.ImportType.SPRITESHEET;
        item.imageWidth = 4096;
        item.imageHeight = 4096;
        item.tileWidth = ImportDialogValidation.MAX_IMAGE_SIZE + 1;
        item.tileHeight = 32;

        assertTrue(ImportDialogValidation.exceedsMaxImageSize(item));

        item.tileWidth = 32;
        item.tileHeight = ImportDialogValidation.MAX_IMAGE_SIZE + 1;

        assertTrue(ImportDialogValidation.exceedsMaxImageSize(item));
    }

    @Test
    public void importValidation_imageSourceCannotExceedMax() throws Exception {
        ImportDialog.ImportItem item = pngItem("large-image");
        item.type = ImportDialog.ImportType.IMAGE;
        item.imageWidth = ImportDialogValidation.MAX_IMAGE_SIZE + 1;
        item.imageHeight = 64;

        assertTrue(ImportDialogValidation.exceedsMaxImageSize(item));

        item.imageWidth = ImportDialogValidation.MAX_IMAGE_SIZE;
        item.imageHeight = ImportDialogValidation.MAX_IMAGE_SIZE;

        assertFalse(ImportDialogValidation.exceedsMaxImageSize(item));
    }

    @Test
    public void importItem_dimensionResolution_failureIsSafe() {
        ImportDialog.ImportItem item = new ImportDialog.ImportItem(new FileHandle("broken.png"));
        final int[] calls = {0};

        item.resolveDimensionsIfNeeded(file -> {
            calls[0]++;
            throw new RuntimeException("decode failure");
        });
        item.resolveDimensionsIfNeeded(file -> {
            calls[0]++;
            return new int[]{64, 64};
        });

        assertEquals(1, calls[0]);
        assertEquals(-1, item.imageWidth);
        assertEquals(-1, item.imageHeight);
    }

    private static ImportDialog.ImportItem pngItem(String prefix) throws Exception {
        File file = File.createTempFile(prefix, ".png");
        file.deleteOnExit();
        return new ImportDialog.ImportItem(new FileHandle(file));
    }
}
