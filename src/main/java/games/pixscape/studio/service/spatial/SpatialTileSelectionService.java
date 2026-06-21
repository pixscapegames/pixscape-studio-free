package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.event.EventFlow;

public final class SpatialTileSelectionService {
    public static final int NO_LAYER = -1;
    public static final String INVALID_EMPTY_CELLS = "Spatial blocks can only be created from occupied tile cells.";

    private int layerEntityId = NO_LAYER;
    private boolean dragging = false;
    private int dragStartGx = 0;
    private int dragStartGy = 0;
    private int dragEndGx = 0;
    private int dragEndGy = 0;

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
    }

    public void updateDrag(int gx, int gy) {
        if (!dragging) return;
        dragEndGx = gx;
        dragEndGy = gy;
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
        if (map == null || !hasAnySelectedCellOccupied(map)) return INVALID_EMPTY_CELLS;
        return null;
    }

    public boolean canCreateSpatialBlock(TiledMapLayerData map) {
        return validationMessage(map) == null;
    }

    public SpatialBlockData toSpatialBlockData(float defaultHeight) {
        return toSpatialBlockData(null, 0f, defaultHeight);
    }

    public SpatialBlockData toSpatialBlockData(TiledMapLayerData map, float defaultHeight) {
        return toSpatialBlockData(map, 0f, defaultHeight);
    }

    public SpatialBlockData toSpatialBlockData(TiledMapLayerData map, float defaultAltitude, float defaultHeight) {
        if (!hasSelection()) return null;
        if (map == null || !hasAnySelectedCellOccupied(map)) return null;

        SpatialBlockData block = previewSpatialBlockData(defaultAltitude, defaultHeight);
        buildLinkedTileRefsFromRect(block, map, getMinGx(), getMinGy(), getMaxGx(), getMaxGy());
        alignFootprintToLowestLinkedTileRefs(block, map);
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
                if (!map.isInside(gx, gy)) continue;
                int tileAssetId = map.getTile(gx, gy);
                if (tileAssetId <= 0) continue;
                block.addLinkedTileRef(gx, gy, tileAssetId);
            }
        }
    }

    private static void alignFootprintToLowestLinkedTileRefs(SpatialBlockData block, TiledMapLayerData map) {
        if (block == null || block.linkedTileRefs == null || block.linkedTileRefs.size == 0) return;

        float lowestY = -Float.MAX_VALUE;
        for (int i = 0, n = block.linkedTileRefs.size; i < n; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            if (ref == null) continue;
            lowestY = Math.max(lowestY, map.tileToWorldY(ref.gx, ref.gy));
        }

        if (lowestY == -Float.MAX_VALUE) return;

        int minGx = Integer.MAX_VALUE;
        int minGy = Integer.MAX_VALUE;
        int maxGx = Integer.MIN_VALUE;
        int maxGy = Integer.MIN_VALUE;
        for (int i = 0, n = block.linkedTileRefs.size; i < n; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            if (ref == null || Float.compare(map.tileToWorldY(ref.gx, ref.gy), lowestY) != 0) continue;
            minGx = Math.min(minGx, ref.gx);
            minGy = Math.min(minGy, ref.gy);
            maxGx = Math.max(maxGx, ref.gx);
            maxGy = Math.max(maxGy, ref.gy);
        }

        if (minGx == Integer.MAX_VALUE) return;

        block.x = minGx;
        block.y = minGy;
        block.width = maxGx - minGx + 1f;
        block.depth = maxGy - minGy + 1f;
    }

    private SpatialBlockData previewSpatialBlockData(float defaultAltitude, float defaultHeight) {
        SpatialBlockData block = new SpatialBlockData();
        block.x = getMinGx();
        block.y = getMinGy();
        block.width = getMaxGxExclusive() - getMinGx();
        block.depth = getMaxGyExclusive() - getMinGy();
        block.altitude = defaultAltitude;
        block.height = defaultHeight > 0f ? defaultHeight : SpatialBlockData.DEFAULT_HEIGHT;
        block.orientation = SpatialBlockOrientation.TILE_CELL;
        block.actorOccluder = true;
        block.physicsCollision = false;
        block.lightOccluder = false;
        block.shadowCaster = false;
        block.particleOccluder = false;
        return block;
    }

    private boolean hasAnySelectedCellOccupied(TiledMapLayerData map) {
        if (map == null) return false;
        for (int gy = getMinGy(); gy < getMaxGyExclusive(); gy++) {
            for (int gx = getMinGx(); gx < getMaxGxExclusive(); gx++) {
                if (map.isInside(gx, gy) && map.getTile(gx, gy) > 0) return true;
            }
        }
        return false;
    }

}
