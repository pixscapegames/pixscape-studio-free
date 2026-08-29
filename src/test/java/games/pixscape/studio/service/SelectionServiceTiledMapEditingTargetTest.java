package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectionServiceTiledMapEditingTargetTest {
    private World world;
    private SceneMeta sceneMeta;
    private StudioEditingModeService editingModes;
    private SelectionService selection;
    private int tiledLayer;
    private int tiledMap;

    @Before
    public void setUp() {
        world = new World(new WorldConfiguration());
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
        sceneMeta = config.getCurrentSceneMeta();

        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, sceneMeta);
        LayerService layers = new LayerService(
                world, null, new HistoryIdRegistry(), identities);
        editingModes = new StudioEditingModeService();
        selection = new SelectionService(world, layers, editingModes);

        tiledLayer = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(tiledLayer);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 0;
        world.getMapper(LayerMetaComponent.class).create(tiledLayer).name = "Tiled";
        tiledMap = world.create();
        world.getMapper(EntityIndexComponent.class).create(tiledMap).layerIndex = 0;
        world.getMapper(TiledLayerComponent.class).create(tiledMap);
        world.process();
    }

    @After
    public void tearDown() {
        ProjectConfig.setInstance(null);
        world.dispose();
    }

    @Test
    public void tiledTargetEntitySelectionAndReselectionPublishAuthoritativeToolbarContext() {
        selection.setActivelayerIdForTiledMapContext(
                tiledLayer, SelectionService.SelectionSource.TREE);
        assertTiledTargetActive();

        int light = ordinaryEntity(true);
        selection.selectOnly(light, SelectionService.SelectionSource.TREE);
        assertEntityTargetActive(light);

        int ordinary = ordinaryEntity(false);
        selection.setActivelayerId(tiledLayer, SelectionService.SelectionSource.TREE);
        selection.selectOnly(ordinary, SelectionService.SelectionSource.TREE);
        assertEntityTargetActive(ordinary);

        selection.clearSelection(SelectionService.SelectionSource.TREE);
        selection.setActivelayerIdForTiledMapContext(
                tiledLayer, SelectionService.SelectionSource.TREE);
        assertTiledTargetActive();
    }

    @Test
    public void deletedTiledMapCannotLeaveStaleEditingTargetActive() {
        selection.setActivelayerIdForTiledMapContext(
                tiledLayer, SelectionService.SelectionSource.TREE);
        assertTrue(selection.isTiledMapEditingTargetActive());

        world.delete(tiledMap);
        world.process();

        assertFalse(selection.isTiledMapEditingTargetActive());
    }

    private int ordinaryEntity(boolean light) {
        int entity = world.create();
        world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 0;
        if (light) world.getMapper(PointLightComponent.class).create(entity);
        world.process();
        return entity;
    }

    private void assertTiledTargetActive() {
        assertEquals(SceneMeta.EditorMode.TILE, sceneMeta.editorMode);
        assertEquals(StudioEditingMode.TILED, editingModes.getCurrentMode());
        assertTrue(selection.isTiledMapEditingTargetActive());
    }

    private void assertEntityTargetActive(int entity) {
        assertEquals(entity, selection.getFirstSelectedEntityId());
        assertEquals(SceneMeta.EditorMode.ENTITY, sceneMeta.editorMode);
        assertEquals(StudioEditingMode.NORMAL, editingModes.getCurrentMode());
        assertFalse(selection.isTiledMapEditingTargetActive());
    }
}
