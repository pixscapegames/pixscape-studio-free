package games.pixscape.studio.service.spatial;

import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;

public final class SpatialBlockSelectionService {
    public static final int NO_MAP = -1;
    public static final int NO_BLOCK = -1;

    private int editingMapEntityId = NO_MAP;
    private int selectedBlockId = NO_BLOCK;
    private int hoveredBlockId = NO_BLOCK;
    private SpatialBlockInteractiveEditSupport.ResizeHandle hoveredResizeHandle;
    private boolean hoveredHeightHandle;
    private SpatialBlockPlacementTarget placementTarget = SpatialBlockPlacementTarget.invalid();
    private boolean editPreviewActive;
    private boolean editPreviewValid;
    private final SpatialWallEditSession wallEditSession = new SpatialWallEditSession();

    private final int eventTag = EventFlow.tag(this);
    private final StudioEditingModeService studioEditingModeService;

    public SpatialBlockSelectionService() {
        this(null);
    }

    public SpatialBlockSelectionService(StudioEditingModeService studioEditingModeService) {
        this.studioEditingModeService = studioEditingModeService;
        EventFlow.i().subscribe(EventFlow.TiledMapEditingTargetChanged.class, evt -> {
            if (evt.mapEntityId() != editingMapEntityId) {
                clear();
            }
        });
    }

    public int getEditingMapEntityId() {
        return editingMapEntityId;
    }

    public int getSelectedBlockId() {
        return selectedBlockId;
    }

    public int getHoveredBlockId() {
        return hoveredBlockId;
    }

    public SpatialBlockInteractiveEditSupport.ResizeHandle getHoveredResizeHandle() {
        return hoveredResizeHandle;
    }

    public boolean isHoveredHeightHandle() {
        return hoveredHeightHandle;
    }

    public SpatialBlockPlacementTarget getPlacementTarget() {
        return placementTarget;
    }

    public boolean isEditingActive() {
        return editingMapEntityId >= 0;
    }

    public boolean hasSelectedBlock() {
        return selectedBlockId > 0;
    }

    public boolean isEditPreviewActive() { return editPreviewActive; }
    public boolean isEditPreviewValid() { return editPreviewValid; }
    public SpatialWallEditSession wallEditSession() { return wallEditSession; }

    public void setEditPreview(boolean valid) {
        editPreviewActive = true;
        editPreviewValid = valid;
    }

    public void clearEditPreview() {
        editPreviewActive = false;
        editPreviewValid = false;
        wallEditSession.cancel();
    }

    public void enterMap(int mapEntityId) {
        if (editingMapEntityId == mapEntityId && selectedBlockId == NO_BLOCK) return;
        editingMapEntityId = mapEntityId;
        setSpatialModeActive(mapEntityId >= 0);
        selectedBlockId = NO_BLOCK;
        hoveredBlockId = NO_BLOCK;
        clearHoveredHandle();
        placementTarget = SpatialBlockPlacementTarget.invalid();
        wallEditSession.cancel();
        clearEditPreview();
        publishSelectionChanged();
    }

    public void selectBlock(int mapEntityId, int blockId) {
        if (mapEntityId < 0 || blockId <= 0) {
            clearSelectionOnly();
            return;
        }
        editingMapEntityId = mapEntityId;
        setSpatialModeActive(true);
        selectedBlockId = blockId;
        hoveredBlockId = blockId;
        clearHoveredHandle();
        placementTarget = SpatialBlockPlacementTarget.invalid();
        wallEditSession.cancel();
        clearEditPreview();
        publishSelectionChanged();
    }

    public void setPlacementTarget(SpatialBlockPlacementTarget target) {
        placementTarget = target != null ? target : SpatialBlockPlacementTarget.invalid();
    }

    public void clearPlacementTarget() {
        placementTarget = SpatialBlockPlacementTarget.invalid();
        wallEditSession.cancel();
        clearEditPreview();
    }

    public void setHoveredBlock(int blockId) {
        hoveredBlockId = blockId > 0 ? blockId : NO_BLOCK;
    }

    public void setHoveredHandle(SpatialBlockInteractiveEditSupport.ResizeHandle handle,
                                 boolean heightHandle) {
        hoveredResizeHandle = handle;
        hoveredHeightHandle = heightHandle;
    }

    public void clearHoveredHandle() {
        hoveredResizeHandle = null;
        hoveredHeightHandle = false;
    }

    public void clearHover() {
        hoveredBlockId = NO_BLOCK;
        clearHoveredHandle();
    }

    public void clearSelectionOnly() {
        if (selectedBlockId == NO_BLOCK && hoveredBlockId == NO_BLOCK) return;
        selectedBlockId = NO_BLOCK;
        hoveredBlockId = NO_BLOCK;
        clearHoveredHandle();
        placementTarget = SpatialBlockPlacementTarget.invalid();
        clearEditPreview();
        publishSelectionChanged();
    }

    public void clear() {
        if (editingMapEntityId == NO_MAP && selectedBlockId == NO_BLOCK && hoveredBlockId == NO_BLOCK) return;
        editingMapEntityId = NO_MAP;
        setSpatialModeActive(false);
        selectedBlockId = NO_BLOCK;
        hoveredBlockId = NO_BLOCK;
        clearHoveredHandle();
        placementTarget = SpatialBlockPlacementTarget.invalid();
        clearEditPreview();
        publishSelectionChanged();
    }

    private void setSpatialModeActive(boolean active) {
        if (studioEditingModeService != null) {
            studioEditingModeService.setModeActive(StudioEditingMode.SPATIAL, active, eventTag);
        }
    }

    private void publishSelectionChanged() {
        EventFlow.i().publish(new EventFlow.SpatialBlockSelectionChanged(
                editingMapEntityId,
                selectedBlockId,
                eventTag
        ));
    }
}
