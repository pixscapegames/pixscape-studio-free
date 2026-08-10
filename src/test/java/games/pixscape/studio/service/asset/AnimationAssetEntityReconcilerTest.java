package games.pixscape.studio.service.asset;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AnimationAssetEntityReconcilerTest {
    @Test
    public void validActiveClipIsPreservedWithoutTouchingPlayback() {
        World world = new World(new WorldConfiguration());
        AnimationAssetMeta meta = animationMeta(17, "idle", "idle", "run");
        AnimationComponent animation = entity(world, 17, 17, "run");
        animation.fps = 7f;
        animation.stateTime = 3f;
        animation.frame = 4;
        animation.playing = false;
        animation.loop = false;
        AtomicInteger previews = new AtomicInteger();

        int changed = AnimationAssetEntityReconciler.reconcile(
                world, 17, meta, ignored -> previews.incrementAndGet(), null);

        assertEquals(0, changed);
        assertEquals("run", animation.currentClip);
        assertEquals(7f, animation.fps, 0f);
        assertEquals(3f, animation.stateTime, 0f);
        assertEquals(4, animation.frame);
        assertFalse(animation.playing);
        assertFalse(animation.loop);
        assertEquals(0, previews.get());
    }

    @Test
    public void removedClipUsesAuthoredCurrentClipAndRefreshesImmediately() {
        World world = new World(new WorldConfiguration());
        AnimationAssetMeta meta = animationMeta(17, "run", "idle", "run");
        AnimationComponent animation = entity(world, 17, 17, "removed");
        animation.fps = 9f;
        animation.stateTime = 8f;
        animation.frame = 5;
        animation.playing = false;
        AtomicInteger previews = new AtomicInteger();
        AtomicInteger events = new AtomicInteger();

        int changed = AnimationAssetEntityReconciler.reconcile(
                world,
                17,
                meta,
                ignored -> previews.incrementAndGet(),
                ignored -> events.incrementAndGet());

        assertEquals(1, changed);
        assertEquals("run", animation.currentClip);
        assertEquals(0f, animation.stateTime, 0f);
        assertEquals(-1, animation.frame);
        assertEquals(9f, animation.fps, 0f);
        assertFalse(animation.playing);
        assertEquals(1, previews.get());
        assertEquals(1, events.get());
    }

    @Test
    public void invalidConfiguredCurrentClipUsesDeterministicFirstAuthoredClip() {
        World world = new World(new WorldConfiguration());
        AnimationAssetMeta meta = animationMeta(17, "missing", "zeta", "alpha");
        AnimationComponent animation = entity(world, 17, 17, "removed");

        AnimationAssetEntityReconciler.reconcile(world, 17, meta, null, null);

        assertEquals("alpha", animation.currentClip);
    }

    @Test
    public void nonActiveAttachedAssetIsNotReconciled() {
        World world = new World(new WorldConfiguration());
        AnimationAssetMeta meta = animationMeta(17, "run", "run");
        AnimationComponent animation = entity(world, 31, 17, "other-active-clip");
        animation.stateTime = 2f;
        animation.frame = 3;
        AtomicInteger previews = new AtomicInteger();

        int changed = AnimationAssetEntityReconciler.reconcile(
                world, 17, meta, ignored -> previews.incrementAndGet(), null);

        assertEquals(0, changed);
        assertEquals("other-active-clip", animation.currentClip);
        assertEquals(2f, animation.stateTime, 0f);
        assertEquals(3, animation.frame);
        assertEquals(0, previews.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidEditedMetadataFailsWithoutFabricatingDefaultClip() {
        World world = new World(new WorldConfiguration());
        AnimationAssetMeta meta = new AnimationAssetMeta(
                17, "animations/hero", "orig/animations/hero", AssetMeta.AssetScope.USER);
        meta.frameCount = 4;
        meta.fps = 12f;
        meta.currentClip = "default";
        entity(world, 17, 17, "removed");

        AnimationAssetEntityReconciler.reconcile(world, 17, meta, null, null);
    }

    private static AnimationComponent entity(World world,
                                             int activeAssetId,
                                             int attachedAssetId,
                                             String clip) {
        int entityId = world.create();
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = activeAssetId;
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(activeAssetId);
        if (attachedAssetId != activeAssetId) animation.animationAssetIds.add(attachedAssetId);
        animation.currentClip = clip;
        animation.fps = 12f;
        animation.playing = true;
        animation.loop = true;
        return animation;
    }

    private static AnimationAssetMeta animationMeta(int id,
                                                    String currentClip,
                                                    String... clipNames) {
        AnimationAssetMeta meta = new AnimationAssetMeta(
                id, "animations/hero", "orig/animations/hero", AssetMeta.AssetScope.USER);
        meta.frameCount = 8;
        meta.fps = 12f;
        meta.currentClip = currentClip;
        for (int i = 0; i < clipNames.length; i++) {
            meta.clips.put(clipNames[i], new AnimationClipMeta(i, i));
        }
        return meta;
    }
}
