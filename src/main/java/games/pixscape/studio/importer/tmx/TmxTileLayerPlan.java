package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;

public record TmxTileLayerPlan(String name,
                               String originalName,
                               int sourceLayerIndex,
                               int width,
                               int height,
                               boolean visible,
                               float parallaxX,
                               float parallaxY,
                               float offsetX,
                               float offsetY,
                               float opacity,
                               long requiredCells,
                               int nonEmptyCellCount,
                               List<TmxTileCellPlan> cells,
                               PropertySet properties,
                               List<TmxObjectPropertyReference> objectPropertyReferences) implements TmxLayerPlan {

    public TmxTileLayerPlan {
        properties = properties.copy();
        objectPropertyReferences = List.copyOf(objectPropertyReferences);
        cells = List.copyOf(cells);
    }

    @Override
    public PropertySet properties() {
        return properties.copy();
    }
}
