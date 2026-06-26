package games.pixscape.studio.importer.tmx;

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
                               List<TmxTileCellPlan> cells) implements TmxLayerPlan {

    public TmxTileLayerPlan {
        cells = List.copyOf(cells);
    }
}
