package games.pixscape.studio.ui.property;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.studio.ui.widget.SimpleSelectBox;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class AnimationAssetPropertiesTest {
    @BeforeClass public static void load() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unload() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void selectingPreviewClipDoesNotMutateAssetOrDefault() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AnimationAssetMeta animation = animation(db);
        AtomicInteger configured = new AtomicInteger(-1);
        AnimationAssetProperties inspector = new AnimationAssetProperties(() -> db, configured::set, path -> new Array<>());
        String snapshot = db.toSnapshotJson();
        inspector.select(animation.id());
        clipBox(inspector).setSelected("walk");
        assertEquals(snapshot, db.toSnapshotJson());
        assertEquals("idle", animation.currentClip);
        assertEquals("Hero", label(inspector, "animationAssetName"));
        assertEquals(Integer.toString(animation.id()), label(inspector, "animationAssetId"));
        assertEquals(0, inspector.findActor("animationAssetId").getListeners().size);
        assertEquals("orig/animations/Hero", label(inspector, "animationAssetSource"));
        assertEquals("4", label(inspector, "animationAssetFrames"));
        assertEquals("walk", clipBox(inspector).getSelected());
        inspector.findActor("configureAnimationClips").fire(new ChangeListener.ChangeEvent());
        assertEquals(animation.id(), configured.get());
    }

    @Test public void catalogReplacementRefreshesMetadataAndPreservesOnlyAnExistingPreviewClip() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AnimationAssetMeta animation = animation(db);
        AtomicReference<AssetMetaDatabase> catalog = new AtomicReference<>(db);
        AnimationAssetProperties inspector = new AnimationAssetProperties(catalog::get, id -> {}, path -> new Array<>());
        inspector.select(animation.id());
        clipBox(inspector).setSelected("walk");
        AssetMetaDatabase replacement = new AssetMetaDatabase();
        AnimationAssetMeta edited = animation(replacement);
        edited.fps = 24;
        edited.frameCount = 7;
        catalog.set(replacement);
        inspector.refreshIfChanged();
        assertEquals("7", label(inspector, "animationAssetFrames"));
        assertEquals("walk", clipBox(inspector).getSelected());
        edited.clips.remove("walk");
        inspector.select(edited.id());
        assertFalse(clipBox(inspector).getItems().contains("walk", false));
        assertEquals("idle", clipBox(inspector).getSelected());
    }

    @Test public void unnamedSingleFramePreviewStopsOnDetachWithoutDisposingBorrowedTexture() throws Exception {
        AssetMetaDatabase db = new AssetMetaDatabase();
        AnimationAssetMeta animation = animation(db);
        animation.clips.clear();
        animation.currentClip = null;
        animation.frameCount = 1;
        AtomicInteger disposals = new AtomicInteger();
        Texture texture = new Texture(2, 2, Pixmap.Format.RGBA8888) {
            @Override public void dispose() { disposals.incrementAndGet(); super.dispose(); }
        };
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class[]{Batch.class},
                (proxy, method, args) -> null);
        Stage stage = new Stage(new ScreenViewport(), batch);
        try {
            AnimationAssetProperties inspector = new AnimationAssetProperties(() -> db, id -> {}, path -> Array.with(texture));
            inspector.select(animation.id());
            stage.addActor(inspector);
            var preview = inspector.findActor("animationAssetPreview");
            assertNotNull(preview);
            preview.act(10);
            assertEquals(0, field(preview, "currentFrame"));
            inspector.remove();
            assertEquals(false, field(preview, "playing"));
            assertNull(inspector.findActor("animationAssetPreview"));
            assertEquals(0, disposals.get());
            assertTrue(animation.clips.isEmpty());
            stage.addActor(inspector);
            assertNotNull(inspector.findActor("animationAssetPreview"));
        } finally {
            stage.dispose();
            texture.dispose();
        }
    }

    private static AnimationAssetMeta animation(AssetMetaDatabase db) {
        var animation = (AnimationAssetMeta) db.registerIfAbsent(AssetType.ANIMATION,
                "animations/Hero", "orig/animations/Hero", AssetMeta.AssetScope.USER);
        animation.frameCount = 4;
        animation.fps = 12;
        animation.currentClip = "idle";
        animation.clips.put("idle", new AnimationClipMeta(0, 0));
        animation.clips.put("walk", new AnimationClipMeta(1, 3));
        return animation;
    }

    private static SimpleSelectBox<String> clipBox(AnimationAssetProperties inspector) {
        return inspector.findActor("animationPreviewClip");
    }

    private static String label(AnimationAssetProperties inspector, String name) {
        return ((VisLabel) inspector.findActor(name)).getText().toString();
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
