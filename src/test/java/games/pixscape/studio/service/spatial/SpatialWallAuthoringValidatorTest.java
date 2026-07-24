package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialWallAuthoringValidatorTest {
    @Test
    public void rejectsMissingStructureIdentityWithoutRepair() {
        TiledMapLayerData map = map(4, 4);
        SpatialBlocksComponent walls = component(wall(1, 0, 0, 0, 2, 1, 0f, 10f));
        Assert.assertEquals(SpatialWallAuthoringValidator.Status.INVALID_TOPOLOGY,
                SpatialWallAuthoringValidator.validateLayer(walls, map).status);
        Assert.assertEquals(0, walls.blocks.get(0).structureId);
    }

    @Test
    public void acceptsChangedDiagnosticAssetId() {
        TiledMapLayerData map = map(2, 2);
        SpatialBlockData wall = wall(1, 1, 0, 0, 1, 1, 0f, 10f);
        wall.linkedTileRefs.get(0).tileAssetId = 99;
        Assert.assertTrue(SpatialWallAuthoringValidator.validateWall(wall, map).isValid());
        Assert.assertEquals(SpatialWallAuthoringValidator.Status.LINKED_ASSET_ID_MISMATCH,
                SpatialWallAuthoringValidator.diagnoseAssetIdMismatch(wall, map).status);
    }

    @Test
    public void rejectsDuplicateContainmentDisconnectedIdentityAndMixedProperties() {
        TiledMapLayerData map = map(8, 8);
        Assert.assertFalse(SpatialWallAuthoringValidator.validateLayer(component(
                wall(1, 1, 0, 0, 2, 2, 0f, 10f), wall(2, 1, 0, 0, 2, 2, 0f, 10f)), map).isValid());
        Assert.assertFalse(SpatialWallAuthoringValidator.validateLayer(component(
                wall(1, 1, 0, 0, 4, 4, 0f, 10f), wall(2, 1, 1, 1, 2, 2, 0f, 10f)), map).isValid());
        Assert.assertFalse(SpatialWallAuthoringValidator.validateLayer(component(
                wall(1, 1, 0, 0, 1, 1, 0f, 10f), wall(2, 1, 6, 6, 1, 1, 0f, 10f)), map).isValid());
        Assert.assertFalse(SpatialWallAuthoringValidator.validateLayer(component(
                wall(1, 1, 0, 0, 3, 1, 0f, 10f), wall(2, 1, 1, 0, 1, 3, 1f, 10f)), map).isValid());
        Assert.assertFalse(SpatialWallAuthoringValidator.validateLayer(component(
                wall(1, 1, 0, 0, 3, 1, 0f, 10f), wall(2, 1, 1, 0, 1, 3, 0f, 11f)), map).isValid());
    }

    static SpatialBlocksComponent component(SpatialBlockData... walls) {
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        for (SpatialBlockData wall : walls) {
            component.blocks.add(wall);
            component.nextSpatialBlockId =
                    Math.max(component.nextSpatialBlockId, wall.id + 1);
        }
        return component;
    }

    static SpatialBlockData wall(int id, int structureId, int x, int y, int width, int depth,
                                 float altitude, float height) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structureId;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.altitude = altitude;
        wall.height = height;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = y; gy < y + depth; gy++) {
            for (int gx = x; gx < x + width; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }

    static TiledMapLayerData map(int width, int height) {
        TiledMapLayerData map = new TiledMapLayerData(width, height, 16, 16, 4);
        for (int gy = 0; gy < height; gy++) for (int gx = 0; gx < width; gx++) map.setTile(gx, gy, 1);
        return map;
    }
}
