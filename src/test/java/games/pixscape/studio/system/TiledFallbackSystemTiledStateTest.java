package games.pixscape.studio.system;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetRenderSize;
import org.junit.Assert;
import org.junit.Test;

public class TiledFallbackSystemTiledStateTest {

    @Test
    public void fallbackWritePublishesDrawReadyTiledRenderRef() {
        TiledMapRenderState tiledState = new TiledMapRenderState(2);
        TiledFallbackSystem system = new TiledFallbackSystem(tiledState, null, id -> null, null);

        TiledMapLayerData map = mapWithRegisteredRef(tiledState);
        int tiledRenderRef = map.tiledRenderRefForTile(0, 0);

        system.writeTileSlot(
                3,
                map,
                tiledRenderRef,
                0,
                0,
                16,
                16,
                profile(1),
                TileTransformFlags.NONE,
                77,
                0f,
                0f,
                1f,
                1f
        );

        Assert.assertTrue(tiledState.isRenderableRef(tiledRenderRef));
        Assert.assertEquals(77, tiledState.textureHandle[tiledRenderRef]);
        Assert.assertEquals(3, tiledState.layerIndex[tiledRenderRef]);
        Assert.assertEquals(1, tiledState.getVisibleRefCount());
        Assert.assertEquals(tiledRenderRef, tiledState.getVisibleRefs()[0]);
    }

    @Test
    public void fallbackEntryCanBeReplacedByAtlasEntryForSameRef() {
        TiledMapRenderState tiledState = new TiledMapRenderState(2);
        TiledFallbackSystem system = new TiledFallbackSystem(tiledState, null, id -> null, null);

        TiledMapLayerData map = mapWithRegisteredRef(tiledState);
        int tiledRenderRef = map.tiledRenderRefForTile(0, 0);
        RuntimeTilesetProfile profile = profile(1);

        system.writeTileSlot(0, map, tiledRenderRef, 0, 0,
                16, 16, profile, TileTransformFlags.NONE, 77, 0f, 0f, 1f, 1f);
        system.writeTileSlot(0, map, tiledRenderRef, 0, 0,
                16, 16, profile, TileTransformFlags.NONE, 501, 0.25f, 0.5f, 0.75f, 1f);

        Assert.assertTrue(tiledState.isRenderableRef(tiledRenderRef));
        Assert.assertEquals(501, tiledState.textureHandle[tiledRenderRef]);
        Assert.assertEquals(0.25f, tiledState.u1[tiledRenderRef], 0.0001f);
        Assert.assertEquals(0.5f, tiledState.v1[tiledRenderRef], 0.0001f);
        Assert.assertEquals(0.75f, tiledState.u2[tiledRenderRef], 0.0001f);
        Assert.assertEquals(1f, tiledState.v2[tiledRenderRef], 0.0001f);
    }

    private static TiledMapLayerData mapWithRegisteredRef(TiledMapRenderState tiledState) {
        TiledMapLayerData map = new TiledMapLayerData(1, 1, 16, 16, 1);
        TileChunk chunk = map.getChunk(0, 0);
        chunk.renderRefStartIndex = tiledState.registerRefs(chunk.cellCount());
        chunk.renderRefCount = chunk.cellCount();
        return map;
    }

    private static RuntimeTilesetProfile profile(int tileAssetId) {
        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.tilesetId = 1;
        profile.referenceCellWidth = 16;
        profile.referenceCellHeight = 16;
        profile.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        profile.anchor = RuntimeTilesetAnchor.TOP_CENTER;
        profile.renderSize = RuntimeTilesetRenderSize.NATIVE;
        profile.tileAssetIds = new int[]{tileAssetId};
        return profile;
    }
}
