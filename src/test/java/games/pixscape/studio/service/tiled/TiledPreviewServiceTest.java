package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class TiledPreviewServiceTest {
    @Test
    public void brushPreviewShowsCoverageAndGhost() {
        TiledPreviewService service = new TiledPreviewService();
        TiledMapLayerData map = new TiledMapLayerData();

        service.show(map, "main", 2, 3, 9, (byte) 0);

        Assert.assertTrue(service.isCoverageVisible());
        Assert.assertTrue(service.isGhostVisible());
        Assert.assertTrue(service.isVisible());
        Assert.assertEquals(2, service.gx());
        Assert.assertEquals(3, service.gy());
        Assert.assertEquals(9, service.assetId());
    }

    @Test
    public void erasePreviewShowsCoverageWithoutGhost() {
        TiledPreviewService service = new TiledPreviewService();
        TiledMapLayerData map = new TiledMapLayerData();

        service.showCoverageOnly(map, "main", 4, 5, 11, (byte) 0);

        Assert.assertTrue(service.isCoverageVisible());
        Assert.assertFalse(service.isGhostVisible());
        Assert.assertFalse(service.isVisible());
        Assert.assertEquals(4, service.gx());
        Assert.assertEquals(5, service.gy());
        Assert.assertEquals(11, service.assetId());
    }

    @Test
    public void tintedPreviewShowsCoverageTintWithoutGhost() {
        TiledPreviewService service = new TiledPreviewService();
        TiledMapLayerData map = new TiledMapLayerData();

        service.showTintedCoverage(map, "main", 6, 7, 13, (byte) 0, 0.05f, 0.92f, 1f, 0.5f);

        Assert.assertTrue(service.isCoverageVisible());
        Assert.assertFalse(service.isGhostVisible());
        Assert.assertTrue(service.isTintVisible());
        Assert.assertTrue(service.isVisible());
        Assert.assertEquals(6, service.gx());
        Assert.assertEquals(7, service.gy());
        Assert.assertEquals(13, service.assetId());
        Assert.assertEquals(0.05f, service.tintR(), 0.0001f);
        Assert.assertEquals(0.92f, service.tintG(), 0.0001f);
        Assert.assertEquals(1f, service.tintB(), 0.0001f);
        Assert.assertEquals(0.5f, service.tintA(), 0.0001f);
    }

    @Test
    public void clearHidesCoverageAndGhost() {
        TiledPreviewService service = new TiledPreviewService();
        service.showTintedCoverage(new TiledMapLayerData(), "main", 4, 5, 11, (byte) 0, 0.05f, 0.92f, 1f, 0.5f);

        service.clear();

        Assert.assertFalse(service.isCoverageVisible());
        Assert.assertFalse(service.isGhostVisible());
        Assert.assertFalse(service.isTintVisible());
    }
}
