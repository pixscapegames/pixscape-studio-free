package games.pixscape.studio.importer.tmx;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.loading.WorldConfigFactory;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.asset.TileAnimationsMetaDatabase;
import games.pixscape.studio.asset.TilesetAnchor;
import games.pixscape.studio.asset.TilesetRenderSize;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.helper.TiledSparseStorageHelper;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.asset.TiledAnimationImportSupport;
import games.pixscape.studio.service.asset.TilesetAssetImportService;
import games.pixscape.studio.service.asset.TilesetAssetImportService.ImageCollectionTileSource;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetAtlasImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetImageCollectionImportRequest;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetImportResult;
import games.pixscape.studio.service.asset.TilesetAssetImportService.TilesetProfileImportSettings;
import games.pixscape.studio.service.atlas.SceneAtlasInputService;
import games.pixscape.studio.service.atlas.SceneAtlasLoaderService;
import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TmxSceneImportService {

    private final ProjectConfig cfg;
    private final FileHandle projectDir;
    private final AssetMetaDatabase assetDb;
    private final TmxImportPlanner planner;
    private final TilesetAssetImportService tilesetImportService;
    private final RuntimeAvailabilityService runtimeAvailabilityService;
    private final SceneAtlasInputService sceneAtlasInputService;

    public TmxSceneImportService(ProjectConfig cfg,
                                 FileHandle projectDir,
                                 AssetMetaDatabase assetDb) {
        this(
                cfg,
                projectDir,
                assetDb,
                new TmxImportPlanner(),
                new TilesetAssetImportService(assetDb),
                new RuntimeAvailabilityService(),
                new SceneAtlasInputService()
        );
    }

    TmxSceneImportService(ProjectConfig cfg,
                          FileHandle projectDir,
                          AssetMetaDatabase assetDb,
                          TmxImportPlanner planner,
                          TilesetAssetImportService tilesetImportService,
                          RuntimeAvailabilityService runtimeAvailabilityService,
                          SceneAtlasInputService sceneAtlasInputService) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.projectDir = Objects.requireNonNull(projectDir, "projectDir");
        this.assetDb = Objects.requireNonNull(assetDb, "assetDb");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.tilesetImportService = Objects.requireNonNull(tilesetImportService, "tilesetImportService");
        this.runtimeAvailabilityService = Objects.requireNonNull(runtimeAvailabilityService, "runtimeAvailabilityService");
        this.sceneAtlasInputService = Objects.requireNonNull(sceneAtlasInputService, "sceneAtlasInputService");
    }

    public TmxSceneImportResult importScene(TmxSceneImportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        if (request.tmxFile() == null) {
            throw new IllegalArgumentException("request.tmxFile is null");
        }

        TmxImportPlanResult planResult = planner.plan(new TmxImportPlanRequest(request.tmxFile()));
        if (!planResult.hasPlan()) {
            return TmxSceneImportResult.rejected(
                    TmxSceneImportStatus.PREFLIGHT_FAILED,
                    planResult,
                    null
            );
        }

        TmxImportPlan plan = planResult.plan();
        TmxSceneImportResult preMutationRejection = validateBeforeMutation(planResult);
        if (preMutationRejection != null) {
            return preMutationRejection;
        }

        String sceneName = uniqueSceneName(sceneName(request, plan));
        TmxSceneImportTransaction transaction = new TmxSceneImportTransaction(cfg, projectDir, assetDb);
        String createdSceneFileName = null;
        String createdSceneTag = null;

        try {
            cfg.createSceneMeta(sceneName);
            SceneMeta meta = cfg.getSceneMeta(sceneName);
            if (meta == null) {
                throw new IllegalStateException("Scene metadata was not created: " + sceneName);
            }
            createdSceneFileName = meta.getFile();
            configureSceneMeta(meta, plan.scene());
            createdSceneTag = cfg.canonicalSceneTagFor(meta);

            ImportAssetsResult importedAssets = importAssets(plan, meta);
            World world = buildImportedWorld(
                    plan,
                    importedAssets.cellLogicalIdsByTileset(),
                    importedAssets.imageAssetsBySourceLayer(),
                    createdSceneTag
            );

            projectDir.child(StudioFs.DIR_SCENES).mkdirs();
            FileHandle sceneFile = projectDir.child(StudioFs.DIR_SCENES).child(createdSceneFileName);
            SceneService.saveScene(world, sceneFile, false);

            syncAtlasInputs(createdSceneTag, importedAssets.importedAssetIds());
            if (request.packSceneAtlas()) {
                SceneAtlasLoaderService.packSceneAtlas(cfg, createdSceneTag, projectDir);
            }

            assetDb.save(projectDir.child(StudioFs.FILE_ASSETS_JSON));
            ProjectConfig.ProjectIO.saveProject(cfg, StudioFs.requireStudioProjectFile(cfg));

            return new TmxSceneImportResult(
                    TmxSceneImportStatus.IMPORTED,
                    planResult,
                    sceneName,
                    createdSceneFileName,
                    createdSceneTag,
                    importedAssets.importedTilesetCount(),
                    importedAssets.importedTileAssetIds().size(),
                    plan.layers().size(),
                    plan.scene().nonEmptyTileCount(),
                    planResult.preflightReport().diagnostics(),
                    null,
                    false,
                    false,
                    new TmxSceneImportRollback(transaction, sceneName, createdSceneFileName, createdSceneTag)
            );
        } catch (RuntimeException ex) {
            try {
                transaction.rollback(sceneName, createdSceneFileName, createdSceneTag);
                return failedResult(planResult, sceneName, createdSceneFileName, createdSceneTag, ex, true);
            } catch (RuntimeException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
                return failedResult(planResult, sceneName, createdSceneFileName, createdSceneTag, ex, false);
            }
        }
    }

    private TmxSceneImportResult validateBeforeMutation(TmxImportPlanResult planResult) {
        return null;
    }

    private String sceneName(TmxSceneImportRequest request, TmxImportPlan plan) {
        if (request.requestedSceneName() != null && !request.requestedSceneName().isBlank()) {
            return request.requestedSceneName().trim();
        }
        return plan.scene().proposedSceneName();
    }

    private String uniqueSceneName(String desired) {
        String base = desired != null && !desired.isBlank() ? desired.trim() : "Imported TMX";
        if (cfg.getSceneMeta(base) == null) {
            return base;
        }
        int suffix = 2;
        while (cfg.getSceneMeta(base + " " + suffix) != null) {
            suffix++;
        }
        return base + " " + suffix;
    }

    private void configureSceneMeta(SceneMeta meta, TmxScenePlan scene) {
        meta.tiledEnabled = true;
        meta.tileWidth = scene.tileWidth();
        meta.tileHeight = scene.tileHeight();
        meta.chunkSize = Math.max(1, meta.chunkSize);
        meta.tiledProjection = scene.tiledProjection() != null
                ? scene.tiledProjection()
                : SceneMetaRuntime.TiledProjection.ORTHO;
        runtimeAvailabilityService.data(meta);
    }

    private ImportAssetsResult importAssets(TmxImportPlan plan, SceneMeta meta) {
        Map<Integer, Map<Integer, Integer>> cellLogicalIdsByTileset = new HashMap<>();
        Set<Integer> importedTileAssetIds = new HashSet<>();
        Map<Integer, ImportedImageAsset> imageAssetsBySourceLayer = new HashMap<>();
        Set<Integer> importedImageAssetIds = new HashSet<>();
        int importedTilesetCount = 0;
        boolean tileAnimationsChanged = false;

        FileHandle tileAnimationsFile = projectDir.child(RuntimeFs.FILE_TILE_ANIMATIONS_JSON);
        TileAnimationsMetaDatabase tileAnimationsDb = TileAnimationsIO.load(tileAnimationsFile);
        FileHandle tilesRoot = projectDir.child(StudioFs.DIR_ORIG_TILES);
        for (TmxTilesetPlan tileset : plan.tilesets()) {
            TilesetImportResult result;
            if (tileset.imageCollection()) {
                result = tilesetImportService.importImageCollection(new TilesetImageCollectionImportRequest(
                        tileset.name(),
                        tilesRoot,
                        tileset.tileWidth(),
                        tileset.tileHeight(),
                        tileset.tileCount(),
                        imageCollectionSources(tileset),
                        collectionProfileSettings(plan.scene(), tileset)
                ));
            } else {
                FileHandle image = new FileHandle(tileset.resolvedImagePath());
                result = tilesetImportService.importAtlas(new TilesetAtlasImportRequest(
                        image,
                        tilesRoot,
                        tileset.tileWidth(),
                        tileset.tileHeight(),
                        tileset.spacing(),
                        tileset.margin()
                ));
            }
            if (result.importedCount() <= 0) {
                throw new IllegalStateException("Tileset import failed: " + tileset.name());
            }
            importedTilesetCount += result.importedCount();
            Map<Integer, Integer> cellLogicalIds = new HashMap<>(result.localTileAssetIds());
            for (Integer assetId : result.localTileAssetIds().values()) {
                if (assetId == null || assetId <= 0) continue;
                importedTileAssetIds.add(assetId);
                runtimeAvailabilityService.addTiledTile(meta, assetId);
            }
            Map<Integer, Integer> animationIds = TiledAnimationImportSupport.importTileAnimations(
                    assetDb,
                    tileAnimationsDb,
                    tileset.name(),
                    tileset.tileAnimations(),
                    result.localTileAssetIds()
            );
            if (!animationIds.isEmpty()) {
                tileAnimationsChanged = true;
                for (Map.Entry<Integer, Integer> entry : animationIds.entrySet()) {
                    if (entry == null || entry.getKey() == null || entry.getValue() == null) continue;
                    cellLogicalIds.put(entry.getKey(), entry.getValue());
                    runtimeAvailabilityService.addTiledAnimation(meta, entry.getValue());
                }
            }
            cellLogicalIdsByTileset.put(tileset.planIndex(), cellLogicalIds);
        }
        if (tileAnimationsChanged) {
            TileAnimationsIO.save(tileAnimationsDb, tileAnimationsFile);
        }

        FileHandle imagesRoot = projectDir.child(StudioFs.DIR_ORIG_IMAGES);
        imagesRoot.mkdirs();
        for (TmxLayerPlan layer : plan.layers()) {
            if (!(layer instanceof TmxImageLayerPlan imageLayer)) {
                continue;
            }
            ImportedImageAsset imageAsset = importImageAsset(imageLayer, imagesRoot);
            imageAssetsBySourceLayer.put(imageLayer.sourceLayerIndex(), imageAsset);
            importedImageAssetIds.add(imageAsset.assetId());
            runtimeAvailabilityService.addSprite(meta, imageAsset.assetId());
        }

        return new ImportAssetsResult(
                importedTilesetCount,
                cellLogicalIdsByTileset,
                importedTileAssetIds,
                imageAssetsBySourceLayer,
                importedImageAssetIds
        );
    }

    private ImportedImageAsset importImageAsset(TmxImageLayerPlan imageLayer, FileHandle imagesRoot) {
        if (imageLayer.resolvedImagePath() == null || imageLayer.resolvedImagePath().isBlank()) {
            throw new IllegalStateException("Image layer source was not resolved: " + imageLayer.name());
        }

        FileHandle source = new FileHandle(imageLayer.resolvedImagePath());
        if (!source.exists() || source.isDirectory()) {
            throw new IllegalStateException("Image layer image is missing: " + imageLayer.imageSource());
        }

        ImageSize imageSize = readImageSize(source);
        String base = StudioFs.baseName(source.name());
        AssetMeta meta = assetDb.registerIfAbsent(
                AssetType.IMAGE,
                StudioFs.PREFIX_IMAGES + base,
                null,
                AssetMeta.AssetScope.USER
        );

        String extension = source.extension();
        String newName = base + "__a" + meta.id + (extension == null || extension.isBlank() ? "" : "." + extension);
        FileHandle dst = imagesRoot.child(newName);
        if (!dst.exists()) {
            source.copyTo(dst);
        }
        meta.sourceRelPath = StudioFs.DIR_ORIG_IMAGES + "/" + newName;

        int width = imageLayer.imageWidth() > 0 ? imageLayer.imageWidth() : imageSize.width();
        int height = imageLayer.imageHeight() > 0 ? imageLayer.imageHeight() : imageSize.height();
        return new ImportedImageAsset(meta.id, width, height);
    }

    private World buildImportedWorld(TmxImportPlan plan,
                                     Map<Integer, Map<Integer, Integer>> tileAssetIdsByTileset,
                                     Map<Integer, ImportedImageAsset> imageAssetsBySourceLayer,
                                     String sceneTag) {
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        int layerIndex = 0;
        for (TmxLayerPlan layerPlan : plan.layers()) {
            if (layerPlan instanceof TmxTileLayerPlan tileLayer) {
                int layerEntity = world.create();
                createTileLayerComponents(world, layerEntity, layerIndex, tileLayer, plan.scene(), sceneTag);
                populateTiles(world, layerEntity, tileLayer, tileAssetIdsByTileset);
                layerIndex++;
            } else if (layerPlan instanceof TmxImageLayerPlan imageLayer) {
                ImportedImageAsset imageAsset = imageAssetsBySourceLayer.get(imageLayer.sourceLayerIndex());
                if (imageAsset == null) {
                    throw new IllegalStateException("Missing imported image asset for layer " + imageLayer.name());
                }
                int layerEntity = world.create();
                createClassicLayerComponents(world, layerEntity, layerIndex, imageLayer);
                createImageLayerSprite(world, layerIndex, plan.scene(), imageLayer, imageAsset, sceneTag);
                layerIndex++;
            }
        }
        world.process();
        return world;
    }

    private void createTileLayerComponents(World world,
                                           int layerEntity,
                                           int layerIndex,
                                           TmxTileLayerPlan tileLayer,
                                           TmxScenePlan scene,
                                           String sceneTag) {
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = layerIndex;
        layer.type = LayerComponent.TYPE_TILED;
        layer.spatialEnabled = false;

        LayerMetaComponent meta = world.getMapper(LayerMetaComponent.class).create(layerEntity);
        meta.name = tileLayer.name();
        meta.description = "";
        meta.locked = false;

        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).create(layerEntity);
        visibility.visible = tileLayer.visible();
        visibility.culledByFrustum = true;
        visibility.inView = false;

        LayerParallaxComponent parallax = world.getMapper(LayerParallaxComponent.class).create(layerEntity);
        parallax.factorX = tileLayer.parallaxX();
        parallax.factorY = tileLayer.parallaxY();

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerEntity);
        tiled.mapWidthCells = tileLayer.width();
        tiled.mapHeightCells = tileLayer.height();
        tiled.originX = tileLayer.offsetX();
        tiled.originY = tileLayer.offsetY();
        tiled.spatialEnabled = false;
        tiled.defaultTileAltitude = 0f;
        tiled.defaultTileHeight = 0f;
        tiled.atlasTag = sceneTag;
        tiled.data = new TiledMapLayerData(
                tileLayer.width(),
                tileLayer.height(),
                scene.tileWidth(),
                scene.tileHeight(),
                16,
                scene.tiledProjection() != null
                        ? scene.tiledProjection()
                        : SceneMetaRuntime.TiledProjection.ORTHO
        );
        tiled.data.originX = tiled.originX;
        tiled.data.originY = tiled.originY;
        tiled.data.visible = tileLayer.visible();
        tiled.data.spatialEnabled = tiled.spatialEnabled;
        tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
        tiled.data.defaultTileHeight = tiled.defaultTileHeight;
    }

    private void createClassicLayerComponents(World world,
                                              int layerEntity,
                                              int layerIndex,
                                              TmxImageLayerPlan imageLayer) {
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = layerIndex;
        layer.type = LayerComponent.TYPE_CLASSIC;
        layer.spatialEnabled = false;

        LayerMetaComponent meta = world.getMapper(LayerMetaComponent.class).create(layerEntity);
        meta.name = imageLayer.name();
        meta.description = "";
        meta.locked = false;

        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).create(layerEntity);
        visibility.visible = imageLayer.visible();
        visibility.culledByFrustum = true;
        visibility.inView = false;

        LayerParallaxComponent parallax = world.getMapper(LayerParallaxComponent.class).create(layerEntity);
        parallax.factorX = imageLayer.parallaxX();
        parallax.factorY = imageLayer.parallaxY();
    }

    private void createImageLayerSprite(World world,
                                        int layerIndex,
                                        TmxScenePlan scene,
                                        TmxImageLayerPlan imageLayer,
                                        ImportedImageAsset imageAsset,
                                        String sceneTag) {
        int spriteEntity = world.create();
        float spriteX = imageLayer.x() + imageLayer.offsetX();
        float spriteY = imageLayerSpriteY(scene, imageLayer, imageAsset);
        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(
                        imageAsset.assetId(),
                        sceneTag,
                        Math.max(1, imageAsset.width()),
                        Math.max(1, imageAsset.height()),
                        spriteX,
                        spriteY,
                        0f,
                        0f,
                        0,
                        BlendMode.ALPHA.id,
                        0,
                        imageLayer.originalName(),
                        layerIndex
                )
                .setTintRgba(tintForOpacity(imageLayer.opacity()));
        init.init(spriteEntity);
        if (imageLayer.repeatX() || imageLayer.repeatY()) {
            RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(spriteEntity);
            repeat.repeatX = imageLayer.repeatX();
            repeat.repeatY = imageLayer.repeatY();
        }
    }

    private static List<ImageCollectionTileSource> imageCollectionSources(TmxTilesetPlan tileset) {
        List<ImageCollectionTileSource> sources = new ArrayList<>();
        if (tileset == null || tileset.imageCollectionTiles() == null) {
            return sources;
        }
        for (var tile : tileset.imageCollectionTiles()) {
            if (tile == null) continue;
            sources.add(new ImageCollectionTileSource(
                    tile.localTileId(),
                    tile.imageFile(),
                    tile.imageSource(),
                    tile.imageWidth(),
                    tile.imageHeight()
            ));
        }
        return sources;
    }

    private static TilesetProfileImportSettings collectionProfileSettings(TmxScenePlan scene,
                                                                          TmxTilesetPlan tileset) {
        int tileWidth = tileset != null && tileset.tileWidth() > 0 ? tileset.tileWidth() : 32;
        int tileHeight = tileset != null && tileset.tileHeight() > 0 ? tileset.tileHeight() : 32;
        return new TilesetProfileImportSettings(
                tileWidth,
                tileHeight,
                scene != null && scene.tiledProjection() != null
                        ? scene.tiledProjection()
                        : SceneMetaRuntime.TiledProjection.ORTHO,
                TilesetAnchor.BOTTOM_CENTER,
                0,
                0,
                TilesetRenderSize.NATIVE
        );
    }

    private static float imageLayerSpriteY(TmxScenePlan scene,
                                           TmxImageLayerPlan imageLayer,
                                           ImportedImageAsset imageAsset) {
        if (scene != null && "orthogonal".equals(scene.orientation())) {
            float mapPixelHeight = scene.mapHeightCells() * (float) scene.tileHeight();
            return mapPixelHeight - imageLayer.y() - imageLayer.offsetY() - imageAsset.height();
        }
        return imageLayer.y() + imageLayer.offsetY();
    }

    private void populateTiles(World world,
                               int layerEntity,
                               TmxTileLayerPlan tileLayer,
                               Map<Integer, Map<Integer, Integer>> cellLogicalIdsByTileset) {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerEntity);
        for (TmxTileCellPlan cell : tileLayer.cells()) {
            Map<Integer, Integer> logicalIds = cellLogicalIdsByTileset.get(cell.tilesetPlanIndex());
            if (logicalIds == null) {
                throw new IllegalStateException("Missing imported tileset for cell gid " + cell.cleanGid());
            }
            Integer logicalId = logicalIds.get(cell.localTileId());
            if (logicalId == null || logicalId <= 0) {
                throw new IllegalStateException("Missing imported tile asset for local tile " + cell.localTileId());
            }
            int gx = TmxTileCoordinateMapper.pixscapeX(cell.sourceX());
            int gy = TmxTileCoordinateMapper.pixscapeY(tileLayer.height(), cell.sourceY());
            byte flags = TmxTileTransformSupport.toTileTransformFlags(cell.transform());
            tiled.data.setTile(gx, gy, logicalId, flags);
            TiledSparseStorageHelper.setTile(tiled, gx, gy, logicalId, flags);
        }
    }

    private void syncAtlasInputs(String sceneTag, Set<Integer> importedAssetIds) {
        Set<String> requiredPaths = new HashSet<>();
        for (Integer assetId : importedAssetIds) {
            if (assetId == null || assetId <= 0) continue;
            AssetMeta meta = assetDb.findById(assetId);
            if (meta == null || meta.sourceRelPath == null || meta.sourceRelPath.isBlank()) continue;
            requiredPaths.add(meta.sourceRelPath);
        }
        sceneAtlasInputService.syncSceneAtlasInput(cfg, sceneTag, projectDir, requiredPaths);
    }

    private TmxSceneImportResult failedResult(TmxImportPlanResult planResult,
                                              String sceneName,
                                              String sceneFileName,
                                              String sceneTag,
                                              RuntimeException failure,
                                              boolean rollbackSucceeded) {
        return new TmxSceneImportResult(
                rollbackSucceeded
                        ? TmxSceneImportStatus.FAILED_ROLLED_BACK
                        : TmxSceneImportStatus.FAILED_ROLLBACK_INCOMPLETE,
                planResult,
                sceneName,
                sceneFileName,
                sceneTag,
                0,
                0,
                0,
                0,
                planResult != null && planResult.preflightReport() != null
                        ? planResult.preflightReport().diagnostics()
                        : java.util.List.of(),
                failure,
                true,
                rollbackSucceeded,
                null
        );
    }

    private ImageSize readImageSize(FileHandle file) {
        try {
            Pixmap pixmap = new Pixmap(file);
            try {
                return new ImageSize(pixmap.getWidth(), pixmap.getHeight());
            } finally {
                pixmap.dispose();
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Image layer image cannot be imported: " + (file != null ? file.path() : "<null>"), ex);
        }
    }

    private static int tintForOpacity(float opacity) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f) & 0xFF;
        return (alpha << 24) | 0x00FFFFFF;
    }

    private record ImportAssetsResult(int importedTilesetCount,
                                      Map<Integer, Map<Integer, Integer>> cellLogicalIdsByTileset,
                                      Set<Integer> importedTileAssetIds,
                                      Map<Integer, ImportedImageAsset> imageAssetsBySourceLayer,
                                      Set<Integer> importedImageAssetIds) {
        private Set<Integer> importedAssetIds() {
            Set<Integer> ids = new HashSet<>(importedTileAssetIds);
            ids.addAll(importedImageAssetIds);
            return ids;
        }
    }

    private record ImportedImageAsset(int assetId, int width, int height) {
    }

    private record ImageSize(int width, int height) {
    }
}
