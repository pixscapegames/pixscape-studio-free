package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxTileLayerInfo(String name,
                               String originalName,
                               boolean visible,
                               float opacity,
                               float offsetX,
                               float offsetY,
                               float parallaxX,
                               float parallaxY,
                               int width,
                               int height,
                               String encoding,
                               String compression,
                               int nonEmptyTileCount,
                               boolean hasTransformFlags,
                               List<TmxTileCellInfo> cells,
                               PropertySet properties,
                               List<TmxObjectPropertyReference> objectPropertyReferences) implements TmxLayerInfo {

    public TmxTileLayerInfo {
        properties = properties.copy();
        objectPropertyReferences = List.copyOf(objectPropertyReferences);
        cells = List.copyOf(cells);
    }

    @Override
    public TmxLayerKind kind() {
        return TmxLayerKind.TILE;
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }
}
