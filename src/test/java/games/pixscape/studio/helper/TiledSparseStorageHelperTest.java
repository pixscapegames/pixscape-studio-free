package games.pixscape.studio.helper;

import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TiledSparseStorageHelperTest {

    @Test
    public void bulkConstructionLeavesEmptyLayerEmpty() {
        TiledLayerComponent bulk = component(4, 4, 2);

        TiledSparseStorageHelper.beginNewLayerStorage(bulk, 0);

        assertEquals(0, bulk.tileXs.size);
        assertEquals(0, bulk.tileYs.size);
        assertEquals(0, bulk.tileAssetIds.size);
        assertEquals(0, bulk.tileTransformFlags.size);
    }

    @Test
    public void bulkConstructionMatchesReferenceForOneCell() {
        assertEquivalent(4, 4, 2, List.of(
                new Cell(2, 1, 17, TileTransformFlags.NONE)
        ));
    }

    @Test
    public void bulkConstructionOmitsEmptyCells() {
        TiledLayerComponent bulk = component(4, 4, 2);
        TiledSparseStorageHelper.NewLayerStorageBuilder builder =
                TiledSparseStorageHelper.beginNewLayerStorage(bulk, 2);

        builder.append(1, 1, 0, TileTransformFlags.FLIP_H);
        builder.append(2, 2, -1, TileTransformFlags.FLIP_V);

        assertEquals(0, bulk.tileAssetIds.size);
        assertEquals(0, bulk.tileTransformFlags.size);
    }

    @Test
    public void bulkConstructionMatchesReferenceForSparseAdjacentAndTransformedCells() {
        assertEquivalent(40, 35, 16, List.of(
                new Cell(0, 0, 11, TileTransformFlags.FLIP_H),
                new Cell(1, 0, 12, TileTransformFlags.FLIP_V),
                new Cell(2, 0, 13, TileTransformFlags.FLIP_D),
                new Cell(17, 18, 10_001,
                        (byte) (TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V)),
                new Cell(39, 34, 99,
                        (byte) (TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V
                                | TileTransformFlags.FLIP_D | 0x40))
        ));
    }

    @Test
    public void bulkConstructionMatchesReferenceAcrossManyCellsAndChunks() {
        List<Cell> cells = new ArrayList<>();
        for (int gy = 0; gy < 64; gy++) {
            for (int gx = 0; gx < 64; gx++) {
                if ((gx + gy) % 3 == 0) {
                    cells.add(new Cell(gx, gy, 1 + gx + gy * 64,
                            (byte) ((gx + gy) & 0x7)));
                }
            }
        }

        assertEquivalent(64, 64, 16, cells);
    }

    @Test(expected = IllegalStateException.class)
    public void bulkConstructionRejectsStorageThatAlreadyContainsCells() {
        TiledLayerComponent component = component(4, 4, 2);
        TiledSparseStorageHelper.setTile(component, 1, 1, 7, TileTransformFlags.NONE);

        TiledSparseStorageHelper.beginNewLayerStorage(component, 1);
    }

    private static void assertEquivalent(int width, int height, int chunkSize, List<Cell> cells) {
        TiledLayerComponent reference = component(width, height, chunkSize);
        TiledLayerComponent bulk = component(width, height, chunkSize);
        TiledSparseStorageHelper.NewLayerStorageBuilder builder =
                TiledSparseStorageHelper.beginNewLayerStorage(bulk, cells.size());

        reference.data.beginContentMutation();
        bulk.data.beginContentMutation();
        try {
            for (Cell cell : cells) {
                reference.data.setTile(cell.gx(), cell.gy(), cell.assetId(), cell.flags());
                TiledSparseStorageHelper.setTile(
                        reference, cell.gx(), cell.gy(), cell.assetId(), cell.flags());

                bulk.data.setTile(cell.gx(), cell.gy(), cell.assetId(), cell.flags());
                builder.append(cell.gx(), cell.gy(), cell.assetId(), cell.flags());
            }
        } finally {
            reference.data.endContentMutation();
            bulk.data.endContentMutation();
        }

        assertEquals(reference.tileXs, bulk.tileXs);
        assertEquals(reference.tileYs, bulk.tileYs);
        assertEquals(reference.tileAssetIds, bulk.tileAssetIds);
        assertEquals(reference.tileTransformFlags, bulk.tileTransformFlags);
        assertEquals(reference.data.contentRevision(), bulk.data.contentRevision());
        assertEquals(reference.data.getChunksX(), bulk.data.getChunksX());
        assertEquals(reference.data.getChunksY(), bulk.data.getChunksY());

        for (int gy = 0; gy < height; gy++) {
            for (int gx = 0; gx < width; gx++) {
                assertEquals(reference.data.getTile(gx, gy), bulk.data.getTile(gx, gy));
                assertEquals(reference.data.getTileTransformFlags(gx, gy),
                        bulk.data.getTileTransformFlags(gx, gy));
            }
        }
    }

    private static TiledLayerComponent component(int width, int height, int chunkSize) {
        TiledLayerComponent component = new TiledLayerComponent();
        component.mapWidthCells = width;
        component.mapHeightCells = height;
        component.data = new TiledMapLayerData(
                width,
                height,
                16,
                16,
                chunkSize,
                SceneMetaRuntime.TiledProjection.ORTHO
        );
        return component;
    }

    private record Cell(int gx, int gy, int assetId, byte flags) {
    }
}
