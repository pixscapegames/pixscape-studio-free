package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.io.StudioFs;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TextureAtlasAssetImportServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private FileHandle external, project;
    private AssetMetaDatabase database;
    private TextureAtlasAssetImportService importer;

    @Before public void setup() throws Exception {
        GdxNativesLoader.load();
        external = new FileHandle(temporary.newFolder("external"));
        project = new FileHandle(temporary.newFolder("project"));
        database = new AssetMetaDatabase();
        importer = new TextureAtlasAssetImportService(database);
        png("page.png", 4, 4, 0x12345678);
    }

    @Test public void mixedAtlasUsesOnlyIndicesAndKeepsAliasesAndSingletonAnimations() {
        List<AssetMeta> assets = load(page("page.png")
                + region("walk", 10, 1, 0) + region("run", 2, 0, 0)
                + region("walk", 2, 0, 0) + region("single", 7, 0, 0)
                + region("still_02", -1, 0, 0)
                + "still_10\n bounds: 0, 0, 1, 1\n");
        assertEquals(5, assets.size());
        assertEquals(AssetType.IMAGE, database.findByLogicalPath("images/still_02").type());
        assertEquals(AssetType.IMAGE, database.findByLogicalPath("images/still_10").type());
        AnimationAssetMeta walk = animation("walk");
        assertEquals(2, walk.frameCount);
        assertEquals(12f, walk.fps, 0);
        assertEquals("default", walk.currentClip);
        assertEquals(1, walk.clips.get("default").end);
        assertEquals(1, animation("single").frameCount);
        assertNotEquals(walk.id(), animation("run").id());
        assertEquals(pixel(frame(walk, 0), 0, 0), pixel(frame(animation("run"), 0), 0, 0));
        assertEquals(0x12345678, pixel(frame(walk, 0), 0, 0));
        assertEquals(0x12345778, pixel(frame(walk, 1), 0, 0));
        assertTrue(frame(walk, 1).name().endsWith("_0001.png"));
        assertFalse(project.child("orig/.tmp").exists());
    }

    @Test public void framesCanSpanPagesAndPersistAsOrdinaryAnimationAssets() {
        png("nested/second.png", 4, 4, 0xaabbccff);
        load(page("page.png") + region("walk", 10, 0, 0)
                + "\n" + page("nested/second.png") + region("walk", 2, 0, 0));
        AnimationAssetMeta animation = animation("walk");
        assertEquals(0xaabbccff, pixel(frame(animation, 0), 0, 0));
        assertEquals(0x12345678, pixel(frame(animation, 1), 0, 0));
        database.save(project.child(StudioFs.FILE_ASSETS_JSON));
        AssetMetaDatabase loaded = AssetMetaDatabase.load(project.child(StudioFs.FILE_ASSETS_JSON));
        AnimationAssetMeta restored = (AnimationAssetMeta) loaded.findById(animation.id());
        assertEquals(animation.sourceRelPath(), restored.sourceRelPath());
        assertEquals(2, restored.frameCount);
        assertNotNull(StudioAnimationAssets.buildRegistry(loaded));
        assertEquals(2, StudioAssetVisualResolver.listProjectPngFramePaths(
                project.child(restored.sourceRelPath()), restored.sourceRelPath()).length);
    }

    @Test public void restoresNonSquareRotationTrimOffsetsAndEveryAlphaPixelExactly() {
        // Packed counterclockwise: [B D F] / [A C E], canonical: [A B] / [C D] / [E F].
        int[] canonical = {0x11223300, 0x22334440, 0x33445580, 0x445566ff, 0x556677fe, 0x66778801};
        Pixmap page = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        page.setBlending(Pixmap.Blending.None);
        int[][] stored = {{canonical[1], canonical[3], canonical[5]}, {canonical[0], canonical[2], canonical[4]}};
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) page.drawPixel(x + 1, y + 1, stored[y][x]);
        PixmapIO.writePNG(external.child("page.png"), page);
        page.dispose();
        load(page("page.png") + "rotated\n bounds: 1, 1, 2, 3\n rotate: 90\n offsets: 1, 2, 5, 6\n"
                + "oldStyle\n xy: 1, 1\n size: 2, 3\n rotate: true\n offset: 1, 2\n orig: 5, 6\n index: -1\n");
        for (String name : List.of("rotated", "oldStyle")) {
            Pixmap restored = new Pixmap(project.child(database.findByLogicalPath("images/" + name).sourceRelPath()));
            try {
                assertEquals(5, restored.getWidth());
                assertEquals(6, restored.getHeight());
                for (int y = 0; y < 6; y++) for (int x = 0; x < 5; x++) {
                    int expected = x >= 1 && x < 3 && y >= 1 && y < 4 ? canonical[(y - 1) * 2 + x - 1] : 0;
                    assertEquals("Pixel " + x + "," + y, expected, restored.getPixel(x, y));
                }
            } finally { restored.dispose(); }
        }
    }

    @Test public void missingPageRejectsWholeAtlas() {
        rejected(page("page.png") + region("valid", 0, 0, 0) + "\n" + page("missing.png") + region("missing", 0, 0, 0), "missing");
    }

    @Test public void nonPngPageRejectsWholeAtlas() {
        external.child("page.png").copyTo(external.child("page.jpg"));
        rejected(page("page.jpg") + region("walk", 0, 0, 0), "PNG");
    }

    @Test public void undecodablePngRejectsWholeAtlas() {
        external.child("page.png").writeBytes(new byte[]{(byte)137,80,78,71,13,10,26,10,0}, false);
        rejected(page("page.png") + region("walk", 0, 0, 0), "decode");
    }

    @Test public void duplicateIndexedRegionRejectsWholeAtlas() {
        rejected(page("page.png") + region("walk", 2, 0, 0) + region("walk", 2, 1, 0), "duplicate (name, index): (walk, 2)");
    }

    @Test public void varyingOriginalAnimationDimensionsAreRejected() {
        rejected(page("page.png") + region("walk", 0, 0, 0) + region("walk", 1, 0, 0)
                + " offsets: 0, 0, 2, 2\n", "identical original dimensions");
    }

    @Test public void invalidGeometryRotationAndMetadataAreRejected() {
        for (String field : List.of("bounds: 4, 0, 1, 1", "bounds: -1, 0, 1, 1", "rotate: 180",
                "offsets: 2, 0, 2, 2", "orig: -1, 2", "split: 1, 1, 1, 1"))
            rejected(page("page.png") + region("walk", 0, 0, 0) + " " + field + "\n", "Atlas region");
        rejected("page.png\n size: 128, 128\n" + region("walk", 0, 0, 0), "size");
        rejected("page.png\n pma: true\n" + region("walk", 0, 0, 0), "Premultiplied");
    }

    @Test public void unsafeNamesAndPagesAreRejected() {
        rejected(page("page.png") + region("../outside", 0, 0, 0), "Unsafe");
        rejected(page("page.png") + region(".hidden", 0, 0, 0), "Unsafe");
        rejected(page("../page.png") + region("walk", 0, 0, 0), "escapes");
    }

    @Test public void unindexedDuplicatesAndRuntimeNameCollisionsAreRejected() {
        rejected(page("page.png") + region("still", -1, 0, 0) + region("still", -1, 1, 0), "duplicate");
        rejected(page("page.png") + region("walk.png", 0, 0, 0) + region("walk.jpg", 1, 0, 0), "Runtime naming convention");
        rejected(page("page.png") + region("walk", -2, 0, 0), "index must be");
    }

    @Test public void restoresUnrotatedTrimWhenPageSizeIsOmitted() {
        load("page.png\nstill\n bounds: 0, 0, 2, 1\n offsets: 2, 1, 5, 4\n");
        FileHandle file = project.child(database.findByLogicalPath("images/still").sourceRelPath());
        assertEquals(0, pixel(file, 1, 2));
        assertEquals(0x12345678, pixel(file, 2, 2));
        assertEquals(0x12345778, pixel(file, 3, 2));
        assertEquals(0, pixel(file, 2, 3));
    }

    @Test public void existingNamesAreNotOverwritten() {
        String atlas = page("page.png") + region("walk", 0, 0, 0);
        load(atlas);
        byte[] before = frame(animation("walk"), 0).readBytes();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> load(atlas));
        assertTrue(failure.getMessage().contains("already exists"));
        assertEquals(1, database.size());
        assertArrayEquals(before, frame(animation("walk"), 0).readBytes());
    }

    @Test public void publicationFailureRollsBackEarlierAssetsAndPreservesConsumedIds() {
        FileHandle blocker = project.child(StudioFs.DIR_ORIG_IMAGES + "/second__a2.png");
        blocker.writeString("keep", false);
        assertThrows(IllegalStateException.class, () -> load(page("page.png")
                + region("first", -1, 0, 0) + region("second", -1, 0, 0)));
        assertEquals(0, database.size());
        assertEquals(3, database.nextId());
        assertFalse(project.child(StudioFs.DIR_ORIG_IMAGES + "/first__a1.png").exists());
        assertEquals("keep", blocker.readString());
        assertFalse(project.child("orig/.tmp").exists());
    }

    /** Opt-in check against the user's immutable source, without committing binary fixtures. */
    @Test public void daemonDirectAtlas() {
        String source = System.getenv("PIXSCAPE_TEST_ATLAS");
        org.junit.Assume.assumeTrue(source != null && !source.isBlank());
        FileHandle descriptor = new FileHandle(source);
        byte[] originalAtlas = descriptor.readBytes();
        byte[] originalPng = descriptor.sibling("test.png").readBytes();
        assertTrue(descriptor.readString().contains("size: 192, 192"));
        List<AssetMeta> assets = importer.importNew(descriptor, project);
        assertEquals(11, assets.size());
        assertEquals(24, assets.stream().mapToInt(a -> ((AnimationAssetMeta) a).frameCount).sum());
        assertEquals(6, assets.stream().filter(a -> ((AnimationAssetMeta) a).frameCount == 1).count());
        assertTrue(assets.stream().allMatch(a -> a.type() == AssetType.ANIMATION));
        assertNotEquals(animation("chrWalk").id(), animation("chrRun").id());
        assertArrayEquals(frame(animation("chrWalk"), 0).readBytes(), frame(animation("chrRun"), 0).readBytes());
        assertArrayEquals(originalAtlas, descriptor.readBytes());
        assertArrayEquals(originalPng, descriptor.sibling("test.png").readBytes());
    }

    private List<AssetMeta> load(String text) {
        FileHandle descriptor = external.child("source.atlas");
        descriptor.writeString(text, false, "UTF-8");
        return importer.importNew(descriptor, project);
    }
    private void rejected(String text, String message) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> load(text));
        assertTrue(failure.getMessage(), failure.getMessage().contains(message));
        assertEquals(0, database.size());
        assertFalse(project.child("orig/.tmp").exists());
    }
    private AnimationAssetMeta animation(String name) { return (AnimationAssetMeta) database.findByLogicalPath("animations/" + name); }
    private FileHandle frame(AnimationAssetMeta animation, int index) {
        FileHandle[] files = project.child(animation.sourceRelPath()).list("png");
        Arrays.sort(files, java.util.Comparator.comparing(FileHandle::name));
        return files[index];
    }
    private static int pixel(FileHandle file, int x, int y) {
        Pixmap pixmap = new Pixmap(file);
        try { return pixmap.getPixel(x, y); } finally { pixmap.dispose(); }
    }
    private void png(String name, int width, int height, int color) {
        Pixmap pixels = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixels.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) pixels.drawPixel(x, y, color + x * 256 + y * 65536);
        PixmapIO.writePNG(external.child(name), pixels);
        pixels.dispose();
    }
    private static String page(String name) { return name + "\n size: 4, 4\n"; }
    private static String region(String name, int index, int x, int y) {
        return name + "\n bounds: " + x + ", " + y + ", 1, 1\n index: " + index + "\n";
    }
}
