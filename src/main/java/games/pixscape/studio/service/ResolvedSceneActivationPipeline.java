package games.pixscape.studio.service;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialStructureCompilation;
import games.pixscape.studio.service.spatial.SpatialWallAuthoringValidator;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.ui.log.StudioLog;

/** Activates one already resolved scene in the existing, already cleared editor World. */
final class ResolvedSceneActivationPipeline {

    private final World world;
    private final TileAnimationLookup tileAnimationLookup;
    private final TiledAllocatorService tiledAllocatorService;
    private final HistoryManager historyManager;
    private final RenderRuntimeRebuilder renderRuntimeRebuilder;
    private final SceneLoadOperation sceneLoader;

    ResolvedSceneActivationPipeline(World world,
                                    TileAnimationLookup tileAnimationLookup,
                                    TiledAllocatorService tiledAllocatorService,
                                    HistoryManager historyManager,
                                    RenderRuntimeRebuilder renderRuntimeRebuilder) {
        this(world, tileAnimationLookup, tiledAllocatorService, historyManager,
                renderRuntimeRebuilder, SceneLoader::loadScene);
    }

    ResolvedSceneActivationPipeline(World world,
                                    TileAnimationLookup tileAnimationLookup,
                                    TiledAllocatorService tiledAllocatorService,
                                    HistoryManager historyManager,
                                    RenderRuntimeRebuilder renderRuntimeRebuilder,
                                    SceneLoadOperation sceneLoader) {
        if (sceneLoader == null) {
            throw new IllegalArgumentException(
                    "Scene load operation is required.");
        }
        this.world = world;
        this.tileAnimationLookup = tileAnimationLookup;
        this.tiledAllocatorService = tiledAllocatorService;
        this.historyManager = historyManager;
        this.renderRuntimeRebuilder = renderRuntimeRebuilder;
        this.sceneLoader = sceneLoader;
    }

    void activate(ResolvedSceneTarget target) {
        Box2dSyncSystem box2dSync = world.getSystem(Box2dSyncSystem.class);
        if (box2dSync != null) {
            box2dSync.setEnabled(false);
            box2dSync.setStepEnabled(false);
        }
        sceneLoader.load(world, target.sceneFile(), false, target.meta());
        normalizeSceneAtlasTags(target.canonicalTag());
        world.process();
        resolveTiledLayersForActivation(
                world,
                target.meta(),
                tileAnimationLookup,
                tiledAllocatorService,
                target.projectTitle(),
                target.sceneName()
        );
        validateAndCompileSpatialBlocksForActivation(
                world, target.projectTitle(), target.sceneName());
        PhysicsService.rebuildPreparedBodyCaches(
                world,
                target.meta().pixelsPerMeter);
        rebuildHistoryIdsFromWorld();
        assertDrawablesHaveEntityIndex("loadScene(" + target.sceneName() + ")");
        renderRuntimeRebuilder.rebuild(
                target.config(), target.canonicalTag(), target.projectDir());
        world.process();
    }

    static void resolveTiledLayersForActivation(World world,
                                                SceneMeta meta,
                                                TileAnimationLookup lookup,
                                                TiledAllocatorService allocator,
                                                String projectTitle,
                                                String sceneName) {
        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        ComponentMapper<LayerComponent> mLayer = world.getMapper(LayerComponent.class);
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] dataArr = bag.getData();

        if (meta == null) {
            throw new SceneService.TiledMapResolutionException(
                    "Cannot resolve tiled maps: scene metadata is missing.");
        }

        for (int i = 0; i < bag.size(); i++) {
            int e = dataArr[i];
            TiledLayerComponent tiled = mTiled.get(e);
            if (tiled == null) continue;

            if (tiled.mapWidthCells <= 0 || tiled.mapHeightCells <= 0
                    || meta.tileWidth <= 0 || meta.tileHeight <= 0 || meta.chunkSize <= 0
                    || meta.tiledProjection == null) {
                throw unresolvedTiledMap(projectTitle, sceneName, e,
                        "the serialized tiled layer or scene map metadata is incomplete");
            }

            tiled.data = new TiledMapLayerData(
                    tiled.mapWidthCells,
                    tiled.mapHeightCells,
                    (int) meta.tileWidth,
                    (int) meta.tileHeight,
                    meta.chunkSize,
                    meta.tiledProjection
            );

            tiled.data.originX = tiled.originX;
            tiled.data.originY = tiled.originY;
            LayerComponent layer = mLayer.getSafe(e, null);
            tiled.spatialEnabled = (layer != null && layer.spatialEnabled) || tiled.spatialEnabled;
            tiled.data.spatialEnabled = tiled.spatialEnabled;
            tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
            tiled.data.defaultTileHeight = tiled.defaultTileHeight;

            if (allocator != null) {
                allocator.allocateLayer(tiled);
            }

            tiled.ensureSparseTileStorageConsistency();
            tiled.data.beginContentMutation();
            try {
                for (int t = 0; t < tiled.tileXs.size; t++) {
                    int gx = tiled.tileXs.get(t);
                    int gy = tiled.tileYs.get(t);
                    int assetId = tiled.tileAssetIds.get(t);
                    byte flags = tiled.tileTransformFlags.get(t);
                    tiled.data.setTile(gx, gy, assetId, flags);

                    if (lookup != null) {
                        int cx = gx / tiled.data.chunkSize;
                        int cy = gy / tiled.data.chunkSize;

                        TileChunk chunk = tiled.data.getChunk(cx, cy);
                        if (chunk != null) {
                            int lx = gx - (cx * tiled.data.chunkSize);
                            int ly = gy - (cy * tiled.data.chunkSize);
                            TileAnimationStateSupport.syncWorldCell(chunk, lx, ly, lookup);
                        }
                    }
                }
            } finally {
                tiled.data.endContentMutation();
            }

            tiled.data.markAllChunksContentDirty();
        }

        ComponentMapper<SpatialBlocksComponent> mBlocks = world.getMapper(SpatialBlocksComponent.class);
        IntBag spatialLayers = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class))
                .getEntities();
        int[] spatialData = spatialLayers.getData();
        for (int i = 0, n = spatialLayers.size(); i < n; i++) {
            int entity = spatialData[i];
            SpatialBlocksComponent blocks = mBlocks.getSafe(entity, null);
            if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) continue;
            TiledLayerComponent tiled = mTiled.getSafe(entity, null);
            if (tiled == null || tiled.data == null) {
                throw unresolvedTiledMap(projectTitle, sceneName, entity,
                        "the Spatial V3 layer has no owning TiledLayerComponent map data");
            }
        }
    }

    static void validateSpatialBlocksForActivation(World world, String sceneName) {
        validateSpatialBlocksForActivation(world, null, sceneName);
    }

    private static void validateSpatialBlocksForActivation(World world,
                                                           String projectTitle,
                                                           String sceneName) {
        SpatialActivationFailure failure = firstInvalidSpatialBlock(world);
        if (failure == null) return;

        String prefix = projectTitle != null && !projectTitle.isBlank()
                ? "Project '" + projectTitle + "', scene '" + safeContext(sceneName) + "' contains "
                : sceneName != null && !sceneName.isBlank()
                ? "Scene '" + sceneName + "' contains " : "Scene contains ";
        String message = prefix + stripTrailingPunctuation(
                failure.result().message(failure.layerName(), failure.layerEntityId()))
                + ". Scene activation was rejected; the authored scene file was left unchanged.";
        StudioLog.error(message);
        throw new SceneService.SpatialSceneActivationException(message);
    }

    static void validateAndCompileSpatialBlocksForActivation(World world,
                                                             String projectTitle,
                                                             String sceneName) {
        validateSpatialBlocksForActivation(world, projectTitle, sceneName);

        ComponentMapper<SpatialBlocksComponent> mBlocks = world.getMapper(SpatialBlocksComponent.class);
        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        ComponentMapper<LayerMetaComponent> mMeta = world.getMapper(LayerMetaComponent.class);
        IntBag layers = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class, TiledLayerComponent.class))
                .getEntities();
        int[] data = layers.getData();
        for (int i = 0, n = layers.size(); i < n; i++) {
            int entity = data[i];
            SpatialBlocksComponent blocks = mBlocks.getSafe(entity, null);
            if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) continue;
            TiledLayerComponent tiled = mTiled.get(entity);
            SpatialStructureCompilation.Result compilation =
                    SpatialStructureCompilation.tryCompile(blocks, tiled.data);
            if (compilation.success()) continue;

            LayerMetaComponent layerMeta = mMeta.getSafe(entity, null);
            String layer = layerMeta != null && layerMeta.name != null && !layerMeta.name.isBlank()
                    ? "layer '" + layerMeta.name + "'" : "layer entity " + entity;
            String message = "Project '" + safeContext(projectTitle) + "', scene '"
                    + safeContext(sceneName) + "', " + layer + " failed Spatial V3 activation compilation: "
                    + stripTrailingPunctuation(compilation.diagnostic()) + ".";
            StudioLog.error(message);
            throw new SceneService.SpatialSceneActivationException(message);
        }
    }

    static SpatialActivationFailure firstInvalidSpatialBlock(World world) {
        if (world == null) return null;

        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        ComponentMapper<SpatialBlocksComponent> mBlocks = world.getMapper(SpatialBlocksComponent.class);
        ComponentMapper<LayerMetaComponent> mMeta = world.getMapper(LayerMetaComponent.class);
        IntBag layers = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class))
                .getEntities();

        int[] data = layers.getData();
        for (int i = 0, n = layers.size(); i < n; i++) {
            int entity = data[i];
            SpatialBlocksComponent blocks = mBlocks.getSafe(entity, null);
            if (blocks == null || blocks.blocks == null) continue;

            TiledLayerComponent tiled = mTiled.getSafe(entity, null);
            String layerName = null;
            LayerMetaComponent meta = mMeta.getSafe(entity, null);
            if (meta != null) {
                layerName = meta.name;
            }

            SpatialWallAuthoringValidator.Result failure =
                    SpatialWallAuthoringValidator.validateLayer(blocks, tiled != null ? tiled.data : null);
            if (failure.isValid()) failure = null;

            if (failure == null) continue;
            return new SpatialActivationFailure(failure, layerName, entity);
        }
        return null;
    }

    private void rebuildHistoryIdsFromWorld() {
        if (historyManager == null || world == null) return;

        HistoryIdRegistry historyIds = historyManager.historyIds();
        historyIds.clear();

        var em = world.getEntityManager();
        ensureHistoryIds(world, historyIds, em, Aspect.all(LayerComponent.class, LayerMetaComponent.class));
        ensureHistoryIds(world, historyIds, em, Aspect.all(EntityIndexComponent.class));
    }

    private static void ensureHistoryIds(World world,
                                         HistoryIdRegistry historyIds,
                                         com.artemis.EntityManager em,
                                         Aspect.Builder aspectBuilder) {
        IntBag bag = world.getAspectSubscriptionManager().get(aspectBuilder).getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            if (!em.isActive(e)) continue;
            historyIds.ensureForEntity(e);
        }
    }

    private void assertDrawablesHaveEntityIndex(String context) {
        ComponentMapper<EntityIndexComponent> mIndex = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<PixscapeIdentityComponent> mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<ParticleEmitterComponent> mEmitter = world.getMapper(ParticleEmitterComponent.class);

        IntBag drawables = world.getAspectSubscriptionManager()
                .get(Aspect.one(AssetRefComponent.class, ParticleEmitterComponent.class))
                .getEntities();

        int[] data = drawables.getData();
        for (int i = 0; i < drawables.size(); i++) {
            int entityId = data[i];
            if (mIndex.has(entityId)) continue;

            String metaName = null;
            PixscapeIdentityComponent identity = mIdentity.getSafe(entityId, null);
            if (identity != null) metaName = identity.name;

            String effectPath = null;
            ParticleEmitterComponent emitter = mEmitter.getSafe(entityId, null);
            if (emitter != null) effectPath = emitter.effectPath;

            throw new IllegalStateException(
                    "Drawable entity missing EntityIndexComponent (" + context + "): id=" + entityId
                            + ", name=" + metaName
                            + ", effect=" + effectPath
            );
        }
    }

    private void normalizeSceneAtlasTags(String canonicalTag) {
        if (canonicalTag == null || canonicalTag.isBlank()) return;

        ComponentMapper<AssetRefComponent> mSrc = world.getMapper(AssetRefComponent.class);
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(AssetRefComponent.class))
                .getEntities();

        int[] data = bag.getData();
        boolean changed = false;
        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            AssetRefComponent src = mSrc.get(e);
            if (src.assetId < 0) continue;
            if (src.atlasTag == null || src.atlasTag.isEmpty()) continue;
            if (canonicalTag.equals(src.atlasTag)) continue;

            src.atlasTag = canonicalTag;
            changed = true;
        }

        if (changed) {
            Gdx.app.log("SceneManager", "normalizeSceneAtlasTags: remapped scene atlas tags to " + canonicalTag);
        }
    }

    private static SceneService.TiledMapResolutionException unresolvedTiledMap(String projectTitle,
                                                                               String sceneName,
                                                                               int layerEntityId,
                                                                               String detail) {
        return new SceneService.TiledMapResolutionException("Project '" + safeContext(projectTitle)
                + "', scene '" + safeContext(sceneName) + "', layer entity " + layerEntityId
                + " could not resolve its owning tiled map: " + detail + ".");
    }

    private static String stripTrailingPunctuation(String message) {
        if (message == null || message.isBlank()) return "invalid Spatial V3 authored data";
        int end = message.length();
        while (end > 0) {
            char c = message.charAt(end - 1);
            if (c != '.' && c != '!' && c != '?') break;
            end--;
        }
        return message.substring(0, end);
    }

    private static String safeContext(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }

    record ResolvedSceneTarget(ProjectConfig config,
                               SceneMeta meta,
                               FileHandle sceneFile,
                               FileHandle projectDir,
                               String projectTitle,
                               String sceneName,
                               String canonicalTag) {
    }

    record SpatialActivationFailure(SpatialWallAuthoringValidator.Result result,
                                    String layerName,
                                    int layerEntityId) {
    }

    @FunctionalInterface
    interface RenderRuntimeRebuilder {
        void rebuild(ProjectConfig config, String canonicalTag, FileHandle projectDir);
    }

    @FunctionalInterface
    interface SceneLoadOperation {
        void load(World world, FileHandle file, boolean editMode, SceneMeta meta);
    }

}
