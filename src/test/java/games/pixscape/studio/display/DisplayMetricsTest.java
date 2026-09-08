package games.pixscape.studio.display;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DisplayMetricsTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void reportsNormalDensityDisplay() {
        DisplayMetrics metrics = metrics(1920, 1080, 1920, 1080);

        assertEquals(1f, metrics.scaleX(), EPSILON);
        assertEquals(1f, metrics.scaleY(), EPSILON);
        assertFalse(metrics.isHiDpi());
    }

    @Test
    public void reportsTwoTimesDisplayScale() {
        DisplayMetrics metrics = metrics(1280, 720, 2560, 1440);

        assertEquals(2f, metrics.scaleX(), EPSILON);
        assertEquals(2f, metrics.scaleY(), EPSILON);
        assertTrue(metrics.isHiDpi());
    }

    @Test
    public void reportsNonIntegerDisplayScale() {
        DisplayMetrics metrics = metrics(1280, 800, 1920, 1200);

        assertEquals(1.5f, metrics.scaleX(), EPSILON);
        assertEquals(1.5f, metrics.scaleY(), EPSILON);
        assertTrue(metrics.isHiDpi());
    }

    @Test
    public void zeroLogicalDimensionKeepsLastValidScale() {
        DisplayMetrics metrics = metrics(1280, 720, 2560, 1440);

        metrics.update(0, 0, 0, 0);

        assertEquals(2f, metrics.scaleX(), EPSILON);
        assertEquals(2f, metrics.scaleY(), EPSILON);
        assertTrue(Float.isFinite(metrics.scaleX()));
        assertTrue(Float.isFinite(metrics.scaleY()));
    }

    @Test
    public void updatesFromNormalToHighDensity() {
        DisplayMetrics metrics = metrics(1920, 1080, 1920, 1080);

        metrics.update(1280, 720, 2560, 1440);

        assertEquals(2f, metrics.scaleX(), EPSILON);
        assertEquals(2f, metrics.scaleY(), EPSILON);
        assertTrue(metrics.isHiDpi());
    }

    @Test
    public void updatesFromHighToNormalDensity() {
        DisplayMetrics metrics = metrics(1280, 720, 2560, 1440);

        metrics.update(1920, 1080, 1920, 1080);

        assertEquals(1f, metrics.scaleX(), EPSILON);
        assertEquals(1f, metrics.scaleY(), EPSILON);
        assertFalse(metrics.isHiDpi());
    }

    private static DisplayMetrics metrics(int logicalWidth,
                                          int logicalHeight,
                                          int framebufferWidth,
                                          int framebufferHeight) {
        DisplayMetrics metrics = new DisplayMetrics();
        metrics.update(logicalWidth, logicalHeight, framebufferWidth, framebufferHeight);
        return metrics;
    }
}
