package games.pixscape.studio.system;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.TileProfilePlacement;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.service.tiled.StudioTilesetProfileResolver;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudioTiledProfilePlacementTest {

    @Test
    public void topCenterProfileMatchesDefaultAnchorPlacement() {
        Fixture fixture = fixture(TilesetAnchor.TOP_CENTER, 0, 0);
        float[] profiled = buildQuad(fixture.map, fixture.profile, (byte) 0);
        float[] topCenterDefault = buildTopCenterDefaultQuad(fixture.map);

        assertQuad(profiled, topCenterDefault);
    }

    @Test
    public void bottomCenterProfileMovesTallTileFromTopCenterDefault() {
        Fixture fixture = fixture(TilesetAnchor.BOTTOM_CENTER, 0, 0);
        float[] quad = buildQuad(fixture.map, fixture.profile, (byte) 0);
        float[] topCenterDefault = buildTopCenterDefaultQuad(fixture.map);

        assertEquals(0f, quad[0], 0.001f);
        assertEquals(0f, quad[1], 0.001f);
        assertEquals(-384f, topCenterDefault[1], 0.001f);
    }

    @Test
    public void profileOffsetMovesPlacement() {
        Fixture fixture = fixture(TilesetAnchor.TOP_CENTER, 12, -8);
        float[] quad = buildQuad(fixture.map, fixture.profile, (byte) 0);

        assertEquals(12f, quad[0], 0.001f);
        assertEquals(-392f, quad[1], 0.001f);
    }

    @Test
    public void transformFlagsStillApplyAfterProfilePlacement() {
        Fixture fixture = fixture(TilesetAnchor.TOP_CENTER, 0, 0);
        float[] normal = buildQuad(fixture.map, fixture.profile, TileTransformFlags.NONE);
        float[] flipped = buildQuad(fixture.map, fixture.profile, TileTransformFlags.FLIP_H);

        assertEquals(normal[6], flipped[0], 0.001f);
        assertEquals(normal[0], flipped[6], 0.001f);
        assertEquals(normal[1], flipped[1], 0.001f);
    }

    @Test
    public void ghostResolverAndStudioAtlasRegistryProduceSameQuad() {
        Fixture fixture = fixture(TilesetAnchor.BOTTOM_CENTER, 9, -7);
        RuntimeTilesetProfiles atlasProfiles = StudioTilesetProfileResolver.buildRuntimeProfiles(fixture.db);
        RuntimeTilesetProfile atlasProfile = atlasProfiles.profileForTileAsset(fixture.tile.id());

        assertQuad(
                buildQuad(fixture.map, fixture.profile, TileTransformFlags.NONE),
                buildQuad(fixture.map, atlasProfile, TileTransformFlags.NONE)
        );
    }

    private static float[] buildQuad(TiledMapLayerData map, RuntimeTilesetProfile profile, byte flags) {
        float[] quad = new float[8];
        TileQuadTransforms.buildSpriteQuad(map, 0, 0, 256, 512, profile, flags, quad);
        return quad;
    }

    private static float[] buildTopCenterDefaultQuad(TiledMapLayerData map) {
        float[] quad = new float[8];
        TileProfilePlacement.buildTopCenterDefaultSpriteQuad(
                map.tileToWorldX(0, 0),
                map.tileToWorldY(0, 0),
                map.tileWidth,
                map.tileHeight,
                256,
                512,
                quad
        );
        return quad;
    }

    private static Fixture fixture(TilesetAnchor anchor, int offsetX, int offsetY) {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                "tiles/iso",
                null,
                AssetMeta.AssetScope.USER
        );
        tileset.tileWidth = 256;
        tileset.tileHeight = 512;
        tileset.referenceCellWidth = 256;
        tileset.referenceCellHeight = 128;
        tileset.projection = SceneMetaRuntime.TiledProjection.ISO;
        tileset.anchor = anchor;
        tileset.offsetX = offsetX;
        tileset.offsetY = offsetY;

        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                "tiles/iso/0",
                "orig/tiles/iso/0.png",
                AssetMeta.AssetScope.USER
        );
        tile.tilesetId = tileset.id();

        StudioTilesetProfileResolver resolver = new StudioTilesetProfileResolver(db::findById);
        return new Fixture(
                db,
                tile,
                new TiledMapLayerData(1, 1, 256, 128, 4, SceneMetaRuntime.TiledProjection.ISO),
                resolver.resolve(tile.id())
        );
    }

    private static void assertQuad(float[] actual, float[] expected) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 0.001f);
        }
    }

    private record Fixture(AssetMetaDatabase db,
                           TileAssetMeta tile,
                           TiledMapLayerData map,
                           RuntimeTilesetProfile profile) {
    }
}
