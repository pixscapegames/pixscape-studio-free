package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.hud.document.*;
import games.pixscape.runtime.loading.RuntimeProjectIO;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;
import org.junit.BeforeClass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RuntimeExportHudMetadataTest {
    @BeforeClass public static void loadNatives() { GdxNativesLoader.load(); }

    @Test
    public void exportPreservesCanonicalDirectHudMetadataAndExportsOnlySelectedHudArtifacts() throws Exception {
        ExportFixture fixture = newFixture();
        fixture.scene().defaultHudScreenId = "  hud\\status  ";
        Path authoredHud = fixture.studioDir().resolve("hud");
        Files.createDirectories(authoredHud);
        Files.writeString(authoredHud.resolve("status.hudscreen"), "{\"schemaVersion\":1,\"documentId\":\"hud/status.json\"}", StandardCharsets.UTF_8);
        Files.writeString(authoredHud.resolve("status.json"),
                new HudDocumentCodec().write(new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))), StandardCharsets.UTF_8);
        Files.writeString(authoredHud.resolve("unrelated.hudscreen"), "{}", StandardCharsets.UTF_8);

        var exported = RuntimeExport.exportRuntime(
                fixture.config(), new FileHandle(fixture.studioDir().toFile()), new FileHandle(fixture.userDir().toFile()));

        assertEquals("hud/status", exported.scenes.get("Main").defaultHudScreenId);
        FileHandle runtimeProject = new FileHandle(fixture.userDir().resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve(RuntimeExport.PROJECT_JSON).toFile());
        JsonValue runtimeScene = new JsonReader().parse(runtimeProject).get("scenes").get("Main");
        assertEquals("hud/status", runtimeScene.getString("defaultHudScreenId"));
        Path runtime = fixture.userDir().resolve(RuntimeExport.RUNTIME_DIR_NAME);
        assertTrue(Files.exists(runtime.resolve("hud/status.hudscreen")));
        assertTrue(Files.exists(runtime.resolve("hud/status.json")));
        assertFalse(Files.exists(runtime.resolve("hud/unrelated.hudscreen")));
        assertTrue(Files.exists(runtime.resolve("atlases/hud/scene1/hud.atlas")));
        assertFalse(new JsonReader().parse(runtimeProject).has("sceneHudFormatVersion"));
        assertEquals("  hud\\status  ", fixture.scene().defaultHudScreenId);
    }

    @Test
    public void exportKeepsAbsentDirectHudAbsent() throws Exception {
        ExportFixture fixture = newFixture();

        var exported = RuntimeExport.exportRuntime(
                fixture.config(), new FileHandle(fixture.studioDir().toFile()), new FileHandle(fixture.userDir().toFile()));

        assertNull(exported.scenes.get("Main").defaultHudScreenId);
        FileHandle runtimeProject = new FileHandle(fixture.userDir().resolve(RuntimeExport.RUNTIME_DIR_NAME)
                .resolve(RuntimeExport.PROJECT_JSON).toFile());
        JsonValue runtimeScene = new JsonReader().parse(runtimeProject).get("scenes").get("Main");
        assertTrue(!runtimeScene.has("defaultHudScreenId")
                || runtimeScene.getString("defaultHudScreenId", null) == null);
        assertFalse(new JsonReader().parse(runtimeProject).has("sceneHudFormatVersion"));
        assertFalse(runtimeProject.parent().child("atlases/hud/scene1").exists());
    }

    private static ExportFixture newFixture() throws Exception {
        Path studioDir = Files.createTempDirectory("runtime-export-hud-metadata-studio");
        Path userDir = Files.createTempDirectory("runtime-export-hud-metadata-user");
        Files.createDirectories(studioDir.resolve(StudioFs.DIR_SCENES));
        Files.writeString(studioDir.resolve(StudioFs.DIR_SCENES).resolve("scene1.json"), "{}", StandardCharsets.UTF_8);
        new AssetMetaDatabase().save(new FileHandle(studioDir.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Runtime Export HUD Metadata";
        cfg.projectFileName = "runtime-export-hud-metadata";
        cfg.exportRootPathDir = userDir.toString();
        cfg.createSceneMeta("Main");
        return new ExportFixture(cfg, cfg.getCurrentSceneMeta(), studioDir, userDir);
    }

    private record ExportFixture(ProjectConfig config, SceneMeta scene, Path studioDir, Path userDir) {
    }
}
