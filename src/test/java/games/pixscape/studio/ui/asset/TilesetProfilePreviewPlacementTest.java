package games.pixscape.studio.ui.asset;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.asset.TilesetAnchor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TilesetProfilePreviewPlacementTest {

    @Test
    public void isometricPlacementBoundsContainTallTileAndShortCell() {
        TilesetProfilePreviewPlacement.Placement placement = TilesetProfilePreviewPlacement.calculate(
                256,
                512,
                256,
                128,
                SceneMetaRuntime.TiledProjection.ISO,
                TilesetAnchor.BOTTOM_CENTER,
                0,
                0
        );

        assertTrue(placement.unionBounds().contains(placement.tileBounds()));
        assertTrue(placement.unionBounds().contains(placement.cellBounds()));
        assertEquals(4, placement.cellOutline().length);
        assertPoint(placement.cellOutline()[0], 0f, 0f);
        assertPoint(placement.cellOutline()[1], 128f, 64f);
        assertPoint(placement.cellOutline()[2], 0f, 128f);
        assertPoint(placement.cellOutline()[3], -128f, 64f);
    }

    @Test
    public void anchorChangesMoveTileRelativeToReferenceCell() {
        TilesetProfilePreviewPlacement.Placement bottomCenter = TilesetProfilePreviewPlacement.calculate(
                32,
                64,
                32,
                32,
                SceneMetaRuntime.TiledProjection.ORTHO,
                TilesetAnchor.BOTTOM_CENTER,
                0,
                0
        );
        TilesetProfilePreviewPlacement.Placement topLeft = TilesetProfilePreviewPlacement.calculate(
                32,
                64,
                32,
                32,
                SceneMetaRuntime.TiledProjection.ORTHO,
                TilesetAnchor.TOP_LEFT,
                0,
                0
        );

        assertBounds(bottomCenter.tileBounds(), -16f, 0f, 32f, 64f);
        assertBounds(topLeft.tileBounds(), 0f, -64f, 32f, 64f);
    }

    @Test
    public void offsetMovesTileRelativeToReferenceCell() {
        TilesetProfilePreviewPlacement.Placement placement = TilesetProfilePreviewPlacement.calculate(
                32,
                64,
                32,
                32,
                SceneMetaRuntime.TiledProjection.ORTHO,
                TilesetAnchor.BOTTOM_CENTER,
                10,
                -6
        );

        assertBounds(placement.tileBounds(), -6f, -6f, 32f, 64f);
        assertBounds(placement.cellBounds(), -16f, 0f, 32f, 32f);
    }

    private static void assertBounds(TilesetProfilePreviewPlacement.Bounds bounds,
                                     float x,
                                     float y,
                                     float width,
                                     float height) {
        assertEquals(x, bounds.x(), 0.001f);
        assertEquals(y, bounds.y(), 0.001f);
        assertEquals(width, bounds.width(), 0.001f);
        assertEquals(height, bounds.height(), 0.001f);
    }

    private static void assertPoint(TilesetProfilePreviewPlacement.Point point, float x, float y) {
        assertEquals(x, point.x(), 0.001f);
        assertEquals(y, point.y(), 0.001f);
    }
}
