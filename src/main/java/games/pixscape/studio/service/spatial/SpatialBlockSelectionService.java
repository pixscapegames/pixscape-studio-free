package games.pixscape.studio.service.spatial;

import games.pixscape.studio.event.EventFlow;

public final class SpatialBlockSelectionService {
    public static final int NO_LAYER = -1;
    public static final int NO_BLOCK = -1;

    private int editingLayerEntityId = NO_LAYER;
    private int selectedBlockId = NO_BLOCK;
    private int hoveredBlockId = NO_BLOCK;
    private SpatialBlockPlacementTarget placementTarget = SpatialBlockPlacementTarget.invalid();

    private final int eventTag = EventFlow.tag(this);

    public SpatialBlockSelectionService() {
        EventFlow.i().subscribe(EventFlow.CurrentLayerChanged.class, evt -> {
            if (evt.layerEntityId() != editingLayerEntityId) {
                clear();
            }
        });
    }

    public int getEditingLayerEntityId() {
        return editingLayerEntityId;
    }

    public int getSelectedBlockId() {
        return selectedBlockId;
    }

    public int getHoveredBlockId() {
        return hoveredBlockId;
    }

    public SpatialBlockPlacementTarget getPlacementTarget() {
        return placementTarget;
    }

    public boolean isEditingActive() {
        return editingLayerEntityId >= 0;
    }

    public boolean hasSelectedBlock() {
        return selectedBlockId > 0;
    }

    public void enterLayer(int layerEntityId) {
        if (editingLayerEntityId == layerEntityId && selectedBlockId == NO_BLOCK) return;
        editingLayerEntityId = layerEntityId;
        selectedBlockId = NO_BLOCK;
        hoveredBlockId = NO_BLOCK;
        placementTarget = SpatialBlockPlacementTarget.invalid();
        publishSelectionChanged();
    }

    public void selectBlock(int layerEntityId, int blockId) {
        if (layerEntityId < 0 || blockId <= 0) {
            clearSelectionOnly();
            return;
        }
        editingLayerEntityId = layerEntityId;
        selectedBlockId = blockId;
        hoveredBlockId = blockId;
        placementTarget = SpatialBlockPlacementTarget.invalid();
        publishSelectionChanged();
    }

    public void setPlacementTarget(SpatialBlockPlacementTarget target) {
        placementTarget = target != null ? target : SpatialBlockPlacementTarget.invalid();
    }

    public void clearPlacementTarget() {
        placementTarget = SpatialBlockPlacementTarget.invalid();
    }

    public void setHoveredBlock(int blockId) {
        hoveredBlockId = blockId > 0 ? blockId : NO_BLOCK;
    }

    public void clearHover() {
        hoveredBlockId = NO_BLOCK;
    }

    public void clearSelectionOnly() {
        if (selectedBlockId == NO_BLOCK && hoveredBlockId == NO_BLOCK) return;
        selectedBlockId = NO_BLOCK;
        hoveredBlockId = NO_BLOCK;
        placementTarget = SpatialBlockPlacementTarget.invalid();
        publishSelectionChanged();
    }

    public void clear() {
        if (editingLayerEntityId == NO_LAYER && selectedBlockId == NO_BLOCK && hoveredBlockId == NO_BLOCK) return;
        editingLayerEntityId = NO_LAYER;
        selectedBlockId = NO_BLOCK;
        hoveredBlockId = NO_BLOCK;
        placementTarget = SpatialBlockPlacementTarget.invalid();
        publishSelectionChanged();
    }

    private void publishSelectionChanged() {
        EventFlow.i().publish(new EventFlow.SpatialBlockSelectionChanged(
                editingLayerEntityId,
                selectedBlockId,
                eventTag
        ));
    }
}
