package games.pixscape.studio.importer.tmx;

import com.artemis.World;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.component.TiledObjectLayerComponent;
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

    void initializeSceneRuntimeAvailability(SceneMeta meta) {
        runtimeAvailabilityService.data(meta);
    }

    ImportAssetsResult importAssets(TmxImportPlan plan, SceneMeta meta) {
        Map<Integer, Map<Integer, Integer>> cellLogicalIdsByTileset = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset = new HashMap<>();
        Map<Integer, Map<Integer, Integer>> animationIdsByTileset = new HashMap<>();
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
            animationIdsByTileset.put(tileset.planIndex(), Map.copyOf(animationIds));
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
                Map.copyOf(animationIdsByTileset),
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
            Map<Integer, Map<Integer, Integer>> animationIdsByTileset,
            Map<Integer, ImportedImageAsset> imageAssetsBySourceLayer,
            String sceneTag) {
        int layerIndex = 0;
        Map<Integer, Integer> stableIdsBySourceObjectId = new HashMap<>();
        List<PendingObjectProperties> pendingProperties = new ArrayList<>();
        for (TmxLayerPlan layerPlan : plan.layers()) {
            if (layerPlan instanceof TmxTileLayerPlan tileLayer) {
                int layerEntity = world.create();
                createTileHostLayerComponents(world, layerEntity, layerIndex, tileLayer);
                identityRegistry.ensureStableId(layerEntity);
                int mapEntity = world.create();
                createTileMapComponents(world, mapEntity, layerIndex, tileLayer, plan.scene(), sceneTag);
                identityRegistry.setName(mapEntity, "Map");
                identityRegistry.ensureStableId(mapEntity);
                populateTiles(world, mapEntity, tileLayer, cellLogicalIdsByTileset);
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
                int layerEntity = world.create();
                createObjectLayerComponents(world, layerEntity, layerIndex, objectLayer);
                identityRegistry.ensureStableId(layerEntity);
                pendingProperties.add(new PendingObjectProperties(layerEntity,
                        objectLayer.properties(), objectLayer.objectPropertyReferences()));
                populateObjects(world, identityRegistry, layerIndex, plan.scene(), objectLayer,
                        staticTileAssetIdsByTileset, animationIdsByTileset, sceneTag,
                        stableIdsBySourceObjectId, pendingProperties);
                layerIndex++;
            }
        }
        finalizeObjectProperties(world, pendingProperties, stableIdsBySourceObjectId);
        world.process();
    }

    private void createTileHostLayerComponents(World world,
                                               int layerEntity,
                                               int layerIndex,
                                               TmxTileLayerPlan tileLayer) {
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

    }

    private void createTileMapComponents(World world,
                                         int mapEntity,
                                         int layerIndex,
                                         TmxTileLayerPlan tileLayer,
                                         TmxScenePlan scene,
                                         String sceneTag) {
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(mapEntity);
        index.layerIndex = layerIndex;
        index.zIndex = 0;
        TransformComponent transform = world.getMapper(TransformComponent.class).create(mapEntity);
        transform.x = tileLayer.offsetX();
        transform.y = tileLayer.offsetY();
        transform.rotationRad = 0f;
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        transform.refreshCaches();
        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).create(mapEntity);
        visibility.visible = true;
        visibility.culledByFrustum = false;
        visibility.inView = true;
        EntityMetaComponent entityMeta = world.getMapper(EntityMetaComponent.class).create(mapEntity);
        entityMeta.kind = EntityKind.TILED_MAP;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(mapEntity);
        tiled.projection = scene.tiledProjection() != null
                ? scene.tiledProjection()
                : TiledProjection.ORTHO;
        tiled.tileWidth = scene.tileWidth();
        tiled.tileHeight = scene.tileHeight();
        tiled.mapWidthCells = tileLayer.width();
        tiled.mapHeightCells = tileLayer.height();
        tiled.chunkSize = 16;
        tiled.originX = tileLayer.offsetX();
        tiled.originY = tileLayer.offsetY();
        tiled.spatialEnabled = false;
        tiled.defaultTileAltitude = 0f;
        tiled.defaultTileHeight = 0f;
        tiled.atlasTag = sceneTag;
        tiled.data = tiled.createMapData();
        // The temporary host layer remains the visibility authority during Stage 1.
        tiled.data.visible = true;
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

        world.getMapper(TiledObjectLayerComponent.class).create(layerEntity);
    }

    private void populateObjects(World world,
                                 IdentityRegistry identityRegistry,
                                 int layerIndex,
                                 TmxScenePlan scene,
                                 TmxObjectLayerPlan objectLayer,
                                 Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset,
                                 Map<Integer, Map<Integer, Integer>> animationIdsByTileset,
                                 String sceneTag,
                                 Map<Integer, Integer> stableIdsBySourceObjectId,
                                 List<PendingObjectProperties> pendingProperties) {
        for (TmxObjectPlan object : objectLayer.objects()) {
            int objectEntity = world.create();
            if (object.kind() == TmxObjectKind.TILE) {
                createTileObjectComponents(world, objectEntity, layerIndex, scene,
                        objectLayer, object, staticTileAssetIdsByTileset,
                        animationIdsByTileset, sceneTag);
            } else {
                createDataObjectComponents(world, objectEntity, layerIndex,
                        scene, objectLayer, object);
            }
            identityRegistry.setName(objectEntity, object.name());
            identityRegistry.ensureStableId(objectEntity);
            if (object.hasPositiveSourceId()) {
                stableIdsBySourceObjectId.put(object.sourceId(),
                        world.getMapper(PixscapeIdentityComponent.class).get(objectEntity).stableId);
            }
            pendingProperties.add(new PendingObjectProperties(objectEntity,
                    object.properties(), object.objectPropertyReferences()));
        }
    }

    private void createDataObjectComponents(World world,
                                            int objectEntity,
                                            int layerIndex,
                                            TmxScenePlan scene,
                                            TmxObjectLayerPlan objectLayer,
                                            TmxObjectPlan object) {
        TransformComponent transform = world.getMapper(TransformComponent.class).create(objectEntity);
        TmxObjectCoordinateMapper.Coordinate position = TmxObjectCoordinateMapper.absolute(
                scene, object.x(), object.y(), objectLayer.offsetX(), objectLayer.offsetY());
        transform.x = position.x();
        transform.y = position.y();
        boolean bakeIsoGeometryRotation = isometricDataGeometry(scene, object.kind());
        // Tiled rotates ISO object geometry in projected screen space. Pixscape's standard
        // rotation cannot represent that non-uniform basis conversion, so bake it into vertices.
        transform.rotationRad = bakeIsoGeometryRotation
                ? 0f
                : -object.rotation() * MathUtils.degreesToRadians;
        transform.scaleX = 1f;
        transform.scaleY = 1f;

        if (object.kind() == TmxObjectKind.RECTANGLE) {
            if ("isometric".equals(scene.orientation())) {
                createPathObjectGeometry(world, objectEntity, transform, scene,
                        rectanglePoints(object), true, object.rotation());
            } else {
                DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(objectEntity);
                dimensions.width = object.width();
                dimensions.height = object.height();
                centerRectangleTransformFromTiledPivot(transform, dimensions.width, dimensions.height);
                // Bounds are editable/pickable geometry, not rendering state.
                world.getMapper(AABBComponent.class).create(objectEntity);
                world.getMapper(OrientedBoundsComponent.class).create(objectEntity);
            }
        } else if (object.kind() == TmxObjectKind.POINT) {
            transform.originX = 0f;
            transform.originY = 0f;
        } else if (object.kind() == TmxObjectKind.POLYGON
                || object.kind() == TmxObjectKind.POLYLINE) {
            createPathObjectGeometry(world, objectEntity, transform, scene,
                    object.points(), object.kind() == TmxObjectKind.POLYGON, object.rotation());
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
        meta.kind = switch (object.kind()) {
            case RECTANGLE -> EntityKind.TILED_RECTANGLE;
            case POINT -> EntityKind.TILED_POINT;
            case POLYGON -> EntityKind.POLYGON;
            case POLYLINE -> EntityKind.POLYLINE;
            default -> throw new IllegalStateException(
                    "Unsupported Tiled Object Layer materialization kind: " + object.kind());
        };

        attachObjectMetadata(world, objectEntity, object);
    }

    private static void createPathObjectGeometry(World world,
                                                 int objectEntity,
                                                 TransformComponent transform,
                                                 TmxScenePlan scene,
                                                 List<TmxObjectPoint> points,
                                                 boolean polygon,
                                                 float tiledRotationDeg) {
        if (points.isEmpty()) {
            throw new IllegalStateException("Path Object has no parsed points.");
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (TmxObjectPoint point : points) {
            TmxObjectCoordinateMapper.Coordinate projected =
                    localPathPoint(scene, point, tiledRotationDeg);
            float x = projected.x();
            float y = projected.y();
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        float width = maxX - minX;
        float height = maxY - minY;
        float[] vertices = new float[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            TmxObjectPoint point = points.get(i);
            TmxObjectCoordinateMapper.Coordinate projected =
                    localPathPoint(scene, point, tiledRotationDeg);
            vertices[i * 2] = projected.x() - minX;
            vertices[i * 2 + 1] = projected.y() - minY;
        }

        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(objectEntity);
        dimensions.width = width;
        dimensions.height = height;
        transform.originX = width * 0.5f;
        transform.originY = height * 0.5f;

        float rawCenterX = minX + transform.originX;
        float rawCenterY = minY + transform.originY;
        float cos = MathUtils.cos(transform.rotationRad);
        float sin = MathUtils.sin(transform.rotationRad);
        transform.x += cos * rawCenterX - sin * rawCenterY;
        transform.y += sin * rawCenterX + cos * rawCenterY;

        if (polygon) {
            world.getMapper(PolygonComponent.class).create(objectEntity).setVertices(vertices);
        } else {
            world.getMapper(PolylineComponent.class).create(objectEntity).setVertices(vertices);
        }
        world.getMapper(AABBComponent.class).create(objectEntity);
        world.getMapper(OrientedBoundsComponent.class).create(objectEntity);
    }

    private static TmxObjectCoordinateMapper.Coordinate localPathPoint(TmxScenePlan scene,
                                                                         TmxObjectPoint point,
                                                                         float tiledRotationDeg) {
        return "isometric".equals(scene.orientation())
                ? TmxObjectCoordinateMapper.localWithTiledRotation(
                        scene, point.x(), point.y(), tiledRotationDeg)
                : TmxObjectCoordinateMapper.local(scene, point.x(), point.y());
    }

    private static boolean isometricDataGeometry(TmxScenePlan scene, TmxObjectKind kind) {
        return "isometric".equals(scene.orientation())
                && (kind == TmxObjectKind.RECTANGLE
                || kind == TmxObjectKind.POLYGON
                || kind == TmxObjectKind.POLYLINE);
    }

    private static List<TmxObjectPoint> rectanglePoints(TmxObjectPlan object) {
        return List.of(
                new TmxObjectPoint(0f, 0f),
                new TmxObjectPoint(object.width(), 0f),
                new TmxObjectPoint(object.width(), object.height()),
                new TmxObjectPoint(0f, object.height())
        );
    }

    /**
     * Converts Tiled's top-left rectangle pivot to Pixscape's centered authoring pivot.
     * The translation is rotated with the rectangle so its world-space geometry is unchanged.
     */
    static void centerRectangleTransformFromTiledPivot(TransformComponent transform,
                                                        float width,
                                                        float height) {
        float dx = width * 0.5f;
        float dy = -height * 0.5f;
        float cos = MathUtils.cos(transform.rotationRad);
        float sin = MathUtils.sin(transform.rotationRad);
        transform.x += cos * dx - sin * dy;
        transform.y += sin * dx + cos * dy;
        transform.originX = dx;
        transform.originY = height * 0.5f;
    }

    private void createTileObjectComponents(World world,
                                            int objectEntity,
                                            int layerIndex,
                                            TmxScenePlan scene,
                                            TmxObjectLayerPlan objectLayer,
                                            TmxObjectPlan object,
                                            Map<Integer, Map<Integer, Integer>> staticTileAssetIdsByTileset,
                                            Map<Integer, Map<Integer, Integer>> animationIdsByTileset,
                                            String sceneTag) {
        int assetId = staticTileAssetId(staticTileAssetIdsByTileset, object);
        float width = object.width() > 0f ? object.width() : object.nativeTileWidth();
        float height = object.height() > 0f ? object.height() : object.nativeTileHeight();
        if (width <= 0f || height <= 0f) {
            throw new IllegalStateException("Tile Object has no usable authored or native size.");
        }

        TmxObjectAlignment alignment = effectiveTileObjectAlignment(object.tileObjectAlignment(), scene);
        TmxObjectCoordinateMapper.Coordinate position = TmxObjectCoordinateMapper.absolute(
                scene, object.x(), object.y(), objectLayer.offsetX(), objectLayer.offsetY());
        float anchorX = alignment.anchorX();
        float anchorY = alignment.anchorY();
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
                        position.x(),
                        position.y(),
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

        Map<Integer, Integer> tilesetAnimationIds =
                animationIdsByTileset.get(object.tilesetPlanIndex());
        Integer animationId = tilesetAnimationIds != null
                ? tilesetAnimationIds.get(object.localTileId())
                : null;
        if (animationId != null && animationId > 0) {
            TiledAnimationComponent animation = world.getMapper(TiledAnimationComponent.class)
                    .create(objectEntity);
            animation.animationId = animationId;
        }
        attachObjectMetadata(world, objectEntity, object);
    }

    static TmxObjectAlignment effectiveTileObjectAlignment(TmxObjectAlignment alignment,
                                                            TmxScenePlan scene) {
        if (alignment != TmxObjectAlignment.UNSPECIFIED) return alignment;
        return "isometric".equals(scene.orientation())
                ? TmxObjectAlignment.BOTTOM
                : TmxObjectAlignment.BOTTOM_LEFT;
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

    }

    private static void finalizeObjectProperties(World world,
                                                 List<PendingObjectProperties> pendingProperties,
                                                 Map<Integer, Integer> stableIdsBySourceObjectId) {
        for (PendingObjectProperties pending : pendingProperties) {
            PropertySet properties = finalizeObjectProperties(
                    pending.properties(), pending.references(), stableIdsBySourceObjectId);
            copyCustomProperties(world, pending.entityId(), properties);
        }
    }

    static PropertySet finalizeObjectProperties(PropertySet base,
                                                List<TmxObjectPropertyReference> references,
                                                Map<Integer, Integer> stableIdsBySourceObjectId) {
        PropertySet result = base != null ? base.copy() : new PropertySet();
        for (TmxObjectPropertyReference reference : references) {
            int stableId = reference.sourceObjectId() == 0 ? -1
                    : requiredStableId(reference, stableIdsBySourceObjectId);
            putObjectReferenceAtPath(result, reference.path(), 0, stableId);
        }
        return result;
    }

    private static int requiredStableId(TmxObjectPropertyReference reference,
                                        Map<Integer, Integer> stableIdsBySourceObjectId) {
        Integer stableId = stableIdsBySourceObjectId.get(reference.sourceObjectId());
        if (stableId == null || stableId <= 0) {
            throw new IllegalStateException("Unable to resolve Tiled object reference #"
                    + reference.sourceObjectId() + " at " + reference.location());
        }
        return stableId;
    }

    private static void putObjectReferenceAtPath(PropertySet properties,
                                                 List<String> path,
                                                 int index,
                                                 int stableId) {
        String name = path.get(index);
        if (index == path.size() - 1) {
            properties.putObjectStableId(name, stableId);
            return;
        }
        PropertyValue value = properties.valueCopy(name);
        if (value == null || value.type() != PropertyType.CLASS) {
            throw new IllegalStateException("OBJECT property path does not resolve through CLASS: "
                    + String.join(".", path));
        }
        PropertySet members = value.classPropertiesCopy();
        putObjectReferenceAtPath(members, path, index + 1, stableId);
        properties.putClass(name, value.className(), members);
    }

    private record PendingObjectProperties(int entityId,
                                           PropertySet properties,
                                           List<TmxObjectPropertyReference> references) {
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
                        : TiledProjection.ORTHO,
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
                                      Map<Integer, Map<Integer, Integer>> animationIdsByTileset,
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
