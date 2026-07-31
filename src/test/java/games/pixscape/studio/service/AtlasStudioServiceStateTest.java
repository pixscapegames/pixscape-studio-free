package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.system.TiledFallbackSystem;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.BeforeClass;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AtlasStudioServiceStateTest {

    @BeforeClass
    public static void ensureHeadlessApplication() {
        if (Gdx.app == null) {
            new HeadlessApplication(
                    new ApplicationAdapter() {
                    },
                    new HeadlessApplicationConfiguration()
            );
        }
    }

    @Test
    public void requestAsyncPack_tracksQueuedScene_andScopedQueueVisibility() {
        AtlasStudioService service = new AtlasStudioService(null);

        service.requestAsyncPack("main");

        assertTrue(service.isPackRequested());
        assertFalse(service.isPackInProgress());
        assertTrue(service.hasAsyncPackQueuedOrRunningFor("main"));
        assertFalse(service.hasAsyncPackQueuedOrRunningFor("other"));
        assertTrue(service.hasAsyncPackQueuedOrRunningFor(null));
    }

    @Test
    public void markDirty_delegatesToAsyncPackRequest() {
        AtlasStudioService service = new AtlasStudioService(null);

        service.markDirty("scene-a");

        assertTrue(service.isPackRequested());
        assertTrue(service.hasAsyncPackQueuedOrRunningFor("scene-a"));
    }

    @Test
    public void atlasLoadUnloadAndUnloadAllRequestFallbackValidation()
            throws Exception {
        TiledFallbackSystem fallback = fallbackSystem();
        WorldCanvas canvas = allocate(WorldCanvas.class);
        setField(canvas, "tiledFallbackSystem", fallback);
        AtlasStudioService service = new AtlasStudioService(canvas);

        fallback.setEnabled(false);
        service.unload("main");
        assertTrue(fallback.isEnabled());

        fallback.setEnabled(false);
        service.unloadAll();
        assertTrue(fallback.isEnabled());

        Path emptyAtlas = Files.createTempFile(
                "tiled-fallback-validation",
                ".atlas"
        );
        fallback.setEnabled(false);
        service.load("main", new FileHandle(emptyAtlas.toFile()));
        assertTrue(fallback.isEnabled());
    }

    private static TiledFallbackSystem fallbackSystem() {
        StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(
                new VisualResolverTestSupport.TrackingAtlasService("main"),
                id -> null,
                new StudioAssetVisualResolver.StandaloneAssetAccess() {
                    @Override
                    public Texture resolveTexture(String projectRelativePath) {
                        return null;
                    }

                    @Override
                    public String[] listPngFramePaths(
                            String projectRelativeDirectory) {
                        return new String[0];
                    }
                }
        );
        TiledFallbackSystem system = new TiledFallbackSystem(
                new TiledMapRenderState(1),
                resolver,
                id -> null,
                null
        );
        new World(new WorldConfiguration().setSystem(system));
        return system;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static void setField(Object target,
                                 String fieldName,
                                 Object value)
            throws Exception {
        Field field = WorldCanvas.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Unsafe unsafe = unsafe();
        unsafe.putObject(
                target,
                unsafe.objectFieldOffset(field),
                value
        );
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
