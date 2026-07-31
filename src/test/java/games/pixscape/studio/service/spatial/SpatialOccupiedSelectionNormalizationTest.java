package games.pixscape.studio.service.spatial;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class SpatialOccupiedSelectionNormalizationTest {
    @Test
    public void emptyRowAboveIsRemoved() {
        assertPeripheralBorderRemoved(1, 1, 4, 2, 1, 2, 4, 2);
    }

    @Test
    public void emptyRowBelowIsRemoved() {
        assertPeripheralBorderRemoved(1, 2, 4, 3, 1, 2, 4, 2);
    }

    @Test
    public void emptyColumnLeftIsRemoved() {
        assertPeripheralBorderRemoved(0, 2, 4, 3, 1, 2, 4, 3);
    }

    @Test
    public void emptyColumnRightIsRemoved() {
        assertPeripheralBorderRemoved(1, 2, 5, 3, 1, 2, 4, 3);
    }

    @Test
    public void emptyBorderOnEverySideIsRemoved() {
        TiledMapLayerData map = map();
        fill(map, 2, 2, 4, 4);
        SpatialTileSelectionService selection = select(1, 1, 5, 5);

        assertRange(selection.normalize(map), 2, 2, 5, 5, 9);
        SpatialBlockData wall = selection.toSpatialBlockData(map, 3f, 12f);

        Assert.assertNotNull(wall);
        Assert.assertEquals(2f, wall.x, 0f);
        Assert.assertEquals(2f, wall.y, 0f);
        Assert.assertEquals(3f, wall.width, 0f);
        Assert.assertEquals(3f, wall.depth, 0f);
        Assert.assertEquals(9, wall.linkedTileRefs.size);
    }

    @Test
    public void oneOccupiedCellSurroundedByEmptyCellsNormalizesToOneByOne() {
        TiledMapLayerData map = map();
        map.setTile(3, 4, 77);

        SpatialTileSelectionService.NormalizedSelection range = select(0, 0, 7, 7).normalize(map);

        assertRange(range, 3, 4, 4, 5, 1);
    }

    @Test
    public void emptySelectionIsInvalid() {
        SpatialTileSelectionService.NormalizedSelection range = select(1, 1, 4, 4).normalize(map());

        Assert.assertEquals(SpatialTileSelectionService.NormalizationStatus.EMPTY, range.status);
    }

    @Test
    public void internalHoleIsInvalid() {
        TiledMapLayerData map = map();
        fill(map, 1, 1, 3, 3);
        map.setTile(2, 2, 0);

        SpatialTileSelectionService.NormalizedSelection range = select(1, 1, 3, 3).normalize(map);

        Assert.assertEquals(SpatialTileSelectionService.NormalizationStatus.HAS_INTERNAL_EMPTY_CELL, range.status);
    }

    @Test
    public void disconnectedIslandsAreInvalid() {
        TiledMapLayerData map = map();
        fill(map, 1, 2, 2, 2);
        fill(map, 5, 2, 6, 2);

        SpatialTileSelectionService.NormalizedSelection range = select(1, 2, 6, 2).normalize(map);

        Assert.assertEquals(
                SpatialTileSelectionService.NormalizationStatus.DISCONNECTED_OR_NON_RECTANGULAR,
                range.status);
    }

    @Test
    public void lShapeIsInvalid() {
        TiledMapLayerData map = map();
        fill(map, 1, 1, 1, 3);
        fill(map, 1, 3, 3, 3);

        SpatialTileSelectionService.NormalizedSelection range = select(1, 1, 3, 3).normalize(map);

        Assert.assertEquals(
                SpatialTileSelectionService.NormalizationStatus.DISCONNECTED_OR_NON_RECTANGULAR,
                range.status);
    }

    @Test
    public void reverseDragProducesIdenticalNormalizedBounds() {
        TiledMapLayerData map = map();
        fill(map, 2, 3, 5, 4);
        SpatialTileSelectionService forward = select(0, 1, 7, 6);
        SpatialTileSelectionService reverse = select(7, 6, 0, 1);

        assertSameRange(forward.normalize(map), reverse.normalize(map));
    }

    @Test
    public void addingPeripheralEmptyCellsKeepsTheSameValidPreviewRange() {
        TiledMapLayerData map = map();
        fill(map, 2, 2, 4, 2);
        SpatialTileSelectionService selection = select(2, 2, 4, 2);
        SpatialTileSelectionService.NormalizedSelection first = selection.normalize(map);
        int minX = first.minGx;
        int maxX = first.maxGxExclusive;
        int minY = first.minGy;
        int maxY = first.maxGyExclusive;

        selection.beginDrag(1, 1, 1);
        selection.updateDrag(5, 3);
        SpatialTileSelectionService.NormalizedSelection expanded = selection.normalize(map);

        Assert.assertTrue(expanded.isValid());
        Assert.assertEquals(minX, expanded.minGx);
        Assert.assertEquals(maxX, expanded.maxGxExclusive);
        Assert.assertEquals(minY, expanded.minGy);
        Assert.assertEquals(maxY, expanded.maxGyExclusive);
    }

    @Test
    public void mapEdgePeripheralCoordinatesAreIgnoredWhenOccupiedResultIsValid() {
        TiledMapLayerData map = map();
        fill(map, 0, 0, 1, 1);

        SpatialTileSelectionService.NormalizedSelection range = select(-3, -2, 2, 2).normalize(map);

        assertRange(range, 0, 0, 2, 2, 4);
    }

    @Test
    public void creationAndUndoRedoUseOnlyNormalizedRowMajorRefs() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        HistoryManager history = new HistoryManager(8);
        history.historyIds().ensureForEntity(layer);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = map();
        fill(tiled.data, 2, 3, 4, 4);
        SpatialBlocksComponent walls = world.getMapper(SpatialBlocksComponent.class).create(layer);
        SpatialTileSelectionService selection = select(0, 0, 7, 7);
        selection.beginDrag(layer, 0, 0);
        selection.updateDrag(7, 7);
        selection.finishDrag();

        Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(
                world, history, new SpatialBlockSelectionService(), selection));
        assertNormalizedWall(walls.blocks.first());

        history.undo();
        Assert.assertEquals(0, walls.blocks.size);
        history.redo();
        assertNormalizedWall(walls.blocks.first());
    }

    private static void assertPeripheralBorderRemoved(int rawMinX, int rawMinY, int rawMaxX, int rawMaxY,
                                                      int occupiedMinX, int occupiedMinY,
                                                      int occupiedMaxX, int occupiedMaxY) {
        TiledMapLayerData map = map();
        fill(map, occupiedMinX, occupiedMinY, occupiedMaxX, occupiedMaxY);

        SpatialTileSelectionService.NormalizedSelection range =
                select(rawMinX, rawMinY, rawMaxX, rawMaxY).normalize(map);

        assertRange(range,
                occupiedMinX, occupiedMinY, occupiedMaxX + 1, occupiedMaxY + 1,
                (occupiedMaxX - occupiedMinX + 1) * (occupiedMaxY - occupiedMinY + 1));
    }

    private static void assertNormalizedWall(SpatialBlockData wall) {
        Assert.assertEquals(2f, wall.x, 0f);
        Assert.assertEquals(3f, wall.y, 0f);
        Assert.assertEquals(3f, wall.width, 0f);
        Assert.assertEquals(2f, wall.depth, 0f);
        Assert.assertEquals(6, wall.linkedTileRefs.size);
        for (int i = 0; i < 6; i++) {
            Assert.assertEquals(2 + i % 3, wall.linkedTileRefs.get(i).gx);
            Assert.assertEquals(3 + i / 3, wall.linkedTileRefs.get(i).gy);
            Assert.assertTrue(wall.linkedTileRefs.get(i).tileAssetId > 0);
        }
    }

    private static void assertSameRange(SpatialTileSelectionService.NormalizedSelection first,
                                        SpatialTileSelectionService.NormalizedSelection second) {
        Assert.assertEquals(first.status, second.status);
        Assert.assertEquals(first.minGx, second.minGx);
        Assert.assertEquals(first.maxGxExclusive, second.maxGxExclusive);
        Assert.assertEquals(first.minGy, second.minGy);
        Assert.assertEquals(first.maxGyExclusive, second.maxGyExclusive);
        Assert.assertEquals(first.occupiedCellCount, second.occupiedCellCount);
    }

    private static void assertRange(SpatialTileSelectionService.NormalizedSelection range,
                                    int minX, int minY, int maxXExclusive, int maxYExclusive, int count) {
        Assert.assertEquals(SpatialTileSelectionService.NormalizationStatus.VALID_FILLED_RECTANGLE, range.status);
        Assert.assertEquals(minX, range.minGx);
        Assert.assertEquals(minY, range.minGy);
        Assert.assertEquals(maxXExclusive, range.maxGxExclusive);
        Assert.assertEquals(maxYExclusive, range.maxGyExclusive);
        Assert.assertEquals(count, range.occupiedCellCount);
    }

    private static SpatialTileSelectionService select(int startX, int startY, int endX, int endY) {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        selection.beginDrag(1, startX, startY);
        selection.updateDrag(endX, endY);
        selection.finishDrag();
        return selection;
    }

    private static TiledMapLayerData map() {
        return new TiledMapLayerData(8, 8, 64, 32, 4, SceneMetaRuntime.TiledProjection.ISO);
    }

    private static void fill(TiledMapLayerData map, int minX, int minY, int maxX, int maxY) {
        int id = 1;
        for (int gy = minY; gy <= maxY; gy++) {
            for (int gx = minX; gx <= maxX; gx++) map.setTile(gx, gy, id++);
        }
    }
}
