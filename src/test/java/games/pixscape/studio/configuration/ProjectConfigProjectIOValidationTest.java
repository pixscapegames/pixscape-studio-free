package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.ui.preview.PreviewTarget;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class ProjectConfigProjectIOValidationTest {

    @Test
    public void sceneMetaDeclaresNoTiledMapCreationDefaults() {
        assertThrows(NoSuchFieldException.class,
                () -> SceneMeta.class.getDeclaredField("tiledProjection"));
        assertThrows(NoSuchFieldException.class,
                () -> SceneMeta.class.getDeclaredField("tileWidth"));
        assertThrows(NoSuchFieldException.class,
                () -> SceneMeta.class.getDeclaredField("tileHeight"));
        assertThrows(NoSuchFieldException.class,
                () -> SceneMeta.class.getDeclaredField("chunkSize"));
    }

    @Test
    public void sceneMetaDeclaresNoTransientEditorState() {
        assertThrows(NoSuchFieldException.class,
                () -> SceneMeta.class.getDeclaredField("editorMode"));
        assertThrows(NoSuchFieldException.class,
                () -> SceneMeta.class.getDeclaredField("showPhysicsFixtures"));
        assertThrows(NoSuchFieldException.class,
                () -> SceneMeta.class.getDeclaredField("showPhysicsJoints"));
    }

    @Test
    public void uniqueSceneNameUsesEstablishedNumberedSuffixes() {
        ProjectConfig cfg = new ProjectConfig();

        assertEquals("New Scene", cfg.uniqueSceneName("New Scene"));
        cfg.createSceneMeta("New Scene");
        assertEquals("New Scene 2", cfg.uniqueSceneName("New Scene"));
        cfg.createSceneMeta("New Scene 2");
        assertEquals("New Scene 3", cfg.uniqueSceneName("New Scene"));
    }

    @Test
    public void loadProject_validProject_loadsSuccessfully() throws Exception {
        Path dir = Files.createTempDirectory("project-config-valid");
        FileHandle projectFile = writeProjectFile(dir, validProjectJson("Main", "scene1.json"));

        ProjectConfig cfg = ProjectConfig.ProjectIO.loadProject(projectFile);

        assertEquals("Test Project", cfg.projectTitle);
        assertEquals("Main", cfg.getCurrentSceneName());
        assertEquals("scene1.json", cfg.getCurrentSceneMeta().getFile());
        assertEquals(projectFile.parent().path(), cfg.projectDirectoryPath);
    }

    @Test(expected = RuntimeException.class)
    public void loadProject_blankProjectTitle_throws() throws Exception {
        Path dir = Files.createTempDirectory("project-config-blank-title");
        writeProjectFile(dir, validProjectJson("Main", "scene1.json").replace("\"projectTitle\":\"Test Project\"", "\"projectTitle\":\"   \""));

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test(expected = RuntimeException.class)
    public void missingProjectKindIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-missing-kind");
        String json = validProjectJson("Main", "scene1.json").replace("\"projectKind\":\"pixscape-studio-project\",", "");
        writeProjectFile(dir, json);

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test(expected = RuntimeException.class)
    public void unknownProjectKindIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-unknown-kind");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"projectKind\":\"pixscape-studio-project\"", "\"projectKind\":\"unknown-kind\"");
        writeProjectFile(dir, json);

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test(expected = RuntimeException.class)
    public void runtimeProjectKindIsRejectedByProjectIO() throws Exception {
        Path dir = Files.createTempDirectory("project-config-runtime-kind");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"projectKind\":\"pixscape-studio-project\"", "\"projectKind\":\"pixscape-runtime-project\"");
        writeProjectFile(dir, json);

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test
    public void sceneSchemaVersionThreeIsAcceptedByStudioProjectIO() throws Exception {
        Path dir = Files.createTempDirectory("project-config-valid-kind");
        FileHandle projectFile = writeProjectFile(dir, validProjectJson("Main", "scene1.json"));

        ProjectConfig cfg = ProjectConfig.ProjectIO.loadProject(projectFile);
        assertEquals(ProjectConfig.STUDIO_PROJECT_KIND, cfg.projectKind);
        assertEquals(3, cfg.getCurrentSceneMeta().sceneSchemaVersion);
    }

    @Test(expected = RuntimeException.class)
    public void missingSceneSchemaVersionIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-missing-scene-schema");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"sceneSchemaVersion\":3,", "");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void sceneSchemaVersionZeroIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-zero-scene-schema");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"sceneSchemaVersion\":3", "\"sceneSchemaVersion\":0");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void sceneSchemaVersionOneIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-future-scene-schema");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"sceneSchemaVersion\":3", "\"sceneSchemaVersion\":1");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void sceneSchemaVersionTwoIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-old-scene-schema");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"sceneSchemaVersion\":3", "\"sceneSchemaVersion\":2");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void missingEntityHighWaterIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-missing-entity-high-water");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"nextEntityStableId\":1,", "");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void nonPositiveEntityHighWaterIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-invalid-entity-high-water");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"nextEntityStableId\":1", "\"nextEntityStableId\":0");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void missingPhysicsHighWaterIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-missing-physics-high-water");
        String json = validProjectJson("Main", "scene1.json")
                .replace(",\"nextPhysicsShapeId\":1", "");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void nonPositivePhysicsHighWaterIsRejected() throws Exception {
        Path dir = Files.createTempDirectory("project-config-invalid-physics-high-water");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"nextPhysicsShapeId\":1", "\"nextPhysicsShapeId\":0");
        ProjectConfig.ProjectIO.loadProject(writeProjectFile(dir, json));
    }

    @Test(expected = RuntimeException.class)
    public void loadProject_malformedJson_throws() throws Exception {
        Path dir = Files.createTempDirectory("project-config-malformed");
        writeProjectFile(dir, "{\"projectTitle\":\"bad\"");

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test(expected = RuntimeException.class)
    public void loadProject_missingProjectTitle_throws() throws Exception {
        Path dir = Files.createTempDirectory("project-config-missing-title");
        String json = validProjectJson("Main", "scene1.json").replace("\"projectTitle\":\"Test Project\",", "");
        writeProjectFile(dir, json);

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test(expected = RuntimeException.class)
    public void loadProject_invalidCurrentSceneReference_throws() throws Exception {
        Path dir = Files.createTempDirectory("project-config-invalid-current-scene");
        String json = validProjectJson("MissingScene", "scene1.json");
        writeProjectFile(dir, json);

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test(expected = RuntimeException.class)
    public void loadProject_blankExportRoot_throws() throws Exception {
        Path dir = Files.createTempDirectory("project-config-blank-export-root");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"exportRootPathDir\":\"/tmp/export\"", "\"exportRootPathDir\":\"   \"");
        writeProjectFile(dir, json);

        ProjectConfig.ProjectIO.loadProject(new FileHandle(dir.resolve("project.json").toFile()));
    }

    @Test(expected = RuntimeException.class)
    public void saveProject_blankExportRoot_throwsAndDoesNotPersistInvalidState() throws Exception {
        Path dir = Files.createTempDirectory("project-config-save-blank-export-root");
        FileHandle projectFile = writeProjectFile(dir, validProjectJson("Main", "scene1.json"));
        String before = projectFile.readString("UTF-8");

        ProjectConfig cfg = ProjectConfig.ProjectIO.loadProject(projectFile);
        cfg.exportRootPathDir = " ";

        try {
            ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        } finally {
            String after = projectFile.readString("UTF-8");
            assertEquals(before, after);
            ProjectConfig reloaded = ProjectConfig.ProjectIO.loadProject(projectFile);
            assertEquals("/tmp/export", reloaded.exportRootPathDir);
        }
    }

    @Test
    public void saveProject_nonBlankExportRoot_persistsSuccessfully() throws Exception {
        Path dir = Files.createTempDirectory("project-config-save-valid-export-root");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Test Project";
        cfg.projectFileName = "test-project";
        cfg.exportRootPathDir = "/tmp/export";
        cfg.previewTarget = PreviewTarget.DESKTOP;
        cfg.glSamples = 0;
        cfg.createSceneMeta("Main");

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);

        ProjectConfig loaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        assertEquals("/tmp/export", loaded.exportRootPathDir);
        assertEquals(projectFile.parent().path(), loaded.projectDirectoryPath);
        assertEquals("Main", loaded.getCurrentSceneName());
    }

    @Test
    public void saveProject_sceneMetaSchemaPhysicsAndScaleFieldsRemainPersistentAfterReload() throws Exception {
        Path dir = Files.createTempDirectory("project-config-scene-meta-physics");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Physics Persist";
        cfg.projectFileName = "physics-persist";
        cfg.exportRootPathDir = "/tmp/export";
        cfg.previewTarget = PreviewTarget.DESKTOP;
        cfg.glSamples = 0;
        cfg.createSceneMeta("Main");

        SceneMeta main = cfg.getCurrentSceneMeta();
        assertNotNull(main);
        main.physicsEnabled = true;
        main.gravityX = 0.75f;
        main.gravityY = -15.25f;
        main.pixelsPerMeter = 128f;

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        ProjectConfig reloaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        SceneMeta reloadedMain = reloaded.getCurrentSceneMeta();

        assertNotNull(reloadedMain);
        assertEquals(3, reloadedMain.sceneSchemaVersion);
        assertTrue(reloadedMain.physicsEnabled);
        assertEquals(0.75f, reloadedMain.gravityX, 0.0001f);
        assertEquals(-15.25f, reloadedMain.gravityY, 0.0001f);
        assertEquals(128f, reloadedMain.pixelsPerMeter, 0.0001f);
    }

    @Test
    public void saveProject_sceneMetadataContainsNoTiledMapCreationDefaults() throws Exception {
        Path dir = Files.createTempDirectory("project-config-no-tiled-map-defaults");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Map Defaults";
        cfg.projectFileName = "map-defaults";
        cfg.exportRootPathDir = "/tmp/export";
        cfg.previewTarget = PreviewTarget.DESKTOP;
        cfg.glSamples = 0;
        cfg.createSceneMeta("Main");
        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        String saved = projectFile.readString("UTF-8");
        ProjectConfig loaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        SceneMeta restored = loaded.getCurrentSceneMeta();

        assertNotNull(restored);
        assertFalse(saved.contains("tiledEnabled"));
        assertFalse(saved.contains("tiledProjection"));
        assertFalse(saved.contains("tileWidth"));
        assertFalse(saved.contains("tileHeight"));
        assertFalse(saved.contains("chunkSize"));
    }

    @Test
    public void loadProject_ignoresIntermediateSchema3TiledEnabledField() throws Exception {
        Path dir = Files.createTempDirectory("project-config-stale-tiled-enabled");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"name\":\"Main\"", "\"tiledEnabled\":true,\"name\":\"Main\"");
        FileHandle projectFile = writeProjectFile(dir, json);

        ProjectConfig loaded = ProjectConfig.ProjectIO.loadProject(projectFile);

        assertNotNull(loaded.getCurrentSceneMeta());
        ProjectConfig.ProjectIO.saveProject(loaded, projectFile);
        assertFalse(projectFile.readString("UTF-8").contains("tiledEnabled"));
    }

    @Test
    public void loadProject_ignoresAndDropsRemovedSceneMetadata() throws Exception {
        Path dir = Files.createTempDirectory("project-config-removed-scene-metadata");
        String json = validProjectJson("Main", "scene1.json")
                .replace("\"name\":\"Main\"",
                        "\"editorMode\":\"TILE\"," +
                        "\"showPhysicsFixtures\":true," +
                        "\"showPhysicsJoints\":true," +
                        "\"mainCameraOffscreen\":true," +
                        "\"name\":\"Main\"");
        FileHandle projectFile = writeProjectFile(dir, json);

        ProjectConfig loaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        ProjectConfig.ProjectIO.saveProject(loaded, projectFile);
        String saved = projectFile.readString("UTF-8");

        assertFalse(saved.contains("editorMode"));
        assertFalse(saved.contains("showPhysicsFixtures"));
        assertFalse(saved.contains("showPhysicsJoints"));
        assertFalse(saved.contains("mainCameraOffscreen"));
    }

    @Test
    public void saveProject_fullIntensityBlackAmbientRoundTripsWithoutBeingDefaulted() throws Exception {
        SceneMeta restored = roundTripAmbient(0f, 0f, 0f, 1f);

        assertEquals(0f, restored.ambientColorR, 0.0001f);
        assertEquals(0f, restored.ambientColorG, 0.0001f);
        assertEquals(0f, restored.ambientColorB, 0.0001f);
        assertEquals(1f, restored.ambientIntensity, 0.0001f);
        assertEquals(0f, restored.ambientMulR, 0.0001f);
        assertEquals(0f, restored.ambientMulG, 0.0001f);
        assertEquals(0f, restored.ambientMulB, 0.0001f);
    }

    @Test
    public void saveProject_ambientMultipliersAreDerivedFromAuthoredValues() throws Exception {
        SceneMeta restored = roundTripAmbient(0.2f, 0.4f, 0.6f, 0.5f);

        assertEquals(0.2f, restored.ambientColorR, 0.0001f);
        assertEquals(0.4f, restored.ambientColorG, 0.0001f);
        assertEquals(0.6f, restored.ambientColorB, 0.0001f);
        assertEquals(0.5f, restored.ambientIntensity, 0.0001f);
        assertEquals(0.6f, restored.ambientMulR, 0.0001f);
        assertEquals(0.7f, restored.ambientMulG, 0.0001f);
        assertEquals(0.8f, restored.ambientMulB, 0.0001f);
    }

    @Test
    public void saveProject_purgedScenePhysicsRemainsDisabledAfterReload() throws Exception {
        Path dir = Files.createTempDirectory("project-config-purged-physics");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Purged Physics";
        cfg.projectFileName = "purged-physics";
        cfg.exportRootPathDir = "/tmp/export";
        cfg.previewTarget = PreviewTarget.DESKTOP;
        cfg.glSamples = 0;
        cfg.createSceneMeta("Main");
        cfg.getCurrentSceneMeta().physicsEnabled = false;

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        ProjectConfig restored = ProjectConfig.ProjectIO.loadProject(projectFile);

        assertFalse(restored.getCurrentSceneMeta().physicsEnabled);
    }

    @Test
    public void saveProject_sceneMetaPhysicsRemainsPersistentAcrossSceneSwitchAndBack() throws Exception {
        Path dir = Files.createTempDirectory("project-config-scene-switch-meta");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Scene Switch";
        cfg.projectFileName = "scene-switch";
        cfg.exportRootPathDir = "/tmp/export";
        cfg.previewTarget = PreviewTarget.DESKTOP;
        cfg.glSamples = 0;

        cfg.createSceneMeta("Main");
        SceneMeta main = cfg.getCurrentSceneMeta();
        assertNotNull(main);
        main.physicsEnabled = true;
        main.gravityX = 3.5f;
        main.gravityY = -5.5f;
        main.pixelsPerMeter = 96f;

        cfg.createSceneMeta("Other");
        cfg.setCurrentSceneByName("Main");
        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);

        ProjectConfig loaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        loaded.setCurrentSceneByName("Other");
        loaded.setCurrentSceneByName("Main");
        SceneMeta loadedMain = loaded.getCurrentSceneMeta();

        assertNotNull(loadedMain);
        assertTrue(loadedMain.physicsEnabled);
        assertEquals(3.5f, loadedMain.gravityX, 0.0001f);
        assertEquals(-5.5f, loadedMain.gravityY, 0.0001f);
        assertEquals(96f, loadedMain.pixelsPerMeter, 0.0001f);
    }

    @Test
    public void saveProject_runtimeAvailabilityRemainsPersistentAfterReload() throws Exception {
        Path dir = Files.createTempDirectory("project-config-runtime-availability");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Runtime Availability";
        cfg.projectFileName = "runtime-availability";
        cfg.exportRootPathDir = "/tmp/export";
        cfg.previewTarget = PreviewTarget.DESKTOP;
        cfg.glSamples = 0;
        cfg.createSceneMeta("Main");

        SceneMeta main = cfg.getCurrentSceneMeta();
        assertNotNull(main);
        main.runtimeAvailability.spriteAssetIds.add(11);
        main.runtimeAvailability.animationAssetIds.add(12);
        main.runtimeAvailability.particleEffectPaths.add("impact.p");
        main.runtimeAvailability.prefabIds.add("enemy_slime");
        main.runtimeAvailability.tiledTileAssetIds.add(6);
        main.runtimeAvailability.tiledAnimationIds.add(7);

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        ProjectConfig reloaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        SceneMeta reloadedMain = reloaded.getCurrentSceneMeta();

        assertNotNull(reloadedMain);
        assertNotNull(reloadedMain.runtimeAvailability);
        assertEquals(Integer.valueOf(11), reloadedMain.runtimeAvailability.spriteAssetIds.get(0));
        assertEquals(Integer.valueOf(12), reloadedMain.runtimeAvailability.animationAssetIds.get(0));
        assertEquals("impact.p", reloadedMain.runtimeAvailability.particleEffectPaths.get(0));
        assertEquals("enemy_slime", reloadedMain.runtimeAvailability.prefabIds.get(0));
        assertEquals(Integer.valueOf(6), reloadedMain.runtimeAvailability.tiledTileAssetIds.get(0));
        assertEquals(Integer.valueOf(7), reloadedMain.runtimeAvailability.tiledAnimationIds.get(0));
    }

    @Test
    public void loadProject_missingRuntimeAvailabilityCreatesEmptyLists() throws Exception {
        Path dir = Files.createTempDirectory("project-config-runtime-availability-missing");
        FileHandle projectFile = writeProjectFile(dir, validProjectJson("Main", "scene1.json"));

        ProjectConfig loaded = ProjectConfig.ProjectIO.loadProject(projectFile);
        SceneMeta scene = loaded.getCurrentSceneMeta();

        assertNotNull(scene.runtimeAvailability);
        assertTrue(scene.runtimeAvailability.spriteAssetIds.isEmpty());
        assertTrue(scene.runtimeAvailability.animationAssetIds.isEmpty());
        assertTrue(scene.runtimeAvailability.particleEffectPaths.isEmpty());
        assertTrue(scene.runtimeAvailability.prefabIds.isEmpty());
        assertTrue(scene.runtimeAvailability.tiledTileAssetIds.isEmpty());
        assertTrue(scene.runtimeAvailability.tiledAnimationIds.isEmpty());
    }

    private static FileHandle writeProjectFile(Path dir, String json) throws Exception {
        Files.writeString(dir.resolve("project.json"), json, StandardCharsets.UTF_8);
        return new FileHandle(dir.resolve("project.json").toFile());
    }

    private static SceneMeta roundTripAmbient(
            float red, float green, float blue, float intensity) throws Exception {
        Path dir = Files.createTempDirectory("project-config-ambient-round-trip");
        FileHandle projectFile = new FileHandle(dir.resolve("project.json").toFile());
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Ambient";
        cfg.projectFileName = "ambient";
        cfg.exportRootPathDir = "/tmp/export";
        cfg.createSceneMeta("Main");
        SceneMeta scene = cfg.getCurrentSceneMeta();
        scene.ambientColorR = red;
        scene.ambientColorG = green;
        scene.ambientColorB = blue;
        scene.ambientIntensity = intensity;
        scene.ambientMulR = 1f;
        scene.ambientMulG = 1f;
        scene.ambientMulB = 1f;

        ProjectConfig.ProjectIO.saveProject(cfg, projectFile);
        return ProjectConfig.ProjectIO.loadProject(projectFile).getCurrentSceneMeta();
    }

    private static String validProjectJson(String currentSceneName, String currentSceneFile) {
        return "{" +
                "\"projectKind\":\"pixscape-studio-project\"," +
                "\"projectTitle\":\"Test Project\"," +
                "\"projectFileName\":\"test-project\"," +
                "\"version\":\"1\"," +
                "\"exportRootPathDir\":\"/tmp/export\"," +
                "\"glProfile\":\"GL30\"," +
                "\"glSamples\":0," +
                "\"currentSceneName\":\"" + currentSceneName + "\"," +
                "\"nextSceneIndex\":2," +
                "\"scenes\":{" +
                "\"Main\":{" +
                "\"sceneSchemaVersion\":3," +
                "\"name\":\"Main\"," +
                "\"file\":\"" + currentSceneFile + "\"," +
                "\"nextEntityStableId\":1," +
                "\"nextPhysicsShapeId\":1" +
                "}" +
                "}" +
                "}";
    }
}
