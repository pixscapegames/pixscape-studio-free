package games.pixscape.studio.service.physics;

/**
 * Shared editor state for physics selection / sub-selection.
 * <p>
 * UX role:
 * - a current body can be focused
 * - a fixture can be hovered
 * - a fixture can be selected
 * - a joint can be selected
 * <p>
 * Notes :
 * - single-instance service, centralized in WorldCanvas
 * - no synchronized: intended for UI / editor thread usage
 */
public final class PhysicsSelectionService {
    public static final int NO_FIXTURE = -1;
    public static final int NO_BODY = -1;
    public static final int NO_JOINT = -1;

    private int focusedBodyEid = NO_BODY;

    private int hoveredBodyEid = NO_BODY;
    private long hoveredFixtureId = NO_FIXTURE;
    private int hoveredJointEid = NO_JOINT;

    private int selectedFixtureId = NO_FIXTURE;
    private int selectedJointEid = NO_JOINT;

    public int getFocusedBodyEid() {
        return focusedBodyEid;
    }

    public int getHoveredBodyEid() {
        return hoveredBodyEid;
    }

    public long getHoveredFixtureId() {
        return hoveredFixtureId;
    }

    public int getHoveredJointEid() {
        return hoveredJointEid;
    }

    public int getSelectedFixtureId() {
        return selectedFixtureId;
    }

    public int getSelectedJointEid() {
        return selectedJointEid;
    }

    public boolean hasFocusedBody() {
        return focusedBodyEid >= 0;
    }

    public boolean hasHoveredFixture() {
        return hoveredFixtureId != NO_FIXTURE;
    }

    public boolean hasHoveredJoint() {
        return hoveredJointEid >= 0;
    }

    public boolean hasSelectedFixture() {
        return selectedFixtureId != NO_FIXTURE;
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
        if (focusedBodyEid == bodyEid) return;
        focusedBodyEid = bodyEid;
        clearSelectionOnly();
    }

    // global fixture hover, without changing focus
    public void setHoveredFixture(int bodyEid, long fixtureId) {
        hoveredBodyEid = bodyEid;
        hoveredFixtureId = fixtureId;
        hoveredJointEid = NO_JOINT;
    }

    // global joint hover, without changing focus
    public void setHoveredJoint(int jointEid) {
        hoveredBodyEid = NO_BODY;
        hoveredFixtureId = NO_FIXTURE;
        hoveredJointEid = jointEid;
    }

    public void setSelectedFixture(int bodyEid, int fixtureId) {
        focusBody(bodyEid);
        selectedFixtureId = fixtureId;
        selectedJointEid = NO_JOINT;
        hoveredBodyEid = bodyEid;
        hoveredFixtureId = fixtureId;
        hoveredJointEid = NO_JOINT;
    }

    public void setSelectedJoint(int bodyEid, int jointEid) {
        focusBody(bodyEid);
        selectedJointEid = jointEid;
        selectedFixtureId = NO_FIXTURE;
        hoveredBodyEid = NO_BODY;
        hoveredFixtureId = NO_FIXTURE;
        hoveredJointEid = jointEid;
    }

    public void clearHover() {
        hoveredBodyEid = NO_BODY;
        hoveredFixtureId = NO_FIXTURE;
        hoveredJointEid = NO_JOINT;
    }

    public void clearSelectionOnly() {
        clearHover();
        selectedFixtureId = NO_FIXTURE;
        selectedJointEid = NO_JOINT;
    }

    public void clear() {
        focusedBodyEid = NO_BODY;
        clearSelectionOnly();
    }

    public boolean isPhysicsEditingActive() {
        return focusedBodyEid >= 0;
    }
}