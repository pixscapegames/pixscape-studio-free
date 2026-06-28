package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAssetMeta;
import games.pixscape.studio.helper.AssetHelper;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.log.StudioLog;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TilesetAssetImportService {

    private final AssetMetaDatabase assetMetaDatabase;

    public TilesetAssetImportService(AssetMetaDatabase assetMetaDatabase) {
        this.assetMetaDatabase = Objects.requireNonNull(assetMetaDatabase, "assetMetaDatabase");
    }

    public TilesetImportResult importAtlas(TilesetAtlasImportRequest request) {
        Objects.requireNonNull(request, "request");

        FileHandle sourceFile = request.sourceFile();
        if (!isImage(sourceFile)) {
            warnUnsupported(sourceFile);
            return TilesetImportResult.skipped();
        }

        int tileW = request.tileWidth();
        int tileH = request.tileHeight();
        int spacing = request.spacing();
        int margin = request.margin();
        validateAtlasSlicing(tileW, tileH, spacing, margin);

        ImageSize size = readImageSize(sourceFile);
        int imageW = size.width;
        int imageH = size.height;
        AtlasGrid grid = calculateAtlasGrid(imageW, imageH, tileW, tileH, spacing, margin);

        String base = atlasBaseName(request);
        FileHandle tilesetDir = prepareTilesetDirectory(request.tilesRoot(), base);

        TilesetAssetMeta tilesetMeta = createOrUpdateTilesetMeta(
                base,
                imageW,
                imageH,
                tileW,
                tileH,
                grid.columns(),
                grid.rows(),
                spacing,
                margin
        );

        splitTilesetSource(sourceFile, tilesetDir, tileW, tileH, spacing, margin);
        Map<Integer, Integer> tileAssetIds = registerSplitTilesAsTileAssets(base, tilesetDir, grid.columns(), tilesetMeta);
        copyTilesetSourceFile(base, sourceFile, tilesetDir, tilesetMeta);

        return new TilesetImportResult(1, tilesetMeta.id, StudioFs.PREFIX_TILES + base, tileAssetIds);
    }

    public TilesetImportResult importDirectory(TilesetDirectoryImportRequest request) {
        Objects.requireNonNull(request, "request");

        FileHandle directory = request.directory();
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            StudioLog.warn("Tileset directory import failed: invalid directory.");
            return TilesetImportResult.skipped();
        }

        String base = baseName(directory.name());
        FileHandle tilesetDir = prepareTilesetDirectory(request.tilesRoot(), base);

        FileHandle[] sourceTiles = listTilesetFolderImages(directory);
        if (sourceTiles == null || sourceTiles.length == 0) {
            StudioLog.warn("Tileset directory import skipped: no PNG files found in " + directory.name());
            return TilesetImportResult.skipped();
        }

        FolderTilesetInfo info = analyzeFolderTileset(sourceTiles);

        TilesetAssetMeta tilesetMeta = createOrUpdateFolderTilesetMeta(
                base,
                sourceTiles.length,
                info.referenceTileWidth,
                info.referenceTileHeight
        );

        Map<Integer, Integer> tileAssetIds = registerFolderTilesAsTileAssets(
                base,
                tilesetDir,
                sourceTiles,
                tilesetMeta
        );

        return new TilesetImportResult(1, tilesetMeta.id, StudioFs.PREFIX_TILES + base, tileAssetIds);
    }

    public record TilesetAtlasImportRequest(FileHandle sourceFile,
                                            FileHandle tilesRoot,
                                            int tileWidth,
                                            int tileHeight,
                                            int spacing,
                                            int margin,
                                            String tilesetName) {
        public TilesetAtlasImportRequest(FileHandle sourceFile,
                                         FileHandle tilesRoot,
                                         int tileWidth,
                                         int tileHeight,
                                         int spacing,
                                         int margin) {
            this(sourceFile, tilesRoot, tileWidth, tileHeight, spacing, margin, null);
        }
    }

    public record TilesetDirectoryImportRequest(FileHandle directory,
                                                FileHandle tilesRoot) {
    }

    public record TilesetImportResult(int importedCount,
                                      int tilesetAssetId,
                                      String tilesetLogicalPath,
                                      Map<Integer, Integer> localTileAssetIds) {

        public TilesetImportResult {
            if (localTileAssetIds == null || localTileAssetIds.isEmpty()) {
                localTileAssetIds = Collections.emptyMap();
            } else {
                localTileAssetIds = Collections.unmodifiableMap(new LinkedHashMap<>(localTileAssetIds));
            }
        }

        static TilesetImportResult skipped() {
            return new TilesetImportResult(0, -1, null, Collections.emptyMap());
        }
    }

    private TilesetAssetMeta createOrUpdateFolderTilesetMeta(String base,
                                                             int tileCount,
                                                             int referenceTileWidth,
                                                             int referenceTileHeight) {
        String tilesetLogical = StudioFs.PREFIX_TILES + base;

        TilesetAssetMeta tilesetMeta = requireTilesetMeta(
                assetMetaDatabase.registerIfAbsent(
                        AssetType.TILESET,
                        tilesetLogical,
                        null,
                        AssetMeta.AssetScope.USER
                )
        );

        tilesetMeta.imageWidth = 0;
        tilesetMeta.imageHeight = 0;
        tilesetMeta.sourceRelPath = null;
        tilesetMeta.tileWidth = referenceTileWidth;
        tilesetMeta.tileHeight = referenceTileHeight;
        tilesetMeta.columns = Math.max(1, tileCount);
        tilesetMeta.rows = 1;
        tilesetMeta.spacing = 0;
        tilesetMeta.margin = 0;

        return tilesetMeta;
    }

    private Map<Integer, Integer> registerFolderTilesAsTileAssets(String base,
                                                                  FileHandle tilesetDir,
                                                                  FileHandle[] sourceTiles,
                                                                  TilesetAssetMeta tilesetMeta) {
        Map<Integer, Integer> tileAssetIds = new LinkedHashMap<>();
        if (sourceTiles == null) return tileAssetIds;

        for (int sheetIndex = 0; sheetIndex < sourceTiles.length; sheetIndex++) {
            FileHandle src = sourceTiles[sheetIndex];
            if (src == null || !src.exists() || src.isDirectory()) continue;

            String logical = StudioFs.PREFIX_TILES + base + "/" + sheetIndex;

            TileAssetMeta tileMeta = requireTileMeta(
                    assetMetaDatabase.registerIfAbsent(
                            AssetType.TILE,
                            logical,
                            null,
                            AssetMeta.AssetScope.USER
                    )
            );

            String ext = src.extension();
            String newFileName = sheetIndex + "__a" + tileMeta.id + "." + ext;
            FileHandle dst = tilesetDir.child(newFileName);

            if (!dst.exists()) {
                src.copyTo(dst);
            }

            tileMeta.sourceRelPath = StudioFs.DIR_ORIG_TILES + "/" + base + "/" + newFileName;
            tileMeta.tilesetId = tilesetMeta.id;
            tileMeta.sheetIndex = sheetIndex;
            tileMeta.cellX = sheetIndex;
            tileMeta.cellY = 0;
            tileAssetIds.put(sheetIndex, tileMeta.id);
        }
        return tileAssetIds;
    }

    private TilesetAssetMeta createOrUpdateTilesetMeta(String base,
                                                       int imageWidth,
                                                       int imageHeight,
                                                       int tileWidth,
                                                       int tileHeight,
                                                       int columns,
                                                       int rows,
                                                       int spacing,
                                                       int margin) {
        String tilesetLogical = StudioFs.PREFIX_TILES + base;

        TilesetAssetMeta tilesetMeta = requireTilesetMeta(
                assetMetaDatabase.registerIfAbsent(
                        AssetType.TILESET,
                        tilesetLogical,
                        null,
                        AssetMeta.AssetScope.USER
                )
        );

        tilesetMeta.imageWidth = imageWidth;
        tilesetMeta.imageHeight = imageHeight;
        tilesetMeta.tileWidth = tileWidth;
        tilesetMeta.tileHeight = tileHeight;
        tilesetMeta.columns = columns;
        tilesetMeta.rows = rows;
        tilesetMeta.spacing = spacing;
        tilesetMeta.margin = margin;

        return tilesetMeta;
    }

    private void splitTilesetSource(FileHandle sourceFile,
                                    FileHandle tilesetDir,
                                    int tileWidth,
                                    int tileHeight,
                                    int spacing,
                                    int margin) {
        splitGridImage(
                sourceFile,
                tilesetDir,
                tileWidth,
                tileHeight,
                spacing,
                margin,
                null
        );
    }

    private Map<Integer, Integer> registerSplitTilesAsTileAssets(String base,
                                                                 FileHandle tilesetDir,
                                                                 int columns,
                                                                 TilesetAssetMeta tilesetMeta) {
        Map<Integer, Integer> tileAssetIds = new LinkedHashMap<>();
        FileHandle[] files = tilesetDir.list((dir, name) ->
                name.endsWith(StudioFs.EXT_PNG) && isNumericBaseName(name));

        sortByNumericBaseName(files);

        if (files == null) {
            return tileAssetIds;
        }

        for (FileHandle f : files) {
            int sheetIndex = Integer.parseInt(AssetHelper.removeExtension(f.name()));
            int cellX = sheetIndex % columns;
            int cellY = sheetIndex / columns;

            String logical = StudioFs.PREFIX_TILES + base + "/" + sheetIndex;

            TileAssetMeta tileMeta = requireTileMeta(
                    assetMetaDatabase.registerIfAbsent(
                            AssetType.TILE,
                            logical,
                            null,
                            AssetMeta.AssetScope.USER
                    )
            );

            String newFileName = sheetIndex + "__a" + tileMeta.id + StudioFs.EXT_PNG;
            FileHandle dst = tilesetDir.child(newFileName);

            if (!dst.exists()) {
                f.moveTo(dst);
            }

            tileMeta.sourceRelPath = StudioFs.DIR_ORIG_TILES + "/" + base + "/" + newFileName;
            tileMeta.tilesetId = tilesetMeta.id;
            tileMeta.sheetIndex = sheetIndex;
            tileMeta.cellX = cellX;
            tileMeta.cellY = cellY;
            tileAssetIds.put(sheetIndex, tileMeta.id);
        }
        return tileAssetIds;
    }

    private void copyTilesetSourceFile(String base,
                                       FileHandle sourceFile,
                                       FileHandle tilesetDir,
                                       TilesetAssetMeta tilesetMeta) {
        String sheetFileName = base + "__a" + tilesetMeta.id + "." + sourceFile.extension();
        FileHandle sheetDst = tilesetDir.child(sheetFileName);

        if (!sheetDst.exists()) {
            sourceFile.copyTo(sheetDst);
        }

        tilesetMeta.sourceRelPath = StudioFs.DIR_ORIG_TILES + "/" + base + "/" + sheetFileName;
    }

    private FileHandle[] listTilesetFolderImages(FileHandle directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return new FileHandle[0];
        }

        FileHandle[] files = directory.list((dir, name) ->
                name != null && name.toLowerCase(Locale.ROOT).endsWith(StudioFs.EXT_PNG)
        );

        if (files == null) {
            return new FileHandle[0];
        }

        Arrays.sort(files, (a, b) -> compareNaturally(a.name(), b.name()));
        return files;
    }

    private FolderTilesetInfo analyzeFolderTileset(FileHandle[] sourceTiles) {
        if (sourceTiles == null || sourceTiles.length == 0) {
            return new FolderTilesetInfo(0, 0, true);
        }

        int minWidth = Integer.MAX_VALUE;
        int minHeight = Integer.MAX_VALUE;
        boolean uniform = true;

        ImageSize first = readImageSize(sourceTiles[0]);
        int firstWidth = first.width;
        int firstHeight = first.height;

        for (FileHandle tile : sourceTiles) {
            if (tile == null || !tile.exists() || tile.isDirectory()) continue;

            ImageSize size = readImageSize(tile);

            minWidth = Math.min(minWidth, size.width);
            minHeight = Math.min(minHeight, size.height);

            if (size.width != firstWidth || size.height != firstHeight) {
                uniform = false;
            }
        }

        if (!uniform) {
            StudioLog.warn(
                    "Tileset directory contains mixed PNG sizes. " +
                            "Using smallest size as logical tile reference: " +
                            minWidth + "x" + minHeight
            );
        }

        return new FolderTilesetInfo(minWidth, minHeight, uniform);
    }

    private int compareNaturally(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        int ia = 0;
        int ib = 0;
        int na = a.length();
        int nb = b.length();

        while (ia < na && ib < nb) {
            char ca = a.charAt(ia);
            char cb = b.charAt(ib);

            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int sa = ia;
                int sb = ib;

                while (ia < na && Character.isDigit(a.charAt(ia))) ia++;
                while (ib < nb && Character.isDigit(b.charAt(ib))) ib++;

                String pa = a.substring(sa, ia);
                String pb = b.substring(sb, ib);

                int cmp = compareNumericStrings(pa, pb);
                if (cmp != 0) return cmp;
                continue;
            }

            int cmp = Character.compare(
                    Character.toLowerCase(ca),
                    Character.toLowerCase(cb)
            );
            if (cmp != 0) return cmp;

            ia++;
            ib++;
        }

        return Integer.compare(na, nb);
    }

    private int compareNumericStrings(String a, String b) {
        int ia = 0;
        int ib = 0;

        while (ia < a.length() && a.charAt(ia) == '0') ia++;
        while (ib < b.length() && b.charAt(ib) == '0') ib++;

        int la = a.length() - ia;
        int lb = b.length() - ib;

        if (la != lb) {
            return Integer.compare(la, lb);
        }

        for (int i = 0; i < la; i++) {
            char ca = a.charAt(ia + i);
            char cb = b.charAt(ib + i);
            if (ca != cb) {
                return Character.compare(ca, cb);
            }
        }

        return Integer.compare(a.length(), b.length());
    }

    private FileHandle prepareTilesetDirectory(FileHandle tilesRoot, String base) {
        FileHandle tilesetDir = tilesRoot.child(base);
        tilesetDir.mkdirs();
        return tilesetDir;
    }

    private TilesetAssetMeta requireTilesetMeta(AssetMeta meta) {
        if (meta instanceof TilesetAssetMeta tilesetMeta) {
            return tilesetMeta;
        }
        throw new IllegalStateException("Expected TilesetAssetMeta but got: " + meta);
    }

    private TileAssetMeta requireTileMeta(AssetMeta meta) {
        if (meta instanceof TileAssetMeta tileMeta) {
            return tileMeta;
        }
        throw new IllegalStateException("Expected TileAssetMeta but got: " + meta);
    }

    private boolean isNumericBaseName(String fileName) {
        String base = AssetHelper.removeExtension(fileName);
        if (base == null || base.isBlank()) return false;

        for (int i = 0; i < base.length(); i++) {
            if (!Character.isDigit(base.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void sortByNumericBaseName(FileHandle[] files) {
        if (files == null) return;

        Arrays.sort(files, Comparator.comparingInt(
                f -> Integer.parseInt(AssetHelper.removeExtension(f.name()))
        ));
    }

    private ImageSize readImageSize(FileHandle file) {
        Pixmap pixmap = new Pixmap(file);
        try {
            return new ImageSize(pixmap.getWidth(), pixmap.getHeight());
        } finally {
            pixmap.dispose();
        }
    }

    private void warnUnsupported(FileHandle file) {
        if (file == null) {
            StudioLog.warn("Unsupported file format: null");
            return;
        }
        String ext = file.extension();
        StudioLog.warn("Unsupported file format: " +
                (ext == null || ext.isBlank() ? file.name() : ("." + ext.toLowerCase(Locale.ROOT))));
    }

    private void splitGridImage(FileHandle sourceFile,
                                FileHandle outputDir,
                                int tileWidth,
                                int tileHeight,
                                int spacing,
                                int margin,
                                String prefix) {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("Source image is missing");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory is null");
        }
        validateAtlasSlicing(tileWidth, tileHeight, spacing, margin);

        outputDir.mkdirs();

        Pixmap source = new Pixmap(sourceFile);
        try {
            int imageWidth = source.getWidth();
            int imageHeight = source.getHeight();
            AtlasGrid grid = calculateAtlasGrid(imageWidth, imageHeight, tileWidth, tileHeight, spacing, margin);

            if (grid.ignoredRightPixels() > 0 || grid.ignoredBottomPixels() > 0) {
                StudioLog.warn(
                        "Grid split will ignore extra pixels: image="
                                + imageWidth + "x" + imageHeight
                                + ", tile=" + tileWidth + "x" + tileHeight
                                + ", spacing=" + spacing
                                + ", margin=" + margin
                );
            }

            int index = 0;

            for (int row = 0; row < grid.rows(); row++) {
                int sourceY = margin + row * (tileHeight + spacing);
                for (int column = 0; column < grid.columns(); column++) {
                    int sourceX = margin + column * (tileWidth + spacing);
                    Pixmap tile = new Pixmap(tileWidth, tileHeight, source.getFormat());
                    try {
                        tile.drawPixmap(
                                source,
                                0, 0,
                                sourceX, sourceY,
                                tileWidth, tileHeight
                        );

                        String fileName = buildSplitTileFileName(prefix, index);
                        FileHandle out = outputDir.child(fileName);
                        PixmapIO.writePNG(out, tile);
                    } finally {
                        tile.dispose();
                    }
                    index++;
                }
            }
        } finally {
            source.dispose();
        }
    }

    private void validateAtlasSlicing(int tileWidth, int tileHeight, int spacing, int margin) {
        if (tileWidth <= 0 || tileHeight <= 0) {
            throw new IllegalArgumentException("Tile size must be > 0");
        }
        if (spacing < 0 || margin < 0) {
            throw new IllegalArgumentException("Tileset spacing and margin must be >= 0");
        }
    }

    private AtlasGrid calculateAtlasGrid(int imageWidth,
                                         int imageHeight,
                                         int tileWidth,
                                         int tileHeight,
                                         int spacing,
                                         int margin) {
        int columns = countTilesOnAxis(imageWidth, tileWidth, spacing, margin);
        int rows = countTilesOnAxis(imageHeight, tileHeight, spacing, margin);
        if (columns <= 0 || rows <= 0) {
            throw new IllegalStateException(
                    "Image is smaller than the requested grid size: "
                            + imageWidth + "x" + imageHeight
                            + " for tiles " + tileWidth + "x" + tileHeight
                            + ", spacing=" + spacing
                            + ", margin=" + margin
            );
        }

        int lastTileRight = margin + ((columns - 1) * (tileWidth + spacing)) + tileWidth;
        int lastTileBottom = margin + ((rows - 1) * (tileHeight + spacing)) + tileHeight;
        return new AtlasGrid(
                columns,
                rows,
                Math.max(0, imageWidth - lastTileRight),
                Math.max(0, imageHeight - lastTileBottom)
        );
    }

    private int countTilesOnAxis(int imageSize, int tileSize, int spacing, int margin) {
        int count = 0;
        for (int position = margin; position <= imageSize - tileSize; position += tileSize + spacing) {
            count++;
        }
        return count;
    }

    private String buildSplitTileFileName(String prefix, int index) {
        if (prefix == null || prefix.isBlank()) {
            return index + StudioFs.EXT_PNG;
        }
        return prefix + "_" + String.format(Locale.ROOT, "%04d", index) + StudioFs.EXT_PNG;
    }

    private static boolean isImage(FileHandle f) {
        return f != null && StudioFs.isImageFile(f.name());
    }

    private static String baseName(String name) {
        return StudioFs.baseName(name);
    }

    private String atlasBaseName(TilesetAtlasImportRequest request) {
        String requestedName = request.tilesetName();
        if (requestedName != null && !requestedName.isBlank()) {
            String sanitized = sanitizeBaseName(requestedName);
            if (!sanitized.isBlank()) {
                return sanitized;
            }
        }
        return baseName(request.sourceFile().name());
    }

    private String sanitizeBaseName(String name) {
        String base = baseName(name);
        if (base == null || base.isBlank()) {
            return "";
        }

        String sanitized = base
                .replaceAll("[^A-Za-z0-9 _-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return sanitized.isBlank() ? "" : sanitized;
    }

    private record ImageSize(int width, int height) {
    }

    private record FolderTilesetInfo(int referenceTileWidth, int referenceTileHeight, boolean uniform) {
    }

    private record AtlasGrid(int columns, int rows, int ignoredRightPixels, int ignoredBottomPixels) {
    }
}
