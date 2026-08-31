package games.pixscape.studio.service.atlas;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;
import org.junit.BeforeClass;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class SceneAtlasInputAnimationDependenciesTest {
    @BeforeClass
    public static void ensureHeadlessFiles() {
        if (Gdx.files == null) {
            new HeadlessApplication(new ApplicationAdapter() {},
                    new HeadlessApplicationConfiguration());
        }
    }

    @Test
    public void currentSceneIncludesEveryAttachedAnimationAsset() throws Exception {
        Harness harness = new Harness();
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(harness.active.id());
        animation.animationAssetIds.add(harness.nonActive.id());
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = harness.active.id();

        Set<String> required = collect(harness.cfg, world, harness.database);

        assertTrue(required.contains(harness.active.sourceRelPath() + "/01.png"));
        assertTrue(required.contains(harness.nonActive.sourceRelPath() + "/01.png"));
    }

    @Test
    public void runtimeAvailableGameObjectIncludesEveryAttachedAnimationAsset() throws Exception {
        Harness harness = new Harness();
        GameObjectAsset gameObject = new GameObjectAsset();
        gameObject.rootSourceEntityId = 1;
        GameObjectAsset.GameObjectEntityData entity = new GameObjectAsset.GameObjectEntityData();
        entity.sourceEntityId = 1;
        entity.transform = new GameObjectAsset.TransformData();
        entity.transform.scaleX = 1f;
        entity.transform.scaleY = 1f;
        entity.gameObject = new GameObjectAsset.GameObjectData();
        gameObject.entities.add(entity);
        GameObjectAsset.GameObjectEntityData visual = new GameObjectAsset.GameObjectEntityData();
        visual.sourceEntityId = 2;
        visual.parentSourceEntityId = 1;
        visual.transform = new GameObjectAsset.TransformData();
        visual.transform.scaleX = 1f;
        visual.transform.scaleY = 1f;
        visual.assetRef = new GameObjectAsset.AssetRefData();
        visual.assetRef.assetId = harness.active.id();
        visual.animation = new GameObjectAsset.AnimationData();
        visual.animation.animationAssetIds.add(harness.active.id());
        visual.animation.animationAssetIds.add(harness.nonActive.id());
        visual.animation.currentClip = "default";
        visual.animation.fps = 12f;
        visual.animation.playing = true;
        visual.animation.loop = true;
        visual.animation.frame = -1;
        gameObject.entities.add(visual);
        new GameObjectAssetLoader().save(
                StudioFs.requireGameObjectFile(harness.cfg, "multi-animation"), gameObject);
        harness.cfg.getCurrentSceneMeta().runtimeAvailability.gameObjectIds
                .add("gameobjects/multi-animation.gameobject");

        Set<String> required = collect(
                harness.cfg, new World(new WorldConfiguration()), harness.database);

        assertTrue(required.contains(harness.active.sourceRelPath() + "/01.png"));
        assertTrue(required.contains(harness.nonActive.sourceRelPath() + "/01.png"));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> collect(ProjectConfig cfg,
                                       World world,
                                       AssetMetaDatabase database) throws Exception {
        Method method = SceneAtlasInputService.class.getDeclaredMethod(
                "collectRequiredAtlasInputPathsForCurrentScene",
                ProjectConfig.class,
                World.class,
                AssetMetaDatabase.class,
                games.pixscape.studio.asset.TileAnimationsMetaDatabase.class
        );
        method.setAccessible(true);
        return (Set<String>) method.invoke(
                new SceneAtlasInputService(), cfg, world, database, null);
    }

    private static final class Harness {
        final ProjectConfig cfg;
        final AssetMetaDatabase database = new AssetMetaDatabase();
        final AssetMeta active;
        final AssetMeta nonActive;

        Harness() throws Exception {
            Path root = Files.createTempDirectory("pixscape-animation-atlas-dependencies");
            cfg = new ProjectConfig();
            cfg.projectTitle = "Animation dependencies";
            cfg.projectFileName = "animation-dependencies";
            cfg.projectDirectoryPath = root.toString();
            cfg.createSceneMeta("Main");
            cfg.setCurrentSceneByName("Main");
            ProjectConfig.setInstance(cfg);

            active = animation("idle");
            nonActive = animation("run");
        }

        private AssetMeta animation(String name) throws Exception {
            String source = StudioFs.DIR_ORIG_ANIMATIONS + "/" + name;
            AssetMeta meta = database.registerIfAbsent(
                    AssetType.ANIMATION,
                    StudioFs.PREFIX_ANIMATIONS + name,
                    source,
                    AssetMeta.AssetScope.USER
            );
            Path directory = Path.of(cfg.projectDirectoryPath).resolve(source);
            Files.createDirectories(directory);
            Files.write(directory.resolve("01.png"), new byte[]{1});
            return meta;
        }
    }
}
