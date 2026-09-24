package games.pixscape.studio.service.runtimeavailability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import games.pixscape.studio.service.runtimeavailability.SceneHudPackInputPlan.*;

/** In-memory reconstruction contract; paths are relative to the published input directory.
 * No final page or coordinates are known here. No mutable editor/GL objects are retained. */
public record SceneHudPackMaterializationManifest(
        List<String> rootIds, List<MaterializedEntry> entries,
        List<BitmapFontMaterialization> bitmapFonts) {
    public SceneHudPackMaterializationManifest {
        rootIds = List.copyOf(rootIds);
        entries = List.copyOf(entries);
        bitmapFonts = List.copyOf(bitmapFonts);
    }

    /** Semantic geometry before any additional trimming by the future packer.
     * Restore original size and compose these offsets with any new packer trim offsets.
     * Keep the future packer's page, xy and rotation, NOT the source atlas rotation. */
    public record SemanticMetadata(int originalWidth, int originalHeight, float offsetX, float offsetY,
                                   Map<String, List<Integer>> values) {
        public SemanticMetadata {
            Map<String, List<Integer>> copy = new LinkedHashMap<>();
            values.forEach((name, value) -> copy.put(name, List.copyOf(value)));
            values = Collections.unmodifiableMap(copy);
        }
        public List<Integer> split() { return values.getOrDefault("split", List.of()); }
        public List<Integer> pad() { return values.getOrDefault("pad", List.of()); }
    }

    /** packerKey is the generated temporary lookup key, never the final Runtime identity.
     * Input pixels have degrees=0/rotate=false; source facts remain in plannedEntry.atlasRegion(). */
    public record MaterializedEntry(String inputPath, AtlasKey packerKey, AtlasEntry plannedEntry,
                                    int pixelWidth, int pixelHeight, boolean sourceRotationNormalized,
                                    SemanticMetadata metadata) {
        public MaterializedEntry {
            Objects.requireNonNull(inputPath, "inputPath");
            Objects.requireNonNull(packerKey, "packerKey");
            Objects.requireNonNull(plannedEntry, "plannedEntry");
            Objects.requireNonNull(metadata, "metadata");
        }
        public AtlasKey intendedKey() { return plannedEntry.key(); }
        public MaterialKind materialKind() { return plannedEntry.materialKind(); }
        public List<ProjectionProvenance> provenance() { return plannedEntry.provenance(); }
        public int materializedDegrees() { return 0; }
        public boolean materializedRotate() { return false; }
    }

    /** Entries are the exact same immutable instances used by the physical-entry list, in page order. */
    public record BitmapFontMaterialization(BitmapFontProjection requirement, List<MaterializedEntry> entries) {
        public BitmapFontMaterialization {
            Objects.requireNonNull(requirement, "requirement");
            entries = List.copyOf(entries);
        }
    }

    public MaterializedEntry entry(AtlasKey intendedKey) {
        return entries.stream().filter(entry -> entry.intendedKey().equals(intendedKey)).findFirst().orElse(null);
    }
    public MaterializedEntry packerEntry(AtlasKey generatedKey) {
        return entries.stream().filter(entry -> entry.packerKey().equals(generatedKey)).findFirst().orElse(null);
    }
}
