package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.runtime.hud.HudBuiltInTextButtonStyle;
import games.pixscape.runtime.hud.HudBitmapFontResource;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageReferences;
import games.pixscape.runtime.hud.document.HudNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects a logical HUD dependency closure into collision-checked future pack inputs.
 * It parses authored metadata only: no output files, TexturePacker calls, or GL objects are created.
 */
public final class SceneHudPackInputProjector {
    public SceneHudPackInputPlan project(FileHandle projectDir, SceneHudDependencyClosure closure) {
        if (projectDir == null) throw new IllegalArgumentException("Studio project directory is required.");
        if (closure == null) throw new IllegalArgumentException("Scene HUD dependency closure is required.");

        Map<String, List<String>> imageRoots = imageRoots(closure);
        Map<String, List<String>> skinRoots = skinRoots(closure);
        Namespace namespace = new Namespace();
        List<SceneHudPackInputPlan.BitmapFontProjection> fonts = new ArrayList<>();

        for (SceneHudDependencyClosure.ImageDependency image : closure.imageDependencies()) {
            String sourcePath = requireProjectFile(projectDir, image.sourceRelPath(), "REGION Image source");
            namespace.register(new Candidate(new SceneHudPackInputPlan.AtlasKey(image.resourceName(), -1),
                    SceneHudPackInputPlan.MaterialKind.FULL_IMAGE, sourcePath, null,
                    provenance(SceneHudPackInputPlan.ProvenanceKind.REGION_IMAGE,
                            imageRoots.get(image.resourceName()), image.assetId(), image.logicalPath(),
                            null, null, null, null, null, null)));
        }

        for (SceneHudDependencyClosure.FontDependency font : closure.fontDependencies()) {
            projectStandaloneFont(projectDir, font, namespace, fonts);
        }

        for (SceneHudDependencyClosure.SkinDependency skin : closure.skinDependencies()) {
            projectSkin(projectDir, skin, skinRoots.get(skin.skinId()), namespace, fonts);
        }

        List<String> builtInRoots = closure.selectedHudScreens().stream()
                .filter(SceneHudDependencyClosure.SelectedHudScreen::requiresBuiltInLabelStyle)
                .map(SceneHudDependencyClosure.SelectedHudScreen::screenId).toList();
        if (!builtInRoots.isEmpty()) {
            namespace.register(new Candidate(
                    new SceneHudPackInputPlan.AtlasKey(HudBuiltInLabelStyle.ATLAS_REGION, -1),
                    SceneHudPackInputPlan.MaterialKind.INTERNAL_BUILT_IN_LABEL_FONT,
                    null, null,
                    provenance(SceneHudPackInputPlan.ProvenanceKind.INTERNAL_BUILT_IN_LABEL_FONT,
                            builtInRoots, null, null, null, null, null, null, null, null)));
        }

        namespace.register(new Candidate(new SceneHudPackInputPlan.AtlasKey(
                HudBuiltInTextButtonStyle.BACKGROUND_REGION, -1),
                SceneHudPackInputPlan.MaterialKind.INTERNAL_WHITE_PIXEL, null, null,
                provenance(SceneHudPackInputPlan.ProvenanceKind.INTERNAL_RESOURCE, List.of(), null, null,
                        null, null, null, null, null, null)));

        fonts.sort(java.util.Comparator.comparing(SceneHudPackInputPlan.BitmapFontProjection::descriptorPath));
        return new SceneHudPackInputPlan(closure.rootIds(), namespace.entries(), fonts);
    }

    private static void projectStandaloneFont(FileHandle projectDir,
                                              SceneHudDependencyClosure.FontDependency font,
                                              Namespace namespace,
                                              List<SceneHudPackInputPlan.BitmapFontProjection> output) {
        FileHandle descriptor = projectFile(projectDir, font.descriptorPath(),
                "Bitmap font Asset " + font.assetId() + " descriptor");
        BitmapFontData data;
        try {
            data = new BitmapFontData(descriptor, false);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Bitmap font Asset " + font.assetId()
                    + " descriptor is malformed.", failure);
        }
        String[] imagePaths = data.getImagePaths();
        if (imagePaths == null || imagePaths.length == 0) {
            throw new IllegalStateException("Bitmap font Asset " + font.assetId()
                    + " descriptor has no pages.");
        }
        List<SceneHudPackInputPlan.AtlasKey> keys = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < imagePaths.length; pageIndex++) {
            FileHandle page = requireProjectFile(projectDir, new FileHandle(imagePaths[pageIndex]),
                    "Bitmap font Asset " + font.assetId() + " page " + pageIndex);
            String pagePath = canonicalProjectPath(projectDir, page, "Bitmap font page");
            boolean declared = font.files().stream().anyMatch(file ->
                    file.kind() == SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE
                            && file.projectRelativePath().equals(pagePath));
            if (!declared) {
                throw new IllegalStateException("Bitmap font Asset " + font.assetId()
                        + " page is absent from its dependency closure: " + pagePath + ".");
            }
            SceneHudPackInputPlan.AtlasKey key = new SceneHudPackInputPlan.AtlasKey(
                    HudBitmapFontResource.pageKey(font.assetId(), pageIndex), -1);
            keys.add(key);
            namespace.register(new Candidate(key, SceneHudPackInputPlan.MaterialKind.FULL_IMAGE,
                    pagePath, null, provenance(SceneHudPackInputPlan.ProvenanceKind.BITMAP_FONT_PAGE,
                    font.hudRoots(), font.assetId(), font.logicalPath(), null, null,
                    null, null, font.descriptorPath(), pageIndex)));
        }
        output.add(new SceneHudPackInputPlan.BitmapFontProjection(
                font.descriptorPath(), keys));
    }

    private static void projectSkin(FileHandle projectDir, SceneHudDependencyClosure.SkinDependency skin,
                                    List<String> roots, Namespace namespace,
                                    List<SceneHudPackInputPlan.BitmapFontProjection> fonts) {
        String skinPath = requiredPath(skin, SceneHudDependencyClosure.FileKind.SKIN_JSON, "Skin JSON");
        FileHandle skinFile = projectFile(projectDir, skinPath, "Skin '" + skin.skinId() + "'");
        String atlasPath = optionalPath(skin, SceneHudDependencyClosure.FileKind.SKIN_ATLAS);
        AtlasFacts atlas = atlasPath == null ? AtlasFacts.empty() : parseAtlas(projectDir, skin, roots,
                projectFile(projectDir, atlasPath, "Skin atlas '" + atlasPath + "'"), namespace);
        projectFonts(projectDir, skin, roots, skinFile, atlas, namespace, fonts);
    }

    /** Projects all regions because arbitrary Skin JSON may dynamically request any atlas lookup name. */
    private static AtlasFacts parseAtlas(FileHandle projectDir, SceneHudDependencyClosure.SkinDependency skin,
                                         List<String> roots, FileHandle atlasFile, Namespace namespace) {
        TextureAtlasData data;
        try {
            data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Scene HUD Skin '" + skin.skinId() + "' atlas '"
                    + atlasFile.name() + "' is malformed.", failure);
        }
        Map<String, List<TextureAtlasData.Region>> byName = new LinkedHashMap<>();
        String atlasPath = canonicalProjectPath(projectDir, atlasFile, "Skin atlas");
        for (TextureAtlasData.Region region : data.getRegions()) {
            String pagePath = canonicalProjectPath(projectDir, region.page.textureFile,
                    "Skin atlas page for '" + region.name + "'");
            requireClosureFile(skin, SceneHudDependencyClosure.FileKind.SKIN_ATLAS_PAGE, pagePath,
                    "Skin atlas page");
            SceneHudPackInputPlan.AtlasRegionMetadata metadata = regionMetadata(pagePath, region);
            namespace.register(new Candidate(new SceneHudPackInputPlan.AtlasKey(region.name, region.index),
                    SceneHudPackInputPlan.MaterialKind.SKIN_ATLAS_REGION, pagePath, metadata,
                    provenance(SceneHudPackInputPlan.ProvenanceKind.SKIN_ATLAS_REGION, roots,
                            null, null, skin.skinId(), atlasPath,
                            region.name, region.index, null, null)));
            byName.computeIfAbsent(region.name, ignored -> new ArrayList<>()).add(region);
        }
        return new AtlasFacts(atlasPath, byName);
    }

    private static SceneHudPackInputPlan.AtlasRegionMetadata regionMetadata(
            String pagePath, TextureAtlasData.Region region) {
        Map<String, List<Integer>> values = new LinkedHashMap<>();
        if (region.names != null) for (int i = 0; i < region.names.length; i++) {
            int[] source = region.values[i];
            List<Integer> copy = new ArrayList<>(source.length);
            for (int value : source) copy.add(value);
            values.put(region.names[i], List.copyOf(copy));
        }
        return new SceneHudPackInputPlan.AtlasRegionMetadata(pagePath, region.page.name, region.page.pma,
                region.left, region.top, region.width, region.height, region.degrees, region.rotate, region.flip,
                region.originalWidth, region.originalHeight, region.offsetX, region.offsetY, values);
    }

    private static void projectFonts(FileHandle projectDir, SceneHudDependencyClosure.SkinDependency skin,
                                     List<String> roots, FileHandle skinFile, AtlasFacts atlas,
                                     Namespace namespace,
                                     List<SceneHudPackInputPlan.BitmapFontProjection> output) {
        JsonValue skinJson;
        try {
            skinJson = new JsonReader().parse(skinFile);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Scene HUD Skin '" + skin.skinId() + "' has malformed JSON.", failure);
        }
        if (skinJson == null || !skinJson.isObject()) {
            throw new IllegalStateException("Scene HUD Skin '" + skin.skinId() + "' must be a JSON object.");
        }
        for (JsonValue section = skinJson.child; section != null; section = section.next) {
            if (!isBitmapFontSection(section.name)) continue;
            if (!section.isObject()) {
                throw new IllegalStateException("Scene HUD Skin '" + skin.skinId()
                        + "' BitmapFont section must be an object.");
            }
            for (JsonValue font = section.child; font != null; font = font.next) {
                projectFont(projectDir, skin, roots, skinFile, atlas, namespace, output, font);
            }
        }
    }

    private static void projectFont(FileHandle projectDir, SceneHudDependencyClosure.SkinDependency skin,
                                    List<String> roots, FileHandle skinFile, AtlasFacts atlas,
                                    Namespace namespace,
                                    List<SceneHudPackInputPlan.BitmapFontProjection> output, JsonValue font) {
        if (!font.isObject()) throw new IllegalStateException("Scene HUD Skin '" + skin.skinId()
                + "' BitmapFont '" + font.name + "' must be an object.");
        String fontFileName = font.getString("file", null);
        if (fontFileName == null || fontFileName.isBlank()) throw new IllegalStateException("Scene HUD Skin '"
                + skin.skinId() + "' BitmapFont '" + font.name + "' has no descriptor file.");
        FileHandle descriptor = requireProjectFile(projectDir, skinFile.parent().child(fontFileName),
                "BitmapFont '" + font.name + "' descriptor '" + fontFileName + "'");
        String descriptorPath = canonicalProjectPath(projectDir, descriptor, "BitmapFont descriptor");
        requireClosureFile(skin, SceneHudDependencyClosure.FileKind.BITMAP_FONT_DESCRIPTOR, descriptorPath,
                "BitmapFont descriptor");
        BitmapFontData data;
        try {
            data = new BitmapFontData(descriptor, false);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Scene HUD Skin '" + skin.skinId() + "' BitmapFont '" + font.name
                    + "' descriptor '" + descriptor.name() + "' is malformed.", failure);
        }
        int pageCount = data.getImagePaths().length;
        if (pageCount == 0) throw new IllegalStateException("Scene HUD Skin '" + skin.skinId()
                + "' BitmapFont '" + font.name + "' has no page declarations.");

        String stem = descriptor.nameWithoutExtension();
        SceneHudBitmapFontResolution.Result resolution = atlas.resolve(stem, pageCount);
        List<SceneHudPackInputPlan.AtlasKey> keys = fontKeys(stem, pageCount, resolution);
        if (resolution == SceneHudBitmapFontResolution.Result.COMPLETE_UNINDEXED
                || resolution == SceneHudBitmapFontResolution.Result.COMPLETE_INDEXED) {
            for (SceneHudPackInputPlan.AtlasKey key : keys) {
                SceneHudPackInputPlan.AtlasEntry entry = namespace.entry(key);
                if (entry == null || !entry.hasProvenance(SceneHudPackInputPlan.ProvenanceKind.SKIN_ATLAS_REGION)) {
                    throw new IllegalStateException("Scene HUD Skin '" + skin.skinId() + "' BitmapFont '"
                            + font.name + "' cannot reuse required atlas key '" + key.displayName() + "'.");
                }
            }
            output.add(new SceneHudPackInputPlan.BitmapFontProjection(descriptorPath, keys));
            return;
        }
        if (resolution == SceneHudBitmapFontResolution.Result.INCOMPLETE) {
            throw new IllegalStateException("Scene HUD Skin '" + skin.skinId() + "' atlas has incomplete indexed"
                    + " regions for BitmapFont '" + font.name + "' descriptor '" + descriptor.name() + "'.");
        }
        for (int page = 0; page < pageCount; page++) {
            FileHandle pageFile = new FileHandle(data.getImagePath(page));
            String pagePath = canonicalProjectPath(projectDir, pageFile,
                    "BitmapFont '" + font.name + "' page " + page);
            requireClosureFile(skin, SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE, pagePath,
                    "BitmapFont page");
            namespace.register(new Candidate(keys.get(page), SceneHudPackInputPlan.MaterialKind.FULL_IMAGE,
                    pagePath, null, provenance(SceneHudPackInputPlan.ProvenanceKind.BITMAP_FONT_PAGE,
                            roots, null, null, null, null, null, null, descriptorPath, page)));
        }
        output.add(new SceneHudPackInputPlan.BitmapFontProjection(descriptorPath, keys));
    }

    private static List<SceneHudPackInputPlan.AtlasKey> fontKeys(
            String stem, int pageCount, SceneHudBitmapFontResolution.Result resolution) {
        List<SceneHudPackInputPlan.AtlasKey> keys = new ArrayList<>();
        if (pageCount == 1) {
            keys.add(new SceneHudPackInputPlan.AtlasKey(stem,
                    resolution == SceneHudBitmapFontResolution.Result.COMPLETE_INDEXED ? 0 : -1));
        } else for (int page = 0; page < pageCount; page++) {
            keys.add(new SceneHudPackInputPlan.AtlasKey(stem, page));
        }
        return List.copyOf(keys);
    }

    private static boolean isBitmapFontSection(String name) {
        return "BitmapFont".equals(name) || "com.badlogic.gdx.graphics.g2d.BitmapFont".equals(name);
    }

    private static Map<String, List<String>> imageRoots(SceneHudDependencyClosure closure) {
        Map<String, LinkedHashSet<String>> roots = new LinkedHashMap<>();
        for (SceneHudDependencyClosure.SelectedHudScreen screen : closure.selectedHudScreens()) {
            if (screen.document() == null) continue;
            collectImageRoots(screen.document().root, screen.screenId(), roots);
        }
        return immutableRoots(roots);
    }

    private static void collectImageRoots(HudNode node, String rootId,
                                           Map<String, LinkedHashSet<String>> roots) {
        if (node == null) return;
        HudImageReferences.visit(node, (ignored, fieldPath, image) ->
                collectImageRoot(image, rootId, roots));
        if (node.children != null)
            for (HudChild child : node.children) if (child != null) collectImageRoots(child.node, rootId, roots);
        if (node.table != null && node.table.rows != null)
            for (games.pixscape.runtime.hud.document.HudTableRow row : node.table.rows)
                if (row != null && row.cells != null)
                    for (games.pixscape.runtime.hud.document.HudTableCell cell : row.cells)
                        if (cell != null) collectImageRoots(cell.content, rootId, roots);
    }

    private static void collectImageRoot(HudImageData image, String rootId,
                                         Map<String, LinkedHashSet<String>> roots) {
        if (image != null && image.source == HudImageSource.REGION && image.resourceName != null) {
            roots.computeIfAbsent(image.resourceName, ignored -> new LinkedHashSet<>()).add(rootId);
        }
    }

    private static Map<String, List<String>> skinRoots(SceneHudDependencyClosure closure) {
        Map<String, LinkedHashSet<String>> roots = new LinkedHashMap<>();
        for (SceneHudDependencyClosure.SelectedHudScreen screen : closure.selectedHudScreens()) {
            String skinId = screen.asset().skinId;
            if (skinId != null && !skinId.isBlank()) roots.computeIfAbsent(
                    skinId.trim().replace('\\', '/'), ignored -> new LinkedHashSet<>()).add(screen.screenId());
        }
        return immutableRoots(roots);
    }

    private static Map<String, List<String>> immutableRoots(Map<String, LinkedHashSet<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, roots) -> result.put(key, List.copyOf(roots)));
        return Map.copyOf(result);
    }

    private static SceneHudPackInputPlan.ProjectionProvenance provenance(
            SceneHudPackInputPlan.ProvenanceKind kind, List<String> roots,
            Integer assetId, String logicalPath, String skinId, String skinAtlasPath,
            String sourceRegionName, Integer sourceRegionIndex, String descriptorPath, Integer fontPageIndex) {
        return new SceneHudPackInputPlan.ProjectionProvenance(kind,
                roots == null ? List.of() : roots, assetId, logicalPath, skinId, skinAtlasPath,
                sourceRegionName, sourceRegionIndex, descriptorPath, fontPageIndex);
    }

    private static String requiredPath(SceneHudDependencyClosure.SkinDependency skin,
                                       SceneHudDependencyClosure.FileKind kind, String label) {
        String path = optionalPath(skin, kind);
        if (path == null) throw new IllegalStateException("Scene HUD Skin '" + skin.skinId()
                + "' is missing " + label + " in its dependency closure.");
        return path;
    }

    private static String optionalPath(SceneHudDependencyClosure.SkinDependency skin,
                                       SceneHudDependencyClosure.FileKind kind) {
        return skin.files().stream().filter(file -> file.kind() == kind)
                .map(SceneHudDependencyClosure.FileDependency::projectRelativePath).findFirst().orElse(null);
    }

    private static void requireClosureFile(SceneHudDependencyClosure.SkinDependency skin,
                                           SceneHudDependencyClosure.FileKind kind,
                                           String path, String label) {
        boolean present = skin.files().stream().anyMatch(file -> file.kind() == kind
                && file.projectRelativePath().equals(path));
        if (!present) throw new IllegalStateException("Scene HUD Skin '" + skin.skinId() + "' "
                + label + " '" + path + "' is absent from its dependency closure.");
    }

    private static String requireProjectFile(FileHandle projectDir, String path, String label) {
        return canonicalProjectPath(projectDir, projectDir.child(path), label);
    }

    private static FileHandle projectFile(FileHandle projectDir, String path, String label) {
        FileHandle file = projectDir.child(path);
        canonicalProjectPath(projectDir, file, label);
        return file;
    }

    private static FileHandle requireProjectFile(FileHandle projectDir, FileHandle file, String label) {
        canonicalProjectPath(projectDir, file, label);
        return file;
    }

    private static String canonicalProjectPath(FileHandle projectDir, FileHandle file, String label) {
        if (file == null || !file.exists() || file.isDirectory()) {
            throw new IllegalStateException(label + " is missing.");
        }
        Path root = projectDir.file().toPath().toAbsolutePath().normalize();
        Path candidate = file.file().toPath().toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) throw new IllegalStateException(label + " escapes the Studio project directory.");
        return root.relativize(candidate).toString().replace('\\', '/');
    }

    private record AtlasFacts(String path, Map<String, List<TextureAtlasData.Region>> regionsByName) {
        private AtlasFacts {
            Map<String, List<TextureAtlasData.Region>> copy = new LinkedHashMap<>();
            regionsByName.forEach((name, regions) -> copy.put(name, List.copyOf(regions)));
            regionsByName = Map.copyOf(copy);
        }

        private static AtlasFacts empty() { return new AtlasFacts(null, Map.of()); }

        private SceneHudBitmapFontResolution.Result resolve(String stem, int pageCount) {
            Map<String, List<Integer>> indexes = new LinkedHashMap<>();
            regionsByName.forEach((name, regions) -> indexes.put(name,
                    regions.stream().map(region -> region.index).toList()));
            return SceneHudBitmapFontResolution.resolve(indexes, stem, pageCount);
        }
    }

    private record Candidate(SceneHudPackInputPlan.AtlasKey key,
                             SceneHudPackInputPlan.MaterialKind materialKind,
                             String sourcePath, SceneHudPackInputPlan.AtlasRegionMetadata atlasRegion,
                             SceneHudPackInputPlan.ProjectionProvenance provenance) {
        private Candidate {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(materialKind, "materialKind");
            Objects.requireNonNull(provenance, "provenance");
        }

        private SceneHudPackInputPlan.AtlasEntry entry() {
            return new SceneHudPackInputPlan.AtlasEntry(key, materialKind, sourcePath, atlasRegion,
                    List.of(provenance));
        }
    }

    private static final class Namespace {
        private final Map<SceneHudPackInputPlan.AtlasKey, SceneHudPackInputPlan.AtlasEntry> entries =
                new LinkedHashMap<>();
        // LibGDX Skin.addRegions flattens indexed atlas identities into name_index.
        private final Map<String, SceneHudPackInputPlan.AtlasKey> skinKeys = new LinkedHashMap<>();

        private void register(Candidate candidate) {
            SceneHudPackInputPlan.AtlasEntry next = candidate.entry();
            SceneHudPackInputPlan.AtlasEntry current = entries.get(next.key());
            if (current == null) {
                String skinKey = next.key().index() < 0 ? next.key().name()
                        : next.key().name() + "_" + next.key().index();
                SceneHudPackInputPlan.AtlasKey previousKey = skinKeys.get(skinKey);
                if (previousKey != null && !previousKey.equals(next.key())) {
                    SceneHudPackInputPlan.AtlasEntry previous = entries.get(previousKey);
                    throw new IllegalStateException("Scene HUD Skin registration key collision: '" + skinKey
                            + "'\n  first AtlasKey: " + previousKey + "\n    provenance: " + previous.provenance()
                            .stream().map(SceneHudPackInputPlan.ProjectionProvenance::describe).toList()
                            + "\n  second AtlasKey: " + next.key() + "\n    provenance: " + next.provenance()
                            .stream().map(SceneHudPackInputPlan.ProjectionProvenance::describe).toList());
                }
                skinKeys.put(skinKey, next.key());
                entries.put(next.key(), next);
            } else if (equivalent(current, next)) {
                List<SceneHudPackInputPlan.ProjectionProvenance> provenance = new ArrayList<>(current.provenance());
                if (!provenance.contains(candidate.provenance())) provenance.add(candidate.provenance());
                entries.put(current.key(), new SceneHudPackInputPlan.AtlasEntry(current.key(), current.materialKind(),
                        current.sourcePath(), current.atlasRegion(), provenance));
            } else {
                throw new IllegalStateException("Scene HUD atlas key collision: " + next.key().displayName()
                        + "\n  first: " + current.provenance().get(0).describe()
                        + "\n  second: " + next.provenance().get(0).describe());
            }
        }

        private SceneHudPackInputPlan.AtlasEntry entry(SceneHudPackInputPlan.AtlasKey key) {
            return entries.get(key);
        }

        private List<SceneHudPackInputPlan.AtlasEntry> entries() {
            return entries.values().stream().sorted(java.util.Comparator
                    .comparing((SceneHudPackInputPlan.AtlasEntry entry) -> entry.materialKind().ordinal())
                    .thenComparing(entry -> entry.key().name())
                    .thenComparingInt(entry -> entry.key().index())).toList();
        }

        private static boolean equivalent(SceneHudPackInputPlan.AtlasEntry first,
                                          SceneHudPackInputPlan.AtlasEntry second) {
            return first.materialKind() == second.materialKind()
                    && Objects.equals(first.sourcePath(), second.sourcePath())
                    && Objects.equals(first.atlasRegion(), second.atlasRegion());
        }
    }
}
