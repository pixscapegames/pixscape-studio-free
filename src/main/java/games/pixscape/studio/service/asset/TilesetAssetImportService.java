package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.log.StudioLog;

import java.util.*;

public final class TilesetAssetImportService {

    private final AssetMetaDatabase assetMetaDatabase;

    public TilesetAssetImportService(AssetMetaDatabase assetMetaDatabase) {
        this.assetMetaDatabase = Objects.requireNonNull(assetMetaDatabase, "assetMetaDatabase");
    }

    public TilesetImportResult importAtlas(TilesetAtlasImportRequest request) {
        return new TilesetReimportService(assetMetaDatabase).importNew(request);
    }

    public TilesetImportResult importDirectory(TilesetDirectoryImportRequest request) {
        return new TilesetReimportService(assetMetaDatabase).importNew(request);
    }

    public TilesetImportResult importImageCollection(TilesetImageCollectionImportRequest request) {
        Objects.requireNonNull(request, "request");

        List<ImageCollectionTileSource> sourceTiles = request.sourceTiles();
        if (sourceTiles == null || sourceTiles.isEmpty()) {
            StudioLog.warn("Image collection tileset import skipped: no tile images found.");
            return TilesetImportResult.skipped();
        }

        FileHandle sourceRoot = null;
        Set<Integer> localIdsSeen = new HashSet<>();
        for (ImageCollectionTileSource source : sourceTiles) {
            if (source == null || source.sourceFile() == null) continue;
            FileHandle color = source.sourceFile();
            if (TilesetReimportService.isNormalCompanion(color.name())) continue;
            if (!isImage(color) || source.localTileId() < 0) continue;
            if (!color.exists() || color.isDirectory())
                throw new IllegalArgumentException("Collection source file is missing: " + color);
            if (!localIdsSeen.add(source.localTileId()))
                throw new IllegalArgumentException("Ambiguous collection local tile ID: " + source.localTileId());
            TilesetReimportService.size(color);
            TilesetReimportService.validateNormal(color, TilesetReimportService.companion(color));
            java.nio.file.Path parent = color.file().toPath().toAbsolutePath().normalize().getParent();
            if (sourceRoot == null) sourceRoot = new FileHandle(parent.toFile());
            else {
                java.nio.file.Path root = sourceRoot.file().toPath();
                while (root != null && !parent.startsWith(root)) root = root.getParent();
                if (root == null) throw new IllegalArgumentException("Collection images have no common source directory.");
                sourceRoot = new FileHandle(root.toFile());
            }
        }

        if (sourceRoot == null) return TilesetImportResult.skipped();
        Map<String, String> sourceKeys = new HashMap<>();
        for (ImageCollectionTileSource source : sourceTiles) {
            if (source == null || source.sourceFile() == null || source.localTileId() < 0 || !isImage(source.sourceFile())
                    || TilesetReimportService.isNormalCompanion(source.sourceFile().name())) continue;
            String key = TilesetReimportService.relative(sourceRoot, source.sourceFile());
            String previousKey = sourceKeys.putIfAbsent(key.toLowerCase(Locale.ROOT), key);
            if (previousKey != null && !previousKey.equals(key))
                throw new IllegalArgumentException("Ambiguous collection source path: " + key);
        }
        String base = imageCollectionBaseName(request);
        AssetMeta previous = assetMetaDatabase.findByLogicalPath(StudioFs.PREFIX_TILES + base);
        if (previous instanceof TilesetAssetMeta existing) {
            new TilesetReimportService(assetMetaDatabase).validateProvenance(existing.id());
            if (!"collection".equals(existing.importKind))
                throw new IllegalArgumentException("Existing tileset source kind differs. Use the tileset import dialog to import as new.");
            FileHandle originalRoot = new FileHandle(new java.io.File(java.net.URI.create(existing.importSourceUri)));
            Map<String, Integer> existingBySource = new HashMap<>();
            for (int i = 0; i < assetMetaDatabase.size(); i++) {
                if (assetMetaDatabase.assetAt(i) instanceof TileAssetMeta tile && tile.tilesetId == existing.id()
                        && tile.importSourceKey != null && existingBySource.putIfAbsent(tile.importSourceKey, tile.id()) != null)
                    throw new IllegalArgumentException("Ambiguous existing tileset source: " + tile.importSourceKey);
            }
            Map<Integer, Integer> localIds = new LinkedHashMap<>();
            for (ImageCollectionTileSource item : sourceTiles) {
                if (item == null || item.sourceFile() == null || TilesetReimportService.isNormalCompanion(item.sourceFile().name())) continue;
                String key = TilesetReimportService.relative(originalRoot, item.sourceFile());
                Integer id = existingBySource.get(key);
                if (key.startsWith("../") || id == null)
                    throw new IllegalArgumentException("Existing tileset sources differ. Use the tileset import dialog to update or import as new.");
                localIds.put(item.localTileId(), id);
            }
            StudioLog.info("Existing tileset resources reused; use tileset Update to apply source image or normal changes.");
            return new TilesetImportResult(0, existing.id(), existing.logicalPath(), localIds);
        }
        if (request.tilesRoot().child(base).exists())
            throw new IllegalArgumentException("Tileset directory already exists without matching metadata: " + base);
        FileHandle tilesetDir = prepareTilesetDirectory(request.tilesRoot(), base);
        int tileCount = Math.max(request.tileCount(), imageCollectionTileCount(sourceTiles));

        TilesetAssetMeta tilesetMeta = createOrUpdateFolderTilesetMeta(
                base,
                tileCount,
                request.tileWidth(),
                request.tileHeight(),
                request.profileSettings()
        );
        tilesetMeta.importKind = "collection";
        tilesetMeta.importSourceUri = TilesetReimportService.sourceUri(sourceRoot);

        Map<Integer, Integer> tileAssetIds = registerImageCollectionTilesAsTileAssets(
                base,
                tilesetDir,
                sourceTiles,
                tilesetMeta
        );
        if (tileAssetIds.isEmpty()) {
            StudioLog.warn("Image collection tileset import skipped: no supported tile images found.");
            return TilesetImportResult.skipped();
        }

        return new TilesetImportResult(1, tilesetMeta.id(), StudioFs.PREFIX_TILES + base, tileAssetIds);
    }

    public record TilesetAtlasImportRequest(FileHandle sourceFile,
                                            FileHandle tilesRoot,
                                            int tileWidth,
                                            int tileHeight,
                                            int spacing,
                                            int margin,
                                            String tilesetName,
                                            TilesetProfileImportSettings profileSettings) {
        public TilesetAtlasImportRequest {
            if (profileSettings == null) {
                profileSettings = TilesetProfileImportSettings.defaults(tileWidth, tileHeight);
            }
        }

        public TilesetAtlasImportRequest(FileHandle sourceFile,
                                         FileHandle tilesRoot,
                                         int tileWidth,
                                         int tileHeight,
                                         int spacing,
                                         int margin) {
            this(sourceFile, tilesRoot, tileWidth, tileHeight, spacing, margin, null);
        }

        public TilesetAtlasImportRequest(FileHandle sourceFile,
                                         FileHandle tilesRoot,
                                         int tileWidth,
                                         int tileHeight,
                                         int spacing,
                                         int margin,
                                         String tilesetName) {
            this(sourceFile, tilesRoot, tileWidth, tileHeight, spacing, margin, tilesetName, null);
        }
    }

    public record TilesetProfileImportSettings(int referenceCellWidth,
                                               int referenceCellHeight,
                                               TiledProjection projection,
                                               TilesetAnchor anchor,
                                               int offsetX,
                                               int offsetY,
                                               TilesetRenderSize renderSize) {
        public static TilesetProfileImportSettings defaults(int tileWidth, int tileHeight) {
            return new TilesetProfileImportSettings(
                    tileWidth > 0 ? tileWidth : 32,
                    tileHeight > 0 ? tileHeight : 32,
                    TiledProjection.ORTHO,
                    TilesetAnchor.TOP_CENTER,
                    0,
                    0,
                    TilesetRenderSize.NATIVE
            );
        }
    }

    public record TilesetDirectoryImportRequest(FileHandle directory,
                                                FileHandle tilesRoot,
                                                TilesetProfileImportSettings profileSettings) {
        public TilesetDirectoryImportRequest(FileHandle directory, FileHandle tilesRoot) {
            this(directory, tilesRoot, null);
        }
    }

    public record TilesetImageCollectionImportRequest(String tilesetName,
                                                      FileHandle tilesRoot,
                                                      int tileWidth,
                                                      int tileHeight,
                                                      int tileCount,
                                                      List<ImageCollectionTileSource> sourceTiles,
                                                      TilesetProfileImportSettings profileSettings) {
        public TilesetImageCollectionImportRequest {
            sourceTiles = sourceTiles == null ? List.of() : List.copyOf(sourceTiles);
            if (profileSettings == null) {
                profileSettings = TilesetProfileImportSettings.defaults(tileWidth, tileHeight);
            }
        }
    }

    public record ImageCollectionTileSource(int localTileId,
                                            FileHandle sourceFile,
                                            String imageSource,
                                            int imageWidth,
                                            int imageHeight) {
    }

    public record DirectoryTilesetAnalysis(FileHandle[] sourceTiles,
                                           int referenceTileWidth,
                                           int referenceTileHeight,
                                           boolean uniform) {
        public DirectoryTilesetAnalysis {
            if (sourceTiles == null) {
                sourceTiles = new FileHandle[0];
            }
        }
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
                                                             int referenceTileHeight,
                                                             TilesetProfileImportSettings profileSettings) {
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
        assetMetaDatabase.updateSourceRelPath(tilesetMeta.id(), null);
        tilesetMeta.tileWidth = referenceTileWidth;
        tilesetMeta.tileHeight = referenceTileHeight;
        tilesetMeta.columns = Math.max(1, tileCount);
        tilesetMeta.rows = 1;
        tilesetMeta.spacing = 0;
        tilesetMeta.margin = 0;
        applyProfileSettings(
                tilesetMeta,
                profileSettings != null
                        ? profileSettings
                        : TilesetProfileImportSettings.defaults(referenceTileWidth, referenceTileHeight)
        );

        return tilesetMeta;
    }

    private Map<Integer, Integer> registerImageCollectionTilesAsTileAssets(String base,
                                                                           FileHandle tilesetDir,
                                                                           List<ImageCollectionTileSource> sourceTiles,
                                                                           TilesetAssetMeta tilesetMeta) {
        Map<Integer, Integer> tileAssetIds = new LinkedHashMap<>();
        Map<String, Integer> assetIdByCanonicalSource = new LinkedHashMap<>();
        if (sourceTiles == null) return tileAssetIds;

        for (ImageCollectionTileSource sourceTile : sourceTiles) {
            if (sourceTile == null || sourceTile.localTileId() < 0) continue;

            FileHandle src = sourceTile.sourceFile();
            if (src != null && TilesetReimportService.isNormalCompanion(src.name())) continue;
            if (!isImage(src) || !src.exists() || src.isDirectory()) {
                warnUnsupported(src);
                continue;
            }

            String canonicalSource = pathOf(src);
            Integer existingAssetId = assetIdByCanonicalSource.get(canonicalSource);
            if (existingAssetId != null && existingAssetId > 0) {
                tileAssetIds.put(sourceTile.localTileId(), existingAssetId);
                continue;
            }

            int localTileId = sourceTile.localTileId();
            String logical = StudioFs.PREFIX_TILES + base + "/" + localTileId;
            TileAssetMeta tileMeta = requireTileMeta(
                    assetMetaDatabase.registerIfAbsent(
                            AssetType.TILE,
                            logical,
                            null,
                            AssetMeta.AssetScope.USER
                    )
            );

            String ext = src.extension();
            String newFileName = localTileId + "__a" + tileMeta.id()
                    + (ext == null || ext.isBlank() ? "" : "." + ext);
            FileHandle dst = tilesetDir.child(newFileName);
            if (!dst.exists()) {
                src.copyTo(dst);
            }

            assetMetaDatabase.updateSourceRelPath(
                    tileMeta.id(),
                    StudioFs.DIR_ORIG_TILES + "/" + base + "/" + newFileName
            );
            FileHandle sourceRoot = new FileHandle(new java.io.File(java.net.URI.create(tilesetMeta.importSourceUri)));
            tileMeta.importSourceKey = TilesetReimportService.relative(sourceRoot, src);
            FileHandle normal = TilesetReimportService.companion(src);
            TilesetReimportService.validateNormal(src, normal);
            if (normal.exists()) {
                String normalName = "normals/" + tileMeta.id() + ".png";
                normal.copyTo(tilesetDir.child(normalName));
                tileMeta.normalSourceRelPath = StudioFs.DIR_ORIG_TILES + "/" + base + "/" + normalName;
            }
            tileMeta.tilesetId = tilesetMeta.id();
            tileMeta.sheetIndex = localTileId;
            tileMeta.cellX = localTileId;
            tileMeta.cellY = 0;
            tileAssetIds.put(localTileId, tileMeta.id());
            assetIdByCanonicalSource.put(canonicalSource, tileMeta.id());
        }
        return tileAssetIds;
    }

    void applyProfileSettings(TilesetAssetMeta tilesetMeta,
                                      TilesetProfileImportSettings profileSettings) {
        if (tilesetMeta == null) return;

        TilesetProfileImportSettings settings = profileSettings != null
                ? profileSettings
                : TilesetProfileImportSettings.defaults(tilesetMeta.tileWidth, tilesetMeta.tileHeight);

        if (settings.referenceCellWidth() <= 0 || settings.referenceCellHeight() <= 0) {
            throw new IllegalArgumentException("Reference cell size must be > 0");
        }
        if (settings.projection() == null || settings.anchor() == null) {
            throw new IllegalArgumentException("Tileset profile projection and anchor are required");
        }
        if (settings.renderSize() != TilesetRenderSize.NATIVE) {
            throw new IllegalArgumentException("Tileset render size must be native");
        }

        tilesetMeta.referenceCellWidth = settings.referenceCellWidth();
        tilesetMeta.referenceCellHeight = settings.referenceCellHeight();
        tilesetMeta.projection = settings.projection();
        tilesetMeta.anchor = settings.anchor();
        tilesetMeta.offsetX = settings.offsetX();
        tilesetMeta.offsetY = settings.offsetY();
        tilesetMeta.renderSize = settings.renderSize();
        tilesetMeta.normalizeProfileDefaults();
    }

    public static DirectoryTilesetAnalysis analyzeDirectory(FileHandle directory) {
        FileHandle[] sourceTiles = listTilesetFolderImages(directory);
        FolderTilesetInfo info = analyzeFolderTileset(sourceTiles);
        return new DirectoryTilesetAnalysis(
                sourceTiles,
                info.referenceTileWidth,
                info.referenceTileHeight,
                info.uniform
        );
    }

    private static FileHandle[] listTilesetFolderImages(FileHandle directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return new FileHandle[0];
        }

        List<FileHandle> collected = new ArrayList<>();
        collectColorImages(directory, collected);
        FileHandle[] files = collected.toArray(new FileHandle[0]);
        Arrays.sort(files, (a, b) -> compareNaturally(
                TilesetReimportService.relative(directory, a), TilesetReimportService.relative(directory, b)));
        return files;
    }

    private static void collectColorImages(FileHandle directory, List<FileHandle> out) {
        for (FileHandle file : listImportFiles(directory)) {
            if (java.nio.file.Files.isSymbolicLink(file.file().toPath()))
                throw new IllegalArgumentException("Collection contains a symbolic link: " + file.name());
            if (file.isDirectory()) collectColorImages(file, out);
            else if (file.name().toLowerCase(Locale.ROOT).endsWith(".png")
                    && !TilesetReimportService.isNormalCompanion(file.name())) out.add(file);
        }
    }

    static FileHandle[] listImportFiles(FileHandle directory) {
        List<FileHandle> files = new ArrayList<>();
        try (var entries = java.nio.file.Files.newDirectoryStream(directory.file().toPath())) {
            for (var entry : entries) files.add(new FileHandle(entry.toFile()));
        } catch (java.io.IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Cannot inspect tileset import directory: " + directory, failure);
        }
        return files.toArray(FileHandle[]::new);
    }

    private static FolderTilesetInfo analyzeFolderTileset(FileHandle[] sourceTiles) {
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

    private static int compareNaturally(String a, String b) {
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

    private static int compareNumericStrings(String a, String b) {
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

    private static ImageSize readImageSize(FileHandle file) {
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

    void splitGridImage(FileHandle sourceFile,
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
            TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(
                    imageWidth,
                    imageHeight,
                    tileWidth,
                    tileHeight,
                    spacing,
                    margin
            );
            if (!layout.hasTiles()) {
                throw new IllegalStateException(
                        "Image is smaller than the requested grid size: "
                                + imageWidth + "x" + imageHeight
                                + " for tiles " + tileWidth + "x" + tileHeight
                                + ", spacing=" + spacing
                                + ", margin=" + margin
                );
            }

            if (layout.unusedRightPixels() > 0 || layout.unusedBottomPixels() > 0) {
                StudioLog.warn(
                        "Grid split will ignore extra pixels: image="
                                + imageWidth + "x" + imageHeight
                                + ", tile=" + tileWidth + "x" + tileHeight
                                + ", spacing=" + spacing
                                + ", margin=" + margin
                );
            }

            for (int index = 0; index < layout.tileCount(); index++) {
                TilesetSliceLayout.SourceRect rect = layout.sourceRect(index);
                if (!rect.valid()) {
                    throw new IllegalStateException("Invalid tileset source rectangle: " + rect.invalidReason());
                }

                Pixmap tile = new Pixmap(rect.width(), rect.height(), source.getFormat());
                try {
                    tile.setBlending(Pixmap.Blending.None);
                    tile.drawPixmap(
                            source,
                            0, 0,
                            rect.x(), rect.y(),
                            rect.width(), rect.height()
                    );

                    String fileName = buildSplitTileFileName(prefix, index);
                    FileHandle out = outputDir.child(fileName);
                    PixmapIO.writePNG(out, tile);
                } finally {
                    tile.dispose();
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

    private String imageCollectionBaseName(TilesetImageCollectionImportRequest request) {
        String requestedName = request.tilesetName();
        if (requestedName != null && !requestedName.isBlank()) {
            String sanitized = sanitizeBaseName(requestedName);
            if (!sanitized.isBlank()) {
                return sanitized;
            }
        }
        return "image-collection";
    }

    private static int imageCollectionTileCount(List<ImageCollectionTileSource> tiles) {
        int max = 0;
        if (tiles == null) return max;
        for (ImageCollectionTileSource tile : tiles) {
            if (tile == null) continue;
            max = Math.max(max, tile.localTileId() + 1);
        }
        return max;
    }

    private static String pathOf(FileHandle file) {
        if (file == null) return "";
        try {
            return file.file().toPath().toAbsolutePath().normalize().toString();
        } catch (RuntimeException ex) {
            return file.path();
        }
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


}
