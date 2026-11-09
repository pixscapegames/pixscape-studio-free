package games.pixscape.studio.ui.tree;

import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TiledMapLabelTest {
    @Test
    public void labelAlwaysIncludesMapPrefixAndCellDimensionsWithOptionalName() {
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.mapWidthCells = 100;
        tiled.mapHeightCells = 75;
        tiled.tileWidth = 32;
        tiled.tileHeight = 16;
        PixscapeIdentityComponent identity = new PixscapeIdentityComponent();
        identity.name = "MonSuperNom";
        assertEquals("Map MonSuperNom (100x75)", ItemTreePanel.tiledMapLabel(tiled, identity));
        for (String absent : new String[]{null, "", "  "}) {
            identity.name = absent;
            assertEquals("Map (100x75)", ItemTreePanel.tiledMapLabel(tiled, identity));
        }
        assertEquals("Map (100x75)", ItemTreePanel.tiledMapLabel(tiled, null));
    }
}
