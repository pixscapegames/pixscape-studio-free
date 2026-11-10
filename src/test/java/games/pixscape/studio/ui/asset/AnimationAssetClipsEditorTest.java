package games.pixscape.studio.ui.asset;

import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.ui.property.entityproperties.AnimationClipsDialog;
import games.pixscape.studio.ui.widget.SimpleFloatField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.*;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AnimationAssetClipsEditorTest {
    @BeforeClass public static void load() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unload() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void cancelDoesNotSaveOrMutateTheSource() throws Exception {
        AnimationAssetMeta source = animation();
        AtomicReference<AnimationAssetMeta> saved = new AtomicReference<>();
        AnimationClipsDialog dialog = AnimationAssetClipsEditor.create(source, 4, saved::set);
        editFps(dialog, "24");
        result(dialog, false);
        assertNull(saved.get());
        assertEquals(12, source.fps, 0);
        assertEquals("walk", source.currentClip);
        assertEquals(3, source.clips.get("walk").end);
    }

    @Test public void validationPublishesOnlyTheEditedCopy() throws Exception {
        AnimationAssetMeta source = animation();
        AtomicReference<AnimationAssetMeta> saved = new AtomicReference<>();
        AnimationClipsDialog dialog = AnimationAssetClipsEditor.create(source, 4, saved::set);
        editFps(dialog, "24");
        result(dialog, true);
        assertNotSame(source, saved.get());
        assertNotSame(source.clips.get("walk"), saved.get().clips.get("walk"));
        assertEquals(24, saved.get().fps, 0);
        assertEquals(12, source.fps, 0);
    }

    private static AnimationAssetMeta animation() {
        var source = new AnimationAssetMeta(1, "animations/Hero", "orig/animations/Hero", AssetMeta.AssetScope.USER);
        source.frameCount = 4;
        source.fps = 12;
        source.currentClip = "walk";
        source.clips.put("walk", new AnimationClipMeta(0, 3));
        return source;
    }

    private static void editFps(AnimationClipsDialog dialog, String value) throws Exception {
        var field = AnimationClipsDialog.class.getDeclaredField("fpsField");
        field.setAccessible(true);
        ((SimpleFloatField) field.get(dialog)).setText(value);
    }

    private static void result(AnimationClipsDialog dialog, boolean ok) throws Exception {
        var method = AnimationClipsDialog.class.getDeclaredMethod("result", Object.class);
        method.setAccessible(true);
        method.invoke(dialog, ok);
    }
}
