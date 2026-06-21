package games.pixscape.studio.ops;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.service.*;
import games.pixscape.studio.asset.AnimationAssetMeta;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.helper.AssetHelper;
import games.pixscape.studio.helper.RenderRebindHelper;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.*;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.GpuSnapshotManager;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.service.atlas.AtlasStudioService;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.PolygonDrawSession;
import games.pixscape.studio.service.spatial.SpatialBlockPlacementTarget;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
import games.pixscape.studio.system.AnimationFallbackSystem;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

public class EditorOpsImpl implements EditorOps {
    private final WorldCanvas canvas;
    private final World world;
    private final HistoryManager historyManager;
    private final HistoryIdRegistry historyIds;
    private final ZOrderRuntimeService zOrderRuntimeService;
    private final SelectionService selectionService;
    private final AtlasStudioService atlasStudioService;
    private final PhysicsService physicsService;
    private final PhysicsSelectionService physicsSelectionService;
    private final SpatialBlockSelectionService spatialBlockSelectionService;
    private final SpatialTileSelectionService spatialTileSelectionService;
    private final PhysicsPolygonAuthoringService polygonAuthoringService;
    private final PolygonDrawSession polygonDrawSession;
    private final IdentityRegistry identityRegistry;
    private SceneService sceneService;
    private final GpuSnapshotManager snapshotManager;
    private final String defaultShaderName;
    private AssetsChangedListener assetsChangedListener;
    private final Vector2 tmpLocal = new Vector2();

    public EditorOpsImpl(
            WorldCanvas canvas
    ) {
        this.canvas = canvas;
        this.world = canvas.getEcsWorld();
        this.historyManager = canvas.getHistoryManager();
        this.historyIds = canvas.getHistoryManager().historyIds();
        this.zOrderRuntimeService = canvas.getZOrderService();
        this.selectionService = canvas.getSelectionService();
        this.physicsSelectionService = canvas.getPhysicsSelectionService();
        this.spatialBlockSelectionService = canvas.getSpatialBlockSelectionService();
        this.spatialTileSelectionService = canvas.getSpatialTileSelectionService();
        this.identityRegistry = new IdentityRegistry();
        this.identityRegistry.bind(this.world);
        this.identityRegistry.rebuild();
        this.atlasStudioService = canvas.getAtlasService();
        this.physicsService = canvas.getPhysicsService();
        this.polygonAuthoringService = new PhysicsPolygonAuthoringService(world);
        this.polygonDrawSession = canvas.getPolygonDrawSession();
        this.snapshotManager = canvas.getGpuSnapshotManager();
        this.defaultShaderName = canvas.getDefaultShaderName();
    }

    public void setSceneService(SceneService sceneService) {
        this.sceneService = sceneService;
    }

    @Override
    public int createSpriteFromAtlas(String atlasTag, String regionPath, float worldX, float worldY) {
        return createSpriteFromAtlas(atlasTag, regionPath, worldX, worldY, null);
    }

    @Override
    public int createSpriteFromAtlas(String atlasTag, String regionPath, float worldX, float worldY, String metaName) {
        if (regionPath == null || regionPath.isEmpty()) {
            return -1;
        }

        String resolvedTag = resolveSceneAtlasTag(atlasTag);
        if (resolvedTag == null || resolvedTag.isEmpty()) {
            return -1;
        }

        TextureAtlas atlas = atlasStudioService.getAtlas(resolvedTag);
        if (atlas == null) {
            return -1;
        }

        TextureRegion region = atlas.findRegion(regionPath);
        if (region == null) {
            return -1;
        }

        int activeLayerIndex = selectionService.getActiveLayerIndex();
        int pixW = region.getRegionWidth();
        int pixH = region.getRegionHeight();

        float originX = pixW * 0.5f;
        float originY = pixH * 0.5f;

        int shaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        int textureHandle = TextureRegistry.handleOf(region.getTexture());
        ensureHandleBoundToTextureArray();
        int blend = BlendMode.ALPHA.id;

        String resolvedMetaName = (metaName != null && !metaName.isBlank()) ? metaName : regionPath;
        int assetId = AssetHelper.extractAssetIdFromRegionName(regionPath);
        if (assetId < 0)
            throw new IllegalStateException("Cannot resolve assetId for region: " + regionPath);

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureSprite(
                        assetId,
                        resolvedTag,
                        regionPath,
                        region.getU(), region.getV(), region.getU2(), region.getV2(),
                        pixW, pixH,
                        worldX, worldY,
                        originX, originY,
                        shaderIdx, blend, textureHandle,
                        resolvedMetaName,
                        activeLayerIndex
                );
        init.setIdentityStableId(allocateStableId());

        CreateEntityCommand cmd = new CreateEntityCommand(
                world,
                historyIds,
                init,
                createdEntityId -> {
                    rebindHistoryEntityRenderAssets(createdEntityId);
                    if (!init.hasCapturedZIndex()) {
                        zOrderRuntimeService.addOnTop(createdEntityId, activeLayerIndex);
                    }
                    selectionService.selectOnly(createdEntityId);
                }
        );

        historyManager.execute(cmd);
        return cmd.getCreatedEntityId();
    }

    @Override
    public int createStandaloneSprite(String relativePath, float worldX, float worldY) {
        return createStandaloneSprite(relativePath, worldX, worldY, null);
    }

    @Override
    public int createStandaloneSprite(String relativePath, float worldX, float worldY, String metaName) {

        if (relativePath == null || relativePath.isEmpty()) {
            return -1;
        }

        Texture tex = StandaloneTextureCache.get(relativePath);
        if (tex == null) {
            return -1;
        }

        int activeLayerIndex = selectionService.getActiveLayerIndex();
        int pixW = tex.getWidth();
        int pixH = tex.getHeight();

        float originX = pixW * 0.5f;
        float originY = pixH * 0.5f;

        int shaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        int textureHandle = TextureRegistry.handleOf(tex);
        int blend = BlendMode.ALPHA.id;

        String resolvedMetaName =
                (metaName != null && !metaName.isBlank()) ? metaName : relativePath;

        String sceneTag = getCurrentSceneTag();
        String sourceRelPath = StudioFs.DIR_ORIG_IMAGES + "/" + relativePath;
        int assetId = (sceneService != null) ? sceneService.resolveAssetIdBySourceRelPath(sourceRelPath) : -1;

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(
                        assetId,
                        sceneTag,
                        pixW, pixH,
                        worldX, worldY,
                        originX, originY,
                        shaderIdx, blend, textureHandle,
                        resolvedMetaName,
                        activeLayerIndex
                );
        init.setIdentityStableId(allocateStableId());

        CreateEntityCommand cmd = new CreateEntityCommand(
                world,
                historyIds,
                init,
                createdEntityId -> {
                    rebindHistoryEntityRenderAssets(createdEntityId);
                    if (!init.hasCapturedZIndex()) {
                        zOrderRuntimeService.addOnTop(createdEntityId, activeLayerIndex);
                    }
                    selectionService.selectOnly(createdEntityId);
                }
        );

        historyManager.execute(cmd);

        // --- Async atlas workflow ---
        String fullRelPath = StudioFs.DIR_ORIG_IMAGES + "/" + relativePath;
        boolean inputChanged = sceneService.ensureImageInAtlasInput(sceneTag, fullRelPath);
        boolean alreadyPacked = assetId >= 0 && atlasStudioService.isPacked(assetId, sceneTag);
        boolean packAlreadyQueuedOrRunning = atlasStudioService.hasAsyncPackQueuedOrRunningFor(sceneTag);

        if (!packAlreadyQueuedOrRunning && (inputChanged || !alreadyPacked)) {
            atlasStudioService.requestAsyncPack(sceneTag);
        }

        return cmd.getCreatedEntityId();
    }

    @Override
    public int createAnimationSprite(String animationsRelPath, float worldX, float worldY) {
        return createAnimationSprite(animationsRelPath, worldX, worldY, null);
    }

    @Override
    public int createAnimationSprite(String animationsRelPath,
                                     float worldX,
                                     float worldY,
                                     String metaName) {

        if (animationsRelPath == null || animationsRelPath.isBlank()) return -1;

        ProjectConfig cfg = ProjectConfig.getInstance();

        String animationRelPath = StudioFs.DIR_ORIG_ANIMATIONS + "/" + animationsRelPath;

        FileHandle projectRoot = StudioFs.requireStudioProjectDir(cfg);
        FileHandle animationDir = projectRoot.child(animationRelPath);
        if (!animationDir.exists() || !animationDir.isDirectory()) return -1;

        Array<FileHandle> frameFiles = new Array<>();
        for (FileHandle child : animationDir.list()) {
            if (child == null || child.isDirectory()) continue;
            String ext = child.extension() != null ? child.extension().toLowerCase() : "";
            if ("png".equals(ext)) frameFiles.add(child);
        }
        if (frameFiles.size == 0) return -1;

        frameFiles.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        FileHandle firstFrame = frameFiles.first();
        Pixmap pm = new Pixmap(firstFrame);
        int frameW = pm.getWidth();
        int frameH = pm.getHeight();
        pm.dispose();

        int frameCount = frameFiles.size;
        AnimationAssetMeta animationMeta = sceneService != null
                ? sceneService.findAnimationAssetMetaBySourceRelPath(animationRelPath)
                : null;
        float fps = animationMeta != null && animationMeta.fps > 0f ? animationMeta.fps : 12f;

        int activeLayerIndex = selectionService.getActiveLayerIndex();

        float originX = frameW * 0.5f;
        float originY = frameH * 0.5f;

        int shaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        int blend = BlendMode.ALPHA.id;

        String firstFrameRelPath = animationRelPath + "/" + firstFrame.name();

        String sceneTag = getCurrentSceneTag();
        Array<TextureAtlas.AtlasRegion> packedFrames =
                (sceneTag != null && !sceneTag.isBlank())
                        ? atlasStudioService.list(sceneTag, animationsRelPath)
                        : new Array<>();
        boolean alreadyPacked = packedFrames.size >= frameCount;

        ObjectMap<String, AnimationComponent.Clip> clipsMap = copyAnimationAssetClips(animationMeta);
        String currentClip = animationMeta != null ? animationMeta.currentClip : null;
        boolean loop = true;

        if (clipsMap.size == 0) {
            clipsMap.put("default", new AnimationComponent.Clip(0, frameCount - 1));
        }
        if (currentClip == null || currentClip.isBlank() || !clipsMap.containsKey(currentClip)) {
            currentClip = "default";
        }

        GenericEntityInitializer init = new GenericEntityInitializer(world);
        if (alreadyPacked) {
            TextureAtlas.AtlasRegion firstPackedFrame = packedFrames.first();
            int textureHandle = TextureRegistry.handleOf(firstPackedFrame.getTexture());
            int assetId = AssetHelper.extractAssetIdFromRegionName(firstPackedFrame.name);
            init.configureSprite(
                    assetId,
                    sceneTag,
                    firstPackedFrame.name,
                    firstPackedFrame.getU(),
                    firstPackedFrame.getV(),
                    firstPackedFrame.getU2(),
                    firstPackedFrame.getV2(),
                    firstPackedFrame.getRegionWidth(),
                    firstPackedFrame.getRegionHeight(),
                    worldX, worldY,
                    originX, originY,
                    shaderIdx, blend, textureHandle,
                    metaName != null ? metaName : animationRelPath,
                    activeLayerIndex
            );
        } else {
            Texture tex = StandaloneTextureCache.getOrLoadProjectRelative(firstFrameRelPath);
            if (tex == null) return -1;
            int textureHandle = TextureRegistry.handleOf(tex);

            int assetId = -1;
            if (sceneService != null) {
                // Animation assets are directory-based in assets.json (orig/animations/<animDir>),
                // not frame-based. Keep first-frame lookup as a backward-compatible fallback.
                assetId = sceneService.resolveAssetIdBySourceRelPath(animationRelPath);
                if (assetId < 0) {
                    assetId = sceneService.resolveAssetIdBySourceRelPath(firstFrameRelPath);
                }
            }
            init.configureStandaloneSprite(
                    assetId,
                    sceneTag,
                    frameW, frameH,
                    worldX, worldY,
                    originX, originY,
                    shaderIdx, blend, textureHandle,
                    metaName != null ? metaName : animationRelPath,
                    activeLayerIndex
            );
        }

        init.setIdentityStableId(allocateStableId());

        init.configureAnimation(animationsRelPath, currentClip, fps, loop, clipsMap);

        CreateEntityCommand cmd = new CreateEntityCommand(
                world,
                historyIds,
                init,
                createdEntityId -> {
                    rebindHistoryEntityRenderAssets(createdEntityId);
                    if (!init.hasCapturedZIndex()) {
                        zOrderRuntimeService.addOnTop(createdEntityId, activeLayerIndex);
                    }
                    selectionService.selectOnly(createdEntityId);
                }
        );

        historyManager.execute(cmd);

        int createdEntityId = cmd.getCreatedEntityId();

        // --- Async atlas workflow ---
        if (!alreadyPacked) {
            sceneService.ensureAnimationDirInAtlasInput(sceneTag, animationRelPath);
            atlasStudioService.requestAsyncPack(sceneTag);
        }

        return createdEntityId;
    }

    private ObjectMap<String, AnimationComponent.Clip> copyAnimationAssetClips(AnimationAssetMeta meta) {
        ObjectMap<String, AnimationComponent.Clip> out = new ObjectMap<>();
        if (meta == null || meta.clips == null) {
            return out;
        }

        for (ObjectMap.Entry<String, AnimationComponent.Clip> entry : meta.clips) {
            if (entry == null || entry.key == null || entry.key.isBlank() || entry.value == null) {
                continue;
            }

            AnimationComponent.Clip src = entry.value;
            AnimationComponent.Clip copy = new AnimationComponent.Clip(src.start, src.end);
            copy.flipX = src.flipX;
            out.put(entry.key, copy);
        }
        return out;
    }

    @Override
    public int createParticleEffect(String effectPath, float worldX, float worldY) {
        return createParticleEffect(effectPath, worldX, worldY, null);
    }

    @Override
    public int createParticleEffect(String effectPath, float worldX, float worldY, String metaName) {
        if (effectPath == null || effectPath.isEmpty()) return -1;

        ProjectConfig cfg = ProjectConfig.getInstance();
        final String sceneTag = getCurrentSceneTag();
        if (sceneTag == null || sceneTag.isBlank()) return -1;

        boolean changed = ensureParticleEffectImagesInAtlasInput(effectPath);
        if (changed && assetsChangedListener != null) {
            assetsChangedListener.onSceneAtlasChanged(sceneTag);
        }

        int activeLayerIndex = selectionService.getActiveLayerIndex();
        String resolvedMetaName = (metaName != null && !metaName.isBlank())
                ? metaName
                : "Particle: " + effectPath;

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureParticleEmitter(effectPath, sceneTag, worldX, worldY, activeLayerIndex, resolvedMetaName);
        init.setIdentityStableId(allocateStableId());

        CreateEntityCommand cmd = new CreateEntityCommand(
                world,
                historyIds,
                init,
                createdEntityId -> {
                    rebindHistoryEntityRenderAssets(createdEntityId);
                    if (!init.hasCapturedZIndex()) {
                        zOrderRuntimeService.addOnTop(createdEntityId, activeLayerIndex);
                    }
                    selectionService.selectOnly(createdEntityId);
                }
        );

        historyManager.execute(cmd);

        // --- Async atlas workflow ---
        if (changed) {
            atlasStudioService.requestAsyncPack(sceneTag);
        }

        return cmd.getCreatedEntityId();
    }


    @Override
    public void setAssetsChangedListener(AssetsChangedListener listener) {
        this.assetsChangedListener = listener;
    }

    @Override
    public void deleteEntities(IntArray entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        IntConsumer onRestoredEntity = restoredEntityId -> {
            rebindHistoryEntityRenderAssets(restoredEntityId);
        };

        DeleteEntitiesCommand cmd = new DeleteEntitiesCommand(world, historyIds, entities, onRestoredEntity);
        execute(cmd);
    }

    @Override
    public void applyTransform(IntArray entities,
                               Float x, Float y, Float dx, Float dy,
                               Float rotRad, Float scaleX, Float scaleY,
                               Float originX, Float originY) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        if (x == null && y == null && dx == null && dy == null &&
                rotRad == null && scaleX == null && scaleY == null &&
                originX == null && originY == null) {
            return;
        }

        ComponentMapper<TransformComponent> mT = world.getMapper(TransformComponent.class);

        GizmoTransformCommand cmd = new GizmoTransformCommand(world, historyIds, TransformOp.MOVE);

        for (int i = 0; i < entities.size; i++) {
            int entityId = entities.get(i);

            TransformComponent t = mT.getSafe(entityId, null);
            if (t == null) {
                continue;
            }

            GizmoTransformCommand.Snapshot before = GizmoTransformCommand.Snapshot.of(t);
            GizmoTransformCommand.Snapshot after = GizmoTransformCommand.Snapshot.of(t);

            float baseX = (x != null) ? x : before.x;
            float baseY = (y != null) ? y : before.y;
            if (dx != null) baseX += dx;
            if (dy != null) baseY += dy;

            if (x != null || dx != null) after.x = baseX;
            if (y != null || dy != null) after.y = baseY;

            if (rotRad != null) after.rotRad = rotRad;
            if (scaleX != null) after.sx = scaleX;
            if (scaleY != null) after.sy = scaleY;
            if (originX != null) after.ox = originX;
            if (originY != null) after.oy = originY;

            long historyId = historyIds.ensureForEntity(entityId);
            cmd.addEntry(historyId, before, after);
        }

        if (!cmd.isNoop()) {
            execute(cmd);
        }
    }

    @Override
    public int createPointLight(float worldX, float worldY) {
        String sceneTag = getCurrentSceneTag();
        if (sceneTag == null) return -1;

        int activeLayerIndex = selectionService.getActiveLayerIndex();

        int shaderIdx = ShaderRegistry.indexOf(RuntimeFs.TEXTURE_ARRAY_POINTLIGHT);
        if (shaderIdx < 0) {
            Gdx.app.error("EditorOps", "Missing shader: " + RuntimeFs.TEXTURE_ARRAY_POINTLIGHT);
            return -1;
        }
        int textureHandle = InternalTextures.whiteHandle();

        int blend = BlendMode.ADDITIVE.id; // IMPORTANT: must match ONE,ONE

        // Light defaults (can be centralized)
        float radius = 200f;
        float intensity = 1f;
        float falloff = 1.5f;
        float r = 1f, g = 0.9f, b = 0.2f;

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configurePointLightProcedural(
                        worldX, worldY,
                        shaderIdx, blend, textureHandle,
                        activeLayerIndex,
                        "Point light",
                        radius, intensity, falloff,
                        r, g, b
                );
        init.setIdentityStableId(allocateStableId());

        CreateEntityCommand cmd = new CreateEntityCommand(
                world,
                historyIds,
                init,
                createdEntityId -> {
                    rebindHistoryEntityRenderAssets(createdEntityId);
                    applyDefaultUniforms(createdEntityId, RuntimeFs.TEXTURE_ARRAY_POINTLIGHT);
                    if (!init.hasCapturedZIndex()) {
                        zOrderRuntimeService.addOnTop(createdEntityId, activeLayerIndex);
                    }
                    selectionService.selectOnly(createdEntityId);
                }
        );

        historyManager.execute(cmd);

        return cmd.getCreatedEntityId();
    }

    @Override
    public int createConeLight(float worldX, float worldY) {
        String sceneTag = getCurrentSceneTag();
        if (sceneTag == null) return -1;

        int activeLayerIndex = selectionService.getActiveLayerIndex();

        int shaderIdx = ShaderRegistry.indexOf(RuntimeFs.TEXTURE_ARRAY_CONELIGHT);
        if (shaderIdx < 0) {
            Gdx.app.error("EditorOps", "Missing shader: " + RuntimeFs.TEXTURE_ARRAY_POINTLIGHT);
            return -1;
        }
        int textureHandle = InternalTextures.whiteHandle();

        int blend = BlendMode.ADDITIVE.id;

        float radius = 250f;
        float intensity = 1f;
        float angleDeg = 60f;
        float softness = 0.1f;
        float falloff = 1.5f;
        float r = 1f, g = 0.9f, b = 0.2f;

        float rotationRad = 0f;

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureConeLightProcedural(
                        worldX, worldY,
                        rotationRad,
                        shaderIdx, blend, textureHandle,
                        activeLayerIndex,
                        "Cone light",
                        radius, intensity, angleDeg, softness, falloff,
                        r, g, b
                );
        init.setIdentityStableId(allocateStableId());

        CreateEntityCommand cmd = new CreateEntityCommand(
                world,
                historyIds,
                init,
                createdEntityId -> {
                    rebindHistoryEntityRenderAssets(createdEntityId);
                    applyDefaultUniforms(createdEntityId, RuntimeFs.TEXTURE_ARRAY_CONELIGHT);
                    if (!init.hasCapturedZIndex()) {
                        zOrderRuntimeService.addOnTop(createdEntityId, activeLayerIndex);
                    }
                    selectionService.selectOnly(createdEntityId);
                }
        );

        historyManager.execute(cmd);

        return cmd.getCreatedEntityId();
    }


    private void applyDefaultUniforms(int entityId, String shaderName) {
        ComponentMapper<ShaderParamsComponent> mapper =
                world.getMapper(ShaderParamsComponent.class);

        ShaderParamsComponent params =
                mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);

        Array<ShaderFloatParam> defaults = ShaderRegistry.getDefaultUniforms(shaderName);

        if (defaults == null) {
            Gdx.app.error("EditorOps", "No default uniforms found for shader: " + shaderName);
            return;
        }

        params.floats.clear();

        for (int i = 0; i < defaults.size; i++) {
            ShaderFloatParam uniform = defaults.get(i);

            if (uniform == null || uniform.name == null || uniform.name.length() == 0) {
                continue;
            }

            params.floats.add(new ShaderFloatParam(uniform.name, uniform.value));
        }
    }

    private int allocateStableId() {
        return identityRegistry.allocateStableId();
    }

    @Override
    public int createJoint(int type, int aEntityId, int bEntityId, float worldX, float worldY) {
        CreateJointCommand cmd = new CreateJointCommand(
                world,
                physicsService,
                historyIds,
                type,
                aEntityId,
                bEntityId,
                worldX,
                worldY
        );
        historyManager.execute(cmd);
        return cmd.getCreatedJointEntityId();
    }

    @Override
    public int createGearJoint(int joint1EntityId, int joint2EntityId) {
        CreateGearJointCommand command = new CreateGearJointCommand(
                world,
                physicsService,
                historyManager.historyIds(),
                joint1EntityId,
                joint2EntityId,
                1f
        );
        historyManager.execute(command);
        return command.getCreatedJointEntityId();
    }

    @Override
    public void deleteJoint(int jointEntityId) {
        if (jointEntityId < 0 || !physicsService.isJoint(jointEntityId)) {
            return;
        }
        historyManager.execute(new DeleteJointCommand(world, historyIds, jointEntityId));
    }

    @Override
    public void deleteFixture(int bodyEid, long fixtureId) {
        if (bodyEid < 0 || fixtureId <= 0) return;

        AuthoredPolygonData authored = polygonAuthoringService.findByGeneratedFixtureId(bodyEid, fixtureId);

        if (authored != null) {
            DeleteAuthoredPolygonCommand cmd = new DeleteAuthoredPolygonCommand(
                    world,
                    historyManager.historyIds(),
                    physicsSelectionService,
                    bodyEid,
                    authored.authoringId
            );

            if (!cmd.isNoop()) {
                historyManager.execute(cmd);
            }

            return;
        }

        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).getSafe(bodyEid, null);

        if (fixtures == null) {
            return;
        }

        historyManager.execute(new DeleteFixtureCommand(
                world,
                historyManager.historyIds(),
                physicsSelectionService,
                bodyEid,
                fixtureId
        ));
    }

    @Override
    public void addBoxFixture(int bodyEid, float worldX, float worldY) {
        if (bodyEid < 0) return;

        FixtureDefData fixture = FixtureCommandSupport.createDefaultFixture();
        fixture.shapeType = FixtureDefData.SHAPE_BOX;

        fixture.offsetX = 0f;
        fixture.offsetY = 0f;

        historyManager.execute(new AddFixtureCommand(
                world,
                historyManager.historyIds(),
                physicsSelectionService,
                bodyEid,
                fixture,
                -1
        ));
    }

    @Override
    public void addCircleFixture(int bodyEid, float worldX, float worldY) {
        if (bodyEid < 0) return;

        FixtureDefData fixture = FixtureCommandSupport.createDefaultFixture();
        fixture.shapeType = FixtureDefData.SHAPE_CIRCLE;
        fixture.radius = 0.5f;

        fixture.offsetX = 0f;
        fixture.offsetY = 0f;

        historyManager.execute(new AddFixtureCommand(
                world,
                historyManager.historyIds(),
                physicsSelectionService,
                bodyEid,
                fixture,
                -1
        ));
    }

    private void worldToBodyLocalPx(int bodyEid, float worldX, float worldY, Vector2 out) {
        TransformComponent t = world.getMapper(TransformComponent.class).getSafe(bodyEid, null);
        if (t == null) {
            out.set(0f, 0f);
            return;
        }

        float dx = worldX - t.x;
        float dy = worldY - t.y;

        float cos = (float) Math.cos(t.rotationRad);
        float sin = (float) Math.sin(t.rotationRad);

        float localX = dx * cos + dy * sin;
        float localY = -dx * sin + dy * cos;

        out.set(localX, localY);
    }

    @Override
    public void beginAddPolygonFixture(int bodyEid) {
        if (bodyEid < 0) return;
        polygonDrawSession.beginCreate(bodyEid);
    }

    @Override
    public void beginEditPolygonFixture(int bodyEid, long fixtureId) {
        if (bodyEid < 0 || fixtureId <= 0L) return;

        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(bodyEid, null);
        if (fixtures == null) return;

        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData f = fixtures.fixtures.get(i);
            if (f == null) continue;
            if (f.fixtureId != fixtureId) continue;
            if (f.shapeType != FixtureDefData.SHAPE_POLYGON) return;
            if (f.polyVerts == null || f.polyCount < 3) return;

            polygonDrawSession.beginEdit(
                    bodyEid,
                    fixtureId,
                    f.polyVerts,
                    f.polyCount
            );
            return;
        }
    }

    @Override
    public void addSpatialBlock(int layerEntityId, float worldX, float worldY) {
        if (layerEntityId < 0) return;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null) return;

        SpatialBlockPlacementTarget target = SpatialBlockPlacementTarget.fromWorld(
                tiled.data,
                layerEntityId,
                worldX,
                worldY,
                null,
                true
        );
        addSpatialBlock(layerEntityId, target);
    }

    @Override
    public void addSpatialBlock(int layerEntityId, SpatialBlockPlacementTarget target) {
        if (layerEntityId < 0) return;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null) return;

        if (target == null || !target.valid() || target.tiledLayerEntity() != layerEntityId) return;

        SpatialBlockData block = new SpatialBlockData();
        block.x = target.targetGx();
        block.y = target.targetGy();
        block.width = 1f;
        block.depth = 1f;
        block.altitude = tiled.defaultTileAltitude;
        block.height = tiled.defaultTileHeight > 0f ? tiled.defaultTileHeight : SpatialBlockData.DEFAULT_HEIGHT;
        block.orientation = SpatialBlockOrientation.TILE_CELL;
        block.actorOccluder = true;
        block.physicsCollision = false;
        block.lightOccluder = false;
        block.shadowCaster = false;
        block.particleOccluder = false;

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world,
                historyManager.historyIds(),
                spatialBlockSelectionService,
                layerEntityId,
                block
        );
        if (!command.isNoop()) {
            historyManager.execute(command);
        }
    }

    @Override
    public void deleteSelectedSpatialBlock() {
        if (spatialBlockSelectionService == null || !spatialBlockSelectionService.hasSelectedBlock()) return;
        int layerEntityId = spatialBlockSelectionService.getEditingLayerEntityId();
        int blockId = spatialBlockSelectionService.getSelectedBlockId();
        DeleteSpatialBlockCommand command = new DeleteSpatialBlockCommand(
                world,
                historyManager.historyIds(),
                spatialBlockSelectionService,
                layerEntityId,
                blockId
        );
        if (!command.isNoop()) {
            historyManager.execute(command);
        }
    }

    @Override
    public void createSpatialBlockFromSelectedTiles() {
        if (spatialTileSelectionService == null || !spatialTileSelectionService.hasSelection()) return;

        int layerEntityId = spatialTileSelectionService.getLayerEntityId();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(layerEntityId, null);
        if (tiled == null || tiled.data == null) {
            spatialTileSelectionService.clear();
            return;
        }
        if (!spatialTileSelectionService.canCreateSpatialBlock(tiled.data)) {
            return;
        }

        SpatialBlockData block = spatialTileSelectionService.toSpatialBlockData(
                tiled.data,
                tiled.defaultTileAltitude,
                tiled.defaultTileHeight
        );
        if (block == null) return;

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world,
                historyManager.historyIds(),
                spatialBlockSelectionService,
                layerEntityId,
                block
        );
        if (!command.isNoop()) {
            historyManager.execute(command);
            spatialTileSelectionService.clear();
        }
    }

    @Override
    public void clearSpatialTileSelection() {
        if (spatialTileSelectionService != null) {
            spatialTileSelectionService.clear();
        }
    }

    @Override
    public void applyZIndex(IntArray entities, Integer set, Integer dz) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        if (set == null && dz == null) {
            return;
        }

        ComponentMapper<EntityIndexComponent> mEntityIndex = world.getMapper(EntityIndexComponent.class);

        ChangeZIndexCommand cmd = new ChangeZIndexCommand(world, historyIds);

        for (int i = 0; i < entities.size; i++) {
            int entityId = entities.get(i);

            EntityIndexComponent index = mEntityIndex.getSafe(entityId, null);
            if (index == null) {
                throw new IllegalStateException("Drawable entity missing EntityIndexComponent (applyZIndex): " + entityId);
            }
            int before = index.getZIndex();

            int after = (set != null) ? set : (before + dz);

            long historyId = historyIds.ensureForEntity(entityId);
            cmd.addEntry(historyId, before, after);
        }

        if (!cmd.isNoop()) {
            execute(cmd);
        }
    }

    @Override
    public void applyLayer(IntArray entities, int layerEntityId) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        ComponentMapper<LayerComponent> mLayerIndex = world.getMapper(LayerComponent.class);
        ComponentMapper<EntityIndexComponent> mEntityIndex = world.getMapper(EntityIndexComponent.class);

        ChangeLayerIndexCommand cmd = new ChangeLayerIndexCommand(world, historyIds);
        if (!mLayerIndex.has(layerEntityId)) {
            throw new IllegalStateException("Layer entity missing LayerComponent: " + layerEntityId);
        }
        int targetLayerIndex = mLayerIndex.get(layerEntityId).layerIndex;

        for (int i = 0; i < entities.size; i++) {
            int entityId = entities.get(i);
            if (!mEntityIndex.has(entityId)) {
                throw new IllegalStateException("Drawable entity missing EntityIndexComponent (applyLayer): " + entityId);
            }
            int beforeLayer = mEntityIndex.get(entityId).getLayerIndex();

            long historyId = historyIds.ensureForEntity(entityId);
            cmd.addEntry(historyId, beforeLayer, targetLayerIndex);
        }

        if (!cmd.isNoop()) {
            execute(cmd);
        }
    }

    private void ensureHandleBoundToTextureArray() {
        String tag = getCurrentSceneTag();
        if (snapshotManager != null) {
            snapshotManager.markDirty(tag, "editor-handle-binding-required");
        }
    }

    private void rebindHistoryEntityRenderAssets(int entityId) {
        String sceneTag = getCurrentSceneTag();
        RenderBindingState before = RenderBindingState.capture(world, entityId);

        if (before.isAnimationBacked() && !hasAtlasBinding(entityId, sceneTag)) {
            if (AnimationFallbackSystem.bindFirstFrameFallback(
                    world,
                    entityId,
                    canvas.getRenderState(),
                    before.effectiveAtlasTag(sceneTag))) {
                ensureHandleBoundToTextureArray();
            } else if (before.isRenderable()) {
                ensureHandleBoundToTextureArray();
            } else {
                RenderRebindHelper.rebindHistoryEntityRenderAssets(
                        canvas,
                        sceneTag,
                        atlasStudioService,
                        entityId
                );
            }
        } else {
            RenderRebindHelper.rebindHistoryEntityRenderAssets(
                    canvas,
                    sceneTag,
                    atlasStudioService,
                    entityId
            );
        }
    }

    private boolean hasAtlasBinding(int entityId, String sceneTag) {
        AssetRefComponent src = world.getMapper(AssetRefComponent.class).getSafe(entityId, null);
        if (src == null) {
            return false;
        }
        String atlasTag = (src.atlasTag != null && !src.atlasTag.isBlank()) ? src.atlasTag : sceneTag;
        return atlasStudioService.resolveCached(src.assetId, atlasTag) != null;
    }

    private String getCurrentSceneTag() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return null;

        return cfg.canonicalSceneTagCurrent();
    }

    private void ensureEditorBundleActive() {
        String tag = getCurrentSceneTag();
        if (snapshotManager != null) {
            snapshotManager.markDirty(tag, "editor-bundle-active-required");
        }
    }

    private boolean ensureParticleEffectImagesInAtlasInput(String effectPath) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) return false;

        String sceneName = cfg.getCurrentSceneName();
        if (sceneName == null || sceneName.isEmpty()) return false;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle effectsRoot = projectDir.child(StudioFs.DIR_ORIG_EFFECTS);
        FileHandle effectFile = effectsRoot.child(effectPath);

        if (!effectFile.exists()) {
            Gdx.app.error("EditorOps",
                    "ensureParticleEffectImagesInAtlasInput: effect introuvable " + effectFile.path());
            return false;
        }

        ParticleEffect effect = new ParticleEffect();
        try {
            effect.loadEmitters(effectFile);
        } catch (Exception ex) {
            Gdx.app.error("EditorOps",
                    "ensureParticleEffectImagesInAtlasInput: failed to read emitters for " + effectFile.path(),
                    ex);
            return false;
        }

        Set<String> rawPaths = new HashSet<>();
        for (ParticleEmitter emitter : effect.getEmitters()) {
            if (emitter == null || emitter.getImagePaths() == null) continue;
            for (String path : emitter.getImagePaths()) {
                if (path != null && !path.isEmpty()) {
                    rawPaths.add(path);
                }
            }
        }

        if (rawPaths.isEmpty()) return false;

        String canonicalTag = cfg.canonicalSceneTag(sceneName);
        if (canonicalTag == null || canonicalTag.isEmpty()) {
            Gdx.app.error("EditorOps",
                    "ensureParticleEffectImagesInAtlasInput: canonicalTag introuvable pour " + sceneName);
            return false;
        }

        FileHandle imagesRoot = projectDir.child(StudioFs.DIR_ORIG_IMAGES);
        FileHandle atlasInputDir = projectDir.child(StudioFs.DIR_ATLASES)
                .child(StudioFs.DIR_INPUT)
                .child(canonicalTag);
        imagesRoot.mkdirs();
        atlasInputDir.mkdirs();

        boolean changed = false;
        boolean loggedCopyChange = false;

        FileHandle atlasesRoot = projectDir.child(StudioFs.DIR_ATLASES);
        FileHandle atlasOutput = atlasesRoot.child(StudioFs.withExt(canonicalTag, StudioFs.EXT_ATLAS));
        if (!atlasOutput.exists()) {
            changed = true;
            Gdx.app.log("EditorOps",
                    "ensureParticleEffectImagesInAtlasInput: changed=true (missing atlas output "
                            + atlasOutput.path() + ")");
        }
        for (String rawPath : rawPaths) {
            if (rawPath == null || rawPath.isEmpty()) continue;

            String normalized = rawPath.replace('\\', '/');
            String fileName = fileNameFromPath(normalized);

            FileHandle source = effectFile.parent().child(normalized);
            if (!source.exists()) {
                source = imagesRoot.child(fileName);
            }

            if (!source.exists()) {
                Gdx.app.error("EditorOps",
                        "ensureParticleEffectImagesInAtlasInput: image introuvable pour l'effet "
                                + effectPath + " -> " + normalized);
                continue;
            }

            FileHandle destImage = imagesRoot.child(fileName);
            if (!destImage.exists()) {
                source.copyTo(destImage);
                if (!loggedCopyChange) {
                    Gdx.app.log("EditorOps",
                            "ensureParticleEffectImagesInAtlasInput: changed=true (copied images)");
                    loggedCopyChange = true;
                }
                changed = true;
            }

            FileHandle destInput = atlasInputDir.child(fileName);
            if (!destInput.exists()) {
                destImage.copyTo(destInput);
                if (!loggedCopyChange) {
                    Gdx.app.log("EditorOps",
                            "ensureParticleEffectImagesInAtlasInput: changed=true (copied images)");
                    loggedCopyChange = true;
                }
                changed = true;
            }
        }

        return changed;
    }

    private String resolveSceneAtlasTag(String requestedTag) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null) return requestedTag;

        String canonicalTag = cfg.canonicalSceneTagCurrent();
        if (canonicalTag == null || canonicalTag.isEmpty()) {
            return requestedTag;
        }

        return canonicalTag;
    }

    private static String fileNameFromPath(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return (slash >= 0) ? path.substring(slash + 1) : path;
    }

    private void execute(Command cmd) {
        if (historyManager != null) {
            historyManager.execute(cmd);
        } else {
            cmd.redo();
        }
    }

    private static final class RenderBindingState {
        final String atlasTag;
        final int textureHandle;
        final boolean textureRegionValid;
        final boolean animationBacked;

        private RenderBindingState(String atlasTag,
                                   int textureHandle,
                                   boolean textureRegionValid,
                                   boolean animationBacked) {
            this.atlasTag = atlasTag;
            this.textureHandle = textureHandle;
            this.textureRegionValid = textureRegionValid;
            this.animationBacked = animationBacked;
        }

        static RenderBindingState capture(World world, int entityId) {
            AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).getSafe(entityId, null);
            RenderMaterialComponent mat = world.getMapper(RenderMaterialComponent.class).getSafe(entityId, null);
            TextureRegionComponent tr = world.getMapper(TextureRegionComponent.class).getSafe(entityId, null);
            AnimationComponent animation = world.getMapper(AnimationComponent.class).getSafe(entityId, null);

            return new RenderBindingState(
                    assetRef != null ? String.valueOf(assetRef.atlasTag) : "<none>",
                    mat != null ? mat.textureHandle : 0,
                    tr != null && tr.valid,
                    animation != null
            );
        }

        boolean isRenderable() {
            return textureHandle != 0 && textureRegionValid;
        }

        boolean isAnimationBacked() {
            return animationBacked;
        }

        String effectiveAtlasTag(String sceneTag) {
            if (atlasTag != null && !atlasTag.isBlank() && !"<none>".equals(atlasTag)) {
                return atlasTag;
            }
            return sceneTag;
        }
    }
}
