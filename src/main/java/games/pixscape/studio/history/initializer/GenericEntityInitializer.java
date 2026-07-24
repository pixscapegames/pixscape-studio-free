package games.pixscape.studio.history.initializer;

import games.pixscape.runtime.physics.PhysicsShapeData;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.model.EntityKind;


/**
 * Generic initializer for simple sprite entities:
 * - Transform / EntityIndex / Meta / Visibility managed by AbstractCommonInitializer
 * - + DimensionsComponent
 * - + TextureRegionComponent (runtime : UV, taille)
 * - + RenderMaterialComponent (runtime : shader, blend, textureHandle)
 * - + AssetRefComponent (serialized logical source : atlas vs standalone)
 * - + TintComponent (couleur multiplicative RGBA)
 * <p>
 * AssetRefComponent = serialized source of truth.
 * TextureRegionComponent = runtime (UV, etc.).
 * <p>
 * Usable for:
 * - DeleteEntitiesCommand (snapshot complet)
 * - CreateEntityCommand (by initializing fields manually)
 */
public class GenericEntityInitializer extends AbstractCommonInitializer {
    // --- TextureRegion (runtime) ---
    protected boolean hasTextureRegion;
    protected float u1, v1, u2, v2;
    protected int pixW, pixH;
    protected boolean textureValid;

    // --- Dimensions ---
    protected boolean hasDimensions;
    protected float dimWidth;
    protected float dimHeight;

    // --- Render material ---
    protected boolean hasRenderMaterial;
    protected int shaderIdx;
    protected int blend;
    protected int textureHandle; // runtime (snapshot for in-session undo)
    protected String materialDebugAtlasTag;

    // --- SpriteSource (serialized logical source) ---
    protected boolean hasAssetRef;
    protected int assetRefAssetId = -1;
    protected String assetRefAtlasTag;

    // --- Tint ---
    protected boolean hasTint;
    /**
     * Color encoded like TintComponent (ABGR int for now).
     */
    protected int tintRgba;

    // --- ParticleEmitter ---
    protected boolean hasParticleEmitter;
    protected String particleEffectPath;
    protected String particleAtlasTag;
    protected boolean particleLocalSpace;
    protected boolean particleAutoStart;
    protected boolean particleLooping;

    // --- Animation ---
    protected boolean hasAnimation;
    protected String animAnimation = "";
    protected float animFps = 12f;
    protected boolean animPlaying = true;
    protected boolean animLoop = true;
    protected float animStateTime = 0f;
    protected int animFrame = -1;
    protected String animCurrentClip = "";
    protected ObjectMap<String, AnimationComponent.Clip> animClips = new ObjectMap<>();

    // --- Shader Params ---
    protected boolean hasShaderParams;
    protected final Array<ShaderFloatParam> shaderFloats = new Array<>();

    // --- Spatial height ---
    protected boolean hasSpatialHeight;
    protected float spatialAltitude;
    protected float spatialHeight;

    // --- Lights ---
    protected boolean hasPointLight;
    protected boolean hasConeLight;

    // Point
    protected boolean pointEnabled = true;
    protected float pointIntensity = 1f;
    protected float pointRadius = 200f;
    protected float pointFalloff = 1.5f;
    protected float pointR = 1f, pointG = 0.9f, pointB = 0.2f;

    // Cone
    protected boolean coneEnabled = true;
    protected float coneIntensity = 1f;
    protected float coneRadius = 250f;
    protected float coneAngleDeg = 60f;
    protected float coneSoftness = 0.1f;
    protected float coneFalloff = 1.5f;
    protected float coneRotationDeg = 0f;
    protected float coneR = 1f, coneG = 0.9f, coneB = 0.2f;

    // --- Physics ---
    protected boolean hasPhysicsBody;
    protected int physBodyType = PhysicsBodyComponent.DYNAMIC;
    protected boolean physFixedRotation = false;
    protected boolean physBullet = false;
    protected boolean physAllowSleep = true;
    protected boolean physAwake = true;
    protected float physGravityScale = 1f;
    protected float physLinearDamping = 0f;
    protected float physAngularDamping = 0f;
    protected boolean physEnabled = true;

    protected boolean hasPhysicsShapes;
    protected final Array<PhysicsShapeData> physicsShapes = new Array<>();

    protected boolean hasPhysicsJoint;
    protected int jointType;
    protected int jointAEid;
    protected int jointBEid;
    protected boolean jointCollideConnected;
    protected float jointAnchorAx, jointAnchorAy, jointAnchorBx, jointAnchorBy;
    protected boolean hasDistanceJoint;
    protected float distanceLengthM, distanceFrequencyHz, distanceDampingRatio;
    protected boolean hasRevoluteJoint;
    protected boolean revoluteEnableLimit, revoluteEnableMotor;
    protected float revoluteLowerAngleRad, revoluteUpperAngleRad, revoluteMotorSpeedRad, revoluteMaxMotorTorque;
    protected boolean hasPrismaticJoint;
    protected float prismaticAxisX, prismaticAxisY, prismaticLowerTranslationM, prismaticUpperTranslationM, prismaticMotorSpeedMps, prismaticMaxMotorForce;
    protected boolean prismaticEnableLimit, prismaticEnableMotor;
    protected boolean hasWheelJoint;
    protected float wheelAxisX, wheelAxisY, wheelMotorSpeedRad, wheelMaxMotorTorque, wheelFrequencyHz, wheelDampingRatio;
    protected boolean wheelEnableMotor;
    protected boolean hasFrictionJoint;
    protected float frictionMaxForce, frictionMaxTorque;
    protected boolean hasMotorJoint;
    protected float motorLinearOffsetX, motorLinearOffsetY, motorAngularOffsetRad, motorMaxForce, motorMaxTorque, motorCorrectionFactor;
    protected boolean hasWeldJoint;
    protected float weldReferenceAngleRad, weldFrequencyHz, weldDampingRatio;
    protected boolean hasPulleyJoint;
    protected float pulleyGroundAx, pulleyGroundAy, pulleyGroundBx, pulleyGroundBy, pulleyLengthAM, pulleyLengthBM, pulleyRatio;
    protected boolean hasGearJoint;
    protected int gearJoint1Eid, gearJoint2Eid;
    protected float gearRatio;

    public GenericEntityInitializer(World world) {
        super(world);
    }

    @Override
    public void syncFrom(int e) {
        super.syncFrom(e);

        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<DimensionsComponent> mDim = world.getMapper(DimensionsComponent.class);
        ComponentMapper<RenderMaterialComponent> mMat = world.getMapper(RenderMaterialComponent.class);
        ComponentMapper<AssetRefComponent> mSrc = world.getMapper(AssetRefComponent.class);
        ComponentMapper<ShaderParamsComponent> mShaderParams = world.getMapper(ShaderParamsComponent.class);
        ComponentMapper<TintComponent> mTint = world.getMapper(TintComponent.class);
        ComponentMapper<ParticleEmitterComponent> mPE = world.getMapper(ParticleEmitterComponent.class);
        ComponentMapper<AnimationComponent> mAnim = world.getMapper(AnimationComponent.class);
        ComponentMapper<SpatialHeightComponent> mSpatialHeight = world.getMapper(SpatialHeightComponent.class);
        ComponentMapper<PointLightComponent> mPL = world.getMapper(PointLightComponent.class);
        ComponentMapper<ConeLightComponent> mCL = world.getMapper(ConeLightComponent.class);
        ComponentMapper<PhysicsBodyComponent> mPhysBody = world.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<PhysicsShapesComponent> mPhysicsShapes = world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PhysicsJointComponent> mJoint = world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsDistanceJointComponent> mDist = world.getMapper(PhysicsDistanceJointComponent.class);
        ComponentMapper<PhysicsRevoluteJointComponent> mRev = world.getMapper(PhysicsRevoluteJointComponent.class);
        ComponentMapper<PhysicsPrismaticJointComponent> mPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
        ComponentMapper<PhysicsWheelJointComponent> mWheel = world.getMapper(PhysicsWheelJointComponent.class);
        ComponentMapper<PhysicsFrictionJointComponent> mFriction = world.getMapper(PhysicsFrictionJointComponent.class);
        ComponentMapper<PhysicsMotorJointComponent> mMotor = world.getMapper(PhysicsMotorJointComponent.class);
        ComponentMapper<PhysicsWeldJointComponent> mWeld = world.getMapper(PhysicsWeldJointComponent.class);
        ComponentMapper<PhysicsPulleyJointComponent> mPulley = world.getMapper(PhysicsPulleyJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> mGear = world.getMapper(PhysicsGearJointComponent.class);

        // --- TextureRegion (runtime) ---
        if (mTR.has(e)) {
            TextureRegionComponent tr = mTR.get(e);
            hasTextureRegion = true;

            u1 = tr.u1;
            v1 = tr.v1;
            u2 = tr.u2;
            v2 = tr.v2;
            pixW = tr.pixW;
            pixH = tr.pixH;
            textureValid = tr.valid;
        } else {
            hasTextureRegion = false;
        }
        // --- Dimensions ---
        if (mDim.has(e)) {
            DimensionsComponent d = mDim.get(e);
            hasDimensions = true;
            dimWidth = d.width;
            dimHeight = d.height;
        } else {
            hasDimensions = false;
        }

        // --- RenderMaterial (runtime) ---
        if (mMat.has(e)) {
            RenderMaterialComponent mat = mMat.get(e);
            hasRenderMaterial = true;
            shaderIdx = mat.getShaderIdx();
            blend = mat.getBlendModeId();
            textureHandle = mat.getTextureHandle();
            materialDebugAtlasTag = mat.debugAtlasTag;
        } else {
            hasRenderMaterial = false;
            materialDebugAtlasTag = null;
        }

        // --- SpriteSource (logical source, serialized) ---
        if (mSrc != null && mSrc.has(e)) {
            AssetRefComponent src = mSrc.get(e);
            hasAssetRef = true;
            assetRefAssetId = src.assetId;
            assetRefAtlasTag = src.atlasTag;
        } else {
            hasAssetRef = false;
            assetRefAssetId = -1;
            assetRefAtlasTag = null;
        }

        // --- ShaderParams ---
        if (mShaderParams != null && mShaderParams.has(e)) {
            ShaderParamsComponent params = mShaderParams.get(e);
            hasShaderParams = true;
            shaderFloats.clear();

            if (params != null && params.floats != null) {
                for (int i = 0; i < params.floats.size; i++) {
                    ShaderFloatParam param = params.floats.get(i);

                    if (param == null || param.name == null || param.name.length() == 0) {
                        continue;
                    }

                    shaderFloats.add(new ShaderFloatParam(param.name, param.value));
                }
            }
        } else {
            hasShaderParams = false;
            shaderFloats.clear();
        }

        // --- Tint ---
        if (mTint != null && mTint.has(e)) {
            TintComponent t = mTint.get(e);
            hasTint = true;
            tintRgba = t.getRgba();
        } else {
            hasTint = false;
            tintRgba = 0xFFFFFFFF; // white by default
        }

        // --- ParticleEmitter ---
        if (mPE != null && mPE.has(e)) {
            ParticleEmitterComponent p = mPE.get(e);
            hasParticleEmitter = true;
            particleEffectPath = p.effectPath;
            particleAtlasTag = p.atlasTag;
            particleLocalSpace = p.localSpace;
            particleAutoStart = p.autoStart;
            particleLooping = p.looping;
        } else {
            hasParticleEmitter = false;
            particleEffectPath = null;
            particleAtlasTag = null;
            particleLocalSpace = true;
            particleAutoStart = true;
            particleLooping = true;
        }

        // --- Animation ---
        if (mAnim.has(e)) {
            AnimationComponent ac = mAnim.get(e);
            hasAnimation = true;

            animAnimation = ac.animation;
            animFps = ac.fps;
            animPlaying = ac.playing;
            animLoop = ac.loop;
            animStateTime = ac.stateTime;
            animFrame = ac.frame;
            animCurrentClip = ac.currentClip;

            animClips.clear();
            if (ac.clips != null) {
                for (ObjectMap.Entry<String, AnimationComponent.Clip> it : ac.clips) {
                    AnimationComponent.Clip c = it.value;
                    if (it.key != null && c != null) {
                        animClips.put(it.key, copyAnimationClip(c));
                    }
                }
            }
        } else {
            hasAnimation = false;
            animClips.clear();
        }

        // --- Spatial height ---
        if (mSpatialHeight.has(e)) {
            SpatialHeightComponent spatial = mSpatialHeight.get(e);
            hasSpatialHeight = true;
            spatialAltitude = spatial.altitude;
            spatialHeight = spatial.height;
        } else {
            hasSpatialHeight = false;
            spatialAltitude = 0f;
            spatialHeight = 0f;
        }

        // --- PointLight ---
        if (mPL.has(e)) {
            var l = mPL.get(e);
            hasPointLight = true;
            pointEnabled = l.enabled;
            pointIntensity = l.intensity;
            pointRadius = l.radius;
            pointFalloff = l.falloff;
            pointR = l.r;
            pointG = l.g;
            pointB = l.b;
        } else {
            hasPointLight = false;
        }

        // --- ConeLight ---
        if (mCL.has(e)) {
            var l = mCL.get(e);
            hasConeLight = true;
            coneEnabled = l.enabled;
            coneIntensity = l.intensity;
            coneRadius = l.radius;
            coneAngleDeg = l.coneAngleDeg;
            coneSoftness = l.softness;
            coneFalloff = l.falloff;
            coneRotationDeg = l.rotationDeg;
            coneR = l.r;
            coneG = l.g;
            coneB = l.b;
        } else {
            hasConeLight = false;
        }

        if (hasPointLight || hasConeLight) {
            hasTextureRegion = false;
            hasAssetRef = false;
        }

        // --- Physics body ---
        if (mPhysBody.has(e)) {
            PhysicsBodyComponent body = mPhysBody.get(e);
            hasPhysicsBody = true;
            physBodyType = body.type;
            physFixedRotation = body.fixedRotation;
            physBullet = body.bullet;
            physAllowSleep = body.allowSleep;
            physAwake = body.awake;
            physGravityScale = body.gravityScale;
            physLinearDamping = body.linearDamping;
            physAngularDamping = body.angularDamping;
            physEnabled = body.enabled;
        } else {
            hasPhysicsBody = false;
        }

        // --- Physics fixtures ---
        if (mPhysicsShapes.has(e) && mPhysicsShapes.get(e).hasShapes()) {
            PhysicsShapesComponent fixtures = mPhysicsShapes.get(e);
            hasPhysicsShapes = true;
            physicsShapes.clear();
            for (PhysicsShapeData fixture : fixtures.shapes) {
                if (fixture != null) physicsShapes.add(fixture.copy());
            }
        } else {
            hasPhysicsShapes = false;
            physicsShapes.clear();
        }

        hasPhysicsJoint = false;
        hasDistanceJoint = mDist.has(e);
        hasRevoluteJoint = mRev.has(e);
        hasPrismaticJoint = mPrism.has(e);
        hasWheelJoint = mWheel.has(e);
        hasFrictionJoint = mFriction.has(e);
        hasMotorJoint = mMotor.has(e);
        hasWeldJoint = mWeld.has(e);
        hasPulleyJoint = mPulley.has(e);
        hasGearJoint = mGear.has(e);
        PhysicsJointComponent base = mJoint.getSafe(e, null);
        if (base != null) {
            hasPhysicsJoint = true;
            jointType = base.type;
            jointAEid = base.aEid;
            jointBEid = base.bEid;
            jointCollideConnected = base.collideConnected;
            jointAnchorAx = base.anchorAx;
            jointAnchorAy = base.anchorAy;
            jointAnchorBx = base.anchorBx;
            jointAnchorBy = base.anchorBy;
        }
        if (hasDistanceJoint) {
            PhysicsDistanceJointComponent c = mDist.get(e);
            distanceLengthM = c.lengthM;
            distanceFrequencyHz = c.frequencyHz;
            distanceDampingRatio = c.dampingRatio;
        }
        if (hasRevoluteJoint) {
            PhysicsRevoluteJointComponent c = mRev.get(e);
            revoluteEnableLimit = c.enableLimit;
            revoluteLowerAngleRad = c.lowerAngleRad;
            revoluteUpperAngleRad = c.upperAngleRad;
            revoluteEnableMotor = c.enableMotor;
            revoluteMotorSpeedRad = c.motorSpeedRad;
            revoluteMaxMotorTorque = c.maxMotorTorque;
        }
        if (hasPrismaticJoint) {
            PhysicsPrismaticJointComponent c = mPrism.get(e);
            prismaticAxisX = c.axisX;
            prismaticAxisY = c.axisY;
            prismaticEnableLimit = c.enableLimit;
            prismaticLowerTranslationM = c.lowerTranslationM;
            prismaticUpperTranslationM = c.upperTranslationM;
            prismaticEnableMotor = c.enableMotor;
            prismaticMotorSpeedMps = c.motorSpeedMps;
            prismaticMaxMotorForce = c.maxMotorForce;
        }
        if (hasWheelJoint) {
            PhysicsWheelJointComponent c = mWheel.get(e);
            wheelAxisX = c.axisX;
            wheelAxisY = c.axisY;
            wheelEnableMotor = c.enableMotor;
            wheelMotorSpeedRad = c.motorSpeedRad;
            wheelMaxMotorTorque = c.maxMotorTorque;
            wheelFrequencyHz = c.frequencyHz;
            wheelDampingRatio = c.dampingRatio;
        }
        if (hasFrictionJoint) {
            PhysicsFrictionJointComponent c = mFriction.get(e);
            frictionMaxForce = c.maxForce;
            frictionMaxTorque = c.maxTorque;
        }
        if (hasMotorJoint) {
            PhysicsMotorJointComponent c = mMotor.get(e);
            motorLinearOffsetX = c.linearOffsetX;
            motorLinearOffsetY = c.linearOffsetY;
            motorAngularOffsetRad = c.angularOffsetRad;
            motorMaxForce = c.maxForce;
            motorMaxTorque = c.maxTorque;
            motorCorrectionFactor = c.correctionFactor;
        }
        if (hasWeldJoint) {
            PhysicsWeldJointComponent c = mWeld.get(e);
            weldReferenceAngleRad = c.referenceAngleRad;
            weldFrequencyHz = c.frequencyHz;
            weldDampingRatio = c.dampingRatio;
        }
        if (hasPulleyJoint) {
            PhysicsPulleyJointComponent c = mPulley.get(e);
            pulleyGroundAx = c.groundAx;
            pulleyGroundAy = c.groundAy;
            pulleyGroundBx = c.groundBx;
            pulleyGroundBy = c.groundBy;
            pulleyLengthAM = c.lengthAM;
            pulleyLengthBM = c.lengthBM;
            pulleyRatio = c.ratio;
        }
        if (hasGearJoint) {
            PhysicsGearJointComponent c = mGear.get(e);
            gearJoint1Eid = c.joint1Eid;
            gearJoint2Eid = c.joint2Eid;
            gearRatio = c.ratio;
        }

    }

    @Override
    public void init(int e) {
        super.init(e);

        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<DimensionsComponent> mDim = world.getMapper(DimensionsComponent.class);
        ComponentMapper<RenderMaterialComponent> mMat = world.getMapper(RenderMaterialComponent.class);
        ComponentMapper<AssetRefComponent> mSrc = world.getMapper(AssetRefComponent.class);
        ComponentMapper<ShaderParamsComponent> mShaderParams = world.getMapper(ShaderParamsComponent.class);
        ComponentMapper<TintComponent> mTint = world.getMapper(TintComponent.class);
        ComponentMapper<ParticleEmitterComponent> mPE = world.getMapper(ParticleEmitterComponent.class);
        ComponentMapper<AnimationComponent> mAnim = world.getMapper(AnimationComponent.class);
        ComponentMapper<SpatialHeightComponent> mSpatialHeight = world.getMapper(SpatialHeightComponent.class);
        ComponentMapper<PointLightComponent> mPL = world.getMapper(PointLightComponent.class);
        ComponentMapper<ConeLightComponent> mCL = world.getMapper(ConeLightComponent.class);
        ComponentMapper<PhysicsBodyComponent> mPhysBody = world.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<PhysicsShapesComponent> mPhysicsShapes = world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PhysicsJointComponent> mJoint = world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsDistanceJointComponent> mDist = world.getMapper(PhysicsDistanceJointComponent.class);
        ComponentMapper<PhysicsRevoluteJointComponent> mRev = world.getMapper(PhysicsRevoluteJointComponent.class);
        ComponentMapper<PhysicsPrismaticJointComponent> mPrism = world.getMapper(PhysicsPrismaticJointComponent.class);
        ComponentMapper<PhysicsWheelJointComponent> mWheel = world.getMapper(PhysicsWheelJointComponent.class);
        ComponentMapper<PhysicsFrictionJointComponent> mFriction = world.getMapper(PhysicsFrictionJointComponent.class);
        ComponentMapper<PhysicsMotorJointComponent> mMotor = world.getMapper(PhysicsMotorJointComponent.class);
        ComponentMapper<PhysicsWeldJointComponent> mWeld = world.getMapper(PhysicsWeldJointComponent.class);
        ComponentMapper<PhysicsPulleyJointComponent> mPulley = world.getMapper(PhysicsPulleyJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> mGear = world.getMapper(PhysicsGearJointComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);


        // --- TextureRegion (runtime) ---
        if (hasTextureRegion) {
            TextureRegionComponent tr = mTR.has(e) ? mTR.get(e) : mTR.create(e);
            tr.u1 = u1;
            tr.v1 = v1;
            tr.u2 = u2;
            tr.v2 = v2;
            tr.pixW = pixW;
            tr.pixH = pixH;
            tr.valid = textureValid;
            if (dirty != null) dirty.material(e);
        }

        // --- Dimensions ---
        if (hasDimensions) {
            DimensionsComponent d = mDim.has(e) ? mDim.get(e) : mDim.create(e);
            d.width = dimWidth;
            d.height = dimHeight;
            if (dirty != null) dirty.geometry(e, GeometryDirty.SIZE);
        }

        // --- RenderMaterial (runtime) ---
        if (hasRenderMaterial) {
            RenderMaterialComponent mat = mMat.has(e) ? mMat.get(e) : mMat.create(e);
            mat.shaderIdx = shaderIdx;
            mat.blendModeId = blend;
            mat.textureHandle = textureHandle;
            mat.debugAtlasTag = materialDebugAtlasTag;
            if (dirty != null) dirty.material(e);
        }

        // --- SpriteSource (source logique) ---
        if (hasAssetRef && mSrc != null) {
            AssetRefComponent src = mSrc.has(e) ? mSrc.get(e) : mSrc.create(e);
            src.assetId = assetRefAssetId;
            src.atlasTag = (assetRefAtlasTag != null) ? assetRefAtlasTag : "main";
        }

        // --- ShaderParams ---
        if (hasShaderParams && mShaderParams != null) {
            ShaderParamsComponent params = mShaderParams.has(e) ? mShaderParams.get(e) : mShaderParams.create(e);
            if (params.floats == null) {
                params.floats = new Array<>();
            } else {
                params.floats.clear();
            }
            for (int i = 0; i < shaderFloats.size; i++) {
                ShaderFloatParam param = shaderFloats.get(i);
                if (param == null || param.name == null || param.name.length() == 0) {
                    continue;
                }
                params.floats.add(new ShaderFloatParam(param.name, param.value));
            }
            if (dirty != null) {
                dirty.material(e);
            }
        }

        // --- Tint ---
        if (hasTint && mTint != null) {
            TintComponent t = mTint.has(e) ? mTint.get(e) : mTint.create(e);
            t.rgba = tintRgba;
            if (dirty != null) dirty.color(e);
        }

        // --- ParticleEmitter ---
        if (hasParticleEmitter && mPE != null) {
            ParticleEmitterComponent p =
                    mPE.has(e) ? mPE.get(e) : mPE.create(e);
            p.effectPath = particleEffectPath;
            p.atlasTag = particleAtlasTag;
            p.localSpace = particleLocalSpace;
            p.autoStart = particleAutoStart;
            p.looping = particleLooping;
        }

        // --- Animation ---
        if (hasAnimation) {
            AnimationComponent ac = mAnim.has(e) ? mAnim.get(e) : mAnim.create(e);

            ac.animation = (animAnimation != null) ? animAnimation : "";
            ac.fps = animFps;
            ac.playing = animPlaying;
            ac.loop = animLoop;
            ac.stateTime = animStateTime;
            ac.frame = animFrame;
            ac.currentClip = (animCurrentClip != null) ? animCurrentClip : "";

            ac.clips.clear();
            for (ObjectMap.Entry<String, AnimationComponent.Clip> it : animClips) {
                AnimationComponent.Clip c = it.value;
                if (it.key != null && c != null) {
                    ac.clips.put(it.key, copyAnimationClip(c));
                }
            }
        }

        // --- Spatial height ---
        if (hasSpatialHeight) {
            SpatialHeightComponent spatial = mSpatialHeight.has(e)
                    ? mSpatialHeight.get(e)
                    : mSpatialHeight.create(e);
            spatial.altitude = spatialAltitude;
            spatial.height = spatialHeight;
            if (dirty != null) dirty.order(e);
        }

        // --- PointLight ---
        if (hasPointLight) {
            var l = mPL.has(e) ? mPL.get(e) : mPL.create(e);
            l.enabled = pointEnabled;
            l.intensity = pointIntensity;
            l.radius = pointRadius;
            l.falloff = pointFalloff;
            l.r = pointR;
            l.g = pointG;
            l.b = pointB;

            if (dirty != null) {
                dirty.material(e);                  // shader params (falloff)
                dirty.color(e);                     // color/intensity
                dirty.geometry(e, GeometryDirty.SIZE); // radius -> size (if size is synchronized)
            }
        }

        // --- ConeLight ---
        if (hasConeLight) {
            var l = mCL.has(e) ? mCL.get(e) : mCL.create(e);
            l.enabled = coneEnabled;
            l.intensity = coneIntensity;
            l.radius = coneRadius;
            l.coneAngleDeg = coneAngleDeg;
            l.softness = coneSoftness;
            l.falloff = coneFalloff;
            l.rotationDeg = coneRotationDeg;
            l.r = coneR;
            l.g = coneG;
            l.b = coneB;

            if (dirty != null) {
                dirty.material(e);
                dirty.color(e);
                dirty.geometry(e, GeometryDirty.SIZE | GeometryDirty.ROTATION);
            }
        }

        // --- Physics body ---
        if (hasPhysicsBody) {
            PhysicsBodyComponent body = mPhysBody.has(e) ? mPhysBody.get(e) : mPhysBody.create(e);
            body.type = physBodyType;
            body.fixedRotation = physFixedRotation;
            body.bullet = physBullet;
            body.allowSleep = physAllowSleep;
            body.awake = physAwake;
            body.gravityScale = physGravityScale;
            body.linearDamping = physLinearDamping;
            body.angularDamping = physAngularDamping;
            body.enabled = physEnabled;
        }

        // --- Physics fixtures ---
        if (hasPhysicsShapes) {
            PhysicsShapesComponent fixtures = mPhysicsShapes.has(e) ? mPhysicsShapes.get(e) : mPhysicsShapes.create(e);
            fixtures.shapes.clear();
            for (PhysicsShapeData fixture : physicsShapes) {
                if (fixture != null) fixtures.shapes.add(fixture.copy());
            }
        }
        if (hasPhysicsJoint) {
            PhysicsJointComponent c = mJoint.has(e) ? mJoint.get(e) : mJoint.create(e);
            c.type = jointType;
            c.aEid = jointAEid;
            c.bEid = jointBEid;
            c.collideConnected = jointCollideConnected;
            c.anchorAx = jointAnchorAx;
            c.anchorAy = jointAnchorAy;
            c.anchorBx = jointAnchorBx;
            c.anchorBy = jointAnchorBy;
        }
        if (hasDistanceJoint) {
            PhysicsDistanceJointComponent c = mDist.has(e) ? mDist.get(e) : mDist.create(e);
            c.lengthM = distanceLengthM;
            c.frequencyHz = distanceFrequencyHz;
            c.dampingRatio = distanceDampingRatio;
        }
        if (hasRevoluteJoint) {
            PhysicsRevoluteJointComponent c = mRev.has(e) ? mRev.get(e) : mRev.create(e);
            c.enableLimit = revoluteEnableLimit;
            c.lowerAngleRad = revoluteLowerAngleRad;
            c.upperAngleRad = revoluteUpperAngleRad;
            c.enableMotor = revoluteEnableMotor;
            c.motorSpeedRad = revoluteMotorSpeedRad;
            c.maxMotorTorque = revoluteMaxMotorTorque;
        }
        if (hasPrismaticJoint) {
            PhysicsPrismaticJointComponent c = mPrism.has(e) ? mPrism.get(e) : mPrism.create(e);
            c.axisX = prismaticAxisX;
            c.axisY = prismaticAxisY;
            c.enableLimit = prismaticEnableLimit;
            c.lowerTranslationM = prismaticLowerTranslationM;
            c.upperTranslationM = prismaticUpperTranslationM;
            c.enableMotor = prismaticEnableMotor;
            c.motorSpeedMps = prismaticMotorSpeedMps;
            c.maxMotorForce = prismaticMaxMotorForce;
        }
        if (hasWheelJoint) {
            PhysicsWheelJointComponent c = mWheel.has(e) ? mWheel.get(e) : mWheel.create(e);
            c.axisX = wheelAxisX;
            c.axisY = wheelAxisY;
            c.enableMotor = wheelEnableMotor;
            c.motorSpeedRad = wheelMotorSpeedRad;
            c.maxMotorTorque = wheelMaxMotorTorque;
            c.frequencyHz = wheelFrequencyHz;
            c.dampingRatio = wheelDampingRatio;
        }
        if (hasFrictionJoint) {
            PhysicsFrictionJointComponent c = mFriction.has(e) ? mFriction.get(e) : mFriction.create(e);
            c.maxForce = frictionMaxForce;
            c.maxTorque = frictionMaxTorque;
        }
        if (hasMotorJoint) {
            PhysicsMotorJointComponent c = mMotor.has(e) ? mMotor.get(e) : mMotor.create(e);
            c.linearOffsetX = motorLinearOffsetX;
            c.linearOffsetY = motorLinearOffsetY;
            c.angularOffsetRad = motorAngularOffsetRad;
            c.maxForce = motorMaxForce;
            c.maxTorque = motorMaxTorque;
            c.correctionFactor = motorCorrectionFactor;
        }
        if (hasWeldJoint) {
            PhysicsWeldJointComponent c = mWeld.has(e) ? mWeld.get(e) : mWeld.create(e);
            c.referenceAngleRad = weldReferenceAngleRad;
            c.frequencyHz = weldFrequencyHz;
            c.dampingRatio = weldDampingRatio;
        }
        if (hasPulleyJoint) {
            PhysicsPulleyJointComponent c = mPulley.has(e) ? mPulley.get(e) : mPulley.create(e);
            c.groundAx = pulleyGroundAx;
            c.groundAy = pulleyGroundAy;
            c.groundBx = pulleyGroundBx;
            c.groundBy = pulleyGroundBy;
            c.lengthAM = pulleyLengthAM;
            c.lengthBM = pulleyLengthBM;
            c.ratio = pulleyRatio;
        }
        if (hasGearJoint) {
            PhysicsGearJointComponent c = mGear.has(e) ? mGear.get(e) : mGear.create(e);
            c.joint1Eid = gearJoint1Eid;
            c.joint2Eid = gearJoint2Eid;
            c.ratio = gearRatio;
        }

        if (dirty != null && (hasPhysicsBody || hasPhysicsShapes)) {
            dirty.physics(e, PhysicsDirtyBits.ALL);
        }


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
        if (source == null || source.length == 0) {
            return new int[0];
        }

        int[] out = new int[source.length];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }

    private static AnimationComponent.Clip copyAnimationClip(AnimationComponent.Clip source) {
        AnimationComponent.Clip copy = new AnimationComponent.Clip(source.start, source.end);
        copy.flipX = source.flipX;
        return copy;
    }

    @Override
    public String label() {
        return "GenericEntity";
    }

    public GenericEntitySnapshotData toSnapshotData(int sourceEntityId) {
        GenericEntitySnapshotData out = new GenericEntitySnapshotData();
        out.sourceEntityId = sourceEntityId;
        out.hasTransform = hasTransform;
        out.x = trX;
        out.y = trY;
        out.rotationRad = trRotationRad;
        out.scaleX = trScaleX;
        out.scaleY = trScaleY;
        out.originX = trOriginX;
        out.originY = trOriginY;
        out.hasEntityIndex = hasEntityIndex;
        out.layerIndex = entityLayerIndex;
        out.zIndex = entityZIndex;
        out.hasMeta = hasMeta;
        out.metaKind = metaKind != null ? metaKind.name() : null;
        out.hasIdentity = hasIdentity;
        out.identityName = identityName;
        out.hasVisibility = hasVisibility;
        out.visible = visible;
        out.hasAabb = hasAabb;
        out.hasObb = hasObb;
        out.hasDimensions = hasDimensions;
        out.dimensionsWidth = dimWidth;
        out.dimensionsHeight = dimHeight;
        out.hasTextureRegion = hasTextureRegion;
        out.textureU1 = u1;
        out.textureV1 = v1;
        out.textureU2 = u2;
        out.textureV2 = v2;
        out.texturePixW = pixW;
        out.texturePixH = pixH;
        out.textureValid = textureValid;
        out.hasRenderMaterial = hasRenderMaterial;
        out.materialShaderIdx = shaderIdx;
        out.materialBlendModeId = blend;
        out.materialTextureHandle = textureHandle;
        out.materialDebugAtlasTag = materialDebugAtlasTag;
        out.hasAssetRef = hasAssetRef;
        out.assetRefAssetId = assetRefAssetId;
        out.assetRefAtlasTag = assetRefAtlasTag;
        out.hasTint = hasTint;
        out.tintRgba = tintRgba;
        out.hasAnimation = hasAnimation;
        out.animationName = animAnimation;
        out.animationFps = animFps;
        out.animationPlaying = animPlaying;
        out.animationLoop = animLoop;
        out.animationStateTime = animStateTime;
        out.animationFrame = animFrame;
        out.animationCurrentClip = animCurrentClip;
        out.animationClips.clear();
        for (ObjectMap.Entry<String, AnimationComponent.Clip> it : animClips) {
            AnimationComponent.Clip c = it.value;
            if (it.key != null && c != null) {
                out.animationClips.put(it.key, copyAnimationClip(c));
            }
        }
        out.hasShaderParams = hasShaderParams;
        out.shaderFloats.clear();

        for (int i = 0; i < shaderFloats.size; i++) {
            ShaderFloatParam param = shaderFloats.get(i);

            if (param == null || param.name == null || param.name.length() == 0) {
                continue;
            }

            out.shaderFloats.add(new ShaderFloatParam(param.name, param.value));
        }
        out.hasSpatialHeight = hasSpatialHeight;
        out.spatialAltitude = spatialAltitude;
        out.spatialHeight = spatialHeight;
        out.hasPhysicsBody = hasPhysicsBody;
        out.bodyType = physBodyType;
        out.fixedRotation = physFixedRotation;
        out.bullet = physBullet;
        out.allowSleep = physAllowSleep;
        out.awake = physAwake;
        out.gravityScale = physGravityScale;
        out.linearDamping = physLinearDamping;
        out.angularDamping = physAngularDamping;
        out.bodyEnabled = physEnabled;
        for (PhysicsShapeData fixture : physicsShapes) {
            if (fixture != null) out.shapes.add(fixture.copy());
        }
        out.hasJoint = hasPhysicsJoint;
        out.jointType = jointType;
        out.jointAEid = jointAEid;
        out.jointBEid = jointBEid;
        out.jointCollideConnected = jointCollideConnected;
        out.jointAnchorAx = jointAnchorAx;
        out.jointAnchorAy = jointAnchorAy;
        out.jointAnchorBx = jointAnchorBx;
        out.jointAnchorBy = jointAnchorBy;
        out.hasDistanceJoint = hasDistanceJoint;
        out.distanceLengthM = distanceLengthM;
        out.distanceFrequencyHz = distanceFrequencyHz;
        out.distanceDampingRatio = distanceDampingRatio;
        out.hasRevoluteJoint = hasRevoluteJoint;
        out.revoluteEnableLimit = revoluteEnableLimit;
        out.revoluteLowerAngleRad = revoluteLowerAngleRad;
        out.revoluteUpperAngleRad = revoluteUpperAngleRad;
        out.revoluteEnableMotor = revoluteEnableMotor;
        out.revoluteMotorSpeedRad = revoluteMotorSpeedRad;
        out.revoluteMaxMotorTorque = revoluteMaxMotorTorque;
        out.hasPrismaticJoint = hasPrismaticJoint;
        out.prismaticAxisX = prismaticAxisX;
        out.prismaticAxisY = prismaticAxisY;
        out.prismaticEnableLimit = prismaticEnableLimit;
        out.prismaticLowerTranslationM = prismaticLowerTranslationM;
        out.prismaticUpperTranslationM = prismaticUpperTranslationM;
        out.prismaticEnableMotor = prismaticEnableMotor;
        out.prismaticMotorSpeedMps = prismaticMotorSpeedMps;
        out.prismaticMaxMotorForce = prismaticMaxMotorForce;
        out.hasWheelJoint = hasWheelJoint;
        out.wheelAxisX = wheelAxisX;
        out.wheelAxisY = wheelAxisY;
        out.wheelEnableMotor = wheelEnableMotor;
        out.wheelMotorSpeedRad = wheelMotorSpeedRad;
        out.wheelMaxMotorTorque = wheelMaxMotorTorque;
        out.wheelFrequencyHz = wheelFrequencyHz;
        out.wheelDampingRatio = wheelDampingRatio;
        out.hasFrictionJoint = hasFrictionJoint;
        out.frictionMaxForce = frictionMaxForce;
        out.frictionMaxTorque = frictionMaxTorque;
        out.hasMotorJoint = hasMotorJoint;
        out.motorLinearOffsetX = motorLinearOffsetX;
        out.motorLinearOffsetY = motorLinearOffsetY;
        out.motorAngularOffsetRad = motorAngularOffsetRad;
        out.motorMaxForce = motorMaxForce;
        out.motorMaxTorque = motorMaxTorque;
        out.motorCorrectionFactor = motorCorrectionFactor;
        out.hasWeldJoint = hasWeldJoint;
        out.weldReferenceAngleRad = weldReferenceAngleRad;
        out.weldFrequencyHz = weldFrequencyHz;
        out.weldDampingRatio = weldDampingRatio;
        out.hasPulleyJoint = hasPulleyJoint;
        out.pulleyGroundAx = pulleyGroundAx;
        out.pulleyGroundAy = pulleyGroundAy;
        out.pulleyGroundBx = pulleyGroundBx;
        out.pulleyGroundBy = pulleyGroundBy;
        out.pulleyLengthAM = pulleyLengthAM;
        out.pulleyLengthBM = pulleyLengthBM;
        out.pulleyRatio = pulleyRatio;
        out.hasGearJoint = hasGearJoint;
        out.gearJoint1Eid = gearJoint1Eid;
        out.gearJoint2Eid = gearJoint2Eid;
        out.gearRatio = gearRatio;
        return out;
    }

    public GenericEntityInitializer applySnapshotData(GenericEntitySnapshotData in) {
        hasTransform = in.hasTransform;
        trX = in.x;
        trY = in.y;
        trRotationRad = in.rotationRad;
        trScaleX = in.scaleX;
        trScaleY = in.scaleY;
        trOriginX = in.originX;
        trOriginY = in.originY;
        hasEntityIndex = in.hasEntityIndex;
        entityLayerIndex = in.layerIndex;
        entityZIndex = in.zIndex;
        hasMeta = in.hasMeta;
        metaKind = in.metaKind != null ? EntityKind.valueOf(in.metaKind) : EntityKind.UNKNOWN;
        hasIdentity = in.hasIdentity;
        identityName = in.identityName;
        identityStableId = IdentityRegistry.UNASSIGNED_STABLE_ID;
        hasVisibility = in.hasVisibility;
        visible = in.visible;
        hasAabb = in.hasAabb;
        hasObb = in.hasObb;
        hasDimensions = in.hasDimensions;
        dimWidth = in.dimensionsWidth;
        dimHeight = in.dimensionsHeight;
        hasTextureRegion = in.hasTextureRegion;
        u1 = in.textureU1;
        v1 = in.textureV1;
        u2 = in.textureU2;
        v2 = in.textureV2;
        pixW = in.texturePixW;
        pixH = in.texturePixH;
        textureValid = in.textureValid;
        hasRenderMaterial = in.hasRenderMaterial;
        shaderIdx = in.materialShaderIdx;
        blend = in.materialBlendModeId;
        textureHandle = in.materialTextureHandle;
        materialDebugAtlasTag = in.materialDebugAtlasTag;
        hasAssetRef = in.hasAssetRef;
        assetRefAssetId = in.assetRefAssetId;
        assetRefAtlasTag = in.assetRefAtlasTag;
        hasTint = in.hasTint;
        tintRgba = in.tintRgba;
        hasAnimation = in.hasAnimation;
        animAnimation = in.animationName;
        animFps = in.animationFps;
        animPlaying = in.animationPlaying;
        animLoop = in.animationLoop;
        animStateTime = in.animationStateTime;
        animFrame = in.animationFrame;
        animCurrentClip = in.animationCurrentClip;
        animClips.clear();
        if (in.animationClips != null) {
            for (ObjectMap.Entry<String, AnimationComponent.Clip> it : in.animationClips) {
                AnimationComponent.Clip c = it.value;
                if (it.key != null && c != null) {
                    animClips.put(it.key, copyAnimationClip(c));
                }
            }
        }
        hasShaderParams = in.hasShaderParams;
        shaderFloats.clear();
        for (int i = 0; i < in.shaderFloats.size; i++) {
            ShaderFloatParam param = in.shaderFloats.get(i);
            if (param == null || param.name == null || param.name.length() == 0) {
                continue;
            }
            shaderFloats.add(new ShaderFloatParam(param.name, param.value));
        }
        hasSpatialHeight = in.hasSpatialHeight;
        spatialAltitude = in.spatialAltitude;
        spatialHeight = in.spatialHeight;
        capturedZIndex = in.hasEntityIndex;
        hasPhysicsBody = in.hasPhysicsBody;
        physBodyType = in.bodyType;
        physFixedRotation = in.fixedRotation;
        physBullet = in.bullet;
        physAllowSleep = in.allowSleep;
        physAwake = in.awake;
        physGravityScale = in.gravityScale;
        physLinearDamping = in.linearDamping;
        physAngularDamping = in.angularDamping;
        physEnabled = in.bodyEnabled;
        physicsShapes.clear();
        if (in.shapes != null) {
            for (PhysicsShapeData fixture : in.shapes) {
                if (fixture != null) physicsShapes.add(fixture.copy());
            }
        }
        hasPhysicsShapes = in.shapes != null && in.shapes.size > 0;
        hasPhysicsJoint = in.hasJoint;
        jointType = in.jointType;
        jointAEid = in.jointAEid;
        jointBEid = in.jointBEid;
        jointCollideConnected = in.jointCollideConnected;
        jointAnchorAx = in.jointAnchorAx;
        jointAnchorAy = in.jointAnchorAy;
        jointAnchorBx = in.jointAnchorBx;
        jointAnchorBy = in.jointAnchorBy;
        hasDistanceJoint = in.hasDistanceJoint;
        distanceLengthM = in.distanceLengthM;
        distanceFrequencyHz = in.distanceFrequencyHz;
        distanceDampingRatio = in.distanceDampingRatio;
        hasRevoluteJoint = in.hasRevoluteJoint;
        revoluteEnableLimit = in.revoluteEnableLimit;
        revoluteLowerAngleRad = in.revoluteLowerAngleRad;
        revoluteUpperAngleRad = in.revoluteUpperAngleRad;
        revoluteEnableMotor = in.revoluteEnableMotor;
        revoluteMotorSpeedRad = in.revoluteMotorSpeedRad;
        revoluteMaxMotorTorque = in.revoluteMaxMotorTorque;
        hasPrismaticJoint = in.hasPrismaticJoint;
        prismaticAxisX = in.prismaticAxisX;
        prismaticAxisY = in.prismaticAxisY;
        prismaticEnableLimit = in.prismaticEnableLimit;
        prismaticLowerTranslationM = in.prismaticLowerTranslationM;
        prismaticUpperTranslationM = in.prismaticUpperTranslationM;
        prismaticEnableMotor = in.prismaticEnableMotor;
        prismaticMotorSpeedMps = in.prismaticMotorSpeedMps;
        prismaticMaxMotorForce = in.prismaticMaxMotorForce;
        hasWheelJoint = in.hasWheelJoint;
        wheelAxisX = in.wheelAxisX;
        wheelAxisY = in.wheelAxisY;
        wheelEnableMotor = in.wheelEnableMotor;
        wheelMotorSpeedRad = in.wheelMotorSpeedRad;
        wheelMaxMotorTorque = in.wheelMaxMotorTorque;
        wheelFrequencyHz = in.wheelFrequencyHz;
        wheelDampingRatio = in.wheelDampingRatio;
        hasFrictionJoint = in.hasFrictionJoint;
        frictionMaxForce = in.frictionMaxForce;
        frictionMaxTorque = in.frictionMaxTorque;
        hasMotorJoint = in.hasMotorJoint;
        motorLinearOffsetX = in.motorLinearOffsetX;
        motorLinearOffsetY = in.motorLinearOffsetY;
        motorAngularOffsetRad = in.motorAngularOffsetRad;
        motorMaxForce = in.motorMaxForce;
        motorMaxTorque = in.motorMaxTorque;
        motorCorrectionFactor = in.motorCorrectionFactor;
        hasWeldJoint = in.hasWeldJoint;
        weldReferenceAngleRad = in.weldReferenceAngleRad;
        weldFrequencyHz = in.weldFrequencyHz;
        weldDampingRatio = in.weldDampingRatio;
        hasPulleyJoint = in.hasPulleyJoint;
        pulleyGroundAx = in.pulleyGroundAx;
        pulleyGroundAy = in.pulleyGroundAy;
        pulleyGroundBx = in.pulleyGroundBx;
        pulleyGroundBy = in.pulleyGroundBy;
        pulleyLengthAM = in.pulleyLengthAM;
        pulleyLengthBM = in.pulleyLengthBM;
        pulleyRatio = in.pulleyRatio;
        hasGearJoint = in.hasGearJoint;
        gearJoint1Eid = in.gearJoint1Eid;
        gearJoint2Eid = in.gearJoint2Eid;
        gearRatio = in.gearRatio;
        return this;
    }

    public GenericEntityInitializer setIdentityName(String name) {
        this.hasIdentity = true;
        this.identityName = name;
        return this;
    }

    /**
     * Allows explicitly configuring a tint before init if needed.
     */
    public GenericEntityInitializer setTintRgba(int abgr) {
        this.hasTint = true;
        this.tintRgba = abgr;
        return this;
    }

    /**
     * Sprite from an atlas (standard packed flow).
     */
    public GenericEntityInitializer configureSprite(
            int assetId,
            String atlasTag,
            String atlasRegion,
            float u1, float v1, float u2, float v2,
            int pixW, int pixH,
            float worldX, float worldY,
            float originX, float originY,
            int shaderIdx, int blend, int textureHandle,
            String identityName,
            int layerIndex
    ) {
        if (assetId < 0) {
            throw new IllegalArgumentException("configureSprite requires valid assetId");
        }

        this.hasTransform = true;
        this.trX = worldX;
        this.trY = worldY;
        this.trRotationRad = 0f;
        this.trScaleX = 1f;
        this.trScaleY = 1f;
        this.trOriginX = originX;
        this.trOriginY = originY;

        this.hasEntityIndex = true;
        this.entityLayerIndex = layerIndex;
        this.entityZIndex = 0;
        this.capturedZIndex = false;

        this.hasMeta = true;
        this.metaKind = EntityKind.SPRITE;

        this.hasIdentity = true;
        this.identityName = identityName;

        this.hasVisibility = true;
        this.visible = true;

        this.hasTextureRegion = true;
        this.u1 = u1;
        this.v1 = v1;
        this.u2 = u2;
        this.v2 = v2;
        this.pixW = pixW;
        this.pixH = pixH;
        this.textureValid = true;

        this.hasDimensions = true;
        this.dimWidth = pixW;
        this.dimHeight = pixH;

        this.hasRenderMaterial = true;
        this.shaderIdx = shaderIdx;
        this.blend = blend;
        this.textureHandle = textureHandle;

        this.hasAabb = true;
        this.hasObb = true;

        this.hasAssetRef = true;
        this.assetRefAssetId = assetId;
        this.assetRefAtlasTag = atlasTag;

        this.hasTint = true;
        this.tintRgba = 0xFFFFFFFF;

        return this;
    }

    /**
     * Sprite standalone : texture brute (orig/images/...), UV = [0..1].
     */
    public GenericEntityInitializer configureStandaloneSprite(
            int assetId,
            String atlasTag,
            int pixW, int pixH,
            float worldX, float worldY,
            float originX, float originY,
            int shaderIdx, int blend, int textureHandle,
            String identityName,
            int layerIndex
    ) {
        // Transform
        this.hasTransform = true;
        this.trX = worldX;
        this.trY = worldY;
        this.trRotationRad = 0f;
        this.trScaleX = 1f;
        this.trScaleY = 1f;
        this.trOriginX = originX;
        this.trOriginY = originY;

        // EntityIndex + meta
        this.hasEntityIndex = true;
        this.entityLayerIndex = layerIndex;
        this.entityZIndex = 0;
        this.capturedZIndex = false;

        this.hasMeta = true;
        this.metaKind = EntityKind.SPRITE;

        this.hasIdentity = true;
        this.identityName = identityName;

        // Visibility
        this.hasVisibility = true;
        this.visible = true;

        // TextureRegion (standalone: UV complets, snapshot runtime)
        this.hasTextureRegion = true;
        this.u1 = 0f;
        this.v1 = 0f;
        this.u2 = 1f;
        this.v2 = 1f;
        this.pixW = pixW;
        this.pixH = pixH;
        this.textureValid = true;

        // Dimensions
        this.hasDimensions = true;
        this.dimWidth = pixW;
        this.dimHeight = pixH;

        // RenderMaterial
        this.hasRenderMaterial = true;
        this.shaderIdx = shaderIdx;
        this.blend = blend;
        this.textureHandle = textureHandle;

        // Bounds
        this.hasAabb = true;
        this.hasObb = true;

        // SpriteSource (standalone) = **logical source of truth**
        this.hasAssetRef = true;
        this.assetRefAssetId = assetId;
        this.assetRefAtlasTag = atlasTag;

        // Default tint: white
        this.hasTint = true;
        this.tintRgba = 0xFFFFFFFF;

        return this;
    }

    public GenericEntityInitializer configureAnimation(
            String animation,
            String currentClip,
            float fps,
            boolean loop,
            ObjectMap<String, AnimationComponent.Clip> clips
    ) {
        this.hasAnimation = true;
        this.metaKind = EntityKind.ANIMATION;
        this.animAnimation = (animation != null) ? animation : "";
        this.animFps = (fps > 0f) ? fps : 12f;
        this.animLoop = loop;
        this.animPlaying = true;
        this.animStateTime = 0f;
        this.animFrame = -1;
        this.animCurrentClip = (currentClip != null) ? currentClip : "";

        this.animClips.clear();
        if (clips != null) {
            for (ObjectMap.Entry<String, AnimationComponent.Clip> it : clips) {
                if (it.key != null && it.value != null) {
                    this.animClips.put(it.key, copyAnimationClip(it.value));
                }
            }
        }

        return this;
    }


    /**
     * Configures a particle emitter entity based on a .p file.
     * No TextureRegion / Dimensions / RenderMaterial here: ParticleEffect handles everything.
     */
    public GenericEntityInitializer configureParticleEmitter(
            String effectPath,
            String atlasTag,
            float worldX,
            float worldY,
            int layerIndex,
            String identityName
    ) {
        // Transform de base
        this.hasTransform = true;
        this.trX = worldX;
        this.trY = worldY;
        this.trRotationRad = 0f;
        this.trScaleX = 1f;
        this.trScaleY = 1f;
        this.trOriginX = 0f;
        this.trOriginY = 0f;

        this.hasDimensions = true;
        this.dimHeight = 50f;
        this.dimWidth = 50f;

        this.hasAabb = true;
        this.hasObb = true;

        // EntityIndex + meta
        this.hasEntityIndex = true;
        this.entityLayerIndex = layerIndex;
        this.entityZIndex = 0;
        this.capturedZIndex = false;

        this.hasMeta = true;
        this.metaKind = EntityKind.PARTICLE;

        this.hasIdentity = true;
        this.identityName = identityName;

        // Visibility
        this.hasVisibility = true;
        this.visible = true;

        // ParticleEmitter
        this.hasParticleEmitter = true;
        this.particleEffectPath = effectPath;
        this.particleAtlasTag = atlasTag;
        this.particleLocalSpace = true;
        this.particleAutoStart = true;
        this.particleLooping = true;

        return this;
    }

    public GenericEntityInitializer configurePointLightProcedural(
            float worldX, float worldY,
            int shaderIdx, int blend, int textureHandle,
            int layerIndex,
            String identityName,
            float radius,
            float intensity,
            float falloff,
            float r, float g, float b
    ) {
        // dimensions = diameter (the actual quad size in world units)
        float d = Math.max(0f, radius) * 2f;

        // ✅ origin consistent with the quad size
        float originX = d * 0.5f;
        float originY = d * 0.5f;

        // Transform
        this.hasTransform = true;
        this.trX = worldX;
        this.trY = worldY;
        this.trRotationRad = 0f;
        this.trScaleX = 1f;
        this.trScaleY = 1f;
        this.trOriginX = originX;
        this.trOriginY = originY;

        // EntityIndex + meta
        this.hasEntityIndex = true;
        this.entityLayerIndex = layerIndex;
        this.entityZIndex = 0;
        this.capturedZIndex = false;

        this.hasMeta = true;
        this.metaKind = EntityKind.POINT_LIGHT;

        this.hasIdentity = true;
        this.identityName = identityName;

        // Visibility
        this.hasVisibility = true;
        this.visible = true;

        // dimensions du quad
        this.hasDimensions = true;
        this.dimWidth = d;
        this.dimHeight = d;

        // and the Transform origin must also remain consistent
        this.trOriginX = originX;
        this.trOriginY = originY;

        // RenderMaterial
        this.hasRenderMaterial = true;
        this.shaderIdx = shaderIdx;
        this.blend = blend;
        this.textureHandle = textureHandle;
        this.hasTextureRegion = false;
        this.hasAssetRef = false;

        // Bounds
        this.hasAabb = true;
        this.hasObb = true;

        // light data
        this.hasPointLight = true;
        this.hasConeLight = false;

        this.pointEnabled = true;
        this.pointRadius = Math.max(0f, radius);
        this.pointIntensity = Math.max(0f, intensity);
        this.pointFalloff = Math.max(0f, falloff);
        this.pointR = r;
        this.pointG = g;
        this.pointB = b;

        this.hasTint = false;

        return this;
    }


    public GenericEntityInitializer configureConeLightProcedural(
            float worldX, float worldY,
            float rotationRad,
            int shaderIdx, int blend, int textureHandle,
            int layerIndex,
            String identityName,
            float radius,
            float intensity,
            float coneAngleDeg,
            float softness,
            float falloff,
            float r, float g, float b
    ) {
        float d = Math.max(0f, radius) * 2f;
        float originX = d * 0.5f;
        float originY = d * 0.5f;

        // Transform
        this.hasTransform = true;
        this.trX = worldX;
        this.trY = worldY;
        this.trRotationRad = rotationRad;
        this.trScaleX = 1f;
        this.trScaleY = 1f;
        this.trOriginX = originX;
        this.trOriginY = originY;

        this.metaKind = EntityKind.CONE_LIGHT;

        this.hasEntityIndex = true;
        this.entityLayerIndex = layerIndex;
        this.entityZIndex = 0;
        this.capturedZIndex = false;

        this.hasMeta = true;
        this.hasIdentity = true;
        this.identityName = identityName;

        // dimensions = diameter
        this.hasDimensions = true;
        this.dimWidth = d;
        this.dimHeight = d;

        // Visibility
        this.hasVisibility = true;
        this.visible = true;

        // RenderMaterial
        this.hasRenderMaterial = true;
        this.shaderIdx = shaderIdx;
        this.blend = blend;
        this.textureHandle = textureHandle;
        this.hasTextureRegion = false;
        this.hasAssetRef = false;

        // Bounds
        this.hasAabb = true;
        this.hasObb = true;

        this.hasConeLight = true;
        this.hasPointLight = false;

        this.coneEnabled = true;
        this.coneRadius = Math.max(0f, radius);
        this.coneIntensity = Math.max(0f, intensity);
        this.coneAngleDeg = coneAngleDeg;
        this.coneSoftness = softness;
        this.coneFalloff = falloff;
        this.coneRotationDeg = rotationRad * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
        this.coneR = r;
        this.coneG = g;
        this.coneB = b;

        this.hasTint = false;

        return this;
    }

    public GenericEntityInitializer setPosition(float x, float y) {
        this.hasTransform = true;
        this.trX = x;
        this.trY = y;
        return this;
    }

    public GenericEntityInitializer translate(float dx, float dy) {
        this.hasTransform = true;
        this.trX += dx;
        this.trY += dy;
        return this;
    }

    public GenericEntityInitializer allocateFreshPhysicsShapeIds(
            games.pixscape.runtime.service.PhysicsService physicsService) {
        for (int i = 0; i < physicsShapes.size; i++) {
            PhysicsShapeData shape = physicsShapes.get(i);
            if (shape != null) {
                shape.physicsShapeId = physicsService.allocateNewPhysicsShapeId();
            }
        }
        return this;
    }

    public GenericEntityInitializer duplicate() {
        GenericEntityInitializer copy = new GenericEntityInitializer(world);

        // --- TextureRegion ---
        copy.hasTextureRegion = this.hasTextureRegion;
        copy.u1 = this.u1;
        copy.v1 = this.v1;
        copy.u2 = this.u2;
        copy.v2 = this.v2;
        copy.pixW = this.pixW;
        copy.pixH = this.pixH;
        copy.textureValid = this.textureValid;

        // --- Dimensions ---
        copy.hasDimensions = this.hasDimensions;
        copy.dimWidth = this.dimWidth;
        copy.dimHeight = this.dimHeight;

        // --- Render material ---
        copy.hasRenderMaterial = this.hasRenderMaterial;
        copy.shaderIdx = this.shaderIdx;
        copy.blend = this.blend;
        copy.textureHandle = this.textureHandle;
        copy.materialDebugAtlasTag = this.materialDebugAtlasTag;

        // --- AssetRef ---
        copy.hasAssetRef = this.hasAssetRef;
        copy.assetRefAssetId = this.assetRefAssetId;
        copy.assetRefAtlasTag = this.assetRefAtlasTag;

        // --- Tint ---
        copy.hasTint = this.hasTint;
        copy.tintRgba = this.tintRgba;

        // --- ParticleEmitter ---
        copy.hasParticleEmitter = this.hasParticleEmitter;
        copy.particleEffectPath = this.particleEffectPath;
        copy.particleAtlasTag = this.particleAtlasTag;
        copy.particleLocalSpace = this.particleLocalSpace;
        copy.particleAutoStart = this.particleAutoStart;
        copy.particleLooping = this.particleLooping;

        // --- Animation ---
        copy.hasAnimation = this.hasAnimation;
        copy.animAnimation = this.animAnimation;
        copy.animFps = this.animFps;
        copy.animPlaying = this.animPlaying;
        copy.animLoop = this.animLoop;
        copy.animStateTime = this.animStateTime;
        copy.animFrame = this.animFrame;
        copy.animCurrentClip = this.animCurrentClip;
        copy.animClips.clear();
        for (ObjectMap.Entry<String, AnimationComponent.Clip> it : this.animClips) {
            if (it.key != null && it.value != null) {
                copy.animClips.put(it.key, copyAnimationClip(it.value));
            }
        }

        // --- Shader params ---
        copy.hasShaderParams = this.hasShaderParams;
        copy.shaderFloats.clear();
        for (int i = 0; i < this.shaderFloats.size; i++) {
            ShaderFloatParam param = this.shaderFloats.get(i);
            if (param == null || param.name == null || param.name.length() == 0) {
                continue;
            }
            copy.shaderFloats.add(new ShaderFloatParam(param.name, param.value));
        }

        // --- Spatial height ---
        copy.hasSpatialHeight = this.hasSpatialHeight;
        copy.spatialAltitude = this.spatialAltitude;
        copy.spatialHeight = this.spatialHeight;

        // --- Lights ---
        copy.hasPointLight = this.hasPointLight;
        copy.hasConeLight = this.hasConeLight;

        copy.pointEnabled = this.pointEnabled;
        copy.pointIntensity = this.pointIntensity;
        copy.pointRadius = this.pointRadius;
        copy.pointFalloff = this.pointFalloff;
        copy.pointR = this.pointR;
        copy.pointG = this.pointG;
        copy.pointB = this.pointB;

        copy.coneEnabled = this.coneEnabled;
        copy.coneIntensity = this.coneIntensity;
        copy.coneRadius = this.coneRadius;
        copy.coneAngleDeg = this.coneAngleDeg;
        copy.coneSoftness = this.coneSoftness;
        copy.coneFalloff = this.coneFalloff;
        copy.coneRotationDeg = this.coneRotationDeg;
        copy.coneR = this.coneR;
        copy.coneG = this.coneG;
        copy.coneB = this.coneB;

        // --- Physics ---
        copy.hasPhysicsBody = this.hasPhysicsBody;
        copy.physBodyType = this.physBodyType;
        copy.physFixedRotation = this.physFixedRotation;
        copy.physBullet = this.physBullet;
        copy.physAllowSleep = this.physAllowSleep;
        copy.physAwake = this.physAwake;
        copy.physGravityScale = this.physGravityScale;
        copy.physLinearDamping = this.physLinearDamping;
        copy.physAngularDamping = this.physAngularDamping;
        copy.physEnabled = this.physEnabled;

        copy.hasPhysicsShapes = this.hasPhysicsShapes;
        copy.physicsShapes.clear();
        for (PhysicsShapeData fixture : this.physicsShapes) {
            if (fixture != null) {
                copy.physicsShapes.add(fixture.copy());
            }
        }

        copy.hasPhysicsJoint = this.hasPhysicsJoint;
        copy.jointType = this.jointType;
        copy.jointAEid = this.jointAEid;
        copy.jointBEid = this.jointBEid;
        copy.jointCollideConnected = this.jointCollideConnected;
        copy.jointAnchorAx = this.jointAnchorAx;
        copy.jointAnchorAy = this.jointAnchorAy;
        copy.jointAnchorBx = this.jointAnchorBx;
        copy.jointAnchorBy = this.jointAnchorBy;
        copy.hasDistanceJoint = this.hasDistanceJoint;
        copy.distanceLengthM = this.distanceLengthM;
        copy.distanceFrequencyHz = this.distanceFrequencyHz;
        copy.distanceDampingRatio = this.distanceDampingRatio;
        copy.hasRevoluteJoint = this.hasRevoluteJoint;
        copy.revoluteEnableLimit = this.revoluteEnableLimit;
        copy.revoluteLowerAngleRad = this.revoluteLowerAngleRad;
        copy.revoluteUpperAngleRad = this.revoluteUpperAngleRad;
        copy.revoluteEnableMotor = this.revoluteEnableMotor;
        copy.revoluteMotorSpeedRad = this.revoluteMotorSpeedRad;
        copy.revoluteMaxMotorTorque = this.revoluteMaxMotorTorque;
        copy.hasPrismaticJoint = this.hasPrismaticJoint;
        copy.prismaticAxisX = this.prismaticAxisX;
        copy.prismaticAxisY = this.prismaticAxisY;
        copy.prismaticEnableLimit = this.prismaticEnableLimit;
        copy.prismaticLowerTranslationM = this.prismaticLowerTranslationM;
        copy.prismaticUpperTranslationM = this.prismaticUpperTranslationM;
        copy.prismaticEnableMotor = this.prismaticEnableMotor;
        copy.prismaticMotorSpeedMps = this.prismaticMotorSpeedMps;
        copy.prismaticMaxMotorForce = this.prismaticMaxMotorForce;
        copy.hasWheelJoint = this.hasWheelJoint;
        copy.wheelAxisX = this.wheelAxisX;
        copy.wheelAxisY = this.wheelAxisY;
        copy.wheelEnableMotor = this.wheelEnableMotor;
        copy.wheelMotorSpeedRad = this.wheelMotorSpeedRad;
        copy.wheelMaxMotorTorque = this.wheelMaxMotorTorque;
        copy.wheelFrequencyHz = this.wheelFrequencyHz;
        copy.wheelDampingRatio = this.wheelDampingRatio;
        copy.hasFrictionJoint = this.hasFrictionJoint;
        copy.frictionMaxForce = this.frictionMaxForce;
        copy.frictionMaxTorque = this.frictionMaxTorque;
        copy.hasMotorJoint = this.hasMotorJoint;
        copy.motorLinearOffsetX = this.motorLinearOffsetX;
        copy.motorLinearOffsetY = this.motorLinearOffsetY;
        copy.motorAngularOffsetRad = this.motorAngularOffsetRad;
        copy.motorMaxForce = this.motorMaxForce;
        copy.motorMaxTorque = this.motorMaxTorque;
        copy.motorCorrectionFactor = this.motorCorrectionFactor;
        copy.hasWeldJoint = this.hasWeldJoint;
        copy.weldReferenceAngleRad = this.weldReferenceAngleRad;
        copy.weldFrequencyHz = this.weldFrequencyHz;
        copy.weldDampingRatio = this.weldDampingRatio;
        copy.hasPulleyJoint = this.hasPulleyJoint;
        copy.pulleyGroundAx = this.pulleyGroundAx;
        copy.pulleyGroundAy = this.pulleyGroundAy;
        copy.pulleyGroundBx = this.pulleyGroundBx;
        copy.pulleyGroundBy = this.pulleyGroundBy;
        copy.pulleyLengthAM = this.pulleyLengthAM;
        copy.pulleyLengthBM = this.pulleyLengthBM;
        copy.pulleyRatio = this.pulleyRatio;
        copy.hasGearJoint = this.hasGearJoint;
        copy.gearJoint1Eid = this.gearJoint1Eid;
        copy.gearJoint2Eid = this.gearJoint2Eid;
        copy.gearRatio = this.gearRatio;

        // --- Fields inherited from AbstractCommonInitializer ---
        copy.hasTransform = this.hasTransform;
        copy.trX = this.trX;
        copy.trY = this.trY;
        copy.trRotationRad = this.trRotationRad;
        copy.trScaleX = this.trScaleX;
        copy.trScaleY = this.trScaleY;
        copy.trOriginX = this.trOriginX;
        copy.trOriginY = this.trOriginY;

        copy.hasEntityIndex = this.hasEntityIndex;
        copy.entityLayerIndex = this.entityLayerIndex;
        copy.entityZIndex = this.entityZIndex;
        copy.capturedZIndex = this.capturedZIndex;

        copy.hasMeta = this.hasMeta;
        copy.metaKind = this.metaKind;

        copy.hasIdentity = this.hasIdentity;
        copy.identityName = this.identityName;
        copy.identityStableId = this.identityStableId;

        copy.hasVisibility = this.hasVisibility;
        copy.visible = this.visible;

        copy.hasAabb = this.hasAabb;
        copy.hasObb = this.hasObb;

        return copy;
    }

    public PreviewVisualData toPreviewVisualData() {
        PreviewVisualData data = new PreviewVisualData();
        data.hasTransform = this.hasTransform;
        data.x = this.trX;
        data.y = this.trY;
        data.rotationRad = this.trRotationRad;
        data.scaleX = this.trScaleX;
        data.scaleY = this.trScaleY;
        data.originX = this.trOriginX;
        data.originY = this.trOriginY;
        data.hasEntityIndex = this.hasEntityIndex;
        data.zIndex = this.entityZIndex;
        data.hasDimensions = this.hasDimensions;
        data.width = this.dimWidth;
        data.height = this.dimHeight;
        data.hasAssetRef = this.hasAssetRef;
        data.assetRefAssetId = this.assetRefAssetId;
        data.hasAnimation = this.hasAnimation;
        data.animationName = this.animAnimation;
        data.hasPointLight = this.hasPointLight;
        data.hasConeLight = this.hasConeLight;
        data.hasPhysicsBody = this.hasPhysicsBody;
        data.hasPhysicsShapes = this.hasPhysicsShapes;
        data.hasPhysicsJoint = this.hasPhysicsJoint;
        return data;
    }

    public static final class PreviewVisualData {
        public boolean hasTransform;
        public float x, y, rotationRad, scaleX, scaleY, originX, originY;
        public boolean hasEntityIndex;
        public int zIndex;
        public boolean hasDimensions;
        public float width, height;
        public boolean hasAssetRef;
        public int assetRefAssetId;
        public boolean hasAnimation;
        public String animationName;
        public boolean hasPointLight, hasConeLight;
        public boolean hasPhysicsBody, hasPhysicsShapes, hasPhysicsJoint;
    }
}
