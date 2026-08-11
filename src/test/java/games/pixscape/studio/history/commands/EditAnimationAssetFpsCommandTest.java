package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import org.junit.Test;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EditAnimationAssetFpsCommandTest {

    @Test
    public void redoUndoRedoSynchronizesMetadataAndActiveRuntimeFps() throws Exception {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta asset = asset(database, "walk", 12f);
        int entityId = createAnimationEntity(world, asset.id(), 12f, asset.id());
        AnimationComponent animation = world.getMapper(AnimationComponent.class).get(entityId);
        AtomicInteger previews = new AtomicInteger();
        AtomicInteger saveRequests = new AtomicInteger();

        history.execute(command(world, history, entityId, asset.id(), 12f, 8f,
                service(database), previews, saveRequests));
        assertEquals(8f, asset.fps, 0f);
        assertEquals(8f, animation.fps, 0f);

        history.undo();
        assertEquals(12f, asset.fps, 0f);
        assertEquals(12f, animation.fps, 0f);

        history.redo();
        assertEquals(8f, asset.fps, 0f);
        assertEquals(8f, animation.fps, 0f);
        assertEquals(3, previews.get());
        assertEquals(3, saveRequests.get());
    }

    @Test
    public void editingInactiveAssetDoesNotOverwriteActiveRuntimeFps() throws Exception {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta active = asset(database, "idle", 6f);
        AnimationAssetMeta inactive = asset(database, "walk", 12f);
        int entityId = createAnimationEntity(
                world, active.id(), 6f, active.id(), inactive.id());
        AnimationComponent animation = world.getMapper(AnimationComponent.class).get(entityId);
        AtomicInteger previews = new AtomicInteger();
        AtomicInteger saveRequests = new AtomicInteger();

        history.execute(command(world, history, entityId, inactive.id(), 12f, 18f,
                service(database), previews, saveRequests));

        assertEquals(18f, inactive.fps, 0f);
        assertEquals(6f, animation.fps, 0f);
        assertEquals(0, previews.get());
        assertEquals(0, saveRequests.get());
    }

    @Test
    public void undoAfterSwitchRestoresMetadataWithoutOverwritingNewActiveFps() throws Exception {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta first = asset(database, "idle", 12f);
        AnimationAssetMeta second = asset(database, "walk", 6f);
        int entityId = createAnimationEntity(
                world, first.id(), 12f, first.id(), second.id());
        AnimationComponent animation = world.getMapper(AnimationComponent.class).get(entityId);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).get(entityId);

        history.execute(command(world, history, entityId, first.id(), 12f, 8f,
                service(database), new AtomicInteger(), new AtomicInteger()));
        assetRef.assetId = second.id();
        animation.fps = 6f;

        history.undo();

        assertEquals(12f, first.fps, 0f);
        assertEquals(6f, animation.fps, 0f);
    }

    @Test
    public void equalFpsIsSuppressedAsNoop() throws Exception {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta asset = asset(database, "walk", 12f);
        int entityId = createAnimationEntity(world, asset.id(), 12f, asset.id());
        EditAnimationAssetFpsCommand command = new EditAnimationAssetFpsCommand(
                world, history.historyIds(), entityId, asset.id(), 12f, 12f,
                service(database), null, null);

        assertTrue(command.isNoop());
        history.execute(command);

        assertFalse(history.canUndo());
        assertEquals(12f, asset.fps, 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonFiniteFpsIsRejected() throws Exception {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta asset = asset(database, "walk", 12f);
        int entityId = createAnimationEntity(world, asset.id(), 12f, asset.id());
        new EditAnimationAssetFpsCommand(
                world, history.historyIds(), entityId, asset.id(),
                12f, Float.NaN, service(database), null, null);
    }

    private static EditAnimationAssetFpsCommand command(
            World world,
            HistoryManager history,
            int entityId,
            int assetId,
            float before,
            float after,
            AnimationAssetAuthoringService service,
            AtomicInteger previews,
            AtomicInteger saveRequests) {
        return new EditAnimationAssetFpsCommand(
                world, history.historyIds(), entityId, assetId, before, after,
                service, ignored -> previews.incrementAndGet(), saveRequests::incrementAndGet);
    }

    private static AnimationAssetAuthoringService service(AssetMetaDatabase database)
            throws Exception {
        FileHandle file = new FileHandle(
                Files.createTempDirectory("animation-fps-command").resolve("assets.json").toFile());
        return new AnimationAssetAuthoringService(
                () -> database, () -> file, ignored -> {
                });
    }

    private static int createAnimationEntity(World world,
                                             int activeAssetId,
                                             float fps,
                                             int... assetIds) {
        int entityId = world.create();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.addAll(assetIds);
        animation.currentClip = "default";
        animation.fps = fps;
        world.getMapper(AssetRefComponent.class).create(entityId).assetId = activeAssetId;
        return entityId;
    }

    private static AnimationAssetMeta asset(
            AssetMetaDatabase database, String name, float fps) {
        AnimationAssetMeta asset = (AnimationAssetMeta) database.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/" + name,
                "orig/animations/" + name,
                AssetMeta.AssetScope.USER);
        asset.frameCount = 2;
        asset.fps = fps;
        asset.currentClip = "default";
        asset.clips.put("default", new AnimationClipMeta(0, 1));
        return asset;
    }
}
