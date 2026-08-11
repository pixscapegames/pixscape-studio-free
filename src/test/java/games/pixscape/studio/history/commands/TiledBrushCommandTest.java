package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.Texture;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.asset.VisualResolverTestSupport;
import games.pixscape.studio.service.tiled.TiledBrushSession;
import games.pixscape.studio.service.tiled.TiledMutationPlan;
import games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner;
import games.pixscape.studio.system.TiledFallbackSystem;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.Assert;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class TiledBrushCommandTest {
    @Test
    public void executeUndoRedoApplyCompleteSnapshotWithOneRevisionEach() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4);
        tiled.data.setTile(1, 1, 3, TileTransformFlags.FLIP_V);
        int initialRevision = tiled.data.contentRevision();
        TiledBrushSession session = new TiledBrushSession(layer);
        session.apply(tiled, 1, 1, 8, TileTransformFlags.FLIP_H);
        session.apply(tiled, 2, 1, 9);
        TiledMutationPlan plan = session.toPlan();
        HistoryManager history = new HistoryManager(16);
        long historyId = history.historyIds().ensureForEntity(layer);
        TiledBrushCommand command = new TiledBrushCommand(world, null, history.historyIds(),
                historyId, plan, new TiledSpatialMutationPlanner());

        history.execute(command);
        Assert.assertEquals(initialRevision + 1, tiled.data.contentRevision());
        Assert.assertEquals(8, tiled.data.getTile(1, 1));
        Assert.assertEquals(9, tiled.data.getTile(2, 1));
        Assert.assertEquals(1, history.getCursor());

        history.undo();
        Assert.assertEquals(initialRevision + 2, tiled.data.contentRevision());
        Assert.assertEquals(3, tiled.data.getTile(1, 1));
        Assert.assertEquals(TileTransformFlags.FLIP_V, tiled.data.getTileTransformFlags(1, 1));
        Assert.assertEquals(0, tiled.data.getTile(2, 1));
        Assert.assertEquals(0, history.getCursor());

        history.redo();
        Assert.assertEquals(initialRevision + 3, tiled.data.contentRevision());
        Assert.assertEquals(8, tiled.data.getTile(1, 1));
        Assert.assertEquals(9, tiled.data.getTile(2, 1));
        Assert.assertEquals(1, history.getCursor());
    }

    @Test
    public void rejectedRedoLeavesHistoryCursorAndMapUnchanged() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4);
        tiled.data.setTile(2, 2, 3);
        TiledBrushSession session = new TiledBrushSession(layer);
        session.apply(tiled, 2, 2, 0);
        HistoryManager history = new HistoryManager(16);
        TiledBrushCommand command = new TiledBrushCommand(world, null, history.historyIds(),
                history.historyIds().ensureForEntity(layer), session.toPlan(),
                new TiledSpatialMutationPlanner());
        history.execute(command);
        history.undo();
        int revision = tiled.data.contentRevision();

        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layer);
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 5;
        wall.structureId = 9;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(2, 2, 3);
        blocks.blocks.add(wall);
        try {
            history.redo();
            Assert.fail("Expected linked-anchor redo rejection.");
        } catch (games.pixscape.studio.service.tiled.TiledMutationRejectedException expected) {
            Assert.assertEquals(0, history.getCursor());
            Assert.assertEquals(3, tiled.data.getTile(2, 2));
            Assert.assertEquals(revision, tiled.data.contentRevision());
            Assert.assertTrue(history.canRedo());
        }
    }

    @Test
    public void redoUndoRedoAlwaysRequestFallbackValidation() throws Exception {
        ProjectConfig previousConfig = ProjectConfig.getInstance();
        ProjectConfig.setInstance(null);
        try {
            World world = new World(new WorldConfiguration());
            int layer = world.create();
            TiledLayerComponent tiled =
                    world.getMapper(TiledLayerComponent.class).create(layer);
            tiled.data = new TiledMapLayerData(8, 8, 32, 16, 4);
            TiledBrushSession session = new TiledBrushSession(layer);
            session.apply(tiled, 1, 1, 8);

            TiledFallbackSystem fallback = fallbackSystem();
            WorldCanvas canvas = allocate(WorldCanvas.class);
            setField(canvas, WorldCanvas.class, "tiledFallbackSystem", fallback);
            setField(
                    canvas,
                    WorldCanvas.class,
                    "tileAnimationRegistry",
                    new TileAnimationRegistry()
            );
            SceneService sceneService = allocate(SceneService.class);
            setField(sceneService, SceneService.class, "canvas", canvas);

            HistoryManager history = new HistoryManager(16);
            TiledBrushCommand command = new TiledBrushCommand(
                    world,
                    sceneService,
                    history.historyIds(),
                    history.historyIds().ensureForEntity(layer),
                    session.toPlan(),
                    new TiledSpatialMutationPlanner()
            );

            fallback.setEnabled(false);
            history.execute(command);
            Assert.assertTrue(fallback.isEnabled());

            fallback.setEnabled(false);
            history.undo();
            Assert.assertTrue(fallback.isEnabled());

            fallback.setEnabled(false);
            history.redo();
            Assert.assertTrue(fallback.isEnabled());
        } finally {
            ProjectConfig.setInstance(previousConfig);
        }
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
