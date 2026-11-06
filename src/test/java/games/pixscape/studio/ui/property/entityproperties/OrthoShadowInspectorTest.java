package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.studio.service.SceneService;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.asset.AnimationAssetAuthoringService;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.SimpleSelectBox;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class OrthoShadowInspectorTest {
    @org.junit.Rule public org.junit.rules.TemporaryFolder files = new org.junit.rules.TemporaryFolder();

    @Test public void selectingHardPcfAndNoneCanBeUndoneAndRedone() throws Exception {
        VisUiTestBootstrap.loadSkin();
        SceneMeta scene = new SceneMeta("Ortho", "ortho.json");
        scene.lightingEnabled = true;
        World world = world(scene);
        try {
            HistoryManager history = new HistoryManager(8);
            LightingPanel panel = panel(world, history, scene);
            int mapEntity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(mapEntity).stableId = 1;
            var map = world.getMapper(TiledLayerComponent.class).create(mapEntity);
            map.projection = TiledProjection.ORTHO;
            map.mapWidthCells = map.mapHeightCells = map.chunkSize = 4;
            map.tileWidth = map.tileHeight = 16;
            int entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 2;
            scene.nextEntityStableId = 3;
            history.historyIds().ensureForEntity(entity);
            var light = world.getMapper(PointLightComponent.class).create(entity);
            light.shadowQuality = 0;
            world.process();
            panel.setEntityId(entity);
            SimpleSelectBox<String> quality = panel.findActor("light-shadow-quality");
            quality.setSelected("Hard"); assertEquals(1, light.shadowQuality);
            quality.setSelected("PCF"); assertEquals(2, light.shadowQuality);
            quality.setSelected("None"); assertEquals(0, light.shadowQuality);
            history.undo(); assertEquals(2, light.shadowQuality);
            panel.setEntityId(entity); assertEquals("PCF", quality.getSelected());
            history.undo(); assertEquals(1, light.shadowQuality);
            panel.setEntityId(entity); assertEquals("Hard", quality.getSelected());
            history.undo(); assertEquals(0, light.shadowQuality);
            history.redo(); assertEquals(1, light.shadowQuality);
            history.redo(); assertEquals(2, light.shadowQuality);
            FileHandle file = new FileHandle(files.newFile("ortho.json"));
            SceneService.saveScene(world, file, false);
            World reopened = world(scene);
            try {
                SceneLoader.loadScene(reopened, file, false, scene);
                reopened.process();
                int restored = reopened.getAspectSubscriptionManager().get(Aspect.all(PointLightComponent.class))
                        .getEntities().get(0);
                LightingPanel reopenedPanel = panel(reopened, new HistoryManager(8), scene);
                reopenedPanel.setEntityId(restored);
                SimpleSelectBox<String> reopenedQuality = reopenedPanel.findActor("light-shadow-quality");
                assertEquals("PCF", reopenedQuality.getSelected());
            } finally {
                reopened.dispose();
            }
            history.redo(); assertEquals(0, light.shadowQuality);
            panel.setEntityId(entity); assertEquals("None", quality.getSelected());
        } finally {
            world.dispose();
            VisUiTestBootstrap.unloadSkin();
        }
    }

    private static World world(SceneMeta scene) {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager())
                .register(SceneMetaRuntime.class.getName(), scene));
    }

    private static LightingPanel panel(World world, HistoryManager history, SceneMeta scene) {
        IdentityRegistry identities = new IdentityRegistry();
        LayerService layers = new LayerService(world, null, history.historyIds(), identities);
        AssetMetaDatabase assets = new AssetMetaDatabase();
        return new LightingPanel(new EntityPropertiesContext(world, history,
                new PhysicsSelectionService(), new PhysicsService(world, null, scene), layers,
                new AtlasStudioService(null), new SelectionService(world, layers), identities,
                new IconResolver(world), () -> {}, assets::findById, ignored -> {}, Array::new,
                new AnimationAssetAuthoringService(() -> assets, () -> null, ignored -> {}), 0, scene), true);
    }
}
