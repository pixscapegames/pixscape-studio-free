package games.pixscape.studio.service.asset;

import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;

import java.util.Map;

/** Publishes parsed definition metadata using the importer's explicit local-ID mapping. */
public final class TilePropertiesImportSupport {
    private TilePropertiesImportSupport() { }

    public static void apply(AssetMetaDatabase database, Map<Integer, Integer> assetIds,
                             Map<Integer, PropertySet> properties) {
        for (var entry : properties.entrySet()) {
            Integer assetId = assetIds.get(entry.getKey());
            if (assetId == null || !(database.findById(assetId) instanceof TileAssetMeta tile))
                throw new IllegalArgumentException("Missing tile definition asset for local ID " + entry.getKey());
            if (tile.sheetIndex != entry.getKey())
                throw new IllegalArgumentException("Local tile ID differs from imported identity: " + entry.getKey());
            entry.getValue().validate();
        }
        for (var entry : properties.entrySet()) {
            TileAssetMeta tile = (TileAssetMeta) database.findById(assetIds.get(entry.getKey()));
            tile.properties = entry.getValue().copy();
        }
    }
}
