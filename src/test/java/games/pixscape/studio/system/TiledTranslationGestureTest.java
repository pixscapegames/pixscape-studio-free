package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.TiledRenderState;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.input.InputState;
import games.pixscape.studio.service.CoordSpaces;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

/** Real Stage dispatch and PickingSystem; injected input, not a native mouse recipe. */
public class TiledTranslationGestureTest {
    @BeforeClass
    public static void setup() {
        VisUiTestBootstrap.loadSkin();
    }
    @AfterClass
    public static void teardown() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void coalescedPressMotionReleaseCapturesMarkerAndCommitsOnce() {
        for (TiledProjection projection : TiledProjection.values()) {
            for (boolean layer : new boolean[]{false, true}) {
                for (float zoom : new float[]{0.5f, 2f}) {
                    try (Fixture f = new Fixture(projection, layer, zoom)) {
                        Vector2 press = f.screen();
                        // GLFW can expose the latest pointer before dispatching queued press events.
                        f.mouseX = (int) press.x + 40;
                        f.mouseY = (int) press.y - 20;
                        f.stage.touchDown((int) press.x, (int) press.y, 0, Input.Buttons.LEFT);
                        f.stage.touchDragged(f.mouseX, f.mouseY, 0);
                        f.stage.touchUp(f.mouseX, f.mouseY, 0, Input.Buttons.LEFT);
                        f.world.process();
                        assertTrue(projection + " layer=" + layer + " zoom=" + zoom + " captured=" + f.captured,
                                layer ? f.selection.getTiledMapEditingTargetEntityId() == f.target
                                        : f.selection.isOnlySelected(f.target));
                        f.assertPose(40 * zoom, 20 * zoom);
                        assertEquals(1, f.history.getCursor());
                        for (int repeat = 0; repeat < 3; repeat++) {
                            f.history.undo();
                            f.world.process();
                            f.assertPose(0, 0);
                            f.history.redo();
                            f.world.process();
                            f.assertPose(40 * zoom, 20 * zoom);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void stationaryClickAndEscapeDoNotCreateHistory() {
        try (Fixture f = new Fixture(TiledProjection.ISO, false, 1)) {
            Vector2 press = f.screen();
            f.mouseX = (int) press.x;
            f.mouseY = (int) press.y;
            f.stage.touchDown(f.mouseX, f.mouseY, 0, Input.Buttons.LEFT);
            f.stage.touchUp(f.mouseX, f.mouseY, 0, Input.Buttons.LEFT);
            f.world.process();
            f.assertPose(0, 0);
            assertEquals(0, f.history.getCursor());
            f.stage.touchDown(f.mouseX, f.mouseY, 0, Input.Buttons.LEFT);
            f.world.process();
            f.mouseX += 40;
            f.stage.touchDragged(f.mouseX, f.mouseY, 0);
            f.world.process();
            f.assertPose(40, 0);
            f.escape = true;
            f.world.process();
            f.escape = false;
            f.stage.touchUp(f.mouseX, f.mouseY, 0, Input.Buttons.LEFT);
            f.world.process();
            f.assertPose(0, 0);
            assertEquals(0, f.history.getCursor());
        }
    }

    @Test
    public void stageTouchFocusCancellationRestoresMapAndTileLayer() {
        for (boolean layer : new boolean[]{false, true}) {
            try (Fixture f = new Fixture(TiledProjection.ORTHO, layer, 1)) {
                Vector2 press = f.screen();
                f.mouseX = (int) press.x;
                f.mouseY = (int) press.y;
                f.stage.touchDown(f.mouseX, f.mouseY, 0, Input.Buttons.LEFT);
                f.world.process();
                f.mouseX += 24;
                f.stage.touchDragged(f.mouseX, f.mouseY, 0);
                f.world.process();
                f.assertPose(24, 0);
                f.stage.cancelTouchFocus();
                f.world.process();
                f.assertPose(0, 0);
                assertFalse(f.picking.isTiledTranslationCaptured());
                assertEquals(0, f.history.getCursor());
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Input previousInput = Gdx.input;
        final Graphics previousGraphics = Gdx.graphics;
        final EventFlow.SubscriptionScope scope = EventFlow.i().newSubscriptionScope(() -> true);
        final HistoryManager history = new HistoryManager(16);
        final IdentityRegistry ids = new IdentityRegistry();
        final InputState input = new InputState();
        final World world;
        final PickingSystem picking;
        final SelectionService selection;
        final Stage stage;
        final OrthographicCamera camera = new OrthographicCamera();
        final int map, tiles, target, child;
        int mouseX, mouseY;
        boolean escape, captured;

        Fixture(TiledProjection projection, boolean layer, float zoom) {
            EventFlow.i().discardPending();
            Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class}, (proxy, method, args) -> {
                if (method.getName().equals("getWidth") || method.getName().equals("getBackBufferWidth")) {
                    return 640;
                }
                if (method.getName().equals("getHeight") || method.getName().equals("getBackBufferHeight")) {
                    return 480;
                }
                return method.invoke(previousGraphics, args);
            });
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> {
                if (method.getName().equals("getX")) {
                    return mouseX;
                }
                if (method.getName().equals("getY")) {
                    return mouseY;
                }
                if (method.getName().equals("isKeyJustPressed")) {
                    return escape && (int) args[0] == Input.Keys.ESCAPE;
                }
                if (method.getName().equals("isKeyPressed")) {
                    return false;
                }
                return method.invoke(previousInput, args);
            });
            Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class}, (proxy, method, args) -> null);
            stage = new Stage(new ScreenViewport(camera), batch);
            stage.getViewport().update(640, 480, true);
            camera.position.set(23, -17, 0);
            camera.zoom = zoom;
            camera.update();
            CoordSpaces coordinates = new CoordSpaces(camera, stage.getViewport(), stage, null);
            PhysicsSelectionService physics = new PhysicsSelectionService();
            PickingSystem[] pickingHolder = new PickingSystem[1];
            GizmoSystem[] gizmoHolder = new GizmoSystem[1];
            scope.run(() -> {
                SpatialBlockSelectionService spatial = new SpatialBlockSelectionService();
                pickingHolder[0] = new PickingSystem(camera, coordinates, input, history, history.historyIds(), null,
                        new TiledRenderState(), physics, null, spatial, new SpatialTileSelectionService(), null);
                gizmoHolder[0] = new GizmoSystem(null, null, null, null, null, null, null, null, null);
            });
            picking = pickingHolder[0];
            GizmoSystem gizmo = gizmoHolder[0];
            LightIconOverlaySystem lights = new LightIconOverlaySystem(null, camera);
            world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(32),
                    new GameObjectHierarchySystem(), lights, gizmo, picking).build());
            lights.setEnabled(false);
            gizmo.setEnabled(false);
            ids.bind(world, new SceneMeta());
            LayerService layers = new LayerService(world, null, history.historyIds(), ids);
            StudioEditingModeService modes = new StudioEditingModeService();
            modes.activateSceneDocument(StudioEditingMode.NORMAL, 0);
            selection = new SelectionService(world, layers, modes);
            picking.setSelectionService(selection);
            picking.setLayerService(layers);
            int sceneLayer = world.create();
            world.getMapper(LayerComponent.class).create(sceneLayer).layerIndex = 0;
            world.getMapper(LayerMetaComponent.class).create(sceneLayer).name = "Test";
            map = node();
            world.getMapper(GameObjectComponent.class).create(map);
            var mapData = world.getMapper(TiledMapComponent.class).create(map);
            mapData.projection = projection;
            mapData.tileWidth = 32;
            mapData.tileHeight = 16;
            mapData.width = mapData.height = 8;
            tiles = node();
            world.getMapper(GameObjectMemberComponent.class).create(tiles).parentStableId = ids.getStableId(map);
            world.getMapper(TiledMapLayerComponent.class).create(tiles).kind = TiledMapLayerComponent.Kind.TILE;
            var grid = world.getMapper(TiledLayerComponent.class).create(tiles);
            grid.projection = projection;
            grid.tileWidth = 32;
            grid.tileHeight = 16;
            grid.mapWidthCells = grid.mapHeightCells = 8;
            grid.chunkSize = 4;
            grid.data = grid.createLayerData();
            int objects = node();
            world.getMapper(GameObjectComponent.class).create(objects);
            world.getMapper(TiledMapLayerComponent.class).create(objects).kind = TiledMapLayerComponent.Kind.OBJECT;
            world.getMapper(GameObjectMemberComponent.class).create(objects).parentStableId = ids.getStableId(map);
            child = node();
            world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = ids.getStableId(objects);
            world.getMapper(TransformComponent.class).get(child).x = 12;
            world.getMapper(TransformComponent.class).get(child).y = 7;
            target = layer ? tiles : map;
            world.process();
            if (layer) {
                selection.setTiledMapEditingTarget(target, SelectionService.SelectionSource.TREE);
                assertTrue(selection.isTiledMapEditingTargetActive());
            } else {
                selection.selectOnly(target);
            }
            EventFlow.i().discardPending();
            stage.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    input.touchDown(mouseX, mouseY, pointer, button);
                    captured = picking.tiledPointerDown(event.getStageX(), event.getStageY());
                    return true;
                }
                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    input.touchDragged(mouseX, mouseY, pointer);
                }
                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    if (event.isTouchFocusCancel()) {
                        picking.cancelTiledTranslationGesture();
                        input.clearAll();
                        return;
                    }
                    input.touchUp(mouseX, mouseY, pointer, button);
                }
            });
        }

        int node() {
            int e = world.create();
            world.getMapper(TransformComponent.class).create(e);
            world.getMapper(EntityIndexComponent.class).create(e);
            ids.ensureStableId(e);
            return e;
        }

        Vector2 screen() {
            return stage.stageToScreenCoordinates(new Vector2(0, 0));
        }

        void assertPose(float x, float y) {
            world.process();
            TransformComponent t = world.getMapper(TransformComponent.class).get(target);
            assertEquals(x, t.x, .001f);
            assertEquals(y, t.y, .001f);
            assertEquals(0, t.rotationRad, 0);
            assertEquals(1, t.scaleX, 0);
            assertEquals(1, t.scaleY, 0);
            assertEquals(0, t.originX, 0);
            assertEquals(0, t.originY, 0);
            var poses = world.getSystem(GameObjectHierarchySystem.class).worldTransforms();
            assertEquals(x, poses.x[tiles], .001f);
            assertEquals(y, poses.y[tiles], .001f);
            assertEquals(12 + (target == map ? x : 0), poses.x[child], .001f);
            assertEquals(7 + (target == map ? y : 0), poses.y[child], .001f);
        }

        @Override
        public void close() {
            stage.dispose();
            world.dispose();
            scope.close();
            Gdx.input = previousInput;
            Gdx.graphics = previousGraphics;
            EventFlow.i().discardPending();
        }
    }
}
