package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;
import games.pixscape.studio.asset.TilesetRenderSize;
import games.pixscape.studio.service.asset.TilesetSliceLayout;

final class ImportDialogValidation {

    static final int MAX_IMAGE_SIZE = 2048;
    static final int MAX_TILESET_OFFSET = 4096;

    private ImportDialogValidation() {
    }

    interface DimensionReader {
        int[] read(FileHandle file);
    }

    static boolean isSupportedImportFile(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return false;
        return isSupportedImage(file) || isParticleFile(file) || isTsxFile(file);
    }

    static boolean isSupportedImage(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return false;
        String ext = file.extension() == null ? "" : file.extension().toLowerCase();
        return "png".equals(ext);
    }

    static boolean isParticleFile(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return false;
        String ext = file.extension() == null ? "" : file.extension().toLowerCase();
        return "p".equals(ext);
    }

    static boolean isTsxFile(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return false;
        String ext = file.extension() == null ? "" : file.extension().toLowerCase();
        return "tsx".equals(ext);
    }

    static int[] readImageDimensions(FileHandle file) {
        if (!isSupportedImage(file)) return null;

        Pixmap pixmap;
        try {
            pixmap = new Pixmap(file);
        } catch (RuntimeException ignored) {
            return null;
        }

        try {
            return new int[]{pixmap.getWidth(), pixmap.getHeight()};
        } finally {
            pixmap.dispose();
        }
    }

    static boolean isSheetType(ImportDialog.ImportType type) {
        return type == ImportDialog.ImportType.SPRITESHEET
                || type == ImportDialog.ImportType.TILESET;
    }

    static boolean isPositiveInteger(String input) {
        if (input == null) return false;
        String normalized = input.trim();
        if (normalized.isEmpty()) return false;

        try {
            return Integer.parseInt(normalized) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static boolean isPositiveIntegerWithinMaxSize(String input) {
        if (!isPositiveInteger(input)) return false;
        return Integer.parseInt(input.trim()) <= MAX_IMAGE_SIZE;
    }

    static boolean isNonNegativeInteger(String input) {
        if (input == null) return false;
        String normalized = input.trim();
        if (normalized.isEmpty()) return false;

        try {
            return Integer.parseInt(normalized) >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static boolean isNonNegativeIntegerWithinMaxSize(String input) {
        if (!isNonNegativeInteger(input)) return false;
        return Integer.parseInt(input.trim()) <= MAX_IMAGE_SIZE;
    }

    static boolean isIntegerWithinTilesetOffsetRange(String input) {
        if (input == null) return false;
        String normalized = input.trim();
        if (normalized.isEmpty()) return false;

        try {
            int value = Integer.parseInt(normalized);
            return value >= -MAX_TILESET_OFFSET && value <= MAX_TILESET_OFFSET;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static boolean isDivisibleForType(ImportDialog.ImportType type, int sheetDimension, String input) {
        if (type != ImportDialog.ImportType.SPRITESHEET) return true;
        if (sheetDimension <= 0) return true;
        if (!isPositiveInteger(input)) return false;

        int tile = Integer.parseInt(input.trim());
        return sheetDimension % tile == 0;
    }

    static boolean hasDivisibilityIssue(ImportDialog.ImportItem item) {
        if (item == null || item.type != ImportDialog.ImportType.SPRITESHEET) return false;
        if (item.imageWidth <= 0 || item.imageHeight <= 0) return false;

        String widthText = item.tileWidthField != null
                ? item.tileWidthField.getText()
                : Integer.toString(item.tileWidth);

        String heightText = item.tileHeightField != null
                ? item.tileHeightField.getText()
                : Integer.toString(item.tileHeight);

        return !isDivisibleForType(item.type, item.imageWidth, widthText)
                || !isDivisibleForType(item.type, item.imageHeight, heightText);
    }

    static boolean hasInvalidTilesetSlicingSettings(ImportDialog.ImportItem item) {
        if (item == null || item.type != ImportDialog.ImportType.TILESET) return false;
        return !isPositiveWithinMax(item.tileWidth)
                || !isPositiveWithinMax(item.tileHeight)
                || !isNonNegativeWithinMax(item.tileMargin)
                || !isNonNegativeWithinMax(item.tileSpacing);
    }

    static boolean hasInvalidTilesetProfileSettings(ImportDialog.ImportItem item) {
        if (item == null || item.type != ImportDialog.ImportType.TILESET) return false;
        return item.referenceCellWidth <= 0
                || item.referenceCellHeight <= 0
                || item.projection == null
                || item.anchor == null
                || item.renderSize != TilesetRenderSize.NATIVE
                || item.offsetX < -MAX_TILESET_OFFSET
                || item.offsetX > MAX_TILESET_OFFSET
                || item.offsetY < -MAX_TILESET_OFFSET
                || item.offsetY > MAX_TILESET_OFFSET;
    }

    static boolean hasTilesetSlicingIssue(ImportDialog.ImportItem item) {
        if (item == null || item.type != ImportDialog.ImportType.TILESET) return false;
        if (item.imageWidth <= 0 || item.imageHeight <= 0) return false;
        return !calculateTilesetSlicing(
                item.imageWidth,
                item.imageHeight,
                item.tileWidth,
                item.tileHeight,
                item.tileSpacing,
                item.tileMargin
        ).hasTiles();
    }

    static TilesetSlicingPreview calculateTilesetSlicing(ImportDialog.ImportItem item) {
        if (item == null) return TilesetSlicingPreview.invalid();
        return calculateTilesetSlicing(
                item.imageWidth,
                item.imageHeight,
                item.tileWidth,
                item.tileHeight,
                item.tileSpacing,
                item.tileMargin
        );
    }

    static TilesetSlicingPreview calculateTilesetSlicing(int imageWidth,
                                                         int imageHeight,
                                                         int tileWidth,
                                                         int tileHeight,
                                                         int spacing,
                                                         int margin) {
        if (imageWidth <= 0 || imageHeight <= 0) return TilesetSlicingPreview.invalid();
        if (!isPositiveWithinMax(tileWidth) || !isPositiveWithinMax(tileHeight)) return TilesetSlicingPreview.invalid();
        if (!isNonNegativeWithinMax(margin) || !isNonNegativeWithinMax(spacing)) return TilesetSlicingPreview.invalid();

        TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(
                imageWidth,
                imageHeight,
                tileWidth,
                tileHeight,
                spacing,
                margin
        );
        if (!layout.hasTiles()) {
            return new TilesetSlicingPreview(imageWidth, imageHeight, 0, 0, 0, 0, false);
        }

        return new TilesetSlicingPreview(
                imageWidth,
                imageHeight,
                layout.columns(),
                layout.rows(),
                layout.unusedRightPixels(),
                layout.unusedBottomPixels(),
                true
        );
    }

    static boolean hasInvalidImageDimensions(ImportDialog.ImportItem item) {
        if (item == null || item.file == null) return false;
        if (!isSupportedImage(item.file)) return false;
        return item.imageWidth <= 0 || item.imageHeight <= 0;
    }

    static boolean exceedsMaxImageSize(ImportDialog.ImportItem item) {
        if (item == null || item.file == null) return false;
        if (!isSupportedImage(item.file)) return false;

        if (!isSheetType(item.type)) {
            if (item.imageWidth <= 0 || item.imageHeight <= 0) return false;
            return item.imageWidth > MAX_IMAGE_SIZE || item.imageHeight > MAX_IMAGE_SIZE;
        }

        int tileWidth = dimensionFromFieldOrValue(item.tileWidthField, item.tileWidth);
        int tileHeight = dimensionFromFieldOrValue(item.tileHeightField, item.tileHeight);
        if (tileWidth <= 0 || tileHeight <= 0) return false;

        return tileWidth > MAX_IMAGE_SIZE || tileHeight > MAX_IMAGE_SIZE;
    }

    private static boolean isPositiveWithinMax(int value) {
        return value > 0 && value <= MAX_IMAGE_SIZE;
    }

    private static boolean isNonNegativeWithinMax(int value) {
        return value >= 0 && value <= MAX_IMAGE_SIZE;
    }

    private static int dimensionFromFieldOrValue(VisValidatableTextField field, int fallback) {
        if (field == null) return fallback;
        String text = field.getText();
        if (!isPositiveInteger(text)) return -1;
        return Integer.parseInt(text.trim());
    }

    record TilesetSlicingPreview(int imageWidth,
                                 int imageHeight,
                                 int columns,
                                 int rows,
                                 int unusedRightPixels,
                                 int unusedBottomPixels,
                                 boolean validSettings) {
        static TilesetSlicingPreview invalid() {
            return new TilesetSlicingPreview(0, 0, 0, 0, 0, 0, false);
        }

        int tileCount() {
            return columns * rows;
        }

        boolean hasTiles() {
            return validSettings && columns > 0 && rows > 0;
        }
    }
}
