package games.pixscape.studio.ui.property;

import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.ui.widget.CollapsibleVisTable;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.Test;

import static org.junit.Assert.*;

public class LayerPropertiesSpatialActivationTest {
    @Test
    public void physicsFreeLayerCanBeActivatedReactivatedAndRemainsUnique() throws Exception {
        ProjectConfig previous = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Spatial without Physics");
        ProjectConfig.setInstance(config);
        World world = new World();
        VisUiTestBootstrap.loadSkin();
        try {
            assertFalse(config.getCurrentSceneMeta().physicsEnabled);
            HistoryManager history = new HistoryManager(16);
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, config.getCurrentSceneMeta());
            LayerService layers = new LayerService(world, null, history.historyIds(), identities);
            int first = layers.addLayerTop("First");
            int second = layers.addLayerTop("Second");
            world.process();
            LayerProperties properties = new LayerProperties(world, history, layers, () -> { });
            VisCheckBox checkbox = (VisCheckBox) field(properties, "spatialCheckBox");
            CollapsibleVisTable section = (CollapsibleVisTable) field(properties, "spatialSection");
            properties.setLayerEntityId(first);
            assertFalse(section.isCollapsed());
            for (int cycle = 0; cycle < 2; cycle++) {
                checkbox.setChecked(true);
                assertTrue(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
                properties.setLayerEntityId(second);
                assertTrue(section.isCollapsed());
                int cursor = history.getCursor();
                // A stale or programmatic request must still preserve the single-layer rule.
                checkbox.setChecked(true);
                assertFalse(world.getMapper(LayerComponent.class).get(second).spatialEnabled);
                assertEquals(cursor, history.getCursor());
                properties.setLayerEntityId(first);
                checkbox.setChecked(false);
                assertFalse(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
                assertFalse(section.isCollapsed());
            }
            history.undo();
            assertTrue(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
            history.redo();
            assertFalse(world.getMapper(LayerComponent.class).get(first).spatialEnabled);
            assertFalse(config.getCurrentSceneMeta().physicsEnabled);
        } finally {
            world.dispose();
            VisUiTestBootstrap.unloadSkin();
            ProjectConfig.setInstance(previous);
            games.pixscape.studio.event.EventFlow.i().discardPending();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
