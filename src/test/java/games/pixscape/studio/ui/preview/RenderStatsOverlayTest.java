package games.pixscape.studio.ui.preview;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RenderStatsOverlayTest {
    @Test
    public void hiddenOverlaySkipsTheEntireDrawPath() {
        assertFalse(RenderStatsOverlay.submitsDraws(false));
        assertTrue(RenderStatsOverlay.submitsDraws(true));
    }
}
