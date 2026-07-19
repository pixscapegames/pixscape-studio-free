package games.pixscape.studio.importer.tmx;

import java.util.List;

public record TmxImportPlan(TmxScenePlan scene,
                            List<TmxTilesetPlan> tilesets,
                            List<TmxLayerPlan> layers) {

    public TmxImportPlan {
        tilesets = List.copyOf(tilesets);
        layers = List.copyOf(layers);
    }
}
