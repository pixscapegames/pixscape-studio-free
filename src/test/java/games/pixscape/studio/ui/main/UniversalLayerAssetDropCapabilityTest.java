package games.pixscape.studio.ui.main;

import games.pixscape.runtime.component.LayerComponent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UniversalLayerAssetDropCapabilityTest {
    @Test
    public void ordinaryContentIsAllowedWithoutAMapTarget() {
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_CLASSIC, false, "image-file"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_CLASSIC, false, "anim-sheet"));
    }

    @Test
    public void ordinaryContentRemainsAllowedWhenMapEditingTargetIsActive() {
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_CLASSIC, true, "image-file"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_CLASSIC, true, "anim-sheet"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_CLASSIC, true, "particle"));
    }

    @Test
    public void tiledAssetsDependOnExplicitMapTargetNotOwningLayerType() {
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_CLASSIC, true, "tile-asset"));
        assertFalse(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_CLASSIC, false, "tile-asset"));
        assertTrue(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_TILED, true, "tiled-animation"));
    }

    @Test
    public void legacyHostStillRejectsOrdinaryContent() {
        assertFalse(WorldCanvas.isAssetPayloadAllowedForEditingContext(
                LayerComponent.TYPE_TILED, true, "atlas-region"));
    }
}
