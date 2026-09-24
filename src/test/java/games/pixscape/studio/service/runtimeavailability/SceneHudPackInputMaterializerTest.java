package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.studio.helper.InternalAssets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static games.pixscape.studio.service.runtimeavailability.SceneHudPackInputPlan.*;
import static games.pixscape.studio.service.runtimeavailability.SceneHudPackMaterializationManifest.*;
import static org.junit.Assert.*;

public class SceneHudPackInputMaterializerTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private final SceneHudPackInputMaterializer materializer = new SceneHudPackInputMaterializer();
    private static final int[] PATTERN = {0xff0000ff, 0x00ff0080, 0x0000ffff, 0x12345600, 0xffffffff, 0x11223344};

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
    }

    @Test public void fullImagesKeepExactBytesPixelsAndUnambiguousIndependentNames() throws Exception {
        FileHandle root = root();
        FileHandle source = root.child("authored/arbitrary.png");
        png(source, 2, 3, PATTERN);
        byte[] original = source.readBytes();
        SceneHudPackInputPlan plan = plan(List.of(
                image("legitimate_0", -1, "authored/arbitrary.png", ProvenanceKind.REGION_IMAGE),
                image("indexed", 0, "authored/arbitrary.png", ProvenanceKind.REGION_IMAGE),
                image("../../outside_0", -1, "authored/arbitrary.png", ProvenanceKind.REGION_IMAGE)), List.of());
        FileHandle out = root.child("build/inputs");
        SceneHudPackMaterializationManifest manifest = materializer.materialize(root, out, plan);
        assertEquals(List.of("entry-000000.png", "entry-000001.png", "entry-000002.png"), paths(manifest));
        for (int i = 0; i < manifest.entries().size(); i++) {
            MaterializedEntry entry = manifest.entries().get(i);
            assertEquals(plan.entries().get(i).key(), entry.intendedKey());
            assertEquals(-1, entry.packerKey().index());
            assertFalse(entry.packerKey().name().contains("_"));
            assertSame(entry, manifest.packerEntry(entry.packerKey()));
            assertPixels(out.child(entry.inputPath()), 2, 3, PATTERN);
            assertArrayEquals(original, out.child(entry.inputPath()).readBytes());
            assertEquals(new SemanticMetadata(2, 3, 0, 0, Map.of()), entry.metadata());
        }
        assertArrayEquals(original, source.readBytes());
        assertFalse(root.child("outside_0.png").exists());
        assertOnlyPngs(out, 3);
    }

    @Test public void nonPngImagesAreEncodedAsPngWithoutResizingOrFiltering() throws Exception {
        FileHandle root = root();
        FileHandle source = root.child("authored/page.bmp");
        source.parent().mkdirs();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff123456);
        image.setRGB(1, 0, 0xffabcdef);
        assertTrue(javax.imageio.ImageIO.write(image, "bmp", source.file()));
        byte[] original = source.readBytes();
        SceneHudPackMaterializationManifest manifest = materializer.materialize(root, root.child("build/inputs"),
                plan(List.of(image("logical", -1, "authored/page.bmp", ProvenanceKind.REGION_IMAGE)), List.of()));
        assertPixels(root.child("build/inputs").child(manifest.entries().get(0).inputPath()), 2, 1,
                new int[]{0x123456ff, 0xabcdefff});
        assertArrayEquals(original, source.readBytes());
    }

    @Test public void ordinaryCropRetainsTrimNinePatchIndexAndCustomMetadataNotWholePage() throws Exception {
        FileHandle root = root();
        int[] page = filled(6, 5, 0x998877ff);
        put(page, 6, 2, 1, 2, 3, PATTERN);
        png(root.child("skin/page.png"), 6, 5, page);
        byte[] original = root.child("skin/page.png").readBytes();
        Map<String, List<Integer>> values = Map.of("split", List.of(1, 1, 2, 0),
                "pad", List.of(0, 1, 0, 2), "custom", List.of(7, 8));
        AtlasRegionMetadata metadata = region(2, 1, 2, 3, false, 9, 10, 2, 4, values);
        SceneHudPackMaterializationManifest manifest = materializer.materialize(root, root.child("build/inputs"),
                plan(List.of(skin("panel", 7, metadata)), List.of()));
        MaterializedEntry result = manifest.entries().get(0);
        assertPixels(root.child("build/inputs").child(result.inputPath()), 2, 3, PATTERN);
        assertEquals(key("panel", 7), result.intendedKey());
        assertEquals(new SemanticMetadata(9, 10, 2, 4, values), result.metadata());
        assertEquals(values.get("split"), result.metadata().split());
        assertEquals(values.get("pad"), result.metadata().pad());
        assertSame(metadata, result.plannedEntry().atlasRegion());
        assertFalse(result.sourceRotationNormalized());
        assertArrayEquals(original, root.child("skin/page.png").readBytes());
        assertOnlyPngs(root.child("build/inputs"), 1);
        assertFalse(root.child("build/inputs/page.png").exists());
        try { result.metadata().values().get("split").add(9); fail("Mutable metadata"); }
        catch (UnsupportedOperationException expected) {}
    }

    @Test public void actualProjectorAtlasRotationNormalizesNonSquarePixelCoordinates() throws Exception {
        FileHandle root = root();
        int[] stored = {PATTERN[1], PATTERN[3], PATTERN[5], PATTERN[0], PATTERN[2], PATTERN[4]};
        int[] page = filled(7, 6, 0x765432ff);
        put(page, 7, 2, 3, 3, 2, stored);
        png(root.child("skin/page.png"), 7, 6, page);
        root.child("skin/game.json").writeString("{}", false);
        root.child("skin/game.atlas").writeString("""
                page.png
                size: 7, 6
                format: RGBA8888
                filter: Nearest, Nearest
                repeat: none
                rotated
                  rotate: 90
                  bounds: 2, 3, 2, 3
                  offsets: 4, 5, 10, 12
                  index: 2
                """, false);
        var files = List.of(
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_JSON, "skin/game.json"),
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_ATLAS, "skin/game.atlas"),
                new SceneHudDependencyClosure.FileDependency(SceneHudDependencyClosure.FileKind.SKIN_ATLAS_PAGE, "skin/page.png"));
        SceneHudDependencyClosure closure = new SceneHudDependencyClosure(List.of(), List.of(),
                List.of(new SceneHudDependencyClosure.SkinDependency("skin/game.json", files)));
        SceneHudPackInputPlan plan = new SceneHudPackInputProjector().project(root, closure);
        FileHandle out = root.child("build/inputs");
        MaterializedEntry result = materializer.materialize(root, out, plan).entry(key("rotated", 2));
        assertNotNull(result);
        assertPixels(out.child(result.inputPath()), 2, 3, PATTERN);
        assertTrue(result.sourceRotationNormalized());
        assertEquals(0, result.materializedDegrees());
        assertFalse(result.materializedRotate());
        assertEquals(90, result.plannedEntry().atlasRegion().degrees());
        assertEquals(new SemanticMetadata(10, 12, 4, 5, Map.of()), result.metadata());
    }

    @Test public void fontRequirementsReuseSkinEntriesAndMaterializeFallbackPagesAndWhiteOnce() throws Exception {
        FileHandle root = root();
        png(root.child("skin/page.png"), 2, 3, PATTERN);
        png(root.child("fonts/not-the-stem.png"), 2, 3, PATTERN);
        png(root.child("fonts/random-a.png"), 1, 1, new int[]{PATTERN[0]});
        png(root.child("fonts/random-b.png"), 1, 1, new int[]{PATTERN[1]});
        for (String name : List.of("atlas", "single", "multi")) root.child("fonts/" + name + ".fnt").writeString("descriptor", false);
        AtlasEntry atlas = skin("atlas", -1, region(0, 0, 2, 3, false, 2, 3, 0, 0, Map.of()));
        List<AtlasEntry> entries = List.of(atlas,
                image("single", -1, "fonts/not-the-stem.png", ProvenanceKind.BITMAP_FONT_PAGE),
                image("multi", 0, "fonts/random-a.png", ProvenanceKind.BITMAP_FONT_PAGE),
                image("multi", 1, "fonts/random-b.png", ProvenanceKind.BITMAP_FONT_PAGE),
                new AtlasEntry(key(InternalAssets.WHITE_PIXEL_REGION, -1), MaterialKind.INTERNAL_WHITE_PIXEL,
                        null, null, List.of(provenance(ProvenanceKind.INTERNAL_RESOURCE))));
        List<BitmapFontProjection> fonts = List.of(font("atlas", List.of(atlas.key())),
                font("single", List.of(key("single", -1))),
                font("multi", List.of(key("multi", 0), key("multi", 1))));
        FileHandle out = root.child("build/inputs");
        SceneHudPackMaterializationManifest result = materializer.materialize(root, out, plan(entries, fonts));
        assertEquals(5, result.entries().size());
        assertSame(result.entries().get(0), result.bitmapFonts().get(0).entries().get(0));
        assertSame(result.entries().get(1), result.bitmapFonts().get(1).entries().get(0));
        assertSame(result.entries().get(2), result.bitmapFonts().get(2).entries().get(0));
        assertSame(result.entries().get(3), result.bitmapFonts().get(2).entries().get(1));
        assertPixels(out.child(result.entries().get(1).inputPath()), 2, 3, PATTERN);
        assertPixels(out.child(result.entries().get(2).inputPath()), 1, 1, new int[]{PATTERN[0]});
        assertPixels(out.child(result.entries().get(3).inputPath()), 1, 1, new int[]{PATTERN[1]});
        assertPixels(out.child(result.entries().get(4).inputPath()), 1, 1, new int[]{0xffffffff});
        assertEquals(6, out.child(result.entries().get(4).inputPath()).readBytes()[25]); // PNG RGBA color type
        assertOnlyPngs(out, 5);
        try { result.entries().clear(); fail("Mutable manifest"); } catch (UnsupportedOperationException expected) {}
    }

    @Test public void builtInLabelFontPageMaterializesFromTheRuntimeClasspath() throws Exception {
        FileHandle root = root();
        AtlasEntry builtIn = new AtlasEntry(
                key(HudBuiltInLabelStyle.ATLAS_REGION, -1),
                MaterialKind.INTERNAL_BUILT_IN_LABEL_FONT, null, null,
                List.of(provenance(ProvenanceKind.INTERNAL_BUILT_IN_LABEL_FONT)));
        FileHandle out = root.child("build/inputs");

        MaterializedEntry result = materializer.materialize(
                root, out, plan(List.of(builtIn), List.of())).entries().get(0);

        Pixmap pixels = new Pixmap(out.child(result.inputPath()));
        try {
            assertTrue(pixels.getWidth() > 0);
            assertTrue(pixels.getHeight() > 0);
        } finally {
            pixels.dispose();
        }
        assertEquals(HudBuiltInLabelStyle.ATLAS_REGION, result.intendedKey().name());
        assertOnlyPngs(out, 1);
    }

    @Test public void deterministicRematerializationReplacesOnlyCompleteGeneratedContents() throws Exception {
        FileHandle root = root();
        png(root.child("authored/source.png"), 2, 3, PATTERN);
        png(root.child("skin/page.png"), 2, 3, PATTERN);
        SceneHudPackInputPlan plan = plan(List.of(image("logical", -1, "authored/source.png", ProvenanceKind.REGION_IMAGE),
                skin("region", -1, region(0, 0, 2, 3, false, 6, 7, 1, 2, Map.of())),
                new AtlasEntry(key(InternalAssets.WHITE_PIXEL_REGION, -1), MaterialKind.INTERNAL_WHITE_PIXEL,
                        null, null, List.of(provenance(ProvenanceKind.INTERNAL_RESOURCE)))), List.of());
        FileHandle out = root.child("build/inputs");
        SceneHudPackMaterializationManifest first = materializer.materialize(root, out, plan);
        List<byte[]> bytes = first.entries().stream().map(entry -> out.child(entry.inputPath()).readBytes()).toList();
        out.child("obsolete.txt").writeString("old", false);
        SceneHudPackMaterializationManifest second = materializer.materialize(root, out, plan);
        assertEquals(first, second);
        for (int i = 0; i < bytes.size(); i++)
            assertArrayEquals(bytes.get(i), out.child(second.entries().get(i).inputPath()).readBytes());
        assertFalse(out.child("obsolete.txt").exists());
        assertNoCandidatesOrBackups(out);
    }

    @Test public void invalidAndMissingSourcesAfterEarlierWritePreservePreviousTargetAndCleanCandidate() throws Exception {
        for (boolean missing : List.of(false, true)) {
            FileHandle root = root();
            png(root.child("authored/valid.png"), 2, 3, PATTERN);
            if (!missing) root.child("authored/bad.png").writeString("not image pixels", false);
            FileHandle out = root.child("build/inputs");
            out.mkdirs();
            out.child("previous.png").writeString("previous contents", false);
            SceneHudPackInputPlan plan = plan(List.of(image("valid", -1, "authored/valid.png", ProvenanceKind.REGION_IMAGE),
                    image("bad", -1, "authored/bad.png", ProvenanceKind.REGION_IMAGE)), List.of());
            expectFailure(root, out, plan);
            assertEquals("previous contents", out.child("previous.png").readString());
            assertEquals(1, out.list().length);
            assertNoCandidatesOrBackups(out);
            FileHandle absent = root.child("build/absent");
            expectFailure(root, absent, plan);
            assertFalse(absent.exists());
            assertNoCandidatesOrBackups(absent);
        }
    }

    @Test public void invalidGeometryRotationAndFontReferenceAlsoRollback() throws Exception {
        FileHandle root = root();
        png(root.child("skin/page.png"), 2, 3, PATTERN);
        root.child("fonts/missing.fnt").writeString("descriptor", false);
        FileHandle out = root.child("build/inputs");
        out.mkdirs();
        out.child("old.txt").writeString("old", false);
        AtlasRegionMetadata invalidBounds = region(1, 0, 2, 3, false, 2, 3, 0, 0, Map.of());
        AtlasRegionMetadata unsupported = new AtlasRegionMetadata("skin/page.png", "page.png", false,
                0, 0, 2, 3, 180, false, false, 2, 3, 0, 0, Map.of());
        AtlasRegionMetadata flipped = new AtlasRegionMetadata("skin/page.png", "page.png", false,
                0, 0, 2, 3, 0, false, true, 2, 3, 0, 0, Map.of());
        for (AtlasRegionMetadata metadata : List.of(invalidBounds, unsupported, flipped)) {
            expectFailure(root, out, plan(List.of(skin("region", -1, metadata)), List.of()));
            assertEquals("old", out.child("old.txt").readString());
            assertNoCandidatesOrBackups(out);
        }
        expectFailure(root, out, plan(List.of(skin("region", -1, region(0, 0, 2, 3, false, 2, 3, 0, 0, Map.of()))),
                List.of(font("missing", List.of(key("not-planned", -1))))));
        assertEquals("old", out.child("old.txt").readString());
        assertNoCandidatesOrBackups(out);
    }

    @Test public void authoredSourcesProjectRootsAndTraversalCannotBeOverwritten() throws Exception {
        FileHandle root = root();
        png(root.child("authored/source.png"), 2, 3, PATTERN);
        byte[] original = root.child("authored/source.png").readBytes();
        SceneHudPackInputPlan plan = plan(List.of(image("name", -1, "authored/source.png", ProvenanceKind.REGION_IMAGE)), List.of());
        for (FileHandle out : List.of(root, root.parent(), root.child("authored"), root.child("authored/source.png"), root.child("build/.tmp")))
            expectFailure(root, out, plan);
        expectFailure(root, root.child("build/inputs"),
                plan(List.of(image("name", -1, "../escaped.png", ProvenanceKind.REGION_IMAGE)), List.of()));
        assertArrayEquals(original, root.child("authored/source.png").readBytes());
    }

    private FileHandle root() throws Exception { return new FileHandle(temporary.newFolder()); }
    private static SceneHudPackInputPlan plan(List<AtlasEntry> entries, List<BitmapFontProjection> fonts) {
        return new SceneHudPackInputPlan(List.of("hud/main"), entries, fonts);
    }
    private static AtlasKey key(String name, int index) { return new AtlasKey(name, index); }
    private static ProjectionProvenance provenance(ProvenanceKind kind) {
        return new ProjectionProvenance(kind, List.of("hud/main"), null, null, null, null, null, null, null, null);
    }
    private static AtlasEntry image(String name, int index, String path, ProvenanceKind kind) {
        return new AtlasEntry(key(name, index), MaterialKind.FULL_IMAGE, path, null, List.of(provenance(kind)));
    }
    private static AtlasEntry skin(String name, int index, AtlasRegionMetadata metadata) {
        return new AtlasEntry(key(name, index), MaterialKind.SKIN_ATLAS_REGION, "skin/page.png", metadata,
                List.of(provenance(ProvenanceKind.SKIN_ATLAS_REGION)));
    }
    private static AtlasRegionMetadata region(int left, int top, int width, int height, boolean rotate,
                                              int originalWidth, int originalHeight, float x, float y,
                                              Map<String, List<Integer>> values) {
        return new AtlasRegionMetadata("skin/page.png", "page.png", false, left, top, width, height,
                rotate ? 90 : 0, rotate, false, originalWidth, originalHeight, x, y, values);
    }
    private static BitmapFontProjection font(String name, List<AtlasKey> keys) {
        return new BitmapFontProjection("fonts/" + name + ".fnt", keys);
    }
    private static void png(FileHandle file, int width, int height, int[] colors) {
        file.parent().mkdirs();
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixels.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels.drawPixel(x, y, colors[y * width + x]);
            PixmapIO.writePNG(file, pixels);
        } finally { pixels.dispose(); }
    }
    private static void assertPixels(FileHandle file, int width, int height, int[] expected) {
        Pixmap pixels = new Pixmap(file);
        try {
            assertEquals(width, pixels.getWidth()); assertEquals(height, pixels.getHeight());
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                assertEquals("pixel " + x + "," + y, expected[y * width + x], pixels.getPixel(x, y));
        } finally { pixels.dispose(); }
    }
    private static int[] filled(int width, int height, int color) {
        int[] result = new int[width * height]; Arrays.fill(result, color); return result;
    }
    private static void put(int[] page, int pageWidth, int left, int top, int width, int height, int[] pixels) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) page[(top + y) * pageWidth + left + x] = pixels[y * width + x];
    }
    private static List<String> paths(SceneHudPackMaterializationManifest manifest) {
        return manifest.entries().stream().map(MaterializedEntry::inputPath).toList();
    }
    private void expectFailure(FileHandle root, FileHandle out, SceneHudPackInputPlan plan) {
        try { materializer.materialize(root, out, plan); fail("Expected failed materialization"); }
        catch (IllegalArgumentException | IllegalStateException expected) {}
    }
    private static void assertNoCandidatesOrBackups(FileHandle out) {
        assertFalse(out.parent().child(".tmp").exists());
        for (FileHandle file : out.parent().list()) assertFalse(file.name(), file.name().startsWith(".backup-"));
    }
    private static void assertOnlyPngs(FileHandle out, int count) throws Exception {
        assertEquals(count, out.list().length);
        try (var files = Files.walk(out.file().toPath())) {
            assertTrue(files.filter(Files::isRegularFile).allMatch(path -> path.toString().endsWith(".png")));
        }
    }
}
