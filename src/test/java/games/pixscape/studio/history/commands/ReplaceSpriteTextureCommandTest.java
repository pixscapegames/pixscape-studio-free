package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.UpdateWorldGeometrySystem;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.atlas.SceneAtlasInputService;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;
import static org.junit.Assert.*;

public class ReplaceSpriteTextureCommandTest {
    private final DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
    private final World world = new World(new WorldConfigurationBuilder()
            .with(dirty, new UpdateWorldGeometrySystem(), new WorldSerializationManager()).build());
    private final HistoryManager history = new HistoryManager(32);
    private final AssetMetaDatabase assets = new AssetMetaDatabase();
    private final AtlasRuntimeService atlas = new AtlasRuntimeService();
    private final ObjectMap<String, Texture> textures = new ObjectMap<>();
    private final AssetMeta first = image("square", 32, 32);
    private final AssetMeta second = image("wide", 64, 16);
    private final StudioAssetVisualResolver resolver = new StudioAssetVisualResolver(atlas, assets::findById,
            new StudioAssetVisualResolver.StandaloneAssetAccess() {
                @Override
                public Texture resolveTexture(String path) {
                    return textures.get(path);
                }

                @Override
                public String[] listPngFramePaths(String path) {
                    return new String[0];
                }
            });

    @After
    public void dispose() {
        world.dispose();
        TextureRegistry.clear();
    }

    @Test
    public void nativeRegionSizePreservesTransformAndFlipsAndRefreshesBoundsAcrossHistory() {
        int entity = sprite();
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        transform.x = 93;
        transform.y = -12;
        transform.originX = 3;
        transform.originY = -7;
        transform.scaleX = -2;
        transform.scaleY = 3;
        transform.rotationRad = .43f;
        transform.refreshCaches();
        String pose = json(transform);
        DimensionsComponent size = world.getMapper(DimensionsComponent.class).get(entity);
        size.width = 37;
        size.height = 21;
        dirty.geometry(entity, games.pixscape.runtime.render.GeometryDirty.ALL);
        world.process();
        float oldMinX = world.getMapper(AABBComponent.class).get(entity).minX;

        // A region in a much larger atlas must not adopt the atlas page dimensions.
        TextureAtlas packed = new TextureAtlas();
        Texture page = new Texture() {
            @Override
            public int getWidth() {
                return 256;
            }

            @Override
            public int getHeight() {
                return 256;
            }

            @Override
            public void setFilter(TextureFilter min, TextureFilter mag) {
                // Atlas indexing is tested without allocating an OpenGL texture.
            }
        };
        packed.addRegion("wide__a" + second.id(), page, 8, 32, 64, 16);
        atlas.loadBorrowed("scene1", packed);
        history.execute(command(entity, second.id()));
        for (int cycle = 0; cycle < 4; cycle++) {
            world.process();
            assertState(entity, second.id(), 64, 16);
            assertEquals(64, world.getMapper(TextureRegionComponent.class).get(entity).pixW);
            assertEquals(pose, json(transform));
            assertNotEquals(oldMinX, world.getMapper(AABBComponent.class).get(entity).minX, .001f);
            history.undo();
            world.process();
            assertState(entity, first.id(), 37, 21);
            assertEquals(oldMinX, world.getMapper(AABBComponent.class).get(entity).minX, .001f);
            assertEquals(pose, json(transform));
            history.redo();
        }
        assertEquals(1, history.getCursor());
    }

    @Test
    public void atlasReachabilityAndPublicationCallbackFollowReplacementAndUndo() throws Exception {
        int entity = sprite();
        ProjectConfig config = new ProjectConfig();
        config.projectFileName = "texture-inputs";
        config.createSceneMeta("Main");
        FileHandle project = new FileHandle(Files.createTempDirectory("texture-inputs").toFile());
        config.projectDirectoryPath = project.path();
        project.child(first.sourceRelPath()).writeString("fixture", false);
        project.child(second.sourceRelPath()).writeString("fixture", false);
        com.badlogic.gdx.Files previousFiles = com.badlogic.gdx.Gdx.files;
        com.badlogic.gdx.Gdx.files = new com.badlogic.gdx.backends.headless.HeadlessFiles();
        try {
            SceneAtlasInputService inputs = new SceneAtlasInputService();
            List<Set<String>> publications = new ArrayList<>();
            history.execute(new ReplaceSpriteTextureCommand(world, history.historyIds(), entity, second.id(),
                    resolver, assets::findById, ignored -> publications.add(inputs.collectRequiredAtlasInputPathsForScene(
                            config, world, config.getCurrentSceneMeta(), assets, null))));
            history.undo();
            history.redo();
            assertEquals(List.of(Set.of(second.sourceRelPath()), Set.of(first.sourceRelPath()),
                    Set.of(second.sourceRelPath())), publications);
            config.getCurrentSceneMeta().runtimeAvailability.spriteAssetIds.add(first.id());
            assertEquals(Set.of(first.sourceRelPath(), second.sourceRelPath()),
                    inputs.collectRequiredAtlasInputPathsForScene(config, world, config.getCurrentSceneMeta(), assets, null));
        } finally {
            com.badlogic.gdx.Gdx.files = previousFiles;
            project.deleteDirectory();
        }
    }

    @Test
    public void independentSettingsAndChildLocalTransformRemainUntouched() {
        int entity = sprite();
        world.getMapper(GameObjectComponent.class).create(entity);
        int child = sprite();
        world.getMapper(PixscapeIdentityComponent.class).get(child).stableId = 2;
        world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 1;
        world.getMapper(TransformComponent.class).get(child).x = 13;
        world.getMapper(TransformComponent.class).get(child).rotationRad = .7f;
        world.getMapper(RenderMaterialComponent.class).get(entity).shaderIdx = 8;
        world.getMapper(TintComponent.class).get(entity).rgba = 0x4db3e6cc;
        world.getMapper(RenderRepeatComponent.class).create(entity).repeatX = true;
        world.getMapper(SpatialFootprintComponent.class).create(entity).radiusPx = 9;
        world.getMapper(SpatialFootprintComponent.class).get(entity).altitude = 27;
        world.getMapper(SurfaceLightingComponent.class).create(entity).altitude = 12;
        world.getMapper(PhysicsBodyComponent.class).create(entity);
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = 1;
        shape.geometry = new PhysicsGeometryData();
        world.getMapper(PhysicsShapesComponent.class).create(entity).shapes.add(shape);
        world.process();
        String before = independentState(entity, child);
        history.execute(command(entity, second.id()));
        assertEquals(before, independentState(entity, child));
        history.undo();
        history.redo();
        assertEquals(before, independentState(entity, child));
    }

    @Test
    public void staticOcclusionRectangleRemainsNormalizedAndIsNotReassignedOnImageRoundTrip() {
        int entity = sprite();
        SpatialRepresentationComponent spatial = world.getMapper(SpatialRepresentationComponent.class).create(entity);
        spatial.rectangles = new ActorSpatialRectangles();
        ActorSpatialRectangleData rectangle = new ActorSpatialRectangleData();
        rectangle.width = .5f;
        rectangle.height = .75f;
        rectangle.x = -.2f;
        rectangle.pivotX = 7;
        spatial.rectangles.put(first.id(), null, rectangle);
        String before = json(spatial);
        history.execute(command(entity, second.id()));
        assertSame(rectangle, spatial.rectangles.get(second.id(), null));
        assertEquals(32, rectangle.width * world.getMapper(DimensionsComponent.class).get(entity).width, 0);
        history.execute(command(entity, first.id()));
        assertState(entity, first.id(), 32, 32);
        assertEquals(16, rectangle.width * world.getMapper(DimensionsComponent.class).get(entity).width, 0);
        assertEquals(before, json(spatial));
        history.undo();
        history.undo();
        assertEquals(before, json(spatial));
    }

    @Test
    public void sameAssetAndInvalidResourceDoNotMutateOrDiscardRedo() {
        int entity = sprite();
        history.execute(command(entity, second.id()));
        history.undo();
        long revision = history.getMutationRevision();
        history.execute(command(entity, first.id()));
        AssetMeta missing = image("missing", 10, 20);
        textures.remove(missing.sourceRelPath());
        assertThrows(IllegalArgumentException.class, () -> history.execute(command(entity, missing.id())));
        assertThrows(IllegalArgumentException.class, () -> history.execute(command(entity, 9999)));
        assertState(entity, first.id(), 32, 32);
        assertEquals(revision, history.getMutationRevision());
        assertTrue(history.canRedo());
        assertEquals(0, history.getCursor());
    }

    @Test
    public void unavailableRedoPreservesThePublishedStateAndHistory() {
        int entity = sprite();
        history.execute(command(entity, second.id()));
        history.undo();
        textures.remove(second.sourceRelPath());
        resolver.invalidateAll();
        assertThrows(IllegalArgumentException.class, history::redo);
        assertState(entity, first.id(), 32, 32);
        assertTrue(history.canRedo());
        assertEquals(0, history.getCursor());
    }

    @Test
    public void roleIncludesSpriteParentsButExcludesSharedRenderCapabilities() {
        int entity = sprite();
        world.getMapper(GameObjectComponent.class).create(entity);
        assertTrue(ReplaceSpriteTextureCommand.isSprite(world, entity, assets::findById));
        world.getMapper(TiledMapLayerComponent.class).create(entity).kind = TiledMapLayerComponent.Kind.IMAGE;
        assertFalse(ReplaceSpriteTextureCommand.isSprite(world, entity, assets::findById));
        world.getMapper(TiledMapLayerComponent.class).remove(entity);
        world.getMapper(AnimationComponent.class).create(entity);
        assertFalse(ReplaceSpriteTextureCommand.isSprite(world, entity, assets::findById));
        world.getMapper(AnimationComponent.class).remove(entity);
        world.getMapper(ParticleEmitterComponent.class).create(entity);
        assertFalse(ReplaceSpriteTextureCommand.isSprite(world, entity, assets::findById));
        world.getMapper(ParticleEmitterComponent.class).remove(entity);
        AssetMeta tile = assets.registerIfAbsent(AssetType.TILE, "tiles/0", "tile.png", AssetMeta.AssetScope.USER);
        world.getMapper(AssetRefComponent.class).get(entity).assetId = tile.id();
        assertFalse(ReplaceSpriteTextureCommand.isSprite(world, entity, assets::findById));
    }

    @Test
    public void historyIdentitySurvivesSnapshotRestorationAndSceneRuntimeReload() throws Exception {
        int entity = sprite();
        history.execute(command(entity, second.id()));
        long historyId = history.historyIds().historyIdOfEntity(entity);
        GenericEntityInitializer captured = new GenericEntityInitializer(world);
        captured.syncFrom(entity);
        history.historyIds().unbindEntity(entity);
        world.delete(entity);
        world.process();
        int restored = world.create();
        captured.init(restored);
        world.process();
        history.historyIds().bind(restored, historyId);
        history.undo();
        assertState(restored, first.id(), 32, 32);
        history.redo();
        FileHandle project = new FileHandle(Files.createTempDirectory("sprite-texture-project").toFile());
        FileHandle scene = project.child("scenes/scene1.json");
        scene.parent().mkdirs();
        World loaded = new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
        try {
            SceneService.saveScene(world, scene, false);
            SceneMetaRuntime metadata = new SceneMetaRuntime();
            metadata.nextEntityStableId = 2;
            SceneLoader.loadScene(loaded, scene, false, metadata);
            loaded.process();
            int loadedId = loaded.getAspectSubscriptionManager().get(com.artemis.Aspect.all(AssetRefComponent.class))
                    .getEntities().get(0);
            assertEquals(second.id(), loaded.getMapper(AssetRefComponent.class).get(loadedId).assetId);
            assertEquals(64, loaded.getMapper(DimensionsComponent.class).get(loadedId).width, 0);

            ProjectConfig config = new ProjectConfig();
            config.projectTitle = "Texture regression";
            config.projectFileName = "texture";
            config.exportRootPathDir = project.child("export").path();
            config.createSceneMeta("Main");
            config.getCurrentSceneMeta().nextEntityStableId = 2;
            config.getCurrentSceneMeta().useNormalMaps = false;
            assets.save(project.child("assets.json"));
            FileHandle page = project.child("atlases/scene1.png");
            page.parent().mkdirs();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64, 16,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", page.file());
            project.child("atlases/scene1.atlas").writeString("scene1.png\nsize:64,16\nformat:RGBA8888\n"
                    + "filter:Nearest,Nearest\nrepeat:none\nwide__a" + second.id()
                    + "\nbounds:0,0,64,16\noffsets:0,0,64,16\n", false);
            RuntimeExport.exportRuntime(config, project, project.child("export"));
            World runtime = new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
            try {
                SceneLoader.loadScene(runtime, project.child("export/" + RuntimeExport.RUNTIME_DIR_NAME
                        + "/scenes/scene1.json"), false, config.getCurrentSceneMeta());
                runtime.process();
                int runtimeId = runtime.getAspectSubscriptionManager().get(com.artemis.Aspect.all(AssetRefComponent.class))
                        .getEntities().get(0);
                assertEquals(second.id(), runtime.getMapper(AssetRefComponent.class).get(runtimeId).assetId);
                assertEquals(64, runtime.getMapper(DimensionsComponent.class).get(runtimeId).width, 0);
            } finally {
                runtime.dispose();
            }
        } finally {
            loaded.dispose();
            project.deleteDirectory();
        }
    }

    private AssetMeta image(String name, int width, int height) {
        AssetMeta meta = assets.registerIfAbsent(AssetType.IMAGE, "images/" + name, "orig/" + name + ".png", AssetMeta.AssetScope.USER);
        textures.put(meta.sourceRelPath(), texture(width, height));
        return meta;
    }

    private int sprite() {
        int entity = world.create();
        new GenericEntityInitializer(world).configureSprite(first.id(), "scene1", "square", 0, 0, 1, 1,
                32, 32, 10, 20, 3, 7, 0, 0, 0, "Sprite", 0).init(entity);
        world.getMapper(PixscapeIdentityComponent.class).get(entity).stableId = 1;
        world.process();
        return entity;
    }

    private ReplaceSpriteTextureCommand command(int entity, int image) {
        return new ReplaceSpriteTextureCommand(world, history.historyIds(), entity, image, resolver, assets::findById, null);
    }

    private void assertState(int entity, int asset, float width, float height) {
        assertEquals(asset, world.getMapper(AssetRefComponent.class).get(entity).assetId);
        DimensionsComponent size = world.getMapper(DimensionsComponent.class).get(entity);
        assertEquals(width, size.width, 0);
        assertEquals(height, size.height, 0);
    }

    private String independentState(int entity, int child) {
        return json(world.getMapper(TransformComponent.class).get(entity))
                + json(world.getMapper(TransformComponent.class).get(child))
                + json(world.getMapper(GameObjectMemberComponent.class).get(child))
                + json(world.getMapper(RenderMaterialComponent.class).get(entity))
                + json(world.getMapper(TintComponent.class).get(entity))
                + json(world.getMapper(RenderRepeatComponent.class).get(entity))
                + json(world.getMapper(SpatialFootprintComponent.class).get(entity))
                + json(world.getMapper(SurfaceLightingComponent.class).get(entity))
                + json(world.getMapper(PhysicsBodyComponent.class).get(entity))
                + json(world.getMapper(PhysicsShapesComponent.class).get(entity));
    }

    private static String json(Object value) {
        return new Json().toJson(value);
    }
}
