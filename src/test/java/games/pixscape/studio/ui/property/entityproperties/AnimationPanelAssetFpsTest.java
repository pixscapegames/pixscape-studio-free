package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.asset.AnimationClipMeta;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.FloatField;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

public class AnimationPanelAssetFpsTest {

    @BeforeClass
    public static void loadStudioSkin() {
        VisUiTestBootstrap.loadSkin();
        VisUI.dispose();
        VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
    }

    @AfterClass
    public static void unloadStudioSkin() {
        VisUI.dispose();
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void authoredFpsSurvivesSwitchingAwayAndBack() throws Exception {
        try (Harness harness = new Harness()) {
            FloatField fpsField = field(harness.panel, "fpsField", FloatField.class);
            fpsField.setText("8.00");
            fpsField.commit();

            assertEquals(8f, harness.idle.fps, 0f);
            assertEquals(8f, harness.animation.fps, 0f);

            switchAnimation(harness.panel, harness.walk.id());
            assertEquals(harness.walk.id(), harness.assetRef.assetId);
            assertEquals(18f, harness.animation.fps, 0f);

            switchAnimation(harness.panel, harness.idle.id());
            assertEquals(harness.idle.id(), harness.assetRef.assetId);
            assertEquals(8f, harness.animation.fps, 0f);

            AnimationAssetMeta reloaded = (AnimationAssetMeta) AssetMetaDatabase
                    .load(harness.assetsFile)
                    .findById(harness.idle.id());
            assertEquals(8f, reloaded.fps, 0f);
        }
    }

    private static void switchAnimation(AnimationPanel panel, int assetId) throws Exception {
        Method method = AnimationPanel.class.getDeclaredMethod("switchAnimation", int.class);
        method.setAccessible(true);
        method.invoke(panel, assetId);
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }

    private static final class Harness implements AutoCloseable {
        private final World world = new World(new WorldConfiguration());
        private final HistoryManager history = new HistoryManager(8);
        private final IdentityRegistry identities = new IdentityRegistry();
        private final AssetMetaDatabase assets = new AssetMetaDatabase();
        private final FileHandle assetsFile;
        private final AnimationAssetMeta idle;
        private final AnimationAssetMeta walk;
        private final AnimationComponent animation;
        private final AssetRefComponent assetRef;
        private final AnimationPanel panel;

        private Harness() throws Exception {
            assetsFile = new FileHandle(Files.createTempDirectory("animation-panel-fps")
                    .resolve("assets.json").toFile());
            idle = animation(assets, "idle", 12f);
            walk = animation(assets, "walk", 18f);
            Array<AnimationAssetMeta> available = new Array<>();
            available.add(idle);
            available.add(walk);

            SceneMeta sceneMeta = new SceneMeta();
            identities.bind(world, sceneMeta);
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            EntityPropertiesContext context = new EntityPropertiesContext(
                    world,
                    history,
                    new PhysicsSelectionService(),
                    new PhysicsService(world, null, sceneMeta),
                    layers,
                    new AtlasStudioService(null),
                    new SelectionService(world, layers),
                    identities,
                    new IconResolver(world),
                    () -> {
                    },
                    assets::findById,
                    ignored -> {
                    },
                    () -> available,
                    new AnimationAssetAuthoringService(
                            () -> assets, () -> assetsFile, ignored -> {
                            }),
                    0);

            int entityId = world.create();
            animation = world.getMapper(AnimationComponent.class).create(entityId);
            animation.animationAssetIds.add(idle.id());
            animation.animationAssetIds.add(walk.id());
            animation.currentClip = "idle";
            animation.fps = 12f;
            assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
            assetRef.assetId = idle.id();
            panel = new AnimationPanel(context);
            panel.setEntityId(entityId);
        }

        @Override
        public void close() {
            identities.bind(null, null);
            world.dispose();
        }

        private static AnimationAssetMeta animation(
                AssetMetaDatabase database, String clip, float fps) {
            AnimationAssetMeta animation = (AnimationAssetMeta) database.registerIfAbsent(
                    AssetType.ANIMATION,
                    "animations/" + clip,
                    "orig/animations/" + clip,
                    AssetMeta.AssetScope.USER);
            animation.frameCount = 2;
            animation.fps = fps;
            animation.currentClip = clip;
            animation.clips.put(clip, new AnimationClipMeta(0, 1));
            return animation;
        }
    }
}
