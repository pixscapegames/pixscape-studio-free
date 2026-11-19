package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.tiled.TiledProjection;

import java.util.*;

public final class TmxImportPlanner {

    private final TmxPreflightService preflightService;

    public TmxImportPlanner() {
        this(new TmxPreflightService());
    }

    TmxImportPlanner(TmxPreflightService preflightService) {
        this.preflightService = preflightService;
    }

    public TmxImportPlanResult plan(TmxImportPlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }

        TmxPreflightReport report = preflightService.analyze(new TmxPreflightRequest(request.tmxFile(), request.tiledProject()));
        if (report.hasBlockingDiagnostics()) {
            return new TmxImportPlanResult(TmxImportPlanStatus.PREFLIGHT_FAILED, report, null);
        }

        TmxImportPlan plan = buildPlan(request.tmxFile(), report);
        TmxImportPlanStatus status = report.diagnostics().stream()
                .anyMatch(d -> d.severity() == TmxDiagnosticSeverity.WARNING)
                ? TmxImportPlanStatus.PLAN_CREATED_WITH_WARNINGS
                : TmxImportPlanStatus.PLAN_CREATED;
        return new TmxImportPlanResult(status, report, plan);
    }

    private TmxImportPlan buildPlan(FileHandle tmxFile, TmxPreflightReport report) {
        List<TmxTilesetPlan> tilesets = buildTilesets(report.tilesets());
        Map<Integer, TmxTilesetPlan> tilesetByFirstGid = new LinkedHashMap<>();
        for (TmxTilesetPlan tileset : tilesets) {
            tilesetByFirstGid.put(tileset.firstGid(), tileset);
        }

        List<TmxLayerPlan> layers = new ArrayList<>();
        for (int i = 0; i < report.layers().size(); i++) {
            TmxLayerInfo layer = report.layers().get(i);
            if (layer instanceof TmxTileLayerInfo tileLayer) {
                layers.add(buildTileLayerPlan(i, tileLayer, tilesetByFirstGid));
            } else if (layer instanceof TmxObjectLayerInfo objectLayer) {
                layers.add(buildObjectLayerPlan(i, objectLayer, tilesets, report.mapInfo().orientation()));
            } else if (layer instanceof TmxImageLayerInfo imageLayer) {
                layers.add(buildImageLayerPlan(i, imageLayer));
            }
        }

        return new TmxImportPlan(
                buildScenePlan(tmxFile, report),
                tilesets,
                layers
        );
    }

    private TmxScenePlan buildScenePlan(FileHandle tmxFile, TmxPreflightReport report) {
        TmxMapInfo map = report.mapInfo();
        return new TmxScenePlan(
                proposedSceneName(tmxFile),
                report.sourcePath(),
                map.orientation(),
                projectionFor(map.orientation()),
                map.width(),
                map.height(),
                map.tileWidth(),
                map.tileHeight(),
                report.requiredTiledCells(),
                report.tileLayerCount(),
                report.nonEmptyTileCount(),
                map.properties(),
                map.objectPropertyReferences()
        );
    }

    private List<TmxTilesetPlan> buildTilesets(List<TmxTilesetInfo> infos) {
        List<TmxTilesetPlan> plans = new ArrayList<>();
        for (int i = 0; i < infos.size(); i++) {
            TmxTilesetInfo info = infos.get(i);
            plans.add(new TmxTilesetPlan(
                    i,
                    info.firstGid(),
                    info.name(),
                    info.sourcePath(),
                    info.resolvedImagePath(),
                    info.imageSource(),
                    info.imageWidth(),
                    info.imageHeight(),
                    info.tileWidth(),
                    info.tileHeight(),
                    info.tileCount(),
                    info.columns(),
                    info.spacing(),
                    info.margin(),
                    info.objectAlignment(),
                    info.tileOffsetX(),
                    info.tileOffsetY(),
                    info.external(),
                    0,
                    Math.max(info.tileCount(), 0),
                    info.imageCollectionTiles(),
                    info.tileAnimations(),
                    buildTileDefinitions(info.tileDefinitions())
            ));
        }
        return plans;
    }

    private static List<TmxTileDefinitionPlan> buildTileDefinitions(
            List<TmxTileDefinitionInfo> definitions) {
        List<TmxTileDefinitionPlan> plans = new ArrayList<>();
        for (TmxTileDefinitionInfo definition : definitions) {
            plans.add(new TmxTileDefinitionPlan(
                    definition.localTileId(),
                    definition.className(),
                    definition.legacyType(),
                    definition.propertiesForPlanning(),
                    definition.propertyPaths()
            ));
        }
        return plans;
    }

    private TmxTileLayerPlan buildTileLayerPlan(int sourceLayerIndex,
                                                TmxTileLayerInfo layer,
                                                Map<Integer, TmxTilesetPlan> tilesetByFirstGid) {
        List<TmxTileCellPlan> cells = new ArrayList<>();
        for (TmxTileCellInfo cell : layer.cells()) {
            TmxTilesetPlan tileset = tilesetByFirstGid.get(cell.tilesetFirstGid());
            int planIndex = tileset != null ? tileset.planIndex() : -1;
            cells.add(new TmxTileCellPlan(
                    cell.sourceX(),
                    cell.sourceY(),
                    cell.cleanGid(),
                    cell.rawGid(),
                    planIndex,
                    cell.tilesetFirstGid(),
                    cell.localTileId(),
                    new TmxTransformPlan(
                            cell.hasTransformFlags(),
                            cell.horizontalFlip(),
                            cell.verticalFlip(),
                            cell.diagonalFlip(),
                            cell.hexagonal120Flag()
                    )
            ));
        }

        return new TmxTileLayerPlan(
                layer.name(),
                layer.originalName(),
                sourceLayerIndex,
                layer.width(),
                layer.height(),
                layer.visible(),
                layer.parallaxX(),
                layer.parallaxY(),
                layer.offsetX(),
                layer.offsetY(),
                layer.opacity(),
                Math.max(0, layer.width()) * (long) Math.max(0, layer.height()),
                layer.nonEmptyTileCount(),
                cells,
                layer.properties(),
                layer.objectPropertyReferences()
        );
    }

    private TmxObjectLayerPlan buildObjectLayerPlan(int sourceLayerIndex,
                                                    TmxObjectLayerInfo layer,
                                                    List<TmxTilesetPlan> tilesets,
                                                    String orientation) {
        List<TmxObjectInfo> supported = new ArrayList<>();
        Map<TmxObjectInfo, Integer> sourceOrders = new IdentityHashMap<>();
        for (int i = 0; i < layer.objects().size(); i++) {
            TmxObjectInfo object = layer.objects().get(i);
            if (!isV1PlannableObject(object.kind())) continue;
            supported.add(object);
            sourceOrders.put(object, i);
        }

        Map<TmxObjectInfo, Integer> zIndices = new IdentityHashMap<>();
        if (layer.drawOrder() == TmxObjectDrawOrder.TOP_DOWN) {
            List<TmxObjectInfo> sorted = new ArrayList<>(supported);
            sorted.sort(Comparator.<TmxObjectInfo>comparingDouble(
                            object -> topDownKey(object, orientation))
                    .thenComparingInt(sourceOrders::get));
            for (int i = 0; i < sorted.size(); i++) zIndices.put(sorted.get(i), i);
        } else {
            for (TmxObjectInfo object : supported) {
                zIndices.put(object, sourceOrders.get(object));
            }
        }

        List<TmxObjectPlan> objects = new ArrayList<>();
        for (TmxObjectInfo object : supported) {
            int rawGid = 0;
            int cleanGid = 0;
            int tilesetPlanIndex = -1;
            int localTileId = -1;
            TmxTransformPlan tileTransform = null;
            TmxObjectAlignment alignment = null;
            int tileOffsetX = 0;
            int tileOffsetY = 0;
            int nativeTileWidth = 0;
            int nativeTileHeight = 0;
            TmxTileDefinitionPlan tileDefinition = null;

            if (object.kind() == TmxObjectKind.TILE) {
                rawGid = (int) (object.gid() & 0xffffffffL);
                TmxGidSupport.DecodedGid decoded = TmxGidSupport.decode(rawGid);
                cleanGid = decoded.cleanGid;
                TmxTilesetPlan tileset = resolveTilesetPlan(cleanGid, tilesets);
                if (tileset == null) {
                    throw new IllegalStateException("Preflight accepted an unresolved Tile Object GID: " + cleanGid);
                }
                tilesetPlanIndex = tileset.planIndex();
                localTileId = cleanGid - tileset.firstGid();
                tileTransform = new TmxTransformPlan(
                        TmxGidSupport.hasTransformFlags(rawGid),
                        decoded.flipH,
                        decoded.flipV,
                        decoded.flipD,
                        false
                );
                alignment = tileset.objectAlignment();
                tileOffsetX = tileset.tileOffsetX();
                tileOffsetY = tileset.tileOffsetY();
                nativeTileWidth = tileset.nativeTileWidth(localTileId);
                nativeTileHeight = tileset.nativeTileHeight(localTileId);
                tileDefinition = tileset.tileDefinition(localTileId);
            }

            String effectiveClassName = effectiveClassName(object, tileDefinition);
            String effectiveLegacyType = effectiveLegacyType(object, tileDefinition);
            PropertySet effectiveProperties = effectiveProperties(object, tileDefinition);

            objects.add(new TmxObjectPlan(
                    object.id(),
                    object.name(),
                    object.className(),
                    object.legacyType(),
                    object.x(),
                    object.y(),
                    object.width(),
                    object.height(),
                    object.rotation(),
                    object.visible(),
                    object.kind(),
                    object.points(),
                    sourceOrders.get(object),
                    zIndices.get(object),
                    rawGid,
                    cleanGid,
                    tilesetPlanIndex,
                    localTileId,
                    tileTransform,
                    alignment,
                    tileOffsetX,
                    tileOffsetY,
                    nativeTileWidth,
                    nativeTileHeight,
                    effectiveClassName,
                    effectiveLegacyType,
                    effectiveProperties,
                    object.objectPropertyReferences()
            ));
        }

        return new TmxObjectLayerPlan(
                layer.name(),
                layer.originalName(),
                sourceLayerIndex,
                layer.visible(),
                layer.parallaxX(),
                layer.parallaxY(),
                layer.offsetX(),
                layer.offsetY(),
                layer.opacity(),
                layer.drawOrder(),
                layer.propertiesForPlanning(),
                layer.objectPropertyReferences(),
                objects
        );
    }

    private static boolean isV1PlannableObject(TmxObjectKind kind) {
        return kind == TmxObjectKind.RECTANGLE
                || kind == TmxObjectKind.POINT
                || kind == TmxObjectKind.TILE
                || kind == TmxObjectKind.POLYGON
                || kind == TmxObjectKind.POLYLINE;
    }

    static float topDownKey(TmxObjectInfo object, String orientation) {
        return "isometric".equals(orientation) ? object.x() + object.y() : object.y();
    }

    private static TmxTilesetPlan resolveTilesetPlan(int cleanGid, List<TmxTilesetPlan> tilesets) {
        return TmxGidSupport.resolveTileset(
                cleanGid, tilesets, TmxTilesetPlan::firstGid, TmxTilesetPlan::tileCount);
    }

    private static String effectiveClassName(TmxObjectInfo object,
                                             TmxTileDefinitionPlan tile) {
        if (nonBlank(object.className())) return object.className();
        if (nonBlank(object.legacyType())) return null;
        return tile != null && nonBlank(tile.className()) ? tile.className() : null;
    }

    private static String effectiveLegacyType(TmxObjectInfo object,
                                              TmxTileDefinitionPlan tile) {
        if (nonBlank(object.className())) return object.legacyType();
        if (nonBlank(object.legacyType())) return object.legacyType();
        if (tile == null || nonBlank(tile.className())) return tile != null ? tile.legacyType() : null;
        return tile.legacyType();
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static PropertySet effectiveProperties(TmxObjectInfo object, TmxTileDefinitionPlan tile) {
        return tile == null ? object.propertiesForPlanning()
                : mergeProperties(tile.properties(), object.propertiesForPlanning());
    }

    private static PropertySet mergeProperties(PropertySet base, PropertySet overrides) {
        PropertySet result = base.copy();
        com.badlogic.gdx.utils.Array<String> names = new com.badlogic.gdx.utils.Array<>();
        overrides.copyNamesTo(names);
        for (String name : names) {
            var value = overrides.valueCopy(name);
            var previous = base.valueCopy(name);
            if (value.type() == PropertyType.CLASS && previous != null && previous.type() == PropertyType.CLASS
                    && value.className().equals(previous.className())) {
                result.putClass(name, value.className(), mergeProperties(
                        previous.classPropertiesCopy(), value.classPropertiesCopy()));
            } else {
                // Lists replace the entire inherited value, including an explicit empty list.
                result.put(name, value);
            }
        }
        return result;
    }

    private TmxImageLayerPlan buildImageLayerPlan(int sourceLayerIndex,
                                                  TmxImageLayerInfo layer) {
        return new TmxImageLayerPlan(
                layer.name(),
                layer.originalName(),
                sourceLayerIndex,
                layer.visible(),
                layer.parallaxX(),
                layer.parallaxY(),
                layer.offsetX(),
                layer.offsetY(),
                layer.opacity(),
                layer.x(),
                layer.y(),
                layer.repeatX(),
                layer.repeatY(),
                layer.imageSource(),
                layer.imageWidth(),
                layer.imageHeight(),
                layer.resolvedImagePath(),
                layer.properties(),
                layer.objectPropertyReferences()
        );
    }

    private static TiledProjection projectionFor(String orientation) {
        if ("isometric".equals(orientation)) {
            return TiledProjection.ISO;
        }
        return TiledProjection.ORTHO;
    }

    private static String proposedSceneName(FileHandle tmxFile) {
        String name = tmxFile != null ? tmxFile.nameWithoutExtension() : "";
        name = name.replaceAll("[^A-Za-z0-9 _-]+", " ").trim();
        name = name.replaceAll("\\s+", " ");
        if (name.isBlank()) {
            return "Imported TMX";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
