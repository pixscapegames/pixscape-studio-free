package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import games.pixscape.runtime.hud.HudTextureProfile;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.service.atlas.AtlasPackingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static games.pixscape.studio.service.runtimeavailability.SceneHudPackInputPlan.*;
import static games.pixscape.studio.service.runtimeavailability.SceneHudPackMaterializationManifest.*;

/** Synchronous, file-only Scene HUD build. Caller serializes builds for the same output directory.
 * Packer placement is physical; the manifest is the only authority for Runtime identity.
 * No editor, Texture, TextureAtlas, export, or async state is retained. */
public final class SceneHudAtlasBuilder {
    private static final String ATLAS = SceneHudEnvironmentPaths.ATLAS_FILE;
    private static final HudTextureProfile PROFILE = HudTextureProfile.forId(HudTextureProfile.DEFAULT_ID);
    // Region parser fields: generated placement/rotation/index and manifest original-size/offsets.
    private static final Set<String> STRUCTURAL = Set.of("xy", "size", "bounds", "rotate", "index",
            "offset", "orig", "offsets");
    // Source page/identity/flip facts never belong in generated region custom values.
    private static final Set<String> SOURCE_ONLY = Set.of("page", "name", "flip", "format", "filter", "repeat", "pma");

    @FunctionalInterface interface Packer {
        void pack(FileHandle input, FileHandle output, String name, HudTextureProfile profile);
    }
    @FunctionalInterface interface DescriptorWriter {
        void write(FileHandle descriptor, TextureAtlasData generated, SceneHudPackMaterializationManifest manifest);
    }

    /** Paths refer only to published artifacts, never the candidate directory. */
    public record Result(String atlasDescriptorPath, List<String> pagePaths, int logicalRegionCount) {
        public Result { pagePaths = List.copyOf(pagePaths); }
        public int pageCount() { return pagePaths.size(); }
    }

    private final Packer packer;
    private final DescriptorWriter writer;

    public SceneHudAtlasBuilder() { this(AtlasPackingService::packHud, SceneHudAtlasBuilder::writeDescriptor); }
    SceneHudAtlasBuilder(Packer packer, DescriptorWriter writer) {
        this.packer = java.util.Objects.requireNonNull(packer);
        this.writer = java.util.Objects.requireNonNull(writer);
    }

    public Result build(FileHandle materializedInputDirectory, SceneHudPackMaterializationManifest manifest,
                        FileHandle outputDirectory) {
        preflight(materializedInputDirectory, manifest, outputDirectory);
        FileHandle candidate = AtomicDirectoryPublication.createCandidate(outputDirectory);
        Throwable primary = null;
        String stage = "packing";
        try {
            packer.pack(materializedInputDirectory, candidate, "hud", PROFILE);
            stage = "temporary atlas validation";
            TextureAtlasData generated = parse(candidate);
            Map<AtlasKey, TextureAtlasData.Region> physical = validateGenerated(candidate, generated, manifest);
            stage = "descriptor reconstruction";
            writer.write(candidate.child(ATLAS), generated, manifest);
            stage = "final atlas validation";
            TextureAtlasData reconstructed = parse(candidate);
            validateFinal(candidate, reconstructed, generated, physical, manifest);
            List<String> pages = new ArrayList<>();
            for (TextureAtlasData.Page page : reconstructed.getPages())
                pages.add(absolute(outputDirectory.child(page.name)));
            Result result = new Result(absolute(outputDirectory.child(ATLAS)), pages, manifest.entries().size());
            stage = "publication";
            try (var publication = AtomicDirectoryPublication.publish(candidate, outputDirectory)) {
                publication.commit();
            }
            return result;
        } catch (RuntimeException failure) {
            var reported = new IllegalStateException("Scene HUD atlas " + stage + " failed: " + failure.getMessage(), failure);
            primary = reported;
            throw reported;
        } catch (Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try { AtomicDirectoryPublication.discardCandidate(candidate); }
            catch (RuntimeException cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private static void preflight(FileHandle input, SceneHudPackMaterializationManifest manifest, FileHandle output) {
        if (input == null || output == null || manifest == null)
            throw new IllegalArgumentException("Scene HUD input directory, manifest and output directory are required.");
        Path inputPath = input.file().toPath().toAbsolutePath().normalize();
        Path outputPath = output.file().toPath().toAbsolutePath().normalize();
        rejectSymlinks(inputPath);
        rejectSymlinks(outputPath);
        if (!Files.isDirectory(inputPath)) throw new IllegalArgumentException("Materialized HUD input directory is missing.");
        if (inputPath.startsWith(outputPath) || outputPath.startsWith(inputPath))
            throw new IllegalArgumentException("Scene HUD input and output directories must not overlap.");
        if (manifest.entries().isEmpty()) throw new IllegalArgumentException("Scene HUD manifest has no entries.");
        Map<String, MaterializedEntry> inputs = new LinkedHashMap<>();
        Map<AtlasKey, MaterializedEntry> packerKeys = new LinkedHashMap<>();
        Map<AtlasKey, MaterializedEntry> finalKeys = new LinkedHashMap<>();
        Map<String, AtlasKey> skinKeys = new LinkedHashMap<>();
        for (MaterializedEntry entry : manifest.entries()) {
            AtlasRegionMetadata source = entry.plannedEntry().atlasRegion();
            if (source != null && source.pagePremultipliedAlpha())
                throw new IllegalStateException("Scene HUD requires straight-alpha pages (pma: false); source-PMA Skin page '"
                        + source.sourcePagePath() + "' region " + entry.intendedKey() + ": "
                        + entry.provenance().stream().map(ProjectionProvenance::describe).toList());
            if (!entry.inputPath().matches("entry-[0-9]{6,}\\.png")
                    || !entry.packerKey().equals(new AtlasKey(new FileHandle(entry.inputPath()).nameWithoutExtension(), -1)))
                throw new IllegalArgumentException("Invalid materialized physical key/path: " + entry.inputPath());
            FileHandle file = input.child(entry.inputPath());
            if (Files.isSymbolicLink(file.file().toPath()) || !file.exists() || file.isDirectory())
                throw new IllegalArgumentException("Materialized HUD input is missing or unsafe: " + entry.inputPath());
            if (entry.pixelWidth() <= 0 || entry.pixelHeight() <= 0)
                throw new IllegalArgumentException("Invalid materialized HUD dimensions: " + entry.intendedKey());
            if (inputs.putIfAbsent(entry.inputPath(), entry) != null
                    || packerKeys.putIfAbsent(entry.packerKey(), entry) != null
                    || finalKeys.putIfAbsent(entry.intendedKey(), entry) != null)
                throw new IllegalArgumentException("Duplicate Scene HUD manifest identity: " + entry.intendedKey());
            // displayName is exactly Skin.addRegions' string key, NOT atlas descriptor identity.
            AtlasKey previous = skinKeys.putIfAbsent(entry.intendedKey().displayName(), entry.intendedKey());
            if (previous != null && !previous.equals(entry.intendedKey()))
                throw new IllegalArgumentException("Invalid Scene HUD Skin registration collision: " + previous + " / " + entry.intendedKey());
        }
        try (var children = Files.list(inputPath)) {
            if (children.anyMatch(path -> !inputs.containsKey(path.getFileName().toString())))
                throw new IllegalArgumentException("Materialized HUD directory contains unexpected inputs/configuration.");
        } catch (IOException failure) { throw new IllegalStateException("Unable to inspect materialized HUD inputs", failure); }
    }

    private static void rejectSymlinks(Path path) {
        for (Path ancestor = path; ancestor != null; ancestor = ancestor.getParent())
            if (Files.isSymbolicLink(ancestor)) throw new IllegalArgumentException("Scene HUD directory has a symlink: " + ancestor);
    }

    private static TextureAtlasData parse(FileHandle directory) {
        FileHandle descriptor = directory.child(ATLAS);
        if (!descriptor.exists()) throw new IllegalStateException("TexturePacker produced no HUD atlas descriptor.");
        return new TextureAtlasData(descriptor, directory, false);
    }

    private static void validatePages(FileHandle directory, TextureAtlasData data) {
        Set<String> names = new java.util.HashSet<>();
        for (TextureAtlasData.Page page : data.getPages()) {
            if (page.name == null || !page.name.matches("hud(?:[0-9]+)?\\.png") || !names.add(page.name)
                    || !absolute(directory.child(page.name)).equals(absolute(page.textureFile)))
                throw new IllegalStateException("Invalid generated HUD page identity: " + page.name);
            if (Files.isSymbolicLink(page.textureFile.file().toPath()))
                throw new IllegalStateException("Generated HUD page is a symlink: " + page.name);
            if (page.pma || page.format != PROFILE.outputFormat()
                    || page.width != PROFILE.pageWidth() || page.height != PROFILE.pageHeight())
                throw new IllegalStateException("Scene HUD requires RGBA8888 straight-alpha pages (pma: false).");
        }
        AtlasPackingService.validateHudPages(data, PROFILE);
        for (FileHandle file : directory.list()) if (!file.name().equals(ATLAS) && !names.contains(file.name()))
            throw new IllegalStateException("Unexpected generated HUD artifact: " + file.name());
    }

    private static Map<AtlasKey, TextureAtlasData.Region> regions(TextureAtlasData data) {
        Map<AtlasKey, TextureAtlasData.Region> result = new LinkedHashMap<>();
        for (TextureAtlasData.Region region : data.getRegions()) {
            AtlasKey key = new AtlasKey(region.name, region.index);
            if (result.putIfAbsent(key, region) != null) throw new IllegalStateException("Duplicate HUD atlas key: " + key);
            int physicalWidth = region.rotate ? region.height : region.width;
            int physicalHeight = region.rotate ? region.width : region.height;
            if ((region.degrees != 0 && region.degrees != 90) || region.rotate != (region.degrees == 90)
                    || region.flip || region.width <= 0 || region.height <= 0 || region.left < 0 || region.top < 0
                    || (long) region.left + physicalWidth > region.page.width
                    || (long) region.top + physicalHeight > region.page.height)
                throw new IllegalStateException("Invalid generated HUD region geometry: " + key);
        }
        return result;
    }

    private static Map<AtlasKey, TextureAtlasData.Region> validateGenerated(FileHandle directory, TextureAtlasData data,
                                                                           SceneHudPackMaterializationManifest manifest) {
        validatePages(directory, data);
        Map<AtlasKey, TextureAtlasData.Region> regions = regions(data);
        Set<AtlasKey> expected = new java.util.LinkedHashSet<>();
        for (MaterializedEntry entry : manifest.entries()) {
            expected.add(entry.packerKey());
            TextureAtlasData.Region region = regions.get(entry.packerKey());
            if (region == null) throw new IllegalStateException("Missing generated HUD packer key: " + entry.packerKey());
            if (region.originalWidth != entry.pixelWidth() || region.originalHeight != entry.pixelHeight()
                    || region.offsetX < 0 || region.offsetY < 0
                    || region.offsetX + region.width > entry.pixelWidth() || region.offsetY + region.height > entry.pixelHeight())
                throw new IllegalStateException("Generated HUD pixels do not match materialized dimensions: " + entry.packerKey());
        }
        if (!regions.keySet().equals(expected)) throw new IllegalStateException("Unexpected generated HUD keys: " + regions.keySet());
        return regions;
    }

    /** Structured LibGDX 1.14.2 writer. Region names and index fields are deliberately separate.
     * split/pad and other 1..4 integer custom values survive; reserved fields cannot shadow placement.
     * Unsupported custom arity/unsafe names fail rather than silently losing metadata. */
    static void writeDescriptor(FileHandle descriptor, TextureAtlasData generated, SceneHudPackMaterializationManifest manifest) {
        Map<AtlasKey, TextureAtlasData.Region> physical = regions(generated);
        StringBuilder text = new StringBuilder();
        for (TextureAtlasData.Page page : generated.getPages()) {
            if (!text.isEmpty()) text.append('\n');
            text.append(page.name).append("\nsize: ").append((int) page.width).append(", ").append((int) page.height)
                    .append("\nformat: ").append(page.format).append("\nfilter: ").append(page.minFilter).append(", ")
                    .append(page.magFilter).append("\nrepeat: ").append(repeat(page)).append("\npma: false\n");
            for (MaterializedEntry entry : manifest.entries()) {
                TextureAtlasData.Region region = physical.get(entry.packerKey());
                if (region.page != page) continue;
                safeToken(entry.intendedKey().name());
                SemanticMetadata semantic = entry.metadata();
                int offsetX = integerOffset(semantic.offsetX() + region.offsetX);
                int offsetY = integerOffset(semantic.offsetY() + region.offsetY);
                // Source offsets are semantic placement, not page bounds. LibGDX permits signed
                // offsets; do not clamp/reinterpret them as generated rectangle coordinates.
                if (semantic.originalWidth() < entry.pixelWidth() || semantic.originalHeight() < entry.pixelHeight())
                    throw new IllegalStateException("Invalid semantic HUD dimensions: " + entry.intendedKey());
                text.append(entry.intendedKey().name()).append("\n  rotate: ").append(region.degrees)
                        .append("\n  bounds: ").append(region.left).append(", ").append(region.top).append(", ")
                        .append(region.width).append(", ").append(region.height)
                        .append("\n  offsets: ").append(offsetX).append(", ").append(offsetY).append(", ")
                        .append(semantic.originalWidth()).append(", ").append(semantic.originalHeight())
                        .append("\n  index: ").append(entry.intendedKey().index()).append('\n');
                for (var value : semanticValues(semantic).entrySet()) {
                    text.append("  ").append(value.getKey()).append(": ");
                    for (int i = 0; i < value.getValue().size(); i++) {
                        if (i > 0) text.append(", ");
                        text.append(value.getValue().get(i));
                    }
                    text.append('\n');
                }
            }
        }
        descriptor.writeString(text.toString(), false, "UTF-8");
    }

    private static String repeat(TextureAtlasData.Page page) {
        boolean x = page.uWrap == com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat;
        boolean y = page.vWrap == com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat;
        return x ? (y ? "xy" : "x") : (y ? "y" : "none");
    }

    private static Map<String, List<Integer>> semanticValues(SemanticMetadata metadata) {
        Map<String, List<Integer>> values = new TreeMap<>();
        for (var entry : metadata.values().entrySet()) {
            if (STRUCTURAL.contains(entry.getKey()) || SOURCE_ONLY.contains(entry.getKey())) continue;
            safeToken(entry.getKey());
            if (entry.getValue().isEmpty() || entry.getValue().size() > 4
                    || ((entry.getKey().equals("split") || entry.getKey().equals("pad")) && entry.getValue().size() != 4))
                throw new IllegalStateException("Unsupported HUD integer metadata: " + entry.getKey());
            values.put(entry.getKey(), entry.getValue());
        }
        return values;
    }

    private static void safeToken(String name) {
        if (name == null || name.isBlank() || !name.equals(name.trim()) || name.indexOf(':') >= 0
                || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalStateException("HUD atlas name/field is not safely representable: " + name);
    }

    private static int integerOffset(float value) {
        if (!Float.isFinite(value) || value != Math.rint(value) || (double) value > Integer.MAX_VALUE || value < Integer.MIN_VALUE)
            throw new IllegalStateException("HUD atlas offset is not an integer: " + value);
        return (int) value;
    }

    private static void validateFinal(FileHandle directory, TextureAtlasData data, TextureAtlasData generated,
                                      Map<AtlasKey, TextureAtlasData.Region> physical, SceneHudPackMaterializationManifest manifest) {
        validatePages(directory, data);
        if (data.getPages().size != generated.getPages().size) throw new IllegalStateException("Final HUD page count changed.");
        for (int i = 0; i < data.getPages().size; i++) {
            TextureAtlasData.Page actual = data.getPages().get(i), expected = generated.getPages().get(i);
            if (!actual.name.equals(expected.name) || actual.width != expected.width || actual.height != expected.height
                    || actual.format != expected.format || actual.minFilter != expected.minFilter || actual.magFilter != expected.magFilter
                    || actual.uWrap != expected.uWrap || actual.vWrap != expected.vWrap || actual.pma)
                throw new IllegalStateException("Final HUD page policy changed: " + actual.name);
        }
        Map<AtlasKey, TextureAtlasData.Region> finalRegions = regions(data);
        Set<AtlasKey> expected = new java.util.LinkedHashSet<>();
        Map<String, AtlasKey> skinKeys = new LinkedHashMap<>();
        for (MaterializedEntry entry : manifest.entries()) {
            expected.add(entry.intendedKey());
            skinKeys.put(entry.intendedKey().displayName(), entry.intendedKey());
            TextureAtlasData.Region actual = finalRegions.get(entry.intendedKey()), packed = physical.get(entry.packerKey());
            if (actual == null) throw new IllegalStateException("Missing final HUD key: " + entry.intendedKey());
            if (!actual.page.name.equals(packed.page.name) || actual.left != packed.left || actual.top != packed.top
                    || actual.width != packed.width || actual.height != packed.height || actual.degrees != packed.degrees
                    || actual.rotate != packed.rotate || actual.originalWidth != entry.metadata().originalWidth()
                    || actual.originalHeight != entry.metadata().originalHeight()
                    || actual.offsetX != integerOffset(entry.metadata().offsetX() + packed.offsetX)
                    || actual.offsetY != integerOffset(entry.metadata().offsetY() + packed.offsetY)
                    || !customValues(actual).equals(semanticValues(entry.metadata())))
                throw new IllegalStateException("Final HUD geometry/semantics changed: " + entry.intendedKey());
        }
        if (!finalRegions.keySet().equals(expected)) throw new IllegalStateException("Unexpected final HUD keys (temporary identity leakage): " + finalRegions.keySet());
        for (BitmapFontMaterialization font : manifest.bitmapFonts()) {
            List<AtlasKey> required = font.requirement().keys();
            if (required.isEmpty() || !font.entries().stream().map(MaterializedEntry::intendedKey).toList().equals(required)
                    || !finalRegions.keySet().containsAll(required))
                throw new IllegalStateException("Final HUD BitmapFont requirements missing: " + font.requirement());
            if (isStandaloneBitmapFont(font)) continue;
            String stem = new FileHandle(font.requirement().descriptorPath()).nameWithoutExtension();
            boolean indexed = skinKeys.containsKey(stem + "_0");
            for (int page = 0; page < required.size(); page++) {
                String lookup = indexed ? stem + "_" + page : stem;
                if ((!indexed && required.size() != 1) || !required.get(page).equals(skinKeys.get(lookup)))
                    throw new IllegalStateException("Final HUD BitmapFont Skin lookup does not resolve intended key: "
                            + font.requirement().descriptorPath() + " lookup '" + lookup + "'");
            }
        }
    }

    private static boolean isStandaloneBitmapFont(BitmapFontMaterialization font) {
        return font.entries().stream().allMatch(entry -> entry.provenance().stream().anyMatch(provenance ->
                provenance.kind() == ProvenanceKind.BITMAP_FONT_PAGE && provenance.assetId() != null));
    }

    private static Map<String, List<Integer>> customValues(TextureAtlasData.Region region) {
        Map<String, List<Integer>> values = new TreeMap<>();
        if (region.names != null) for (int i = 0; i < region.names.length; i++) {
            List<Integer> previous = values.putIfAbsent(region.names[i], Arrays.stream(region.values[i]).boxed().toList());
            if (previous != null) throw new IllegalStateException("Duplicate final HUD custom field: " + region.names[i]);
        }
        return values;
    }

    private static String absolute(FileHandle file) { return file.file().toPath().toAbsolutePath().normalize().toString().replace('\\', '/'); }
}
