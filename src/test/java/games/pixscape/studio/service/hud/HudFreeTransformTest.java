package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudHorizontalAnchor;
import games.pixscape.runtime.hud.document.HudVerticalAnchor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HudFreeTransformTest {
    private static final float EPSILON = 0.001f;

    @Test public void movePreservesCenterAnchorAndNonDefaultPivot() {
        HudFreePlacement placement = placement(HudHorizontalAnchor.CENTER, HudVerticalAnchor.CENTER, .5f, .25f);
        HudFreeTransform.Snapshot snapshot = HudFreeTransform.snapshot(placement, 20f, 40f,
                100f, 80f, new HudTransformGeometry.Bounds(40f, 30f, 20f, 40f));

        HudFreeTransform.Result result = HudFreeTransform.resolve(snapshot,
                new HudTransformGeometry.Bounds(55f, 23f, 20f, 40f), false, false);

        assertEquals(15f, result.offsetX(), EPSILON);
        assertEquals(-7f, result.offsetY(), EPSILON);
        assertEquals(20f, result.width(), EPSILON);
        assertEquals(40f, result.height(), EPSILON);
    }

    @Test public void resizeWritesOnlyChangedAxisAndKeepsAnchorsAndPivotsUntouched() {
        HudFreePlacement placement = placement(HudHorizontalAnchor.RIGHT, HudVerticalAnchor.TOP, 1f, 1f);
        HudFreeTransform.Snapshot snapshot = HudFreeTransform.snapshot(placement, 30f, 40f,
                200f, 100f, new HudTransformGeometry.Bounds(150f, 40f, 30f, 40f));
        HudTransformGeometry.Bounds visual = HudTransformGeometry.resize(snapshot.visualBounds(),
                HudTransformHandle.E, 20f, 0f);

        HudFreeTransform.Result result = HudFreeTransform.resolve(snapshot, visual, true, false);

        assertEquals(0f, result.offsetX(), EPSILON);
        assertEquals(-20f, result.offsetY(), EPSILON);
        assertEquals(50f, result.width(), EPSILON);
        assertEquals(40f, result.height(), EPSILON);
        assertEquals(HudHorizontalAnchor.RIGHT, snapshot.horizontalAnchor());
        assertEquals(HudVerticalAnchor.TOP, snapshot.verticalAnchor());
        assertEquals(1f, snapshot.pivotX(), EPSILON);
        assertEquals(1f, snapshot.pivotY(), EPSILON);
    }

    @Test public void westResizeMovesOffsetWhileKeepingTheRightVisualEdgeFixed() {
        HudFreePlacement placement = placement(HudHorizontalAnchor.LEFT, HudVerticalAnchor.BOTTOM, 0f, 0f);
        HudFreeTransform.Snapshot snapshot = HudFreeTransform.snapshot(placement, 30f, 20f,
                100f, 100f, new HudTransformGeometry.Bounds(10f, 10f, 30f, 20f));
        HudTransformGeometry.Bounds visual = HudTransformGeometry.resize(snapshot.visualBounds(),
                HudTransformHandle.W, 5f, 0f);

        HudFreeTransform.Result result = HudFreeTransform.resolve(snapshot, visual, true, false);

        assertEquals(15f, result.offsetX(), EPSILON);
        assertEquals(10f, result.offsetY(), EPSILON);
        assertEquals(25f, result.width(), EPSILON);
        assertEquals(20f, result.height(), EPSILON);
    }

    @Test public void leftBottomAndRightTopMovesUseTheirActualAnchorOrigins() {
        HudFreePlacement leftBottom = placement(HudHorizontalAnchor.LEFT, HudVerticalAnchor.BOTTOM, 0f, 0f);
        HudFreeTransform.Snapshot leftSnapshot = HudFreeTransform.snapshot(leftBottom, 20f, 20f,
                100f, 100f, new HudTransformGeometry.Bounds(10f, 15f, 20f, 20f));
        HudFreeTransform.Result left = HudFreeTransform.resolve(leftSnapshot,
                new HudTransformGeometry.Bounds(17f, 11f, 20f, 20f), false, false);
        assertEquals(17f, left.offsetX(), EPSILON);
        assertEquals(11f, left.offsetY(), EPSILON);

        HudFreePlacement rightTop = placement(HudHorizontalAnchor.RIGHT, HudVerticalAnchor.TOP, 0f, 0f);
        HudFreeTransform.Snapshot rightSnapshot = HudFreeTransform.snapshot(rightTop, 20f, 20f,
                100f, 100f, new HudTransformGeometry.Bounds(70f, 60f, 20f, 20f));
        HudFreeTransform.Result right = HudFreeTransform.resolve(rightSnapshot,
                new HudTransformGeometry.Bounds(77f, 56f, 20f, 20f), false, false);
        assertEquals(-23f, right.offsetX(), EPSILON);
        assertEquals(-44f, right.offsetY(), EPSILON);
    }

    @Test public void northSouthAndCornerResizeResolveBothSizeAndOffsetAxes() {
        HudFreePlacement placement = placement(HudHorizontalAnchor.LEFT, HudVerticalAnchor.BOTTOM, 0f, 0f);
        HudFreeTransform.Snapshot snapshot = HudFreeTransform.snapshot(placement, 30f, 40f,
                100f, 100f, new HudTransformGeometry.Bounds(10f, 20f, 30f, 40f));

        HudFreeTransform.Result north = HudFreeTransform.resolve(snapshot,
                HudTransformGeometry.resize(snapshot.visualBounds(), HudTransformHandle.N, 0f, 5f), false, true);
        assertEquals(10f, north.offsetX(), EPSILON);
        assertEquals(20f, north.offsetY(), EPSILON);
        assertEquals(30f, north.width(), EPSILON);
        assertEquals(45f, north.height(), EPSILON);

        HudFreeTransform.Result southWest = HudFreeTransform.resolve(snapshot,
                HudTransformGeometry.resize(snapshot.visualBounds(), HudTransformHandle.SW, 4f, 6f), true, true);
        assertEquals(14f, southWest.offsetX(), EPSILON);
        assertEquals(26f, southWest.offsetY(), EPSILON);
        assertEquals(26f, southWest.width(), EPSILON);
        assertEquals(34f, southWest.height(), EPSILON);
    }

    private static HudFreePlacement placement(HudHorizontalAnchor horizontal,
                                              HudVerticalAnchor vertical, float pivotX, float pivotY) {
        HudFreePlacement placement = new HudFreePlacement();
        placement.horizontalAnchor = horizontal;
        placement.verticalAnchor = vertical;
        placement.pivotX = pivotX;
        placement.pivotY = pivotY;
        return placement;
    }
}
