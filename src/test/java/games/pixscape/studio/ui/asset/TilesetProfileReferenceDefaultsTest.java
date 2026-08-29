package games.pixscape.studio.ui.asset;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;

import static org.junit.Assert.*;

public class TilesetProfileReferenceDefaultsTest {

    @Test
    public void referenceCellFollowsTileSizeWhileStillDefault() {
        TilesetProfileReferenceDefaults defaults = new TilesetProfileReferenceDefaults(
                32,
                24,
                32,
                24,
                TiledProjection.ORTHO
        );

        assertTrue(defaults.widthFollowsTile());
        assertTrue(defaults.heightFollowsTile());
        TilesetProfileReferenceDefaults.ReferenceSize referenceSize =
                defaults.referenceSizeAfterTileSizeChange(48, 36, 32, 24);
        assertEquals(48, referenceSize.width());
        assertEquals(36, referenceSize.height());
    }

    @Test
    public void isometricProjectionUsesHalfTileWidthHeightWhileAutoFollowing() {
        TilesetProfileReferenceDefaults defaults = new TilesetProfileReferenceDefaults(
                32,
                32,
                32,
                32,
                TiledProjection.ORTHO
        );

        TilesetProfileReferenceDefaults.ReferenceSize referenceSize =
                defaults.referenceSizeAfterProjectionChange(
                        TiledProjection.ISO,
                        256,
                        512,
                        32,
                        32
                );

        assertEquals(256, referenceSize.width());
        assertEquals(128, referenceSize.height());
    }

    @Test
    public void orthogonalProjectionRestoresTileHeightWhileAutoFollowing() {
        TilesetProfileReferenceDefaults defaults = new TilesetProfileReferenceDefaults(
                256,
                512,
                256,
                128,
                TiledProjection.ISO
        );

        TilesetProfileReferenceDefaults.ReferenceSize referenceSize =
                defaults.referenceSizeAfterProjectionChange(
                        TiledProjection.ORTHO,
                        256,
                        512,
                        256,
                        128
                );

        assertEquals(256, referenceSize.width());
        assertEquals(512, referenceSize.height());
    }

    @Test
    public void manualReferenceWidthStopsFollowingReferenceSize() {
        TilesetProfileReferenceDefaults defaults = new TilesetProfileReferenceDefaults(
                32,
                24,
                32,
                24,
                TiledProjection.ORTHO
        );

        defaults.markReferenceWidthEdited();

        assertFalse(defaults.widthFollowsTile());
        assertFalse(defaults.heightFollowsTile());
        TilesetProfileReferenceDefaults.ReferenceSize referenceSize =
                defaults.referenceSizeAfterProjectionChange(
                        TiledProjection.ISO,
                        48,
                        36,
                        40,
                        24
                );
        assertEquals(40, referenceSize.width());
        assertEquals(24, referenceSize.height());
    }

    @Test
    public void nonDefaultReferenceCellDoesNotFollowInitialTileSize() {
        TilesetProfileReferenceDefaults defaults = new TilesetProfileReferenceDefaults(
                32,
                24,
                40,
                44,
                TiledProjection.ORTHO
        );

        assertFalse(defaults.widthFollowsTile());
        assertFalse(defaults.heightFollowsTile());
        TilesetProfileReferenceDefaults.ReferenceSize referenceSize =
                defaults.referenceSizeAfterTileSizeChange(48, 36, 40, 44);
        assertEquals(40, referenceSize.width());
        assertEquals(44, referenceSize.height());
    }
}
