package games.pixscape.studio.system;

import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsOverlaySelectionUtilTest {

    @Test
    public void jointIsRelatedWhenFocusedBodyMatchesEitherEndpoint() {
        PhysicsJointComponent joint = new PhysicsJointComponent();
        joint.aEid = 10;
        joint.bEid = 20;

        Assert.assertTrue(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(joint, 10, -1, 33));
        Assert.assertTrue(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(joint, 20, -1, 33));
    }

    @Test
    public void selectedJointIsRelatedWithoutFocusedBody() {
        PhysicsJointComponent joint = new PhysicsJointComponent();
        joint.aEid = 1;
        joint.bEid = 2;

        Assert.assertTrue(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(joint, -1, 77, 77));
    }

    @Test
    public void selectedJointDoesNotHideFocusedBodyRelatedJoints() {
        PhysicsJointComponent focusedBodyJoint = new PhysicsJointComponent();
        focusedBodyJoint.aEid = 100;
        focusedBodyJoint.bEid = 200;

        PhysicsJointComponent selectedJoint = new PhysicsJointComponent();
        selectedJoint.aEid = 1;
        selectedJoint.bEid = 2;

        Assert.assertTrue(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(focusedBodyJoint, 100, 77, 55));
        Assert.assertTrue(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(selectedJoint, 100, 77, 77));
    }

    @Test
    public void noSelectionMeansNotRelated() {
        PhysicsJointComponent joint = new PhysicsJointComponent();
        joint.aEid = 1;
        joint.bEid = 2;

        Assert.assertFalse(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(joint, -1, -1, 8));
    }

    @Test
    public void unrelatedJointIsNotRelated() {
        PhysicsJointComponent joint = new PhysicsJointComponent();
        joint.aEid = 1;
        joint.bEid = 2;

        Assert.assertFalse(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(joint, 99, 77, 55));
    }

    @Test
    public void nullJointIsNotRelated() {
        Assert.assertFalse(PhysicsOverlaySelectionUtil.isJointRelatedToSelection(null, 99, 77, 77));
    }
}
