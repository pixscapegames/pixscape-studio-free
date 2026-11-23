package games.pixscape.studio.configuration;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.loading.RuntimeProjectIO;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditSpatialFootprintCommand;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class RuntimeExportFootprintCoordinatesTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void exportAndRuntimeLoadKeepIndependentOffsetsWithoutConversion() throws Exception {
        FileHandle studio = new FileHandle(temp.newFolder("studio"));
        FileHandle output = new FileHandle(temp.newFolder("output"));
        var config = new ProjectConfig();
        config.projectTitle = config.projectFileName = "Coordinates";
        config.exportRootPathDir = output.path();
        config.createSceneMeta("Main");
        var meta = config.getCurrentSceneMeta();
        var identities = new IdentityRegistry();
        World authored = world(), loaded = world();
        try {
            identities.bind(authored, meta);
            int actor = authored.create();
            identities.ensureStableId(actor);
            authored.getMapper(GameObjectComponent.class).create(actor);
            authored.getMapper(EntityIndexComponent.class).create(actor);
            authored.getMapper(TransformComponent.class).create(actor).x = 120;
            var foot = authored.getMapper(SpatialFootprintComponent.class).create(actor);
            foot.localOffsetXPx = 31;
            foot.localOffsetYPx = -19;
            var rectangles = new ActorSpatialRectangles();
            var rectangle = new ActorSpatialRectangleData();
            rectangle.pivotX = 7;
            rectangle.pivotY = -3;
            rectangles.staticRectangle = rectangle.copy();
            rectangles.put(7, "idle", rectangle.copy());
            rectangles.put(7, "walk", rectangle.copy());
            authored.getMapper(SpatialRepresentationComponent.class).create(actor).rectangles = rectangles;
            authored.process();
            var history = new HistoryManager(10);
            var before = EditSpatialFootprintCommand.Snapshot.capture(foot);
            history.execute(new EditSpatialFootprintCommand(authored, history.historyIds(), actor,
                    before, before.withOffsetX(65).withOffsetY(24)));
            studio.child("scenes").mkdirs();
            var data = new SaveFileFormat();
            data.entities.add(actor);
            try (var stream = studio.child("scenes").child(meta.file).write(false)) {
                authored.getSystem(WorldSerializationManager.class).save(stream, data);
            }
            new AssetMetaDatabase().save(studio.child("assets.json"));
            RuntimeExport.exportRuntime(config, studio, output);
            FileHandle runtime = output.child(RuntimeExport.RUNTIME_DIR_NAME);
            var runtimeConfig = RuntimeProjectIO.loadProject(runtime);
            var runtimeMeta = runtimeConfig.scenes.get(meta.name);
            var result = SceneLoader.loadScene(loaded, runtime.child("scenes").child(meta.file), false, runtimeMeta);
            int e = result.entities.get(0);
            var actualFoot = loaded.getMapper(SpatialFootprintComponent.class).get(e);
            assertEquals(65, actualFoot.localOffsetXPx, 0);
            assertEquals(24, actualFoot.localOffsetYPx, 0);
            var actual = loaded.getMapper(SpatialRepresentationComponent.class).get(e).rectangles;
            assertTrue(rectangle.same(actual.staticRectangle));
            assertTrue(rectangle.same(actual.get(7, "idle")));
            assertTrue(rectangle.same(actual.get(7, "walk")));
            assertEquals(120, loaded.getMapper(TransformComponent.class).get(e).x, 0);
        } finally {
            identities.bind(null, null);
            authored.dispose();
            loaded.dispose();
        }
    }

    private static World world() {
        World world = new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
        world.getSystem(WorldSerializationManager.class).setSerializer(new JsonArtemisSerializer(world));
        return world;
    }
}
