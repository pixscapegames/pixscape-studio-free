package games.pixscape.studio.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.AnimationComponent;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class AnimationAssetClipsPersistenceTest {

    @Test
    public void saveAndLoad_preservesAnimationAssetClips() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();

        AnimationAssetMeta animation = (AnimationAssetMeta) db.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/hero",
                "orig/animations/hero__a1",
                AssetMeta.AssetScope.USER
        );
        animation.frameCount = 8;
        animation.fps = 10f;
        animation.currentClip = "run";
        animation.clips = new ObjectMap<>();
        animation.clips.put("idle", new AnimationComponent.Clip(0, 1));
        AnimationComponent.Clip run = new AnimationComponent.Clip(2, 7);
        run.flipX = true;
        animation.clips.put("run", run);

        Path tmp = Files.createTempFile("animation-asset-clips", ".json");
        FileHandle file = new FileHandle(tmp.toFile());
        db.save(file);

        AssetMetaDatabase loaded = AssetMetaDatabase.load(file);
        assertTrue(loaded.findByLogicalPath("animations/hero") instanceof AnimationAssetMeta);

        AnimationAssetMeta loadedAnimation =
                (AnimationAssetMeta) loaded.findByLogicalPath("animations/hero");

        assertEquals(8, loadedAnimation.frameCount);
        assertEquals(10f, loadedAnimation.fps, 0.001f);
        assertEquals("run", loadedAnimation.currentClip);
        assertEquals(2, loadedAnimation.clips.size);
        assertEquals(0, loadedAnimation.clips.get("idle").start);
        assertEquals(1, loadedAnimation.clips.get("idle").end);
        assertEquals(2, loadedAnimation.clips.get("run").start);
        assertEquals(7, loadedAnimation.clips.get("run").end);
        assertTrue(loadedAnimation.clips.get("run").flipX);
    }

    @Test
    public void loadAnimationAssetWithoutClips_initializesEmptyClipsMap() throws Exception {
        String json = """
                {
                version: 2,
                nextId: 2,
                assets: [
                { id: 1, type: "animation", logicalPath: "animations/hero", sourceRelPath: "orig/animations/hero__a1", scope: "USER", frameCount: 3, fps: 12 }
                ]
                }
                """;

        Path tmp = Files.createTempFile("animation-asset-no-clips", ".json");
        Files.writeString(tmp, json);

        AssetMetaDatabase loaded = AssetMetaDatabase.load(new FileHandle(tmp.toFile()));
        AnimationAssetMeta animation =
                (AnimationAssetMeta) loaded.findByLogicalPath("animations/hero");

        assertTrue(animation.clips != null);
        assertFalse(animation.clips.containsKey("default"));
    }
}
