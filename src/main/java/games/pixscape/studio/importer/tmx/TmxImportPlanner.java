package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.api.ClassProperty;
import games.pixscape.runtime.api.CustomProperties;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;

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

        TmxPreflightReport report = preflightService.analyze(new TmxPreflightRequest(request.tmxFile()));
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
                layers.add(buildObjectLayerPlan(i, objectLayer, tilesets));
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
                report.nonEmptyTileCount()
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
                cells
        );
    }

    private TmxObjectLayerPlan buildObjectLayerPlan(int sourceLayerIndex,
                                                    TmxObjectLayerInfo layer,
                                                    List<TmxTilesetPlan> tilesets) {
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
            sorted.sort(Comparator
                    .comparingDouble(TmxObjectInfo::y)
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
                    effectiveProperties
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
                objects
        );
    }

    private static boolean isV1PlannableObject(TmxObjectKind kind) {
        return kind == TmxObjectKind.RECTANGLE
                || kind == TmxObjectKind.POINT
                || kind == TmxObjectKind.TILE;
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

    private static PropertySet effectiveProperties(TmxObjectInfo object,
                                                   TmxTileDefinitionPlan tile) {
        if (tile == null) return object.propertiesForPlanning();
        return mergeProperties(
                reader(tile.properties()), tile.propertyPaths(),
                reader(object.propertiesForPlanning()), object.propertyPaths(),
                List.of());
    }

    private static PropertySet mergeProperties(PropertyReader base,
                                               List<List<String>> basePaths,
                                               PropertyReader override,
                                               List<List<String>> overridePaths,
                                               List<String> prefix) {
        LinkedHashSet<String> names = directNames(basePaths, prefix);
        names.addAll(directNames(overridePaths, prefix));
        PropertySet merged = new PropertySet(names.size());
        for (String name : names) {
            boolean overridden = hasDirectPath(overridePaths, prefix, name);
            if (!overridden) {
                copyProperty(base, merged, name, basePaths, prefix);
                continue;
            }

            PropertyType overrideType = override.typeOf(name);
            PropertyType baseType = base.typeOf(name);
            if (overrideType == PropertyType.CLASS && baseType == PropertyType.CLASS) {
                ClassProperty baseClass = base.getClassValue(name);
                ClassProperty overrideClass = override.getClassValue(name);
                if (baseClass.typeName().equals(overrideClass.typeName())) {
                    PropertySet members = mergeProperties(
                            reader(baseClass.properties()), basePaths,
                            reader(overrideClass.properties()), overridePaths,
                            appendPath(prefix, name));
                    merged.putClass(name, overrideClass.typeName(), members);
                    continue;
                }
            }
            copyProperty(override, merged, name, overridePaths, prefix);
        }
        return merged;
    }

    private static LinkedHashSet<String> directNames(List<List<String>> paths, List<String> prefix) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (List<String> path : paths) {
            if (path.size() > prefix.size() && startsWith(path, prefix)) {
                names.add(path.get(prefix.size()));
            }
        }
        return names;
    }

    private static boolean hasDirectPath(List<List<String>> paths, List<String> prefix, String name) {
        for (List<String> path : paths) {
            if (path.size() == prefix.size() + 1
                    && startsWith(path, prefix)
                    && name.equals(path.get(prefix.size()))) return true;
        }
        return false;
    }

    private static boolean startsWith(List<String> path, List<String> prefix) {
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equals(path.get(i))) return false;
        }
        return true;
    }

    private static List<String> appendPath(List<String> prefix, String name) {
        List<String> path = new ArrayList<>(prefix.size() + 1);
        path.addAll(prefix);
        path.add(name);
        return path;
    }

    private static void copyProperty(PropertyReader source,
                                     PropertySet target,
                                     String name,
                                     List<List<String>> paths,
                                     List<String> prefix) {
        PropertyType type = source.typeOf(name);
        if (type == null) return;
        switch (type) {
            case STRING -> target.putString(name, source.getString(name, ""));
            case BOOLEAN -> target.putBoolean(name, source.getBoolean(name, false));
            case INTEGER -> target.putInt(name, source.getInt(name, 0));
            case FLOAT -> target.putFloat(name, source.getFloat(name, 0f));
            case COLOR -> target.putColorRgba8888(name, source.getColorRgba8888(name, 0));
            case CLASS -> {
                ClassProperty value = source.getClassValue(name);
                PropertySet members = mergeProperties(
                        reader(value.properties()), paths,
                        reader(new PropertySet(0)), List.of(),
                        appendPath(prefix, name));
                target.putClass(name, value.typeName(), members);
            }
        }
    }

    private static PropertyReader reader(PropertySet properties) {
        return new PropertySetReader(properties);
    }

    private static PropertyReader reader(CustomProperties properties) {
        return new CustomPropertiesReader(properties);
    }

    private interface PropertyReader {
        PropertyType typeOf(String name);
        String getString(String name, String fallback);
        boolean getBoolean(String name, boolean fallback);
        int getInt(String name, int fallback);
        float getFloat(String name, float fallback);
        int getColorRgba8888(String name, int fallback);
        ClassProperty getClassValue(String name);
    }

    private record PropertySetReader(PropertySet properties) implements PropertyReader {
        @Override public PropertyType typeOf(String name) { return properties.typeOf(name); }
        @Override public String getString(String name, String fallback) { return properties.getString(name, fallback); }
        @Override public boolean getBoolean(String name, boolean fallback) { return properties.getBoolean(name, fallback); }
        @Override public int getInt(String name, int fallback) { return properties.getInt(name, fallback); }
        @Override public float getFloat(String name, float fallback) { return properties.getFloat(name, fallback); }
        @Override public int getColorRgba8888(String name, int fallback) { return properties.getColorRgba8888(name, fallback); }
        @Override public ClassProperty getClassValue(String name) { return properties.getClassValue(name); }
    }

    private record CustomPropertiesReader(CustomProperties properties) implements PropertyReader {
        @Override public PropertyType typeOf(String name) { return properties.typeOf(name); }
        @Override public String getString(String name, String fallback) { return properties.getString(name, fallback); }
        @Override public boolean getBoolean(String name, boolean fallback) { return properties.getBoolean(name, fallback); }
        @Override public int getInt(String name, int fallback) { return properties.getInt(name, fallback); }
        @Override public float getFloat(String name, float fallback) { return properties.getFloat(name, fallback); }
        @Override public int getColorRgba8888(String name, int fallback) { return properties.getColorRgba8888(name, fallback); }
        @Override public ClassProperty getClassValue(String name) { return properties.getClassValue(name); }
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
                layer.resolvedImagePath()
        );
    }

    private static SceneMetaRuntime.TiledProjection projectionFor(String orientation) {
        if ("isometric".equals(orientation)) {
            return SceneMetaRuntime.TiledProjection.ISO;
        }
        return SceneMetaRuntime.TiledProjection.ORTHO;
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
