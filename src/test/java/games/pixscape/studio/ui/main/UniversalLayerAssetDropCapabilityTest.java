package games.pixscape.studio.ui.main;

import games.pixscape.studio.ui.asset.dnd.DragPayload;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UniversalLayerAssetDropCapabilityTest {
    @Test
    public void ordinaryContentIsAllowedWithoutAMapTarget() {
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, false, "image-file"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, false, "anim-sheet"));
    }

    @Test
    public void ordinaryContentRemainsAllowedWhenMapEditingTargetIsActive() {
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, true, "image-file"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, true, "anim-sheet"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, true, "particle"));
    }

    @Test
    public void tiledAssetsDependOnExplicitMapTargetNotOwningLayerType() {
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, true, "tile-asset"));
        assertFalse(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, false, "tile-asset"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                true, true, "tiled-animation"));
    }

    @Test
    public void tiledDropUsesTheAuthoredLogicalIdForTilesAndAnimations() {
        DragPayload tile = new DragPayload();
        tile.type = "tile-asset";
        tile.assetId = 41;
        assertEquals(41, WorldCanvas.resolveTiledDropAssetId(tile));

        DragPayload animation = new DragPayload();
        animation.type = "tiled-animation";
        animation.assetId = 41;
        animation.tileAnimationId = 84;
        assertEquals(84, WorldCanvas.resolveTiledDropAssetId(animation));

        DragPayload other = new DragPayload();
        other.type = "image-file";
        other.assetId = 41;
        assertEquals(-1, WorldCanvas.resolveTiledDropAssetId(other));
    }

    @Test
    public void nonLayerTargetRejectsOrdinaryContent() {
        assertFalse(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                false, true, "atlas-region"));
    }

    @Test
    public void gameObjectRequiringSpatialIsRejectedOnlyOnNonSpatialLayer() {
        assertFalse(WorldCanvas.isGameObjectAssetAllowedForLayer(false, true));
        assertTrue(WorldCanvas.isGameObjectAssetAllowedForLayer(true, true));
        assertTrue(WorldCanvas.isGameObjectAssetAllowedForLayer(false, false));
    }
}
