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
    private LayerService layers;
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
        layers = new LayerService(
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
        selection.setTiledMapEditingTarget(
                tiledMap, SelectionService.SelectionSource.TREE);
        assertTiledTargetActive();
        assertEquals(tiledLayer, selection.getActivelayerId());
        assertEquals(tiledMap, selection.getTiledMapEditingTargetEntityId());

        int light = ordinaryEntity(true);
        selection.selectOnly(light, SelectionService.SelectionSource.TREE);
        assertEntityTargetActive(light);

        int ordinary = ordinaryEntity(false);
        selection.setActivelayerId(tiledLayer, SelectionService.SelectionSource.TREE);
        selection.selectOnly(ordinary, SelectionService.SelectionSource.TREE);
        assertEntityTargetActive(ordinary);

        selection.clearSelection(SelectionService.SelectionSource.TREE);
        selection.setTiledMapEditingTarget(
                tiledMap, SelectionService.SelectionSource.TREE);
        assertTiledTargetActive();
    }

    @Test
    public void deletedTiledMapCannotLeaveStaleEditingTargetActive() {
        selection.setTiledMapEditingTarget(
                tiledMap, SelectionService.SelectionSource.TREE);
        assertTrue(selection.isTiledMapEditingTargetActive());

        world.delete(tiledMap);
        world.process();

        assertFalse(selection.isTiledMapEditingTargetActive());
        assertEquals(-1, selection.getTiledMapEditingTargetEntityId());
        assertEquals(StudioEditingMode.NORMAL, editingModes.getCurrentMode());
    }

    @Test
    public void invalidMapTargetIsRejectedWithoutRequiringTiledComponentOnHost() {
        selection.setTiledMapEditingTarget(
                tiledLayer, SelectionService.SelectionSource.TREE);

        assertEquals(-1, selection.getTiledMapEditingTargetEntityId());
        assertFalse(selection.isTiledMapEditingTargetActive());
        assertFalse(world.getMapper(TiledLayerComponent.class).has(tiledLayer));
    }

    @Test
    public void selectingAnotherLayerClearsMapTargetButKeepsLayerIdentityIndependent() {
        selection.setTiledMapEditingTarget(tiledMap, SelectionService.SelectionSource.TREE);
        int classicLayer = layer(1, LayerComponent.TYPE_CLASSIC);

        selection.setActivelayerId(classicLayer, SelectionService.SelectionSource.TREE);

        assertEquals(classicLayer, selection.getActivelayerId());
        assertEquals(-1, selection.getTiledMapEditingTargetEntityId());
        assertEquals(StudioEditingMode.NORMAL, editingModes.getCurrentMode());
    }

    @Test
    public void selectingAnotherMapReplacesTargetAndActivatesItsOwningHost() {
        int otherHost = layer(1, LayerComponent.TYPE_TILED);
        int otherMap = world.create();
        world.getMapper(EntityIndexComponent.class).create(otherMap).layerIndex = 1;
        world.getMapper(TiledLayerComponent.class).create(otherMap);
        world.process();
        selection.setTiledMapEditingTarget(tiledMap, SelectionService.SelectionSource.TREE);

        selection.setTiledMapEditingTarget(otherMap, SelectionService.SelectionSource.TREE);

        assertEquals(otherHost, selection.getActivelayerId());
        assertEquals(otherMap, selection.getTiledMapEditingTargetEntityId());
        assertTrue(selection.isTiledMapEditingTargetActive());
    }

    private int layer(int layerIndex, int type) {
        int entity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
        layer.layerIndex = layerIndex;
        layer.type = type;
        world.getMapper(LayerMetaComponent.class).create(entity).name = "Layer " + layerIndex;
        world.process();
        layers.rebuildFromWorld();
        return entity;
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
        assertEquals(tiledMap, selection.getTiledMapEditingTargetEntityId());
        assertTrue(selection.isTiledMapEditingTargetActive());
    }

    private void assertEntityTargetActive(int entity) {
        assertEquals(entity, selection.getFirstSelectedEntityId());
        assertEquals(SceneMeta.EditorMode.ENTITY, sceneMeta.editorMode);
        assertEquals(StudioEditingMode.NORMAL, editingModes.getCurrentMode());
        assertEquals(-1, selection.getTiledMapEditingTargetEntityId());
        assertFalse(selection.isTiledMapEditingTargetActive());
    }
}
