package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioIO;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AtlasRepackRecoveryIntegrationTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass public static void app() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test public void failedSameNamePixelChangeRepacksAfterRestartThenSkips() throws Exception {
        ProjectConfig previous = ProjectConfig.getInstance();
        FileHandle project = new FileHandle(temporaryFolder.newFolder("project"));
        FileHandle source = project.child("images/A.png");
        FileHandle input = project.child("atlases/input/scene/A.png");
        FileHandle atlases = project.child("atlases");
        FileHandle atlas = atlases.child("scene.atlas");
        AtomicInteger packs = new AtomicInteger();
        AtomicBoolean failNextPack = new AtomicBoolean(true);
        ProjectConfig config = new ProjectConfig();
        config.projectDirectoryPath = project.path();
        ProjectConfig.setInstance(config);
        try {
            writePng(source, 0xffff0000);
            assertTrue(StudioIO.copyIfDifferent(source, input));
            SceneAtlasLoaderService.packSceneAtlasToDirectory(config, "scene", project, atlases);
            assertEquals(0xffff0000, regionPixel(atlas, "A"));

            Harness first = new Harness(project, packs, failNextPack);
            try {
                assertFalse(save(first, source, input, project, atlas));
                assertEquals(0, packs.get());

                writePng(source, 0xff267bd9);
                assertTrue(source.file().setLastModified(input.lastModified() + 2000L));
                try {
                    save(first, source, input, project, atlas);
                    fail("The failed pack must fail the save");
                } catch (IllegalStateException expected) {
                    assertTrue(expected.getMessage().contains("pack failed"));
                }
                assertEquals(1, packs.get());
                assertEquals(0xffff0000, regionPixel(atlas, "A"));
                assertTrue(marker(project).exists());
            } finally {
                first.service.disposeAsyncPack();
            }

            Harness restarted = new Harness(project, packs, failNextPack);
            try {
                assertTrue(save(restarted, source, input, project, atlas));
                assertEquals(2, packs.get());
                assertEquals(0xff267bd9, regionPixel(atlas, "A"));
                assertFalse(marker(project).exists());
                assertFalse(save(restarted, source, input, project, atlas));
                assertEquals(2, packs.get());
            } finally {
                restarted.service.disposeAsyncPack();
            }
            Harness cleanStartup = new Harness(project, packs, failNextPack);
            try {
                assertFalse(save(cleanStartup, source, input, project, atlas));
                assertEquals(2, packs.get());
            } finally {
                cleanStartup.service.disposeAsyncPack();
            }
        } finally {
            ProjectConfig.setInstance(previous);
        }
    }

    private static boolean save(Harness harness, FileHandle source, FileHandle input,
                                FileHandle project, FileHandle atlas) throws Exception {
        boolean inputChanged = StudioIO.copyIfDifferent(source, input);
        if (inputChanged) {
            // Keep the source/destination metadata stable for the following unchanged save.
            assertTrue(input.file().setLastModified(source.lastModified()));
            harness.service.markRepackRequired(project, "scene");
        }
        if (!inputChanged && !harness.service.hasAsyncPackQueuedOrRunningFor("scene")
                && harness.service.coversCurrentInput("scene", input.parent(), atlas)) {
            return false;
        }
        long generation = harness.service.requestAsyncPack("scene",
                AsyncAtlasRepackCoordinator.RepackReason.SAVE);
        harness.service.updateAsyncPack();
        harness.executor.runNext();
        harness.service.applyIfPackReady();
        AtlasStudioService.PackCompletion completion = harness.service.lastPackCompletion();
        if (completion == null || completion.generation() != generation) {
            throw new IllegalStateException("Pack ended without publication");
        }
        if (completion.failure() != null) throw new IllegalStateException("Atlas pack failed",
                completion.failure());
        return true;
    }

    private static FileHandle marker(FileHandle project) {
        return project.child("atlases/input/scene/.repack-required");
    }

    private static int regionPixel(FileHandle atlas, String name) throws Exception {
        TextureAtlasData data = new TextureAtlasData(atlas, atlas.parent(), false);
        for (TextureAtlasData.Region region : data.getRegions()) {
            if (name.equals(region.name)) {
                BufferedImage page = ImageIO.read(region.page.textureFile.file());
                return page.getRGB(region.left + 4, region.top + 4);
            }
        }
        throw new AssertionError("Missing region " + name);
    }

    private static void writePng(FileHandle file, int color) throws Exception {
        file.parent().mkdirs();
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, color);
        ImageIO.write(image, "png", file.file());
    }

    private static final class Harness {
        final ControlledAtlasExecutor executor = new ControlledAtlasExecutor();
        final AtlasStudioService service;

        Harness(FileHandle project, AtomicInteger packs, AtomicBoolean failNextPack) {
            service = new AtlasStudioService(null, (tag, generation, reason) -> {
                packs.incrementAndGet();
                if (failNextPack.getAndSet(false)) throw new IllegalStateException("pack failed");
                FileHandle output = project.child("atlases/.tmp/" + tag + "-gen" + generation);
                SceneAtlasCoverage.Validation validation =
                        SceneAtlasLoaderService.packSceneAtlasToDirectory(
                                ProjectConfig.getInstance(), tag, project, output);
                return new AtlasStudioService.ScenePrepared(tag, output, output.child(tag + ".atlas"),
                        validation, null, false);
            }, executor, () -> 0L, (artifact, atlasesDir) -> {
                // The prepared files are private to this generation until this callback publishes them.
                for (FileHandle file : artifact.outputDir().list()) {
                    if (!file.isDirectory()) file.copyTo(atlasesDir.child(file.name()));
                }
            });
        }
    }
}
