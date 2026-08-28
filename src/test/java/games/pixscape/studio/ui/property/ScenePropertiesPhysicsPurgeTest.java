package games.pixscape.studio.ui.property;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisDialog;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionReconciler;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.system.UiRefreshDispatchSystem;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ScenePropertiesPhysicsPurgeTest {
    @BeforeClass
    public static void loadUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void purgeRemovesPhysicsButPreservesOrdinaryLayers() throws Exception {
        SceneMeta meta = new SceneMeta();
        meta.physicsEnabled = true;
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        config.getCurrentSceneMeta().physicsEnabled = true;
        meta = config.getCurrentSceneMeta();
        ProjectConfig.setInstance(config);

        DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        Box2dWorldService box2d =
                new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        UiRefreshDispatchSystem afterEcs = new UiRefreshDispatchSystem();
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, sync, afterEcs)
                .build());
        sync.setSceneMeta(meta);
        sync.setEnabled(true);
        sync.setStepEnabled(false);

        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        LayerService layers = new LayerService(
                world,
                new TiledAllocatorService(),
                history.historyIds(),
                identities);
        SelectionService selection = new SelectionService(world, layers);
        PhysicsSelectionService physicsSelection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(physicsSelection);
        reconciler.bindWorld(world);
        PhysicsService physics = new PhysicsService(world, box2d, meta);

        int firstLayer = layer(world, history, 0, LayerComponent.TYPE_CLASSIC);
        int activeLayer = layer(world, history, 1, LayerComponent.TYPE_CLASSIC);
        int unrelatedEntity = world.create();
        world.getMapper(TransformComponent.class).create(unrelatedEntity).x = 42f;
        history.historyIds().ensureForEntity(unrelatedEntity);
        int bodyA = body(world, history, physics, 0f);
        int bodyB = body(world, history, physics, 100f);
        int joint = physics.createDistanceJoint(bodyA, bodyB);
        long jointHistoryId = history.historyIds().ensureForEntity(joint);
        long bodyHistoryId = history.historyIds().historyIdOfEntity(bodyA);
        selection.setActivelayerId(activeLayer);
        physicsSelection.focusBody(bodyA);
        history.execute(new CounterCommand());
        boolean[] previewDirty = {false};
        boolean[] disposed = {false};

        world.process();
        Assert.assertEquals(2, box2d.world.getBodyCount());
        Assert.assertEquals(1, box2d.world.getJointCount());

        SceneProperties properties = new SceneProperties(
                world,
                history,
                physics,
                selection,
                layers,
                reconciler,
                () -> {
                    Assert.assertEquals(0, box2d.world.getBodyCount());
                    Assert.assertEquals(0, box2d.world.getJointCount());
                    sync.setEnabled(false);
                    sync.setStepEnabled(false);
                    sync.setBox2d(null);
                    physics.setBox2d(null);
                    box2d.dispose();
                    disposed[0] = true;
                },
                () -> previewDirty[0] = true);

        Method begin = SceneProperties.class.getDeclaredMethod(
                "beginPhysicsPurge", SceneMeta.class);
        begin.setAccessible(true);
        begin.invoke(properties, meta);

        Assert.assertTrue(meta.physicsEnabled);
        Assert.assertFalse(disposed[0]);
        Assert.assertTrue(history.canUndo());

        world.process();

        Assert.assertFalse(meta.physicsEnabled);
        Assert.assertTrue(disposed[0]);
        Assert.assertTrue(previewDirty[0]);
        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertTrue(history.isDirty());
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(bodyA));
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(bodyB));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(bodyA));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(bodyB));
        Assert.assertEquals(0, world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsBodyComponent.class)).getEntities().size());
        Assert.assertEquals(0, world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsJointComponent.class)).getEntities().size());
        Assert.assertEquals(2, layers.count());
        Assert.assertTrue(world.getEntityManager().isActive(firstLayer));
        Assert.assertTrue(world.getEntityManager().isActive(activeLayer));
        Assert.assertTrue(world.getEntityManager().isActive(unrelatedEntity));
        Assert.assertEquals(42f,
                world.getMapper(TransformComponent.class).get(unrelatedEntity).x, 0f);
        Assert.assertEquals(activeLayer, selection.getActivelayerId());
        Assert.assertEquals(-1, history.historyIds().entityOfHistoryId(jointHistoryId));
        Assert.assertEquals(bodyA, history.historyIds().entityOfHistoryId(bodyHistoryId));
        Assert.assertFalse(physicsSelection.hasFocusedBody());
        identities.bind(null, null);
        world.dispose();
    }

    @Test
    public void cancelLeavesEcsMetadataHistoryAndPreviewUntouched() throws Exception {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        SceneMeta meta = config.getCurrentSceneMeta();
        meta.physicsEnabled = true;
        ProjectConfig.setInstance(config);

        World world = new World(new WorldConfigurationBuilder()
                .with(new UiRefreshDispatchSystem())
                .build());
        int entityId = world.create();
        HistoryManager history = new HistoryManager(8);
        long historyId = history.historyIds().ensureForEntity(entityId);
        history.execute(new CounterCommand());
        boolean[] previewDirty = {false};
        SceneProperties properties = new SceneProperties(
                world, history, null, null, null, null, null,
                () -> previewDirty[0] = true);
        Batch batch = (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
        Stage stage = new Stage(new ScreenViewport(), batch);
        stage.addActor(properties);

        Method show = SceneProperties.class.getDeclaredMethod(
                "showRemoveAllPhysicsDialog", SceneMeta.class);
        show.setAccessible(true);
        show.invoke(properties, meta);
        VisDialog dialog = null;
        for (Actor actor : stage.getActors()) {
            if (actor instanceof VisDialog) dialog = (VisDialog) actor;
        }
        Assert.assertNotNull(dialog);
        Method result = dialog.getClass().getDeclaredMethod("result", Object.class);
        result.setAccessible(true);
        result.invoke(dialog, false);

        Assert.assertTrue(meta.physicsEnabled);
        Assert.assertTrue(world.getEntityManager().isActive(entityId));
        Assert.assertEquals(entityId, history.historyIds().entityOfHistoryId(historyId));
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(previewDirty[0]);
        stage.dispose();
        world.dispose();
    }

    @Test
    public void globalPhysicsPurgeWarningRemainsPermanent() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/property/SceneProperties.java"),
                StandardCharsets.UTF_8);

        Assert.assertTrue(source.contains(
                "Disabling physics will permanently delete all physics in this scene."));
    }

    @Test
    public void pixelsPerMeterDefaultsToOneHundredAndEditingKeepsLiveCallback()
            throws Exception {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        SceneMeta meta = config.getCurrentSceneMeta();
        ProjectConfig.setInstance(config);
        boolean[] previewDirty = {false};
        World world = new World(new WorldConfigurationBuilder()
                .with(new UiRefreshDispatchSystem())
                .build());
        SceneProperties properties = new SceneProperties(
                world, new HistoryManager(8), null, null, null, null, null,
                () -> previewDirty[0] = true);

        Assert.assertEquals(100f, meta.pixelsPerMeter, 0f);
        Method refreshPhysics = SceneProperties.class.getDeclaredMethod(
                "refreshPhysicsFromMeta");
        refreshPhysics.setAccessible(true);
        refreshPhysics.invoke(properties);
        Field ppmField = SceneProperties.class.getDeclaredField("pixelsPerMeter");
        ppmField.setAccessible(true);
        SimpleFloatField ppm = (SimpleFloatField) ppmField.get(properties);
        int[] eventCount = {0};
        float[] publishedPpm = {0f};
        EventFlow.Listener<EventFlow.ScenePhysicsPixelsPerMeterChanged> listener =
                event -> {
                    eventCount[0]++;
                    publishedPpm[0] = event.pixelsPerMeter();
                };
        EventFlow.i().flush();
        EventFlow.i().subscribe(
                EventFlow.ScenePhysicsPixelsPerMeterChanged.class, listener);

        String[] invalidValues = {"0", "-1", "NaN", "Infinity"};
        for (String invalid : invalidValues) {
            ppm.setText(invalid);
            ppm.commit();
            Assert.assertEquals(100f, meta.pixelsPerMeter, 0f);
            Assert.assertFalse(previewDirty[0]);
        }

        ppm.setText("64");
        ppm.commit();

        Assert.assertEquals(64f, meta.pixelsPerMeter, 0f);
        Assert.assertTrue(previewDirty[0]);
        Assert.assertEquals(0, eventCount[0]);
        EventFlow.i().flush();
        Assert.assertEquals(1, eventCount[0]);
        Assert.assertEquals(64f, publishedPpm[0], 0f);
        EventFlow.i().unsubscribe(
                EventFlow.ScenePhysicsPixelsPerMeterChanged.class, listener);
        String eventSource = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/event/EventFlow.java"),
                StandardCharsets.UTF_8);
        String propertiesSource = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/property/SceneProperties.java"),
                StandardCharsets.UTF_8);
        Assert.assertTrue(eventSource.contains("ScenePhysicsPixelsPerMeterChanged"));
        Assert.assertTrue(propertiesSource.contains("ScenePhysicsPixelsPerMeterChanged"));
        world.dispose();
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == Boolean.TYPE) return false;
        if (type == Integer.TYPE) return 0;
        if (type == Float.TYPE) return 0f;
        return null;
    }

    private static int layer(
            World world, HistoryManager history, int index, int type) {
        int entityId = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entityId);
        layer.layerIndex = index;
        layer.type = type;
        world.getMapper(LayerMetaComponent.class).create(entityId);
        history.historyIds().ensureForEntity(entityId);
        return entityId;
    }

    private static int body(
            World world,
            HistoryManager history,
            PhysicsService physics,
            float x) {
        int entityId = world.create();
        TransformComponent transform =
                world.getMapper(TransformComponent.class).create(entityId);
        transform.x = x;
        history.historyIds().ensureForEntity(entityId);
        physics.ensurePhysics(entityId);
        return entityId;
    }

    private static final class CounterCommand implements Command {
        @Override
        public String label() {
            return "Seed";
        }

        @Override
        public void redo() {
        }

        @Override
        public void undo() {
        }
    }
}
