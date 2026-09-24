package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.hud.HudResourceRequirements;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageReferences;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.service.atlas.HudImageAssetRef;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Resolves explicit Scene HUD roots into a deterministic, authored-resource dependency closure.
 * This collector performs no packing, export, GL work, or output filesystem mutation.
 */
public final class SceneHudDependencyCollector {

    /** Identity-only dependencies; inspecting them never reads Image, Font or Skin source bytes. */
    public record HudUsage(java.util.Set<Integer> fontAssetIds, String runtimeSkinId,
                           String authoringSkinId) {
        public HudUsage { fontAssetIds = java.util.Set.copyOf(fontAssetIds); }
    }

    public HudUsage inspectUsage(SceneHudScreenSnapshot screen) {
        Objects.requireNonNull(screen, "HUD screen snapshot");
        HudScreenAsset asset = screen.asset();
        try { asset.validate(); }
        catch (RuntimeException failure) {
            throw rootFailure(screen.screenId(), "has an invalid HUD asset", failure);
        }
        HudValidationResult validation = validator.validate(screen.document());
        if (!validation.isValid()) {
            throw rootFailure(screen.screenId(), "has an invalid HUD document: "
                    + validationMessage(validation), null);
        }
        HudResourceRequirements requirements = HudResourceRequirements.from(validation.validatedDocument());
        String associatedSkin = normalizedSkinId(asset.skinId);
        String runtimeSkin = requirements.requiresSkin()
                ? normalizedRequiredSkinId(screen.screenId(), asset) : null;
        return new HudUsage(requirements.bitmapFontAssetIds(), runtimeSkin, associatedSkin);
    }

    static String normalizedSkinId(String skinId) {
        return skinId == null || skinId.isBlank() ? null : skinId.trim().replace('\\', '/');
    }

    /** Cheap authored-only check before reusing a previously resolved closure. No source files are read. */
    boolean samePackReferences(SceneHudScreenSnapshot before, SceneHudScreenSnapshot after) {
        if (before == null || after == null || !before.screenId().equals(after.screenId())) return false;
        References previous = references(before);
        return previous != null && previous.equals(references(after));
    }

    private record References(String skinId, boolean requiresAtlas, boolean builtInLabel,
                              java.util.Set<Integer> fonts, java.util.Set<String> images) {}

    private References references(SceneHudScreenSnapshot screen) {
        HudValidationResult validation = validator.validate(screen.document());
        if (!validation.isValid()) return null;
        HudResourceRequirements requirements = HudResourceRequirements.from(validation.validatedDocument());
        java.util.Set<String> images = new TreeSet<>();
        for (HudNode node : validation.validatedDocument().nodeIndex().values()) {
            HudImageReferences.visit(node, (ignored, field, image) -> {
                if (image != null && image.source == HudImageSource.REGION) images.add(image.resourceName);
            });
        }
        String skin = requirements.requiresSkin() ? screen.asset().skinId : null;
        return new References(skin, requirements.requiresAtlas(), requirements.requiresBuiltInLabelStyle(),
                new TreeSet<>(requirements.bitmapFontAssetIds()), images);
    }

    /** Reuses the Runtime HUD Skin dependency rules for an isolated import bundle. */
    public static SceneHudDependencyClosure.SkinDependency inspectSkinBundle(
            FileHandle bundleRoot, String skinRelativePath) {
        if (bundleRoot == null || skinRelativePath == null || skinRelativePath.isBlank()) {
            throw new IllegalArgumentException("Skin bundle root and JSON path are required.");
        }
        return scanSkin("Skin import", bundleRoot, skinRelativePath.replace('\\', '/'));
    }
    private final HudDocumentPersistenceService persistence;
    private final HudDocumentValidator validator;

    public SceneHudDependencyCollector() {
        this(new HudDocumentPersistenceService(), new HudDocumentValidator());
    }

    SceneHudDependencyCollector(HudDocumentPersistenceService persistence,
                                HudDocumentValidator validator) {
        this.persistence = persistence;
        this.validator = validator;
    }

    /**
     * Collects only the supplied roots. Open snapshots may override an exactly matching root,
     * but cannot make an unrelated HUD reachable.
     */
    public SceneHudDependencyClosure collect(FileHandle projectDir,
                                             AssetMetaDatabase assetDatabase,
                                             List<String> hudRootIds,
                                             List<SceneHudScreenSnapshot> openOverrides) {
        if (projectDir == null) throw new IllegalArgumentException("Studio project directory is required.");
        if (assetDatabase == null) throw new IllegalArgumentException("Asset metadata database is required.");

        List<String> roots = normalizeRoots(hudRootIds);
        if (roots.isEmpty()) return SceneHudDependencyClosure.empty();

        Map<String, SceneHudScreenSnapshot> overrides =
                selectedOpenOverrides(roots, openOverrides);
        List<SceneHudDependencyClosure.SelectedHudScreen> screens = new ArrayList<>();
        Map<Integer, SceneHudDependencyClosure.ImageDependency> images = new LinkedHashMap<>();
        Map<String, SceneHudDependencyClosure.SkinDependency> skins = new LinkedHashMap<>();
        Map<Integer, SceneHudDependencyClosure.FontDependency> fonts = new LinkedHashMap<>();

        for (String rootId : roots) {
            SceneHudScreenSnapshot override = overrides.get(rootId);
            HudScreenAsset asset;
            HudDocumentV1 document;
            try {
                if (override != null) {
                    asset = override.asset();
                    document = override.document();
                } else {
                    HudDocumentPersistenceService.Loaded saved = persistence.load(projectDir, rootId);
                    asset = saved.asset();
                    document = saved.document();
                }
                if (asset == null) throw new IllegalStateException("HUD asset is missing.");
                asset.validate();
            } catch (RuntimeException failure) {
                throw rootFailure(rootId, "cannot resolve its HUD asset/document", failure);
            }

            HudValidationResult structural = validator.validate(document);
            if (!structural.isValid()) {
                throw rootFailure(rootId, "has an invalid HUD document: " + validationMessage(structural), null);
            }
            HudResourceRequirements requirements = HudResourceRequirements.from(structural.validatedDocument());
            boolean requiresSkin = requirements.requiresSkin();
            boolean requiresAtlas = requirements.requiresAtlas();
            boolean requiresBuiltInLabelStyle = requirements.requiresBuiltInLabelStyle();
            collectRegionImages(rootId, structural.validatedDocument().nodeIndex().values(),
                    projectDir, assetDatabase, images);
            for (Integer fontAssetId : requirements.bitmapFontAssetIds()) {
                SceneHudDependencyClosure.FontDependency current = fonts.get(fontAssetId);
                if (current == null) {
                    fonts.put(fontAssetId, scanStandaloneFont(rootId, fontAssetId,
                            projectDir, assetDatabase));
                } else if (!current.hudRoots().contains(rootId)) {
                    List<String> fontRoots = new ArrayList<>(current.hudRoots());
                    fontRoots.add(rootId);
                    fonts.put(fontAssetId, new SceneHudDependencyClosure.FontDependency(
                            current.assetId(), current.logicalPath(), current.descriptorPath(),
                            current.files(), fontRoots));
                }
            }
            if (requiresSkin) {
                String skinId = normalizedRequiredSkinId(rootId, asset);
                skins.computeIfAbsent(skinId,
                        ignored -> scanSkin(rootId, projectDir, skinId));
            }
            screens.add(new SceneHudDependencyClosure.SelectedHudScreen(
                    rootId, asset, document, requiresSkin, requiresAtlas,
                    requiresBuiltInLabelStyle));
        }

        return new SceneHudDependencyClosure(screens, new ArrayList<>(images.values()),
                new ArrayList<>(skins.values()), new ArrayList<>(fonts.values()));
    }

    private static SceneHudDependencyClosure.FontDependency scanStandaloneFont(
            String rootId, int assetId, FileHandle projectDir, AssetMetaDatabase database) {
        AssetMeta meta = database.findById(assetId);
        if (meta == null || meta.type() != AssetType.FONT) {
            throw rootFailure(rootId, "references unknown bitmap font Asset " + assetId, null);
        }
        FileHandle descriptor = requireProjectFile(projectDir,
                projectDir.child(meta.sourceRelPath()), rootId,
                "Bitmap font Asset " + assetId + " descriptor");
        BitmapFontData data = parseStandaloneFontData(descriptor, rootId, assetId);
        List<SceneHudDependencyClosure.FileDependency> files = new ArrayList<>();
        addDependency(files, fileDependency(projectDir, descriptor,
                SceneHudDependencyClosure.FileKind.BITMAP_FONT_DESCRIPTOR, rootId,
                "Bitmap font descriptor"));
        Path descriptorRoot = descriptor.parent().file().toPath().toAbsolutePath().normalize();
        for (int pageIndex = 0; pageIndex < data.getImagePaths().length; pageIndex++) {
            FileHandle page = new FileHandle(data.getImagePath(pageIndex));
            Path pagePath = page.file().toPath().toAbsolutePath().normalize();
            if (!pagePath.startsWith(descriptorRoot)) {
                throw rootFailure(rootId, "bitmap font Asset " + assetId + " page "
                        + pageIndex + " escapes its descriptor folder", null);
            }
            if (!"png".equalsIgnoreCase(page.extension())) {
                throw rootFailure(rootId, "bitmap font Asset " + assetId + " page "
                        + pageIndex + " is not a PNG", null);
            }
            requireProjectFile(projectDir, page, rootId,
                    "Bitmap font Asset " + assetId + " page " + pageIndex);
            addDependency(files, fileDependency(projectDir, page,
                    SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE, rootId,
                    "Bitmap font page"));
        }
        String descriptorPath = projectRelativePath(projectDir, descriptor, rootId,
                "Bitmap font descriptor");
        return new SceneHudDependencyClosure.FontDependency(assetId, meta.logicalPath(),
                descriptorPath, files, List.of(rootId));
    }

    private static BitmapFontData parseStandaloneFontData(FileHandle descriptor,
                                                           String rootId, int assetId) {
        try {
            BitmapFontData data = new BitmapFontData(descriptor, false);
            if (data.getImagePaths() == null || data.getImagePaths().length == 0) {
                throw rootFailure(rootId, "bitmap font Asset " + assetId
                        + " descriptor has no page declarations", null);
            }
            return data;
        } catch (RuntimeException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("Scene HUD root '")) {
                throw failure;
            }
            throw rootFailure(rootId, "bitmap font Asset " + assetId
                    + " descriptor is malformed", failure);
        }
    }

    private static List<String> normalizeRoots(List<String> requestedRoots) {
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        if (requestedRoots != null) {
            for (String root : requestedRoots) {
                String normalized = HudScreenAssetId.normalizeOptional(root);
                if (normalized != null) roots.add(normalized);
            }
        }
        return List.copyOf(roots);
    }

    private static Map<String, SceneHudScreenSnapshot> selectedOpenOverrides(
            List<String> roots, List<SceneHudScreenSnapshot> snapshots) {
        LinkedHashSet<String> selected = new LinkedHashSet<>(roots);
        Map<String, SceneHudScreenSnapshot> result = new LinkedHashMap<>();
        if (snapshots != null) {
            for (SceneHudScreenSnapshot snapshot : snapshots) {
                if (snapshot == null || !selected.contains(snapshot.screenId())) continue;
                result.putIfAbsent(snapshot.screenId(), snapshot);
            }
        }
        return result;
    }

    private static void collectRegionImages(String rootId, Iterable<HudNode> nodes,
                                            FileHandle projectDir, AssetMetaDatabase database,
                                            Map<Integer, SceneHudDependencyClosure.ImageDependency> out) {
        for (HudNode node : nodes) {
            HudImageReferences.visit(node, (ignored, fieldPath, image) -> collectRegionImage(
                    rootId, node, fieldPath, image, projectDir, database, out));
        }
    }

    private static void collectRegionImage(String rootId, HudNode node, String fieldPath,
                                           HudImageData image,
                                           FileHandle projectDir, AssetMetaDatabase database,
                                           Map<Integer, SceneHudDependencyClosure.ImageDependency> out) {
            if (image == null || image.source != HudImageSource.REGION) return;
            String resourceName = image.resourceName;
            try {
                HudImageAssetRef reference = HudImageAssetRef.resolve(resourceName, database);
                FileHandle source = projectDir.child(reference.sourceRelPath());
                if (!source.exists() || source.isDirectory()) {
                    throw new IllegalStateException("source file is missing: " + reference.sourceRelPath());
                }
                AssetMeta meta = database.findById(reference.assetId());
                out.putIfAbsent(reference.assetId(), new SceneHudDependencyClosure.ImageDependency(
                        reference.assetId(), meta != null ? meta.logicalPath() : null,
                        reference.resourceName(), reference.sourceRelPath()));
            } catch (RuntimeException failure) {
                throw rootFailure(rootId, "node '" + node.id + "' field '" + fieldPath
                        + "' REGION resource '"
                        + resourceName + "' cannot resolve a project Image dependency", failure);
            }
    }

    private static String normalizedRequiredSkinId(String rootId, HudScreenAsset asset) {
        String skinId = normalizedSkinId(asset.skinId);
        if (skinId == null) {
            throw rootFailure(rootId, "requires a Skin for DRAWABLE or a custom HUD widget style, but skinId is absent", null);
        }
        return skinId;
    }

    private static SceneHudDependencyClosure.SkinDependency scanSkin(
            String rootId, FileHandle projectDir, String skinId) {
        FileHandle skinFile = requireProjectFile(projectDir, projectDir.child(skinId),
                rootId, "Skin '" + skinId + "'");
        List<SceneHudDependencyClosure.FileDependency> dependencies = new ArrayList<>();
        addDependency(dependencies, fileDependency(projectDir, skinFile,
                SceneHudDependencyClosure.FileKind.SKIN_JSON, rootId, "Skin JSON"));

        SourceSkinAtlas atlas = null;
        FileHandle atlasFile = skinFile.sibling(skinFile.nameWithoutExtension() + ".atlas");
        if (atlasFile.exists()) {
            atlas = parseSkinAtlas(rootId, projectDir, atlasFile);
            addDependency(dependencies, fileDependency(projectDir, atlasFile,
                    SceneHudDependencyClosure.FileKind.SKIN_ATLAS, rootId, "Skin atlas"));
            for (FileHandle page : atlas.pages()) {
                addDependency(dependencies, fileDependency(projectDir, page,
                        SceneHudDependencyClosure.FileKind.SKIN_ATLAS_PAGE, rootId, "Skin atlas page"));
            }
        }

        JsonValue skinJson;
        try {
            skinJson = new JsonReader().parse(skinFile);
        } catch (RuntimeException failure) {
            throw rootFailure(rootId, "Skin '" + skinId + "' has malformed JSON", failure);
        }
        if (skinJson == null || !skinJson.isObject()) {
            throw rootFailure(rootId, "Skin '" + skinId + "' must be a JSON object", null);
        }
        for (JsonValue section = skinJson.child; section != null; section = section.next) {
            if (!isBitmapFontSection(section.name)) continue;
            collectBitmapFonts(rootId, projectDir, skinFile, atlas, section, dependencies);
        }
        return new SceneHudDependencyClosure.SkinDependency(skinId, dependencies);
    }

    private static boolean isBitmapFontSection(String sectionName) {
        return "BitmapFont".equals(sectionName)
                || "com.badlogic.gdx.graphics.g2d.BitmapFont".equals(sectionName);
    }

    private static void collectBitmapFonts(String rootId, FileHandle projectDir, FileHandle skinFile,
                                           SourceSkinAtlas atlas,
                                           JsonValue fonts,
                                           List<SceneHudDependencyClosure.FileDependency> dependencies) {
        if (!fonts.isObject()) {
            throw rootFailure(rootId, "Skin BitmapFont section must be an object", null);
        }
        for (JsonValue font = fonts.child; font != null; font = font.next) {
            if (!font.isObject()) {
                throw rootFailure(rootId, "Skin BitmapFont '" + font.name + "' must be an object", null);
            }
            String fontPath = font.getString("file", null);
            if (fontPath == null || fontPath.isBlank()) {
                throw rootFailure(rootId, "Skin '" + skinFile.name() + "' BitmapFont '"
                        + font.name + "' has no descriptor file", null);
            }
            FileHandle descriptor = requireProjectFile(projectDir, skinFile.parent().child(fontPath), rootId,
                    "Skin '" + skinFile.name() + "' BitmapFont '" + font.name
                            + "' descriptor '" + fontPath + "'");
            addDependency(dependencies, fileDependency(projectDir, descriptor,
                    SceneHudDependencyClosure.FileKind.BITMAP_FONT_DESCRIPTOR, rootId, "BitmapFont descriptor"));

            BitmapFontData data = parseFontData(descriptor, rootId, skinFile.name(), font.name);
            int pageCount = data.getImagePaths().length;
            FontAtlasResolution resolution = atlas != null
                    ? atlas.resolveFont(descriptor.nameWithoutExtension(), pageCount)
                    : FontAtlasResolution.ABSENT;
            if (resolution == FontAtlasResolution.COMPLETE) continue;
            if (resolution == FontAtlasResolution.INCOMPLETE) {
                throw rootFailure(rootId, "Skin '" + skinFile.name()
                        + "' atlas has incomplete indexed regions for BitmapFont '"
                        + font.name + "' descriptor '" + descriptor.name() + "': "
                        + atlas.missingRegionDescription(descriptor.nameWithoutExtension(), pageCount), null);
            }
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                FileHandle page = new FileHandle(data.getImagePath(pageIndex));
                String pageName = page.name();
                requireProjectFile(projectDir, page, rootId,
                        "Skin '" + skinFile.name() + "' BitmapFont '" + font.name
                                + "' descriptor '" + descriptor.name() + "' page '" + pageName + "'");
                addDependency(dependencies, fileDependency(projectDir, page,
                        SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE, rootId, "BitmapFont page"));
            }
        }
    }

    private static SourceSkinAtlas parseSkinAtlas(
            String rootId, FileHandle projectDir, FileHandle atlasFile) {
        try {
            TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
            List<FileHandle> pages = new ArrayList<>();
            for (TextureAtlasData.Page page : data.getPages()) {
                pages.add(requireProjectFile(projectDir, page.textureFile, rootId,
                        "Skin atlas page '" + page.name + "'"));
            }
            if (pages.isEmpty()) {
                throw rootFailure(rootId, "Skin atlas '" + atlasFile.name() + "' has no page declarations", null);
            }
            Map<String, List<Integer>> indexesByName = new LinkedHashMap<>();
            for (TextureAtlasData.Region region : data.getRegions()) {
                indexesByName.computeIfAbsent(region.name, ignored -> new ArrayList<>()).add(region.index);
            }
            return new SourceSkinAtlas(List.copyOf(pages), indexesByName);
        } catch (RuntimeException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("Scene HUD root '")) {
                throw failure;
            }
            throw rootFailure(rootId, "Skin atlas '" + atlasFile.name() + "' is malformed", failure);
        }
    }

    private static BitmapFontData parseFontData(
            FileHandle descriptor, String rootId, String skinName, String fontName) {
        try {
            BitmapFontData data = new BitmapFontData(descriptor, false);
            if (data.getImagePaths() == null || data.getImagePaths().length == 0) {
                throw rootFailure(rootId, "Skin '" + skinName + "' BitmapFont '" + fontName
                        + "' descriptor '" + descriptor.name() + "' has no page declarations", null);
            }
            return data;
        } catch (RuntimeException failure) {
            if (failure.getMessage() != null && failure.getMessage().startsWith("Scene HUD root '")) {
                throw failure;
            }
            throw rootFailure(rootId, "Skin '" + skinName + "' BitmapFont '" + fontName
                    + "' descriptor '" + descriptor.name() + "' is malformed", failure);
        }
    }

    private static SceneHudDependencyClosure.FileDependency fileDependency(
            FileHandle projectDir, FileHandle file, SceneHudDependencyClosure.FileKind kind,
            String rootId, String label) {
        requireProjectFile(projectDir, file, rootId, label);
        return new SceneHudDependencyClosure.FileDependency(kind,
                projectRelativePath(projectDir, file, rootId, label));
    }

    private static void addDependency(List<SceneHudDependencyClosure.FileDependency> dependencies,
                                      SceneHudDependencyClosure.FileDependency dependency) {
        if (!dependencies.contains(dependency)) dependencies.add(dependency);
    }

    private static FileHandle requireProjectFile(FileHandle projectDir, FileHandle file,
                                                 String rootId, String label) {
        if (file == null || !file.exists() || file.isDirectory()) {
            throw rootFailure(rootId, label + " is missing", null);
        }
        projectRelativePath(projectDir, file, rootId, label);
        return file;
    }

    private static String projectRelativePath(FileHandle projectDir, FileHandle file,
                                              String rootId, String label) {
        Path root = projectDir.file().toPath().toAbsolutePath().normalize();
        Path candidate = file.file().toPath().toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            throw rootFailure(rootId, label + " escapes the Studio project directory", null);
        }
        return root.relativize(candidate).toString().replace('\\', '/');
    }

    private static IllegalStateException rootFailure(String rootId, String message, Throwable cause) {
        String full = "Scene HUD root '" + rootId + "' " + message + ".";
        return cause == null ? new IllegalStateException(full) : new IllegalStateException(full, cause);
    }

    private static String validationMessage(HudValidationResult validation) {
        StringBuilder out = new StringBuilder();
        validation.issues().forEach(issue -> out.append(issue.code()).append(" at ")
                .append(issue.path()).append(": ").append(issue.message()).append("; "));
        return out.toString();
    }

    /** GL-free source-atlas facts used only to mirror LibGDX Skin font resolution. */
    private record SourceSkinAtlas(List<FileHandle> pages, Map<String, List<Integer>> indexesByName) {
        private SourceSkinAtlas {
            pages = List.copyOf(pages);
            Map<String, List<Integer>> copied = new LinkedHashMap<>();
            indexesByName.forEach((name, indexes) -> copied.put(name, List.copyOf(indexes)));
            indexesByName = Map.copyOf(copied);
        }

        private FontAtlasResolution resolveFont(String descriptorStem, int pageCount) {
            return switch (SceneHudBitmapFontResolution.resolve(indexesByName, descriptorStem, pageCount)) {
            case COMPLETE_UNINDEXED, COMPLETE_INDEXED -> FontAtlasResolution.COMPLETE;
            case ABSENT -> FontAtlasResolution.ABSENT;
            case INCOMPLETE -> FontAtlasResolution.INCOMPLETE;
            };
        }

        private String missingRegionDescription(String descriptorStem, int pageCount) {
            List<Integer> indexes = indexesByName.getOrDefault(descriptorStem, List.of());
            List<String> missing = new ArrayList<>();
            for (int page = 0; page < pageCount; page++) {
                if (!indexes.contains(page)) missing.add(descriptorStem + "_" + page);
            }
            return "missing " + String.join(", ", missing);
        }
    }

    private enum FontAtlasResolution {
        COMPLETE,
        ABSENT,
        INCOMPLETE
    }
}
