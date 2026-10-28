package games.pixscape.studio.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.asset.TileAnimationProjectDefData;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.asset.TilesetAssetMeta;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SceneServiceTilesetDeletionTest {

    @Test
    public void findTileAnimationsUsingTileset_reportsAnimationsReferencingItsTiles() {
        AssetMetaDatabase assets = new AssetMetaDatabase();
        TilesetAssetMeta terrain = (TilesetAssetMeta) assets.registerIfAbsent(
                AssetType.TILESET, "tiles/terrain", "orig/tiles/terrain", AssetMeta.AssetScope.USER);
        TilesetAssetMeta props = (TilesetAssetMeta) assets.registerIfAbsent(
                AssetType.TILESET, "tiles/props", "orig/tiles/props", AssetMeta.AssetScope.USER);
        TileAssetMeta terrainTile = (TileAssetMeta) assets.registerIfAbsent(
                AssetType.TILE, "tiles/terrain/grass", "orig/tiles/terrain/grass.png", AssetMeta.AssetScope.USER);
        terrainTile.tilesetId = terrain.id();
        TileAssetMeta propsTile = (TileAssetMeta) assets.registerIfAbsent(
                AssetType.TILE, "tiles/props/crate", "orig/tiles/props/crate.png", AssetMeta.AssetScope.USER);
        propsTile.tilesetId = props.id();

        TileAnimationsMetaDatabase animations = new TileAnimationsMetaDatabase();
        animations.animations.add(animation("terrain_wave", terrainTile.id()));
        animations.animations.add(animation("props_idle", propsTile.id()));

        Array<String> names = SceneService.findTileAnimationsUsingTileset(assets, animations, terrain.id());

        assertEquals(1, names.size);
        assertEquals("terrain_wave", names.first());
    }

    private static TileAnimationProjectDefData animation(String name, int frameAssetId) {
        TileAnimationProjectDefData animation = new TileAnimationProjectDefData();
        animation.id = frameAssetId + 1000;
        animation.name = name;
        animation.frameAssetIds = new int[]{frameAssetId};
        animation.frameDurationsMs = new int[]{100};
        return animation;
    }
}
