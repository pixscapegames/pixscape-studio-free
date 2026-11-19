package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.tiled.TiledProjection;

import java.util.List;

public record TmxScenePlan(String proposedSceneName,
                           String sourceTmxPath,
                           String orientation,
                           TiledProjection tiledProjection,
                           int mapWidthCells,
                           int mapHeightCells,
                           int tileWidth,
                           int tileHeight,
                           long requiredTiledCells,
                           int tileLayerCount,
                           long nonEmptyTileCount,
                           PropertySet properties,
                           List<TmxObjectPropertyReference> objectPropertyReferences) {
    public TmxScenePlan {
        properties = properties.copy();
        objectPropertyReferences = List.copyOf(objectPropertyReferences);
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }
}
