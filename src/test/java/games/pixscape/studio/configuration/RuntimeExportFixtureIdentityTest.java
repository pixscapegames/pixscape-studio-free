package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.io.StudioFs;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RuntimeExportFixtureIdentityTest {
    @Test
    public void exportPreservesDistinctAuthoredAndSpatialFixtureIdentitiesAndHighWater() throws Exception {
        Path studioDir = Files.createTempDirectory("fixture-export-studio");
        Path userDir = Files.createTempDirectory("fixture-export-user");
        ProjectConfig config = config(userDir);
        config.getSceneMeta("Main").nextFixtureId = 1_000_007;
        writeScene(studioDir, validScene(1_000_005, 1_000_006));

        RuntimeExport.exportRuntime(config, handle(studioDir), handle(userDir));

        JsonValue scene = new JsonReader().parse(handle(userDir
                .resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("scenes").resolve("scene1.json")));
        JsonValue entities = scene.get("entities");
        Assert.assertEquals(1_000_005, entities.get("0").get("components")
                .get("PhysicsFixturesComponent").get("fixtures").get(0).getInt("fixtureId"));
        Assert.assertEquals(1_000_006, entities.get("1").get("components")
                .get("PhysicsFixturesComponent").get("fixtures").get(0).getInt("fixtureId"));
        Assert.assertEquals(1_000_006, entities.get("1").get("components")
                .get("SpatialBlocksComponent").get("blocks").get(0).getInt("fixtureId"));

        JsonValue project = new JsonReader().parse(handle(userDir
                .resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve(RuntimeExport.PROJECT_JSON)));
        Assert.assertEquals(1_000_007, project.get("scenes").get("Main").getInt("nextFixtureId"));
    }

    @Test
    public void invalidHighWaterRefusesExportBeforeReplacingExistingOutput() throws Exception {
        Path studioDir = Files.createTempDirectory("fixture-invalid-export-studio");
        Path userDir = Files.createTempDirectory("fixture-invalid-export-user");
        ProjectConfig config = config(userDir);
        config.getSceneMeta("Main").nextFixtureId = 1_000_006;
        writeScene(studioDir, validScene(1_000_005, 1_000_006));
        Path runtimeDir = userDir.resolve(RuntimeExport.RUNTIME_DIR_NAME);
        Files.createDirectories(runtimeDir);
        Path marker = runtimeDir.resolve("keep.txt");
        Files.writeString(marker, "preserved", StandardCharsets.UTF_8);

        try {
            RuntimeExport.exportRuntime(config, handle(studioDir), handle(userDir));
            Assert.fail("Expected invalid fixture high-water mark to reject export");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("must be greater"));
        }
        Assert.assertEquals("preserved", Files.readString(marker, StandardCharsets.UTF_8));
    }

    private static ProjectConfig config(Path userDir) {
        ProjectConfig config = new ProjectConfig();
        config.projectTitle = "Fixture Identity";
        config.projectFileName = "fixture-identity";
        config.exportRootPathDir = userDir.toString();
        config.createSceneMeta("Main");
        return config;
    }

    private static void writeScene(Path studioDir, String json) throws Exception {
        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"),
                json, StandardCharsets.UTF_8);
        new AssetMetaDatabase().save(handle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON)));
    }

    private static String validScene(int authoredFixtureId, int spatialFixtureId) {
        return "{\"entities\":{" +
                "\"0\":{\"components\":{" +
                "\"PhysicsFixturesComponent\":{\"fixtures\":[{\"fixtureId\":" + authoredFixtureId + "}]}" +
                ",\"PhysicsAuthoringComponent\":{\"polygons\":[{\"authoringId\":5," +
                "\"generatedFixtureIds\":[" + authoredFixtureId + "]}]}}}," +
                "\"1\":{\"components\":{" +
                "\"PhysicsFixturesComponent\":{\"fixtures\":[{\"fixtureId\":" + spatialFixtureId + "}]}" +
                ",\"SpatialBlocksComponent\":{\"blocks\":[{\"id\":5," +
                "\"physicsCollision\":true,\"fixtureId\":" + spatialFixtureId + "}]}}}}}";
    }

    private static FileHandle handle(Path path) {
        return new FileHandle(path.toFile());
    }
}
