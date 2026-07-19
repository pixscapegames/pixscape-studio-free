package games.pixscape.studio.service.asset;

public final class TilesetSliceLayout {

    private TilesetSliceLayout() {
    }

    public static Layout calculate(int imageWidth,
                                   int imageHeight,
                                   int tileWidth,
                                   int tileHeight,
                                   int spacing,
                                   int margin) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return Layout.invalid(imageWidth, imageHeight, "invalid image size");
        }
        if (tileWidth <= 0 || tileHeight <= 0) {
            return Layout.invalid(imageWidth, imageHeight, "tile size must be > 0");
        }
        if (spacing < 0 || margin < 0) {
            return Layout.invalid(imageWidth, imageHeight, "spacing and margin must be >= 0");
        }

        int columns = countTilesOnAxis(imageWidth, tileWidth, spacing, margin);
        int rows = countTilesOnAxis(imageHeight, tileHeight, spacing, margin);
        if (columns <= 0 || rows <= 0) {
            return Layout.invalid(imageWidth, imageHeight, "no tile fits");
        }

        int lastTileRight = margin + ((columns - 1) * (tileWidth + spacing)) + tileWidth;
        int lastTileBottom = margin + ((rows - 1) * (tileHeight + spacing)) + tileHeight;
        return new Layout(
                true,
                null,
                imageWidth,
                imageHeight,
                tileWidth,
                tileHeight,
                spacing,
                margin,
                columns,
                rows,
                Math.max(0, imageWidth - lastTileRight),
                Math.max(0, imageHeight - lastTileBottom)
        );
    }

    private static int countTilesOnAxis(int imageSize, int tileSize, int spacing, int margin) {
        int count = 0;
        for (int position = margin; position <= imageSize - tileSize; position += tileSize + spacing) {
            count++;
        }
        return count;
    }

    public record Layout(boolean valid,
                         String invalidReason,
                         int imageWidth,
                         int imageHeight,
                         int tileWidth,
                         int tileHeight,
                         int spacing,
                         int margin,
                         int columns,
                         int rows,
                         int unusedRightPixels,
                         int unusedBottomPixels) {
        static Layout invalid(int imageWidth, int imageHeight, String reason) {
            return new Layout(false, reason, imageWidth, imageHeight, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public int tileCount() {
            return columns * rows;
        }

        public boolean hasTiles() {
            return valid && columns > 0 && rows > 0;
        }

        public SourceRect sourceRect(int tileIndex) {
            if (!hasTiles()) {
                return SourceRect.invalid(invalidReason != null ? invalidReason : "invalid slicing");
            }
            if (tileIndex < 0 || tileIndex >= tileCount()) {
                return SourceRect.invalid("tile index out of bounds");
            }

            int column = tileIndex % columns;
            int row = tileIndex / columns;
            int x = margin + column * (tileWidth + spacing);
            int y = margin + row * (tileHeight + spacing);
            if (x < 0 || y < 0 || x + tileWidth > imageWidth || y + tileHeight > imageHeight) {
                return SourceRect.invalid("tile index outside image");
            }

            return new SourceRect(true, null, x, y, tileWidth, tileHeight, tileIndex);
        }

        public SourceRect clampedSourceRect(int tileIndex) {
            if (!hasTiles()) {
                return SourceRect.invalid(invalidReason != null ? invalidReason : "invalid slicing");
            }
            int clamped = Math.max(0, Math.min(tileIndex, tileCount() - 1));
            return sourceRect(clamped);
        }
    }

    public record SourceRect(boolean valid,
                             String invalidReason,
                             int x,
                             int y,
                             int width,
                             int height,
                             int tileIndex) {
        static SourceRect invalid(String reason) {
            return new SourceRect(false, reason, 0, 0, 0, 0, -1);
        }
    }
}
