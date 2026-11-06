package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
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
    @Test public void selectingHardAndNoneCanBeUndoneAndRedone() {
        VisUiTestBootstrap.loadSkin();
        SceneMeta scene = new SceneMeta("Ortho", "ortho.json");
        scene.lightingEnabled = true;
        World world = new World(new WorldConfiguration().register(SceneMetaRuntime.class.getName(), scene));
        try {
            HistoryManager history = new HistoryManager(8);
            IdentityRegistry identities = new IdentityRegistry();
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            AssetMetaDatabase assets = new AssetMetaDatabase();
            LightingPanel panel = new LightingPanel(new EntityPropertiesContext(world, history,
                    new PhysicsSelectionService(), new PhysicsService(world, null, scene), layers,
                    new AtlasStudioService(null), new SelectionService(world, layers), identities,
                    new IconResolver(world), () -> {}, assets::findById, ignored -> {}, Array::new,
                    new AnimationAssetAuthoringService(() -> assets, () -> null, ignored -> {}), 0, scene), true);
            world.getMapper(TiledLayerComponent.class).create(world.create()).projection = TiledProjection.ORTHO;
            int entity = world.create();
            history.historyIds().ensureForEntity(entity);
            var light = world.getMapper(PointLightComponent.class).create(entity);
            light.shadowQuality = 0;
            world.process();
            panel.setEntityId(entity);
            SimpleSelectBox<String> quality = panel.findActor("light-shadow-quality");
            quality.setSelected("Hard"); assertEquals(1, light.shadowQuality);
            quality.setSelected("None"); assertEquals(0, light.shadowQuality);
            history.undo(); assertEquals(1, light.shadowQuality);
            panel.setEntityId(entity); assertEquals("Hard", quality.getSelected());
            history.undo(); assertEquals(0, light.shadowQuality);
            history.redo(); assertEquals(1, light.shadowQuality);
            history.redo(); assertEquals(0, light.shadowQuality);
            panel.setEntityId(entity); assertEquals("None", quality.getSelected());
        } finally {
            world.dispose();
            VisUiTestBootstrap.unloadSkin();
        }
    }
}
