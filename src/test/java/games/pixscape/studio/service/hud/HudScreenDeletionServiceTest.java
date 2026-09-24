package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAssetLoader;
import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HudScreenDeletionServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void deletesUnusedDescriptorAndItsActualDocumentButKeepsSharedResources() throws Exception {
        Fixture fixture = fixture();
        fixture.root.child("orig/images/shared.png").writeBytes(new byte[] {1, 2, 3}, false);

        HudScreenDeletionService.DeletionResult result = fixture.service.delete(
                fixture.root, fixture.configuration, "hud/status");

        assertEquals("hud/status", result.screenId());
        assertTrue(result.documentDeleted());
        assertTrue(result.deletionAcquired());
        assertFalse(result.hasCleanupWarning());
        assertFalse(fixture.root.child("hud/status.hudscreen").exists());
        assertFalse(fixture.root.child("hud/status.json").exists());
        assertTrue(fixture.root.child("orig/images/shared.png").exists());
    }

    @Test public void blocksHudAssociatedWithClosedPersistedSceneAndReportsItsAssociation() throws Exception {
        Fixture fixture = fixture();
        fixture.configuration.getSceneMeta("Closed Scene").defaultHudScreenId = "hud\\status";
        save(fixture);

        HudScreenDeletionService.HudScreenInUseException failure = assertThrows(
                HudScreenDeletionService.HudScreenInUseException.class,
                () -> fixture.service.delete(fixture.root, fixture.configuration, "hud/status"));

        assertEquals(1, failure.usage().references().size());
        assertEquals("Closed Scene", failure.usage().references().get(0).sceneName());
        assertEquals(HudScreenDeletionService.SCENE_ASSOCIATION,
                failure.usage().references().get(0).referenceType());
        assertTrue(fixture.root.child("hud/status.hudscreen").exists());
    }

    @Test public void blocksLiveUnsavedAdditionAndPersistedReferenceRemovedOnlyInMemory() throws Exception {
        Fixture fixture = fixture();
        fixture.configuration.getSceneMeta("Open Scene").defaultHudScreenId = "hud/status";
        assertTrue(fixture.service.inspectUsage(fixture.root, fixture.configuration, "hud/status").used());

        fixture.configuration.getSceneMeta("Open Scene").defaultHudScreenId = null;
        fixture.configuration.getSceneMeta("Closed Scene").defaultHudScreenId = "hud/status";
        save(fixture);
        fixture.configuration.getSceneMeta("Closed Scene").defaultHudScreenId = null;

        HudScreenDeletionService.UsageReport usage = fixture.service.inspectUsage(
                fixture.root, fixture.configuration, "hud/status");
        assertEquals(1, usage.references().size());
        assertEquals("Closed Scene", usage.references().get(0).sceneName());
    }

    @Test public void reportsAllUsingScenesWithoutDuplicatesAcrossLiveAndPersistedMetadata() throws Exception {
        Fixture fixture = fixture();
        fixture.configuration.getSceneMeta("Closed Scene").defaultHudScreenId = "hud/status";
        fixture.configuration.getSceneMeta("Open Scene").defaultHudScreenId = "hud/status";
        save(fixture);

        HudScreenDeletionService.UsageReport usage = fixture.service.inspectUsage(
                fixture.root, fixture.configuration, "hud/status");

        assertEquals(2, usage.references().size());
        assertEquals("Closed Scene", usage.references().get(0).sceneName());
        assertEquals("Open Scene", usage.references().get(1).sceneName());
    }

    @Test public void preservesSharedHudDocumentWhenAnotherDescriptorUsesIt() throws Exception {
        Fixture fixture = fixture();
        fixture.root.child("hud/other.hudscreen").writeString(
                fixture.root.child("hud/status.hudscreen").readString("UTF-8"), false, "UTF-8");

        HudScreenDeletionService.DeletionResult result = fixture.service.delete(
                fixture.root, fixture.configuration, "hud/status");

        assertFalse(result.documentDeleted());
        assertFalse(fixture.root.child("hud/status.hudscreen").exists());
        assertTrue(fixture.root.child("hud/status.json").exists());
        assertTrue(fixture.root.child("hud/other.hudscreen").exists());
    }

    @Test public void rollsBackDescriptorWhenDocumentStagingFails() throws Exception {
        Fixture fixture = fixture();
        AtomicInteger moves = new AtomicInteger();
        HudScreenDeletionService failing = new HudScreenDeletionService(new HudScreenAssetLoader(),
                (source, target) -> {
                    if (moves.incrementAndGet() == 2) throw new IOException("simulated document move failure");
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> failing.delete(fixture.root, fixture.configuration, "hud/status"));

        assertTrue(failure.getMessage().contains("without leaving a broken descriptor"));
        assertTrue(fixture.root.child("hud/status.hudscreen").exists());
        assertTrue(fixture.root.child("hud/status.json").exists());
    }

    @Test public void succeedsWhenSecondTemporaryFileCleanupFailsAfterAllMoves() throws Exception {
        Fixture fixture = fixture();
        AtomicInteger cleanupAttempts = new AtomicInteger();
        HudScreenDeletionService service = new HudScreenDeletionService(new HudScreenAssetLoader(),
                (source, target) -> Files.move(source, target, StandardCopyOption.REPLACE_EXISTING),
                target -> {
                    if (cleanupAttempts.incrementAndGet() == 2) {
                        throw new IOException("simulated document cleanup failure");
                    }
                    Files.delete(target);
                });

        HudScreenDeletionService.DeletionResult result = service.delete(
                fixture.root, fixture.configuration, "hud/status");

        assertTrue(result.deletionAcquired());
        assertTrue(result.hasCleanupWarning());
        assertEquals(2, cleanupAttempts.get());
        assertEquals(1, result.temporaryFilesRemaining().size());
        assertTrue(Files.exists(Path.of(result.temporaryFilesRemaining().get(0))));
        assertFalse(fixture.root.child("hud/status.hudscreen").exists());
        assertFalse(fixture.root.child("hud/status.json").exists());
        assertEquals(0, HudScreenAssetBrowser.scan(fixture.root).size);
    }

    @Test public void continuesTemporaryCleanupAfterFirstFailure() throws Exception {
        Fixture fixture = fixture();
        AtomicInteger cleanupAttempts = new AtomicInteger();
        HudScreenDeletionService service = new HudScreenDeletionService(new HudScreenAssetLoader(),
                (source, target) -> Files.move(source, target, StandardCopyOption.REPLACE_EXISTING),
                target -> {
                    if (cleanupAttempts.incrementAndGet() == 1) {
                        throw new IOException("simulated descriptor cleanup failure");
                    }
                    Files.delete(target);
                });

        HudScreenDeletionService.DeletionResult result = service.delete(
                fixture.root, fixture.configuration, "hud/status");

        assertTrue(result.deletionAcquired());
        assertEquals(2, cleanupAttempts.get());
        assertEquals(1, result.temporaryFilesRemaining().size());
        assertTrue(Files.exists(Path.of(result.temporaryFilesRemaining().get(0))));
        assertFalse(fixture.root.child("hud/status.hudscreen").exists());
        assertFalse(fixture.root.child("hud/status.json").exists());
        assertEquals(0, HudScreenAssetBrowser.scan(fixture.root).size);
    }

    @Test public void blocksDeletionWhenPersistedSceneMetadataCannotBeRead() throws Exception {
        Fixture fixture = fixture();
        fixture.root.child("project.json").writeString("{ not valid JSON", false, "UTF-8");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> fixture.service.delete(fixture.root, fixture.configuration, "hud/status"));

        assertTrue(failure.getMessage().contains("cannot be read"));
        assertTrue(fixture.root.child("hud/status.hudscreen").exists());
        assertTrue(fixture.root.child("hud/status.json").exists());
    }

    private Fixture fixture() throws Exception {
        FileHandle root = new FileHandle(temporary.newFolder());
        ProjectConfig configuration = new ProjectConfig();
        configuration.projectTitle = "HUD deletion";
        configuration.projectFileName = "project";
        configuration.projectDirectoryPath = root.path();
        configuration.exportRootPathDir = "build/export";
        configuration.createSceneMeta("Open Scene");
        configuration.createSceneMeta("Closed Scene");
        new HudScreenAssetAuthoringService().create(root, "status");
        Fixture fixture = new Fixture(root, configuration, new HudScreenDeletionService());
        save(fixture);
        return fixture;
    }

    private static void save(Fixture fixture) {
        ProjectConfig.ProjectIO.saveProject(fixture.configuration, fixture.root.child("project.json"));
    }

    private record Fixture(FileHandle root, ProjectConfig configuration, HudScreenDeletionService service) {}
}
