package games.pixscape.studio.ui.importer;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.importer.tmx.TmxDiagnostic;
import games.pixscape.studio.importer.tmx.TmxDiagnosticSeverity;
import games.pixscape.studio.importer.tmx.TmxImportPlanStatus;
import games.pixscape.studio.importer.tmx.TmxSceneImportResult;
import games.pixscape.studio.importer.tmx.TmxSceneImportStatus;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TmxImportUiSupportTest {

    @Test
    public void resolveSceneName_usesTrimmedInputOrPlannerDefault() {
        assertEquals("MapFromPlanner", TmxImportUiSupport.resolveSceneName("MapFromPlanner", null));
        assertEquals("CustomName", TmxImportUiSupport.resolveSceneName("MapFromPlanner", "  CustomName  "));

        try {
            TmxImportUiSupport.resolveSceneName("MapFromPlanner", "   ");
            fail("Blank scene names must be rejected.");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Scene name"));
        }
    }

    @Test
    public void formatDiagnostics_ordersBlockingBeforeWarningsAndShowsLongLists() {
        List<TmxDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.add(new TmxDiagnostic(TmxDiagnosticSeverity.WARNING, "TMX_WARNING", "warning", "layer"));
        diagnostics.add(new TmxDiagnostic(TmxDiagnosticSeverity.INFO, "TMX_INFO", "info", "map"));
        diagnostics.add(new TmxDiagnostic(TmxDiagnosticSeverity.BLOCKING, "TMX_BLOCKING", "blocking", "tileset"));
        for (int i = 0; i < 7; i++) {
            diagnostics.add(new TmxDiagnostic(TmxDiagnosticSeverity.INFO, "TMX_EXTRA_" + i, "extra " + i, null));
        }

        String formatted = TmxImportUiSupport.formatDiagnostics(diagnostics);

        assertTrue(formatted.indexOf("TMX_BLOCKING") < formatted.indexOf("TMX_WARNING"));
        assertTrue(formatted.contains("- BLOCKING TMX_BLOCKING: blocking (tileset)"));
        assertTrue(formatted.contains("TMX_EXTRA_6"));
        assertFalse(formatted.contains("and 2 more..."));
        assertEquals("No diagnostics.", TmxImportUiSupport.formatDiagnostics(List.of()));
    }

    @Test
    public void resultMessages_keepSuccessAndFailureClear() {
        TmxDiagnostic warning = new TmxDiagnostic(
                TmxDiagnosticSeverity.WARNING,
                "TMX_OBJECT_LAYER_OUT_OF_SCOPE",
                "Object layers are reported but not imported.",
                "Objects"
        );
        TmxSceneImportResult success = new TmxSceneImportResult(
                TmxSceneImportStatus.IMPORTED,
                null,
                "Village",
                "Village.json",
                "Village",
                1,
                2,
                3,
                4,
                List.of(warning),
                null,
                false,
                false,
                null
        );

        String successMessage = TmxImportUiSupport.formatSuccessMessage(success);
        assertTrue(successMessage.contains("Village"));
        assertTrue(successMessage.contains("1 warning"));

        TmxDiagnostic blocking = new TmxDiagnostic(
                TmxDiagnosticSeverity.BLOCKING,
                "TMX_MISSING_IMAGE",
                "Tileset image is missing.",
                "terrain"
        );
        TmxSceneImportResult failure = new TmxSceneImportResult(
                TmxSceneImportStatus.PREFLIGHT_FAILED,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                List.of(blocking),
                null,
                false,
                false,
                null
        );

        String failureMessage = TmxImportUiSupport.formatFailureMessage(failure);
        assertTrue(failureMessage.contains("PREFLIGHT_FAILED"));
        assertTrue(failureMessage.contains("TMX_MISSING_IMAGE"));
    }

    @Test
    public void prepare_preflightsWithoutCreatingProjectFiles() throws Exception {
        Path dir = Files.createTempDirectory("tmx-ui-preflight");
        writeFile(dir.resolve("terrain.png"), "fake image");
        FileHandle tmx = writeFile(dir.resolve("map.tmx"), """
                <map orientation="orthogonal" width="1" height="1" tilewidth="16" tileheight="16">
                  <tileset firstgid="1" name="terrain" tilewidth="16" tileheight="16" tilecount="1" columns="1">
                    <image source="terrain.png" width="16" height="16"/>
                  </tileset>
                  <layer name="Ground" width="1" height="1"><data encoding="csv">1</data></layer>
                </map>
                """);

        TmxImportUiSupport.TmxImportPreparation preparation = TmxImportUiSupport.prepare(tmx);

        assertEquals(TmxImportPlanStatus.PLAN_CREATED, preparation.planResult().status());
        assertTrue(preparation.planResult().hasPlan());
        assertFalse(preparation.hasBlockingDiagnostics());
        assertEquals("Map", preparation.proposedSceneName());
        assertFalse(Files.exists(dir.resolve(StudioFs.FILE_ASSETS_JSON)));
        assertFalse(Files.exists(dir.resolve(StudioFs.DIR_SCENES)));
    }

    private static FileHandle writeFile(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return new FileHandle(path.toFile());
    }
}
