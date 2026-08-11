package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AnimationAssetAuthoringServiceTest {

    @Test
    public void updatePersistsAuthoredFpsAndPublishesAuthoritativeDatabase() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta animation = animation(database, 12f);
        FileHandle assetsFile = tempAssetsFile();
        AtomicInteger publications = new AtomicInteger();
        AnimationAssetAuthoringService service = new AnimationAssetAuthoringService(
                () -> database,
                () -> assetsFile,
                published -> {
                    if (published != database) throw new AssertionError("Database instance changed");
                    publications.incrementAndGet();
                });

        service.updateFps(animation.id(), 8f);

        AnimationAssetMeta persisted = (AnimationAssetMeta) AssetMetaDatabase.load(assetsFile)
                .findById(animation.id());
        assertEquals(8f, animation.fps, 0f);
        assertEquals(8f, persisted.fps, 0f);
        assertEquals(1, publications.get());
    }

    @Test(expected = IllegalStateException.class)
    public void nonAnimationAssetIdIsRejected() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta image = database.registerIfAbsent(
                AssetType.IMAGE, "images/player", "orig/images/player.png", AssetMeta.AssetScope.USER);
        AnimationAssetAuthoringService service = service(database, tempAssetsFile());

        service.updateFps(image.id(), 8f);
    }

    @Test(expected = IllegalStateException.class)
    public void missingAssetIdIsRejected() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();

        service(database, tempAssetsFile()).updateFps(999, 8f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidFpsIsRejected() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta animation = animation(database, 12f);

        service(database, tempAssetsFile()).updateFps(animation.id(), Float.NaN);
    }

    @Test
    public void persistenceFailureRollsBackInMemoryFpsAndSkipsPublication() throws Exception {
        AssetMetaDatabase database = new AssetMetaDatabase();
        AnimationAssetMeta animation = animation(database, 12f);
        AtomicInteger publications = new AtomicInteger();
        AnimationAssetAuthoringService service = new AnimationAssetAuthoringService(
                () -> database,
                AnimationAssetAuthoringServiceTest::unusedFile,
                ignored -> publications.incrementAndGet(),
                (ignoredDatabase, ignoredFile) -> {
                    throw new RuntimeException("simulated persistence failure");
                });

        try {
            service.updateFps(animation.id(), 8f);
            fail("Expected persistence failure");
        } catch (RuntimeException expected) {
            assertEquals("simulated persistence failure", expected.getMessage());
        }

        assertEquals(12f, animation.fps, 0f);
        assertEquals(0, publications.get());
    }

    private static AnimationAssetAuthoringService service(
            AssetMetaDatabase database, FileHandle assetsFile) {
        return new AnimationAssetAuthoringService(
                () -> database, () -> assetsFile, ignored -> {
                });
    }

    private static AnimationAssetMeta animation(AssetMetaDatabase database, float fps) {
        AnimationAssetMeta animation = (AnimationAssetMeta) database.registerIfAbsent(
                AssetType.ANIMATION,
                "animations/walk",
                "orig/animations/walk",
                AssetMeta.AssetScope.USER);
        animation.frameCount = 2;
        animation.fps = fps;
        animation.currentClip = "default";
        animation.clips.put("default", new AnimationClipMeta(0, 1));
        return animation;
    }

    private static FileHandle tempAssetsFile() throws Exception {
        Path directory = Files.createTempDirectory("animation-fps-authoring");
        return new FileHandle(directory.resolve("assets.json").toFile());
    }

    private static FileHandle unusedFile() {
        return new FileHandle("unused-assets.json");
    }
}
