package games.pixscape.studio.ops;

import games.pixscape.studio.asset.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AssetEntityDefaultNameTest {

    @Test
    public void spritePlacementUsesLogicalDisplayName() {
        assertEquals("tux", defaultName(new ImageAssetMeta(
                290, "images/tux", "orig/images/tux__a290.png", AssetMeta.AssetScope.USER)));
    }

    @Test
    public void animationPlacementUsesLogicalDisplayName() {
        assertEquals("hero", defaultName(new AnimationAssetMeta(
                304, "animations/hero", "orig/animations/hero__a304", AssetMeta.AssetScope.USER)));
    }

    @Test
    public void particlePlacementUsesLogicalDisplayName() {
        assertEquals("fire", defaultName(new ParticleAssetMeta(
                305, "effects/fire", "orig/effects/fire.p", AssetMeta.AssetScope.USER)));
    }

    private static String defaultName(AssetMeta meta) {
        return AssetDisplayInfo.defaultEntityName(null, meta, meta.sourceRelPath());
    }
}
