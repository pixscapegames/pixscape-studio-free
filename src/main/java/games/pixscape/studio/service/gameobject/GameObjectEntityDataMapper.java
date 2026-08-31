package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;

import java.util.Map;

final class GameObjectEntityDataMapper {
    GameObjectAsset.GameObjectEntityData fromGraphEntry(
            World world, EntityGraphEntry entry, IntMap<Integer> stableToSource) {
        GenericEntitySnapshotData source =
                entry.initializer().toSnapshotData(entry.sourceEntityId());
        GameObjectAsset.GameObjectEntityData data =
                new GameObjectAsset.GameObjectEntityData();
        data.sourceEntityId = source.sourceEntityId;
        copySnapshotToAsset(source, data);

        int entityId = entry.sourceEntityId();
        if (world.getMapper(GameObjectComponent.class).has(entityId)) {
            data.gameObject = new GameObjectAsset.GameObjectData();
        }
        PixscapeTagComponent tags = world.getMapper(PixscapeTagComponent.class)
                .getSafe(entityId, null);
        if (tags != null) {
            data.tags = new GameObjectAsset.TagsData();
            for (String tag : tags.tags) data.tags.values.add(tag);
        }
        CustomPropertiesComponent properties = world.getMapper(CustomPropertiesComponent.class)
                .getSafe(entityId, null);
        if (properties != null && properties.properties != null) {
            data.customProperties = remapProperties(properties.properties, stableToSource);
        }
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class)
                .getSafe(entityId, null);
        if (repeat != null) {
            data.repeat = new GameObjectAsset.RepeatData();
            data.repeat.repeatX = repeat.repeatX;
            data.repeat.repeatY = repeat.repeatY;
        }
        PointLightComponent point = world.getMapper(PointLightComponent.class)
                .getSafe(entityId, null);
        if (point != null) {
            data.pointLight = new GameObjectAsset.PointLightData();
            data.pointLight.r = point.r;
            data.pointLight.g = point.g;
            data.pointLight.b = point.b;
            data.pointLight.intensity = point.intensity;
            data.pointLight.radius = point.radius;
            data.pointLight.falloff = point.falloff;
            data.pointLight.enabled = point.enabled;
        }
        ConeLightComponent cone = world.getMapper(ConeLightComponent.class)
                .getSafe(entityId, null);
        if (cone != null) {
            data.coneLight = new GameObjectAsset.ConeLightData();
            data.coneLight.r = cone.r;
            data.coneLight.g = cone.g;
            data.coneLight.b = cone.b;
            data.coneLight.intensity = cone.intensity;
            data.coneLight.radius = cone.radius;
            data.coneLight.coneAngleDeg = cone.coneAngleDeg;
            data.coneLight.rotationDeg = cone.rotationDeg;
            data.coneLight.softness = cone.softness;
            data.coneLight.falloff = cone.falloff;
            data.coneLight.enabled = cone.enabled;
        }
        return data;
    }

    EntityGraphEntry toGraphEntry(World world, GameObjectAsset.GameObjectEntityData data) {
        GenericEntitySnapshotData snapshot = new GenericEntitySnapshotData();
        snapshot.sourceEntityId = data.sourceEntityId;
        if (data.transform != null) {
            snapshot.hasTransform = true;
            snapshot.x = data.transform.x;
            snapshot.y = data.transform.y;
            snapshot.rotationRad = data.transform.rotationRad;
            snapshot.scaleX = data.transform.scaleX;
            snapshot.scaleY = data.transform.scaleY;
            snapshot.originX = data.transform.originX;
            snapshot.originY = data.transform.originY;
        }
        if (data.entityIndex != null) {
            snapshot.hasEntityIndex = true;
            snapshot.layerIndex = 0;
            snapshot.zIndex = data.entityIndex.zIndex;
        }
        if (data.meta != null) {
            snapshot.hasMeta = true;
            snapshot.metaKind = data.meta.kind;
        }
        if (data.identity != null) {
            snapshot.hasIdentity = true;
            snapshot.identityName = data.identity.name;
        }
        if (data.visibility != null) {
            snapshot.hasVisibility = true;
            snapshot.visible = data.visibility.visible;
        }
        if (data.boundsFlags != null) {
            snapshot.hasAabb = data.boundsFlags.hasAabb;
            snapshot.hasObb = data.boundsFlags.hasObb;
        }
        if (data.dimensions != null) {
            snapshot.hasDimensions = true;
            snapshot.dimensionsWidth = data.dimensions.width;
            snapshot.dimensionsHeight = data.dimensions.height;
        }
        if (data.quadDeform != null) {
            snapshot.hasQuadDeform = true;
            snapshot.quadBlX = data.quadDeform.blX;
            snapshot.quadBlY = data.quadDeform.blY;
            snapshot.quadBrX = data.quadDeform.brX;
            snapshot.quadBrY = data.quadDeform.brY;
            snapshot.quadTrX = data.quadDeform.trX;
            snapshot.quadTrY = data.quadDeform.trY;
            snapshot.quadTlX = data.quadDeform.tlX;
            snapshot.quadTlY = data.quadDeform.tlY;
        }
        if (data.renderMaterial != null) {
            snapshot.hasRenderMaterial = true;
            snapshot.materialShaderIdx = data.renderMaterial.shaderIdx;
            snapshot.materialBlendModeId = data.renderMaterial.blendModeId;
        }
        if (data.assetRef != null) {
            snapshot.hasAssetRef = true;
            snapshot.assetRefAssetId = data.assetRef.assetId;
            snapshot.assetRefAtlasTag = data.assetRef.atlasTag;
        }
        if (data.tint != null) {
            snapshot.hasTint = true;
            snapshot.tintRgba = data.tint.rgba;
        }
        if (data.animation != null) {
            snapshot.hasAnimation = true;
            snapshot.animationAssetIds.addAll(data.animation.animationAssetIds);
            snapshot.animationFps = data.animation.fps;
            snapshot.animationPlaying = data.animation.playing;
            snapshot.animationLoop = data.animation.loop;
            snapshot.animationStateTime = data.animation.stateTime;
            snapshot.animationFrame = data.animation.frame;
            snapshot.animationCurrentClip = data.animation.currentClip;
        }
        if (data.shaderParams != null) {
            snapshot.hasShaderParams = true;
            for (Map.Entry<String, Float> entry : data.shaderParams.floats.entrySet()) {
                snapshot.shaderFloats.add(new ShaderFloatParam(entry.getKey(), entry.getValue()));
            }
        }
        GenericEntityInitializer initializer = new GenericEntityInitializer(world)
                .applySnapshotData(snapshot);
        return new EntityGraphEntry(data.sourceEntityId, initializer);
    }

    private static void copySnapshotToAsset(
            GenericEntitySnapshotData source, GameObjectAsset.GameObjectEntityData data) {
        if (source.hasTransform) {
            data.transform = new GameObjectAsset.TransformData();
            data.transform.x = source.x;
            data.transform.y = source.y;
            data.transform.rotationRad = source.rotationRad;
            data.transform.scaleX = source.scaleX;
            data.transform.scaleY = source.scaleY;
            data.transform.originX = source.originX;
            data.transform.originY = source.originY;
        }
        if (source.hasEntityIndex) {
            data.entityIndex = new GameObjectAsset.EntityIndexData();
            data.entityIndex.zIndex = source.zIndex;
        }
        if (source.hasMeta) {
            data.meta = new GameObjectAsset.MetaData();
            data.meta.kind = source.metaKind;
        }
        if (source.hasIdentity) {
            data.identity = new GameObjectAsset.IdentityData();
            data.identity.name = source.identityName;
        }
        if (source.hasVisibility) {
            data.visibility = new GameObjectAsset.VisibilityData();
            data.visibility.visible = source.visible;
        }
        if (source.hasAabb || source.hasObb) {
            data.boundsFlags = new GameObjectAsset.BoundsFlagsData();
            data.boundsFlags.hasAabb = source.hasAabb;
            data.boundsFlags.hasObb = source.hasObb;
        }
        if (source.hasDimensions) {
            data.dimensions = new GameObjectAsset.DimensionsData();
            data.dimensions.width = source.dimensionsWidth;
            data.dimensions.height = source.dimensionsHeight;
        }
        if (source.hasQuadDeform) {
            data.quadDeform = new GameObjectAsset.QuadDeformData();
            data.quadDeform.blX = source.quadBlX;
            data.quadDeform.blY = source.quadBlY;
            data.quadDeform.brX = source.quadBrX;
            data.quadDeform.brY = source.quadBrY;
            data.quadDeform.trX = source.quadTrX;
            data.quadDeform.trY = source.quadTrY;
            data.quadDeform.tlX = source.quadTlX;
            data.quadDeform.tlY = source.quadTlY;
        }
        if (source.hasRenderMaterial) {
            data.renderMaterial = new GameObjectAsset.RenderMaterialData();
            data.renderMaterial.shaderIdx = source.materialShaderIdx;
            data.renderMaterial.blendModeId = source.materialBlendModeId;
        }
        if (source.hasAssetRef) {
            data.assetRef = new GameObjectAsset.AssetRefData();
            data.assetRef.assetId = source.assetRefAssetId;
            data.assetRef.atlasTag = source.assetRefAtlasTag;
        }
        if (source.hasTint) {
            data.tint = new GameObjectAsset.TintData();
            data.tint.rgba = source.tintRgba;
        }
        if (source.hasAnimation) {
            data.animation = new GameObjectAsset.AnimationData();
            data.animation.animationAssetIds.addAll(source.animationAssetIds);
            data.animation.fps = source.animationFps;
            data.animation.playing = source.animationPlaying;
            data.animation.loop = source.animationLoop;
            data.animation.stateTime = source.animationStateTime;
            data.animation.frame = source.animationFrame;
            data.animation.currentClip = source.animationCurrentClip;
        }
        if (source.hasShaderParams) {
            data.shaderParams = new GameObjectAsset.ShaderParamsData();
            for (ShaderFloatParam param : source.shaderFloats) {
                if (param != null && param.name != null && !param.name.isEmpty()) {
                    data.shaderParams.floats.put(param.name, param.value);
                }
            }
        }
    }

    private static PropertySet remapProperties(
            PropertySet source, IntMap<Integer> stableToSource) {
        PropertySet remapped = new PropertySet(source.size());
        Array<String> names = new Array<>();
        source.copyNamesTo(names);
        for (String name : names) {
            PropertyValue value = source.valueCopy(name);
            if (value.type() == PropertyType.OBJECT) {
                int stableId = value.asObjectStableId();
                if (stableId == -1) {
                    remapped.putObjectStableId(name, -1);
                } else if (stableToSource.containsKey(stableId)) {
                    remapped.putObjectStableId(name, stableToSource.get(stableId));
                } else {
                    throw new IllegalArgumentException("Game Object asset source entity has "
                            + "unsupported external OBJECT reference stableId " + stableId + ".");
                }
            } else if (value.type() == PropertyType.CLASS) {
                remapped.putClass(name, value.className(),
                        remapProperties(value.classPropertiesCopy(), stableToSource));
            } else {
                remapped.put(name, value);
            }
        }
        return remapped;
    }
}
