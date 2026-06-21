package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;

final class LightMutationSupport {
    private LightMutationSupport() {
    }

    static boolean applyRadiusAndOptionalRotation(World world,
                                                  int entityId,
                                                  float radius,
                                                  float rotationRad,
                                                  boolean editRotation,
                                                  DirtyTrackerSystem dirty) {
        ComponentMapper<PointLightComponent> mPoint = world.getMapper(PointLightComponent.class);
        ComponentMapper<ConeLightComponent> mCone = world.getMapper(ConeLightComponent.class);
        ComponentMapper<TransformComponent> mTransform = world.getMapper(TransformComponent.class);
        ComponentMapper<DimensionsComponent> mDim = world.getMapper(DimensionsComponent.class);

        PointLightComponent point = mPoint.getSafe(entityId, null);
        ConeLightComponent cone = mCone.getSafe(entityId, null);
        TransformComponent transform = mTransform.getSafe(entityId, null);
        DimensionsComponent dim = mDim.getSafe(entityId, null);

        boolean hasLight = false;
        boolean editedConeRotation = false;
        float clampedRadius = EditLightRadiusCommand.clamp(radius);

        if (point != null) {
            point.radius = clampedRadius;
            hasLight = true;
        }
        if (cone != null) {
            cone.radius = clampedRadius;
            hasLight = true;
            if (editRotation && transform != null) {
                transform.rotationRad = rotationRad;
                editedConeRotation = true;
            }
        }
        if (!hasLight) return false;

        if (dim != null) {
            float d = clampedRadius * 2f;
            dim.width = d;
            dim.height = d;
            if (transform != null) {
                transform.originX = d * 0.5f;
                transform.originY = d * 0.5f;
            }
        }

        if (dirty != null) {
            int bits = GeometryDirty.SIZE;
            if (cone != null) bits |= GeometryDirty.ROTATION;
            dirty.geometry(entityId, bits);
            dirty.color(entityId);
            dirty.material(entityId);
        }
        return editedConeRotation;
    }
}
