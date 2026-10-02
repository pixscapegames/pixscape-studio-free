package games.pixscape.studio.service;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import games.pixscape.studio.service.atlas.AtlasInputSyncResult;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.atlas.SceneAtlasCoverage;
import games.pixscape.studio.service.atlas.SceneAtlasLoaderService;
import games.pixscape.studio.io.StudioIO;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class SceneAtlasReuseIntegrationTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass public static void app() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test public void unchangedSaveSkipsAndMissingCarRepacksExactlyOnce() throws Exception {
        FileHandle project = new FileHandle(temporaryFolder.newFolder("project"));
        FileHandle input = project.child("atlases/input/scene");
        FileHandle atlases = project.child("atlases");
        input.mkdirs();
        writePng(input.child("driver.png"), 0xff00ff00);
        SceneAtlasLoaderService.packSceneAtlasToDirectory(null, "scene", project, atlases);
        AtlasStudioService service = new AtlasStudioService(null);
        try {
            assertTrue(skip(project, input, atlases, service));
            assertTrue(skip(project, input, atlases, service));

            writePng(input.child("car.png"), 0xff267bd9);
            assertFalse(skip(project, input, atlases, service));
            SceneAtlasLoaderService.packSceneAtlasToDirectory(null, "scene", project, atlases);
            assertTrue(skip(project, input, atlases, service));
            assertTrue(skip(project, input, atlases, service));

            FileHandle source = project.child("assets/car.png");
            writePng(source, 0xffaa22cc);
            assertTrue(source.file().setLastModified(input.child("car.png").lastModified() + 2000L));
            assertTrue(StudioIO.copyIfDifferent(source, input.child("car.png")));
            assertFalse("External asset edits must request a pack",
                    SceneService.shouldSkipSaveAtlasRepack(project, "scene",
                            new AtlasInputSyncResult(true, 1, 0),
                            service.coversCurrentInput("scene", input, atlases.child("scene.atlas")), false));
        } finally {
            service.disposeAsyncPack();
        }
    }

    @Test public void snapshotRejectsInputsChangedDuringPackAndNewResultCoversThem() throws Exception {
        FileHandle project = new FileHandle(temporaryFolder.newFolder("changing-project"));
        FileHandle input = project.child("atlases/input/scene");
        input.mkdirs();
        FileHandle first = new FileHandle(temporaryFolder.newFolder("first"));
        FileHandle second = new FileHandle(temporaryFolder.newFolder("second"));
        writePng(input.child("driver.png"), 0xff00ff00);
        SceneAtlasCoverage.InputSnapshot atStart = SceneAtlasCoverage.snapshot(input);
        SceneAtlasLoaderService.packSceneAtlasToDirectory(null, "scene", project, first);
        SceneAtlasCoverage.Validation oldResult = SceneAtlasCoverage.validate(atStart, first.child("scene.atlas"));

        writePng(input.child("car.png"), 0xff267bd9);
        assertFalse(oldResult.input().sameFiles(SceneAtlasCoverage.snapshot(input)));
        assertFalse(SceneAtlasCoverage.coversInput(input, first.child("scene.atlas")));
        SceneAtlasLoaderService.packSceneAtlasToDirectory(null, "scene", project, second);
        SceneAtlasCoverage.Validation current = SceneAtlasCoverage.validate(
                SceneAtlasCoverage.snapshot(input), second.child("scene.atlas"));
        assertTrue(current.input().sameFiles(SceneAtlasCoverage.snapshot(input)));
        assertTrue(SceneAtlasCoverage.coversInput(input, second.child("scene.atlas")));
    }

    @Test public void saveCompletionRequiresPublishedGenerationAndPropagatesFailure() {
        assertFalse(SceneService.packPublishedOrThrow("scene", 4, null, true));
        try {
            SceneService.packPublishedOrThrow("scene", 4, null, false);
            fail("Worker termination alone must not be success");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("without publication"));
        }
        RuntimeException failure = new RuntimeException("invalid atlas");
        try {
            SceneService.packPublishedOrThrow("scene", 4,
                    new AtlasStudioService.PackCompletion("scene", 4, failure), false);
            fail("Pack failure must reach save");
        } catch (IllegalStateException expected) {
            assertSame(failure, expected.getCause());
        }
        assertFalse(SceneService.packPublishedOrThrow("scene", 4,
                new AtlasStudioService.PackCompletion("scene", 3, null), true));
        assertTrue(SceneService.packPublishedOrThrow("scene", 4,
                new AtlasStudioService.PackCompletion("scene", 4, null), false));
    }

    private static boolean skip(FileHandle project, FileHandle input, FileHandle atlases,
                                AtlasStudioService service) {
        return SceneService.shouldSkipSaveAtlasRepack(project, "scene",
                AtlasInputSyncResult.unchanged(),
                service.coversCurrentInput("scene", input, atlases.child("scene.atlas")),
                false);
    }

    private static void writePng(FileHandle output, int argb) throws Exception {
        output.parent().mkdirs();
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, argb);
        ImageIO.write(image, "png", output.file());
    }
}
