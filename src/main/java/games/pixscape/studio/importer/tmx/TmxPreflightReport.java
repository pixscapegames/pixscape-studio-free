package games.pixscape.studio.importer.tmx;

import java.util.List;

public record TmxPreflightReport(String sourcePath,
                                 TmxMapInfo mapInfo,
                                 List<TmxTilesetInfo> tilesets,
                                 List<TmxLayerInfo> layers,
                                 List<TmxDiagnostic> diagnostics,
                                 int tileLayerCount,
                                 long requiredTiledCells,
                                 long nonEmptyTileCount) {

    public TmxPreflightReport {
        tilesets = List.copyOf(tilesets);
        layers = List.copyOf(layers);
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean hasBlockingDiagnostics() {
        return diagnostics.stream().anyMatch(d -> d.severity() == TmxDiagnosticSeverity.BLOCKING);
    }

    public boolean isImportableCandidate() {
        return !hasBlockingDiagnostics();
    }
}
