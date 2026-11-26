package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.MenuItem;
import com.kotcrab.vis.ui.widget.PopupMenu;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialLinkProposal;
import games.pixscape.studio.ui.contextmenu.StudioContextMenu;

import static org.junit.Assert.*;

/**
 * Real native direct menu, vertical dialog, linked fixture publication and actor passage, called by the GL smoke.
 */
final class SpatialLinkGlChecks {
    static SpatialBlocksComponent run() {
        ProjectConfig previous = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Link fixture");
        config.getCurrentSceneMeta().pixelsPerMeter = 32;
        ProjectConfig.setInstance(config);
        VisUI.load(new com.badlogic.gdx.scenes.scene2d.ui.Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
        Stage stage = new Stage(new ScreenViewport());
        stage.getViewport().update(768, 576, true);
        World world = new World(new WorldConfiguration());
        int map = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(map);
        tiled.data = new TiledLayerData(12, 12, 32, 16, 4, TiledProjection.ISO);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) tiled.data.setTile(x, y, 1);
        world.getMapper(TransformComponent.class).create(map).refreshCaches();
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(map);
        blocks.blocks.add(wall(1, 1, 1, 1, 3, .3f, 90));
        blocks.blocks.add(wall(2, 2, 4, 1, 2, .15f, 60));
        blocks.nextSpatialBlockId = 3;
        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(map);
        body.type = PhysicsBodyComponent.STATIC;
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(map);
        for (int id = 1; id <= 2; id++) {
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = id;
            shape.spatialBlockId = id;
            shapes.shapes.add(shape);
        }
        PhysicsShapeData manual = PhysicsService.createDefaultShape(3);
        manual.geometry.offsetX = 1000;
        shapes.shapes.add(manual);
        PhysicsCompiledFixturesComponent compiled = world.getMapper(PhysicsCompiledFixturesComponent.class).create(map);
        world.process();
        PhysicsService.publishPreparedCandidate(shapes, compiled, PhysicsService.prepareBodyCandidate(world, map, shapes.shapes, 32));
        String manualBefore = new com.badlogic.gdx.utils.Json().toJson(shapes.shapes.peek());
        HistoryManager history = new HistoryManager(8);
        history.historyIds().ensureForEntity(map);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        selection.selectBlock(map, 1);
        selection.toggleBlock(map, 2);
        try {
            PopupMenu menu = new PopupMenu();
            StudioContextMenu.appendSpatialLinkItems(menu, world, history, selection, stage, map);
            assertEquals(2, menu.getChildren().size);
            MenuItem link = (MenuItem) menu.getChildren().first();
            assertEquals("Link volume", link.getText().toString());
            captureMenu(stage, menu, "link");
            int actorsBefore = stage.getActors().size;
            click(item(menu, "Match thickness"));
            assertEquals("Match thickness must not open a dialog", actorsBefore, stage.getActors().size);
            assertEquals("Match thickness", history.peekUndoLabel());
            assertEquals(.3f, blocks.blocks.get(1).depth, 0);
            assertEquals(60, blocks.blocks.get(1).height, 0);
            assertEquals(0, blocks.links.size);
            history.undo();
            assertFalse(history.canUndo());
            assertEquals(.15f, blocks.blocks.get(1).depth, 0);
            history.redo();
            assertEquals(.3f, blocks.blocks.get(1).depth, 0);
            click(link);
            assertEquals("Successful link must not open a dialog", actorsBefore, stage.getActors().size);
            assertNull(selection.linkPreview());
            assertEquals("Link volume", history.peekUndoLabel());
            history.undo();
            assertEquals(0, blocks.links.size);
            assertEquals("One link click creates exactly one history entry", "Match thickness", history.peekUndoLabel());
            history.redo();
            assertEquals(1, blocks.links.size);
            assertEquals(.3f, blocks.blocks.get(1).depth, 0);
            assertTrue(compiled.valid);
            SpatialBlocksComponent linkedSnapshot = SpatialLinkProposal.copy(blocks);
            assertEquals(manualBefore, new com.badlogic.gdx.utils.Json().toJson(shapes.shapes.peek()));
            nativeActorPassage(compiled, tiled.data);
            verticalDialog(world, stage, history, selection, map, blocks, compiled, tiled.data, manualBefore, shapes);
            stage.act(1);
            stage.act(1);
            history.undo();
            assertEquals(0, blocks.links.size);
            history.redo();
            assertEquals(1, blocks.links.size);
            PopupMenu unlinkMenu = new PopupMenu();
            StudioContextMenu.appendSpatialLinkItems(unlinkMenu, world, history, selection, stage, map);
            MenuItem unlink = null;
            for (Actor actor : unlinkMenu.getChildren())
                if (actor instanceof MenuItem && ((MenuItem) actor).getText().toString().equals("Unlink volume"))
                    unlink = (MenuItem) actor;
            assertNotNull(unlink);
            captureMenu(stage, unlinkMenu, "unlink");
            actorsBefore = stage.getActors().size;
            click(unlink);
            assertEquals("Successful unlink must not open a dialog", actorsBefore, stage.getActors().size);
            assertEquals("Unlink volume", history.peekUndoLabel());
            assertEquals(0, blocks.links.size);
            assertEquals(.3f, blocks.blocks.get(1).depth, 0);
            history.undo();
            assertEquals(1, blocks.links.size);
            history.redo();
            assertEquals(0, blocks.links.size);
            selectionMenusAndRefusals(world, history, selection, stage, map, blocks);
            return linkedSnapshot;
        } finally {
            selection.dispose();
            world.dispose();
            stage.dispose();
            VisUI.dispose();
            ProjectConfig.setInstance(previous);
        }
    }

    private static void verticalDialog(World world, Stage stage, HistoryManager history,
                                       SpatialBlockSelectionService selection, int map, SpatialBlocksComponent blocks,
                                       PhysicsCompiledFixturesComponent compiled, TiledLayerData data,
                                       String manual, PhysicsShapesComponent shapes) {
        int upper = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(upper);
        tiled.projection = TiledProjection.ISO;
        tiled.tileWidth = 32;
        tiled.tileHeight = 16;
        tiled.mapWidthCells = tiled.mapHeightCells = 12;
        tiled.chunkSize = 4;
        tiled.defaultTileAltitude = 100;
        tiled.originY = 100;
        tiled.defaultTileHeight = 90;
        tiled.data = tiled.createLayerData();
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) tiled.data.setTile(x, y, 1);
        SpatialBlocksComponent reference = world.getMapper(SpatialBlocksComponent.class).create(upper);
        SpatialBlockData fixed = wall(1, 1, 1, 1, 3, .3f, 90);
        fixed.altitude = 100;
        reference.blocks.add(fixed);
        reference.nextSpatialBlockId = 2;
        world.process();
        selection.selectBlock(map, 1);
        PopupMenu menu = new PopupMenu();
        StudioContextMenu.appendSpatialLinkItems(menu, world, history, selection, stage, map);
        MenuItem item = null;
        for (Actor actor : menu.getChildren())
            if (actor instanceof MenuItem && ((MenuItem) actor).getText().toString().equals("Connect to wall"))
                item = (MenuItem) actor;
        assertNotNull(item);
        click(item);
        games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog dialog = verticalDialog(stage);
        assertNotNull(dialog);
        assertTrue(selection.verticalJoinPreview().diagnostic, selection.verticalJoinPreview().valid);
        assertEquals(2, selection.verticalJoinPreview().affected.size());
        stage.act(1);
        stage.draw();
        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        verticalButton(dialog, "Cancel").fire(new ChangeListener.ChangeEvent());
        stage.act(1);
        assertNull(selection.verticalJoinPreview());
        assertEquals(90, blocks.blocks.first().height, 0);
        click(item);
        dialog = verticalDialog(stage);
        verticalButton(dialog, "Connect to wall").fire(new ChangeListener.ChangeEvent());
        stage.act(1);
        assertEquals(100, blocks.blocks.first().height, 0);
        assertEquals(100, blocks.blocks.get(1).height, 0);
        assertEquals(100, world.getMapper(TiledLayerComponent.class).get(map).defaultTileHeight, 0);
        assertEquals(100, fixed.altitude, 0);
        assertEquals(90, fixed.height, 0);
        assertEquals(1, blocks.links.size);
        assertTrue(compiled.valid);
        assertEquals(manual, new com.badlogic.gdx.utils.Json().toJson(shapes.shapes.peek()));
        nativeActorPassage(compiled, data);
        history.undo();
        assertEquals(90, blocks.blocks.first().height, 0);
        history.redo();
        assertEquals(100, blocks.blocks.first().height, 0);
        history.undo();
        selection.toggleBlock(map, 2);
    }

    private static games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog verticalDialog(Stage stage) {
        for (int i = stage.getActors().size - 1; i >= 0; i--)
            if (stage.getActors().get(i) instanceof games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog)
                return (games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog) stage.getActors().get(i);
        return null;
    }

    private static VisTextButton verticalButton(games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog dialog, String text) {
        for (Actor actor : dialog.getButtonsTable().getChildren())
            if (actor instanceof VisTextButton && ((VisTextButton) actor).getText().toString().equals(text))
                return (VisTextButton) actor;
        throw new AssertionError(text);
    }

    private static void nativeActorPassage(PhysicsCompiledFixturesComponent compiled, TiledLayerData map) {
        Box2D.init();
        com.badlogic.gdx.physics.box2d.World physics = new com.badlogic.gdx.physics.box2d.World(new Vector2(), true);
        try {
            Body wallBody = physics.createBody(new BodyDef());
            for (CompiledFixtureData fixture : compiled.fixtures)
                if (fixture.shapeType == PhysicsGeometryData.SHAPE_POLYGON) {
                    PolygonShape polygon = new PolygonShape();
                    polygon.set(fixture.polygonVertices, 0, fixture.polygonVertexCount * 2);
                    wallBody.createFixture(polygon, 0);
                    polygon.dispose();
                }
            BodyDef definition = new BodyDef();
            definition.type = BodyDef.BodyType.DynamicBody;
            float[] start = new float[2], end = new float[2];
            map.projectSpatialPoint(3.5f, .8f, 0, start, 0);
            map.projectSpatialPoint(4.5f, .8f, 0, end, 0);
            definition.position.set(start[0] / 32, start[1] / 32);
            Body actor = physics.createBody(definition);
            CircleShape circle = new CircleShape();
            circle.setRadius(.02f);
            actor.createFixture(circle, 1);
            circle.dispose();
            actor.setLinearVelocity((end[0] - start[0]) / 32, (end[1] - start[1]) / 32);
            for (int i = 0; i < 60; i++) physics.step(1f / 60, 6, 2);
            assertEquals(end[0] / 32, actor.getPosition().x, .01f);
            assertEquals(end[1] / 32, actor.getPosition().y, .01f);
            // The same real linked fixtures block a transverse attempted crossing.
            map.projectSpatialPoint(4, .8f, 0, start, 0);
            map.projectSpatialPoint(4, 1.6f, 0, end, 0);
            actor.setTransform(start[0] / 32, start[1] / 32, 0);
            actor.setLinearVelocity((end[0] - start[0]) / 32, (end[1] - start[1]) / 32);
            for (int i = 0; i < 60; i++) physics.step(1f / 60, 6, 2);
            assertTrue(actor.getPosition().dst(end[0] / 32, end[1] / 32) > .05f);
        } finally {
            physics.dispose();
        }
    }

    private static SpatialBlockData wall(int id, int structure, float x, float y, float width, float depth, float height) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structure;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.height = height;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = (int) Math.floor(y); gy < (int) Math.ceil(y + depth); gy++)
            for (int gx = (int) Math.floor(x); gx < (int) Math.ceil(x + width); gx++) wall.addLinkedTileRef(gx, gy, 1);
        return wall;
    }

    private static void click(MenuItem item) {
        InputEvent event = new InputEvent();
        for (com.badlogic.gdx.scenes.scene2d.EventListener listener : item.getListeners())
            if (listener instanceof ClickListener) ((ClickListener) listener).clicked(event, 1, 1);
    }

    private static void captureMenu(Stage stage, PopupMenu menu, String name) {
        menu.showMenu(stage, 200, 350);
        Gdx.gl.glClearColor(.08f, .08f, .08f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(1);
        stage.draw();
        assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        Pixmap screenshot = Pixmap.createFromFrameBuffer(0, 0, 768, 576);
        try {
            PixmapIO.writePNG(new FileHandle(System.getProperty("java.io.tmpdir") + "/pixscape-volume-" + name + ".png"), screenshot, -1, true);
        } finally {
            screenshot.dispose();
            menu.remove();
        }
    }

    private static MenuItem item(PopupMenu menu, String label) {
        for (Actor actor : menu.getChildren()) {
            if (actor instanceof MenuItem && ((MenuItem) actor).getText().toString().equals(label)) {
                return (MenuItem) actor;
            }
        }
        return null;
    }

    private static PopupMenu menu(World world, HistoryManager history, SpatialBlockSelectionService selection, Stage stage, int map) {
        PopupMenu menu = new PopupMenu();
        StudioContextMenu.appendSpatialLinkItems(menu, world, history, selection, stage, map);
        return menu;
    }

    private static void selectionMenusAndRefusals(World world, HistoryManager history,
                                                  SpatialBlockSelectionService selection, Stage stage, int map,
                                                  SpatialBlocksComponent blocks) {
        selection.clearSelectionOnly();
        assertEquals(0, menu(world, history, selection, stage, map).getChildren().size);
        selection.selectBlock(map, 1);
        PopupMenu single = menu(world, history, selection, stage, map);
        assertNull(item(single, "Link volume"));
        assertNull(item(single, "Unlink volume"));
        assertTrue(item(single, "Ctrl-click another volume to link").isDisabled());
        blocks.links.add(new games.pixscape.runtime.spatial.SpatialBlockLink(1, 2, 2, 1));
        assertFalse(item(menu(world, history, selection, stage, map), "Unlink volume").isDisabled());
        blocks.links.add(new games.pixscape.runtime.spatial.SpatialBlockLink(1, 3, 2, 1));
        PopupMenu ambiguous = menu(world, history, selection, stage, map);
        assertTrue(item(ambiguous, "Unlink volume").isDisabled());
        assertNotNull(item(ambiguous, "Select the two linked volumes to unlink"));
        selection.toggleBlock(map, 2);
        assertFalse(item(menu(world, history, selection, stage, map), "Unlink volume").isDisabled());
        selection.toggleBlock(map, 3);
        assertNull(item(menu(world, history, selection, stage, map), "Link volume"));
        assertTrue(item(menu(world, history, selection, stage, map), "Unlink volume").isDisabled());
        blocks.links.clear();
        selection.selectBlock(map, 1);
        selection.toggleBlock(map, 2);
        blocks.blocks.get(1).altitude = 10;
        String before = new com.badlogic.gdx.utils.Json().toJson(blocks);
        String undo = history.peekUndoLabel();
        int actorsBefore = stage.getActors().size;
        click(item(menu(world, history, selection, stage, map), "Link volume"));
        assertEquals(before, new com.badlogic.gdx.utils.Json().toJson(blocks));
        assertEquals(undo, history.peekUndoLabel());
        assertEquals(actorsBefore + 1, stage.getActors().size);
        com.kotcrab.vis.ui.widget.VisDialog refusal = (com.kotcrab.vis.ui.widget.VisDialog) stage.getActors().peek();
        assertEquals("Link volume", refusal.getTitleLabel().getText().toString());
        assertTrue(refusal.getContentTable().toString().contains("The volumes must have the same base altitude."));
        stage.act(1);
        stage.draw();
        refusal.remove();
        blocks.blocks.get(1).altitude = 0;
    }
}
