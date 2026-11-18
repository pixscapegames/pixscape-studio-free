package games.pixscape.studio.service.tiled;

import com.artemis.*;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.*;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.*;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.DeleteGameObjectHierarchyCommand;
import games.pixscape.studio.service.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Exercises the same gesture adapter called by PickingSystem, without a Physics system. */
public class TiledObjectEditorTest {
    private World world;
    private IdentityRegistry identities;
    private HistoryManager history;
    private SelectionService selection;
    private TiledObjectAuthoringService authoring;
    private TiledObjectEditor editor;
    private TiledToolService tools;
    private int map, layer;

    @Before public void setup() {
        world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(64),
                new GameObjectHierarchySystem(), new UpdateWorldGeometrySystem()).build());
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Shapes");
        ProjectConfig.setInstance(config);
        identities = new IdentityRegistry();
        identities.bind(world, config.getCurrentSceneMeta());
        history = new HistoryManager(100);
        int host = world.create();
        world.getMapper(LayerComponent.class).create(host).layerIndex = 0;
        world.getMapper(LayerMetaComponent.class).create(host).name = "Objects";
        world.getMapper(VisibilityComponent.class).create(host).visible = true;
        map = world.create();
        world.getMapper(GameObjectComponent.class).create(map);
        world.getMapper(TransformComponent.class).create(map).refreshCaches();
        world.getMapper(EntityIndexComponent.class).create(map).layerIndex = 0;
        TiledMapComponent grid = world.getMapper(TiledMapComponent.class).create(map);
        grid.projection = TiledProjection.ORTHO;
        grid.tileWidth = 32;
        grid.tileHeight = 16;
        grid.width = grid.height = 20;
        identities.ensureStableId(map);
        identities.indexEntityImmediately(map);
        world.process();
        LayerService layers = new LayerService(world, null, history.historyIds(), identities);
        StudioEditingModeService modes = new StudioEditingModeService();
        modes.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        selection = new SelectionService(world, layers, modes);
        selection.setActivelayerId(host);
        tools = new TiledToolService();
        authoring = new TiledObjectAuthoringService(world, history);
        editor = new TiledObjectEditor(world, history, selection, layers, modes, tools);
        layer = authoring.createObjectLayer(map);
        world.process();
        selection.selectOnly(layer, SelectionService.SelectionSource.TREE);
        assertTrue(editor.canCreate());
    }

    @After public void teardown() {
        identities.bind(null, null);
        world.dispose();
        ProjectConfig.setInstance(null);
    }

    private void input(float x, float y, boolean press, boolean down, boolean release, boolean escape) {
        editor.process(x, y, 1, press, down, release, escape, false, false);
        world.process();
    }
    private void click(float x, float y) {
        input(x, y, true, true, false, false);
        input(x, y, false, false, true, false);
    }
    private float[] vertices(int e) { return world.getMapper(PolygonComponent.class).get(e).vertices; }

    @Test public void rectanglePreviewCancelResizeAndRepeatedHistory() {
        int cursor = history.getCursor();
        tools.setMode(TiledToolService.Mode.OBJECT_RECTANGLE);
        input(10, 20, true, true, false, false);
        input(110, 80, false, true, false, false);
        assertEquals(cursor, history.getCursor());
        input(110, 80, false, true, false, true);
        assertEquals(cursor, history.getCursor());
        tools.setMode(TiledToolService.Mode.OBJECT_RECTANGLE);
        input(10, 20, true, true, false, false);
        input(110, 80, false, false, true, false);
        int e = selection.getFirstSelectedEntityId();
        int stable = identities.getStableId(e);
        assertEquals(cursor + 1, history.getCursor());
        assertFalse(world.getMapper(PolygonComponent.class).has(e));
        assertFalse(world.getMapper(PhysicsBodyComponent.class).has(e));
        assertFalse(world.getMapper(PhysicsShapesComponent.class).has(e));
        assertEquals(100, TiledObjectGeometry.capture(world, e).width(), 0);
        input(110, 80, true, true, false, false);
        input(150, 100, false, true, false, false);
        assertEquals(100, TiledObjectGeometry.capture(world, e).width(), 0);
        input(150, 100, false, false, true, false);
        assertEquals(cursor + 2, history.getCursor());
        assertEquals(140, TiledObjectGeometry.capture(world, e).width(), .001f);
        for (int i = 0; i < 3; i++) {
            history.undo(); world.process();
            assertEquals(100, TiledObjectGeometry.capture(world, e).width(), .001f);
            history.redo(); world.process();
            assertEquals(140, TiledObjectGeometry.capture(world, e).width(), .001f);
        }
        history.execute(new DeleteGameObjectHierarchyCommand(world, history.historyIds(),
                com.badlogic.gdx.utils.IntArray.with(e), null));
        world.process();
        for (int i = 0; i < 3; i++) {
            history.undo(); world.process();
            e = identities.findByStableId(stable);
            assertEquals(140, TiledObjectGeometry.capture(world, e).width(), .001f);
            assertEquals(layer, TiledObjectAuthoringService.parent(world, e));
            history.redo(); world.process();
            assertEquals(-1, identities.findByStableId(stable));
        }
    }

    @Test public void concaveCreationVertexEditingCancellationAndEdgeOperations() {
        tools.setMode(TiledToolService.Mode.OBJECT_POLYGON);
        float[] expected = {0, 0, 100, 0, 100, 100, 50, 40, 0, 100};
        int cursor = history.getCursor();
        for (int i = 0; i < expected.length; i += 2) { click(expected[i], expected[i + 1]); }
        assertEquals(cursor, history.getCursor());
        click(0, 0);
        int e = selection.getFirstSelectedEntityId();
        assertArrayEquals(expected, vertices(e), 0);
        assertEquals(cursor + 1, history.getCursor());
        input(50, 40, true, true, false, false);
        input(-20, -30, false, true, false, false);
        assertArrayEquals(expected, vertices(e), 0);
        input(-20, -30, false, true, false, true);
        assertArrayEquals(expected, vertices(e), 0);
        assertEquals(cursor + 1, history.getCursor());
        input(50, 40, true, true, false, false);
        input(-20, -30, false, false, true, false);
        float[] edited = expected.clone(); edited[6] = -20; edited[7] = -30;
        assertArrayEquals(edited, vertices(e), 0);
        assertEquals(cursor + 2, history.getCursor());
        assertEquals(-20, world.getMapper(AABBComponent.class).get(e).minX, .001f);
        assertEquals(-30, world.getMapper(AABBComponent.class).get(e).minY, .001f);
        editor.insertVertex(e, 0, 40, -20); world.process();
        assertEquals(12, vertices(e).length);
        editor.removeVertex(e, 1); world.process();
        assertArrayEquals(edited, vertices(e), 0);
        history.undo(); world.process();
        assertEquals(12, vertices(e).length);
        history.undo(); world.process();
        assertArrayEquals(edited, vertices(e), 0);
        history.undo(); world.process();
        assertArrayEquals(expected, vertices(e), 0);
    }

    @Test public void isometricRectangleKeepsAxesAndOppositeCornerAcrossNumericAndCanvasResize() {
        world.getMapper(TiledMapComponent.class).get(map).projection = TiledProjection.ISO;
        float[] corners = authoring.rectangleVertices(layer, 0, 0, 80, 100);
        int e = authoring.createRectangle(layer, corners);
        world.process(); selection.selectOnly(e);
        assertArrayEquals(corners, vertices(e), 0);
        TiledObjectGeometry.Snapshot before = TiledObjectGeometry.capture(world, e);
        for (int corner = 0; corner < 4; corner++) {
            int opposite = (corner + 2) % 4 * 2;
            TiledObjectGeometry.Snapshot resized = TiledObjectGeometry.dragCorner(before, corner,
                    corners[corner * 2] * 1.2f + 3, corners[corner * 2 + 1] * 1.2f + 7);
            float[] p = resized.vertices();
            assertEquals(corners[opposite], p[opposite], .001f);
            assertEquals(corners[opposite + 1], p[opposite + 1], .001f);
            assertEquals(0, (p[2] - p[0]) * (corners[3] - corners[1])
                    - (p[3] - p[1]) * (corners[2] - corners[0]), .01f);
            assertEquals(0, (p[6] - p[0]) * (corners[7] - corners[1])
                    - (p[7] - p[1]) * (corners[6] - corners[0]), .01f);
        }
        TiledObjectGeometry.Snapshot resized = TiledObjectGeometry.resize(before, 200, 80);
        editor.commit(e, before, resized); world.process();
        assertEquals(200, TiledObjectGeometry.edgeLength(TiledObjectGeometry.capture(world, e), true), .001f);
        assertEquals(80, TiledObjectGeometry.edgeLength(TiledObjectGeometry.capture(world, e), false), .001f);
        resized.vertices()[0] = 999; // Command ownership must be independent of caller arrays.
        history.undo(); world.process(); assertArrayEquals(corners, vertices(e), 0);
        history.redo(); world.process(); assertEquals(corners[0], vertices(e)[0], 0);
    }

    @Test public void moveSelectedObjectCommitsOnceEvenWithCoalescedReleaseAndPreservesLocalData() {
        for (TiledProjection projection : new TiledProjection[]{TiledProjection.ORTHO, TiledProjection.ISO}) {
            world.getMapper(TiledMapComponent.class).get(map).projection = projection;
            int e = authoring.createRectangle(layer, authoring.rectangleVertices(layer, 10, 20, 110, 150));
            world.process(); selection.selectOnly(e);
            var before = TiledObjectGeometry.capture(world, e);
            float[] vertices = TiledObjectGeometry.vertices(before);
            Vector2 center = TiledObjectGeometry.localToWorld(world, e,
                    (vertices[0] + vertices[4]) * .5f, (vertices[1] + vertices[5]) * .5f, new Vector2());
            int cursor = history.getCursor();
            input(center.x, center.y, true, true, false, false);
            input(center.x + 50, center.y - 30, false, false, true, false);
            assertEquals(cursor + 1, history.getCursor());
            assertArrayEquals(vertices, TiledObjectGeometry.vertices(TiledObjectGeometry.capture(world, e)), 0);
            assertEquals(projection == TiledProjection.ORTHO ? 60 : 50,
                    world.getMapper(TransformComponent.class).get(e).x, .001f);
            history.undo(); world.process();
            assertTrue(before.sameAs(TiledObjectGeometry.capture(world, e)));
            history.redo(); world.process();
            assertArrayEquals(vertices, TiledObjectGeometry.vertices(TiledObjectGeometry.capture(world, e)), 0);
        }
    }

    @Test public void clickAndReturnToStartAreNoopsAndHistoryChangeCancelsPreview() {
        int e = authoring.createPolygon(layer, new float[]{0,0,100,0,100,100,0,100});
        world.process(); selection.selectOnly(e);
        int cursor = history.getCursor();
        click(0, 0);
        assertEquals(cursor, history.getCursor());
        input(0, 0, true, true, false, false);
        input(-40, -30, false, true, false, false);
        input(0, 0, false, false, true, false);
        assertEquals(cursor, history.getCursor());
        input(0, 0, true, true, false, false);
        input(-40, -30, false, true, false, false);
        editor.insertVertex(e, 0, 50, 0); world.process();
        input(-40, -30, false, false, true, false);
        assertFalse(editor.active());
        assertEquals(cursor + 1, history.getCursor());
        assertArrayEquals(new float[]{0,0,50,0,100,0,100,100,0,100}, vertices(e), 0);
    }

    @Test public void incompatibleLayerRefusesCreationAndSelectionChangeCancelsGesture() {
        tools.setMode(TiledToolService.Mode.OBJECT_POLYGON);
        click(0, 0); click(100, 0);
        int cursor = history.getCursor();
        selection.selectOnly(map);
        input(100, 100, false, false, false, false);
        assertFalse(editor.active()); assertFalse(editor.canCreate());
        assertEquals(cursor, history.getCursor());
        try { authoring.createPolygon(map, new float[]{0,0,100,0,0,100}); fail(); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("object layer")); }
    }

    @Test public void parentTransformsAndOriginsRoundTripWithoutChangingContourOrder() {
        TransformComponent parent = world.getMapper(TransformComponent.class).get(map);
        parent.x = 300; parent.y = -100;
        parent.refreshCaches();
        float[] contour = {-30, 10, 20, 10, 20, -40, 0, -10, -30, -40};
        int e = authoring.createPolygon(layer, contour); world.process();
        TransformComponent t = world.getMapper(TransformComponent.class).get(e);
        t.x = 25; t.y = 16; t.originX = -3; t.originY = 7; t.rotationRad = -.2f;
        t.scaleX = 2; t.scaleY = .7f;
        t.refreshCaches(); world.getSystem(DirtyTrackerSystem.class).geometry(e, games.pixscape.runtime.render.GeometryDirty.ALL);
        world.process(); selection.selectOnly(e);
        Vector2 p = TiledObjectGeometry.localToWorld(world, e, contour[0], contour[1], new Vector2());
        Vector2 local = TiledObjectGeometry.worldToLocal(world, e, p.x, p.y, new Vector2());
        assertEquals(contour[0], local.x, .001f); assertEquals(contour[1], local.y, .001f);
        input(p.x, p.y, true, true, false, false);
        TiledObjectGeometry.localToWorld(world, e, -50, 20, p);
        input(p.x, p.y, false, false, true, false);
        float[] edited = contour.clone(); edited[0] = -50; edited[1] = 20;
        assertArrayEquals(edited, vertices(e), .001f);
        assertArrayEquals(contour, new float[]{-30,10,20,10,20,-40,0,-10,-30,-40}, 0);
    }
}
