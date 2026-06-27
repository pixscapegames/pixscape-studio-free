package games.pixscape.studio.importer.tmx;

public enum TmxSceneImportStatus {
    IMPORTED,
    PREFLIGHT_FAILED,
    TILED_BUDGET_EXCEEDED,
    UNSUPPORTED_TILESET_SPACING_MARGIN,
    FAILED_ROLLED_BACK,
    FAILED_ROLLBACK_INCOMPLETE
}
