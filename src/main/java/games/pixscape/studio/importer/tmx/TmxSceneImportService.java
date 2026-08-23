package games.pixscape.studio.importer.tmx;

import com.artemis.World;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.helper.TiledSparseStorageHelper;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.TileAnimationsIO;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.asset.TiledAnimationImportSupport;
import games.pixscape.studio.service.asset.TilesetAssetImportService;
import games.pixscape.studio.service.asset.TilesetAssetImportService.*;
import games.pixscape.studio.service.atlas.SceneAtlasInputService;
import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityService;

import java.util.*;

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
        TmxSceneImportSession session = beginImport(request);
        try {
            session.prepare();
            if (session.finished()) return session.result();
            session.createScene();
            session.importAssets();
            session.materializeAndSaveScene();
            session.updateAtlas();
            return session.persistAndFinish();
        } catch (RuntimeException ex) {
            if (!session.mutationStarted()) throw ex;
            return session.rollback(ex);
        }
    }

    public TmxSceneImportSession beginImport(TmxSceneImportRequest request) {
        if (request == null) throw new IllegalArgumentException("request is null");
        if (request.tmxFile() == null) throw new IllegalArgumentException("request.tmxFile is null");
        return new TmxSceneImportSession(this, request);
    }

    ProjectConfig config() {
        return cfg;
    }

    FileHandle projectDir() {
        return projectDir;
    }

    AssetMetaDatabase assetDatabase() {
        return assetDb;
    }

    TmxImportPlanResult plan(TmxSceneImportRequest request) {
        return planner.plan(new TmxImportPlanRequest(request.tmxFile()));
    }

    TmxSceneImportResult validateBeforeMutation(TmxImportPlanResult planResult) {
        return null;
    }

    String sceneName(TmxSceneImportRequest request, TmxImportPlan plan) {
        if (request.requestedSceneName() != null && !request.requestedSceneName().isBlank()) {
            return request.requestedSceneName().trim();
        }
        return plan.scene().proposedSceneName();
    }

    String uniqueSceneName(String desired) {
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

    void configureSceneMeta(SceneMeta meta, TmxScenePlan scene) {
        meta.tiledEnabled = true;
        meta.tileWidth = scene.tileWidth();
        meta.tileHeight = scene.tileHeight();
        meta.chunkSize = Math.max(1, meta.chunkSize);
        meta.tiledProjection = scene.tiledProjection() != null
                ? scene.tiledProjection()
                : SceneMetaRuntime.TiledProjection.ORTHO;
        runtimeAvailabilityService.data(meta);
    }

    ImportAssetsResult importAssets(TmxImportPlan plan, SceneMeta meta) {
        Map<Integer, Map<Integer, Integer>> cellLogicalIdsByTileset = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset = new HashMap<>();
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
            staticTileAssetIdsByTileset.put(
                    tileset.planIndex(), Map.copyOf(result.localTileAssetIds()));
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

        for (TmxLayerPlan layer : plan.layers()) {
            if (!(layer instanceof TmxObjectLayerPlan objectLayer)) continue;
            for (TmxObjectPlan object : objectLayer.objects()) {
                if (object.kind() != TmxObjectKind.TILE) continue;
                Integer assetId = staticTileAssetId(staticTileAssetIdsByTileset, object);
                runtimeAvailabilityService.addSprite(meta, assetId);
            }
        }

        return new ImportAssetsResult(
                importedTilesetCount,
                cellLogicalIdsByTileset,
                staticTileAssetIdsByTileset,
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
        String newName = base + "__a" + meta.id() + (extension == null || extension.isBlank() ? "" : "." + extension);
        FileHandle dst = imagesRoot.child(newName);
        if (!dst.exists()) {
            source.copyTo(dst);
        }
        assetDb.updateSourceRelPath(
                meta.id(),
                StudioFs.DIR_ORIG_IMAGES + "/" + newName
        );

        int width = imageLayer.imageWidth() > 0 ? imageLayer.imageWidth() : imageSize.width();
        int height = imageLayer.imageHeight() > 0 ? imageLayer.imageHeight() : imageSize.height();
        return new ImportedImageAsset(meta.id(), width, height);
    }

    void populateImportedWorld(World world,
            IdentityRegistry identityRegistry, TmxImportPlan plan,
            Map<Integer, Map<Integer, Integer>> cellLogicalIdsByTileset,
            Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset,
            Map<Integer, ImportedImageAsset> imageAssetsBySourceLayer,
            String sceneTag) {
        int layerIndex = 0;
        for (TmxLayerPlan layerPlan : plan.layers()) {
            if (layerPlan instanceof TmxTileLayerPlan tileLayer) {
                int layerEntity = world.create();
                createTileLayerComponents(world, layerEntity, layerIndex, tileLayer, plan.scene(), sceneTag);
                identityRegistry.ensureStableId(layerEntity);
                populateTiles(world, layerEntity, tileLayer, cellLogicalIdsByTileset);
                layerIndex++;
            } else if (layerPlan instanceof TmxImageLayerPlan imageLayer) {
                ImportedImageAsset imageAsset = imageAssetsBySourceLayer.get(imageLayer.sourceLayerIndex());
                if (imageAsset == null) {
                    throw new IllegalStateException("Missing imported image asset for layer " + imageLayer.name());
                }
                int layerEntity = world.create();
                createClassicLayerComponents(world, layerEntity, layerIndex, imageLayer);
                identityRegistry.ensureStableId(layerEntity);
                createImageLayerSprite(world, identityRegistry, layerIndex,
                        plan.scene(), imageLayer, imageAsset, sceneTag);
                layerIndex++;
            } else if (layerPlan instanceof TmxObjectLayerPlan objectLayer) {
                requireOrthogonalObjectLayer(plan.scene(), objectLayer);
                int layerEntity = world.create();
                createObjectLayerComponents(world, layerEntity, layerIndex, objectLayer);
                identityRegistry.ensureStableId(layerEntity);
                populateObjects(world, identityRegistry, layerIndex, plan.scene(), objectLayer,
                        staticTileAssetIdsByTileset, sceneTag);
                layerIndex++;
            }
        }
        world.process();
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

    private void createObjectLayerComponents(World world,
                                             int layerEntity,
                                             int layerIndex,
                                             TmxObjectLayerPlan objectLayer) {
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = layerIndex;
        layer.type = LayerComponent.TYPE_CLASSIC;
        layer.spatialEnabled = false;

        LayerMetaComponent meta = world.getMapper(LayerMetaComponent.class).create(layerEntity);
        meta.name = objectLayer.name();
        meta.description = "";
        meta.locked = false;

        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).create(layerEntity);
        visibility.visible = objectLayer.visible();
        visibility.culledByFrustum = true;
        visibility.inView = false;

        LayerParallaxComponent parallax = world.getMapper(LayerParallaxComponent.class).create(layerEntity);
        parallax.factorX = objectLayer.parallaxX();
        parallax.factorY = objectLayer.parallaxY();

        copyCustomProperties(world, layerEntity, objectLayer.properties());
    }

    private void populateObjects(World world,
                                 IdentityRegistry identityRegistry,
                                 int layerIndex,
                                 TmxScenePlan scene,
                                 TmxObjectLayerPlan objectLayer,
                                 Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset,
                                 String sceneTag) {
        float mapPixelHeight = scene.mapHeightCells() * (float) scene.tileHeight();
        for (TmxObjectPlan object : objectLayer.objects()) {
            int objectEntity = world.create();
            if (object.kind() == TmxObjectKind.TILE) {
                createTileObjectComponents(world, objectEntity, layerIndex, mapPixelHeight,
                        objectLayer, object, staticTileAssetIdsByTileset, sceneTag);
            } else {
                createDataObjectComponents(world, objectEntity, layerIndex,
                        mapPixelHeight, objectLayer, object);
            }
            identityRegistry.setName(objectEntity, object.name());
            identityRegistry.ensureStableId(objectEntity);
        }
    }

    private void createDataObjectComponents(World world,
                                            int objectEntity,
                                            int layerIndex,
                                            float mapPixelHeight,
                                            TmxObjectLayerPlan objectLayer,
                                            TmxObjectPlan object) {
        TransformComponent transform = world.getMapper(TransformComponent.class).create(objectEntity);
        transform.x = object.x() + objectLayer.offsetX();
        transform.y = mapPixelHeight - object.y() - objectLayer.offsetY();
        transform.rotationRad = -object.rotation() * MathUtils.degreesToRadians;
        transform.scaleX = 1f;
        transform.scaleY = 1f;

        if (object.kind() == TmxObjectKind.RECTANGLE) {
            transform.originX = 0f;
            transform.originY = object.height();
            DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(objectEntity);
            dimensions.width = object.width();
            dimensions.height = object.height();
        } else if (object.kind() == TmxObjectKind.POINT) {
            transform.originX = 0f;
            transform.originY = 0f;
        } else {
            throw new IllegalStateException(
                    "Unsupported Tiled Object Layer materialization kind: " + object.kind());
        }
        transform.refreshCaches();

        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(objectEntity);
        index.layerIndex = layerIndex;
        index.zIndex = object.zIndex();

        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).create(objectEntity);
        visibility.visible = object.visible();
        visibility.culledByFrustum = false;
        visibility.inView = true;

        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).create(objectEntity);
        meta.note = "";
        meta.kind = EntityKind.UNKNOWN;

        attachObjectMetadata(world, objectEntity, object);
    }

    private void createTileObjectComponents(World world,
                                            int objectEntity,
                                            int layerIndex,
                                            float mapPixelHeight,
                                            TmxObjectLayerPlan objectLayer,
                                            TmxObjectPlan object,
                                            Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset,
                                            String sceneTag) {
        int assetId = staticTileAssetId(staticTileAssetIdsByTileset, object);
        float width = object.width() > 0f ? object.width() : object.nativeTileWidth();
        float height = object.height() > 0f ? object.height() : object.nativeTileHeight();
        if (width <= 0f || height <= 0f) {
            throw new IllegalStateException("Tile Object has no usable authored or native size.");
        }

        float anchorX = object.tileObjectAlignment().anchorX();
        float anchorY = object.tileObjectAlignment().anchorY();
        float baseOriginX = anchorX * width - object.tileOffsetX();
        float baseOriginY = (1f - anchorY) * height + object.tileOffsetY();
        TmxTileTransformSupport.TileObjectTransform tiledTransform =
                TmxTileTransformSupport.decomposeTileObject(
                        width, height, baseOriginX, baseOriginY, object.tileTransform());

        GenericEntityInitializer init = new GenericEntityInitializer(world)
                .configureStandaloneSprite(
                        assetId,
                        sceneTag,
                        Math.max(1, object.nativeTileWidth()),
                        Math.max(1, object.nativeTileHeight()),
                        object.x() + objectLayer.offsetX(),
                        mapPixelHeight - object.y() - objectLayer.offsetY(),
                        tiledTransform.originX(),
                        tiledTransform.originY(),
                        0,
                        BlendMode.ALPHA.id,
                        0,
                        object.name(),
                        layerIndex
                )
                .setTintRgba(tintForOpacity(objectLayer.opacity()));
        init.init(objectEntity);

        TransformComponent transform = world.getMapper(TransformComponent.class).get(objectEntity);
        transform.rotationRad = -object.rotation() * MathUtils.degreesToRadians
                + tiledTransform.rotationOffsetRad();
        transform.scaleX = tiledTransform.scaleX();
        transform.scaleY = tiledTransform.scaleY();
        transform.refreshCaches();

        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(objectEntity);
        dimensions.width = width;
        dimensions.height = height;

        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(objectEntity);
        index.zIndex = object.zIndex();

        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).get(objectEntity);
        visibility.visible = object.visible();
        attachObjectMetadata(world, objectEntity, object);
    }

    private static int staticTileAssetId(
            Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset,
            TmxObjectPlan object) {
        Map<Integer, Integer> localIds = staticTileAssetIdsByTileset.get(object.tilesetPlanIndex());
        Integer assetId = localIds != null ? localIds.get(object.localTileId()) : null;
        if (assetId == null || assetId <= 0) {
            throw new IllegalStateException(
                    "Missing imported static tile asset for local tile " + object.localTileId());
        }
        return assetId;
    }

    private static void attachObjectMetadata(World world,
                                             int objectEntity,
                                             TmxObjectPlan object) {
        String classificationTag = classificationTag(object);
        if (classificationTag != null) {
            PixscapeTagComponent tags = world.getMapper(PixscapeTagComponent.class).create(objectEntity);
            tags.tags.add(classificationTag);
        }

        copyCustomProperties(world, objectEntity, object.properties());
    }

    static String classificationTag(TmxObjectPlan object) {
        if (object == null) return null;
        String modernClass = normalizedTag(object.effectiveClassName());
        return modernClass != null ? modernClass : normalizedTag(object.effectiveLegacyType());
    }

    private static String normalizedTag(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void copyCustomProperties(World world,
                                             int entity,
                                             PropertySet properties) {
        if (properties == null || properties.isEmpty()) return;
        CustomPropertiesComponent component = world
                .getMapper(CustomPropertiesComponent.class)
                .create(entity);
        component.properties.copyFrom(properties);
    }

    private static void requireOrthogonalObjectLayer(TmxScenePlan scene,
                                                     TmxObjectLayerPlan objectLayer) {
        if (scene == null || !"orthogonal".equals(scene.orientation())) {
            throw new IllegalStateException(
                    "Tiled Object Layer materialization requires an orthogonal map: "
                            + objectLayer.name());
        }
    }

    private void createImageLayerSprite(World world,
                                        IdentityRegistry identityRegistry,
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
        identityRegistry.ensureStableId(spriteEntity);
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
        TiledSparseStorageHelper.NewLayerStorageBuilder sparseStorage =
                TiledSparseStorageHelper.beginNewLayerStorage(tiled, tileLayer.nonEmptyCellCount());
        tiled.data.beginContentMutation();
        try {
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
                sparseStorage.append(gx, gy, logicalId, flags);
            }
        } finally {
            tiled.data.endContentMutation();
        }
    }

    void syncAtlasInputs(String sceneTag, Set<Integer> importedAssetIds) {
        Set<String> requiredPaths = new HashSet<>();
        for (Integer assetId : importedAssetIds) {
            if (assetId == null || assetId <= 0) continue;
            AssetMeta meta = assetDb.findById(assetId);
            if (meta == null || meta.sourceRelPath() == null || meta.sourceRelPath().isBlank()) continue;
            requiredPaths.add(meta.sourceRelPath());
        }
        sceneAtlasInputService.syncSceneAtlasInput(cfg, sceneTag, projectDir, requiredPaths);
    }

    TmxSceneImportResult failedResult(TmxImportPlanResult planResult,
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

    record ImportAssetsResult(int importedTilesetCount,
                                      Map<Integer, Map<Integer, Integer>> cellLogicalIdsByTileset,
                                      Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset,
                                      Set<Integer> importedTileAssetIds,
                                      Map<Integer, ImportedImageAsset> imageAssetsBySourceLayer,
                                      Set<Integer> importedImageAssetIds) {
        Set<Integer> importedAssetIds() {
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
