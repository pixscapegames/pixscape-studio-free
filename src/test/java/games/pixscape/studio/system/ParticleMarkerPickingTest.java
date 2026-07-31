package games.pixscape.studio.system;

import games.pixscape.studio.service.ParticleOverlayVisual;
import org.junit.Test;

import static org.junit.Assert.*;

public class ParticleMarkerPickingTest {

    @Test
    public void markerHitUsesEmitterPoint() {
        float radius = 15f;
        assertTrue(PickingSystem.isParticleMarkerHit(10f, 20f, 10f, 20f, radius * radius));
        assertTrue(PickingSystem.isParticleMarkerHit(25f, 20f, 10f, 20f, radius * radius));
        assertFalse(PickingSystem.isParticleMarkerHit(25.1f, 20f, 10f, 20f, radius * radius));
    }

    @Test
    public void markerRadiusIsFixedInPixelsAcrossZoom() {
        float expectedPixels = ParticleOverlayVisual.MARKER_SIZE_PX * 0.5f
                + PickingSystem.PARTICLE_PICK_TOL_PX;
        float zoomOneWorldPerPixel = 0.5f;
        float zoomFourWorldPerPixel = 2f;

        assertEquals(expectedPixels * zoomOneWorldPerPixel,
                PickingSystem.particleMarkerHitRadiusWorld(zoomOneWorldPerPixel), 0f);
        assertEquals(expectedPixels,
                PickingSystem.particleMarkerHitRadiusWorld(zoomFourWorldPerPixel)
                        / zoomFourWorldPerPixel, 0f);
    }

    @Test
    public void lassoUsesEmitterPoint() {
        assertTrue(PickingSystem.isPointInsideLasso(5f, 6f, 0f, 0f, 10f, 10f));
        assertFalse(PickingSystem.isPointInsideLasso(11f, 6f, 0f, 0f, 10f, 10f));
    }
}
