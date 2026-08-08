package games.pixscape.studio.service;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.TextureArrayData;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.StudioTextureArrayUploadBridge;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PreparedAtlasPublicationTest {

    @BeforeClass
    public static void ensureHeadlessApplication() {
        if (com.badlogic.gdx.Gdx.app == null) {
            new HeadlessApplication(
                    new ApplicationAdapter() {
                    },
                    new HeadlessApplicationConfiguration()
            );
        }
    }

    @Test
    public void preparationPreservesPageOrderPixelsDimensionsAndWhiteLayer() throws Exception {
        Path dir = Files.createTempDirectory("prepared-atlas-publication-pixels");
        FileHandle red = writeSolidPage(dir.resolve("scene.png"), 0xff0000ff);
        FileHandle green = writeSolidPage(dir.resolve("scene-2.png"), 0x00ff00ff);
        PreparedAtlasPublication candidate = prepare(
                "scene",
                7L,
                new FileHandle[]{red, green},
                new String[]{"scene.png", "scene-2.png"}
        );
        Pixmap whiteLayer = candidate.layer(0);
        Pixmap redLayer = candidate.layer(1);
        Pixmap greenLayer = candidate.layer(2);

        assertEquals(3, candidate.layerCount());
        assertEquals(2, candidate.pageCount());
        assertEquals(2, candidate.layerSize());
        assertEquals(48L, candidate.byteSize());
        assertEquals("scene.png", candidate.pageName(0));
        assertEquals("scene-2.png", candidate.pageName(1));
        assertEquals(0xffffffff, whiteLayer.getPixel(0, 0));
        assertEquals(0xff0000ff, redLayer.getPixel(0, 0));
        assertEquals(0x00ff00ff, greenLayer.getPixel(0, 0));
        assertSame(candidate.pagePixels(0), redLayer);
        assertSame(candidate.pagePixels(1), greenLayer);

        candidate.close();
        assertTrue(whiteLayer.isDisposed());
        assertTrue(redLayer.isDisposed());
        assertTrue(greenLayer.isDisposed());
    }

    @Test
    public void preparedPublicationMatchesLibgdxMetadataAndDoesNotRereadPages() throws Exception {
        Path dir = Files.createTempDirectory("prepared-atlas-publication-metadata");
        FileHandle firstPage = writeSolidPage(dir.resolve("scene.png"), 0xff0000ff);
        FileHandle secondPage = writeSolidPage(dir.resolve("scene-2.png"), 0x00ff00ff);
        FileHandle atlasFile = writeAtlas(dir.resolve("scene.atlas"));
        TextureAtlas.TextureAtlasData baselineData = new TextureAtlas.TextureAtlasData(
                atlasFile,
                atlasFile.parent(),
                false
        );
        for (TextureAtlas.TextureAtlasData.Page page : baselineData.getPages()) {
            page.texture = fakeTexture(2, 2);
        }
        TextureAtlas baseline = new TextureAtlas(baselineData);
        PreparedAtlasPublication candidate = PreparedAtlasPublication.prepare("scene", 8L, atlasFile, 2);

        firstPage.delete();
        secondPage.delete();
        AtomicInteger pageUploads = new AtomicInteger();
        PreparedAtlasPublication.Uploaded uploaded = candidate.upload(
                new FileHandle(dir.toFile()),
                (publishedFile, pixels, page) -> {
                    pageUploads.incrementAndGet();
                    return fakeTexture(pixels.getWidth(), pixels.getHeight());
                },
                layers -> fakeTextureArray()
        );
        TextureAtlas prepared = uploaded.atlas();

        assertEquals(2, pageUploads.get());
        assertEquals(baseline.getRegions().size, prepared.getRegions().size);
        for (int i = 0; i < baseline.getRegions().size; i++) {
            TextureAtlas.AtlasRegion expected = baseline.getRegions().get(i);
            TextureAtlas.AtlasRegion actual = prepared.getRegions().get(i);
            assertEquals(expected.name, actual.name);
            assertEquals(expected.index, actual.index);
            assertEquals(expected.getRegionX(), actual.getRegionX());
            assertEquals(expected.getRegionY(), actual.getRegionY());
            assertEquals(expected.getRegionWidth(), actual.getRegionWidth());
            assertEquals(expected.getRegionHeight(), actual.getRegionHeight());
            assertEquals(expected.originalWidth, actual.originalWidth);
            assertEquals(expected.originalHeight, actual.originalHeight);
            assertEquals(expected.rotate, actual.rotate);
            assertEquals(expected.getU(), actual.getU(), 0f);
            assertEquals(expected.getV(), actual.getV(), 0f);
            assertEquals(expected.getU2(), actual.getU2(), 0f);
            assertEquals(expected.getV2(), actual.getV2(), 0f);
        }

        baseline.dispose();
        uploaded.close();
        candidate.close();
    }

    @Test
    public void newerAcceptedGenerationDisposesPreviousCandidateExactlyOnce() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        PreparedAtlasPublication first = prepareEmpty("scene", 1L);
        PreparedAtlasPublication second = prepareEmpty("scene", 2L);

        assertTrue(manager.acceptPreparedPublication(first, 1L));
        assertTrue(manager.acceptPreparedPublication(second, 2L));

        assertTrue(first.isDisposed());
        assertFalse(second.isDisposed());
        assertEquals(1, manager.preparedPublicationCount());
        manager.disposeAll();
        assertTrue(second.isDisposed());
        assertEquals(0, manager.preparedPublicationCount());
    }

    @Test
    public void staleGenerationIsRejectedAndCpuBuffersAreReleased() {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        PreparedAtlasPublication stale = prepareEmpty("scene", 3L);

        assertFalse(manager.acceptPreparedPublication(stale, 4L));

        assertTrue(stale.isDisposed());
        assertEquals(0, manager.preparedPublicationCount());
    }

    @Test
    public void successfulUploadTransfersPageTexturesToAtlasAndDisposesEachOwnerOnce() throws Exception {
        Path dir = Files.createTempDirectory("prepared-atlas-publication-success");
        PreparedAtlasPublication candidate = prepare(
                "scene",
                4L,
                new FileHandle[]{writeSolidPage(dir.resolve("scene.png"), 0xff0000ff)},
                new String[]{"scene.png"}
        );
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        CountingTexture pageTexture = fakeTexture(2, 2);
        CountingTextureArray textureArray = fakeTextureArray();

        assertTrue(manager.acceptPreparedPublication(candidate, 4L));
        PreparedAtlasPublication.Uploaded uploaded = manager.uploadPreparedPublication(
                "scene",
                4L,
                4L,
                new FileHandle(dir.toFile()),
                (publishedFile, pixels, page) -> pageTexture,
                layers -> textureArray
        );

        assertTrue(candidate.isDisposed());
        assertEquals(0, manager.preparedPublicationCount());
        TextureAtlas atlas = uploaded.takeAtlas();
        uploaded.close();
        uploaded.close();
        assertEquals(1, textureArray.disposeCalls);
        assertEquals(0, pageTexture.disposeCalls);
        atlas.dispose();
        atlas.dispose();
        assertEquals(1, pageTexture.disposeCalls);
    }

    @Test
    public void pageUploadFailureDisposesPartialGpuStateAndLeavesOldSnapshotActive() throws Exception {
        Path dir = Files.createTempDirectory("prepared-atlas-page-failure");
        PreparedAtlasPublication candidate = prepare(
                "scene",
                5L,
                new FileHandle[]{
                        writeSolidPage(dir.resolve("scene.png"), 0xff0000ff),
                        writeSolidPage(dir.resolve("scene-2.png"), 0x00ff00ff)
                },
                new String[]{"scene.png", "scene-2.png"}
        );
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle old = bundle();
        manager.replaceActiveSnapshot("scene", old);
        CountingTexture firstPage = fakeTexture(2, 2);
        CountingTextureArray textureArray = fakeTextureArray();
        AtomicInteger page = new AtomicInteger();
        assertTrue(manager.acceptPreparedPublication(candidate, 5L));

        assertThrows(IllegalStateException.class, () -> manager.uploadPreparedPublication(
                "scene",
                5L,
                5L,
                new FileHandle(dir.toFile()),
                (publishedFile, pixels, metadata) -> {
                    if (page.getAndIncrement() == 0) return firstPage;
                    throw new IllegalStateException("page upload failed");
                },
                layers -> textureArray
        ));

        assertTrue(candidate.isDisposed());
        assertEquals(1, firstPage.disposeCalls);
        assertEquals(1, textureArray.disposeCalls);
        assertSame(old, manager.activeSnapshot("scene"));
    }

    @Test
    public void textureArrayFailureSkipsPageUploadsAndLeavesOldSnapshotActive() throws Exception {
        Path dir = Files.createTempDirectory("prepared-atlas-array-failure");
        PreparedAtlasPublication candidate = prepare(
                "scene",
                6L,
                new FileHandle[]{writeSolidPage(dir.resolve("scene.png"), 0xff0000ff)},
                new String[]{"scene.png"}
        );
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle old = bundle();
        manager.replaceActiveSnapshot("scene", old);
        AtomicInteger pageUploads = new AtomicInteger();
        assertTrue(manager.acceptPreparedPublication(candidate, 6L));

        assertThrows(IllegalStateException.class, () -> manager.uploadPreparedPublication(
                "scene",
                6L,
                6L,
                new FileHandle(dir.toFile()),
                (publishedFile, pixels, page) -> {
                    pageUploads.incrementAndGet();
                    return fakeTexture(2, 2);
                },
                layers -> {
                    throw new IllegalStateException("array upload failed");
                }
        ));

        assertTrue(candidate.isDisposed());
        assertEquals(0, pageUploads.get());
        assertSame(old, manager.activeSnapshot("scene"));
    }

    @Test
    public void oneShotArrayUploadBorrowsPreparedLayersForSubsequentPageUpload() throws Exception {
        Path dir = Files.createTempDirectory("prepared-atlas-one-shot-array");
        PreparedAtlasPublication candidate = prepare(
                "scene",
                9L,
                new FileHandle[]{writeSolidPage(dir.resolve("scene.png"), 0xff0000ff)},
                new String[]{"scene.png"}
        );
        Pixmap white = candidate.layer(0);
        Pixmap red = candidate.layer(1);
        CountingTexture pageTexture = fakeTexture(2, 2);
        GL20 previousGl = com.badlogic.gdx.Gdx.gl;
        GL20 previousGl20 = com.badlogic.gdx.Gdx.gl20;
        GL30 previousGl30 = com.badlogic.gdx.Gdx.gl30;
        int[] nextTexture = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                (proxy, method, args) -> {
                    if ("glGenTexture".equals(method.getName())) return nextTexture[0]++;
                    return defaultValue(method.getReturnType());
                }
        );
        com.badlogic.gdx.Gdx.gl = gl;
        com.badlogic.gdx.Gdx.gl20 = gl;
        com.badlogic.gdx.Gdx.gl30 = gl;

        PreparedAtlasPublication.Uploaded uploaded = null;
        TextureAtlas atlas = null;
        AtlasRuntimeService.TextureArrayBundle bundle = null;
        try {
            uploaded = candidate.upload(
                    new FileHandle(dir.toFile()),
                    (publishedFile, pixels, page) -> {
                        assertFalse(white.isDisposed());
                        assertFalse(red.isDisposed());
                        assertEquals(0xff0000ff, pixels.getPixel(0, 0));
                        return pageTexture;
                    },
                    StudioTextureArrayUploadBridge::uploadBorrowed
            );

            assertFalse(white.isDisposed());
            assertFalse(red.isDisposed());
            bundle = uploaded.buildBundle();
            atlas = uploaded.takeAtlas();
            candidate.close();

            assertTrue(white.isDisposed());
            assertTrue(red.isDisposed());
            assertEquals(2, bundle.textureArray.getWidth());
            assertEquals(2, bundle.textureArray.getHeight());
            assertEquals(2, bundle.textureArray.getDepth());
            assertFalse(bundle.textureArray.isManaged());
            assertEquals(0, bundle.handle2layer.get(
                    games.pixscape.runtime.render.InternalTextures.whiteHandle(), -1));
            assertEquals(1, bundle.handle2layer.get(
                    games.pixscape.runtime.service.TextureRegistry.handleOf(pageTexture), -1));
        } finally {
            if (atlas != null) atlas.dispose();
            if (bundle != null) bundle.textureArray.dispose();
            if (uploaded != null) uploaded.close();
            candidate.close();
            com.badlogic.gdx.Gdx.gl = previousGl;
            com.badlogic.gdx.Gdx.gl20 = previousGl20;
            com.badlogic.gdx.Gdx.gl30 = previousGl30;
        }
        assertEquals(1, pageTexture.disposeCalls);
    }

    @Test
    public void staleUploadedGenerationIsDisposedWithoutReplacingOldSnapshot() throws Exception {
        Path dir = Files.createTempDirectory("prepared-atlas-stale-upload");
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle old = bundle();
        manager.replaceActiveSnapshot("scene", old);
        PreparedAtlasPublication candidate = prepareEmpty("scene", 7L);
        CountingTextureArray textureArray = fakeTextureArray();
        assertTrue(manager.acceptPreparedPublication(candidate, 7L));
        PreparedAtlasPublication.Uploaded uploaded = manager.uploadPreparedPublication(
                "scene",
                7L,
                7L,
                new FileHandle(dir.toFile()),
                (publishedFile, pixels, page) -> fakeTexture(2, 2),
                layers -> textureArray
        );

        boolean published = manager.publishPreparedSnapshot("scene", 7L, 8L, uploaded);

        assertFalse(published);
        assertEquals(1, textureArray.disposeCalls);
        assertSame(old, manager.activeSnapshot("scene"));
    }

    private static PreparedAtlasPublication prepareEmpty(String sceneTag, long generation) {
        return PreparedAtlasPublication.prepareFromPages(
                sceneTag,
                generation,
                new Array<>(),
                new Array<>(),
                2
        );
    }

    private static PreparedAtlasPublication prepare(String sceneTag,
                                                    long generation,
                                                    FileHandle[] files,
                                                    String[] names) {
        return PreparedAtlasPublication.prepareFromPages(
                sceneTag,
                generation,
                new Array<>(files),
                new Array<>(names),
                2
        );
    }

    private static FileHandle writeSolidPage(Path path, int rgba) {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(rgba);
        pixmap.fill();
        FileHandle file = new FileHandle(path.toFile());
        PixmapIO.writePNG(file, pixmap);
        pixmap.dispose();
        return file;
    }

    private static FileHandle writeAtlas(Path path) throws Exception {
        String source = """
                scene.png
                size: 2, 2
                format: RGBA8888
                filter: Linear, Linear
                repeat: none
                hero
                bounds: 0, 0, 1, 2
                offsets: 0, 0, 1, 2
                rotate: false
                index: -1

                scene-2.png
                size: 2, 2
                format: RGBA8888
                filter: Linear, Linear
                repeat: none
                spark
                bounds: 1, 0, 1, 1
                offsets: 0, 0, 1, 1
                rotate: false
                index: -1
                """;
        Files.writeString(path, source, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }

    private static AtlasRuntimeService.TextureArrayBundle bundle() {
        return new AtlasRuntimeService.TextureArrayBundle(null, new IntIntMap());
    }

    private static CountingTexture fakeTexture(int width, int height) {
        try {
            CountingTexture texture = allocate(CountingTexture.class);
            texture.testWidth = width;
            texture.testHeight = height;
            return texture;
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static CountingTextureArray fakeTextureArray() {
        try {
            return allocate(CountingTextureArray.class);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class CountingTexture extends Texture {
        int testWidth;
        int testHeight;
        int disposeCalls;

        private CountingTexture() {
            super((TextureData) null);
        }

        @Override
        public int getWidth() {
            return testWidth;
        }

        @Override
        public int getHeight() {
            return testHeight;
        }

        @Override
        public void setFilter(TextureFilter minFilter, TextureFilter magFilter) {
        }

        @Override
        public void setWrap(TextureWrap u, TextureWrap v) {
        }

        @Override
        public void dispose() {
            disposeCalls++;
        }
    }

    private static final class CountingTextureArray extends TextureArray {
        int disposeCalls;

        private CountingTextureArray() {
            super((TextureArrayData) null);
        }

        @Override
        public void dispose() {
            disposeCalls++;
        }
    }
}
