package games.pixscape.studio.ui.main;

import org.junit.Test;

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
    public void nonLayerTargetRejectsOrdinaryContent() {
        assertFalse(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                false, true, "atlas-region"));
    }
}
