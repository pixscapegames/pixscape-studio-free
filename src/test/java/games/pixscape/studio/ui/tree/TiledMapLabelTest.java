package games.pixscape.studio.ui.tree;

import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledMapComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TiledMapLabelTest {
    @Test
    public void labelAlwaysIncludesMapPrefixAndCellDimensionsWithOptionalName() {
        TiledMapComponent tiled = new TiledMapComponent();
        tiled.width = 100;
        tiled.height = 75;
        tiled.tileWidth = 32;
        tiled.tileHeight = 16;
        PixscapeIdentityComponent identity = new PixscapeIdentityComponent();
        identity.name = "MonSuperNom";
        assertEquals("Tiled Map MonSuperNom (100x75)", ItemTreePanel.tiledMapLabel(tiled, identity));
        assertEquals("Tile layer MonSuperNom", ItemTreePanel.tileLayerLabel(identity));
        for (String absent : new String[]{null, "", "  "}) {
            identity.name = absent;
            assertEquals("Tiled Map (100x75)", ItemTreePanel.tiledMapLabel(tiled, identity));
            assertEquals("Tile layer", ItemTreePanel.tileLayerLabel(identity));
        }
        assertEquals("Tiled Map (100x75)", ItemTreePanel.tiledMapLabel(tiled, null));
        assertEquals("Tile layer", ItemTreePanel.tileLayerLabel(null));
    }
}
