package games.pixscape.studio.importer.tmx;

import java.util.List;

public record TmxSceneImportResult(TmxSceneImportStatus status,
                                   TmxImportPlanResult planResult,
                                   String sceneName,
                                   String sceneFileName,
                                   String sceneTag,
                                   int importedTilesetCount,
                                   int importedTileCount,
                                   int importedLayerCount,
                                   long importedCellCount,
                                   List<TmxDiagnostic> diagnostics,
                                   Throwable failure,
                                   boolean rollbackAttempted,
                                   boolean rollbackSucceeded,
                                   TmxSceneImportRollback rollback) {

    public TmxSceneImportResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean imported() {
        return status == TmxSceneImportStatus.IMPORTED;
    }

    static TmxSceneImportResult rejected(TmxSceneImportStatus status,
                                         TmxImportPlanResult planResult,
                                         TmxDiagnostic diagnostic) {
        return new TmxSceneImportResult(
                status,
                planResult,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                diagnostic != null ? List.of(diagnostic) : List.of(),
                null,
                false,
                false,
                null
        );
    }
}
