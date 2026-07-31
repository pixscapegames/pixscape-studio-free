package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TiledFallbackValidationLifecycleTest {

    @Test
    public void canvasRequestIsSafeBeforeSystemCreationAndEnablesExistingSystem()
            throws Exception {
        WorldCanvas incompleteCanvas = allocate(WorldCanvas.class);
        incompleteCanvas.requestTiledFallbackValidation();

        TiledFallbackSystem system = fallbackSystem();
        system.setEnabled(false);
        WorldCanvas canvas = canvas(system, new TileAnimationRegistry());

        canvas.requestTiledFallbackValidation();

        assertTrue(system.isEnabled());
    }

    @Test
    public void profileReloadRequestsValidation() throws Exception {
        TiledFallbackSystem system = fallbackSystem();
        system.setEnabled(false);
        WorldCanvas canvas = canvas(system, new TileAnimationRegistry());

        canvas.refreshTilesetProfileRegistry(new AssetMetaDatabase());

        assertTrue(system.isEnabled());
    }

    @Test
    public void assetMetadataLookupReplacementRequestsValidation()
            throws Exception {
        TiledFallbackSystem system = fallbackSystem();
        system.setEnabled(false);
        WorldCanvas canvas = canvas(system, new TileAnimationRegistry());

        canvas.bindAssetMetaLookup(id -> null);

        assertTrue(system.isEnabled());
    }

    @Test
    public void centralAnimationRegistryReloadRequestsValidation()
            throws Exception {
        TiledFallbackSystem system = fallbackSystem();
        system.setEnabled(false);
        TileAnimationRegistry registry = new TileAnimationRegistry();
        registry.put(100, new int[]{7}, new int[]{100});
        WorldCanvas canvas = canvas(system, registry);
        SceneService sceneService = allocate(SceneService.class);
        setField(sceneService, SceneService.class, "canvas", canvas);

        Method reload = SceneService.class.getDeclaredMethod(
                "reloadTileAnimationRegistryFromProjectData"
        );
        reload.setAccessible(true);
        reload.invoke(sceneService);

        assertTrue(system.isEnabled());
        assertFalse(registry.contains(100));
    }

    private static TiledFallbackSystem fallbackSystem() {
        TiledFallbackSystem system = new TiledFallbackSystem(
                new TiledMapRenderState(1),
                visualResolver(),
                id -> null,
                null
        );
        new World(new WorldConfiguration().setSystem(system));
        return system;
    }

    private static StudioAssetVisualResolver visualResolver() {
        return new StudioAssetVisualResolver(
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
    }

    private static WorldCanvas canvas(TiledFallbackSystem system,
                                      TileAnimationRegistry registry)
            throws Exception {
        WorldCanvas canvas = allocate(WorldCanvas.class);
        setField(
                canvas,
                WorldCanvas.class,
                "tiledFallbackSystem",
                system
        );
        setField(
                canvas,
                WorldCanvas.class,
                "tileAnimationRegistry",
                registry
        );
        setField(
                canvas,
                WorldCanvas.class,
                "assetVisualResolver",
                visualResolver()
        );
        return canvas;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static void setField(Object target,
                                 Class<?> declaringType,
                                 String fieldName,
                                 Object value)
            throws Exception {
        Field field = declaringType.getDeclaredField(fieldName);
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
