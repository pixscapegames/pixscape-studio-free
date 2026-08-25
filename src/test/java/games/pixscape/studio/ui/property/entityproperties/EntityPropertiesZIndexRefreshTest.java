package games.pixscape.studio.ui.property.entityproperties;

import com.artemis.World;
import com.kotcrab.vis.ui.widget.VisLabel;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EntityPropertiesZIndexRefreshTest {
    @BeforeClass
    public static void loadVisUi() { VisUiTestBootstrap.loadSkin(); }

    @AfterClass
    public static void unloadVisUi() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void refreshZIndexReadsCurrentEcsValueWithoutRebinding() {
        World world = new World();
        try {
            int entity = world.create();
            EntityIndexComponent index =
                    world.getMapper(EntityIndexComponent.class).create(entity);
            index.zIndex = 5;
            world.process();
            VisLabel label = new VisLabel();

            EntityProperties.refreshZIndexLabel(
                    world, world.getMapper(EntityIndexComponent.class), entity, label);
            assertEquals("5", label.getText().toString());

            index.zIndex = 2;
            EntityProperties.refreshZIndexLabel(
                    world, world.getMapper(EntityIndexComponent.class), entity, label);
            assertEquals("2", label.getText().toString());
        } finally {
            world.dispose();
        }
    }
}
