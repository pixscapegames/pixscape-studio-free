package games.pixscape.studio.asset;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AssetDisplayInfoTest {

    @Test
    public void imagePresentationUsesLogicalPathAndPreservesIdentityAndSource() {
        AssetDisplayInfo info = AssetDisplayInfo.from(new ImageAssetMeta(
                290,
                "images/tux",
                "orig/images/tux__a290.png",
                AssetMeta.AssetScope.USER
        ));

        assertEquals("tux", info.displayName());
        assertEquals(290, info.assetId());
        assertEquals("orig/images/tux__a290.png", info.sourcePath());
    }

    @Test
    public void animationPresentationUsesLogicalPath() {
        AssetDisplayInfo info = AssetDisplayInfo.from(new AnimationAssetMeta(
                304,
                "animations/hero",
                "orig/animations/hero__a304",
                AssetMeta.AssetScope.USER
        ));

        assertEquals("hero", info.displayName());
        assertEquals(304, info.assetId());
        assertEquals("orig/animations/hero__a304", info.sourcePath());
    }

    @Test
    public void particlePresentationUsesLogicalPathAndExposesAssetId() {
        AssetDisplayInfo info = AssetDisplayInfo.from(new ParticleAssetMeta(
                305,
                "effects/fire",
                "orig/effects/fire.p",
                AssetMeta.AssetScope.USER
        ));

        assertEquals("fire", info.displayName());
        assertEquals(305, info.assetId());
        assertEquals("orig/effects/fire.p", info.sourcePath());
    }

    @Test
    public void physicalSourceIsNeverParsedForDisplayName() {
        AssetDisplayInfo info = AssetDisplayInfo.from(new ImageAssetMeta(
                412,
                "images/player.avatar",
                "orig/images/not.the.name__a_text__a412.png",
                AssetMeta.AssetScope.USER
        ));

        assertEquals("player.avatar", info.displayName());
    }

    @Test
    public void requestedEntityNameWinsSoRebindCannotReplaceUserName() {
        AssetMeta meta = new ImageAssetMeta(
                290,
                "images/tux",
                "orig/images/tux__a290.png",
                AssetMeta.AssetScope.USER
        );

        assertEquals("Captain Tux", AssetDisplayInfo.defaultEntityName(
                "Captain Tux",
                meta,
                "tux__a290.png"
        ));
    }
}
