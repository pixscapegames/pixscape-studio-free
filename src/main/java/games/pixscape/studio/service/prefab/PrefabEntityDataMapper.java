package games.pixscape.studio.service.prefab;

import com.artemis.World;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.prefab.PrefabAsset;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;

import java.util.Map;

final class PrefabEntityDataMapper {
    PrefabAsset.PrefabEntityData fromGraphEntry(EntityGraphEntry entry) {
        GenericEntitySnapshotData s = entry.initializer().toSnapshotData(entry.sourceEntityId());
        PrefabAsset.PrefabEntityData d = new PrefabAsset.PrefabEntityData();
        d.sourceEntityId = s.sourceEntityId;
        if (s.hasTransform) {
            d.transform = new PrefabAsset.TransformData();
            d.transform.x = s.x;
            d.transform.y = s.y;
            d.transform.rotationRad = s.rotationRad;
            d.transform.scaleX = s.scaleX;
            d.transform.scaleY = s.scaleY;
            d.transform.originX = s.originX;
            d.transform.originY = s.originY;
        }
        if (s.hasEntityIndex) {
            d.entityIndex = new PrefabAsset.EntityIndexData();
            d.entityIndex.layerIndex = s.layerIndex;
            d.entityIndex.zIndex = s.zIndex;
        }
        if (s.hasMeta) {
            d.meta = new PrefabAsset.MetaData();
            d.meta.kind = s.metaKind;
        }
        if (s.hasIdentity) {
            d.identity = new PrefabAsset.IdentityData();
            d.identity.name = s.identityName;
        }
        if (s.hasVisibility) {
            d.visibility = new PrefabAsset.VisibilityData();
            d.visibility.visible = s.visible;
        }
        if (s.hasAabb || s.hasObb) {
            d.boundsFlags = new PrefabAsset.BoundsFlagsData();
            d.boundsFlags.hasAabb = s.hasAabb;
            d.boundsFlags.hasObb = s.hasObb;
        }
        if (s.hasDimensions) {
            d.dimensions = new PrefabAsset.DimensionsData();
            d.dimensions.width = s.dimensionsWidth;
            d.dimensions.height = s.dimensionsHeight;
        }
        if (s.hasTextureRegion) {
            d.textureRegion = new PrefabAsset.TextureRegionData();
            d.textureRegion.u1 = s.textureU1;
            d.textureRegion.v1 = s.textureV1;
            d.textureRegion.u2 = s.textureU2;
            d.textureRegion.v2 = s.textureV2;
            d.textureRegion.pixW = s.texturePixW;
            d.textureRegion.pixH = s.texturePixH;
            d.textureRegion.valid = s.textureValid;
        }
        if (s.hasRenderMaterial) {
            d.renderMaterial = new PrefabAsset.RenderMaterialData();
            d.renderMaterial.shaderIdx = s.materialShaderIdx;
            d.renderMaterial.blendModeId = s.materialBlendModeId;
            d.renderMaterial.textureHandle = s.materialTextureHandle;
            d.renderMaterial.debugAtlasTag = s.materialDebugAtlasTag;
        }
        if (s.hasAssetRef) {
            d.assetRef = new PrefabAsset.AssetRefData();
            d.assetRef.assetId = s.assetRefAssetId;
            d.assetRef.atlasTag = s.assetRefAtlasTag;
        }
        if (s.hasTint) {
            d.tint = new PrefabAsset.TintData();
            d.tint.rgba = s.tintRgba;
        }
        if (s.hasAnimation) {
            d.animation = new PrefabAsset.AnimationData();
            d.animation.name = s.animationName;
            d.animation.fps = s.animationFps;
            d.animation.playing = s.animationPlaying;
            d.animation.loop = s.animationLoop;
            d.animation.stateTime = s.animationStateTime;
            d.animation.frame = s.animationFrame;
            d.animation.currentClip = s.animationCurrentClip;
            d.animation.clips.clear();
            if (s.animationClips != null) {
                for (ObjectMap.Entry<String, AnimationComponent.Clip> it : s.animationClips) {
                    AnimationComponent.Clip c = it.value;
                    if (it.key != null && c != null) {
                        d.animation.clips.put(it.key, new PrefabAsset.AnimationClipData(c.start, c.end));
                    }
                }
            }
        }
        if (s.hasShaderParams) {
            d.shaderParams = new PrefabAsset.ShaderParamsData();
            for (int i = 0; i < s.shaderFloats.size; i++) {
                ShaderFloatParam param = s.shaderFloats.get(i);
                if (param == null || param.name == null || param.name.isEmpty()) {
                    continue;
                }
                d.shaderParams.floats.put(param.name, param.value);
            }
        }
        if (s.hasPhysicsBody) {
            d.physicsBody = new PrefabAsset.PhysicsBodyData();
            d.physicsBody.type = s.bodyType;
            d.physicsBody.fixedRotation = s.fixedRotation;
            d.physicsBody.bullet = s.bullet;
            d.physicsBody.allowSleep = s.allowSleep;
            d.physicsBody.awake = s.awake;
            d.physicsBody.gravityScale = s.gravityScale;
            d.physicsBody.linearDamping = s.linearDamping;
            d.physicsBody.angularDamping = s.angularDamping;
        }
        for (PhysicsShapeData shape : s.shapes) {
            if (shape != null) {
                d.physicsShapes.add(shape.copy());
            }
        }
        if (s.hasJoint) {
            d.joint = new PrefabAsset.JointBaseData();
            d.joint.type = s.jointType;
            d.joint.aEid = s.jointAEid;
            d.joint.bEid = s.jointBEid;
            d.joint.collideConnected = s.jointCollideConnected;
            d.joint.anchorAx = s.jointAnchorAx;
            d.joint.anchorAy = s.jointAnchorAy;
            d.joint.anchorBx = s.jointAnchorBx;
            d.joint.anchorBy = s.jointAnchorBy;
        }
        if (s.hasDistanceJoint) {
            d.distanceJoint = new PrefabAsset.DistanceJointData();
            d.distanceJoint.lengthM = s.distanceLengthM;
            d.distanceJoint.frequencyHz = s.distanceFrequencyHz;
            d.distanceJoint.dampingRatio = s.distanceDampingRatio;
        }
        if (s.hasRevoluteJoint) {
            d.revoluteJoint = new PrefabAsset.RevoluteJointData();
            d.revoluteJoint.enableLimit = s.revoluteEnableLimit;
            d.revoluteJoint.lowerAngleRad = s.revoluteLowerAngleRad;
            d.revoluteJoint.upperAngleRad = s.revoluteUpperAngleRad;
            d.revoluteJoint.enableMotor = s.revoluteEnableMotor;
            d.revoluteJoint.motorSpeedRad = s.revoluteMotorSpeedRad;
            d.revoluteJoint.maxMotorTorque = s.revoluteMaxMotorTorque;
        }
        if (s.hasPrismaticJoint) {
            d.prismaticJoint = new PrefabAsset.PrismaticJointData();
            d.prismaticJoint.axisX = s.prismaticAxisX;
            d.prismaticJoint.axisY = s.prismaticAxisY;
            d.prismaticJoint.enableLimit = s.prismaticEnableLimit;
            d.prismaticJoint.lowerTranslationM = s.prismaticLowerTranslationM;
            d.prismaticJoint.upperTranslationM = s.prismaticUpperTranslationM;
            d.prismaticJoint.enableMotor = s.prismaticEnableMotor;
            d.prismaticJoint.motorSpeedMps = s.prismaticMotorSpeedMps;
            d.prismaticJoint.maxMotorForce = s.prismaticMaxMotorForce;
        }
        if (s.hasWheelJoint) {
            d.wheelJoint = new PrefabAsset.WheelJointData();
            d.wheelJoint.axisX = s.wheelAxisX;
            d.wheelJoint.axisY = s.wheelAxisY;
            d.wheelJoint.enableMotor = s.wheelEnableMotor;
            d.wheelJoint.motorSpeedRad = s.wheelMotorSpeedRad;
            d.wheelJoint.maxMotorTorque = s.wheelMaxMotorTorque;
            d.wheelJoint.frequencyHz = s.wheelFrequencyHz;
            d.wheelJoint.dampingRatio = s.wheelDampingRatio;
        }
        if (s.hasFrictionJoint) {
            d.frictionJoint = new PrefabAsset.FrictionJointData();
            d.frictionJoint.maxForce = s.frictionMaxForce;
            d.frictionJoint.maxTorque = s.frictionMaxTorque;
        }
        if (s.hasMotorJoint) {
            d.motorJoint = new PrefabAsset.MotorJointData();
            d.motorJoint.linearOffsetX = s.motorLinearOffsetX;
            d.motorJoint.linearOffsetY = s.motorLinearOffsetY;
            d.motorJoint.angularOffsetRad = s.motorAngularOffsetRad;
            d.motorJoint.maxForce = s.motorMaxForce;
            d.motorJoint.maxTorque = s.motorMaxTorque;
            d.motorJoint.correctionFactor = s.motorCorrectionFactor;
        }
        if (s.hasWeldJoint) {
            d.weldJoint = new PrefabAsset.WeldJointData();
            d.weldJoint.referenceAngleRad = s.weldReferenceAngleRad;
            d.weldJoint.frequencyHz = s.weldFrequencyHz;
            d.weldJoint.dampingRatio = s.weldDampingRatio;
        }
        if (s.hasPulleyJoint) {
            d.pulleyJoint = new PrefabAsset.PulleyJointData();
            d.pulleyJoint.groundAx = s.pulleyGroundAx;
            d.pulleyJoint.groundAy = s.pulleyGroundAy;
            d.pulleyJoint.groundBx = s.pulleyGroundBx;
            d.pulleyJoint.groundBy = s.pulleyGroundBy;
            d.pulleyJoint.lengthAM = s.pulleyLengthAM;
            d.pulleyJoint.lengthBM = s.pulleyLengthBM;
            d.pulleyJoint.ratio = s.pulleyRatio;
        }
        if (s.hasGearJoint) {
            d.gearJoint = new PrefabAsset.GearJointData();
            d.gearJoint.joint1Eid = s.gearJoint1Eid;
            d.gearJoint.joint2Eid = s.gearJoint2Eid;
            d.gearJoint.ratio = s.gearRatio;
        }
        return d;
    }

    EntityGraphEntry toGraphEntry(World world, PrefabAsset.PrefabEntityData d) {
        GenericEntitySnapshotData s = new GenericEntitySnapshotData();
        s.sourceEntityId = d.sourceEntityId;
        if (d.transform != null) {
            s.hasTransform = true;
            s.x = d.transform.x;
            s.y = d.transform.y;
            s.rotationRad = d.transform.rotationRad;
            s.scaleX = d.transform.scaleX;
            s.scaleY = d.transform.scaleY;
            s.originX = d.transform.originX;
            s.originY = d.transform.originY;
        }
        if (d.entityIndex != null) {
            s.hasEntityIndex = true;
            s.layerIndex = d.entityIndex.layerIndex;
            s.zIndex = d.entityIndex.zIndex;
        }
        if (d.meta != null) {
            s.hasMeta = true;
            s.metaKind = d.meta.kind;
        }
        if (d.identity != null) {
            s.hasIdentity = true;
            s.identityName = d.identity.name;
        }
        if (d.visibility != null) {
            s.hasVisibility = true;
            s.visible = d.visibility.visible;
        }
        if (d.boundsFlags != null) {
            s.hasAabb = d.boundsFlags.hasAabb;
            s.hasObb = d.boundsFlags.hasObb;
        }
        if (d.dimensions != null) {
            s.hasDimensions = true;
            s.dimensionsWidth = d.dimensions.width;
            s.dimensionsHeight = d.dimensions.height;
        }
        if (d.textureRegion != null) {
            s.hasTextureRegion = true;
            s.textureU1 = d.textureRegion.u1;
            s.textureV1 = d.textureRegion.v1;
            s.textureU2 = d.textureRegion.u2;
            s.textureV2 = d.textureRegion.v2;
            s.texturePixW = d.textureRegion.pixW;
            s.texturePixH = d.textureRegion.pixH;
            s.textureValid = d.textureRegion.valid;
        }
        if (d.renderMaterial != null) {
            s.hasRenderMaterial = true;
            s.materialShaderIdx = d.renderMaterial.shaderIdx;
            s.materialBlendModeId = d.renderMaterial.blendModeId;
            s.materialTextureHandle = d.renderMaterial.textureHandle;
            s.materialDebugAtlasTag = d.renderMaterial.debugAtlasTag;
        }
        if (d.assetRef != null) {
            s.hasAssetRef = true;
            s.assetRefAssetId = d.assetRef.assetId;
            s.assetRefAtlasTag = d.assetRef.atlasTag;
        }
        if (d.tint != null) {
            s.hasTint = true;
            s.tintRgba = d.tint.rgba;
        }
        if (d.animation != null) {
            s.hasAnimation = true;
            s.animationName = d.animation.name;
            s.animationFps = d.animation.fps;
            s.animationPlaying = d.animation.playing;
            s.animationLoop = d.animation.loop;
            s.animationStateTime = d.animation.stateTime;
            s.animationFrame = d.animation.frame;
            s.animationCurrentClip = d.animation.currentClip;
            s.animationClips.clear();
            if (d.animation.clips != null) {
                for (ObjectMap.Entry<String, PrefabAsset.AnimationClipData> it : d.animation.clips) {
                    PrefabAsset.AnimationClipData c = it.value;
                    if (it.key != null && c != null) {
                        s.animationClips.put(it.key, new AnimationComponent.Clip(c.start, c.end));
                    }
                }
            }
        }
        if (d.shaderParams != null && d.shaderParams.floats != null) {
            s.hasShaderParams = true;
            s.shaderFloats.clear();
            for (Map.Entry<String, Float> entry : d.shaderParams.floats.entrySet()) {
                String name = entry.getKey();
                Float value = entry.getValue();
                if (name == null || name.isEmpty() || value == null) {
                    continue;
                }
                s.shaderFloats.add(new ShaderFloatParam(name, value));
            }
        }

        if (d.physicsBody != null) {
            s.hasPhysicsBody = true;
            s.bodyType = d.physicsBody.type;
            s.fixedRotation = d.physicsBody.fixedRotation;
            s.bullet = d.physicsBody.bullet;
            s.allowSleep = d.physicsBody.allowSleep;
            s.awake = d.physicsBody.awake;
            s.gravityScale = d.physicsBody.gravityScale;
            s.linearDamping = d.physicsBody.linearDamping;
            s.angularDamping = d.physicsBody.angularDamping;
        }
        if (d.physicsShapes != null) {
            for (PhysicsShapeData source : d.physicsShapes) {
                if (source == null) {
                    continue;
                }
                s.shapes.add(source.copy());
            }
        }
        if (d.joint != null) {
            s.hasJoint = true;
            s.jointType = d.joint.type;
            s.jointAEid = d.joint.aEid;
            s.jointBEid = d.joint.bEid;
            s.jointCollideConnected = d.joint.collideConnected;
            s.jointAnchorAx = d.joint.anchorAx;
            s.jointAnchorAy = d.joint.anchorAy;
            s.jointAnchorBx = d.joint.anchorBx;
            s.jointAnchorBy = d.joint.anchorBy;
        }
        if (d.distanceJoint != null) {
            s.hasDistanceJoint = true;
            s.distanceLengthM = d.distanceJoint.lengthM;
            s.distanceFrequencyHz = d.distanceJoint.frequencyHz;
            s.distanceDampingRatio = d.distanceJoint.dampingRatio;
        }
        if (d.revoluteJoint != null) {
            s.hasRevoluteJoint = true;
            s.revoluteEnableLimit = d.revoluteJoint.enableLimit;
            s.revoluteLowerAngleRad = d.revoluteJoint.lowerAngleRad;
            s.revoluteUpperAngleRad = d.revoluteJoint.upperAngleRad;
            s.revoluteEnableMotor = d.revoluteJoint.enableMotor;
            s.revoluteMotorSpeedRad = d.revoluteJoint.motorSpeedRad;
            s.revoluteMaxMotorTorque = d.revoluteJoint.maxMotorTorque;
        }
        if (d.prismaticJoint != null) {
            s.hasPrismaticJoint = true;
            s.prismaticAxisX = d.prismaticJoint.axisX;
            s.prismaticAxisY = d.prismaticJoint.axisY;
            s.prismaticEnableLimit = d.prismaticJoint.enableLimit;
            s.prismaticLowerTranslationM = d.prismaticJoint.lowerTranslationM;
            s.prismaticUpperTranslationM = d.prismaticJoint.upperTranslationM;
            s.prismaticEnableMotor = d.prismaticJoint.enableMotor;
            s.prismaticMotorSpeedMps = d.prismaticJoint.motorSpeedMps;
            s.prismaticMaxMotorForce = d.prismaticJoint.maxMotorForce;
        }
        if (d.wheelJoint != null) {
            s.hasWheelJoint = true;
            s.wheelAxisX = d.wheelJoint.axisX;
            s.wheelAxisY = d.wheelJoint.axisY;
            s.wheelEnableMotor = d.wheelJoint.enableMotor;
            s.wheelMotorSpeedRad = d.wheelJoint.motorSpeedRad;
            s.wheelMaxMotorTorque = d.wheelJoint.maxMotorTorque;
            s.wheelFrequencyHz = d.wheelJoint.frequencyHz;
            s.wheelDampingRatio = d.wheelJoint.dampingRatio;
        }
        if (d.frictionJoint != null) {
            s.hasFrictionJoint = true;
            s.frictionMaxForce = d.frictionJoint.maxForce;
            s.frictionMaxTorque = d.frictionJoint.maxTorque;
        }
        if (d.motorJoint != null) {
            s.hasMotorJoint = true;
            s.motorLinearOffsetX = d.motorJoint.linearOffsetX;
            s.motorLinearOffsetY = d.motorJoint.linearOffsetY;
            s.motorAngularOffsetRad = d.motorJoint.angularOffsetRad;
            s.motorMaxForce = d.motorJoint.maxForce;
            s.motorMaxTorque = d.motorJoint.maxTorque;
            s.motorCorrectionFactor = d.motorJoint.correctionFactor;
        }
        if (d.weldJoint != null) {
            s.hasWeldJoint = true;
            s.weldReferenceAngleRad = d.weldJoint.referenceAngleRad;
            s.weldFrequencyHz = d.weldJoint.frequencyHz;
            s.weldDampingRatio = d.weldJoint.dampingRatio;
        }
        if (d.pulleyJoint != null) {
            s.hasPulleyJoint = true;
            s.pulleyGroundAx = d.pulleyJoint.groundAx;
            s.pulleyGroundAy = d.pulleyJoint.groundAy;
            s.pulleyGroundBx = d.pulleyJoint.groundBx;
            s.pulleyGroundBy = d.pulleyJoint.groundBy;
            s.pulleyLengthAM = d.pulleyJoint.lengthAM;
            s.pulleyLengthBM = d.pulleyJoint.lengthBM;
            s.pulleyRatio = d.pulleyJoint.ratio;
        }
        if (d.gearJoint != null) {
            s.hasGearJoint = true;
            s.gearJoint1Eid = d.gearJoint.joint1Eid;
            s.gearJoint2Eid = d.gearJoint.joint2Eid;
            s.gearRatio = d.gearJoint.ratio;
        }
        GenericEntityInitializer init = new GenericEntityInitializer(world).applySnapshotData(s);
        return new EntityGraphEntry(d.sourceEntityId, init);
    }

    private static float[] copyFloatArray(float[] source, int wantedLength) {
        int n = Math.max(0, wantedLength);
        float[] out = new float[n];
        if (source != null && n > 0) {
            System.arraycopy(source, 0, out, 0, Math.min(source.length, n));
        }
        return out;
    }

    private static int[] copyIntArray(int[] source) {
        if (source == null || source.length == 0) return new int[0];
        int[] out = new int[source.length];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }
}
