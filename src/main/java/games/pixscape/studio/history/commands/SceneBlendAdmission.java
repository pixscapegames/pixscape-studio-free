package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneLoadingInvocationStrategy;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.LightCompositionPass;
import games.pixscape.runtime.render.lighting.ActiveLights;
import games.pixscape.runtime.render.lighting.ForwardAdmission;

/**
 * Command-time checks on authored entity materials, including off-camera sprites.
 */
final class SceneBlendAdmission {
    private SceneBlendAdmission() {
    }

    static boolean usesForward(World world, boolean requested) {
        if (!requested) return false;
        synchronize(world);
        return ActiveLights.count(world) > 0;
    }

    static String lightingRefusal(World world, boolean requestedForward) {
        synchronize(world);
        boolean forward = requestedForward && ActiveLights.count(world) > 0;
        var entities = world.getAspectSubscriptionManager().get(Aspect.all(RenderMaterialComponent.class)).getEntities();
        var materials = world.getMapper(RenderMaterialComponent.class);
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            String reason = refusal(world, entity, materials.get(entity).blendModeId, forward);
            if (reason != null) return reason;
        }
        return null;
    }

    private static void synchronize(World world) {
        if (world.getInvocationStrategy() instanceof SceneLoadingInvocationStrategy strategy)
            strategy.synchronizeEntitySubscriptions();
    }

    static String refusal(World world, int entity, int blend, boolean forward) {
        boolean light = world.getMapper(PointLightComponent.class).has(entity)
                || world.getMapper(ConeLightComponent.class).has(entity);
        try {
            if (forward && !light) ForwardAdmission.validateBlend(entity, blend, false);
            else LightCompositionPass.validate(light, blend);
            return null;
        } catch (IllegalArgumentException incompatible) {
            var identity = world.getMapper(PixscapeIdentityComponent.class).getSafe(entity, null);
            String owner = identity == null ? "Entity " + entity : "'" + identity.name + "' (entity " + entity + ")";
            BlendMode mode = BlendMode.fromId(blend);
            String name = mode.id == blend ? mode.name() : "blend ID " + blend;
            return owner + " cannot use " + name + " with "
                    + (forward ? "Forward" : "reference") + " lighting. Choose "
                    + (light ? "ADDITIVE" : "ALPHA") + " to use this lighting mode.";
        }
    }
}
