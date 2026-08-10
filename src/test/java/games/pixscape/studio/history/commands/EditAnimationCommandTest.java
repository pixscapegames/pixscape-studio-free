package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class EditAnimationCommandTest {
    @Test
    public void undoRedoRestoresAvailableAndActiveAnimations() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(17);
        animation.animationAssetIds.add(31);
        animation.currentClip = "idle";
        animation.fps = 12f;
        animation.frame = 2;
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = 17;

        EditAnimationCommand.Snapshot before =
                EditAnimationCommand.Snapshot.capture(animation, assetRef);
        EditAnimationCommand.Snapshot after = before.copy();
        after.activeAssetId = 31;
        after.currentClip = "run";
        after.fps = 24f;
        after.stateTime = 0f;
        after.frame = -1;
        AtomicInteger refreshes = new AtomicInteger();

        history.execute(new EditAnimationCommand(
                world, history.historyIds(), entityId, before, after,
                ignored -> refreshes.incrementAndGet(), null));
        assertEquals(31, assetRef.assetId);
        assertArrayEquals(new int[]{17, 31}, animation.animationAssetIds.toArray());
        assertEquals("run", animation.currentClip);
        assertEquals(24f, animation.fps, 0f);

        history.undo();
        assertEquals(17, assetRef.assetId);
        assertArrayEquals(new int[]{17, 31}, animation.animationAssetIds.toArray());
        assertEquals("idle", animation.currentClip);
        assertEquals(2, animation.frame);

        history.redo();
        assertEquals(31, assetRef.assetId);
        assertEquals(3, refreshes.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateAnimationAssetIdsAreRejected() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(17);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = 17;
        EditAnimationCommand.Snapshot before =
                EditAnimationCommand.Snapshot.capture(animation, assetRef);
        EditAnimationCommand.Snapshot after = before.copy();
        after.animationAssetIds.add(17);
        new EditAnimationCommand(
                world, history.historyIds(), entityId, before, after, null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void finalAnimationCannotBeRemoved() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(17);
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = 17;
        EditAnimationCommand.Snapshot before =
                EditAnimationCommand.Snapshot.capture(animation, assetRef);
        EditAnimationCommand.Snapshot after = before.copy();
        after.animationAssetIds.clear();
        new EditAnimationCommand(
                world, history.historyIds(), entityId, before, after, null, null);
    }
}
