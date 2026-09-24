package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import games.pixscape.studio.helper.InternalAssets;
import games.pixscape.studio.service.atlas.AtlasPackingService;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static games.pixscape.studio.service.runtimeavailability.SceneHudPackInputPlan.*;
import static games.pixscape.studio.service.runtimeavailability.SceneHudPackMaterializationManifest.*;
import static org.junit.Assert.*;

public class SceneHudAtlasBuilderTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private static final int[] PIXELS = {0xff0000ff, 0x00ff0080, 0x0000ffff, 0x123456ff, 0xffffffff, 0x11223344};
    private final SceneHudAtlasBuilder builder = new SceneHudAtlasBuilder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
    }

    @Test public void realPackerRestoresLogicalKeysWithoutSuffixInferenceOrGl() throws Exception {
        FileHandle root = root();
        png(root.child("source.png"), 2, 3, PIXELS);
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(
                image("logo", -1, "source.png"), image("legitimate_0", -1, "source.png"),
                image("animation", 2, "source.png"), white()), List.of());
        SceneHudAtlasBuilder.Result result = builder.build(root.child("inputs"), manifest, root.child("output"));
        Map<AtlasKey, TextureAtlasData.Region> regions = parsed(root.child("output"));
        assertEquals(List.of(key("logo", -1), key("legitimate_0", -1), key("animation", 2), white().key())
                .stream().collect(java.util.stream.Collectors.toSet()), regions.keySet());
        assertEquals(4, result.logicalRegionCount());
        assertEquals(1, result.pageCount());
        assertTrue(result.atlasDescriptorPath().endsWith("/output/hud.atlas"));
        assertEquals(List.of(root.child("output/hud.png").path().replace('\\', '/')), result.pagePaths());
        assertFalse(root.child("output/hud.atlas").readString().contains("entry-"));
        assertFalse(result.atlasDescriptorPath().contains(".tmp"));
        assertNull(Gdx.gl);
        assertPixels(root.child("output"), regions.get(key("logo", -1)), PIXELS);
        assertPixels(root.child("output"), regions.get(white().key()), new int[]{0xffffffff});
        assertFalse(data(root.child("output")).getPages().first().pma);
        try { result.pagePaths().add("mutable"); fail("immutable"); } catch (UnsupportedOperationException expected) { }
        noCandidates(root);
    }

    @Test public void generatedPlacementAndNormalizedRotationPreserveSemanticGeometryAndValues() throws Exception {
        FileHandle root = root();
        png(root.child("source.png"), 2, 3, PIXELS);
        Map<String, List<Integer>> values = new LinkedHashMap<>();
        values.put("split", List.of(0, 1, 1, 1));
        values.put("pad", List.of(1, 0, 0, 1));
        values.put("custom", List.of(7, -4, 12, 0));
        values.put("bounds", List.of(99, 99, 1, 1));
        values.put("offsets", List.of(99, 99, 1, 1));
        values.put("pma", List.of(1));
        AtlasEntry rotated = skin(root, "rotated", -1, "skin-page.png", true, false, 9, 10, 2, 3, values);
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(rotated, image("ordinary", -1, "source.png")), List.of());
        assertTrue(manifest.entries().get(0).sourceRotationNormalized());
        Map<AtlasKey, TextureAtlasData.Region> generated = new LinkedHashMap<>();
        SceneHudAtlasBuilder recording = new SceneHudAtlasBuilder((input, output, name, profile) -> {
            AtlasPackingService.packHud(input, output, name, profile);
            generated.putAll(parsed(output));
        }, SceneHudAtlasBuilder::writeDescriptor);
        recording.build(root.child("inputs"), manifest, root.child("output"));
        TextureAtlasData.Region actual = parsed(root.child("output")).get(key("rotated", -1));
        TextureAtlasData.Region packed = generated.get(manifest.entries().get(0).packerKey());
        assertEquals(packed.page.name, actual.page.name);
        assertEquals(packed.left, actual.left);
        assertEquals(packed.top, actual.top);
        assertEquals(packed.width, actual.width);
        assertEquals(packed.height, actual.height);
        assertFalse(actual.rotate);
        assertEquals(0, actual.degrees);
        assertEquals(9, actual.originalWidth);
        assertEquals(10, actual.originalHeight);
        assertEquals(2, actual.offsetX, 0);
        assertEquals(3, actual.offsetY, 0);
        assertArrayEquals(new int[]{0, 1, 1, 1}, actual.findValue("split"));
        assertArrayEquals(new int[]{1, 0, 0, 1}, actual.findValue("pad"));
        assertArrayEquals(new int[]{7, -4, 12, 0}, actual.findValue("custom"));
        assertNull(actual.findValue("bounds"));
        assertNull(actual.findValue("offsets"));
        assertNull(actual.findValue("pma"));
        assertPixels(root.child("output"), actual, PIXELS);
    }

    @Test public void signedSemanticOffsetsAreRestoredWithoutBecomingPageCoordinates() throws Exception {
        FileHandle root = root();
        AtlasEntry entry = skin(root, "signed", -1, "page.png", false, false, 9, 10, -2, 12, Map.of());
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(entry), List.of());
        builder.build(root.child("inputs"), manifest, root.child("output"));
        TextureAtlasData.Region region = parsed(root.child("output")).get(entry.key());
        assertEquals(-2, region.offsetX, 0);
        assertEquals(12, region.offsetY, 0);
        assertTrue(region.left >= 0);
        assertTrue(region.top >= 0);
        assertPixels(root.child("output"), region, PIXELS);
    }

    @Test public void currentAliasingEmitsEveryPhysicalKeyAndRetainsDifferentLogicalSemantics() throws Exception {
        FileHandle root = root();
        AtlasEntry first = skin(root, "first", -1, "a.png", false, false, 9, 10, 1, 2, Map.of("custom", List.of(11)));
        AtlasEntry second = skin(root, "second", 0, "b.png", false, false, 12, 13, 3, 4, Map.of("custom", List.of(22)));
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(first, second), List.of());
        assertArrayEquals(root.child("inputs/entry-000000.png").readBytes(), root.child("inputs/entry-000001.png").readBytes());
        Map<AtlasKey, TextureAtlasData.Region> generated = new LinkedHashMap<>();
        new SceneHudAtlasBuilder((input, output, name, profile) -> {
            AtlasPackingService.packHud(input, output, name, profile);
            generated.putAll(parsed(output));
        }, SceneHudAtlasBuilder::writeDescriptor).build(root.child("inputs"), manifest, root.child("output"));
        assertEquals(2, generated.size());
        TextureAtlasData.Region a = generated.get(manifest.entries().get(0).packerKey());
        TextureAtlasData.Region b = generated.get(manifest.entries().get(1).packerKey());
        assertEquals(a.page.name, b.page.name);
        assertEquals(a.left, b.left);
        assertEquals(a.top, b.top);
        assertEquals(a.width, b.width);
        assertEquals(a.height, b.height);
        Map<AtlasKey, TextureAtlasData.Region> result = parsed(root.child("output"));
        assertEquals(9, result.get(first.key()).originalWidth);
        assertEquals(12, result.get(second.key()).originalWidth);
        assertEquals(1, result.get(first.key()).offsetX, 0);
        assertEquals(3, result.get(second.key()).offsetX, 0);
        assertArrayEquals(new int[]{11}, result.get(first.key()).findValue("custom"));
        assertArrayEquals(new int[]{22}, result.get(second.key()).findValue("custom"));
    }

    @Test public void allFourFontSourcesResolveFinalStemAndIndexes() throws Exception {
        FileHandle root = root();
        List<AtlasEntry> entries = new ArrayList<>();
        List<BitmapFontProjection> fonts = new ArrayList<>();
        for (boolean atlasBacked : List.of(true, false)) for (int count : List.of(1, 2)) {
            String stem = (atlasBacked ? "atlas" : "pages") + count;
            List<AtlasKey> keys = new ArrayList<>();
            for (int page = 0; page < count; page++) {
                int index = count == 1 ? -1 : page;
                String path = stem + "-physical-" + page + ".png";
                AtlasEntry entry;
                if (atlasBacked)
                    entry = skin(root, stem, index, path, false, false, 2, 3, 0, 0, Map.of());
                else {
                    png(root.child(path), 2, 3, PIXELS);
                    entry = new AtlasEntry(key(stem, index), MaterialKind.FULL_IMAGE, path, null,
                            List.of(new ProjectionProvenance(ProvenanceKind.BITMAP_FONT_PAGE, List.of("hud/main"),
                                    null, null, null, null, null, null, stem + ".fnt", page)));
                }
                entries.add(entry);
                keys.add(entry.key());
            }
            writeFont(root.child(stem + ".fnt"), count);
            fonts.add(new BitmapFontProjection(stem + ".fnt", keys));
        }
        SceneHudPackMaterializationManifest manifest = materialize(root, entries, fonts);
        builder.build(root.child("inputs"), manifest, root.child("output"));
        Map<AtlasKey, TextureAtlasData.Region> regions = parsed(root.child("output"));
        for (BitmapFontProjection font : fonts) for (AtlasKey key : font.keys()) assertNotNull(regions.get(key));
        // Exercise actual Skin.getRegions/getRegion selection without creating textures or a GL atlas.
        com.badlogic.gdx.scenes.scene2d.ui.Skin skin = new com.badlogic.gdx.scenes.scene2d.ui.Skin();
        Map<AtlasKey, com.badlogic.gdx.graphics.g2d.TextureRegion> registrations = new LinkedHashMap<>();
        try {
            for (AtlasKey key : regions.keySet()) {
                var region = new com.badlogic.gdx.graphics.g2d.TextureRegion();
                registrations.put(key, region);
                skin.add(key.displayName(), region, com.badlogic.gdx.graphics.g2d.TextureRegion.class);
            }
            for (BitmapFontProjection font : fonts) {
                var selected = skin.getRegions(new FileHandle(font.descriptorPath()).nameWithoutExtension());
                if (font.keys().get(0).index() < 0) {
                    assertNull(selected);
                    assertSame(registrations.get(font.keys().get(0)), skin.getRegion(font.keys().get(0).name()));
                } else {
                    assertEquals(font.keys().size(), selected.size);
                    for (int page = 0; page < selected.size; page++) assertSame(registrations.get(font.keys().get(page)), selected.get(page));
                }
            }
        } finally { skin.dispose(); }
        assertEquals(6, regions.size());
        assertFalse(root.child("output/hud.atlas").readString().contains("entry-"));
    }

    @Test public void compatibleMultipleSkinsProjectDeduplicateMaterializeAndBuild() throws Exception {
        FileHandle root = root();
        png(root.child("shared.png"), 2, 3, PIXELS);
        root.child("shared.atlas").writeString("shared.png\nsize: 2,3\nfilter: Linear,Linear\npanel\nbounds: 0,0,2,3\nindex: -1\n", false);
        root.child("a.json").writeString("{}", false);
        root.child("b.json").writeString("{}", false);
        List<SceneHudDependencyClosure.SkinDependency> skins = new ArrayList<>();
        for (String json : List.of("a.json", "b.json")) skins.add(new SceneHudDependencyClosure.SkinDependency(json, List.of(
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_JSON, json),
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_ATLAS, "shared.atlas"),
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_ATLAS_PAGE, "shared.png"))));
        SceneHudPackInputPlan plan = new SceneHudPackInputProjector().project(root,
                new SceneHudDependencyClosure(List.of(), List.of(), skins));
        assertEquals(2, plan.entry(key("panel", -1)).provenance().size());
        SceneHudPackMaterializationManifest manifest = new SceneHudPackInputMaterializer().materialize(root, root.child("inputs"), plan);
        builder.build(root.child("inputs"), manifest, root.child("output"));
        assertEquals(2, parsed(root.child("output")).size());
        // An incompatible second Skin is rejected by projection, before any new materialization/build.
        root.child("other.png").writeBytes(root.child("shared.png").readBytes(), false);
        root.child("other.atlas").writeString(root.child("shared.atlas").readString().replace("shared.png", "other.png"), false);
        skins.set(1, new SceneHudDependencyClosure.SkinDependency("b.json", List.of(
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_JSON, "b.json"),
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_ATLAS, "other.atlas"),
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_ATLAS_PAGE, "other.png"))));
        try {
            new SceneHudPackInputProjector().project(root, new SceneHudDependencyClosure(List.of(), List.of(), skins));
            fail("collision");
        } catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("atlas key collision")); }
        assertNotNull(parsed(root.child("output")).get(key("panel", -1)));
    }

    @Test public void sourcePmaIsRejectedBeforePackingWithPreciseDiagnosticAndOldTargetIntact() throws Exception {
        FileHandle root = root();
        AtlasEntry entry = skin(root, "pma-region", -1, "pma-page.png", false, true, 2, 3, 0, 0, Map.of());
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(entry), List.of());
        seedOld(root);
        try {
            new SceneHudAtlasBuilder((i, o, n, p) -> fail("PMA must fail before packing"), SceneHudAtlasBuilder::writeDescriptor)
                    .build(root.child("inputs"), manifest, root.child("output"));
            fail("PMA rejection");
        } catch (IllegalStateException expected) {
            for (String part : List.of("pma-page.png", "pma-region", "Skin 'skin'", "pma: false", "straight-alpha"))
                assertTrue(expected.getMessage(), expected.getMessage().contains(part));
        }
        oldIntact(root);
    }

    @Test public void failedPackerAndTemporaryParseLeavePreviousPublicationUntouched() throws Exception {
        for (boolean malformed : List.of(false, true)) {
            FileHandle root = root();
            SceneHudPackMaterializationManifest manifest = basic(root);
            seedOld(root);
            SceneHudAtlasBuilder failing = new SceneHudAtlasBuilder((input, output, name, profile) -> {
                if (malformed) {
                    AtlasPackingService.packHud(input, output, name, profile);
                    output.child("hud.atlas").writeString("hud.png\nsize: invalid, 2048\n", false);
                } else {
                    output.child("partial.png").writeString("partial", false);
                    throw new IllegalStateException("injected pack failure");
                }
            }, SceneHudAtlasBuilder::writeDescriptor);
            fails(failing, root, manifest, malformed ? "temporary atlas validation" : "packing");
            oldIntact(root);
        }
    }

    @Test public void standaloneFontUsesStableAssetKeyInsteadOfDescriptorStem() throws Exception {
        FileHandle root = root();
        String descriptor = "orig/fonts/42/custom-name.fnt";
        String page = "orig/fonts/42/page.png";
        png(root.child(page), 2, 3, PIXELS);
        writeFont(root.child(descriptor), 1);
        AtlasKey key = key("hud-font-42-page-0", -1);
        AtlasEntry entry = new AtlasEntry(key, MaterialKind.FULL_IMAGE, page, null,
                List.of(new ProjectionProvenance(ProvenanceKind.BITMAP_FONT_PAGE, List.of("hud/main"),
                        42, "fonts/custom-name.fnt", null, null, null, null, descriptor, 0)));
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(entry),
                List.of(new BitmapFontProjection(descriptor, List.of(key))));

        builder.build(root.child("inputs"), manifest, root.child("output"));

        assertNotNull(parsed(root.child("output")).get(key));
    }

    @Test public void temporaryKeysAndBoundsAndMissingPagesAreStrictlyValidated() throws Exception {
        for (String corruption : List.of("missing", "unexpected", "duplicate", "bounds", "page")) {
            FileHandle root = root();
            SceneHudPackMaterializationManifest manifest = basic(root);
            seedOld(root);
            SceneHudAtlasBuilder failing = new SceneHudAtlasBuilder((input, output, name, profile) -> {
                AtlasPackingService.packHud(input, output, name, profile);
                FileHandle atlas = output.child("hud.atlas");
                String original = atlas.readString();
                switch (corruption) {
                    case "missing" -> atlas.writeString(original.substring(0, original.indexOf("entry-")), false);
                    case "unexpected" -> atlas.writeString(original.replace("entry-000000", "unexpected"), false);
                    case "duplicate" -> atlas.writeString(original + original.substring(original.indexOf("entry-")), false);
                    case "bounds" -> {
                        String changed = original.replaceFirst("(?m)^\\s*(?:bounds|xy):[^\\r\\n]+", "  xy: 2048,2048");
                        assertNotEquals(original, changed);
                        atlas.writeString(changed, false);
                    }
                    case "page" -> { assertTrue(output.child("hud.png").delete()); }
                }
            }, SceneHudAtlasBuilder::writeDescriptor);
            fails(failing, root, manifest, "temporary atlas validation");
            oldIntact(root);
        }
    }

    @Test public void reconstructionRejectsUnsupportedMetadataAndUnsafeNamesTransactionally() throws Exception {
        for (String corruption : List.of("arity", "name", "fractional")) {
            FileHandle root = root();
            Map<String, List<Integer>> values = corruption.equals("arity") ? Map.of("custom", List.of(1,2,3,4,5)) : Map.of();
            AtlasEntry entry = skin(root, corruption.equals("name") ? "unsafe:name" : "safe", -1,
                    "page.png", false, false, 9, 10, corruption.equals("fractional") ? 0.5f : 1, 2, values);
            SceneHudPackMaterializationManifest manifest = materialize(root, List.of(entry), List.of());
            seedOld(root);
            fails(builder, root, manifest, "descriptor reconstruction");
            oldIntact(root);
        }
    }

    @Test public void finalValidationRejectsChangedIdentityMetadataAndPmaWithoutPublishing() throws Exception {
        for (String corruption : List.of("key", "offset", "pma", "custom")) {
            FileHandle root = root();
            AtlasEntry entry = skin(root, "safe", -1, "page.png", false, false, 9, 10, 1, 2, Map.of("custom", List.of(7)));
            SceneHudPackMaterializationManifest manifest = materialize(root, List.of(entry), List.of());
            seedOld(root);
            SceneHudAtlasBuilder failing = new SceneHudAtlasBuilder(AtlasPackingService::packHud, (descriptor, generated, m) -> {
                SceneHudAtlasBuilder.writeDescriptor(descriptor, generated, m);
                String original = descriptor.readString();
                String changed = switch (corruption) {
                    case "key" -> original.replace("\nsafe\n", "\nentry-000000\n");
                    case "offset" -> original.replace("offsets: 1, 2, 9, 10", "offsets: 0, 0, 9, 10");
                    case "pma" -> original.replace("pma: false", "pma: true");
                    default -> original.replace("custom: 7", "custom: 8");
                };
                assertNotEquals(original, changed);
                descriptor.writeString(changed, false);
            });
            fails(failing, root, manifest, "final atlas validation");
            oldIntact(root);
        }
    }

    @Test public void missingFinalFontRequirementsPreventPublication() throws Exception {
        FileHandle root = root();
        SceneHudPackMaterializationManifest ordinary = basic(root);
        BitmapFontProjection requirement = new BitmapFontProjection("missing.fnt",
                List.of(key("missing", -1)));
        SceneHudPackMaterializationManifest invalid = new SceneHudPackMaterializationManifest(ordinary.rootIds(), ordinary.entries(),
                List.of(new BitmapFontMaterialization(requirement, List.of())));
        seedOld(root);
        fails(builder, root, invalid, "BitmapFont requirements missing");
        oldIntact(root);
    }

    @Test public void repeatBuildsHaveIdenticalDescriptorsPagesAndStablePaths() throws Exception {
        FileHandle root = root();
        png(root.child("source.png"), 2, 3, PIXELS);
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(image("logo", -1, "source.png"),
                skin(root, "panel", 2, "page.png", true, false, 9, 10, 1, 2,
                        Map.of("zeta", List.of(3,4), "alpha", List.of(-2))), white()), List.of());
        SceneHudAtlasBuilder.Result first = builder.build(root.child("inputs"), manifest, root.child("output"));
        byte[] descriptor = root.child("output/hud.atlas").readBytes();
        byte[] page = root.child("output/hud.png").readBytes();
        SceneHudAtlasBuilder.Result second = builder.build(root.child("inputs"), manifest, root.child("output"));
        assertEquals(first, second);
        assertArrayEquals(descriptor, root.child("output/hud.atlas").readBytes());
        assertArrayEquals(page, root.child("output/hud.png").readBytes());
        String text = root.child("output/hud.atlas").readString();
        assertFalse(text.contains(root.path()));
        assertFalse(text.contains(".tmp"));
        assertFalse(text.contains("entry-"));
        noCandidates(root);
    }

    @Test public void realMultipagePackingUsesNormalPackerNamesAndPublishedPaths() throws Exception {
        FileHandle root = root();
        int[] red = new int[1100*1100], blue = new int[1100*1100];
        Arrays.fill(red, 0xff0000ff);
        Arrays.fill(blue, 0x0000ffff);
        png(root.child("red.png"), 1100, 1100, red);
        png(root.child("blue.png"), 1100, 1100, blue);
        SceneHudPackMaterializationManifest manifest = materialize(root, List.of(image("red", -1, "red.png"), image("blue", -1, "blue.png")), List.of());
        SceneHudAtlasBuilder.Result result = builder.build(root.child("inputs"), manifest, root.child("output"));
        assertEquals(2, result.pageCount());
        assertEquals(List.of("hud.png", "hud2.png"), result.pagePaths().stream().map(path -> new FileHandle(path).name()).toList());
        for (TextureAtlasData.Page page : data(root.child("output")).getPages()) {
            assertEquals(2048, page.width, 0);
            assertEquals(2048, page.height, 0);
            assertFalse(page.pma);
        }
        assertNotEquals(parsed(root.child("output")).get(key("red", -1)).page.name,
                parsed(root.child("output")).get(key("blue", -1)).page.name);
    }

    @Test public void unexpectedInputConfigurationAndOverlappingDirectoriesAreRejected() throws Exception {
        FileHandle root = root();
        SceneHudPackMaterializationManifest manifest = basic(root);
        root.child("inputs/pack.json").writeString("{rotation:true}", false);
        seedOld(root);
        try { builder.build(root.child("inputs"), manifest, root.child("output")); fail("configuration"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("unexpected inputs")); }
        oldIntact(root);
        try { builder.build(root.child("inputs"), manifest, root.child("inputs/output")); fail("overlap"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("overlap")); }
    }

    private FileHandle root() throws Exception { return Gdx.files.absolute(temporary.newFolder().getAbsolutePath()); }
    private static AtlasKey key(String name, int index) { return new AtlasKey(name, index); }
    private static ProjectionProvenance provenance(ProvenanceKind kind, String name, int index) {
        return new ProjectionProvenance(kind, List.of("hud/main"), 1, "images/" + name, "skin", "skin.atlas", name, index, null, null);
    }
    private static AtlasEntry image(String name, int index, String path) {
        return new AtlasEntry(key(name, index), MaterialKind.FULL_IMAGE, path, null, List.of(provenance(ProvenanceKind.REGION_IMAGE, name, index)));
    }
    private static AtlasEntry white() {
        return new AtlasEntry(key(InternalAssets.WHITE_PIXEL_REGION, -1), MaterialKind.INTERNAL_WHITE_PIXEL, null, null,
                List.of(provenance(ProvenanceKind.INTERNAL_RESOURCE, InternalAssets.WHITE_PIXEL_REGION, -1)));
    }
    private static AtlasEntry skin(FileHandle root, String name, int index, String path, boolean rotated, boolean pma,
                                    int originalWidth, int originalHeight, float ox, float oy, Map<String, List<Integer>> values) {
        int[] page = new int[6*6];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 2; x++) {
            int px = 1 + (rotated ? y : x), py = 2 + (rotated ? 2-x-1 : y);
            page[py*6+px] = PIXELS[y*2+x];
        }
        png(root.child(path), 6, 6, page);
        return new AtlasEntry(key(name, index), MaterialKind.SKIN_ATLAS_REGION, path,
                new AtlasRegionMetadata(path, path, pma, 1, 2, 2, 3, rotated ? 90 : 0, rotated, false,
                        originalWidth, originalHeight, ox, oy, values), List.of(provenance(ProvenanceKind.SKIN_ATLAS_REGION, name, index)));
    }
    private static SceneHudPackMaterializationManifest materialize(FileHandle root, List<AtlasEntry> entries, List<BitmapFontProjection> fonts) {
        return new SceneHudPackInputMaterializer().materialize(root, root.child("inputs"), new SceneHudPackInputPlan(List.of("hud/main"), entries, fonts));
    }
    private static SceneHudPackMaterializationManifest basic(FileHandle root) {
        png(root.child("source.png"), 2, 3, PIXELS);
        return materialize(root, List.of(image("logo", -1, "source.png")), List.of());
    }
    private static TextureAtlasData data(FileHandle output) { return new TextureAtlasData(output.child("hud.atlas"), output, false); }
    private static Map<AtlasKey, TextureAtlasData.Region> parsed(FileHandle output) {
        Map<AtlasKey, TextureAtlasData.Region> result = new LinkedHashMap<>();
        for (TextureAtlasData.Region region : data(output).getRegions()) assertNull(result.put(key(region.name, region.index), region));
        return result;
    }
    private static void png(FileHandle path, int w, int h, int[] pixels) {
        path.parent().mkdirs();
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        try {
            pixmap.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) pixmap.drawPixel(x, y, pixels[y*w+x]);
            PixmapIO.writePNG(path, pixmap, java.util.zip.Deflater.DEFAULT_COMPRESSION, false);
        } finally { pixmap.dispose(); }
    }
    private static void assertPixels(FileHandle output, TextureAtlasData.Region region, int[] pixels) {
        Pixmap page = new Pixmap(output.child(region.page.name));
        try {
            assertEquals(region.width*region.height, pixels.length);
            for (int y = 0; y < region.height; y++) for (int x = 0; x < region.width; x++)
                assertEquals(pixels[y*region.width+x], page.getPixel(region.left+x, region.top+y));
        } finally { page.dispose(); }
    }
    private static void seedOld(FileHandle root) {
        png(root.child("previous-source.png"), 1, 1, new int[]{0xabcdef80});
        SceneHudPackMaterializationManifest previous = new SceneHudPackInputMaterializer().materialize(root, root.child("previous-inputs"),
                new SceneHudPackInputPlan(List.of("hud/previous"), List.of(image("previous", -1, "previous-source.png")), List.of()));
        new SceneHudAtlasBuilder().build(root.child("previous-inputs"), previous, root.child("output"));
        root.child("output/hud.atlas").copyTo(root.child("previous.atlas"));
        root.child("output/hud.png").copyTo(root.child("previous.png"));
    }
    private static void oldIntact(FileHandle root) throws Exception {
        assertArrayEquals(root.child("previous.atlas").readBytes(), root.child("output/hud.atlas").readBytes());
        assertArrayEquals(root.child("previous.png").readBytes(), root.child("output/hud.png").readBytes());
        assertNotNull(parsed(root.child("output")).get(key("previous", -1)));
        assertEquals(2, root.child("output").list().length);
        noCandidates(root);
    }
    private static void noCandidates(FileHandle root) throws Exception {
        if (root.child(".tmp").exists()) assertEquals(0, root.child(".tmp").list().length);
        try (var children = Files.list(root.file().toPath())) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith(".backup-")));
        }
    }
    private static void fails(SceneHudAtlasBuilder builder, FileHandle root, SceneHudPackMaterializationManifest manifest, String message) {
        try { builder.build(root.child("inputs"), manifest, root.child("output")); fail("Expected failure"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }
    private static void writeFont(FileHandle descriptor, int pages) {
        StringBuilder text = new StringBuilder("info face=\"test\" size=16 padding=0,0,0,0 spacing=0,0\ncommon lineHeight=16 base=12 scaleW=2 scaleH=3 pages=")
                .append(pages).append(" packed=0\n");
        for (int page = 0; page < pages; page++) text.append("page id=").append(page).append(" file=\"unrelated-").append(page).append(".png\"\n");
        text.append("chars count=1\nchar id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0\nkernings count=0\n");
        descriptor.writeString(text.toString(), false);
    }
}
