package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.commands.ToggleSpatialActorCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Explicit opt-in standalone GL check. Creates only an isolated fixture and never opens user scenes.
 */
public final class ActorShadowRectangleGlCheck {
    public static void main(String[] args) throws Exception {
        FileHandle root = new FileHandle(args[0]);
        root.mkdirs();
        System.setProperty("user.home", root.child("home").path());
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectDirectoryPath = root.path();
        cfg.projectFileName = "fixture.json";
        cfg.exportRootPathDir = root.child("export").path();
        cfg.createSceneMeta("Volume fixture");
        cfg.getCurrentSceneMeta().physicsEnabled = false;
        ProjectConfig.setInstance(cfg);
        FileHandle image = root.child("orig/images/barrel.png");
        new FileHandle(args[1]).copyTo(image);
        AssetMetaDatabase db = new AssetMetaDatabase();
        var imageMeta = db.registerIfAbsent(AssetType.IMAGE, "images/barrel", "orig/images/barrel.png", AssetMeta.AssetScope.USER);
        db.save(root.child("assets.json"));
        Throwable[] failed = {null};
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Actor spatial rectangle isolated UI check");
        gl.setWindowedMode(1200, 850);
        boolean manual = args.length > 2;
        gl.setInitialVisible(manual);
        gl.useVsync(true);
        gl.setForegroundFPS(30);
        gl.disableAudio(true);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        new Lwjgl3Application(new ApplicationAdapter() {
            StudioApplicationAdapter studio;
            SceneEditorContext ctx;
            ActorSpatialRectangleCanvasEditor overlay;
            Texture texture;
            int e, frame;
            long start = System.nanoTime();

            @Override
            public void create() {
                try {
                    studio = new StudioApplicationAdapter();
                    studio.create();
                    var canvas = studio.getCanvas();
                    ctx = canvas.getAttachedSceneContext();
                    if (ctx == null) {
                        ctx = canvas.createSceneContext("scene1", cfg.getCurrentSceneMeta());
                        canvas.attach(ctx);
                    }
                    studio.getEditorDocumentManager().openScene("scene1", "Volume fixture", ctx);
                    ctx.layerService().addLayerTop("Actors");
                    var world = ctx.world();
                    world.process();
                    var layer = world.getMapper(LayerComponent.class).get(ctx.layerService().getLayerEntity(0));
                    layer.spatialEnabled = true;
                    texture = new Texture(image);
                    e = world.create();
                    new GenericEntityInitializer(world).configureStandaloneSprite(imageMeta.id(), "scene1", texture.getWidth(), texture.getHeight(),
                            0, 0, texture.getWidth() / 2f, 0, ShaderRegistry.indexOf(canvas.getDefaultShaderName()),
                            games.pixscape.runtime.render.BlendMode.ALPHA.id, TextureRegistry.handleOf(texture), "Volume witness", 0).init(e);
                    ctx.identityRegistry().ensureStableId(e);
                    world.process();
                    games.pixscape.runtime.component.spatial.SpatialFootprintComponent foot = new games.pixscape.runtime.component.spatial.SpatialFootprintComponent();
                    foot.radiusPx = 20f;
                    ctx.historyManager().execute(new ToggleSpatialActorCommand(world, ctx.historyManager().historyIds(),
                            e, true, true, foot));
                    volume().width = 120;
                    volume().height = 120;
                    volume().x = -60;
                    volume().y = 20;
                    volume().pivotY = 25;
                    canvas.focusCameraAt(0, 85);
                    ((OrthographicCamera) canvas.getGridStage().getCamera()).zoom = .55f;
                    ctx.selectionService().selectOnly(e);
                    world.process();
                    overlay = (ActorSpatialRectangleCanvasEditor) studio.getUiStage().getRoot().findActor("actor-spatial-rectangle-editor");
                    assertNotNull(overlay);
                    studio.resize(1200, 850);
                    arrange(false);
                    if (manual && args[2].equals("overlap")) {
                        arrange(true);
                    }
                } catch (Throwable failure) {
                    stop(failure);
                }
            }

            ActorSpatialRectangleData volume() {
                return ctx.world().getMapper(SpatialRepresentationComponent.class).get(e).rectangles.staticRectangle;
            }

            @Override
            public void render() {
                if (failed[0] != null) {
                    return;
                }
                try {
                    studio.render();
                    frame++;
                    if (manual) {
                        if (frame == 14) {
                            arrange(args[2].equals("overlap"));
                        }
                        if (frame == 15) {
                            capture("manual-start");
                        }
                        if (root.child("STOP").exists() || System.nanoTime() - start > 900_000_000_000L) {
                            Gdx.app.exit();
                        }
                        return;
                    }
                    if (System.nanoTime() - start > 60_000_000_000L) {
                        throw new AssertionError("UI deadline");
                    }
                    switch (frame) {
                        case 15 -> {
                            assertTrue("All handles available without scene physics", positions());
                            assertNull(studio.getUiStage().getRoot().findActor("actor-spatial-edit-target"));
                            checkGestures();
                            arrange(false);
                        }
                        case 17 -> capture("separated");
                        case 18 -> arrange(true);
                        case 20 -> capture("coincident");
                        case 21 -> checkCoincident();
                        case 24 -> {
                            ((OrthographicCamera) studio.getCanvas().getGridStage().getCamera()).zoom = .5f;
                        }
                        case 26 -> {
                            checkGestures();
                            checkPartialOverlap();
                        }
                        case 28 -> {
                            ((OrthographicCamera) studio.getCanvas().getGridStage().getCamera()).zoom = 1.8f;
                        }
                        case 30 -> {
                            checkGestures();
                            checkPartialOverlap();
                        }
                        case 32 -> {
                            cfg.getCurrentSceneMeta().physicsEnabled = true;
                            ctx.world().getMapper(PhysicsBodyComponent.class).create(e).type = PhysicsBodyComponent.STATIC;
                            PhysicsShapeData box = PhysicsService.createDefaultShape(1);
                            box.geometry.halfWidth = .7f;
                            box.geometry.halfHeight = .3f;
                            box.geometry.offsetX = .2f;
                            var shapes = ctx.world().getMapper(PhysicsShapesComponent.class).create(e);
                            PhysicsService.publishPreparedCandidate(shapes,
                                    ctx.world().getMapper(PhysicsCompiledFixturesComponent.class).create(e),
                                    PhysicsService.prepareBodyCandidate(com.badlogic.gdx.utils.Array.with(box)));
                            ctx.world().process();
                            long generation = ctx.world().getMapper(PhysicsCompiledFixturesComponent.class).get(e).generation;
                            checkGestures();
                            ctx.world().process();
                            assertTrue(box.contentEquals(shapes.shapes.first()));
                            assertEquals(generation, ctx.world().getMapper(PhysicsCompiledFixturesComponent.class).get(e).generation);
                        }
                        case 35 -> {
                            positions();
                            Vector2 h = handle(4);
                            var modes = studio.getCanvas().getStudioEditingModeService();
                            modes.setMode(games.pixscape.studio.service.StudioEditingMode.PHYSICS, 0);
                            assertFalse("Spatial handles must not intercept physics mode", positions());
                            assertNull(overlay.hit(h.x, h.y, true));
                            modes.setMode(games.pixscape.studio.service.StudioEditingMode.NORMAL, 0);
                            ctx.selectionService().selectOnly(e);
                        }
                        case 38 -> {
                            root.child("DONE").writeString("PASS: six handles, overlap, zoom, locked target, history, cancellation, Shift, independent physics", false);
                            Gdx.app.exit();
                        }
                    }
                } catch (Throwable failure) {
                    stop(failure);
                }
            }

            void arrange(boolean coincident) throws Exception {
                SpatialFootprintComponent foot = ctx.world().getMapper(SpatialFootprintComponent.class).get(e);
                foot.localOffsetXPx = 0;
                foot.localOffsetYPx = 0;
                foot.radiusPx = 30;
                volume().width = 120;
                volume().height = 100;
                volume().x = -60;
                volume().y = coincident ? -50 : 30;
                volume().pivotX = coincident ? 0 : -50;
                volume().pivotY = 0;
                // The inspector overlays the right side of the grid viewport in this fixed-size fixture.
                if (overlay != null && positions()) {
                    var canvas = studio.getCanvas();
                    Vector2 actual = screen(handle(4));
                    Vector2 desired = new Vector2(570, 440);
                    canvas.getGridStage().screenToStageCoordinates(actual);
                    canvas.getGridStage().screenToStageCoordinates(desired);
                    var camera = canvas.getGridStage().getCamera();
                    canvas.focusCameraAt(camera.position.x + actual.x - desired.x, camera.position.y + actual.y - desired.y);
                }
            }

            void checkGestures() throws Exception {
                SpatialFootprintComponent foot = ctx.world().getMapper(SpatialFootprintComponent.class).get(e);
                float transformX = ctx.world().getMapper(TransformComponent.class).get(e).x;
                for (int index = 0; index < 6; index++) {
                    System.out.println("CHECK handle=" + index + " zoom=" + ((OrthographicCamera) studio.getCanvas().getGridStage().getCamera()).zoom);
                    arrange(false);
                    positions();
                    var before = foot.copy();
                    var rect = volume().copy();
                    int cursor = ctx.historyManager().getCursor();
                    Vector2 h = handle(index), target = new Vector2(h).add(24, 12);
                    press(h);
                    assertEquals(index, activeHandle());
                    drag(target);
                    studio.getUiStage().act(0);
                    String fieldName = index >= 4 ? "spatial-footprint-" + (index == 4 ? 2 : 1)
                            : "actor-spatial-rectangle-" + (index == 0 ? 4 : index == 1 ? 2 : index - 2);
                    FloatField field = studio.getUiStage().getRoot().findActor(fieldName);
                    float value = switch (index) {
                        case 0 -> volume().pivotX;
                        case 1 -> volume().x;
                        case 2 -> volume().width;
                        case 3 -> volume().height;
                        case 4 -> foot.localOffsetXPx;
                        default -> foot.radiusPx;
                    };
                    assertEquals(value, Float.parseFloat(field.getText()), .02f);
                    release(target);
                    assertEquals("One command per gesture", cursor + 1, ctx.historyManager().getCursor());
                    var afterFoot = foot.copy();
                    var afterRect = volume().copy();
                    if (index >= 4) {
                        assertFalse(sameFoot(before, foot));
                        assertTrue("Footprint preserves rectangle and relative pivot", rect.same(volume()));
                    } else {
                        assertTrue("Rectangle/pivot preserves footprint", sameFoot(before, foot));
                        assertFalse(rect.same(volume()));
                        if (index == 0) {
                            assertEquals(rect.x, volume().x, 0);
                            assertEquals(rect.y, volume().y, 0);
                            assertEquals(rect.width, volume().width, 0);
                            assertEquals(rect.height, volume().height, 0);
                        } else {
                            assertEquals(rect.pivotX, volume().pivotX, 0);
                            assertEquals(rect.pivotY, volume().pivotY, 0);
                        }
                    }
                    ctx.historyManager().undo();
                    assertTrue(sameFoot(before, foot));
                    assertTrue(rect.same(volume()));
                    ctx.historyManager().redo();
                    assertTrue(sameFoot(afterFoot, foot));
                    assertTrue(afterRect.same(volume()));
                    for (boolean escape : new boolean[]{true, false}) {
                        assertTrue("Positions after history, handle=" + index, positions());
                        h = handle(index);
                        press(h);
                        drag(new Vector2(h).add(18, -15));
                        if (escape) {
                            studio.getUiStage().keyDown(Input.Keys.ESCAPE);
                        } else {
                            studio.getUiStage().cancelTouchFocus();
                        }
                        assertFalse(overlay.isDragging());
                        assertTrue(sameFoot(afterFoot, foot));
                        assertTrue(afterRect.same(volume()));
                        assertEquals(cursor + 1, ctx.historyManager().getCursor());
                    }
                    ctx.historyManager().undo();
                }
                // Cross another handle without releasing: touch focus must retain the first target.
                arrange(false);
                positions();
                Vector2 h = handle(0), crossing = handle(1);
                var rectangle = volume().copy();
                press(h);
                drag(crossing);
                assertEquals(0, activeHandle());
                drag(new Vector2(crossing).add(15, 0));
                assertEquals(0, activeHandle());
                assertEquals(rectangle.x, volume().x, 0);
                studio.getUiStage().cancelTouchFocus();
                assertTrue(rectangle.same(volume()));
                // The real Input interface supplies Shift at press, just as for ordinary native input.
                Input original = Gdx.input;
                try {
                    Gdx.input = (Input) java.lang.reflect.Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                            (proxy, method, arguments) -> method.getName().equals("isKeyPressed") && ((Integer) arguments[0]) == Input.Keys.SHIFT_LEFT
                                    ? true : method.invoke(original, arguments));
                    positions();
                    h = handle(4);
                    Vector2 end = new Vector2(h).add(40, 0);
                    float zoom = ((OrthographicCamera) studio.getCanvas().getGridStage().getCamera()).zoom;
                    press(h);
                    drag(end);
                    release(end);
                    assertEquals(40 * zoom * .1f, foot.localOffsetXPx, .15f);
                    ctx.historyManager().undo();
                } finally {
                    Gdx.input = original;
                }
                assertEquals(transformX, ctx.world().getMapper(TransformComponent.class).get(e).x, 0);
                arrange(false);
            }

            void checkCoincident() throws Exception {
                // The agreed simple workflow: move the first coincident geometry to expose the next.
                arrange(true);
                for (int index : new int[]{0, 1, 4}) {
                    positions();
                    Vector2 h = handle(index), end = new Vector2(h).add(index == 1 ? -45 : 45, 20);
                    press(h);
                    assertEquals(index, activeHandle());
                    drag(end);
                    release(end);
                }
                capture("coincident-separated-by-dragging");
                arrange(false);
            }

            void checkPartialOverlap() throws Exception {
                arrange(true);
                var foot = ctx.world().getMapper(SpatialFootprintComponent.class).get(e);
                volume().pivotX = 6;
                volume().x -= 6;
                for (int index : new int[]{0, 1, 4}) {
                    positions();
                    Vector2 h = handle(index);
                    press(h);
                    assertEquals("Nearest handle wins despite overlapping hit discs", index, activeHandle());
                    studio.getUiStage().cancelTouchFocus();
                }
                assertEquals(0, foot.localOffsetXPx, 0);
                arrange(false);
            }

            int activeHandle() throws Exception {
                Field field = ActorSpatialRectangleCanvasEditor.class.getDeclaredField("handle");
                field.setAccessible(true);
                return field.getInt(overlay);
            }

            void capture(String name) {
                com.badlogic.gdx.graphics.Pixmap capture = com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(0, 0, 1200, 850);
                try {
                    com.badlogic.gdx.graphics.PixmapIO.writePNG(root.child(name + ".png"), capture, -1, true);
                } finally {
                    capture.dispose();
                }
            }

            boolean sameFoot(SpatialFootprintComponent a, SpatialFootprintComponent b) {
                return games.pixscape.studio.history.commands.EditSpatialFootprintCommand.Snapshot.capture(a)
                        .equals(games.pixscape.studio.history.commands.EditSpatialFootprintCommand.Snapshot.capture(b));
            }

            boolean positions() throws Exception {
                Method m = ActorSpatialRectangleCanvasEditor.class.getDeclaredMethod("positions");
                m.setAccessible(true);
                return (Boolean) m.invoke(overlay);
            }

            Vector2 handle(int i) throws Exception {
                Field f = ActorSpatialRectangleCanvasEditor.class.getDeclaredField("handles");
                f.setAccessible(true);
                return new Vector2(((Vector2[]) f.get(overlay))[i]);
            }

            Vector2 screen(Vector2 point) {
                return studio.getUiStage().stageToScreenCoordinates(new Vector2(point));
            }

            void press(Vector2 p) {
                Vector2 s = screen(p);
                assertTrue("Stage touch at " + s + " selected=" + ctx.selectionService().getFirstSelectedEntityId(), studio.getUiStage().touchDown(Math.round(s.x), Math.round(s.y), 0, Input.Buttons.LEFT));
                assertTrue("Native Stage picked a spatial handle", overlay.isDragging());
            }

            void drag(Vector2 p) {
                Vector2 s = screen(p);
                studio.getUiStage().touchDragged(Math.round(s.x), Math.round(s.y), 0);
            }

            void release(Vector2 p) {
                Vector2 s = screen(p);
                studio.getUiStage().touchUp(Math.round(s.x), Math.round(s.y), 0, Input.Buttons.LEFT);
            }

            void stop(Throwable error) {
                capture("failure");
                failed[0] = error;
                error.printStackTrace();
                Gdx.app.exit();
            }

            @Override
            public void resize(int w, int h) {
                if (studio != null) studio.resize(w, h);
            }

            @Override
            public void dispose() {
                if (studio != null) studio.dispose();
            }
        }, gl);
        if (failed[0] != null) throw new AssertionError(failed[0]);
    }
}
