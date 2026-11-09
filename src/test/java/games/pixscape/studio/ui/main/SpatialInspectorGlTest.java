package games.pixscape.studio.ui.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.ui.property.PropertiesPanel;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real inspector routing and widgets in an isolated desktop context, without user projects. */
public class SpatialInspectorGlTest {
    @Test public void previewNotificationsRefreshOnlyTheBoundVolume() throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_DOCUMENT_GL_TEST")));
        String previousHome = System.getProperty("user.home");
        ProjectConfig previousConfig = ProjectConfig.getInstance();
        FileHandle directory = new FileHandle(Files.createTempDirectory("pixscape-spatial-inspector").toFile());
        System.setProperty("user.home", directory.path());
        ProjectConfig config = new ProjectConfig();
        config.projectDirectoryPath = directory.path();
        config.projectFileName = "test.pixscape";
        ProjectConfig.setInstance(config);
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Spatial inspector regression");
        gl.setInitialVisible(false);
        gl.setWindowedMode(1200, 800);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        gl.disableAudio(true);
        try (var globals = new StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio;
                @Override public void create() {
                    try {
                        studio = new StudioApplicationAdapter();
                        studio.create();
                        var database = new games.pixscape.studio.asset.AssetMetaDatabase();
                        database.save(directory.child("assets.json"));
                        var databaseField = SceneService.class.getDeclaredField("assetMetaDatabase");
                        databaseField.setAccessible(true);
                        databaseField.set(studio.getSceneService(), database);
                        config.createSceneMeta("Scene");
                        var context = studio.getCanvas().createSceneContext(
                                config.canonicalSceneTagCurrent(), config.getCurrentSceneMeta());
                        context.layerService().addLayerTop("Layer");
                        context.world().process();
                        SceneService.saveScene(context.world(), directory.child("scenes")
                                .child(config.getCurrentSceneMeta().getFile()), false);
                        studio.getCanvas().releaseSceneContext(context);
                        context.dispose();
                        var load = SceneService.class.getDeclaredMethod("loadScene",
                                ProjectConfig.class, String.class, FileHandle.class);
                        load.setAccessible(true);
                        load.invoke(studio.getSceneService(), config, "Scene", directory);
                        context = studio.getSceneEditorContext();
                        var world = context.world();
                        var selection = context.spatialBlockSelectionService();
                        int map = world.create();
                        var data = new TiledMapLayerData(4, 4, 64, 64, 2, TiledProjection.ORTHO);
                        data.setTile(1, 1, 1);
                        world.getMapper(TiledLayerComponent.class).create(map).data = data;
                        var blocks = world.getMapper(SpatialBlocksComponent.class).create(map);
                        var wall = new SpatialBlockData();
                        wall.id = wall.structureId = 1;
                        wall.x = wall.y = wall.width = wall.depth = 1;
                        wall.height = 40;
                        wall.beginAuthoredLinkedTileRefs();
                        wall.addLinkedTileRef(1, 1, 1);
                        blocks.blocks.add(wall);
                        blocks.nextSpatialBlockId = 2;
                        context.historyManager().historyIds().ensureForEntity(map);
                        PropertiesPanel panel = (PropertiesPanel) field(studio, "propertiesPanel");
                        selection.selectBlock(map, 1);
                        flush(panel);
                        assertEquals(1, field(panel, "boundSpatialBlockId"));
                        Object properties = field(panel, "spatialBlockProperties");
                        SimpleFloatField height = (SimpleFloatField) field(properties, "heightField");
                        assertEquals(40f, Float.parseFloat(height.getText()), 0f);
                        assertTrue(selection.wallEditSession().begin(map, 1, blocks, data));
                        assertTrue(selection.wallEditSession().updateHeight(65));
                        selection.setEditPreview(true);
                        // Queued notifications must not refresh UI before the refresh boundary.
                        assertEquals(40f, Float.parseFloat(height.getText()), 0f);
                        flush(panel);
                        assertEquals(1, field(panel, "boundSpatialBlockId"));
                        assertEquals(65f, Float.parseFloat(height.getText()), 0f);
                        assertEquals(40f, wall.height, 0);
                        selection.clearEditPreview();
                        flush(panel);
                        assertEquals(40f, Float.parseFloat(height.getText()), 0f);
                        assertEquals(1, field(panel, "boundSpatialBlockId"));
                        // A pending preview must not override a later scene selection.
                        assertTrue(selection.wallEditSession().begin(map, 1, blocks, data));
                        selection.wallEditSession().updateHeight(75);
                        selection.setEditPreview(true);
                        context.selectionService().clearSelection();
                        flush(panel);
                        assertEquals(-1, field(panel, "boundSpatialBlockId"));
                        selection.clearEditPreview();
                        flush(panel);
                        assertEquals(-1, field(panel, "boundSpatialBlockId"));
                    } catch (Throwable ex) { failure[0] = ex; }
                    finally { Gdx.app.exit(); }
                }
                @Override public void dispose() { if (studio != null) studio.dispose(); }
            }, gl);
        } finally {
            System.setProperty("user.home", previousHome);
            ProjectConfig.setInstance(previousConfig);
            directory.deleteDirectory();
        }
        if (failure[0] != null) throw new AssertionError("Spatial inspector workflow failed", failure[0]);
    }

    private static void flush(PropertiesPanel panel) {
        EventFlow.i().flush();
        panel.updateIfDirty();
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
