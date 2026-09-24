package games.pixscape.studio.service.runtimeavailability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, file-free description of the entries a future Scene HUD pack must materialize. */
public final class SceneHudPackInputPlan {
    /** The TextureAtlas lookup identity: an ordinary region has index {@value #UNINDEXED}. */
    public record AtlasKey(String name, int index) {
        public static final int UNINDEXED = -1;

        public AtlasKey {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Atlas key name is required.");
            if (index < UNINDEXED) throw new IllegalArgumentException("Atlas key index must be at least -1.");
        }

        public String displayName() { return index == UNINDEXED ? name : name + "_" + index; }
    }

    public enum MaterialKind {
        FULL_IMAGE,
        SKIN_ATLAS_REGION,
        INTERNAL_WHITE_PIXEL,
        INTERNAL_BUILT_IN_LABEL_FONT
    }

    public enum ProvenanceKind {
        REGION_IMAGE,
        SKIN_ATLAS_REGION,
        BITMAP_FONT_PAGE,
        INTERNAL_RESOURCE,
        INTERNAL_BUILT_IN_LABEL_FONT
    }

    /** All GL-free TextureAtlasData.Region facts a later materializer must preserve or restore. */
    public record AtlasRegionMetadata(
            String sourcePagePath, String sourcePageName, boolean pagePremultipliedAlpha,
            int left, int top, int width, int height,
            int degrees, boolean rotate, boolean flip,
            int originalWidth, int originalHeight, float offsetX, float offsetY,
            Map<String, List<Integer>> values) {
        public AtlasRegionMetadata {
            if (sourcePagePath == null || sourcePagePath.isBlank()) {
                throw new IllegalArgumentException("Source atlas page path is required.");
            }
            values = immutableValues(values);
        }

        public List<Integer> split() { return values.getOrDefault("split", List.of()); }
        public List<Integer> pad() { return values.getOrDefault("pad", List.of()); }
    }

    /** Why an entry is reachable. Null fields are deliberately inapplicable to a provenance kind. */
    public record ProjectionProvenance(
            ProvenanceKind kind, List<String> hudRoots,
            Integer assetId, String assetLogicalPath,
            String skinId, String skinAtlasPath, String sourceRegionName, Integer sourceRegionIndex,
            String bitmapFontDescriptorPath, Integer bitmapFontPageIndex) {
        public ProjectionProvenance {
            Objects.requireNonNull(kind, "kind");
            hudRoots = List.copyOf(hudRoots == null ? List.of() : hudRoots);
        }

        public String describe() {
            String roots = hudRoots.isEmpty() ? "" : " roots=" + String.join(",", hudRoots);
            return switch (kind) {
            case REGION_IMAGE -> "REGION Image asset " + assetId + " '" + assetLogicalPath + "'" + roots;
            case SKIN_ATLAS_REGION -> "Skin '" + skinId + "' atlas '" + skinAtlasPath
                    + "' region '" + sourceRegionName + "' index " + sourceRegionIndex + roots;
            case BITMAP_FONT_PAGE -> (assetId != null ? "BitmapFont Asset " + assetId + " " : "BitmapFont ")
                    + "descriptor '" + bitmapFontDescriptorPath + "' page "
                    + bitmapFontPageIndex + roots;
            case INTERNAL_RESOURCE -> "Pixscape internal white pixel";
            case INTERNAL_BUILT_IN_LABEL_FONT -> "Pixscape built-in Label font" + roots;
            };
        }
    }

    /** One final key and the exact source material a later materializer must use. */
    public record AtlasEntry(AtlasKey key, MaterialKind materialKind, String sourcePath,
                             AtlasRegionMetadata atlasRegion, List<ProjectionProvenance> provenance) {
        public AtlasEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(materialKind, "materialKind");
            if (materialKind == MaterialKind.INTERNAL_WHITE_PIXEL
                    || materialKind == MaterialKind.INTERNAL_BUILT_IN_LABEL_FONT) {
                if (sourcePath != null || atlasRegion != null) {
                    throw new IllegalArgumentException("Internal entries have no authored source.");
                }
            } else if (sourcePath == null || sourcePath.isBlank()) {
                throw new IllegalArgumentException("Authored entry source path is required.");
            }
            if (materialKind == MaterialKind.SKIN_ATLAS_REGION && atlasRegion == null) {
                throw new IllegalArgumentException("Skin atlas entries require region metadata.");
            }
            if (materialKind != MaterialKind.SKIN_ATLAS_REGION && atlasRegion != null) {
                throw new IllegalArgumentException("Only skin atlas entries carry region metadata.");
            }
            provenance = List.copyOf(provenance == null ? List.of() : provenance);
            if (provenance.isEmpty()) throw new IllegalArgumentException("Entry provenance is required.");
        }

        public boolean hasProvenance(ProvenanceKind kind) {
            return provenance.stream().anyMatch(candidate -> candidate.kind() == kind);
        }
    }

    /** A font descriptor and the already-planned TextureAtlas keys satisfying it. */
    public record BitmapFontProjection(String descriptorPath, List<AtlasKey> keys) {
        public BitmapFontProjection {
            if (descriptorPath == null || descriptorPath.isBlank()) {
                throw new IllegalArgumentException("Font descriptor path is required.");
            }
            keys = List.copyOf(keys);
        }
    }

    private final List<String> rootIds;
    private final List<AtlasEntry> entries;
    private final List<BitmapFontProjection> bitmapFonts;
    private final Map<AtlasKey, AtlasEntry> entriesByKey;

    SceneHudPackInputPlan(List<String> rootIds, List<AtlasEntry> entries,
                          List<BitmapFontProjection> bitmapFonts) {
        this.rootIds = List.copyOf(rootIds);
        this.entries = List.copyOf(entries);
        this.bitmapFonts = List.copyOf(bitmapFonts);
        Map<AtlasKey, AtlasEntry> byKey = new LinkedHashMap<>();
        for (AtlasEntry entry : this.entries) {
            if (byKey.putIfAbsent(entry.key(), entry) != null) {
                throw new IllegalArgumentException("Plan contains duplicate final key " + entry.key().displayName());
            }
        }
        this.entriesByKey = Collections.unmodifiableMap(byKey);
    }

    public List<String> rootIds() { return rootIds; }
    public List<AtlasEntry> entries() { return entries; }
    public List<BitmapFontProjection> bitmapFonts() { return bitmapFonts; }
    public AtlasEntry entry(AtlasKey key) { return entriesByKey.get(key); }

    public List<AtlasEntry> regionImageEntries() { return entriesFor(ProvenanceKind.REGION_IMAGE); }
    public List<AtlasEntry> skinAtlasRegionEntries() { return entriesFor(ProvenanceKind.SKIN_ATLAS_REGION); }
    public List<AtlasEntry> fontPageEntries() { return entriesFor(ProvenanceKind.BITMAP_FONT_PAGE); }
    public List<AtlasEntry> internalEntries() { return entriesFor(ProvenanceKind.INTERNAL_RESOURCE); }

    private List<AtlasEntry> entriesFor(ProvenanceKind kind) {
        return entries.stream().filter(entry -> entry.hasProvenance(kind)).toList();
    }

    private static Map<String, List<Integer>> immutableValues(Map<String, List<Integer>> source) {
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        if (source != null) source.forEach((name, value) -> copy.put(name, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }
}
