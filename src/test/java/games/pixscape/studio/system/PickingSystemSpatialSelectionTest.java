package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.studio.service.spatial.SpatialBlockProjection;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.input.InputState;
import games.pixscape.studio.service.CoordSpaces;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Exercises real picking and queued selection events without a native GL window.
 */
public class PickingSystemSpatialSelectionTest {
    @BeforeClass
    public static void setup() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void teardown() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void allNineHandlesKeepSpatialSelectionThroughoutRepeatedDrags() throws Exception {
        for (int handle = 0; handle < 9; handle++) {
            try (Fixture f = new Fixture()) {
                for (int repeat = 0; repeat < 2; repeat++) {
                    Vector2 start = f.handle(handle);
                    assertEquals(handle, f.pointer(start, true, true, false));
                    assertEquals(List.of("entity", "wall:1"), f.events);
                    f.events.clear();
                    Vector2 end = new Vector2(start).add(12, 12);
                    f.pointer(end, false, true, false);
                    assertTrue("handle " + handle, f.selection.wallEditSession().isActive());
                    assertEquals(1, f.selection.getSelectedBlockId());
                    assertTrue("Drag must not replace the inspector target: " + f.events, f.events.isEmpty());
                    f.pointer(end, false, false, true);
                    assertEquals(1, f.selection.getSelectedBlockId());
                    assertFalse("Release must not select the scene", f.events.contains("entity"));
                    assertEquals("handle " + handle + ": " + f.selection.wallEditSession().commitMessage(), 1, f.history.getCursor());
                    f.history.undo();
                    assertEquals(1, f.selection.getSelectedBlockId());
                    f.history.redo();
                    assertEquals(1, f.selection.getSelectedBlockId());
                    f.history.undo();
                    EventFlow.i().flush();
                    f.events.clear();
                }
            }
        }
    }

    @Test
    public void anotherVolumeAndBackgroundRemainSelectable() throws Exception {
        try (Fixture f = new Fixture()) {
            // The center of the second volume's base is away from the selected handles.
            f.pointer(new Vector2(704, 192), true, true, false);
            assertEquals(2, f.selection.getSelectedBlockId());
            assertEquals(List.of("entity", "wall:2"), f.events);
            f.pointer(new Vector2(704, 192), false, false, true);
            f.events.clear();
            f.pointer(new Vector2(-200, -200), true, true, false);
            assertFalse(f.selection.hasSelectedBlock());
            assertEquals(List.of("wall:-1"), f.events);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Input originalInput = Gdx.input;
        final EventFlow.SubscriptionScope scope = EventFlow.i().newSubscriptionScope(() -> true);
        final HistoryManager history = new HistoryManager(16);
        final SpatialBlockSelectionService selection;
        final PickingSystem picking;
        final World world;
        final int map;
        final SpatialBlocksComponent blocks;
        final TiledMapLayerData data;
        final List<String> events = new ArrayList<>();
        final Method process;
        int mouseX, mouseY;

        Fixture() throws Exception {
            EventFlow.i().discardPending();
            SpatialBlockSelectionService[] selected = new SpatialBlockSelectionService[1];
            PickingSystem[] system = new PickingSystem[1];
            GizmoSystem[] gizmos = new GizmoSystem[1];
            OrthographicCamera camera = new OrthographicCamera(1024, 768);
            ScreenViewport viewport = new ScreenViewport(camera);
            viewport.setScreenBounds(0, 0, 1024, 768);
            camera.update();
            scope.run(() -> {
                selected[0] = new SpatialBlockSelectionService();
                gizmos[0] = new GizmoSystem(null, null, null, null, null, null, null, null, null);
                system[0] = new PickingSystem(camera, new CoordSpaces(camera, viewport, null, null),
                        new InputState(), history, history.historyIds(), null, new TiledMapRenderState(),
                        new PhysicsSelectionService(), null, selected[0], null, null);
                EventFlow.i().subscribe(EventFlow.SelectionChanged.class, e -> events.add("entity"));
                EventFlow.i().subscribe(EventFlow.SpatialBlockSelectionChanged.class,
                        e -> events.add("wall:" + e.blockId()));
            });
            selection = selected[0];
            picking = system[0];
            GizmoSystem gizmo = gizmos[0];
            LightIconOverlaySystem lights = new LightIconOverlaySystem(null, camera);
            world = new World(new WorldConfigurationBuilder().with(
                    new games.pixscape.runtime.system.DirtyTrackerSystem(32),
                    new games.pixscape.runtime.system.GameObjectHierarchySystem(), lights, gizmo, picking).build());
            gizmo.setEnabled(false);
            lights.setEnabled(false);
            picking.setEnabled(false);
            picking.setSelectionService(new SelectionService(world, null));
            map = world.create();
            data = new TiledMapLayerData(16, 16, 64, 64, 4, TiledProjection.ORTHO);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) data.setTile(x, y, 1);
            world.getMapper(TiledLayerComponent.class).create(map).data = data;
            blocks = world.getMapper(SpatialBlocksComponent.class).create(map);
            blocks.blocks.add(wall(1, 2));
            blocks.blocks.add(wall(2, 10));
            blocks.nextSpatialBlockId = 3;
            world.process();
            history.historyIds().ensureForEntity(map);
            selection.selectBlock(map, 1);
            EventFlow.i().flush();
            events.clear();
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> mouseX;
                        case "getY" -> mouseY;
                        default -> method.invoke(originalInput, args);
                    });
            process = PickingSystem.class.getDeclaredMethod("processSpatialBlockMode",
                    float.class, float.class, boolean.class, boolean.class, boolean.class);
            process.setAccessible(true);
        }

        int pointer(Vector2 point, boolean pressed, boolean down, boolean released) throws Exception {
            mouseX = Math.round(point.x);
            mouseY = Math.round(point.y);
            int handle = (Integer) process.invoke(picking, point.x, point.y, pressed, down, released);
            EventFlow.i().flush();
            return handle;
        }

        Vector2 handle(int handle) {
            float[] vertices = new float[8];
            if (handle == 8) {
                SpatialBlockProjection.projectTopFootprint(data, blocks.blocks.first(), vertices);
                return new Vector2((vertices[0] + vertices[4]) / 2, (vertices[1] + vertices[5]) / 2);
            }
            SpatialBlockProjection.projectBaseFootprint(data, blocks.blocks.first(), vertices);
            if (handle >= 4) return new Vector2(vertices[(handle - 4) * 2], vertices[(handle - 4) * 2 + 1]);
            int next = (handle + 1) % 4;
            return new Vector2((vertices[handle * 2] + vertices[next * 2]) / 2,
                    (vertices[handle * 2 + 1] + vertices[next * 2 + 1]) / 2);
        }

        @Override
        public void close() {
            scope.close();
            selection.dispose();
            world.dispose();
            Gdx.input = originalInput;
            EventFlow.i().discardPending();
        }

        private static SpatialBlockData wall(int id, int x) {
            SpatialBlockData wall = new SpatialBlockData();
            wall.id = id;
            wall.structureId = id;
            wall.x = x;
            wall.y = 2;
            wall.width = 2;
            wall.depth = 2;
            wall.height = 80;
            wall.beginAuthoredLinkedTileRefs();
            for (int y = 1; y < 5; y++) for (int gx = x - 1; gx < x + 3; gx++) wall.addLinkedTileRef(gx, y, 1);
            return wall;
        }
    }
}
