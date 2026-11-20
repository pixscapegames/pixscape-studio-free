package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.studio.history.commands.EditSpatialFootprintCommand;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.history.commands.SetFootprintInheritanceCommand;
import games.pixscape.studio.history.commands.RemoveSpatialSubtreeCommand;
import games.pixscape.studio.history.commands.EditAnimationCommand;
import games.pixscape.studio.service.SpatialActorShapeSupport;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.ui.widget.ActorSpatialRectangleCanvasEditor;
import games.pixscape.studio.ui.widget.FloatField;
import org.junit.Test;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

/** Exercises the real Stage gesture listeners, rather than only their history commands. */
public class ActorSpatialCanvasEditingGlTest {
    @Test
    public void initializedRectangleGesturesPreserveOtherSettingsAndHistory() throws Exception {
        withStudio(850, (studio, root) -> {
            var file = project(studio, root);
            addImage(root, "spatial", 160, 240);
            var database = games.pixscape.studio.asset.AssetMetaDatabase.load(root.child("assets.json"));
            var animationMeta = (games.pixscape.studio.asset.AnimationAssetMeta) database.registerIfAbsent(
                    games.pixscape.studio.asset.AssetType.ANIMATION, "spatial-clips", "orig/animations/spatial-clips",
                    games.pixscape.studio.asset.AssetMeta.AssetScope.USER);
            animationMeta.frameCount = 2;
            animationMeta.fps = 8;
            animationMeta.currentClip = "idle";
            animationMeta.clips.put("idle", new games.pixscape.studio.asset.AnimationClipMeta(0, 0));
            animationMeta.clips.put("walk", new games.pixscape.studio.asset.AnimationClipMeta(1, 1));
            for (int frame = 0; frame < 2; frame++) {
                root.child("orig/images/spatial.png").copyTo(root.child("orig/animations/spatial-clips/frame" + frame + ".png"));
            }
            database.save(root.child("assets.json"));
            open(studio, file);
            ((TopMenuBar) field(studio, "topMenuBar")).beginProject();
            var canvas = studio.getCanvas();
            var ctx = canvas.getAttachedSceneContext();
            ctx.sceneMeta().physicsEnabled = false;
            ctx.world().getMapper(LayerComponent.class).get(ctx.layerService().getLayerEntity(0)).spatialEnabled = true;
            int entity = canvas.getEditorOps().createStandaloneSprite("spatial.png", 0, 0);
            ctx.world().process();
            ctx.historyManager().execute(new ToggleSpatialActorCommand(ctx.world(), ctx.historyManager().historyIds(),
                    entity, true, true, new SpatialFootprintComponent()));
            assertNotNull(rectangle(ctx, entity));
            ctx.selectionService().selectOnly(entity);
            canvas.focusCameraAt(80, 120);
            studio.render();
            studio.render();
            numeric(studio, "actor-spatial-rectangle-0", 96);
            numeric(studio, "actor-spatial-rectangle-1", 120);
            numeric(studio, "actor-spatial-rectangle-2", 32);
            numeric(studio, "actor-spatial-rectangle-3", 80);
            numeric(studio, "actor-spatial-rectangle-4", -70);
            numeric(studio, "actor-spatial-rectangle-5", -50);
            numeric(studio, "spatial-footprint-1", 32);
            assertEquals(0.6f, rectangle(ctx, entity).width, 0);
            assertEquals(-70f, rectangle(ctx, entity).pivotX, 0);
            var overlay = (ActorSpatialRectangleCanvasEditor) studio.getUiStage().getRoot()
                    .findActor("actor-spatial-rectangle-editor");
            assertNotNull(overlay);
            center(studio, overlay);

            for (int index : new int[]{2, 3, 1, 0, 4, 5}) {
                checkGesture(studio, ctx, overlay, entity, index);
            }
            ctx.sceneMeta().physicsEnabled = true;
            canvas.getPhysicsService().ensurePhysics(entity);
            ctx.world().process();
            var shapes = ctx.world().getMapper(PhysicsShapesComponent.class).get(entity);
            var expectedShape = shapes.shapes.first().copy();
            checkGesture(studio, ctx, overlay, entity, 2);
            checkGesture(studio, ctx, overlay, entity, 0);
            assertTrue(expectedShape.contentEquals(shapes.shapes.first()));

            canvas.getPhysicsService().removePhysics(entity);
            ctx.sceneMeta().physicsEnabled = false;
            var parentRectangle = rectangle(ctx, entity).copy();
            int child = canvas.getEditorOps().createStandaloneSpriteInGameObject(entity, "spatial.png", "Child");
            int sibling = canvas.getEditorOps().createStandaloneSpriteInGameObject(entity, "spatial.png", "Sibling");
            ctx.world().process();
            assertNotNull("Creation under a spatial parent initializes the representation", rectangle(ctx, child));
            assertEquals(entity, SpatialActorShapeSupport.owner(ctx.world(), child));
            var siblingRectangle = rectangle(ctx, sibling).copy();
            ctx.selectionService().selectOnly(child);
            studio.render();
            numeric(studio, "actor-spatial-rectangle-0", 90);
            numeric(studio, "actor-spatial-rectangle-3", 40);
            numeric(studio, "actor-spatial-rectangle-4", -60);
            center(studio, overlay);
            checkGesture(studio, ctx, overlay, child, 2);
            for (boolean inherited : new boolean[]{false, true}) {
                ctx.historyManager().execute(new SetFootprintInheritanceCommand(ctx.world(),
                        ctx.historyManager().historyIds(), child, inherited, ctx.documentType()));
                ctx.world().process();
                assertEquals(inherited ? entity : child, SpatialActorShapeSupport.owner(ctx.world(), child));
                checkGesture(studio, ctx, overlay, child, 2);
                checkGesture(studio, ctx, overlay, child, 0);
            }
            assertTrue(parentRectangle.same(rectangle(ctx, entity)));
            assertTrue(siblingRectangle.same(rectangle(ctx, sibling)));
            var childRectangle = rectangle(ctx, child).copy();
            ctx.historyManager().execute(new RemoveSpatialSubtreeCommand(ctx.world(), ctx.historyManager().historyIds(), entity));
            for (int cycle = 0; cycle < 3; cycle++) {
                ctx.world().process();
                assertEquals(-1, SpatialActorShapeSupport.owner(ctx.world(), child));
                assertTrue(childRectangle.same(rectangle(ctx, child)));
                ctx.historyManager().undo();
                ctx.world().process();
                assertEquals(entity, SpatialActorShapeSupport.owner(ctx.world(), child));
                ctx.historyManager().redo();
            }
            ctx.historyManager().undo();
            ctx.world().process();
            checkGesture(studio, ctx, overlay, child, 2);
            int childStableId = ctx.world().getMapper(PixscapeIdentityComponent.class).get(child).stableId;
            awaitAtlas(studio);
            SceneService.saveScene(ctx.world(), root.child("scenes/scene1.json"), false);
            ProjectConfig.ProjectIO.saveProject(ProjectConfig.getInstance(), file);
            open(studio, file);
            ((TopMenuBar) field(studio, "topMenuBar")).beginProject();
            ctx = canvas.getAttachedSceneContext();
            child = ctx.identityRegistry().findByStableId(childStableId);
            assertTrue(child >= 0);
            assertTrue(childRectangle.same(rectangle(ctx, child)));
            ctx.selectionService().selectOnly(child);
            studio.render();
            center(studio, overlay);
            checkGesture(studio, ctx, overlay, child, 2);

            int animation = canvas.getEditorOps().createAnimationSprite("spatial-clips", 0, 0);
            ctx.world().process();
            ctx.historyManager().execute(new ToggleSpatialActorCommand(ctx.world(), ctx.historyManager().historyIds(),
                    animation, true, true, new SpatialFootprintComponent()));
            ctx.selectionService().selectOnly(animation);
            studio.render();
            numeric(studio, "actor-spatial-rectangle-0", 90);
            numeric(studio, "actor-spatial-rectangle-3", 40);
            numeric(studio, "actor-spatial-rectangle-4", -60);
            center(studio, overlay);
            checkGesture(studio, ctx, overlay, animation, 2);
            var idle = rectangle(ctx, animation).copy();
            var component = ctx.world().getMapper(AnimationComponent.class).get(animation);
            var asset = ctx.world().getMapper(AssetRefComponent.class).get(animation);
            var beforeClip = EditAnimationCommand.Snapshot.capture(component, asset);
            var afterClip = beforeClip.copy();
            afterClip.currentClip = "walk";
            ctx.historyManager().execute(new EditAnimationCommand(ctx.world(), ctx.historyManager().historyIds(),
                    animation, beforeClip, afterClip, null, null));
            studio.render();
            assertEquals(1f, rectangle(ctx, animation).width, 0f);
            numeric(studio, "actor-spatial-rectangle-0", 110);
            numeric(studio, "actor-spatial-rectangle-3", 60);
            numeric(studio, "actor-spatial-rectangle-4", -40);
            center(studio, overlay);
            checkGesture(studio, ctx, overlay, animation, 3);
            checkGesture(studio, ctx, overlay, animation, 0);
            var walk = rectangle(ctx, animation).copy();
            assertTrue(idle.same(ctx.world().getMapper(SpatialRepresentationComponent.class).get(animation)
                    .rectangles.get(asset.assetId, "idle")));
            int animationStableId = ctx.world().getMapper(PixscapeIdentityComponent.class).get(animation).stableId;
            awaitAtlas(studio);
            SceneService.saveScene(ctx.world(), root.child("scenes/scene1.json"), false);
            ProjectConfig.ProjectIO.saveProject(ProjectConfig.getInstance(), file);
            open(studio, file);
            ((TopMenuBar) field(studio, "topMenuBar")).beginProject();
            ctx = canvas.getAttachedSceneContext();
            animation = ctx.identityRegistry().findByStableId(animationStableId);
            assertTrue(walk.same(rectangle(ctx, animation)));
            assertTrue(idle.same(ctx.world().getMapper(SpatialRepresentationComponent.class).get(animation)
                    .rectangles.get(animationMeta.id(), "idle")));
            ctx.selectionService().selectOnly(animation);
            studio.render();
            center(studio, overlay);
            checkGesture(studio, ctx, overlay, animation, 2);
        });
    }

    private static void checkGesture(StudioApplicationAdapter studio, SceneEditorContext ctx,
            ActorSpatialRectangleCanvasEditor overlay, int entity, int index) throws Exception {
        var history = ctx.historyManager();
        var before = rectangle(ctx, entity).copy();
        int owner = SpatialActorShapeSupport.owner(ctx.world(), entity);
        var footprint = EditSpatialFootprintCommand.Snapshot.capture(
                ctx.world().getMapper(SpatialFootprintComponent.class).get(owner));
        int cursor = history.getCursor();
        Vector2 start = handle(studio, overlay, index);
        press(studio, overlay, start);
        assertEquals("Picked handle", index, field(overlay, "handle"));
        release(studio, start);
        assertEquals("Click without movement", cursor, history.getCursor());
        assertTrue(before.same(rectangle(ctx, entity)));

        for (boolean escape : new boolean[]{true, false}) {
            start = handle(studio, overlay, index);
            press(studio, overlay, start);
            studio.getUiStage().touchDragged(Math.round(start.x + 18), Math.round(start.y - 14), 0);
            assertEquals("Preview has no history entry", cursor, history.getCursor());
            if (escape) {
                studio.getUiStage().keyDown(Input.Keys.ESCAPE);
                release(studio, start);
            } else {
                studio.getUiStage().cancelTouchFocus();
            }
            assertFalse(overlay.isDragging());
            assertTrue(before.same(rectangle(ctx, entity)));
            assertEquals(footprint, EditSpatialFootprintCommand.Snapshot.capture(
                    ctx.world().getMapper(SpatialFootprintComponent.class).get(owner)));
            assertEquals(cursor, history.getCursor());
        }
        start = handle(studio, overlay, index);
        Vector2 end = new Vector2(start).add(18, -14);
        press(studio, overlay, start);
        studio.getUiStage().touchDragged(Math.round(end.x), Math.round(end.y), 0);
        release(studio, end);
        assertFalse(overlay.isDragging());
        assertEquals("One command per completed gesture", cursor + 1, history.getCursor());
        ActorSpatialRectangleData after = rectangle(ctx, entity).copy();
        var afterFootprint = EditSpatialFootprintCommand.Snapshot.capture(
                ctx.world().getMapper(SpatialFootprintComponent.class).get(owner));
        if (index >= 4) {
            assertTrue(before.same(after));
            assertNotEquals(footprint, afterFootprint);
        } else {
            assertEquals(footprint, afterFootprint);
            assertFalse(before.same(after));
            if (index != 0) {
                assertEquals(before.pivotX, after.pivotX, 0);
                assertEquals(before.pivotY, after.pivotY, 0);
            } else {
                assertEquals(before.x, after.x, 0);
                assertEquals(before.y, after.y, 0);
                assertEquals(before.width, after.width, 0);
                assertEquals(before.height, after.height, 0);
            }
        }
        for (int cycle = 0; cycle < 3; cycle++) {
            history.undo();
            assertTrue(before.same(rectangle(ctx, entity)));
            assertEquals(footprint, EditSpatialFootprintCommand.Snapshot.capture(
                    ctx.world().getMapper(SpatialFootprintComponent.class).get(owner)));
            history.redo();
            assertTrue(after.same(rectangle(ctx, entity)));
            assertEquals(afterFootprint, EditSpatialFootprintCommand.Snapshot.capture(
                    ctx.world().getMapper(SpatialFootprintComponent.class).get(owner)));
            assertEquals(entity, ctx.selectionService().getFirstSelectedEntityId());
        }
        history.undo();
    }

    private static ActorSpatialRectangleData rectangle(SceneEditorContext ctx, int entity) {
        var asset = ctx.world().getMapper(AssetRefComponent.class).get(entity);
        var animation = ctx.world().getMapper(AnimationComponent.class).getSafe(entity, null);
        return ctx.world().getMapper(SpatialRepresentationComponent.class).get(entity).rectangles
                .get(asset.assetId, animation == null ? null : animation.currentClip);
    }

    private static void center(StudioApplicationAdapter studio, ActorSpatialRectangleCanvasEditor overlay) throws Exception {
        Vector2 actual = handle(studio, overlay, 1);
        Vector2 desired = new Vector2(500, 400);
        var canvas = studio.getCanvas();
        canvas.getGridStage().screenToStageCoordinates(actual);
        canvas.getGridStage().screenToStageCoordinates(desired);
        var camera = canvas.getGridStage().getCamera();
        canvas.focusCameraAt(camera.position.x + actual.x - desired.x, camera.position.y + actual.y - desired.y);
        studio.render();
    }

    private static void awaitAtlas(StudioApplicationAdapter studio) throws Exception {
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

    private static void numeric(StudioApplicationAdapter studio, String name, float value) {
        FloatField input = studio.getUiStage().getRoot().findActor(name);
        assertNotNull(name, input);
        input.setText(Float.toString(value));
        input.commit();
    }

    private static Vector2 handle(StudioApplicationAdapter studio, ActorSpatialRectangleCanvasEditor overlay, int index)
            throws Exception {
        var positions = ActorSpatialRectangleCanvasEditor.class.getDeclaredMethod("positions");
        positions.setAccessible(true);
        assertTrue((Boolean) positions.invoke(overlay));
        Vector2 point = new Vector2(((Vector2[]) field(overlay, "handles"))[index]);
        return studio.getUiStage().stageToScreenCoordinates(point);
    }

    private static void press(StudioApplicationAdapter studio, ActorSpatialRectangleCanvasEditor overlay, Vector2 point) {
        assertTrue("Stage touch at " + point, studio.getUiStage().touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT));
        assertTrue("The Stage must pick the spatial handle", overlay.isDragging());
    }

    private static void release(StudioApplicationAdapter studio, Vector2 point) {
        studio.getUiStage().touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
    }
}
