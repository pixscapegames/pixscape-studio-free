package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class EntityNodeLayoutTest {

    @BeforeClass
    public static void loadVisUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void reliesOnTreeIconSpacingWithoutAdditionalLabelPadding() {
        EntityNode node = new EntityNode("Entity", null, 1, true);
        Table row = (Table) node.getActor();

        Assert.assertEquals(0f, row.getCell(node.getLabel()).getPadLeft(), 0.01f);
    }
}
