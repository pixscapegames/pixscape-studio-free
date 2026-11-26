package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.TiledRenderState;
import games.pixscape.runtime.render.lighting.RoundedWallDescription;
import games.pixscape.runtime.render.lighting.TileSurfaceDescription;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.commands.AddTiledMapCommand;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.spatial.CornerProfileEditSession;
import games.pixscape.studio.system.GizmoSystem;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

/** Real canvas Stage capture, frame processing and saved-scene lifecycle in an isolated home. */
public class RoundedCornerCanvasGlTest {
    private int mouseX = 10, mouseY = 150;
    private boolean escape;

    @Test
    public void roundedVolumesAndVertexGesturesDoNotRequireSceneLighting() throws Exception {
        runCornerScene(false);
    }

    @Test
    public void lightingCycleKeepsGesturesAndPublishesEditedShadows() throws Exception {
        runCornerScene(true);
    }

    private void runCornerScene(boolean lightingCycle) throws Exception {
        withStudio(900, (studio, root) -> {
            var file = project(studio, root);
            int asset = tile(root, lightingCycle);
            open(studio, file);
            ((TopMenuBar) field(studio, "topMenuBar")).beginProject();
            WorldCanvas canvas = studio.getCanvas();
            var context = canvas.getAttachedSceneContext();
            var history = context.historyManager();
            AtomicInteger created = new AtomicInteger(-1);
            history.execute(new AddTiledMapCommand(context.layerService(), context.layerService().getLayerEntity(0),
                    10, 10, TiledProjection.ISO, 256, 128, 8, created::set));
            int entity = created.get();
            studio.render();
            var tiled = context.world().getMapper(TiledLayerComponent.class).get(entity);
            tiled.spatialEnabled = tiled.data.spatialEnabled = true;
            tiled.data.setTile(1, 1, asset);
            tiled.data.setTile(5, 5, asset);
            games.pixscape.studio.helper.TiledSparseStorageHelper.setTile(tiled, 1, 1, asset, (byte) 0);
            games.pixscape.studio.helper.TiledSparseStorageHelper.setTile(tiled, 5, 5, asset, (byte) 0);
            studio.getSceneService().requestTiledFallbackValidation();
            studio.getSceneService().ensureSceneAtlasInputHasAsset("scene1", asset);
            studio.getSceneService().requestAsyncPack("scene1");
            long deadline = System.nanoTime() + 15_000_000_000L;
            while (canvas.getAtlasService().hasAsyncPackQueuedOrRunningFor("scene1")) {
                assertTrue("Atlas publication deadline", System.nanoTime() < deadline);
                Thread.sleep(10);
                studio.render();
            }
            studio.render();
            var blocks = context.world().getMapper(SpatialBlocksComponent.class).create(entity);
            for (int cell : new int[]{1, 5}) {
                for (int axis = 0; axis < 2; axis++) {
                    SpatialBlockData wall = new SpatialBlockData();
                    wall.id = blocks.allocateNextSpatialBlockId();
                    wall.structureId = cell;
                    wall.x = wall.y = cell;
                    wall.width = axis == 0 ? 3 : .25f;
                    wall.depth = axis == 0 ? .25f : 3;
                    wall.height = 150;
                    wall.shadowCaster = wall.lightOccluder = true;
                    wall.beginAuthoredLinkedTileRefs();
                    wall.addLinkedTileRef(cell, cell, asset);
                    blocks.blocks.add(wall);
                }
            }
            blocks.revision++;
            TileSurfaceDescription description = new TileSurfaceDescription();
            description.assetId = asset;
            description.rounded = new RoundedWallDescription();
            tiled.lightingDescriptions.add(description);
            context.sceneMeta().lightingEnabled = false;
            context.world().getSystem(games.pixscape.studio.system.StudioRenderSubmitSystem.class).setLightingEnabled(false);
            context.selectionService().setTiledMapEditingTarget(entity, games.pixscape.studio.service.SelectionService.SelectionSource.TREE);
            context.spatialBlockSelectionService().selectBlock(entity, 1);
            canvas.focusCameraAt(128, 290);
            studio.render();
            studio.render();
            if (lightingCycle) {
                verifyLightingCycle(studio, canvas, entity, asset);
                return;
            }
            var state = (TiledRenderState) field(canvas, "tiledState");
            var gizmo = context.world().getSystem(GizmoSystem.class);
            assertFalse(state.lightingEnabled);
            checkVolumeMenu(studio, canvas, entity);
            studio.render();
            capture("overview-selected");
            assertTrue(context.spatialBlockSelectionService().isEditingActive());
            assertTrue("Tile native resource must be prepared", state.isRenderableRef(tiled.data.tiledRenderRefForTile(1, 1)));
            assertEquals(2, gizmo.cornerOverview().size());
            var sync = context.world().getSystem(games.pixscape.runtime.system.RenderTiledSyncSystem.class);
            var profiles = (games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles) field(sync, "tilesetProfiles");
            assertNotNull("Native tileset profile", profiles.profileForTileAsset(asset));
            var atlas = (games.pixscape.runtime.service.AtlasRuntimeService) field(sync, "atlasRuntimeService");
            assertNotNull("Published tile image", atlas.resolveBinding(asset, tiled.atlasTag));
            var edit = new CornerProfileEditSession();
            assertNull(edit.inspect(context.world(), state, history,
                    new CornerProfileEditSession.Target(entity, 1, 1, asset, 1)));
            assertTrue(edit.openExisting());
            context.spatialBlockSelectionService().setCornerProfile(edit);
            var cornerMenu = rebuildMenu(canvas);
            assertNull(menuItem(cornerMenu, "Applies to all occurrences of this asset in this Map tile layer"));
            assertNotNull(menuItem(cornerMenu, "Remove corner profile (all occurrences of this asset in this map)"));
            cornerMenu.showMenu(studio.getUiStage(), 450, 500);
            studio.render();
            capture("corner-menu");
            cornerMenu.remove();
            Input original = Gdx.input;
            Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> mouseX;
                        case "getY" -> mouseY;
                        case "isKeyPressed" -> false;
                        case "isKeyJustPressed" -> escape && (Integer) args[0] == Input.Keys.ESCAPE;
                        default -> method.invoke(original, args);
                    });
            try {
                var camera = (OrthographicCamera) canvas.getGridStage().getCamera();
                for (float zoom : new float[]{.5f, 1, 2}) {
                    camera.zoom = zoom;
                    camera.update();
                    studio.render();
                    for (int vertex : new int[]{2, 3, 4}) {
                        int cursor = history.getCursor();
                        float x = edit.screenX(vertex), y = edit.screenY(vertex);
                        var start = canvas.getGridStage().stageToScreenCoordinates(new Vector2(x, y));
                        pointer(start);
                        canvas.getGridStage().mouseMoved(mouseX, mouseY);
                        studio.render();
                        assertEquals(vertex, edit.highlightedVertex());
                        assertTrue(canvas.getGridStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT));
                        studio.render();
                        assertTrue("Canvas must capture the vertex at zoom " + zoom, edit.dragging());
                        var end = canvas.getGridStage().stageToScreenCoordinates(new Vector2(x + 2, y + 1));
                        pointer(end);
                        canvas.getGridStage().touchDragged(mouseX, mouseY, 0);
                        studio.render();
                        assertTrue(edit.reason(), edit.valid());
                        assertEquals(cursor, history.getCursor());
                        assertEquals(x + 2, edit.screenX(vertex), zoom);
                        assertEquals(y + 1, edit.screenY(vertex), zoom);
                        if (zoom == 1 && vertex == 3) {
                            capture("vertex-drag");
                        }
                        canvas.getGridStage().touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT);
                        studio.render();
                        assertEquals("zoom=" + zoom + " vertex=" + vertex + " reason=" + edit.reason() + " x=" + edit.screenX(vertex) + " initial=" + x, cursor + 1, history.getCursor());
                        var after = tiled.lightingDescriptions.first().rounded.copy();
                        history.undo();
                        studio.render();
                        history.redo();
                        studio.render();
                        assertTrue(after.sameAs(tiled.lightingDescriptions.first().rounded));
                        assertEquals(2, gizmo.cornerOverview().size());
                        float[] first = gizmo.cornerOverview().corner(0).support().outerXY;
                        float[] second = gizmo.cornerOverview().corner(1).support().outerXY;
                        for (int coordinate = 0; coordinate < first.length; coordinate++) {
                            assertEquals("Both occurrences must use the committed profile", 512,
                                    second[coordinate] - first[coordinate], .001f);
                        }
                    }
                }
                var saved = tiled.lightingDescriptions.first().rounded.copy();
                int cursor = history.getCursor();
                var start = canvas.getGridStage().stageToScreenCoordinates(new Vector2(edit.screenX(3), edit.screenY(3)));
                pointer(start);
                canvas.getGridStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT);
                studio.render();
                pointer(start.cpy().add(2, -1));
                canvas.getGridStage().touchDragged(mouseX, mouseY, 0);
                studio.render();
                escape = true;
                studio.render();
                escape = false;
                canvas.getGridStage().touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT);
                studio.render();
                assertEquals(cursor, history.getCursor());
                assertTrue(saved.sameAs(tiled.lightingDescriptions.first().rounded));
                context.spatialBlockSelectionService().clear();
                studio.render();
                assertFalse(edit.active());
                context.spatialBlockSelectionService().selectBlock(entity, 1);
                studio.render();
                assertEquals(2, gizmo.cornerOverview().size());
                context.spatialBlockSelectionService().clearSelectionOnly();
                studio.render();
                assertEquals(2, gizmo.cornerOverview().size());
                capture("overview-deselected");
                var corner = gizmo.cornerOverview().corner(0);
                float handleX = corner.x(3), handleY = corner.y(3) - 4 * camera.zoom;
                assertNull("This press is outside the body", gizmo.cornerOverview().pickBody(entity, handleX, handleY, state));
                pointer(canvas.getGridStage().stageToScreenCoordinates(new Vector2(handleX, handleY)));
                canvas.getGridStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT);
                studio.render();
                assertTrue("Handle tolerance takes priority over body picking", context.spatialBlockSelectionService().cornerProfile().dragging());
                canvas.getGridStage().touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT);
                studio.render();
                assertEquals(cursor, history.getCursor());
                context.spatialBlockSelectionService().clearSelectionOnly();
                int stable = context.identityRegistry().ensureStableId(entity);
                SceneService.saveScene(context.world(), root.child("scenes/scene1.json"), false);
                ProjectConfig.ProjectIO.saveProject(ProjectConfig.getInstance(), file);
                open(studio, file);
                ((TopMenuBar) field(studio, "topMenuBar")).beginProject();
                context = canvas.getAttachedSceneContext();
                entity = context.identityRegistry().findByStableId(stable);
                context.selectionService().setTiledMapEditingTarget(entity, games.pixscape.studio.service.SelectionService.SelectionSource.TREE);
                context.spatialBlockSelectionService().enterMap(entity);
                canvas.focusCameraAt(128, 290);
                studio.render();
                studio.render();
                tiled = context.world().getMapper(TiledLayerComponent.class).get(entity);
                assertTrue(saved.sameAs(tiled.lightingDescriptions.first().rounded));
                assertEquals(2, context.world().getSystem(GizmoSystem.class).cornerOverview().size());
                capture("reloaded");
            } finally {
                Gdx.input = original;
            }
        });
    }

    private void verifyLightingCycle(StudioApplicationAdapter studio, WorldCanvas canvas, int entity, int asset) throws Exception {
        var context = canvas.getAttachedSceneContext();
        var history = context.historyManager();
        var world = context.world();
        var tiled = world.getMapper(TiledLayerComponent.class).get(entity);
        var blocks = world.getMapper(SpatialBlocksComponent.class).get(entity);
        var selection = context.spatialBlockSelectionService();
        var submit = world.getSystem(games.pixscape.studio.system.StudioRenderSubmitSystem.class);
        var state = (TiledRenderState) field(canvas, "tiledState");
        var gizmo = world.getSystem(GizmoSystem.class);
        int lightEntity = canvas.getEditorOps().createPointLight(-80, 150);
        assertTrue(lightEntity >= 0);
        var light = world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).get(lightEntity);
        light.radius = 1500;
        light.height = 300;
        light.intensity = 2;
        light.r = light.g = light.b = 1;
        light.shadowResolution = 512;
        light.shadowQuality = 1;
        settle(studio);
        context.selectionService().setTiledMapEditingTarget(entity, games.pixscape.studio.service.SelectionService.SelectionSource.TREE);
        settle(studio);
        selection.selectBlock(entity, 1);
        settle(studio);
        assertTrue("Fixture must enter spatial editing after creating the light", selection.isEditingActive());
        var edit = new CornerProfileEditSession();
        assertNull(edit.inspect(world, state, history, new CornerProfileEditSession.Target(entity, 1, 1, asset, 1)));
        assertTrue(edit.openExisting());
        selection.setCornerProfile(edit);
        Input original = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> mouseX;
                    case "getY" -> mouseY;
                    case "isKeyPressed" -> false;
                    case "isKeyJustPressed" -> escape && (Integer) args[0] == Input.Keys.ESCAPE;
                    default -> method.invoke(original, args);
                });
        float[] lastLitShadow = null;
        float[] lastLitCaster = null;
        try {
            for (int phase = 0; phase < 3; phase++) {
                boolean enabled = phase != 1;
                String name = new String[]{"on", "off", "reactivated"}[phase];
                String beforeToggle = authored(tiled, blocks);
                float[] outline = gizmo.cornerOverview().corner(0).support().outerXY.clone();
                float[] wire = gizmo.cornerOverview().wire(blocks.blocks.first().structureId).clone();
                history.execute(new games.pixscape.studio.history.commands.SetSceneLightingCommand(
                        world, context.sceneMeta(), enabled, submit::setLightingEnabled, null));
                settle(studio);
                settle(studio);
                assertEquals(enabled, state.lightingEnabled);
                assertEquals("Lighting must not mutate authored geometry", beforeToggle, authored(tiled, blocks));
                assertArrayEquals(outline, gizmo.cornerOverview().corner(0).support().outerXY, .001f);
                assertArrayEquals(wire, gizmo.cornerOverview().wire(blocks.blocks.first().structureId), .001f);
                assertTrue("An open profile must survive the lighting toggle", edit.active());
                history.undo();
                settle(studio);
                assertEquals(!enabled, state.lightingEnabled);
                assertEquals(beforeToggle, authored(tiled, blocks));
                history.redo();
                settle(studio);
                assertEquals(enabled, state.lightingEnabled);
                assertEquals(beforeToggle, authored(tiled, blocks));
                if (phase == 2) {
                    assertPublishedCorner(state, entity, gizmo);
                    var caster = state.lightingMaps.get(entity).roundedSupport(1, 1).texturedCaster.vertices;
                    assertFalse("Unlit edits must replace the old shadow geometry", java.util.Arrays.equals(lastLitCaster, caster));
                    float[] shadow = shadowPixels(submit, "shadow-reactivated");
                    int changed = differences(lastLitShadow, shadow);
                    assertTrue("The GPU shadow atlas must use the unlit edit, changed=" + changed, changed > 0);
                    System.out.println("Lighting cycle: GPU shadow pixels changed after unlit edits=" + changed);
                }
                assertVolumeOverlay(gizmo, blocks, tiled);
                capture("cycle-" + name + "-editing");
                float dx = phase == 2 ? -4 : 4, dy = phase == 2 ? -2 : 2;
                for (int vertex : phase == 2 ? new int[]{3, 2} : new int[]{2, 3}) {
                    int cursor = history.getCursor();
                    var before = tiled.lightingDescriptions.first().rounded.copy();
                    float x = edit.screenX(vertex), y = edit.screenY(vertex);
                    pointer(canvas.getGridStage().stageToScreenCoordinates(new Vector2(x, y)));
                    canvas.getGridStage().mouseMoved(mouseX, mouseY);
                    settle(studio);
                    assertEquals(vertex, edit.highlightedVertex());
                    assertTrue(canvas.getGridStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT));
                    settle(studio);
                    assertTrue("Vertex capture with lighting " + name, edit.dragging());
                    pointer(canvas.getGridStage().stageToScreenCoordinates(new Vector2(x + dx, y + dy)));
                    canvas.getGridStage().touchDragged(mouseX, mouseY, 0);
                    settle(studio);
                    assertTrue(edit.reason(), edit.valid());
                    assertEquals(cursor, history.getCursor());
                    assertEquals(x + dx, edit.screenX(vertex), 1);
                    capture("cycle-" + name + "-drag-" + vertex);
                    canvas.getGridStage().touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT);
                    settle(studio);
                    assertEquals(cursor + 1, history.getCursor());
                    var after = tiled.lightingDescriptions.first().rounded.copy();
                    assertFalse(before.sameAs(after));
                    float[] changed = gizmo.cornerOverview().corner(0).support().outerXY.clone();
                    history.undo();
                    settle(studio);
                    assertTrue(before.sameAs(tiled.lightingDescriptions.first().rounded));
                    history.redo();
                    settle(studio);
                    assertTrue(after.sameAs(tiled.lightingDescriptions.first().rounded));
                    assertArrayEquals(changed, gizmo.cornerOverview().corner(0).support().outerXY, 0);
                    if (enabled) {
                        assertPublishedCorner(state, entity, gizmo);
                    }
                }
                selection.clearSelectionOnly();
                settle(studio);
                assertFalse(edit.active());
                assertVolumeOverlay(gizmo, blocks, tiled);
                capture("cycle-" + name + "-deselected");
                int beforePick = history.getCursor();
                var handle = gizmo.cornerOverview().corner(0);
                pointer(canvas.getGridStage().stageToScreenCoordinates(new Vector2(handle.x(3), handle.y(3))));
                assertTrue(canvas.getGridStage().touchDown(mouseX, mouseY, 0, Input.Buttons.LEFT));
                settle(studio);
                assertTrue("Picking the displayed handle must reopen the editor with lighting " + name,
                        selection.cornerProfile().dragging());
                canvas.getGridStage().touchUp(mouseX, mouseY, 0, Input.Buttons.LEFT);
                settle(studio);
                assertEquals("A selection click must not create history", beforePick, history.getCursor());
                selection.selectBlock(entity, 1);
                selection.toggleBlock(entity, 2);
                var menu = rebuildMenu(canvas);
                int cursor = history.getCursor();
                int actors = studio.getUiStage().getActors().size;
                clickMenu(menuItem(menu, "Link volume"));
                settle(studio);
                assertEquals(1, blocks.links.size);
                assertEquals(cursor + 1, history.getCursor());
                assertEquals("Link volume", history.peekUndoLabel());
                assertEquals("No confirmation or preview", actors, studio.getUiStage().getActors().size);
                history.undo();
                settle(studio);
                assertEquals(0, blocks.links.size);
                history.redo();
                settle(studio);
                assertEquals(1, blocks.links.size);
                assertVolumeOverlay(gizmo, blocks, tiled);
                menu = rebuildMenu(canvas);
                clickMenu(menuItem(menu, "Unlink volume"));
                settle(studio);
                assertEquals(0, blocks.links.size);
                assertEquals(cursor + 2, history.getCursor());
                assertEquals("Unlink volume", history.peekUndoLabel());
                history.undo();
                settle(studio);
                assertEquals(1, blocks.links.size);
                history.redo();
                settle(studio);
                assertEquals(0, blocks.links.size);
                assertVolumeOverlay(gizmo, blocks, tiled);
                capture("cycle-" + name + "-selected");
                var camera = (OrthographicCamera) canvas.getGridStage().getCamera();
                float previousZoom = camera.zoom;
                camera.zoom = 1.5f;
                camera.update();
                settle(studio);
                capture("cycle-" + name + "-overview");
                camera.zoom = previousZoom;
                camera.update();
                settle(studio);
                if (enabled) {
                    assertPublishedCorner(state, entity, gizmo);
                    lastLitCaster = state.lightingMaps.get(entity).roundedSupport(1, 1).texturedCaster.vertices.clone();
                    float[] shadow = shadowPixels(submit, "shadow-" + name + "-edited");
                    if (phase == 2) {
                        assertEquals("Returning vertices to their earlier positions must restore the same GPU shadows", 0,
                                differences(lastLitShadow, shadow));
                    }
                    lastLitShadow = shadow;
                }
                selection.selectBlock(entity, 1);
                edit = new CornerProfileEditSession();
                assertNull(edit.inspect(world, state, history, new CornerProfileEditSession.Target(entity, 1, 1, asset, 1)));
                assertTrue(edit.openExisting());
                selection.setCornerProfile(edit);
                settle(studio);
                System.out.println("Lighting cycle " + name + ": selection, overlays, two vertex drags, Link/Unlink, Undo/Redo passed");
            }
        } finally {
            Gdx.input = original;
        }
    }

    private static void settle(StudioApplicationAdapter studio) {
        // Picking commits after geometry preparation; the following frame publishes that geometry.
        studio.render();
        studio.render();
    }

    private static String authored(TiledLayerComponent tiled, SpatialBlocksComponent blocks) {
        var json = new com.badlogic.gdx.utils.Json();
        return json.toJson(blocks.blocks) + json.toJson(blocks.links) + json.toJson(tiled.lightingDescriptions)
                + ":" + tiled.defaultTileHeight + ":" + tiled.data.defaultTileHeight;
    }

    private static void assertVolumeOverlay(GizmoSystem gizmo, SpatialBlocksComponent blocks, TiledLayerComponent tiled) {
        var overview = gizmo.cornerOverview();
        assertEquals(2, overview.size());
        for (int i = 0; i < overview.size(); i++) {
            var corner = overview.corner(i);
            float[] obsoleteCorner = new float[2];
            tiled.data.projectSpatialPoint(corner.minX(), corner.minY(), corner.base() + corner.height() * .5f, obsoleteCorner, 0);
            var wire = overview.wire(blocks.blocks.get(i * 2).structureId);
            for (int j = 0; j < wire.length; j += 4) {
                assertTrue("The replaced right-angle vertical must not survive in the wire overlay",
                        com.badlogic.gdx.math.Intersector.distanceSegmentPoint(wire[j], wire[j + 1], wire[j + 2], wire[j + 3],
                                obsoleteCorner[0], obsoleteCorner[1]) > .01f);
            }
        }
        for (var wall : blocks.blocks) {
            assertTrue(overview.replacesWall(wall.id));
            assertNotNull(overview.wire(wall.structureId));
            assertTrue("Keep useful straight wall continuations", overview.wire(wall.structureId).length > 0);
        }
    }

    private static void assertPublishedCorner(TiledRenderState state, int entity, GizmoSystem gizmo) {
        var published = state.lightingMaps.get(entity);
        assertNotNull(published);
        for (int i = 0; i < gizmo.cornerOverview().size(); i++) {
            var corner = gizmo.cornerOverview().corner(i);
            var support = published.roundedSupport(corner.target().gx(), corner.target().gy());
            assertNotNull(support);
            assertArrayEquals(corner.support().outerXY, support.outerXY, 0);
            assertArrayEquals(corner.support().texturedCaster.vertices, support.texturedCaster.vertices, 0);
        }
    }

    private static void clickMenu(com.kotcrab.vis.ui.widget.MenuItem item) {
        assertNotNull(item);
        assertFalse(item.isDisabled());
        var event = new com.badlogic.gdx.scenes.scene2d.InputEvent();
        for (var listener : item.getListeners()) {
            if (listener instanceof com.badlogic.gdx.scenes.scene2d.utils.ClickListener click) {
                click.clicked(event, 1, 1);
            }
        }
    }

    private static int differences(float[] before, float[] after) {
        assertEquals(before.length, after.length);
        int different = 0;
        for (int i = 0; i < before.length; i++) {
            if (Math.abs(before[i] - after[i]) > .000001f) {
                different++;
            }
        }
        return different;
    }

    private static float[] shadowPixels(games.pixscape.studio.system.StudioRenderSubmitSystem submit, String name) throws Exception {
        var renderer = submit.lightingRenderer();
        assertTrue("Shadow casters must reach the renderer", renderer.selectedCasters > 0);
        assertTrue("The GPU must draw the shadows", renderer.shadowDraws > 0);
        var atlas = (com.badlogic.gdx.graphics.glutils.FrameBuffer) field(renderer, "atlas");
        assertNotNull(atlas);
        int width = atlas.getWidth(), height = atlas.getHeight();
        var buffer = com.badlogic.gdx.utils.BufferUtils.newFloatBuffer(width * height);
        atlas.begin();
        try {
            Gdx.gl30.glReadBuffer(com.badlogic.gdx.graphics.GL30.GL_COLOR_ATTACHMENT0);
            Gdx.gl.glReadPixels(0, 0, width, height, com.badlogic.gdx.graphics.GL30.GL_RED, com.badlogic.gdx.graphics.GL20.GL_FLOAT, buffer);
            assertEquals(com.badlogic.gdx.graphics.GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            atlas.end();
        }
        float[] pixels = new float[width * height];
        buffer.get(pixels);
        int occupied = 0;
        for (float value : pixels) {
            assertTrue(Float.isFinite(value));
            if (value < .999f) {
                occupied++;
            }
        }
        assertTrue("Shadow atlas must contain actual caster depth", occupied > 0);
        String directory = System.getenv("PIXSCAPE_CORNER_CAPTURE_DIR");
        if (directory != null) {
            Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            try {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        float depth = Math.max(0, Math.min(1, pixels[y * width + x]));
                        image.setColor(depth, depth, depth, 1);
                        image.drawPixel(x, height - 1 - y);
                    }
                }
                PixmapIO.writePNG(new FileHandle(directory).child(name + ".png"), image);
            } finally {
                image.dispose();
            }
        }
        System.out.println(name + ": GPU casters=" + renderer.selectedCasters + ", draws=" + renderer.shadowDraws + ", depth pixels=" + occupied);
        return pixels;
    }

    private static com.kotcrab.vis.ui.widget.PopupMenu rebuildMenu(WorldCanvas canvas) throws Exception {
        Object contextMenu = field(canvas, "contextMenu");
        var build = contextMenu.getClass().getDeclaredMethod("buildMenu");
        build.setAccessible(true);
        build.invoke(contextMenu);
        return (com.kotcrab.vis.ui.widget.PopupMenu) field(contextMenu, "menu");
    }

    private static com.kotcrab.vis.ui.widget.MenuItem menuItem(com.kotcrab.vis.ui.widget.PopupMenu menu, String text) {
        for (var child : menu.getChildren()) {
            if (child instanceof com.kotcrab.vis.ui.widget.MenuItem item && item.getText().toString().equals(text)) {
                return item;
            }
        }
        return null;
    }

    private static void checkVolumeMenu(StudioApplicationAdapter studio, WorldCanvas canvas, int entity) throws Exception {
        var selection = canvas.getAttachedSceneContext().spatialBlockSelectionService();
        selection.clearSelectionOnly();
        var menu = rebuildMenu(canvas);
        assertNotNull(menuItem(menu, "Add volume"));
        assertTrue(menuItem(menu, "Delete selected volume").isDisabled());
        selection.selectBlock(entity, 1);
        menu = rebuildMenu(canvas);
        assertFalse(menuItem(menu, "Delete selected volume").isDisabled());
        assertNull(menuItem(menu, "Applies to all occurrences of this asset in this Map tile layer"));
        selection.toggleBlock(entity, 2);
        menu = rebuildMenu(canvas);
        assertNotNull(menuItem(menu, "Link volume"));
        assertNotNull(menuItem(menu, "Match thickness"));
        menu.showMenu(studio.getUiStage(), 450, 500);
        studio.render();
        capture("volume-menu");
        menu.remove();
        selection.selectBlock(entity, 1);
    }

    private void pointer(Vector2 point) {
        mouseX = Math.round(point.x);
        mouseY = Math.round(point.y);
    }

    private static int tile(FileHandle root, boolean opaque) {
        var db = AssetMetaDatabase.load(root.child("assets.json"));
        var set = (TilesetAssetMeta) db.registerIfAbsent(AssetType.TILESET, "tiles/corner", "orig/corner", AssetMeta.AssetScope.USER);
        set.tileWidth = 256;
        set.tileHeight = 512;
        set.referenceCellWidth = 256;
        set.referenceCellHeight = 128;
        set.projection = TiledProjection.ISO;
        set.anchor = TilesetAnchor.BOTTOM_CENTER;
        var tile = (TileAssetMeta) db.registerIfAbsent(AssetType.TILE, "tiles/corner/0", "orig/corner/0.png", AssetMeta.AssetScope.USER);
        db.updateSourceRelPath(tile.id(), "orig/tiles/corner/corner__a" + tile.id() + ".png");
        tile.tilesetId = set.id();
        tile.sheetIndex = 0;
        Pixmap pixels = new Pixmap(256, 512, Pixmap.Format.RGBA8888);
        try {
            pixels.setColor(opaque ? .6f : .2f, opaque ? .65f : .25f, opaque ? .7f : .3f, opaque ? 1 : .2f);
            pixels.fill();
            PixmapIO.writePNG(root.child(tile.sourceRelPath()), pixels);
        } finally {
            pixels.dispose();
        }
        db.save(root.child("assets.json"));
        return tile.id();
    }

    private static void capture(String name) {
        String directory = System.getenv("PIXSCAPE_CORNER_CAPTURE_DIR");
        if (directory == null) {
            return;
        }
        Pixmap pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            PixmapIO.writePNG(new FileHandle(directory).child(name + ".png"), pixels, -1, true);
        } finally {
            pixels.dispose();
        }
    }
}
