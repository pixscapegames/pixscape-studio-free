package games.pixscape.studio.system;

import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import org.junit.Assert;
import org.junit.Test;

public class LightIconOverlaySystemWorldPoseTest {
    @Test
    public void usesResolvedHierarchyPoseWithoutMutatingAuthoredLocalTransform() {
        TransformComponent local = new TransformComponent();
        local.x = 3f;
        local.y = 4f;
        local.rotationRad = .25f;
        WorldTransformState state = new WorldTransformState(4);
        state.setResolved(2, 30f, 40f, 1.25f, 2f, 2f);

        Assert.assertEquals(30f, LightIconOverlaySystem.resolvedWorldX(state, 2, local), 0f);
        Assert.assertEquals(40f, LightIconOverlaySystem.resolvedWorldY(state, 2, local), 0f);
        Assert.assertEquals(1.25f,
                LightIconOverlaySystem.resolvedWorldRotation(state, 2, local), 0f);
        Assert.assertEquals(3f, local.x, 0f);
        Assert.assertEquals(4f, local.y, 0f);
        Assert.assertEquals(.25f, local.rotationRad, 0f);
    }

    @Test
    public void preservesStandaloneAuthoredPoseWhenNoHierarchyStateIsAvailable() {
        TransformComponent authored = new TransformComponent();
        authored.x = -2f;
        authored.y = 9f;
        authored.rotationRad = -.5f;

        Assert.assertEquals(-2f, LightIconOverlaySystem.resolvedWorldX(null, 0, authored), 0f);
        Assert.assertEquals(9f, LightIconOverlaySystem.resolvedWorldY(null, 0, authored), 0f);
        Assert.assertEquals(-.5f,
                LightIconOverlaySystem.resolvedWorldRotation(null, 0, authored), 0f);
    }
}
