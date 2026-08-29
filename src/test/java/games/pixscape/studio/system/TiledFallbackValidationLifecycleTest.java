package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TiledAnimationComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.TiledAnimationSystem;
import games.pixscape.runtime.system.TiledEntityAnimationSystem;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAnimationProjectDefData;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

        canvas.publishAssetMetaDatabase(new AssetMetaDatabase());

        assertTrue(system.isEnabled());
    }

    @Test
    public void lookupBindingAloneDefersValidationToMetadataPublication()
            throws Exception {
        TiledFallbackSystem system = fallbackSystem();
        system.setEnabled(false);
        WorldCanvas canvas = canvas(system, new TileAnimationRegistry());

        canvas.bindAssetMetaLookup(id -> null);

        assertFalse(system.isEnabled());
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

    @Test
    public void projectAnimationReloadReplacesStaleLiveRegistryWithExactDiskDefinitions()
            throws Exception {
        Path path = Files.createTempDirectory("scene-service-animation-reload");
        FileHandle projectDir = new FileHandle(path.toFile());
        TileAnimationsMetaDatabase database = TileAnimationsIO.createEmpty();
        TileAnimationProjectDefData definition = new TileAnimationProjectDefData();
        definition.id = 42;
        definition.name = "Imported Tiled animation";
        definition.frameAssetIds = new int[]{701, 702};
        definition.frameDurationsMs = new int[]{90, 140};
        database.animations.add(definition);
        TileAnimationsIO.save(
                database,
                projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );

        TiledFallbackSystem system = fallbackSystem();
        system.setEnabled(false);
        TileAnimationRegistry registry = new TileAnimationRegistry();
        registry.put(100, new int[]{7}, new int[]{100});
        WorldCanvas canvas = canvas(system, registry);
        SceneService sceneService = allocate(SceneService.class);
        setField(sceneService, SceneService.class, "canvas", canvas);

        Method reload = SceneService.class.getDeclaredMethod(
                "reloadTileAnimationsFromProject",
                FileHandle.class
        );
        reload.setAccessible(true);
        reload.invoke(sceneService, projectDir);

        assertFalse(registry.contains(100));
        assertTrue(registry.contains(42));
        assertEquals(1, registry.size());
        TileAnimationDef runtimeDefinition = registry.get(42);
        assertNotNull(runtimeDefinition);
        assertEquals(701, runtimeDefinition.frameAssetId(0));
        assertEquals(702, runtimeDefinition.frameAssetId(1));
        assertEquals(90, runtimeDefinition.frameDurationMs(0));
        assertEquals(140, runtimeDefinition.frameDurationMs(1));
        assertTrue(system.isEnabled());
        assertImportedAnimationConsumersAdvanceImmediately(registry);

        TileAnimationsMetaDatabase restoredDatabase = TileAnimationsIO.createEmpty();
        TileAnimationProjectDefData restoredDefinition = new TileAnimationProjectDefData();
        restoredDefinition.id = 100;
        restoredDefinition.name = "Pre-import animation";
        restoredDefinition.frameAssetIds = new int[]{7};
        restoredDefinition.frameDurationsMs = new int[]{100};
        restoredDatabase.animations.add(restoredDefinition);
        TileAnimationsIO.save(
                restoredDatabase,
                projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON)
        );

        reload.invoke(sceneService, projectDir);

        assertTrue(registry.contains(100));
        assertFalse(registry.contains(42));
        assertEquals(1, registry.size());
    }

    private static void assertImportedAnimationConsumersAdvanceImmediately(
            TileAnimationRegistry registry) {
        VisualResolverTestSupport.TrackingAtlasService atlas =
                new VisualResolverTestSupport.TrackingAtlasService("scene");
        atlas.publish(
                new TextureAtlas(),
                VisualResolverTestSupport.binding(
                        701,
                        "frame-701",
                        VisualResolverTestSupport.texture(16, 16)
                ),
                VisualResolverTestSupport.binding(
                        702,
                        "frame-702",
                        VisualResolverTestSupport.texture(16, 16)
                )
        );
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(8);
        TiledAnimationSystem tiledSystem = new TiledAnimationSystem(registry);
        tiledSystem.setAdvanceOnlyVisibleChunks(false);
        TiledEntityAnimationSystem entitySystem =
                new TiledEntityAnimationSystem(registry, atlas);
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, tiledSystem, entitySystem)
                .build());

        int layerEntity = world.create();
        world.getMapper(LayerComponent.class).create(layerEntity).type = LayerComponent.TYPE_CLASSIC;
        int mapEntity = world.create();
        world.getMapper(EntityIndexComponent.class).create(mapEntity).layerIndex = 0;
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(mapEntity);
        tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
        tiled.data.setTile(0, 0, 42);
        TileChunk chunk = tiled.data.getChunk(0, 0);
        TileAnimationStateSupport.syncWorldCell(chunk, 0, 0, registry);

        int objectEntity = world.create();
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(objectEntity);
        assetRef.assetId = 700;
        assetRef.atlasTag = "scene";
        world.getMapper(TextureRegionComponent.class).create(objectEntity);
        world.getMapper(RenderMaterialComponent.class).create(objectEntity);
        world.getMapper(TiledAnimationComponent.class).create(objectEntity).animationId = 42;

        world.setDelta(0.091f);
        world.process();

        assertEquals(1, chunk.getAnimFrameIndex(0));
        assertEquals(1, chunk.getAnimFrameElapsedMs(0));
        assertEquals(702, TileAnimationResolver.resolveVisualAssetId(
                42,
                chunk.getAnimFrameIndex(0),
                registry
        ));
        TiledAnimationComponent objectAnimation =
                world.getMapper(TiledAnimationComponent.class).get(objectEntity);
        assertEquals(1, objectAnimation.frameIndex);
        assertEquals(1, objectAnimation.frameElapsedMs);
        assertEquals(702, objectAnimation.appliedFrameAssetId);
        assertEquals(700, assetRef.assetId);
        world.dispose();
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
