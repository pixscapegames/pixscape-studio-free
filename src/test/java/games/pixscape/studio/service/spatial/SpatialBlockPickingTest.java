package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockPickingTest {
    private final float[] base = new float[8];
    private final float[] top = new float[8];

    @Test
    public void thinContinuousVolumeCanBePickedAtBaseTopAndSide() {
        TiledMapLayerData map = map();
        SpatialBlockData wall = wall(1);
        SpatialBlocksComponent walls = new SpatialBlocksComponent();
        walls.blocks.add(wall);
        SpatialBlockProjection.projectBaseFootprint(map, wall, base);
        SpatialBlockProjection.projectTopFootprint(map, wall, top);

        Assert.assertEquals(1, pick(walls, map, center(base)));
        Assert.assertEquals(1, pick(walls, map, center(top)));
        float sideX = (base[0] + base[2] + top[0] + top[2]) * 0.25f;
        float sideY = (base[1] + base[3] + top[1] + top[3]) * 0.25f;
        Assert.assertEquals(1, SpatialBlockPicking.find(walls, map, -1, sideX, sideY, base, top));
    }

    @Test
    public void overlappingVolumesPreferSelectedThenDeterministicReverseOrder() {
        TiledMapLayerData map = map();
        SpatialBlocksComponent walls = new SpatialBlocksComponent();
        walls.blocks.add(wall(1));
        walls.blocks.add(wall(2));
        SpatialBlockProjection.projectBaseFootprint(map, walls.blocks.first(), base);
        float[] point = center(base);

        Assert.assertEquals(1, SpatialBlockPicking.find(walls, map, 1, point[0], point[1], base, top));
        Assert.assertEquals(2, SpatialBlockPicking.find(walls, map, -1, point[0], point[1], base, top));
    }

    private int pick(SpatialBlocksComponent walls, TiledMapLayerData map, float[] point) {
        return SpatialBlockPicking.find(walls, map, -1, point[0], point[1], base, top);
    }

    private static float[] center(float[] vertices) {
        return new float[]{
                (vertices[0] + vertices[2] + vertices[4] + vertices[6]) * 0.25f,
                (vertices[1] + vertices[3] + vertices[5] + vertices[7]) * 0.25f
        };
    }

    private static SpatialBlockData wall(int id) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = id;
        wall.x = 2.14f;
        wall.y = 3.38f;
        wall.width = 2.62f;
        wall.depth = 0.19f;
        wall.height = 1f;
        wall.beginAuthoredLinkedTileRefs();
        for (int gx = 2; gx <= 4; gx++) wall.addLinkedTileRef(gx, 3, 1);
        return wall;
    }

    private static TiledMapLayerData map() {
        return new TiledMapLayerData(8, 8, 100, 50, 4, SceneMetaRuntime.TiledProjection.ISO);
    }
}
