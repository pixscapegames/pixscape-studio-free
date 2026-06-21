package games.pixscape.studio.system;

import games.pixscape.runtime.component.physics.PhysicsJointComponent;

final class PhysicsOverlaySelectionUtil {

    private PhysicsOverlaySelectionUtil() {
    }

    static boolean isJointRelatedToSelection(PhysicsJointComponent joint,
                                             int focusedBodyEid,
                                             int selectedJointEid,
                                             int jointEid) {
        if (joint == null) return false;
        if (selectedJointEid >= 0 && selectedJointEid == jointEid) return true;
        if (focusedBodyEid < 0) return false;
        return joint.aEid == focusedBodyEid || joint.bEid == focusedBodyEid;
    }
}
