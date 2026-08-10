package games.pixscape.studio.service.asset;

import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.studio.asset.*;
import org.junit.Test;

import static org.junit.Assert.*;

public class StudioAnimationAssetsTest {
    @Test
    public void registryCopiesAuthoredAnimationAssetDefinitions() {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta animation = (AnimationAssetMeta) database.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero",
                AssetMeta.AssetScope.USER
        );
        animation.frameCount = 6;
        animation.fps = 18f;
        animation.currentClip = "run";
        AnimationClipMeta run = new AnimationClipMeta(2, 5);
        run.flipX = true;
        animation.clips.put("run", run);

        AnimationRegistry registry = StudioAnimationAssets.buildRegistry(database);
        AnimationDef definition = registry.getByAssetId(animation.id());

        assertNotNull(definition);
        assertEquals("hero", definition.name());
        assertEquals(18f, definition.fps(), 0f);
        assertEquals("run", definition.currentClip());
        assertEquals(2, definition.clip("run").start());
        assertEquals(5, definition.clip("run").end());
        assertTrue(definition.clip("run").flipX());
    }

    @Test(expected = IllegalArgumentException.class)
    public void validationRejectsMissingAuthoredClips() {
        AnimationAssetMeta animation = new AnimationAssetMeta(
                17,
                "animations/hero",
                "orig/animations/hero",
                AssetMeta.AssetScope.USER
        );
        animation.frameCount = 6;
        animation.fps = 12f;
        animation.currentClip = "default";

        StudioAnimationAssets.validate(animation);
    }

    @Test(expected = IllegalArgumentException.class)
    public void validationRejectsUnavailableConfiguredCurrentClip() {
        AnimationAssetMeta animation = new AnimationAssetMeta(
                17,
                "animations/hero",
                "orig/animations/hero",
                AssetMeta.AssetScope.USER
        );
        animation.frameCount = 6;
        animation.fps = 12f;
        animation.currentClip = "removed";
        animation.clips.put("run", new AnimationClipMeta(0, 5));

        StudioAnimationAssets.validate(animation);
    }

    @Test
    public void editableCopyDoesNotShareAuthoredClipState() {
        AnimationAssetMeta original = new AnimationAssetMeta(
                17,
                "animations/hero",
                "orig/animations/hero",
                AssetMeta.AssetScope.USER
        );
        original.frameCount = 6;
        original.fps = 12f;
        original.currentClip = "run";
        original.clips.put("run", new AnimationClipMeta(0, 5));

        AnimationAssetMeta edited = StudioAnimationAssets.copyOf(original);
        edited.currentClip = "idle";
        edited.clips.get("run").start = 3;
        edited.clips.put("idle", new AnimationClipMeta(0, 1));

        assertEquals("run", original.currentClip);
        assertEquals(0, original.clips.get("run").start);
        assertFalse(original.clips.containsKey("idle"));
    }
}
