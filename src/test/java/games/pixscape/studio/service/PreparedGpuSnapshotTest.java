package games.pixscape.studio.service;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.TextureArrayData;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class PreparedGpuSnapshotTest {

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
        Path dir = Files.createTempDirectory("prepared-gpu-snapshot-pixels");
        FileHandle red = writeSolidPage(dir.resolve("scene.png"), 0xff0000ff);
        FileHandle green = writeSolidPage(dir.resolve("scene-2.png"), 0x00ff00ff);
        PreparedGpuSnapshot candidate = prepare(
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

        candidate.close();
        assertTrue(whiteLayer.isDisposed());
        assertTrue(redLayer.isDisposed());
        assertTrue(greenLayer.isDisposed());
    }

    @Test
    public void newerAcceptedGenerationDisposesPreviousCandidateExactlyOnce() throws Exception {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        PreparedGpuSnapshot first = prepareEmpty("scene", 1L);
        PreparedGpuSnapshot second = prepareEmpty("scene", 2L);

        assertTrue(manager.acceptPreparedSnapshot(first, 1L));
        assertTrue(manager.acceptPreparedSnapshot(second, 2L));

        assertTrue(first.isDisposed());
        assertFalse(second.isDisposed());
        assertEquals(1, manager.preparedSnapshotCount());
        manager.disposeAll();
        assertTrue(second.isDisposed());
        assertEquals(0, manager.preparedSnapshotCount());
    }

    @Test
    public void staleGenerationIsRejectedAndCpuBuffersAreReleased() throws Exception {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        PreparedGpuSnapshot stale = prepareEmpty("scene", 3L);

        assertFalse(manager.acceptPreparedSnapshot(stale, 4L));

        assertTrue(stale.isDisposed());
        assertEquals(0, manager.preparedSnapshotCount());
    }

    @Test
    public void acceptedGenerationReleasesCpuBuffersAfterUploadAndGpuOwnerExactlyOnce() throws Exception {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        PreparedGpuSnapshot candidate = prepareEmpty("scene", 4L);
        CountingTextureArray textureArray = allocate(CountingTextureArray.class);

        assertTrue(manager.acceptPreparedSnapshot(candidate, 4L));
        PreparedGpuSnapshot.Uploaded uploaded = manager.uploadPreparedSnapshot(
                "scene",
                4L,
                4L,
                layers -> textureArray
        );

        assertTrue(candidate.isDisposed());
        assertEquals(0, manager.preparedSnapshotCount());
        assertEquals(0, textureArray.disposeCalls);
        uploaded.close();
        uploaded.close();
        assertEquals(1, textureArray.disposeCalls);
    }

    @Test
    public void failedUploadReleasesCandidateAndLeavesOldSnapshotActive() throws Exception {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle old = bundle();
        manager.replaceActiveSnapshot("scene", old);
        PreparedGpuSnapshot candidate = prepareEmpty("scene", 5L);
        assertTrue(manager.acceptPreparedSnapshot(candidate, 5L));

        assertThrows(IllegalStateException.class, () -> manager.uploadPreparedSnapshot(
                "scene",
                5L,
                5L,
                layers -> {
                    throw new IllegalStateException("upload failed");
                }
        ));

        assertTrue(candidate.isDisposed());
        assertEquals(0, manager.preparedSnapshotCount());
        assertSame(old, manager.activeSnapshot("scene"));
    }

    @Test
    public void staleUploadedGenerationIsDisposedWithoutReplacingOldSnapshot() throws Exception {
        GpuSnapshotManager manager = new GpuSnapshotManager(null, null);
        AtlasRuntimeService.TextureArrayBundle old = bundle();
        manager.replaceActiveSnapshot("scene", old);
        PreparedGpuSnapshot candidate = prepareEmpty("scene", 6L);
        CountingTextureArray textureArray = allocate(CountingTextureArray.class);
        assertTrue(manager.acceptPreparedSnapshot(candidate, 6L));
        PreparedGpuSnapshot.Uploaded uploaded = manager.uploadPreparedSnapshot(
                "scene",
                6L,
                6L,
                layers -> textureArray
        );

        boolean published = manager.publishPreparedSnapshot(
                "scene",
                6L,
                7L,
                uploaded,
                new Array<>()
        );

        assertFalse(published);
        assertEquals(1, textureArray.disposeCalls);
        assertSame(old, manager.activeSnapshot("scene"));
    }

    private static PreparedGpuSnapshot prepareEmpty(String sceneTag, long generation) {
        return PreparedGpuSnapshot.prepareFromPages(
                sceneTag,
                generation,
                new Array<>(),
                new Array<>(),
                2
        );
    }

    private static PreparedGpuSnapshot prepare(String sceneTag,
                                               long generation,
                                               FileHandle[] files,
                                               String[] names) {
        Array<FileHandle> pageFiles = new Array<>(files);
        Array<String> pageNames = new Array<>(names);
        return PreparedGpuSnapshot.prepareFromPages(
                sceneTag,
                generation,
                pageFiles,
                pageNames,
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

    private static AtlasRuntimeService.TextureArrayBundle bundle() {
        return new AtlasRuntimeService.TextureArrayBundle(null, new IntIntMap());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
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
