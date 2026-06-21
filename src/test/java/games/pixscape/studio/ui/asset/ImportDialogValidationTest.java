package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
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
