package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.kotcrab.vis.ui.widget.VisValidatableTextField;

final class ImportDialogValidation {

    static final int MAX_IMAGE_SIZE = 2048;

    private ImportDialogValidation() {
    }

    interface DimensionReader {
        int[] read(FileHandle file);
    }

    static boolean isSupportedImportFile(FileHandle file) {
        if (file == null || !file.exists() || file.isDirectory()) return false;
        return isSupportedImage(file) || isParticleFile(file);
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

    static boolean isDivisibleForType(ImportDialog.ImportType type, int sheetDimension, String input) {
        if (!isSheetType(type)) return true;
        if (sheetDimension <= 0) return true;
        if (!isPositiveInteger(input)) return false;

        int tile = Integer.parseInt(input.trim());
        return sheetDimension % tile == 0;
    }

    static boolean hasDivisibilityIssue(ImportDialog.ImportItem item) {
        if (item == null || !isSheetType(item.type)) return false;
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

    private static int dimensionFromFieldOrValue(VisValidatableTextField field, int fallback) {
        if (field == null) return fallback;
        String text = field.getText();
        if (!isPositiveInteger(text)) return -1;
        return Integer.parseInt(text.trim());
    }
}
