package games.pixscape.studio.system;

import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.asset.ImageAssetMeta;
import org.junit.Test;

import java.util.function.IntFunction;

import static org.junit.Assert.*;

public class TiledFallbackSystemAssetMetaLookupTest {

    @Test
    public void canBindLiveLookupAfterBootstrapCreation() {
        final int sharedId = 101;

        ImageAssetMeta bootstrapMeta = new ImageAssetMeta(
                sharedId,
                "bootstrap",
                "images/bootstrap.png",
                AssetMeta.AssetScope.USER
        );
        IntFunction<AssetMeta> bootstrapLookup = id -> id == sharedId ? bootstrapMeta : null;

        TiledFallbackSystem system = new TiledFallbackSystem(null, null, bootstrapLookup, null);
        assertSame(bootstrapMeta, system.resolveAssetMeta(sharedId));

        ImageAssetMeta liveMeta = new ImageAssetMeta(
                sharedId,
                "live",
                "images/live.png",
                AssetMeta.AssetScope.USER
        );
        IntFunction<AssetMeta> liveLookup = id -> id == sharedId ? liveMeta : null;

        system.setAssetMetaLookup(liveLookup);

        assertSame(liveMeta, system.resolveAssetMeta(sharedId));
    }

    @Test
    public void resolveAssetMetaFollowsLatestLookupAfterBinding() {
        AssetMetaDatabase staleDb = new AssetMetaDatabase();
        staleDb.registerIfAbsent(
                AssetType.IMAGE, "stale", "images/stale.png", AssetMeta.AssetScope.USER
        );

        AssetMetaDatabase currentDb = new AssetMetaDatabase();
        AssetMeta currentMeta = currentDb.registerIfAbsent(
                AssetType.IMAGE, "current", "images/current.png", AssetMeta.AssetScope.USER
        );

        TiledFallbackSystem system = new TiledFallbackSystem(null, null, staleDb::findById, null);
        system.setAssetMetaLookup(currentDb::findById);

        assertSame(currentMeta, system.resolveAssetMeta(currentMeta.id));
    }

    @Test
    public void importedAssetBecomesResolvableImmediatelyWithLiveLookup() {
        AssetMetaDatabase liveDb = new AssetMetaDatabase();
        TiledFallbackSystem system = new TiledFallbackSystem(null, null, id -> null, null);
        system.setAssetMetaLookup(liveDb::findById);

        AssetMeta importedMeta = liveDb.registerIfAbsent(
                AssetType.IMAGE, "imported", "images/imported.png", AssetMeta.AssetScope.USER
        );

        assertSame(importedMeta, system.resolveAssetMeta(importedMeta.id));
    }

    @Test
    public void oldBootstrapSnapshotDoesNotStayAuthoritativeAfterBind() {
        AssetMetaDatabase bootstrapDb = new AssetMetaDatabase();
        ImageAssetMeta bootstrapMeta = (ImageAssetMeta) bootstrapDb.registerIfAbsent(
                AssetType.IMAGE,
                "tile",
                "images/bootstrap-tile.png",
                AssetMeta.AssetScope.USER
        );
        int assetId = bootstrapMeta.id;

        TiledFallbackSystem system = new TiledFallbackSystem(null, null, bootstrapDb::findById, null);
        assertEquals("images/bootstrap-tile.png", system.resolveAssetMeta(assetId).sourceRelPath);

        IntFunction<AssetMeta> liveLookup = id -> {
            if (id != assetId) return null;
            return new ImageAssetMeta(
                    assetId,
                    "tile",
                    "images/live-tile.png",
                    AssetMeta.AssetScope.USER
            );
        };

        system.setAssetMetaLookup(liveLookup);

        AssetMeta resolved = system.resolveAssetMeta(assetId);
        assertNotNull(resolved);
        assertEquals("images/live-tile.png", resolved.sourceRelPath);
    }
}
