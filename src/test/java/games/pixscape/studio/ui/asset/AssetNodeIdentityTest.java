package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class AssetNodeIdentityTest {

    @Test
    public void metadataBackedNodesExposeAssetIdentityForEveryRelevantKind() {
        assertMetadataNode(AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES,
                new ImageAssetMeta(10, "images/ship", "orig/images/ship.png", AssetMeta.AssetScope.USER));
        assertMetadataNode(AssetNode.Kind.ANIMATION, AssetNode.Root.ANIMATIONS,
                new AnimationAssetMeta(11, "animations/walk", "orig/animations/walk", AssetMeta.AssetScope.USER));
        assertMetadataNode(AssetNode.Kind.PARTICLE, AssetNode.Root.PARTICLES,
                new ParticleAssetMeta(12, "effects/smoke", "orig/effects/smoke.p", AssetMeta.AssetScope.USER));
        assertMetadataNode(AssetNode.Kind.IMAGE, AssetNode.Root.TILES,
                new TileAssetMeta(13, "tiles/grass", "orig/tiles/grass.png", AssetMeta.AssetScope.USER));
    }

    @Test
    public void syntheticIdentitiesAreNotAssignedAsAssetIds() {
        AssetNode gameObject = synthetic(AssetNode.Kind.GAME_OBJECT, AssetNode.Root.GAME_OBJECTS, "gameObject");
        AssetNode tiledAnimation = synthetic(AssetNode.Kind.TILED_ANIMATION, AssetNode.Root.TILES, "walk");
        tiledAnimation.tileAnimationId = 71;
        AssetNode frame = synthetic(AssetNode.Kind.TILED_ANIMATION_FRAME, AssetNode.Root.TILES, "frame");
        frame.tileAnimationId = 71;
        AssetNode folder = synthetic(AssetNode.Kind.FOLDER, AssetNode.Root.IMAGES, "folder");
        AssetNode virtualRoot = synthetic(AssetNode.Kind.TILED_ANIMATIONS_FOLDER, AssetNode.Root.TILES, "Animations");

        assertEquals(-1, gameObject.assetId);
        assertEquals(-1, tiledAnimation.assetId);
        assertEquals(-1, frame.assetId);
        assertEquals(-1, folder.assetId);
        assertEquals(-1, virtualRoot.assetId);
        assertEquals(71, tiledAnimation.tileAnimationId);
        assertEquals(71, frame.tileAnimationId);
    }

    @Test
    public void metadataBackedTiledAnimationFrameUsesFrameAssetIdNotAnimationId() {
        AssetNode frame = synthetic(AssetNode.Kind.TILED_ANIMATION_FRAME, AssetNode.Root.TILES, "frame")
                .applyAssetMeta(new TileAssetMeta(
                        44,
                        "tiles/water",
                        "orig/tiles/water__a44.png",
                        AssetMeta.AssetScope.USER
                ));
        frame.tileAnimationId = 91;

        assertEquals(44, frame.assetId);
        assertEquals(91, frame.tileAnimationId);
    }

    @Test
    public void metadataTooltipContainsOnlyNameAndId() {
        AssetNode node = synthetic(AssetNode.Kind.IMAGE, AssetNode.Root.IMAGES, "physical.png")
                .applyAssetMeta(new ImageAssetMeta(
                        290,
                        "images/tux",
                        "orig/images/tux__a290.png",
                        AssetMeta.AssetScope.USER
                ));

        assertEquals(
                "Name: tux\nID: 290",
                node.tooltipText()
        );
        assertFalse(node.tooltipText().contains("Source"));
        assertFalse(node.tooltipText().contains("Type"));
    }

    @Test
    public void syntheticNodeKeepsExistingTooltip() {
        AssetNode node = synthetic(AssetNode.Kind.GAME_OBJECT, AssetNode.Root.GAME_OBJECTS, "Player gameObject");

        assertEquals("Player gameObject", node.tooltipText());
    }

    @Test
    public void runtimeAvailabilityGameObjectTooltipMatchesProjectNameInSubfolder() throws Exception {
        FileHandle file = new FileHandle("project/gameobjects/ship.gameobject");
        AssetNode projectNode = new AssetNode(AssetNode.Kind.GAME_OBJECT,
                AssetNode.Root.GAME_OBJECTS, file.path(),
                StudioFs.removeExtension(file.name()), null);

        Class<?> itemType = Class.forName(RuntimeAvailabilityThumbsView.class.getName()
                + "$RuntimeAvailabilityItem");
        Method gameObject = itemType.getDeclaredMethod("gameObject",
                String.class, FileHandle.class, FileHandle.class);
        gameObject.setAccessible(true);
        Object item = gameObject.invoke(null, "gameobjects/ship.gameobject", file, null);
        Method tooltipText = itemType.getDeclaredMethod("tooltipText");
        tooltipText.setAccessible(true);

        assertEquals("ship", projectNode.tooltipText());
        assertEquals(projectNode.tooltipText(), tooltipText.invoke(item));
        Field id = itemType.getDeclaredField("gameObjectId");
        id.setAccessible(true);
        assertEquals("gameobjects/ship.gameobject", id.get(item));

        Method particle = itemType.getDeclaredMethod("particle", String.class, String.class);
        particle.setAccessible(true);
        assertEquals("smoke", tooltipText.invoke(
                particle.invoke(null, "effects/smoke.p", "smoke")));
    }

    private static void assertMetadataNode(AssetNode.Kind kind, AssetNode.Root root, AssetMeta meta) {
        AssetNode node = synthetic(kind, root, "physical-name").applyAssetMeta(meta);
        AssetDisplayInfo expected = AssetDisplayInfo.from(meta);

        assertEquals(expected.displayName(), node.name);
        assertEquals(expected.assetId(), node.assetId);
        assertEquals(expected.sourcePath(), node.assetInfo.sourcePath());
    }

    private static AssetNode synthetic(AssetNode.Kind kind, AssetNode.Root root, String name) {
        return new AssetNode(kind, root, "path", name, null);
    }
}
