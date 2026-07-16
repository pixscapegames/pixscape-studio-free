package games.pixscape.studio.service.tiled;

/** Actionable authoring rejection for a complete tiled mutation. */
public final class TiledSpatialMutationRejection {
    public enum Kind { LINKED_ANCHOR, SPATIAL_POST_STATE }

    private final Kind kind;
    private final int affectedCellCount;
    private final int affectedWallCount;
    private final int firstBlockId;
    private final int firstStructureId;
    private final String detail;
    private final RuntimeException cause;

    TiledSpatialMutationRejection(Kind kind, int affectedCellCount, int affectedWallCount,
                                  int firstBlockId, int firstStructureId,
                                  String detail, RuntimeException cause) {
        this.kind = kind;
        this.affectedCellCount = affectedCellCount;
        this.affectedWallCount = affectedWallCount;
        this.firstBlockId = firstBlockId;
        this.firstStructureId = firstStructureId;
        this.detail = detail;
        this.cause = cause;
    }

    public Kind kind() { return kind; }
    public int affectedCellCount() { return affectedCellCount; }
    public int affectedWallCount() { return affectedWallCount; }
    public int firstBlockId() { return firstBlockId; }
    public int firstStructureId() { return firstStructureId; }
    public String detail() { return detail; }
    public RuntimeException cause() { return cause; }

    public String userMessage() {
        if (kind == Kind.LINKED_ANCHOR) {
            return "Cannot erase " + affectedCellCount + " tile" + (affectedCellCount == 1 ? "" : "s")
                    + " because " + (affectedCellCount == 1 ? "it is" : "they are") + " used by "
                    + affectedWallCount + " Spatial wall" + (affectedWallCount == 1 ? "" : "s") + ".\n\n"
                    + "First affected block: " + firstBlockId + ", structure: " + firstStructureId + ".\n\n"
                    + "Edit or remove the affected Spatial walls first.";
        }
        return "The tiled change would create an invalid Spatial post-state.\n\n" + detail;
    }
}
