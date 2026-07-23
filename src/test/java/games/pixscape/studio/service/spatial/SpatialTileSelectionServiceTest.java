package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.SelectionService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class SpatialTileSelectionServiceTest {
    @Test
    public void predictiveHoverIsSeparateFromAuthoredRangeSelection() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        selection.setHover(7, 3, 4);

        Assert.assertTrue(selection.hasHover());
        Assert.assertFalse(selection.hasSelection());
        Assert.assertFalse(selection.isDragging());
        Assert.assertEquals(3, selection.getHoverGx());
        Assert.assertEquals(4, selection.getHoverGy());
        selection.clearHover();
        Assert.assertFalse(selection.hasHover());
    }
    @Test
    public void dragHorizontalCreatesOneCellTallRectangle() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();

        selection.beginDrag(12, 2, 6);
        selection.updateDrag(8, 6);
        selection.finishDrag();

        Assert.assertTrue(selection.hasSelection());
        Assert.assertFalse(selection.isDragging());
        Assert.assertEquals(2, selection.getMinGx());
        Assert.assertEquals(6, selection.getMinGy());
        Assert.assertEquals(8, selection.getMaxGx());
        Assert.assertEquals(6, selection.getMaxGy());
        Assert.assertEquals(9, selection.getMaxGxExclusive());
        Assert.assertEquals(7, selection.getMaxGyExclusive());
        Assert.assertEquals(7, selection.getCellCount());
        Assert.assertTrue(selection.contains(12, 7, 6));
        Assert.assertFalse(selection.contains(12, 7, 7));
        Assert.assertFalse(selection.contains(99, 7, 6));
    }

    @Test
    public void dragVerticalCreatesOneCellWideRectangle() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();

        selection.beginDrag(4, 5, 1);
        selection.updateDrag(5, 5);
        selection.finishDrag();

        Assert.assertEquals(5, selection.getMinGx());
        Assert.assertEquals(1, selection.getMinGy());
        Assert.assertEquals(5, selection.getMaxGx());
        Assert.assertEquals(5, selection.getMaxGy());
        Assert.assertEquals(5, selection.getCellCount());
    }

    @Test
    public void diagonalDragCreatesNormalizedRectangle() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(3, 5);
        selection.updateDrag(6, 8);
        selection.finishDrag();

        Assert.assertEquals(1, selection.getMinGx());
        Assert.assertEquals(2, selection.getMinGy());
        Assert.assertEquals(6, selection.getMaxGx());
        Assert.assertEquals(8, selection.getMaxGy());
        Assert.assertEquals(42, selection.getCellCount());
        Assert.assertTrue(selection.contains(4, 3, 5));
        Assert.assertTrue(selection.contains(4, 6, 8));
    }

    @Test
    public void singleCellSelectionRemainsValid() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(4, 4);
        map.setTile(2, 3, 101);

        selection.beginDrag(4, 2, 3);
        selection.finishDrag();

        SpatialBlockData block = selection.toSpatialBlockData(map, 155f, 18f);

        Assert.assertNotNull(block);
        Assert.assertEquals(2f, block.x, 0.0001f);
        Assert.assertEquals(3f, block.y, 0.0001f);
        Assert.assertEquals(1f, block.width, 0.0001f);
        Assert.assertEquals(1f, block.depth, 0.0001f);
        Assert.assertEquals(155f, block.altitude, 0.0001f);
        Assert.assertEquals(18f, block.height, 0.0001f);
        Assert.assertTrue(block.actorOccluder);
        Assert.assertTrue(block.linkedTileRefsAuthored);
        Assert.assertEquals(1, block.linkedTileRefs.size);
        Assert.assertEquals(2, block.linkedTileRefs.get(0).gx);
        Assert.assertEquals(3, block.linkedTileRefs.get(0).gy);
        Assert.assertEquals(101, block.linkedTileRefs.get(0).tileAssetId);
    }

    @Test
    public void rectangularSelectionCreatesLinkedRefsForEveryCell() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);
        fillRect(map, 1, 2, 3, 3, 100);

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(3, 3);
        selection.finishDrag();

        Assert.assertEquals(2, selection.getMinGy());
        Assert.assertEquals(3, selection.getMaxGy());

        SpatialBlockData block = selection.toSpatialBlockData(map, 0f, 10f);
        Assert.assertNotNull(block);
        Assert.assertEquals(6, block.linkedTileRefs.size);
        Assert.assertEquals(1, block.linkedTileRefs.get(0).gx);
        Assert.assertEquals(2, block.linkedTileRefs.get(0).gy);
        Assert.assertEquals(100, block.linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(3, block.linkedTileRefs.get(5).gx);
        Assert.assertEquals(3, block.linkedTileRefs.get(5).gy);
        Assert.assertEquals(105, block.linkedTileRefs.get(5).tileAssetId);
    }

    @Test
    public void oneEmptyCellRejectsTheWholeSelectedRectangle() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);
        map.setTile(1, 2, 101);
        map.setTile(3, 2, 103);

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(3, 2);
        selection.finishDrag();

        Assert.assertEquals(SpatialTileSelectionService.INVALID_NON_RECTANGULAR, selection.validationMessage(map));
        Assert.assertNull(selection.toSpatialBlockData(map, 0f, 10f));
    }

    @Test
    public void sparseSelectionIsNotShrunkToOccupiedRows() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);
        map.setTile(1, 2, 101);
        map.setTile(3, 3, 102);
        map.setTile(2, 4, 103);
        map.setTile(3, 4, 104);

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(3, 4);
        selection.finishDrag();

        SpatialBlockData block = selection.toSpatialBlockData(map, 0f, 10f);

        Assert.assertNull(block);
    }

    @Test
    public void isoSelectionKeepsExactAuthoredRectangle() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = isoMap(8, 8);
        map.setTile(1, 2, 101);
        map.setTile(2, 2, 102);
        map.setTile(3, 2, 103);

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(3, 2);
        selection.finishDrag();

        SpatialBlockData block = selection.toSpatialBlockData(map, 0f, 10f);

        Assert.assertNotNull(block);
        Assert.assertEquals(1f, block.x, 0.0001f);
        Assert.assertEquals(2f, block.y, 0.0001f);
        Assert.assertEquals(3f, block.width, 0.0001f);
        Assert.assertEquals(1f, block.depth, 0.0001f);
        Assert.assertEquals(3, block.linkedTileRefs.size);
    }

    @Test
    public void fullyEmptySelectionIsInvalidAndDoesNotCreateBrokenRefs() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(3, 3);
        selection.finishDrag();

        Assert.assertEquals(SpatialTileSelectionService.INVALID_EMPTY_CELLS, selection.validationMessage(map));
        Assert.assertNull(selection.toSpatialBlockData(map, 0f, 10f));
    }

    @Test
    public void overlappingSpatialVolumeDoesNotInvalidateAnchorSelection() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);
        map.setTile(1, 2, 101);

        selection.beginDrag(4, 1, 2);
        selection.finishDrag();

        Assert.assertNull(selection.validationMessage(map));
        Assert.assertNotNull(selection.toSpatialBlockData(map, 0f, 10f));
    }

    @Test
    public void createdLinkedRefsContainTileMetadataOnlyAndNoRuntimeCacheData() throws Exception {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);
        map.setTile(1, 2, 101);
        map.setTile(2, 2, 102);

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(2, 2);
        selection.finishDrag();

        SpatialBlockData block = selection.toSpatialBlockData(map, 0f, 10f);

        Assert.assertNotNull(block);
        Assert.assertEquals(2, block.linkedTileRefs.size);
        Assert.assertEquals(1, block.linkedTileRefs.get(0).gx);
        Assert.assertEquals(2, block.linkedTileRefs.get(0).gy);
        Assert.assertEquals(101, block.linkedTileRefs.get(0).tileAssetId);
        assertNoRuntimeCacheField("drawSlot");
        assertNoRuntimeCacheField("drawIndex");
        assertNoRuntimeCacheField("anchorDrawIndex");
        assertNoRuntimeCacheField("resolvedAnchorIndex");
        assertNoRuntimeCacheField("insertionTarget");
    }

    @Test
    public void squareSelectionKeepsExistingLinkedTileRefsArrayFormat() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);
        fillRect(map, 2, 3, 3, 4, 200);

        selection.beginDrag(4, 3, 4);
        selection.updateDrag(2, 3);
        selection.finishDrag();

        SpatialBlockData block = selection.toSpatialBlockData(map, 0f, 10f);

        Assert.assertNotNull(block);
        Assert.assertTrue(block.linkedTileRefsAuthored);
        Assert.assertEquals(4, block.linkedTileRefs.size);
        Assert.assertEquals(2, block.linkedTileRefs.get(0).gx);
        Assert.assertEquals(3, block.linkedTileRefs.get(0).gy);
        Assert.assertEquals(200, block.linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(3, block.linkedTileRefs.get(3).gx);
        Assert.assertEquals(4, block.linkedTileRefs.get(3).gy);
        Assert.assertEquals(203, block.linkedTileRefs.get(3).tileAssetId);
    }

    @Test
    public void successiveHorizontalThenVerticalEditsDoNotKeepOldDirection() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        TiledMapLayerData map = map(8, 8);
        fillHorizontal(map, 1, 2, 3, 100);
        fillVertical(map, 5, 1, 3, 200);

        selection.beginDrag(4, 1, 2);
        selection.updateDrag(3, 2);
        selection.finishDrag();

        SpatialBlockData horizontal = selection.toSpatialBlockData(map, 0f, 10f);
        Assert.assertNotNull(horizontal);
        Assert.assertEquals(3, horizontal.linkedTileRefs.size);

        selection.beginDrag(4, 5, 1);
        selection.updateDrag(5, 3);
        selection.finishDrag();

        SpatialBlockData vertical = selection.toSpatialBlockData(map, 0f, 10f);
        Assert.assertNotNull(vertical);
        Assert.assertEquals(3, vertical.linkedTileRefs.size);
        Assert.assertEquals(5, vertical.linkedTileRefs.get(0).gx);
        Assert.assertEquals(1, vertical.linkedTileRefs.get(0).gy);
        Assert.assertEquals(200, vertical.linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(5, vertical.linkedTileRefs.get(2).gx);
        Assert.assertEquals(3, vertical.linkedTileRefs.get(2).gy);
        Assert.assertEquals(202, vertical.linkedTileRefs.get(2).tileAssetId);
    }

    @Test
    public void selectionStaysAttachedToOwningTiledLayerOnly() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();

        selection.beginDrag(77, 1, 2);
        selection.updateDrag(4, 2);
        selection.finishDrag();

        Assert.assertEquals(77, selection.getLayerEntityId());
        Assert.assertTrue(selection.contains(77, 3, 2));
        Assert.assertFalse(selection.contains(78, 3, 2));
    }

    @Test
    public void selectionClearsWhenLayerChangesOrModeLeavesTile() {
        SpatialTileSelectionService selection = new SpatialTileSelectionService();

        selection.beginDrag(7, 1, 1);
        selection.finishDrag();
        EventFlow.i().publish(new EventFlow.CurrentLayerChanged(
                8,
                SelectionService.SelectionSource.VIEWPORT,
                0
        ));
        EventFlow.i().flush();

        Assert.assertFalse(selection.hasSelection());

        selection.beginDrag(7, 1, 1);
        selection.finishDrag();
        EventFlow.i().publish(new EventFlow.EditorModeChanged(EventFlow.EditorMode.ENTITY, 0));
        EventFlow.i().flush();

        Assert.assertFalse(selection.hasSelection());
    }

    private static TiledMapLayerData map(int width, int height) {
        return new TiledMapLayerData(width, height, 16, 16, 8, SceneMetaRuntime.TiledProjection.ORTHO);
    }

    private static TiledMapLayerData isoMap(int width, int height) {
        return new TiledMapLayerData(width, height, 256, 128, 8, SceneMetaRuntime.TiledProjection.ISO);
    }

    private static void fillHorizontal(TiledMapLayerData map, int minGx, int gy, int maxGx, int firstTileId) {
        for (int gx = minGx; gx <= maxGx; gx++) {
            map.setTile(gx, gy, firstTileId + gx - minGx);
        }
    }

    private static void fillVertical(TiledMapLayerData map, int gx, int minGy, int maxGy, int firstTileId) {
        for (int gy = minGy; gy <= maxGy; gy++) {
            map.setTile(gx, gy, firstTileId + gy - minGy);
        }
    }

    private static void fillRect(TiledMapLayerData map, int minGx, int minGy, int maxGx, int maxGy, int firstTileId) {
        int tileId = firstTileId;
        for (int gy = minGy; gy <= maxGy; gy++) {
            for (int gx = minGx; gx <= maxGx; gx++) {
                map.setTile(gx, gy, tileId++);
            }
        }
    }

    private static void assertNoRuntimeCacheField(String name) throws Exception {
        for (Field field : SpatialBlockData.LinkedTileRef.class.getDeclaredFields()) {
            Assert.assertNotEquals(name, field.getName());
        }
    }
}
