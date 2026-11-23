package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.system.SpatialRenderOrderSystem;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.commands.AddPhysicsBodyCommand;
import games.pixscape.studio.history.commands.EditSpatialFootprintCommand;
import games.pixscape.studio.history.commands.FixtureCommandSupport;
import games.pixscape.studio.history.commands.GameObjectHierarchyCommandSupport;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.input.InputManipulationContext;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.SpatialActorShapeSupport;
import games.pixscape.studio.system.GizmoSystem;
import games.pixscape.studio.system.PickingSystem;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.tree.EntityNode;
import games.pixscape.studio.ui.widget.ActorSpatialRectangleCanvasEditor;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

/** Items, Scene2D capture and render/frame regression coverage; opt in with PIXSCAPE_DOCUMENT_GL_TEST=1. */
public class CursorOverlaySelectionGlTest {
    private StudioApplicationAdapter app;
    private SceneEditorContext ctx;
    private WorldCanvas canvas;
    private PickingSystem picking;
    private GizmoSystem gizmo;
    private Object tree;
    private Object overlay;
    private int mouseX = 10, mouseY = 150;

    @Test
    public void physicsResizeUsesGeometryPermissionAndKeepsTransformScaleLocked() throws Exception {
        withFixture(this::matrix);
    }

    @Test
    public void itemsSelectionRoutesInspectorAndOverlaysWithoutChangingSceneData() throws Exception {
        withFixture(() -> {
            int actor = spatialAnimation();
            int child = canvas.getEditorOps().createStandaloneSpriteInGameObject(actor, "audit.png", "Child");
            configurePhysics(actor, true);
            transitions(actor, child);
        });
    }

    @Test
    public void footprintGesturesOnlyEditTheirOwnerAndCancelWhenInspectionChanges() throws Exception {
        withFixture(() -> {
            int actor = spatialAnimation();
            int child = canvas.getEditorOps().createAnimationSpriteInGameObject(actor, "audit-clips", "Inherited");
            configurePhysics(actor, true);
            footprintGestures(actor, child);
            inheritedFootprint(child, actor);
            int group = canvas.getEditorOps().createGameObject(0, 0);
            spatial(group);
            configurePhysics(group, false);
            assertFalse(ctx.world().getMapper(SpatialRepresentationComponent.class).has(group));
            footprintGestures(group, actor);
        });
    }

    @Test
    public void capturedPhysicsPreviewsFollowTargetModeAndDocumentLifetime() throws Exception {
        withFixture(() -> {
            int actor = spatialAnimation();
            int other = animation("Other actor", 160, 0);
            configurePhysics(other, false);
            cancellation(actor, other);
            documentSwitch(actor);
        });
    }

    @Test
    public void hoverCleanupRestoresOnlyTheCursorOwnedByPicking() throws Exception {
        withFixture(() -> cursorOwnership(spatialAnimation()));
    }

    @Test
    public void spatialHandleCursorDoesNotUseTheActorScaleLock() throws Exception {
        withFixture(() -> {
            int actor = spatialAnimation();
            canvas.getEditorOps().createStandaloneSpriteInGameObject(actor, "audit.png", "Child");
            configurePhysics(actor, true);
            spatialCursorIgnoresObjectScaleLock(actor);
        });
    }

    @Test
    public void fixturesWithoutAuthoredGeometryCannotResize() throws Exception {
        withFixture(() -> linkedGeometryProtected(spatialAnimation()));
    }

    private int spatialAnimation() {
        int actor = animation("Spatial animation", 0, 0);
        spatial(actor);
        return actor;
    }

    @FunctionalInterface
    private interface Check {
        void run() throws Exception;
    }

    private void withFixture(Check check) throws Exception {
        withStudio(900, (studio, root) -> {
            Input original = Gdx.input;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> mouseX;
                        case "getY" -> mouseY;
                        case "isKeyPressed", "isKeyJustPressed", "isButtonPressed", "isButtonJustPressed" -> false;
                        default -> method.invoke(original, args);
                    });
            try {
                setup(studio, root);
                check.run();
                awaitAtlas(studio);
            } finally {
                Gdx.input = original;
            }
        });
    }

    private void setup(StudioApplicationAdapter studio, FileHandle root) throws Exception {
        app = studio;
        var file = project(studio, root);
        addImage(root, "audit", 160, 180);
        var db = AssetMetaDatabase.load(root.child("assets.json"));
        var meta = (AnimationAssetMeta) db.registerIfAbsent(AssetType.ANIMATION,
                "audit-clips", "orig/animations/audit-clips", AssetMeta.AssetScope.USER);
        meta.frameCount = 2;
        meta.fps = 8;
        meta.currentClip = "idle";
        meta.clips.put("idle", new AnimationClipMeta(0, 1));
        for (int n = 0; n < 2; n++) {
            root.child("orig/images/audit.png").copyTo(root.child("orig/animations/audit-clips/frame" + n + ".png"));
        }
        db.save(root.child("assets.json"));
        open(studio, file);
        ((TopMenuBar) field(studio, "topMenuBar")).beginProject();
        canvas = studio.getCanvas();
        ctx = canvas.getAttachedSceneContext();
        ctx.sceneMeta().physicsEnabled = true;
        ctx.world().getMapper(LayerComponent.class).get(ctx.layerService().getLayerEntity(0)).spatialEnabled = true;
        picking = ctx.world().getSystem(PickingSystem.class);
        gizmo = ctx.world().getSystem(GizmoSystem.class);
        tree = field(app.getItemTreePanel(), "tree");
        overlay = field(canvas, "actorShadowRectangleEditor");
        canvas.focusCameraAt(0, 0);
        refresh();
    }

    private int animation(String label, float x, float y) {
        int e = canvas.getEditorOps().createAnimationSprite("audit-clips", x, y, label);
        assertTrue(e >= 0);
        ctx.world().process();
        return e;
    }

    private void spatial(int e) {
        ctx.historyManager().execute(new ToggleSpatialActorCommand(ctx.world(), ctx.historyManager().historyIds(),
                e, true, true, new SpatialFootprintComponent()));
        ctx.world().process();
    }

    private PhysicsShapeData configurePhysics(int e, boolean circle) {
        if (!ctx.world().getMapper(PhysicsBodyComponent.class).has(e)) {
            ctx.historyManager().execute(new AddPhysicsBodyCommand(ctx.world(), ctx.historyManager().historyIds(),
                    canvas.getPhysicsService(), e, PhysicsBodyComponent.STATIC, true));
        }
        var candidate = FixtureCommandSupport.copyFixtures(ctx.world(), e);
        var g = candidate.first().geometry;
        g.shapeType = circle ? PhysicsGeometryData.SHAPE_CIRCLE : PhysicsGeometryData.SHAPE_BOX;
        g.radius = canvas.getPhysicsService().pxToM(35);
        g.halfWidth = canvas.getPhysicsService().pxToM(35);
        g.halfHeight = canvas.getPhysicsService().pxToM(25);
        g.offsetX = 0;
        g.offsetY = 0;
        FixtureCommandSupport.prepareAndPublish(ctx.world(), e, candidate);
        FixtureCommandSupport.markDirty(ctx.world(), e);
        ctx.world().process();
        return shape(e);
    }

    private PhysicsShapeData shape(int e) {
        return ctx.world().getMapper(PhysicsShapesComponent.class).get(e).shapes.first();
    }

    private void matrix() throws Exception {
        int plain = animation("Fresh animation", 0, 0);
        assertFalse(ctx.world().getMapper(GameObjectComponent.class).has(plain));
        for (boolean hasSpatial : new boolean[]{false, true}) {
            if (hasSpatial) {
                spatial(plain);
            }
            assertFalse("Spatial alone must not promote the animation", ctx.world().getMapper(GameObjectComponent.class).has(plain));
            exercise(plain, "fresh-animation spatial=" + hasSpatial, false);
        }
        canvas.getEditorOps().createStandaloneSpriteInGameObject(plain, "audit.png", "Visual child");
        ctx.world().process();
        assertTrue(ctx.world().getMapper(GameObjectComponent.class).has(plain));
        assertEquals("ANIMATION", ctx.world().getMapper(EntityMetaComponent.class).get(plain).kind.name());
        exercise(plain, "animation-with-child spatial=true", true);
        ctx.historyManager().execute(new ToggleSpatialActorCommand(ctx.world(), ctx.historyManager().historyIds(), plain, false, true, null));
        ctx.world().process();
        exercise(plain, "animation-with-child spatial=false", true);
        spatial(plain);
        int child = canvas.getEditorOps().createAnimationSpriteInGameObject(plain, "audit-clips", "Inherited animation");
        ctx.world().process();
        assertEquals(plain, SpatialActorShapeSupport.owner(ctx.world(), child));
        exercise(child, "inherited-animation-leaf", false);
        canvas.getEditorOps().createStandaloneSpriteInGameObject(child, "audit.png", "Grandchild");
        ctx.world().process();
        exercise(child, "inherited-animation-with-child", true);
        int sprite = canvas.getEditorOps().createStandaloneSprite("audit.png", 0, 0);
        ctx.world().process();
        exercise(sprite, "plain-sprite", false);
        spatial(sprite);
        exercise(sprite, "spatial-sprite", false);
        int group = canvas.getEditorOps().createGameObject(0, 0);
        ctx.world().process();
        exercise(group, "actual-game-object", true);
    }


    private void exercise(int entity, String label, boolean locked) throws Exception {
        for (boolean circle : new boolean[]{true, false}) {
            var fixture = configurePhysics(entity, circle);
            selectNode(entity, "findBodyNode");
            canvas.getPhysicsSelectionService().setSelectedShape(entity, fixture.physicsShapeId);
            assertTrue(canvas.getPhysicsSelectionService().isPhysicsEditingActive());
            assertEquals(locked, GameObjectHierarchyCommandSupport.isPhysicsAncestorScaleLocked(ctx.world(), entity));
            var before = fixture.copy();
            String otherData = visualAndFootprint(entity);
            Vector2 start = physicsHandle(entity, circle);
            hover(start);
            assertEquals(label, "RESIZE", field(gizmo, "cursorKind").toString());
            if (label.equals("animation-with-child spatial=true")) {
                capture(circle ? "circle-resize-cursor" : "box-resize-cursor");
            }
            int history = ctx.historyManager().getCursor();
            press(start);
            assertEquals(true, field(picking, circle ? "resizingCircleActive" : "resizingBoxActive"));
            Vector2 end = start.cpy().add(20, -12);
            drag(end);
            var after = shape(entity).copy();
            assertFalse(label, before.contentEquals(after));
            assertEquals(history, ctx.historyManager().getCursor());
            release(end);
            if (label.equals("animation-with-child spatial=true")) {
                capture(circle ? "circle-resized" : "box-resized");
            }
            assertEquals(history + 1, ctx.historyManager().getCursor());
            assertEquals(false, field(picking, circle ? "resizingCircleActive" : "resizingBoxActive"));
            for (int cycle = 0; cycle < 2; cycle++) {
                ctx.historyManager().undo();
                assertTrue(before.contentEquals(shape(entity)));
                ctx.historyManager().redo();
                assertTrue(after.contentEquals(shape(entity)));
            }
            assertEquals(otherData, visualAndFootprint(entity));
            hover(new Vector2(10, 150));
            assertHoverCleared();
            selectNode(entity, "findNode");
            if (locked) {
                float[] corners = (float[]) invoke(picking, "computeOBBWorldCorners", new Class<?>[]{int.class}, entity);
                assertNotNull(corners);
                start = screen(new Vector2(corners[4], corners[5]));
                hover(start);
                assertEquals("FORBIDDEN", field(gizmo, "cursorKind").toString());
                String original = authored(entity);
                press(start);
                assertFalse(((InputManipulationContext) field(picking, "ctx")).isDragging());
                release(start);
                assertEquals(original, authored(entity));
            }
            System.out.println("PASS " + label + " " + (circle ? "Circle" : "Box") + " Stage/frame resize, Undo/Redo, scale lock");
        }
    }

    private String visualAndFootprint(int entity) {
        var json = new com.badlogic.gdx.utils.Json();
        int owner = SpatialActorShapeSupport.owner(ctx.world(), entity);
        return json.toJson(ctx.world().getMapper(TransformComponent.class).getSafe(entity, null))
                + json.toJson(ctx.world().getMapper(SpatialRepresentationComponent.class).getSafe(entity, null))
                + (owner < 0 ? "" : json.toJson(ctx.world().getMapper(SpatialFootprintComponent.class).get(owner)));
    }

    private Vector2 physicsHandle(int entity, boolean circle) throws Exception {
        var fixture = shape(entity);
        Vector2 worldPoint = new Vector2();
        if (circle) {
            canvas.getPhysicsService().computeShapeCenterWU(entity, fixture, worldPoint);
            invoke(picking, "remapPhysicsPoint", new Class<?>[]{int.class, Vector2.class}, entity, worldPoint);
            invoke(picking, "applyDisplayOffset", new Class<?>[]{int.class, Vector2.class}, entity, worldPoint);
            worldPoint.x += canvas.getPhysicsService().computeShapeRadiusWU(fixture);
        } else {
            float[] corners = new float[8];
            invoke(picking, "copyFixtureBoxCornersWorld", new Class<?>[]{int.class, PhysicsShapeData.class, float[].class}, entity, fixture, corners);
            worldPoint.set(corners[4], corners[5]);
        }
        return screen(worldPoint);
    }

    private Vector2 screen(Vector2 worldPoint) {
        return canvas.getGridStage().stageToScreenCoordinates(worldPoint);
    }

    private void pointer(Vector2 point) {
        mouseX = Math.round(point.x);
        mouseY = Math.round(point.y);
    }

    private void hover(Vector2 point) {
        pointer(point);
        canvas.getGridStage().mouseMoved(mouseX, mouseY);
        refresh();
    }

    private void press(Vector2 point) {
        pointer(point);
        assertTrue(canvas.getGridStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT));
        refresh();
    }

    private void drag(Vector2 point) {
        pointer(point);
        canvas.getGridStage().touchDragged(mouseX, mouseY, 0);
        refresh();
    }

    private void release(Vector2 point) {
        pointer(point);
        canvas.getGridStage().touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT);
        refresh();
    }

    private void assertHoverCleared() throws Exception {
        assertEquals("NONE", field(gizmo, "cursorKind").toString());
        assertEquals(false, field(picking, "osCursorHidden"));
        assertEquals(InputManipulationContext.Handle.NONE, ((InputManipulationContext) field(picking, "ctx")).hoveredHandle());
        assertEquals(-1, ctx.physicsSelectionService().getHoveredBodyEid());
    }

    private void selectNode(int entity, String method) throws Exception {
        pointer(new Vector2(10, 150));
        refresh();
        EntityNode node = (EntityNode) invoke(tree, method,
                new Class<?>[]{method.equals("findNode") ? Integer.class : int.class}, entity);
        assertNotNull("Items node " + method + " for " + entity, node);
        for (var parent = node.getParent(); parent != null; parent = parent.getParent()) {
            parent.setExpanded(true);
        }
        refresh();
        Actor actor = node.getActor();
        for (Actor parent = actor.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane pane) {
                pane.scrollTo(0, actor.getY(), actor.getWidth(), actor.getHeight());
                pane.updateVisualScroll();
            }
        }
        refresh();
        Vector2 point = app.getUiStage().stageToScreenCoordinates(
                node.getLabel().localToStageCoordinates(new Vector2(4, node.getLabel().getHeight() / 2)));
        pointer(point);
        assertTrue(app.getUiStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT));
        app.getUiStage().touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT);
        refresh();
        assertSame("Actual Items click must select the requested row", node, ((Tree<?, ?>) tree).getSelection().first());
    }

    private boolean footprintVisible() throws Exception {
        return (Boolean) invoke(overlay, "positions", new Class<?>[]{});
    }

    private void influenceDrawn(boolean expected) throws Exception {
        float[] output = (float[]) field(gizmo, "tmpActorInfluenceQuad");
        java.util.Arrays.fill(output, Float.NaN);
        refresh();
        assertEquals("Influence envelope rendering", expected, Float.isFinite(output[0]));
    }

    private void transitions(int entity, int other) throws Exception {
        configurePhysics(other, false);
        selectNode(entity, "findNode");
        assertTrue(ctx.world().getSystem(SpatialRenderOrderSystem.class).writeActorInfluenceQuad(entity, new float[8]));
        influenceDrawn(true);
        String before = authored(entity, other);
        int history = ctx.historyManager().getCursor();
        for (String node : new String[]{"findBodyNode", "findFootprintNode", "findBodyNode"}) {
            selectNode(entity, node);
            boolean footprint = node.equals("findFootprintNode");
            assertEquals(footprint, footprintVisible());
            influenceDrawn(false);
            assertHoverCleared();
            if (footprint) {
                assertEquals(entity, ctx.selectionService().getInspectedFootprintOwner());
                assertEquals(0, ctx.selectionService().getSelectionSnapshot().size);
                assertEquals(-1, canvas.getPhysicsSelectionService().getFocusedBodyEid());
                assertEquals(entity, field(field(app, "propertiesPanel"), "boundEntity"));
                assertFootprintHandlesOnly();
                capture("footprint-inspection");
            } else {
                assertEquals(entity, canvas.getPhysicsSelectionService().getFocusedBodyEid());
                assertEquals(entity, field(field(app, "propertiesPanel"), "boundBody"));
            }
        }
        selectNode(other, "findBodyNode");
        assertEquals(other, canvas.getPhysicsSelectionService().getFocusedBodyEid());
        assertHoverCleared();
        Vector2 background = new Vector2(320, 270);
        press(background);
        release(background);
        hover(new Vector2(10, 150));
        assertEquals(-1, ctx.physicsSelectionService().getFocusedBodyEid());
        assertEquals(0, ctx.selectionService().getSelectionSnapshot().size);
        assertFalse(footprintVisible());
        assertHoverCleared();
        assertEquals(history, ctx.historyManager().getCursor());
        assertEquals(before, authored(entity, other));
        capture("deselected");
    }

    private void assertFootprintHandlesOnly() throws Exception {
        assertTrue(footprintVisible());
        var handles = (Vector2[]) field(overlay, "handles");
        for (int i = 0; i < 4; i++) {
            assertTrue("No rectangle/pivot handle " + i, Float.isNaN(handles[i].x));
        }
        assertTrue(Float.isFinite(handles[4].x));
        assertTrue(Float.isFinite(handles[5].x));
    }

    private Vector2 footprintHandle(int index) throws Exception {
        assertTrue(footprintVisible());
        return app.getUiStage().stageToScreenCoordinates(((Vector2[]) field(overlay, "handles"))[index].cpy());
    }

    private Vector2 beginFootprintDrag(int index) throws Exception {
        Vector2 point = footprintHandle(index);
        pointer(point);
        assertTrue(app.getUiStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT));
        assertTrue(((ActorSpatialRectangleCanvasEditor) overlay).isDragging());
        point.add(18, -10);
        pointer(point);
        app.getUiStage().touchDragged(mouseX, mouseY, 0);
        refresh();
        return point;
    }

    private void footprintGestures(int entity, int other) throws Exception {
        selectNode(entity, "findFootprintNode");
        assertFootprintHandlesOnly();
        for (int index : new int[]{4, 5}) {
            String geometry = new com.badlogic.gdx.utils.Json().toJson(ctx.world().getMapper(SpatialRepresentationComponent.class).getSafe(entity, null));
            var collision = shape(entity).copy();
            var transform = ctx.world().getMapper(TransformComponent.class).get(entity);
            String visual = new com.badlogic.gdx.utils.Json().toJson(transform);
            var before = footprint(entity);
            int history = ctx.historyManager().getCursor();
            Vector2 end = beginFootprintDrag(index);
            var after = footprint(entity);
            assertNotEquals(before, after);
            assertEquals(before.altitude(), after.altitude(), 0);
            if (index == 4) {
                assertEquals(before.radius(), after.radius(), 0);
                assertNotEquals(before.offsetX(), after.offsetX(), 0);
                assertNotEquals(before.offsetY(), after.offsetY(), 0);
            } else {
                assertEquals(before.offsetX(), after.offsetX(), 0);
                assertEquals(before.offsetY(), after.offsetY(), 0);
                assertNotEquals(before.radius(), after.radius(), 0);
            }
            assertEquals(history, ctx.historyManager().getCursor());
            app.getUiStage().touchUp(Math.round(end.x), Math.round(end.y), 0, Input.Buttons.LEFT);
            refresh();
            assertEquals(history + 1, ctx.historyManager().getCursor());
            assertEquals(0, ctx.selectionService().getSelectionSnapshot().size);
            ctx.historyManager().undo();
            assertEquals(before, footprint(entity));
            ctx.historyManager().redo();
            assertEquals(after, footprint(entity));
            assertTrue(collision.contentEquals(shape(entity)));
            assertEquals(visual, new com.badlogic.gdx.utils.Json().toJson(transform));
            assertEquals(geometry, new com.badlogic.gdx.utils.Json().toJson(ctx.world().getMapper(SpatialRepresentationComponent.class).getSafe(entity, null)));
        }
        var before = footprint(entity);
        var otherBefore = footprint(other);
        int history = ctx.historyManager().getCursor();
        beginFootprintDrag(4);
        ctx.selectionService().selectOnly(other);
        refresh();
        assertFalse(((ActorSpatialRectangleCanvasEditor) overlay).isDragging());
        assertEquals(before, footprint(entity));
        assertEquals(otherBefore, footprint(other));
        assertEquals(history, ctx.historyManager().getCursor());
        selectNode(entity, "findFootprintNode");
        beginFootprintDrag(5);
        ctx.selectionService().selectOnly(entity); // Same entity, different selection role.
        refresh();
        assertFalse(((ActorSpatialRectangleCanvasEditor) overlay).isDragging());
        assertEquals(before, footprint(entity));
        selectNode(entity, "findFootprintNode");
        beginFootprintDrag(4);
        canvas.getPhysicsSelectionService().focusBody(entity);
        refresh();
        assertFalse(((ActorSpatialRectangleCanvasEditor) overlay).isDragging());
        assertEquals(before, footprint(entity));
        selectNode(entity, "findFootprintNode");
        beginFootprintDrag(4);
        ctx.selectionService().clearSelection();
        refresh();
        assertFalse(((ActorSpatialRectangleCanvasEditor) overlay).isDragging());
        assertEquals(before, footprint(entity));
        assertFalse(footprintVisible());
    }

    private EditSpatialFootprintCommand.Snapshot footprint(int entity) {
        int owner = SpatialActorShapeSupport.owner(ctx.world(), entity);
        return owner < 0 ? null : EditSpatialFootprintCommand.Snapshot.capture(ctx.world().getMapper(SpatialFootprintComponent.class).get(owner));
    }

    private void inheritedFootprint(int child, int owner) throws Exception {
        assertEquals(owner, SpatialActorShapeSupport.owner(ctx.world(), child));
        assertFalse(ctx.world().getMapper(SpatialFootprintComponent.class).has(child));
        selectNode(child, "findNode");
        assertTrue(footprintVisible());
        var before = footprint(owner);
        Vector2 end = beginFootprintDrag(5);
        app.getUiStage().touchUp(Math.round(end.x), Math.round(end.y), 0, Input.Buttons.LEFT);
        refresh();
        assertNotEquals(before, footprint(owner));
        ctx.historyManager().undo();
        assertEquals(before, footprint(owner));
        selectNode(owner, "findFootprintNode");
        assertFootprintHandlesOnly();
        assertEquals(owner, field(overlay, "owner"));
    }

    private void cancellation(int entity, int other) throws Exception {
        for (boolean circle : new boolean[]{true, false}) {
            for (boolean changeTarget : new boolean[]{true, false}) {
                var before = configurePhysics(entity, circle).copy();
                selectNode(entity, "findBodyNode");
                canvas.getPhysicsSelectionService().setSelectedShape(entity, before.physicsShapeId);
                int history = ctx.historyManager().getCursor();
                Vector2 start = physicsHandle(entity, circle);
                press(start);
                drag(start.cpy().add(20, -12));
                assertFalse(before.contentEquals(shape(entity)));
                var otherBefore = shape(other).copy();
                if (changeTarget) {
                    ctx.selectionService().selectOnly(other);
                    canvas.getPhysicsSelectionService().focusBody(other);
                } else {
                    canvas.getPhysicsSelectionService().clear();
                }
                refresh();
                assertTrue(before.contentEquals(shape(entity)));
                assertTrue(otherBefore.contentEquals(shape(other)));
                assertEquals(false, field(picking, circle ? "resizingCircleActive" : "resizingBoxActive"));
                assertEquals(history, ctx.historyManager().getCursor());
                release(start);
            }
            var before = configurePhysics(entity, circle).copy();
            selectNode(entity, "findBodyNode");
            canvas.getPhysicsSelectionService().setSelectedShape(entity, before.physicsShapeId);
            int history = ctx.historyManager().getCursor();
            press(physicsHandle(entity, circle));
            Vector2 ui = new Vector2(800, 180);
            drag(ui);
            assertEquals("Capture survives UI crossing", true, field(picking, circle ? "resizingCircleActive" : "resizingBoxActive"));
            release(ui);
            assertEquals(history + 1, ctx.historyManager().getCursor());
            assertHoverCleared();
            ctx.historyManager().undo();
            assertTrue(before.contentEquals(shape(entity)));
        }
    }

    private void linkedGeometryProtected(int entity) throws Exception {
        for (boolean circle : new boolean[]{true, false}) {
            configurePhysics(entity, circle);
            selectNode(entity, "findBodyNode");
            canvas.getPhysicsSelectionService().setSelectedShape(entity, shape(entity).physicsShapeId);
            Vector2 point = physicsHandle(entity, circle);
            // Keep the compiled fixture but remove directly authored geometry, as for a generated shape.
            var original = shape(entity).geometry;
            shape(entity).geometry = null;
            try {
                hover(point);
                assertEquals(InputManipulationContext.Handle.NONE, ((InputManipulationContext) field(picking, "ctx")).hoveredHandle());
                press(point);
                assertEquals(false, field(picking, circle ? "resizingCircleActive" : "resizingBoxActive"));
                release(point);
                assertNull(shape(entity).geometry);
            } finally {
                shape(entity).geometry = original;
            }
        }
    }

    private void documentSwitch(int entity) throws Exception {
        awaitAtlas(app);
        selectNode(entity, "findFootprintNode");
        var before = footprint(entity);
        int history = ctx.historyManager().getCursor();
        beginFootprintDrag(4);
        var scene = app.getEditorDocumentManager().activeDocument();
        assertNotNull(app.openHudScreen("hud/Screen"));
        refresh();
        assertFalse(((ActorSpatialRectangleCanvasEditor) overlay).isDragging());
        assertEquals(before, footprint(entity));
        assertEquals(history, ctx.historyManager().getCursor());
        assertHoverCleared();
        app.getEditorDocumentManager().activate(scene.key());
        refresh();
        assertSame(ctx, canvas.getAttachedSceneContext());
        for (boolean circle : new boolean[]{true, false}) {
            var collision = configurePhysics(entity, circle).copy();
            selectNode(entity, "findBodyNode");
            ctx.physicsSelectionService().setSelectedShape(entity, collision.physicsShapeId);
            Vector2 start = physicsHandle(entity, circle);
            press(start);
            drag(start.cpy().add(20, -12));
            assertFalse(collision.contentEquals(shape(entity)));
            int cursor = ctx.historyManager().getCursor();
            app.openHudScreen("hud/Screen");
            refresh();
            assertTrue(collision.contentEquals(shape(entity)));
            assertEquals(cursor, ctx.historyManager().getCursor());
            assertHoverCleared();
            app.getEditorDocumentManager().activate(scene.key());
            refresh();
            assertSame(ctx, canvas.getAttachedSceneContext());
            assertTrue(collision.contentEquals(shape(entity)));
        }
    }

    private void cursorOwnership(int entity) throws Exception {
        configurePhysics(entity, true);
        selectNode(entity, "findBodyNode");
        ctx.physicsSelectionService().setSelectedShape(entity, shape(entity).physicsShapeId);
        hover(physicsHandle(entity, true));
        assertEquals(true, field(picking, "osCursorHidden"));
        Graphics original = Gdx.graphics;
        var writes = new java.util.ArrayList<Object>();
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setSystemCursor")) {
                        writes.add(args[0]);
                    }
                    return method.invoke(original, args);
                });
        try {
            DragContext.get().begin(new DragPayload());
            picking.process();
            assertHoverCleared();
            picking.cancelLightValueGesture();
            assertTrue("Picking must not reset the cursor owned by asset dragging", writes.isEmpty());
            DragContext.get().cancel();
            picking.process();
            assertEquals("RESIZE", field(gizmo, "cursorKind").toString());
            writes.clear();
            pointer(new Vector2(10, 150));
            picking.process();
            assertHoverCleared();
            assertEquals(java.util.List.of(com.badlogic.gdx.graphics.Cursor.SystemCursor.Arrow), writes);
            writes.clear();
            picking.process();
            picking.cancelLightValueGesture();
            assertTrue("No cursor ownership means no OS cursor write", writes.isEmpty());
        } finally {
            DragContext.get().cancel();
            Gdx.graphics = original;
        }
    }

    private void spatialCursorIgnoresObjectScaleLock(int entity) throws Exception {
        selectNode(entity, "findNode");
        assertTrue(GameObjectHierarchyCommandSupport.isPhysicsAncestorScaleLocked(ctx.world(), entity));
        var spatial = ctx.spatialBlockSelectionService();
        // Isolate cursor routing from block picking: the selected actor's lock cannot authorize a block handle.
        spatial.enterMap(entity);
        try {
            for (var handle : new InputManipulationContext.Handle[]{InputManipulationContext.Handle.E, InputManipulationContext.Handle.NE}) {
                invoke(picking, "updateCursorForHover", new Class<?>[]{InputManipulationContext.Handle.class,
                                boolean.class, InputManipulationContext.Mode.class, InputManipulationContext.Handle.class},
                        handle, false, InputManipulationContext.Mode.IDLE, InputManipulationContext.Handle.NONE);
                assertEquals("RESIZE", field(gizmo, "cursorKind").toString());
            }
        } finally {
            spatial.clear();
            refresh();
        }
    }

    private void capture(String name) {
        String directory = System.getProperty("pixscape.cursorReviewDir");
        if (directory == null) {
            return;
        }
        var image = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            com.badlogic.gdx.graphics.PixmapIO.writePNG(new FileHandle(directory).child(name + ".png"), image, -1, true);
        } finally {
            image.dispose();
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private String authored(int... entities) {
        StringBuilder result = new StringBuilder();
        var json = new com.badlogic.gdx.utils.Json();
        for (int entity : entities) {
            for (Class type : new Class[]{TransformComponent.class, PhysicsBodyComponent.class, PhysicsShapesComponent.class,
                    SpatialFootprintComponent.class, SpatialRepresentationComponent.class, GameObjectComponent.class,
                    GameObjectMemberComponent.class, PixscapeIdentityComponent.class, EntityMetaComponent.class}) {
                result.append(json.toJson(ctx.world().getMapper(type).getSafe(entity, null)));
            }
        }
        return result.toString();
    }

    private void refresh() {
        EventFlow.i().flush();
        app.render();
        app.render();
    }

    private void awaitAtlas(StudioApplicationAdapter studio) throws Exception {
        var atlas = studio.getCanvas().getAtlasService();
        long deadline = System.nanoTime() + 15_000_000_000L;
        studio.render();
        while (atlas.hasAsyncPackQueuedOrRunningFor("scene1")) {
            assertTrue("Atlas publication deadline", System.nanoTime() < deadline);
            Thread.sleep(10);
            studio.render();
        }
        studio.render();
    }

    private Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                var method = c.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (NoSuchMethodException ex) {
                // Continue through the superclass for private probes.
            }
        }
        throw new NoSuchMethodException(name);
    }
}
