package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class CanvasModeIndicatorTest {
    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void mapsAllFiveLabelsExactly() {
        Assert.assertEquals("Normal", CanvasModeIndicator.displayName(StudioEditingMode.NORMAL));
        Assert.assertEquals("Physics", CanvasModeIndicator.displayName(StudioEditingMode.PHYSICS));
        Assert.assertEquals("Spatial", CanvasModeIndicator.displayName(StudioEditingMode.SPATIAL));
        Assert.assertEquals("Tiled", CanvasModeIndicator.displayName(StudioEditingMode.TILED));
        Assert.assertEquals("Lights", CanvasModeIndicator.displayName(StudioEditingMode.LIGHTS));
    }

    @Test
    public void isNonInteractiveAndUpdatesStyleFromTheEvent() {
        EventFlow.i().flush();
        StudioEditingModeService service = new StudioEditingModeService();
        CanvasModeIndicator indicator = new CanvasModeIndicator(service);
        try {
            Assert.assertEquals(Touchable.disabled, indicator.getTouchable());
            Assert.assertEquals("Mode: Normal", indicator.getDisplayedText());
            Assert.assertTrue(indicator.isUsingNormalBackground());

            service.setMode(StudioEditingMode.SPATIAL, 1);
            EventFlow.i().flush();
            Assert.assertEquals("Mode: Spatial", indicator.getDisplayedText());
            Assert.assertFalse(indicator.isUsingNormalBackground());
        } finally {
            indicator.dispose();
        }
    }

    @Test
    public void showsCurrentTiledCoordinatesAndClearsInvalidCoordinates() {
        EventFlow.i().flush();
        StudioEditingModeService service = new StudioEditingModeService();
        CanvasModeIndicator indicator = new CanvasModeIndicator(service);
        try {
            service.reset(1);
            service.setMode(StudioEditingMode.TILED, 1);
            EventFlow.i().flush();
            Assert.assertEquals("Mode: Tiled    Tile: (-, -)", indicator.getDisplayedText());

            EventFlow.i().publish(new EventFlow.TiledCursorChanged(true, -3, 42, 2));
            EventFlow.i().flush();
            Assert.assertEquals("Mode: Tiled    Tile: (-3, 42)", indicator.getDisplayedText());

            EventFlow.i().publish(new EventFlow.TiledCursorChanged(false, 0, 0, 2));
            EventFlow.i().flush();
            Assert.assertEquals("Mode: Tiled    Tile: (-, -)", indicator.getDisplayedText());

            EventFlow.i().publish(new EventFlow.TiledCursorChanged(true, 7, 8, 2));
            EventFlow.i().flush();
            service.setMode(StudioEditingMode.SPATIAL, 1);
            EventFlow.i().flush();
            Assert.assertEquals("Mode: Spatial", indicator.getDisplayedText());

            service.reset(1);
            service.setMode(StudioEditingMode.TILED, 1);
            EventFlow.i().flush();
            Assert.assertEquals("Mode: Tiled    Tile: (-, -)", indicator.getDisplayedText());
        } finally {
            indicator.dispose();
        }
    }
}
