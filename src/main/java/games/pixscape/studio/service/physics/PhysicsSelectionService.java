package games.pixscape.studio.service.physics;

import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;

/** Central editor selection for physics sources and their compiled parts. */
public final class PhysicsSelectionService {
    public static final int NO_SHAPE = -1;
    public static final int NO_PART = -1;
    public static final int NO_BODY = -1;
    public static final int NO_JOINT = -1;

    private int focusedBodyEid = NO_BODY;

    private int hoveredBodyEid = NO_BODY;
    private int hoveredPhysicsShapeId = NO_SHAPE;
    private int hoveredPartIndex = NO_PART;
    private int hoveredJointEid = NO_JOINT;

    private int selectedPhysicsShapeId = NO_SHAPE;
    private int selectedPartIndex = NO_PART;
    private int selectedJointEid = NO_JOINT;
    private final StudioEditingModeService studioEditingModeService;
    private final int eventTag = EventFlow.tag(this);

    public PhysicsSelectionService() {
        this(null);
    }

    public PhysicsSelectionService(StudioEditingModeService studioEditingModeService) {
        this.studioEditingModeService = studioEditingModeService;
        EventFlow.i().subscribe(EventFlow.TiledMapEditingTargetChanged.class, evt -> {
            if (focusedBodyEid >= 0 && focusedBodyEid != evt.mapEntityId()) {
                clear();
            }
        });
    }

    public int getFocusedBodyEid() {
        return focusedBodyEid;
    }

    public int getHoveredBodyEid() {
        return hoveredBodyEid;
    }

    public int getHoveredPhysicsShapeId() {
        return hoveredPhysicsShapeId;
    }

    public int getHoveredPartIndex() {
        return hoveredPartIndex;
    }

    public int getHoveredJointEid() {
        return hoveredJointEid;
    }

    public int getSelectedPhysicsShapeId() {
        return selectedPhysicsShapeId;
    }

    public int getSelectedPartIndex() {
        return selectedPartIndex;
    }

    public int getSelectedJointEid() {
        return selectedJointEid;
    }

    public boolean hasFocusedBody() {
        return focusedBodyEid >= 0;
    }

    public boolean hasHoveredShape() {
        return hoveredPhysicsShapeId != NO_SHAPE;
    }

    public boolean hasHoveredJoint() {
        return hoveredJointEid >= 0;
    }

    public boolean hasSelectedShape() {
        return selectedPhysicsShapeId != NO_SHAPE;
    }

    public boolean hasSelectedJoint() {
        return selectedJointEid >= 0;
    }

    public boolean isFocusedBody(int bodyEid) {
        return focusedBodyEid == bodyEid && bodyEid >= 0;
    }

    public boolean isHoveredJoint(int jointEid) {
        return hoveredJointEid == jointEid && jointEid >= 0;
    }

    public boolean isSelectedJoint(int jointEid) {
        return selectedJointEid == jointEid && jointEid >= 0;
    }

    public void focusBody(int bodyEid) {
        if (focusedBodyEid == bodyEid) {
            return;
        }
        focusedBodyEid = bodyEid;
        if (studioEditingModeService != null) {
            studioEditingModeService.setModeActive(StudioEditingMode.PHYSICS, bodyEid >= 0, eventTag);
        }
        clearSelectionOnly();
    }

    public void setHoveredShape(int bodyEid, int physicsShapeId) {
        setHoveredShape(bodyEid, physicsShapeId, NO_PART);
    }

    public void setHoveredShape(int bodyEid, int physicsShapeId, int partIndex) {
        hoveredBodyEid = bodyEid;
        hoveredPhysicsShapeId = physicsShapeId;
        hoveredPartIndex = partIndex;
        hoveredJointEid = NO_JOINT;
    }

    public void setHoveredJoint(int jointEid) {
        hoveredBodyEid = NO_BODY;
        hoveredPhysicsShapeId = NO_SHAPE;
        hoveredPartIndex = NO_PART;
        hoveredJointEid = jointEid;
    }

    public void setSelectedShape(int bodyEid, int physicsShapeId) {
        setSelectedShape(bodyEid, physicsShapeId, NO_PART);
    }

    public void setSelectedShape(int bodyEid, int physicsShapeId, int partIndex) {
        focusBody(bodyEid);
        selectedPhysicsShapeId = physicsShapeId;
        selectedPartIndex = partIndex;
        selectedJointEid = NO_JOINT;
        setHoveredShape(bodyEid, physicsShapeId, partIndex);
    }

    public void setSelectedJoint(int bodyEid, int jointEid) {
        focusBody(bodyEid);
        selectedJointEid = jointEid;
        selectedPhysicsShapeId = NO_SHAPE;
        selectedPartIndex = NO_PART;
        hoveredBodyEid = NO_BODY;
        hoveredPhysicsShapeId = NO_SHAPE;
        hoveredPartIndex = NO_PART;
        hoveredJointEid = jointEid;
    }

    public void clearHover() {
        hoveredBodyEid = NO_BODY;
        hoveredPhysicsShapeId = NO_SHAPE;
        hoveredPartIndex = NO_PART;
        hoveredJointEid = NO_JOINT;
    }

    public boolean clearHoveredShape() {
        if (hoveredPhysicsShapeId == NO_SHAPE) return false;
        hoveredBodyEid = NO_BODY;
        hoveredPhysicsShapeId = NO_SHAPE;
        hoveredPartIndex = NO_PART;
        return true;
    }

    public boolean clearHoveredJoint() {
        if (hoveredJointEid == NO_JOINT) return false;
        hoveredJointEid = NO_JOINT;
        return true;
    }

    public boolean clearSelectedShape() {
        if (selectedPhysicsShapeId == NO_SHAPE) return false;
        selectedPhysicsShapeId = NO_SHAPE;
        selectedPartIndex = NO_PART;
        return true;
    }

    public boolean clearSelectedJoint() {
        if (selectedJointEid == NO_JOINT) return false;
        selectedJointEid = NO_JOINT;
        return true;
    }

    public boolean resetSelectedPartIndex() {
        if (selectedPartIndex == NO_PART) return false;
        selectedPartIndex = NO_PART;
        return true;
    }

    public boolean resetHoveredPartIndex() {
        if (hoveredPartIndex == NO_PART) return false;
        hoveredPartIndex = NO_PART;
        return true;
    }

    public void clearSelectionOnly() {
        clearHover();
        selectedPhysicsShapeId = NO_SHAPE;
        selectedPartIndex = NO_PART;
        selectedJointEid = NO_JOINT;
    }

    public boolean clearSelectedShapeIfMatches(int bodyEntityId, int physicsShapeId) {
        if (!isFocusedBody(bodyEntityId) || selectedPhysicsShapeId != physicsShapeId) {
            return false;
        }
        clearSelectionOnly();
        return true;
    }

    public void clear() {
        boolean wasEditing = focusedBodyEid != NO_BODY;
        focusedBodyEid = NO_BODY;
        clearSelectionOnly();
        if (wasEditing && studioEditingModeService != null) {
            studioEditingModeService.setModeActive(StudioEditingMode.PHYSICS, false, eventTag);
        }
    }

    public boolean isPhysicsEditingActive() {
        return focusedBodyEid >= 0;
    }
}
