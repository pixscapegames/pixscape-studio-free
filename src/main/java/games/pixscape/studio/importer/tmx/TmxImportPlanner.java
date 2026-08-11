package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.loading.SceneMetaRuntime;

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
                    info.external(),
                    0,
                    Math.max(info.tileCount(), 0),
                    info.imageCollectionTiles(),
                    info.tileAnimations()
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
