package games.pixscape.studio.service;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.importer.tmx.TmxSceneImportRequest;
import games.pixscape.studio.importer.tmx.TmxSceneImportResult;
import games.pixscape.studio.importer.tmx.TmxSceneImportService;
import games.pixscape.studio.io.StudioFs;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class SceneServiceRollbackTest {

    private static HeadlessApplication app;

    @BeforeClass
    public static void startGdx() {
        if (Gdx.app == null) {
            app = new HeadlessApplication(new ApplicationAdapter() {
            }, new HeadlessApplicationConfiguration());
        }
    }

    @AfterClass
    public static void stopGdx() {
        if (app != null) {
            app.exit();
            app = null;
        }
    }

    @Test
    public void rollbackCreatedSceneMeta_removesFailedSceneAndPreservesCurrent() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("NewScene");

        SceneService.rollbackCreatedSceneMeta(cfg, "NewScene");

        assertEquals("Main", cfg.getCurrentSceneName());
        assertEquals(1, cfg.getSceneNames().size);
        assertNotNull(cfg.getSceneMeta("Main"));
    }

    @Test
    public void rollbackDeletedSceneMeta_restoresMetadataAndCurrentPointer() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        SceneMeta deleted = cfg.getSceneMeta("Main");

        cfg.removeSceneMeta("Main");

        SceneService.rollbackDeletedSceneMeta(cfg, "Main", deleted, "Main");

        assertEquals("Main", cfg.getCurrentSceneName());
        assertNotNull(cfg.getSceneMeta("Main"));
        assertEquals(deleted.getFile(), cfg.getSceneMeta("Main").getFile());
    }

    @Test
    public void cleanupFailedNewProjectDir_doesNotDeletePreExistingDirectory() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-existing-project-dir");
        FileHandle projectDir = new FileHandle(dir.toFile());
        projectDir.child("keep.txt").writeString("keep", false, "UTF-8");

        SceneService.cleanupFailedNewProjectDir(projectDir, true);

        assertTrue(projectDir.exists());
        assertTrue(projectDir.child("keep.txt").exists());
    }

    @Test
    public void cleanupFailedNewProjectDir_deletesOnlyDirectoryCreatedByAttempt() throws Exception {
        Path root = Files.createTempDirectory("scene-service-created-project-dir");
        FileHandle projectDir = new FileHandle(root.resolve("created-project").toFile());
        projectDir.mkdirs();
        projectDir.child("partial.txt").writeString("partial", false, "UTF-8");

        SceneService.cleanupFailedNewProjectDir(projectDir, false);

        assertFalse(projectDir.exists());
    }

    @Test
    public void attachRollbackFailure_keepsPrimaryAndAddsSuppressedRollbackError() {
        RuntimeException primary = new RuntimeException("main failure");
        RuntimeException rollback = new RuntimeException("rollback failure");

        RuntimeException merged = SceneService.attachRollbackFailure(primary, rollback);

        assertSame(primary, merged);
        assertEquals(1, merged.getSuppressed().length);
        assertSame(rollback, merged.getSuppressed()[0]);
    }

    @Test
    public void rollbackSceneSwitchConfigPointer_restoresPreviousCurrentScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        cfg.setCurrentSceneByName("Second");

        boolean restored = SceneService.rollbackSceneSwitchConfigPointer(cfg, "Main");

        assertTrue(restored);
        assertEquals("Main", cfg.getCurrentSceneName());
        assertNotNull(cfg.getSceneMeta("Second"));
    }

    @Test
    public void rollbackSceneCreateState_removesGhostSceneFileAndRestoresCurrentScene() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-rollback-create");
        FileHandle projectDir = new FileHandle(dir.toFile());
        projectDir.child("scenes").mkdirs();

        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("BrokenScene");
        SceneMeta broken = cfg.getSceneMeta("BrokenScene");
        String brokenFile = broken.getFile();
        projectDir.child("scenes").child(brokenFile).writeString("{\"scene\":\"partial\"}", false, "UTF-8");

        SceneService.rollbackSceneCreateState(cfg, "Main", "BrokenScene", brokenFile, projectDir);

        assertEquals("Main", cfg.getCurrentSceneName());
        assertNull(cfg.getSceneMeta("BrokenScene"));
        assertFalse(projectDir.child("scenes").child(brokenFile).exists());
    }

    @Test
    public void tmxActivationFailure_rollsBackMaterializedImportAndRestoresPreviousScene() throws Exception {
        Path dir = Files.createTempDirectory("scene-service-tmx-activation-failure");
        FileHandle projectDir = new FileHandle(dir.toFile());
        projectDir.child(StudioFs.DIR_SCENES).mkdirs();
        projectDir.child(StudioFs.DIR_ORIG_TILES).mkdirs();
        projectDir.child(StudioFs.DIR_ATLASES).mkdirs();

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "TMX Activation Failure";
        cfg.projectFileName = "tmx-activation-failure";
        cfg.projectDirectoryPath = dir.toString();
        cfg.exportRootPathDir = dir.resolve("export").toString();
        cfg.createSceneMeta("Main");
        ProjectConfig.setInstance(cfg);

        AssetMetaDatabase db = new AssetMetaDatabase();
        db.save(projectDir.child(StudioFs.FILE_ASSETS_JSON));
        ProjectConfig.ProjectIO.saveProject(cfg, StudioFs.requireStudioProjectFile(cfg));
        writePng(projectDir.child("terrain.png"), 16, 16);
        FileHandle tmx = projectDir.child("map.tmx");
        tmx.writeString("""
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """, false, "UTF-8");

        TmxSceneImportResult result = new TmxSceneImportService(cfg, projectDir, db)
                .importScene(new TmxSceneImportRequest(tmx, "Imported", false));
        assertTrue(result.imported());
        assertNotNull(result.rollback());
        assertNotNull(cfg.getSceneMeta("Imported"));
        FileHandle importedSceneFile = projectDir.child(StudioFs.DIR_SCENES).child(result.sceneFileName());
        assertTrue(importedSceneFile.exists());

        AtomicBoolean previousRestored = new AtomicBoolean(false);
        IllegalStateException failure = SceneService.recoverTmxImportActivationFailure(
                result,
                "Main",
                new RuntimeException("activation boom"),
                () -> {
                    previousRestored.set(true);
                    cfg.setCurrentSceneByName("Main");
                }
        );

        assertTrue(failure.getMessage().contains("rolled back"));
        assertTrue(previousRestored.get());
        assertEquals("Main", cfg.getCurrentSceneName());
        assertNull(cfg.getSceneMeta("Imported"));
        assertFalse(importedSceneFile.exists());
        assertFalse(projectDir.child(StudioFs.DIR_ORIG_TILES).child("terrain").exists());
        assertEquals(0, db.assets.size);
    }


    @Test
    public void deleteSceneTransition_nonActiveDeleteKeepsCurrentScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        cfg.setCurrentSceneByName("Main");

        boolean removed = cfg.removeSceneMeta("Second");

        assertTrue(removed);
        assertEquals("Main", cfg.getCurrentSceneName());
        assertNull(cfg.getSceneMeta("Second"));
    }

    @Test
    public void deleteSceneTransition_activeDeleteMovesCurrentToRemainingScene() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Second");
        cfg.setCurrentSceneByName("Main");

        boolean removed = cfg.removeSceneMeta("Main");

        assertTrue(removed);
        assertEquals("Second", cfg.getCurrentSceneName());
        assertNotNull(cfg.getSceneMeta("Second"));
    }

    @Test
    public void resetProjectConfigToEmptyState_replacesSingletonWithSafeEmptyConfig() {
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Before";
        cfg.createSceneMeta("Main");
        ProjectConfig.setInstance(cfg);

        SceneService.resetProjectConfigToEmptyState();

        ProjectConfig reset = ProjectConfig.getInstance();
        assertEquals(0, reset.getSceneNames().size);
        assertNull(reset.getCurrentSceneName());
    }

    private static void writePng(FileHandle file, int width, int height) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(0.2f, 0.6f, 0.3f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

}
