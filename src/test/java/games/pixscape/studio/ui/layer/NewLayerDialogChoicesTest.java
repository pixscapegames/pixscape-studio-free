package games.pixscape.studio.ui.layer;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.commands.CreateLayerCommand;
import games.pixscape.studio.service.LayerService;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class NewLayerDialogChoicesTest {
    @After public void tearDown() { ProjectConfig.setInstance(null); }

    @Test
    public void newLayerUiHasNoUserFacingLayerTypeChoice() {
        assertArrayEquals(new String[]{"name"},
                java.util.Arrays.stream(NewLayerRequest.class.getRecordComponents())
                        .map(component -> component.getName()).toArray(String[]::new));
        for (Field field : NewLayerDialog.class.getDeclaredFields()) {
            assertFalse("New Layer must not expose a type selector",
                    VisSelectBox.class.isAssignableFrom(field.getType()));
        }
    }

    @Test
    public void manualNewLayerCommandAlwaysCreatesOrdinaryNonSpatialLayer() {
        World world = new World(new WorldConfiguration());
        try {
            ProjectConfig config = new ProjectConfig();
            config.createSceneMeta("Main");
            ProjectConfig.setInstance(config);
            SceneMeta meta = config.getCurrentSceneMeta();
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, meta);
            LayerService layers = new LayerService(world, null, new HistoryIdRegistry(), identities);
            CreateLayerCommand command = new CreateLayerCommand(layers, 0, "Layer", null);

            command.redo();
            int layerEntity = layers.getLayerEntity(0);
            LayerComponent layer = world.getMapper(LayerComponent.class).get(layerEntity);

            assertFalse(layer.spatialEnabled);
            assertEquals("Layer", world.getMapper(LayerMetaComponent.class).get(layerEntity).name);
        } finally {
            world.dispose();
        }
    }
}
