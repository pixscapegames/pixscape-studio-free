package games.pixscape.studio.ui.preview;

import org.junit.Test;

import static org.junit.Assert.*;

public class PreviewFrameCadenceTest {
    @Test public void averagesFramesOverRealDurationInsteadOfInstantaneousFps() {
        PreviewFrameCadence cadence = new PreviewFrameCadence();
        cadence.onFrameStart(0);
        cadence.onFrameStart(10_000_000);
        cadence.onFrameStart(40_000_000);
        assertEquals(50, cadence.averageFps(), 0);
        assertEquals(20, cadence.averageMs(), 0);
        assertEquals(.04, cadence.seconds(), 0);
        assertFalse(cadence.isFull());
    }

    @Test public void windowSlidesInTimeAndShortFpsRespondsSeparately() {
        PreviewFrameCadence cadence = new PreviewFrameCadence();
        long now = 0;
        cadence.onFrameStart(now);
        for (int i = 0; i < 500; i++) cadence.onFrameStart(now += 10_000_000);
        assertTrue(cadence.isFull());
        assertEquals(100, cadence.averageFps(), 0);
        for (int i = 0; i < 100; i++) cadence.onFrameStart(now += 20_000_000);
        assertEquals(400, cadence.sampleCount());
        assertEquals(5, cadence.seconds(), 0);
        assertEquals(80, cadence.averageFps(), 0);
        assertEquals(12.5, cadence.averageMs(), 0);
        assertEquals(50, cadence.fps(), 0);
    }

    @Test public void boundaryKeepsOneWholeMeasuredFrame() {
        PreviewFrameCadence cadence = new PreviewFrameCadence();
        long now = 0;
        cadence.onFrameStart(now);
        for (int i = 0; i < 1000; i++) cadence.onFrameStart(now += 30_000_000);
        assertEquals(167, cadence.sampleCount());
        assertEquals(5.01, cadence.seconds(), .000001);
        assertEquals(1000.0 / 30, cadence.averageFps(), .000001);
    }

    @Test public void boundedStorageReportsItsActualPartialWindow() {
        PreviewFrameCadence cadence = new PreviewFrameCadence(64);
        for (int i = 0; i <= 1000; i++) cadence.onFrameStart(i * 1_000_000L);
        assertEquals(64, cadence.sampleCount());
        assertEquals(.064, cadence.seconds(), 0);
        assertFalse(cadence.isFull());
        assertEquals(1000, cadence.fps(), 0);
        assertEquals(1000, cadence.averageFps(), 0);
    }

    @Test public void pauseReloadAndDebuggerGapDiscardOldSamples() {
        PreviewFrameCadence cadence = new PreviewFrameCadence();
        cadence.onFrameStart(1);
        cadence.onFrameStart(10_000_001);
        cadence.reset();
        assertEquals(0, cadence.sampleCount());
        assertEquals(0, cadence.averageMs(), 0);
        cadence.onFrameStart(20_000_000_000L);
        assertEquals(0, cadence.sampleCount());
        cadence.onFrameStart(20_020_000_000L);
        assertEquals(50, cadence.averageFps(), 0);
        cadence.onFrameStart(30_000_000_000L);
        assertEquals(0, cadence.sampleCount());
        cadence.onFrameStart(30_040_000_000L);
        assertEquals(25, cadence.averageFps(), 0);
    }
}
