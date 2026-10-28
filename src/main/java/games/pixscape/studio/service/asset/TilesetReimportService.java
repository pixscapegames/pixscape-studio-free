package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.atlas.AtlasStudioService;

import java.util.*;

import static games.pixscape.studio.service.asset.TilesetAssetImportService.*;

/** Prepared tileset file import. No ECS mutation or inferred image identity. */
public final class TilesetReimportService {
    private final AssetMetaDatabase db;
    public TilesetReimportService(AssetMetaDatabase db) { this.db = Objects.requireNonNull(db); }

    @FunctionalInterface public interface UsageProof {
        Map<Integer, String> retentionReasons(Set<Integer> missingIds);
    }
    @FunctionalInterface public interface PackingValidation {
        void validate(FileHandle candidate, List<String> colorFiles);
    }
    public record Source(String key, FileHandle color, FileHandle normal, int index,
                         int width, int height) {}
    public record Plan(FileHandle tilesRoot, FileHandle source, String base, String kind,
                       TilesetProfileImportSettings settings, TilesetAtlasImportRequest grid,
                       int targetId, List<Source> sources, Map<String, Integer> matches,
                       Set<Integer> removed, Map<Integer, String> retained,
                       List<String> notices,
                       String catalogSnapshot) {
        public String summary() {
            return "Updated: " + matches.size() + " / added: " + (sources.size() - matches.size())
                    + " / deleted: " + removed.size() + " / absent but retained: " + retained.size()
                    + "\nImages change; existing tile IDs, placements and edited settings are kept."
                    + "\nThis import cannot be undone."
                    + (notices.isEmpty() ? "" : "\n" + String.join("\n", notices));
        }
    }

    /** Provenance wins. A same-name collision is a suggestion requiring an explicit choice. */
    public List<TilesetAssetMeta> candidates(FileHandle source, String name) {
        List<TilesetAssetMeta> exact = new ArrayList<>(), named = new ArrayList<>();
        String uri = sourceUri(source);
        for (int i = 0; i < db.size(); i++) {
            if (!(db.assetAt(i) instanceof TilesetAssetMeta set)) continue;
            if (uri.equals(set.importSourceUri)) exact.add(set);
            if ((StudioFs.PREFIX_TILES + name).equals(set.logicalPath())) named.add(set);
        }
        return List.copyOf(exact.isEmpty() ? named : exact);
    }

    public Plan prepareDirectory(TilesetDirectoryImportRequest request, int targetId,
                                 UsageProof usage) {
        if (targetId > 0) validateProvenance(targetId);
        if (!request.directory().exists() || !request.directory().isDirectory())
            throw new IllegalArgumentException("Collection source directory is missing.");
        var analysis = analyzeDirectory(request.directory());
        List<Source> sources = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        List<String> notices = new ArrayList<>();
        for (FileHandle file : analysis.sourceTiles()) {
            String key = relative(request.directory(), file);
            if (!keys.add(key.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Ambiguous source path: " + key);
            int[] size = size(file);
            FileHandle normal = companion(file);
            validateNormal(file, normal);
            sources.add(new Source(key, file, normal.exists() ? normal : null,
                    sources.size(), size[0], size[1]));
        }
        if (sources.isEmpty() && targetId <= 0) throw new IllegalArgumentException("No color PNG files in this collection.");
        collectOrphans(request.directory(), request.directory(), keys, notices);
        return prepare(request.tilesRoot(), request.directory(), StudioFs.baseName(request.directory().name()),
                "collection", request.profileSettings() != null ? request.profileSettings()
                        : TilesetProfileImportSettings.defaults(analysis.referenceTileWidth(), analysis.referenceTileHeight()),
                null, targetId, sources, usage, notices);
    }

    public Plan prepareAtlas(TilesetAtlasImportRequest request, int targetId, UsageProof usage) {
        if (targetId > 0) validateProvenance(targetId);
        if (isNormalCompanion(request.sourceFile().name()))
            throw new IllegalArgumentException("A normal companion cannot be imported as a color tileset.");
        int[] size = size(request.sourceFile());
        var layout = TilesetSliceLayout.calculate(size[0], size[1], request.tileWidth(), request.tileHeight(),
                request.spacing(), request.margin());
        if (!layout.hasTiles()) throw new IllegalArgumentException("The requested grid contains no tiles.");
        FileHandle normal = companion(request.sourceFile());
        validateNormal(request.sourceFile(), normal);
        if (targetId > 0) {
            TilesetAssetMeta old = requireSet(targetId);
            if (old.imageWidth != size[0] || old.imageHeight > size[1]
                    || old.tileWidth != request.tileWidth() || old.tileHeight != request.tileHeight()
                    || old.spacing != request.spacing() || old.margin != request.margin()
                    || old.columns != layout.columns() || old.rows > layout.rows())
                throw new IllegalArgumentException("Grid dimensions or slicing changed. Update refused to preserve tile identity and volumes.");
        }
        List<Source> sources = new ArrayList<>();
        for (int index = 0; index < layout.tileCount(); index++) {
            var rect = layout.sourceRect(index);
            sources.add(new Source("cell:" + (index % layout.columns()) + ":" + (index / layout.columns()),
                    request.sourceFile(), normal.exists() ? normal : null, index, rect.width(), rect.height()));
        }
        String name = request.tilesetName() != null ? StudioFs.baseName(request.tilesetName())
                : StudioFs.baseName(request.sourceFile().name());
        return prepare(request.tilesRoot(), request.sourceFile(), name, "grid", request.profileSettings(),
                request, targetId, sources, usage, new ArrayList<>());
    }

    private Plan prepare(FileHandle root, FileHandle source, String base, String kind,
                          TilesetProfileImportSettings settings, TilesetAtlasImportRequest grid,
                          int targetId, List<Source> sources, UsageProof usage,
                          List<String> notices) {
        if (base == null || base.isBlank() || base.contains("/") || base.contains("\\")
                || base.equals(".") || base.equals("..")) throw new IllegalArgumentException("Invalid tileset name.");
        TilesetAssetMeta target = targetId > 0 ? requireSet(targetId) : null;
        if (target != null) {
            base = target.logicalPath().substring(StudioFs.PREFIX_TILES.length());
            if (!target.importKind.equals(kind)) throw new IllegalArgumentException("Tileset source kind changed.");
        } else {
            String desired = base;
            for (int suffix = 2; db.findByLogicalPath(StudioFs.PREFIX_TILES + base) != null
                    || root.child(base).exists(); suffix++) base = desired + "-" + suffix;
        }
        if (base.contains("/") || base.contains("\\") || base.equals(".") || base.equals(".."))
            throw new IllegalArgumentException("Tileset destination is not a managed directory.");
        FileHandle project = root.parent().parent();
        Map<String, Integer> byKey = new LinkedHashMap<>();
        Set<Integer> oldIds = new LinkedHashSet<>();
        for (int i = 0; i < db.size(); i++) {
            if (!(db.assetAt(i) instanceof TileAssetMeta tile) || tile.tilesetId != targetId || targetId <= 0) continue;
            oldIds.add(tile.id());
            String key = tile.importSourceKey;
            if (byKey.putIfAbsent(key, tile.id()) != null)
                throw new IllegalArgumentException("Ambiguous current import: multiple tiles claim source '" + key + "'.");
        }
        Map<String, Integer> matches = new LinkedHashMap<>();
        for (Source item : sources) {
            Integer id = byKey.get(item.key());
            if (id == null) continue;
            TileAssetMeta tile = (TileAssetMeta) db.findById(id);
            int[] oldSize = size(project.child(tile.sourceRelPath()));
            if (oldSize[0] != item.width() || oldSize[1] != item.height())
                throw new IllegalArgumentException("Dimensions changed for tile " + id + " (" + item.key()
                        + "). Update refused; edited geometry and alignment need an explicit review.");
            matches.put(item.key(), id);
            if (tile.normalSourceRelPath != null && item.normal() == null)
                notices.add("Normal association will be removed: " + item.key());
        }
        Set<Integer> missing = new LinkedHashSet<>(oldIds);
        missing.removeAll(matches.values());
        Map<Integer, String> retained = missing.isEmpty() ? Map.of() : usage.retentionReasons(Set.copyOf(missing));
        Set<Integer> removed = new LinkedHashSet<>(missing);
        removed.removeAll(retained.keySet());
        for (int id : missing) notices.add("Source file absent: " + ((TileAssetMeta) db.findById(id)).importSourceKey
                + " (tile " + id + "). " + (retained.containsKey(id) ? "Retained: " + retained.get(id) : "Unused; will be deleted."));
        return new Plan(root, source, base, kind, settings, grid, targetId, List.copyOf(sources),
                Map.copyOf(matches), Set.copyOf(removed), Map.copyOf(retained),
                List.copyOf(notices), db.toSnapshotJson());
    }

    public TilesetImportResult apply(Plan plan, PackingValidation packing, Runnable saveCatalog) {
        if (!db.toSnapshotJson().equals(plan.catalogSnapshot()))
            throw new IllegalArgumentException("Asset metadata changed. Review the tileset update again.");
        FileHandle target = plan.tilesRoot().child(plan.base());
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(target);
        AssetMetaDatabase before = AssetMetaDatabase.fromSnapshotJson(plan.catalogSnapshot());
        FileHandle catalogFile = plan.tilesRoot().parent().parent().child(StudioFs.FILE_ASSETS_JSON);
        byte[] catalogBytes = games.pixscape.studio.service.ProjectFileCleanupService.snapshotFile(catalogFile);
        Map<Integer, Integer> ids = new LinkedHashMap<>();
        try {
            if (target.exists()) copyContents(target, candidate);
            TilesetAssetMeta set = plan.targetId() > 0 ? requireSet(plan.targetId())
                    : (TilesetAssetMeta) db.registerIfAbsent(AssetType.TILESET,
                    StudioFs.PREFIX_TILES + plan.base(), null, AssetMeta.AssetScope.USER);
            set.importSourceUri = sourceUri(plan.source());
            set.importKind = plan.kind();
            int nextIndex = 0;
            for (int i = 0; i < db.size(); i++) if (db.assetAt(i) instanceof TileAssetMeta tile && tile.tilesetId == set.id())
                nextIndex = Math.max(nextIndex, tile.sheetIndex + 1);
            if (plan.targetId() <= 0) {
                set.tileWidth = plan.sources().stream().mapToInt(Source::width).min().orElseThrow();
                set.tileHeight = plan.sources().stream().mapToInt(Source::height).min().orElseThrow();
                set.columns = plan.sources().size(); set.rows = 1;
                new TilesetAssetImportService(db).applyProfileSettings(set, plan.settings());
            }
            FileHandle split = null;
            if (plan.grid() != null) {
                var req = plan.grid();
                split = candidate.child(".split");
                var importer = new TilesetAssetImportService(db);
                importer.splitGridImage(req.sourceFile(), split, req.tileWidth(), req.tileHeight(), req.spacing(), req.margin(), null);
                FileHandle normal = companion(req.sourceFile());
                if (normal.exists()) importer.splitGridImage(normal, split.child("normals"),
                        req.tileWidth(), req.tileHeight(), req.spacing(), req.margin(), null);
                int[] imageSize = size(req.sourceFile());
                var layout = TilesetSliceLayout.calculate(imageSize[0], imageSize[1], req.tileWidth(), req.tileHeight(), req.spacing(), req.margin());
                set.imageWidth = imageSize[0]; set.imageHeight = imageSize[1];
                set.tileWidth = req.tileWidth(); set.tileHeight = req.tileHeight();
                set.columns = layout.columns(); set.rows = layout.rows(); set.spacing = req.spacing(); set.margin = req.margin();
                String sheet = plan.base() + "__a" + set.id() + "." + req.sourceFile().extension();
                req.sourceFile().copyTo(candidate.child(sheet));
                db.updateSourceRelPath(set.id(), StudioFs.DIR_ORIG_TILES + "/" + plan.base() + "/" + sheet);
            }
            String prefix = StudioFs.DIR_ORIG_TILES + "/" + plan.base() + "/";
            Map<String, Integer> normalOwners = new HashMap<>();
            for (int i = 0; i < db.size(); i++) if (db.assetAt(i) instanceof TileAssetMeta tile && tile.normalSourceRelPath != null)
                normalOwners.merge(tile.normalSourceRelPath, 1, Integer::sum);
            for (Source source : plan.sources()) {
                Integer id = plan.matches().get(source.key());
                TileAssetMeta tile;
                if (id != null) tile = (TileAssetMeta) db.findById(id);
                else {
                    int index = plan.targetId() <= 0 || plan.grid() != null ? source.index() : nextIndex++;
                    tile = (TileAssetMeta) db.registerIfAbsent(AssetType.TILE, set.logicalPath() + "/" + index,
                            null, AssetMeta.AssetScope.USER);
                    tile.tilesetId = set.id(); tile.sheetIndex = index;
                    tile.cellX = plan.grid() == null ? index : index % set.columns;
                    tile.cellY = plan.grid() == null ? 0 : index / set.columns;
                }
                // An asset-owned destination prevents overwriting a resource shared by another asset.
                String colorName = tile.sheetIndex + "__a" + tile.id() + ".png";
                if (db.sourceOwnerCount(prefix + colorName) > 1 || normalOwners.getOrDefault(prefix + colorName, 0) > 0)
                    colorName = "owned-" + UUID.randomUUID() + "__a" + tile.id() + ".png";
                FileHandle color = split == null ? source.color() : split.child(source.index() + ".png");
                color.copyTo(candidate.child(colorName));
                db.updateSourceRelPath(tile.id(), prefix + colorName);
                tile.importSourceKey = source.key();
                String oldNormal = tile.normalSourceRelPath;
                tile.normalSourceRelPath = null;
                if (source.normal() != null) {
                    String normalName = "normals/" + tile.id() + ".png";
                    if (db.sourceOwnerCount(prefix + normalName) > 0
                            || normalOwners.getOrDefault(prefix + normalName, 0) > ((prefix + normalName).equals(oldNormal) ? 1 : 0))
                        normalName = "normals/owned-" + tile.id() + "-" + UUID.randomUUID() + ".png";
                    FileHandle normal = split == null ? source.normal() : split.child("normals/" + source.index() + ".png");
                    normal.copyTo(candidate.child(normalName));
                    validateNormal(candidate.child(colorName), candidate.child(normalName));
                    tile.normalSourceRelPath = prefix + normalName;
                }
                ids.put(source.index(), tile.id());
            }
            if (plan.grid() == null) set.columns = Math.max(set.columns, nextIndex);
            for (int id : plan.removed()) db.removeById(id);
            // Delete only previously owned files that no surviving asset still references.
            Set<String> referenced = new HashSet<>();
            List<String> colors = new ArrayList<>();
            for (int i = 0; i < db.size(); i++) {
                AssetMeta meta = db.assetAt(i);
                if (meta.sourceRelPath() != null) referenced.add(meta.sourceRelPath());
                if (meta instanceof TileAssetMeta tile) {
                    if (tile.normalSourceRelPath != null) referenced.add(tile.normalSourceRelPath);
                    if (tile.tilesetId == set.id()) {
                        String path = tile.sourceRelPath();
                        if (path == null) throw new IllegalArgumentException("Tile color resource is missing: " + tile.id());
                        if (path.startsWith(prefix)) colors.add(path.substring(prefix.length()));
                        else {
                            String checkPath = ".retained-colors/" + tile.id() + ".png";
                            catalogFile.parent().child(path).copyTo(candidate.child(checkPath));
                            colors.add(checkPath);
                        }
                    }
                }
            }
            for (int i = 0; i < before.size(); i++) if (before.assetAt(i) instanceof TileAssetMeta tile && tile.tilesetId == set.id()) {
                for (String path : new String[]{tile.sourceRelPath(), tile.normalSourceRelPath})
                    if (path != null && path.startsWith(prefix) && !referenced.contains(path)) candidate.child(path.substring(prefix.length())).delete();
            }
            if (split != null) split.deleteDirectory();
            packing.validate(candidate, colors);
            candidate.child(".retained-colors").deleteDirectory();
            try (var publication = AtomicDirectoryPublication.publish(candidate, target)) {
                saveCatalog.run();
                publication.commit();
            }
            return new TilesetImportResult(1, set.id(), set.logicalPath(), ids);
        } catch (RuntimeException | Error failure) {
            db.restoreAfterFailedImport(before);
            try {
                games.pixscape.studio.service.ProjectFileCleanupService.restoreFileFromSnapshot(catalogFile, catalogBytes);
                if (db.nextId() > before.nextId()) db.save(catalogFile);
            }
            catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        } finally {
            AtomicDirectoryPublication.discardCandidate(candidate);
        }
    }

    public TilesetImportResult importNew(TilesetDirectoryImportRequest request) {
        return apply(prepareDirectory(request, -1, ids -> Map.of()),
                AtlasStudioService::validateTilesetImportCandidate, () -> {});
    }
    public TilesetImportResult importNew(TilesetAtlasImportRequest request) {
        return apply(prepareAtlas(request, -1, ids -> Map.of()),
                AtlasStudioService::validateTilesetImportCandidate, () -> {});
    }
    private TilesetAssetMeta requireSet(int id) {
        if (!(db.findById(id) instanceof TilesetAssetMeta set)) throw new IllegalArgumentException("Tileset not found: " + id);
        return set;
    }

    /** Reimport boundary only: projects without this feature's provenance remain usable. */
    public void validateProvenance(int id) {
        TilesetAssetMeta set = requireSet(id);
        if (set.importKind == null || set.importKind.isBlank() || set.importSourceUri == null || set.importSourceUri.isBlank())
            throw missingProvenance(id, "tileset source and kind");
        if (!set.importKind.equals("grid") && !set.importKind.equals("collection"))
            throw new IllegalArgumentException("Invalid current tileset import kind: " + set.importKind);
        try {
            java.net.URI uri = java.net.URI.create(set.importSourceUri);
            if (!"file".equals(uri.getScheme()) || !new java.io.File(uri).isAbsolute()) throw new IllegalArgumentException();
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid current tileset source URI: " + id, failure);
        }
        if (set.importKind.equals("grid") && (set.imageWidth <= 0 || set.imageHeight <= 0 || set.columns <= 0 || set.rows <= 0))
            throw missingProvenance(id, "grid dimensions and slicing");
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < db.size(); i++) {
            if (!(db.assetAt(i) instanceof TileAssetMeta tile) || tile.tilesetId != id) continue;
            String key = tile.importSourceKey;
            if (key == null || key.isBlank()) throw missingProvenance(id, "source identity for tile " + tile.id());
            if (set.importKind.equals("collection")) {
                if (key.startsWith("/") || key.contains("\\") || key.contains(":")
                        || Arrays.stream(key.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))
                        || !StudioFs.isImageFile(key) || isNormalCompanion(key))
                    throw new IllegalArgumentException("Invalid current collection source path for tile " + tile.id() + ": " + key);
            } else if (tile.cellX < 0 || tile.cellX >= set.columns || tile.cellY < 0 || tile.cellY >= set.rows
                    || !key.equals("cell:" + tile.cellX + ":" + tile.cellY))
                throw new IllegalArgumentException("Invalid current grid cell identity for tile " + tile.id());
            if (!keys.add(key.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Ambiguous current import: multiple tiles claim source '" + key + "'.");
        }
    }

    private static IllegalArgumentException missingProvenance(int id, String field) {
        return new IllegalArgumentException("Tileset " + id + " lacks required reimport provenance (" + field
                + "). Older imports cannot be updated. No source recovery is performed; the project remains usable.");
    }
    /** The lowercase "_n" marker is case-sensitive: "_N" is a color orientation. */
    public static boolean isNormalCompanion(String fileName) {
        return fileName.endsWith("_n.png");
    }
    static FileHandle companion(FileHandle color) { return color.sibling(color.nameWithoutExtension() + "_n.png"); }
    static void validateNormal(FileHandle color, FileHandle normal) {
        if (!normal.exists()) return;
        if (!Arrays.equals(size(color), size(normal))) throw new IllegalArgumentException("Normal dimensions differ from color: " + normal.name());
    }
    static int[] size(FileHandle file) {
        Pixmap pixels = new Pixmap(file);
        try { return new int[]{pixels.getWidth(), pixels.getHeight()}; }
        finally { pixels.dispose(); }
    }
    static String sourceUri(FileHandle source) {
        try { return source.file().getCanonicalFile().toURI().toString(); }
        catch (java.io.IOException failure) { throw new IllegalArgumentException("Cannot resolve source path", failure); }
    }
    static String relative(FileHandle root, FileHandle file) {
        return root.file().toPath().toAbsolutePath().normalize().relativize(file.file().toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
    }
    private static void copyContents(FileHandle from, FileHandle to) {
        for (FileHandle item : listImportFiles(from)) {
            if (java.nio.file.Files.isSymbolicLink(item.file().toPath())) throw new IllegalArgumentException("Source directory contains a symbolic link.");
            if (item.isDirectory()) { FileHandle child = to.child(item.name()); child.mkdirs(); copyContents(item, child); }
            else item.copyTo(to.child(item.name()));
        }
    }
    private static void collectOrphans(FileHandle root, FileHandle dir, Set<String> colors, List<String> notices) {
        for (FileHandle file : listImportFiles(dir)) {
            if (file.isDirectory()) collectOrphans(root, file, colors, notices);
            else if (isNormalCompanion(file.name())) {
                String key = relative(root, file);
                String color = key.substring(0, key.length() - 6) + ".png";
                if (!colors.contains(color.toLowerCase(Locale.ROOT))) notices.add("Orphan normal ignored: " + key);
            }
        }
    }
}
