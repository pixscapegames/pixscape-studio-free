package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.Initializer;

/** History-safe initializer for the authored state outside the generic snapshot model. */
final class GameObjectAssetEntityInitializer implements Initializer {
    private final World world;
    private final GenericEntityInitializer generic;
    private boolean gameObject;
    private String sourceAssetId;
    private int parentStableId;
    private Array<String> tags;
    private PropertySet properties;
    private GameObjectAsset.RepeatData repeat;
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
        if (data.tags != null) this.tags = new Array<>(data.tags.values.toArray(new String[0]));
        this.properties = remappedProperties != null ? remappedProperties.copy() : null;
        this.repeat = copy(data.repeat);
        this.pointLight = copy(data.pointLight);
        this.coneLight = copy(data.coneLight);
    }

    GenericEntityInitializer generic() { return generic; }

    @Override
    public void syncFrom(int entityId) {
        generic.syncFrom(entityId);
        GameObjectComponent root = world.getMapper(GameObjectComponent.class).getSafe(entityId, null);
        gameObject = root != null;
        sourceAssetId = root != null && root.sourceAssetId != null ? root.sourceAssetId : "";
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class).getSafe(entityId, null);
        parentStableId = member != null ? member.parentStableId : -1;
        PixscapeTagComponent tagComponent = world.getMapper(PixscapeTagComponent.class).getSafe(entityId, null);
        tags = tagComponent != null && tagComponent.tags != null ? new Array<>(tagComponent.tags) : null;
        CustomPropertiesComponent custom = world.getMapper(CustomPropertiesComponent.class).getSafe(entityId, null);
        properties = custom != null && custom.properties != null ? custom.properties.copy() : null;
        RenderRepeatComponent repeated = world.getMapper(RenderRepeatComponent.class).getSafe(entityId, null);
        repeat = repeated != null ? repeat(repeated.repeatX, repeated.repeatY) : null;
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
        if (tags != null) {
            PixscapeTagComponent component = world.getMapper(PixscapeTagComponent.class).create(entityId);
            component.tags.addAll(tags);
        }
        if (properties != null) {
            world.getMapper(CustomPropertiesComponent.class).create(entityId).properties = properties.copy();
        }
        if (repeat != null) {
            RenderRepeatComponent component = world.getMapper(RenderRepeatComponent.class).create(entityId);
            component.repeatX = repeat.repeatX;
            component.repeatY = repeat.repeatY;
        }
        if (pointLight != null) apply(world.getMapper(PointLightComponent.class).create(entityId), pointLight);
        if (coneLight != null) apply(world.getMapper(ConeLightComponent.class).create(entityId), coneLight);
    }

    @Override
    public String label() { return "Game Object Entity"; }

    private static GameObjectAsset.RepeatData repeat(boolean x, boolean y) {
        GameObjectAsset.RepeatData result = new GameObjectAsset.RepeatData();
        result.repeatX = x; result.repeatY = y; return result;
    }

    private static GameObjectAsset.RepeatData copy(GameObjectAsset.RepeatData value) {
        return value != null ? repeat(value.repeatX, value.repeatY) : null;
    }

    private static GameObjectAsset.PointLightData copy(GameObjectAsset.PointLightData value) {
        if (value == null) return null;
        GameObjectAsset.PointLightData result = new GameObjectAsset.PointLightData();
        result.r = value.r; result.g = value.g; result.b = value.b;
        result.intensity = value.intensity; result.radius = value.radius;
        result.falloff = value.falloff; result.enabled = value.enabled;
        return result;
    }

    private static GameObjectAsset.PointLightData copy(PointLightComponent value) {
        if (value == null) return null;
        GameObjectAsset.PointLightData result = new GameObjectAsset.PointLightData();
        result.r = value.r; result.g = value.g; result.b = value.b;
        result.intensity = value.intensity; result.radius = value.radius;
        result.falloff = value.falloff; result.enabled = value.enabled;
        return result;
    }

    private static void apply(PointLightComponent target, GameObjectAsset.PointLightData value) {
        target.r = value.r; target.g = value.g; target.b = value.b;
        target.intensity = value.intensity; target.radius = value.radius;
        target.falloff = value.falloff; target.enabled = value.enabled;
    }

    private static GameObjectAsset.ConeLightData copy(GameObjectAsset.ConeLightData value) {
        if (value == null) return null;
        GameObjectAsset.ConeLightData result = new GameObjectAsset.ConeLightData();
        result.r = value.r; result.g = value.g; result.b = value.b;
        result.intensity = value.intensity; result.radius = value.radius;
        result.coneAngleDeg = value.coneAngleDeg; result.rotationDeg = value.rotationDeg;
        result.softness = value.softness; result.falloff = value.falloff;
        result.enabled = value.enabled;
        return result;
    }

    private static GameObjectAsset.ConeLightData copy(ConeLightComponent value) {
        if (value == null) return null;
        GameObjectAsset.ConeLightData result = new GameObjectAsset.ConeLightData();
        result.r = value.r; result.g = value.g; result.b = value.b;
        result.intensity = value.intensity; result.radius = value.radius;
        result.coneAngleDeg = value.coneAngleDeg; result.rotationDeg = value.rotationDeg;
        result.softness = value.softness; result.falloff = value.falloff;
        result.enabled = value.enabled;
        return result;
    }

    private static void apply(ConeLightComponent target, GameObjectAsset.ConeLightData value) {
        target.r = value.r; target.g = value.g; target.b = value.b;
        target.intensity = value.intensity; target.radius = value.radius;
        target.coneAngleDeg = value.coneAngleDeg; target.rotationDeg = value.rotationDeg;
        target.softness = value.softness; target.falloff = value.falloff;
        target.enabled = value.enabled;
    }
}
