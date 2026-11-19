package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.Initializer;

/**
 * History-safe initializer for the authored state outside the generic snapshot model.
 */
final class GameObjectAssetEntityInitializer implements Initializer {
    private final World world;
    private final GenericEntityInitializer generic;
    private boolean gameObject;
    private String sourceAssetId;
    private int parentStableId;
    private GameObjectAsset.PointLightData pointLight;
    private GameObjectAsset.ConeLightData coneLight;

    GameObjectAssetEntityInitializer(
            World world,
            GenericEntityInitializer generic,
            GameObjectAsset.GameObjectEntityData data,
            int parentStableId,
            String sourceAssetId,
            PropertySet remappedProperties) {
        this.world = world;
        this.generic = generic;
        this.gameObject = data.gameObject != null;
        this.parentStableId = parentStableId;
        this.sourceAssetId = sourceAssetId != null ? sourceAssetId : "";
        generic.setCustomProperties(remappedProperties);
        this.pointLight = copy(data.pointLight);
        this.coneLight = copy(data.coneLight);
    }

    @Override
    public int activeLightCount() {
        return games.pixscape.runtime.render.lighting.ActiveLights.isActive(generic.isVisible(),
                pointLight != null, pointLight != null && pointLight.enabled, coneLight != null, coneLight != null && coneLight.enabled) ? 1 : 0;
    }

    GenericEntityInitializer generic() {
        return generic;
    }

    @Override
    public void syncFrom(int entityId) {
        generic.syncFrom(entityId);
        GameObjectComponent root = world.getMapper(GameObjectComponent.class).getSafe(entityId, null);
        gameObject = root != null;
        sourceAssetId = root != null && root.sourceAssetId != null ? root.sourceAssetId : "";
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).getSafe(entityId, null);
        parentStableId = member != null ? member.parentStableId : -1;
        pointLight = copy(world.getMapper(PointLightComponent.class).getSafe(entityId, null));
        coneLight = copy(world.getMapper(ConeLightComponent.class).getSafe(entityId, null));
    }

    @Override
    public void init(int entityId) {
        generic.init(entityId);
        if (gameObject) {
            world.getMapper(GameObjectComponent.class).create(entityId).sourceAssetId = sourceAssetId;
        }
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entityId).parentStableId = parentStableId;
        }
        if (pointLight != null) apply(world.getMapper(PointLightComponent.class).create(entityId), pointLight);
        if (coneLight != null) apply(world.getMapper(ConeLightComponent.class).create(entityId), coneLight);
    }

    @Override
    public String label() {
        return "Game Object Entity";
    }

    private static GameObjectAsset.PointLightData copy(GameObjectAsset.PointLightData value) {
        if (value == null) return null;
        GameObjectAsset.PointLightData result = new GameObjectAsset.PointLightData();
        result.r = value.r;
        result.g = value.g;
        result.b = value.b;
        result.intensity = value.intensity;
        result.radius = value.radius;
        result.falloff = value.falloff;
        result.enabled = value.enabled;
        result.height = value.height;
        result.shadowQuality = value.shadowQuality;
        result.shadowResolution = value.shadowResolution;
        return result;
    }

    private static GameObjectAsset.PointLightData copy(PointLightComponent value) {
        if (value == null) return null;
        GameObjectAsset.PointLightData result = new GameObjectAsset.PointLightData();
        result.r = value.r;
        result.g = value.g;
        result.b = value.b;
        result.intensity = value.intensity;
        result.radius = value.radius;
        result.falloff = value.falloff;
        result.enabled = value.enabled;
        result.height = value.height;
        result.shadowQuality = value.shadowQuality;
        result.shadowResolution = value.shadowResolution;
        return result;
    }

    private static void apply(PointLightComponent target, GameObjectAsset.PointLightData value) {
        target.r = value.r;
        target.g = value.g;
        target.b = value.b;
        target.intensity = value.intensity;
        target.radius = value.radius;
        target.falloff = value.falloff;
        target.enabled = value.enabled;
        target.height = value.height;
        target.shadowQuality = value.shadowQuality;
        target.shadowResolution = value.shadowResolution;
    }

    private static GameObjectAsset.ConeLightData copy(GameObjectAsset.ConeLightData value) {
        if (value == null) return null;
        GameObjectAsset.ConeLightData result = new GameObjectAsset.ConeLightData();
        result.r = value.r;
        result.g = value.g;
        result.b = value.b;
        result.intensity = value.intensity;
        result.radius = value.radius;
        result.coneAngleDeg = value.coneAngleDeg;
        result.rotationDeg = value.rotationDeg;
        result.inclinationDeg = value.inclinationDeg;
        result.softness = value.softness;
        result.falloff = value.falloff;
        result.enabled = value.enabled;
        result.height = value.height;
        result.shadowQuality = value.shadowQuality;
        result.shadowResolution = value.shadowResolution;
        return result;
    }

    private static GameObjectAsset.ConeLightData copy(ConeLightComponent value) {
        if (value == null) return null;
        GameObjectAsset.ConeLightData result = new GameObjectAsset.ConeLightData();
        result.r = value.r;
        result.g = value.g;
        result.b = value.b;
        result.intensity = value.intensity;
        result.radius = value.radius;
        result.coneAngleDeg = value.coneAngleDeg;
        result.rotationDeg = value.rotationDeg;
        result.inclinationDeg = value.inclinationDeg;
        result.softness = value.softness;
        result.falloff = value.falloff;
        result.enabled = value.enabled;
        result.height = value.height;
        result.shadowQuality = value.shadowQuality;
        result.shadowResolution = value.shadowResolution;
        return result;
    }

    private static void apply(ConeLightComponent target, GameObjectAsset.ConeLightData value) {
        target.r = value.r;
        target.g = value.g;
        target.b = value.b;
        target.intensity = value.intensity;
        target.radius = value.radius;
        target.coneAngleDeg = value.coneAngleDeg;
        target.rotationDeg = value.rotationDeg;
        target.inclinationDeg = value.inclinationDeg;
        target.softness = value.softness;
        target.falloff = value.falloff;
        target.enabled = value.enabled;
        target.height = value.height;
        target.shadowQuality = value.shadowQuality;
        target.shadowResolution = value.shadowResolution;
    }
}
