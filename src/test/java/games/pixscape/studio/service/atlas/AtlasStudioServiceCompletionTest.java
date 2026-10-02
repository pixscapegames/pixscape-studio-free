package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class AtlasStudioServiceCompletionTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass public static void app() {
        if (Gdx.app == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test public void workerAndInvalidPublicationFailuresNeverBecomeSuccessOrRepackLoops()
            throws Exception {
        ControlledAtlasExecutor executor = new ControlledAtlasExecutor();
        FileHandle output = new FileHandle(temporaryFolder.newFolder("output"));
        AtlasStudioService service = new AtlasStudioService(null, (tag, generation, reason) -> {
            if (generation == 1 || generation == 4) {
                throw new IllegalStateException("worker failed");
            }
            return new AtlasStudioService.ScenePrepared(tag, output.child("gen" + generation),
                    output.child("gen" + generation + "/scene.atlas"),
                    null, null, generation == 3);
        }, executor, () -> 0L);
        try {
            for (long generation = 1; generation <= 2; generation++) {
                assertEquals(generation, service.requestAsyncPack("scene",
                        AsyncAtlasRepackCoordinator.RepackReason.SAVE));
                service.updateAsyncPack();
                executor.runNext();
                service.applyIfPackReady();
                assertEquals(generation, service.lastPackCompletion().generation());
                assertNotNull(service.lastPackCompletion().failure());
                assertFalse(service.hasAsyncPackQueuedOrRunningFor("scene"));
            }

            assertEquals(3, service.requestAsyncPack("scene",
                    AsyncAtlasRepackCoordinator.RepackReason.SAVE));
            service.updateAsyncPack();
            executor.runNext();
            service.applyIfPackReady();
            assertEquals(4, service.currentPackGeneration());
            assertTrue(service.hasAsyncPackQueuedOrRunningFor("scene"));
            assertEquals(2, service.lastPackCompletion().generation());
            service.updateAsyncPack();
            executor.runNext();
            service.applyIfPackReady();
            assertEquals(4, service.lastPackCompletion().generation());
            assertNotNull(service.lastPackCompletion().failure());
            assertFalse(service.hasAsyncPackQueuedOrRunningFor("scene"));
            assertEquals(0, executor.queued());
        } finally {
            service.disposeAsyncPack();
        }
    }
}
