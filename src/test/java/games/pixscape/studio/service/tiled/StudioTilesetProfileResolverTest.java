package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetRenderSize;
import games.pixscape.studio.asset.*;
import org.junit.Test;

import static org.junit.Assert.*;

public class StudioTilesetProfileResolverTest {

    @Test
    public void resolveMapsTileAssetToRuntimeTilesetProfile() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = tileset(db, "tiles/terrain");
        tileset.tileWidth = 256;
        tileset.tileHeight = 512;
        tileset.referenceCellWidth = 256;
        tileset.referenceCellHeight = 128;
        tileset.projection = SceneMetaRuntime.TiledProjection.ISO;
        tileset.anchor = TilesetAnchor.BOTTOM_CENTER;
        tileset.offsetX = 12;
        tileset.offsetY = -8;
        tileset.renderSize = TilesetRenderSize.NATIVE;

        TileAssetMeta tile = tile(db, "tiles/terrain/0", tileset.id());

        StudioTilesetProfileResolver resolver = new StudioTilesetProfileResolver(db::findById);
        RuntimeTilesetProfile profile = resolver.resolve(tile.id());

        assertEquals(tileset.id(), profile.tilesetId);
        assertEquals("tiles/terrain", profile.logicalPath);
        assertEquals(256, profile.tileWidth);
        assertEquals(512, profile.tileHeight);
        assertEquals(256, profile.referenceCellWidth);
        assertEquals(128, profile.referenceCellHeight);
        assertEquals(SceneMetaRuntime.TiledProjection.ISO, profile.projection);
        assertSame(RuntimeTilesetAnchor.BOTTOM_CENTER, profile.anchor);
        assertEquals(12, profile.offsetX);
        assertEquals(-8, profile.offsetY);
        assertSame(RuntimeTilesetRenderSize.NATIVE, profile.renderSize);
    }

    @Test
    public void resolveMissingTileOrTilesetReturnsNull() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TileAssetMeta tile = tile(db, "tiles/missing/0", 999);
        StudioTilesetProfileResolver resolver = new StudioTilesetProfileResolver(db::findById);

        assertNull(resolver.resolve(-1));
        assertNull(resolver.resolve(9999));
        assertNull(resolver.resolve(tile.id()));
    }

    @Test
    public void resolveRefreshesCachedProfileWhenTilesetMetadataChanges() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = tileset(db, "tiles/terrain");
        tileset.referenceCellWidth = 32;
        tileset.referenceCellHeight = 32;
        tileset.anchor = TilesetAnchor.TOP_CENTER;
        TileAssetMeta tile = tile(db, "tiles/terrain/0", tileset.id());

        StudioTilesetProfileResolver resolver = new StudioTilesetProfileResolver(db::findById);
        RuntimeTilesetProfile first = resolver.resolve(tile.id());
        tileset.anchor = TilesetAnchor.BOTTOM_CENTER;
        tileset.offsetY = -16;
        RuntimeTilesetProfile second = resolver.resolve(tile.id());

        assertSame(RuntimeTilesetAnchor.TOP_CENTER, first.anchor);
        assertSame(RuntimeTilesetAnchor.BOTTOM_CENTER, second.anchor);
        assertEquals(-16, second.offsetY);
    }

    @Test
    public void buildRuntimeProfilesMapsStudioTileAssetsForAtlasRendering() {
        AssetMetaDatabase db = new AssetMetaDatabase();
        TilesetAssetMeta tileset = tileset(db, "tiles/terrain");
        tileset.tileWidth = 256;
        tileset.tileHeight = 512;
        tileset.referenceCellWidth = 256;
        tileset.referenceCellHeight = 128;
        tileset.projection = SceneMetaRuntime.TiledProjection.ISO;
        tileset.anchor = TilesetAnchor.BOTTOM_CENTER;
        tileset.offsetX = 6;
        tileset.offsetY = -10;
        TileAssetMeta first = tile(db, "tiles/terrain/0", tileset.id());
        TileAssetMeta second = tile(db, "tiles/terrain/1", tileset.id());

        RuntimeTilesetProfiles profiles = StudioTilesetProfileResolver.buildRuntimeProfiles(db);
        RuntimeTilesetProfile profile = profiles.profileForTileAsset(first.id());

        assertNotNull(profile);
        assertSame(profile, profiles.profileForTileAsset(second.id()));
        assertEquals(tileset.id(), profile.tilesetId);
        assertEquals(2, profile.tileAssetIds.length);
        assertEquals(first.id(), profile.tileAssetIds[0]);
        assertEquals(second.id(), profile.tileAssetIds[1]);
        assertSame(RuntimeTilesetAnchor.BOTTOM_CENTER, profile.anchor);
        assertEquals(6, profile.offsetX);
        assertEquals(-10, profile.offsetY);
    }

    @Test
    public void reloadRuntimeProfilesReplacesStaleRegistryWithLiveAssetDatabase() {
        AssetMetaDatabase beforeImport = new AssetMetaDatabase();
        RuntimeTilesetProfiles profiles = StudioTilesetProfileResolver.buildRuntimeProfiles(beforeImport);

        AssetMetaDatabase afterImport = new AssetMetaDatabase();
        TilesetAssetMeta tileset = tileset(afterImport, "tiles/new");
        tileset.tileWidth = 48;
        tileset.tileHeight = 96;
        tileset.referenceCellWidth = 64;
        tileset.referenceCellHeight = 32;
        tileset.anchor = TilesetAnchor.BOTTOM_CENTER;
        TileAssetMeta tile = tile(afterImport, "tiles/new/0", tileset.id());

        assertNull(profiles.profileForTileAsset(tile.id()));

        StudioTilesetProfileResolver.reloadRuntimeProfiles(profiles, afterImport);

        RuntimeTilesetProfile profile = profiles.profileForTileAsset(tile.id());
        assertNotNull(profile);
        assertEquals(tileset.id(), profile.tilesetId);
        assertEquals(64, profile.referenceCellWidth);
        assertEquals(32, profile.referenceCellHeight);
        assertSame(RuntimeTilesetAnchor.BOTTOM_CENTER, profile.anchor);
    }

    private static TilesetAssetMeta tileset(AssetMetaDatabase db, String logicalPath) {
        return (TilesetAssetMeta) db.registerIfAbsent(
                AssetType.TILESET,
                logicalPath,
                null,
                AssetMeta.AssetScope.USER
        );
    }

    private static TileAssetMeta tile(AssetMetaDatabase db, String logicalPath, int tilesetId) {
        TileAssetMeta tile = (TileAssetMeta) db.registerIfAbsent(
                AssetType.TILE,
                logicalPath,
                "orig/" + logicalPath + ".png",
                AssetMeta.AssetScope.USER
        );
        tile.tilesetId = tilesetId;
        return tile;
    }
}
