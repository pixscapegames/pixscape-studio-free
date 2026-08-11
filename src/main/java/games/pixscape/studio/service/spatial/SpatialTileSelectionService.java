package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.event.EventFlow;

public final class SpatialTileSelectionService {
    public static final int NO_LAYER = -1;
    public static final String INVALID_EMPTY_CELLS = "Spatial blocks can only be created from occupied tile cells.";
    public static final String INVALID_INTERNAL_EMPTY_CELL = "Spatial block selections cannot contain internal empty cells.";
    public static final String INVALID_NON_RECTANGULAR = "Spatial block selections must contain one filled occupied rectangle.";

    public enum NormalizationStatus {
        VALID_FILLED_RECTANGLE,
        EMPTY,
        HAS_INTERNAL_EMPTY_CELL,
        DISCONNECTED_OR_NON_RECTANGULAR,
        OUTSIDE_MAP
    }

    /** Reusable normalized occupied range. Callers must not retain it across selection updates. */
    public static final class NormalizedSelection {
        public int minGx;
        public int maxGxExclusive;
        public int minGy;
        public int maxGyExclusive;
        public int occupiedCellCount;
        public NormalizationStatus status = NormalizationStatus.EMPTY;

        public boolean isValid() {
            return status == NormalizationStatus.VALID_FILLED_RECTANGLE;
        }
    }

    private int layerEntityId = NO_LAYER;
    private boolean dragging = false;
    private int dragStartGx = 0;
    private int dragStartGy = 0;
    private int dragEndGx = 0;
    private int dragEndGy = 0;
    private boolean pointerCellValid = false;
    private int hoverLayerEntityId = NO_LAYER;
    private int hoverGx;
    private int hoverGy;
    private float gestureDx;
    private float gestureDy;
    private final NormalizedSelection normalized = new NormalizedSelection();

    public SpatialTileSelectionService() {
        EventFlow.i().subscribe(EventFlow.CurrentLayerChanged.class, evt -> {
            if (evt.layerEntityId() != layerEntityId) {
                clear();
            }
        });
        EventFlow.i().subscribe(EventFlow.EditorModeChanged.class, evt -> {
            if (evt.mode() != EventFlow.EditorMode.TILE) {
                clear();
            }
        });
        EventFlow.i().subscribe(EventFlow.SceneMapResized.class, evt -> clear());
        EventFlow.i().subscribe(EventFlow.SpatialBlockSelectionChanged.class, evt -> {
            if (evt.layerEntityId() != layerEntityId) {
                clear();
            }
        });
    }

    public void beginDrag(int layerEntityId, int gx, int gy) {
        if (layerEntityId < 0) {
            clear();
            return;
        }

        this.layerEntityId = layerEntityId;
        this.dragging = true;
        this.dragStartGx = gx;
        this.dragStartGy = gy;
        this.dragEndGx = gx;
        this.dragEndGy = gy;
        this.pointerCellValid = true;
        this.gestureDx = 0f;
        this.gestureDy = 0f;
        clearHover();
    }

    public void updateDrag(int gx, int gy) {
        if (!dragging) return;
        dragEndGx = gx;
        dragEndGy = gy;
        pointerCellValid = true;
    }

    public void updateGesture(float dx, float dy) {
        if (!dragging) return;
        gestureDx = dx;
        gestureDy = dy;
    }

    public void updateDragOutsideMap() {
        if (!dragging) return;
        pointerCellValid = false;
        gestureDx = 0f;
        gestureDy = 0f;
        clearHover();
    }

    public void setHover(int layerEntityId, int gx, int gy) {
        hoverLayerEntityId = layerEntityId;
        hoverGx = gx;
        hoverGy = gy;
    }

    public void clearHover() { hoverLayerEntityId = NO_LAYER; }
    public boolean hasHover() { return hoverLayerEntityId >= 0; }
    public int getHoverLayerEntityId() { return hoverLayerEntityId; }
    public int getHoverGx() { return hoverGx; }
    public int getHoverGy() { return hoverGy; }

    public SpatialWallAttachments.Axis gestureAxis() {
        float ax = Math.abs(gestureDx);
        float ay = Math.abs(gestureDy);
        if (ax <= 0.0001f && ay <= 0.0001f) return null;
        return ax >= ay ? SpatialWallAttachments.Axis.HORIZONTAL : SpatialWallAttachments.Axis.VERTICAL;
    }

    public void finishDrag() {
        dragging = false;
    }

    public void clear() {
        layerEntityId = NO_LAYER;
        dragging = false;
        dragStartGx = 0;
        dragStartGy = 0;
        dragEndGx = 0;
        dragEndGy = 0;
        pointerCellValid = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean hasSelection() {
        return layerEntityId >= 0;
    }

    public int getLayerEntityId() {
        return layerEntityId;
    }

    public int getMinGx() {
        return Math.min(dragStartGx, dragEndGx);
    }

    public int getMinGy() {
        return Math.min(dragStartGy, dragEndGy);
    }

    public int getMaxGx() {
        return Math.max(dragStartGx, dragEndGx);
    }

    public int getMaxGy() {
        return Math.max(dragStartGy, dragEndGy);
    }

    public int getMaxGxExclusive() {
        return getMaxGx() + 1;
    }

    public int getMaxGyExclusive() {
        return getMaxGy() + 1;
    }

    public int getCellCount() {
        if (!hasSelection()) return 0;
        return (getMaxGxExclusive() - getMinGx()) * (getMaxGyExclusive() - getMinGy());
    }

    public boolean contains(int layerEntityId, int gx, int gy) {
        return hasSelection()
                && this.layerEntityId == layerEntityId
                && gx >= getMinGx()
                && gx < getMaxGxExclusive()
                && gy >= getMinGy()
                && gy < getMaxGyExclusive();
    }

    public String validationMessage(TiledMapLayerData map) {
        if (!hasSelection()) return "No spatial tile selection.";
        NormalizationStatus status = normalize(map).status;
        if (status == NormalizationStatus.VALID_FILLED_RECTANGLE) return null;
        if (status == NormalizationStatus.HAS_INTERNAL_EMPTY_CELL) return INVALID_INTERNAL_EMPTY_CELL;
        if (status == NormalizationStatus.DISCONNECTED_OR_NON_RECTANGULAR) return INVALID_NON_RECTANGULAR;
        return INVALID_EMPTY_CELLS;
    }

    public String validationMessage(TiledMapLayerData map,
                                    SpatialBlocksComponent existingWalls,
                                    float defaultAltitude,
                                    float defaultHeight) {
        String coverageFailure = validationMessage(map);
        if (coverageFailure != null) return coverageFailure;
        SpatialBlockData candidate = toSpatialBlockData(map, defaultAltitude, defaultHeight);
        SpatialWallThicknessInheritance.Result inherited =
                SpatialWallThicknessInheritance.apply(candidate, existingWalls, gestureAxis());
        if (!inherited.valid) return inherited.error;
        inherited.wall.id = existingWalls != null
                ? existingWalls.peekNextSpatialBlockId() : 1;
        SpatialStructureTopology.Plan plan = SpatialStructureTopology.add(existingWalls, inherited.wall, map);
        return plan.valid ? null : plan.error;
    }

    public boolean canCreateSpatialBlock(TiledMapLayerData map) {
        return validationMessage(map) == null;
    }

    public boolean canCreateSpatialBlock(TiledMapLayerData map,
                                         SpatialBlocksComponent existingWalls,
                                         float defaultAltitude,
                                         float defaultHeight) {
        return validationMessage(map, existingWalls, defaultAltitude, defaultHeight) == null;
    }

    public SpatialBlockData toSpatialBlockData(float defaultHeight) {
        return toSpatialBlockData(null, 0f, defaultHeight);
    }

    public SpatialBlockData toSpatialBlockData(TiledMapLayerData map, float defaultHeight) {
        return toSpatialBlockData(map, 0f, defaultHeight);
    }

    public SpatialBlockData toSpatialBlockData(TiledMapLayerData map, float defaultAltitude, float defaultHeight) {
        if (!hasSelection()) return null;
        NormalizedSelection range = normalize(map);
        if (!range.isValid()) return null;

        return fromOccupiedRect(map,
                range.minGx, range.minGy,
                range.maxGxExclusive - 1, range.maxGyExclusive - 1,
                defaultAltitude, defaultHeight);
    }

    /**
     * Treats the raw pointer rectangle as a search window and returns its minimal
     * filled occupied-cell rectangle. Peripheral empty cells are intentionally ignored.
     */
    public NormalizedSelection normalize(TiledMapLayerData map) {
        normalized.minGx = 0;
        normalized.maxGxExclusive = 0;
        normalized.minGy = 0;
        normalized.maxGyExclusive = 0;
        normalized.occupiedCellCount = 0;
        normalized.status = NormalizationStatus.EMPTY;

        if (!hasSelection()) return normalized;
        if (!pointerCellValid || map == null || map.mapWidth <= 0 || map.mapHeight <= 0) {
            normalized.status = NormalizationStatus.OUTSIDE_MAP;
            return normalized;
        }

        int scanMinGx = Math.max(0, getMinGx());
        int scanMaxGx = Math.min(map.mapWidth - 1, getMaxGx());
        int scanMinGy = Math.max(0, getMinGy());
        int scanMaxGy = Math.min(map.mapHeight - 1, getMaxGy());
        if (scanMinGx > scanMaxGx || scanMinGy > scanMaxGy) {
            normalized.status = NormalizationStatus.OUTSIDE_MAP;
            return normalized;
        }

        int occupiedMinGx = Integer.MAX_VALUE;
        int occupiedMaxGx = Integer.MIN_VALUE;
        int occupiedMinGy = Integer.MAX_VALUE;
        int occupiedMaxGy = Integer.MIN_VALUE;
        int occupiedCount = 0;
        for (int gy = scanMinGy; gy <= scanMaxGy; gy++) {
            for (int gx = scanMinGx; gx <= scanMaxGx; gx++) {
                if (map.getTile(gx, gy) <= 0) continue;
                occupiedCount++;
                if (gx < occupiedMinGx) occupiedMinGx = gx;
                if (gx > occupiedMaxGx) occupiedMaxGx = gx;
                if (gy < occupiedMinGy) occupiedMinGy = gy;
                if (gy > occupiedMaxGy) occupiedMaxGy = gy;
            }
        }

        normalized.occupiedCellCount = occupiedCount;
        if (occupiedCount == 0) return normalized;

        normalized.minGx = occupiedMinGx;
        normalized.maxGxExclusive = occupiedMaxGx + 1;
        normalized.minGy = occupiedMinGy;
        normalized.maxGyExclusive = occupiedMaxGy + 1;

        int expectedCount = (occupiedMaxGx - occupiedMinGx + 1)
                * (occupiedMaxGy - occupiedMinGy + 1);
        if (occupiedCount == expectedCount) {
            normalized.status = NormalizationStatus.VALID_FILLED_RECTANGLE;
            return normalized;
        }

        boolean boundaryGap = false;
        for (int gy = occupiedMinGy; gy <= occupiedMaxGy; gy++) {
            for (int gx = occupiedMinGx; gx <= occupiedMaxGx; gx++) {
                if (map.getTile(gx, gy) > 0) continue;
                if (gx == occupiedMinGx || gx == occupiedMaxGx
                        || gy == occupiedMinGy || gy == occupiedMaxGy) {
                    boundaryGap = true;
                }
            }
        }
        normalized.status = boundaryGap
                ? NormalizationStatus.DISCONNECTED_OR_NON_RECTANGULAR
                : NormalizationStatus.HAS_INTERNAL_EMPTY_CELL;
        return normalized;
    }

    public static SpatialBlockData fromOccupiedRect(TiledMapLayerData map,
                                                     int minGx, int minGy,
                                                     int maxGx, int maxGy,
                                                     float defaultAltitude,
                                                     float defaultHeight) {
        if (map == null || minGx > maxGx || minGy > maxGy) return null;
        for (int gy = minGy; gy <= maxGy; gy++) {
            for (int gx = minGx; gx <= maxGx; gx++) {
                if (!map.isInside(gx, gy) || map.getTile(gx, gy) <= 0) return null;
            }
        }
        SpatialBlockData block = new SpatialBlockData();
        block.x = minGx;
        block.y = minGy;
        block.width = maxGx - minGx + 1;
        block.depth = maxGy - minGy + 1;
        block.altitude = defaultAltitude;
        block.height = defaultHeight > 0f ? defaultHeight : SpatialBlockData.DEFAULT_HEIGHT;
        block.actorOccluder = true;
        buildLinkedTileRefsFromRect(block, map, minGx, minGy, maxGx, maxGy);
        return block;
    }

    public static void buildLinkedTileRefsFromRect(SpatialBlockData block,
                                                   TiledMapLayerData map,
                                                   int minGx,
                                                   int minGy,
                                                   int maxGx,
                                                   int maxGy) {
        if (block == null) return;

        block.beginAuthoredLinkedTileRefs();
        if (map == null) return;

        for (int gy = minGy; gy <= maxGy; gy++) {
            for (int gx = minGx; gx <= maxGx; gx++) {
                int tileAssetId = map.getTile(gx, gy);
                block.addLinkedTileRef(gx, gy, tileAssetId);
            }
        }
    }

}
