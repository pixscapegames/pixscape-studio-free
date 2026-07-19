package games.pixscape.studio.ui.asset;

import games.pixscape.studio.service.asset.TilesetSliceLayout;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TilesetPreviewSliceTest {

    @Test
    public void calculate_matchesImportBehaviorForLargeTilesetGrid() {
        TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(448, 800, 32, 32, 0, 0);

        assertTrue(layout.valid());
        assertEquals(14, layout.columns());
        assertEquals(25, layout.rows());
        assertEquals(350, layout.tileCount());
        assertEquals(0, layout.unusedRightPixels());
        assertEquals(0, layout.unusedBottomPixels());
    }

    @Test
    public void sourceRect_mapsTileIndicesToImportSourceRectangles() {
        TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(448, 800, 32, 32, 0, 0);

        assertRect(layout.sourceRect(0), 0, 0, 0, 32, 32);
        assertRect(layout.sourceRect(1), 1, 32, 0, 32, 32);
        assertRect(layout.sourceRect(13), 13, 416, 0, 32, 32);
        assertRect(layout.sourceRect(14), 14, 0, 32, 32, 32);
    }

    @Test
    public void calculate_marginAndSpacingMatchImportBehavior() {
        TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(8, 8, 2, 2, 1, 1);

        assertTrue(layout.valid());
        assertEquals(2, layout.columns());
        assertEquals(2, layout.rows());
        assertEquals(4, layout.tileCount());
        assertEquals(2, layout.unusedRightPixels());
        assertEquals(2, layout.unusedBottomPixels());
        assertRect(layout.sourceRect(3), 3, 4, 4, 2, 2);
    }

    @Test
    public void calculate_invalidSlicingReturnsInvalidStatusWithoutThrowing() {
        TilesetSliceLayout.Layout zeroTile = TilesetSliceLayout.calculate(8, 8, 0, 2, 0, 0);
        TilesetSliceLayout.Layout noTileFits = TilesetSliceLayout.calculate(8, 8, 9, 2, 0, 0);
        TilesetSliceLayout.Layout negativeSpacing = TilesetSliceLayout.calculate(8, 8, 2, 2, -1, 0);

        assertFalse(zeroTile.valid());
        assertEquals("tile size must be > 0", zeroTile.invalidReason());
        assertFalse(noTileFits.valid());
        assertEquals("no tile fits", noTileFits.invalidReason());
        assertFalse(negativeSpacing.valid());
        assertEquals("spacing and margin must be >= 0", negativeSpacing.invalidReason());
    }

    @Test
    public void clampedSourceRect_clampsIndexWhenTileCountShrinks() {
        TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(64, 32, 16, 16, 0, 0);

        assertRect(layout.clampedSourceRect(99), 7, 48, 16, 16, 16);
    }

    @Test
    public void sourceRect_reportsOutOfBoundsWithoutThrowing() {
        TilesetSliceLayout.Layout layout = TilesetSliceLayout.calculate(64, 32, 16, 16, 0, 0);
        TilesetSliceLayout.SourceRect rect = layout.sourceRect(99);

        assertFalse(rect.valid());
        assertEquals("tile index out of bounds", rect.invalidReason());
    }

    private static void assertRect(TilesetSliceLayout.SourceRect rect,
                                   int tileIndex,
                                   int x,
                                   int y,
                                   int width,
                                   int height) {
        assertTrue(rect.valid());
        assertEquals(tileIndex, rect.tileIndex());
        assertEquals(x, rect.x());
        assertEquals(y, rect.y());
        assertEquals(width, rect.width());
        assertEquals(height, rect.height());
    }
}
