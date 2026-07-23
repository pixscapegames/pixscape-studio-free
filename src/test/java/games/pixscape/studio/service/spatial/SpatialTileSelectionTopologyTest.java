package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialTileSelectionTopologyTest {
    @Test
    public void ownedStartCanTransitionInvalidValidInvalidAndValid() {
        TiledMapLayerData map = new TiledMapLayerData(5, 5, 16, 16, 8, SceneMetaRuntime.TiledProjection.ORTHO);
        for (int gy = 0; gy < 5; gy++) for (int gx = 0; gx < 5; gx++) map.setTile(gx, gy, 1);
        SpatialBlocksComponent walls = new SpatialBlocksComponent();
        SpatialBlockData horizontal = SpatialTileSelectionService.fromOccupiedRect(map, 0, 1, 2, 1, 0f, 10f);
        horizontal.id = 1;
        horizontal.structureId = 1;
        walls.blocks.add(horizontal);
        SpatialTileSelectionService selection = new SpatialTileSelectionService();

        selection.beginDrag(7, 1, 1);
        Assert.assertFalse(selection.canCreateSpatialBlock(map, walls, 0f, 10f));
        selection.updateDrag(1, 3);
        Assert.assertTrue(selection.canCreateSpatialBlock(map, walls, 0f, 10f));
        selection.updateDrag(1, 1);
        Assert.assertFalse(selection.canCreateSpatialBlock(map, walls, 0f, 10f));
        selection.updateDrag(1, 4);
        Assert.assertTrue(selection.canCreateSpatialBlock(map, walls, 0f, 10f));
        selection.updateDragOutsideMap();
        Assert.assertFalse(selection.canCreateSpatialBlock(map, walls, 0f, 10f));
    }
}
