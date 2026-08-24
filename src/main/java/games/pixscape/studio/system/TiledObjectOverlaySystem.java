package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PolygonComponent;
import games.pixscape.runtime.component.PolylineComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.helper.GizmoDrawHelper;
import games.pixscape.studio.helper.AuthoredGeometryTransform;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioDisplayOffsetResolver;

/** Passive Studio overlays for imported Tiled Rectangle and Point objects. */
public final class TiledObjectOverlaySystem extends IteratingSystem {

    private static final float POINT_OUTER_RADIUS_PX = 6f;
    private static final float POINT_CENTER_RADIUS_PX = 2f;
    private static final float POINT_STROKE_PX = 1.25f;
    private static final float ZERO_RECT_SIZE_PX = 7f;

    private final StudioDrawContext ctx;
    private final float[] rectangleCorners = new float[8];
    private final float[] pointCenter = new float[2];
    private float[] pathVertices = new float[8];

    private LayerService layerService;
    private SelectionService selectionService;
    private StudioDisplayOffsetResolver displayOffsetResolver;

    private ComponentMapper<EntityMetaComponent> mEntityMeta;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<DimensionsComponent> mDimensions;
    private ComponentMapper<PolygonComponent> mPolygon;
    private ComponentMapper<PolylineComponent> mPolyline;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<VisibilityComponent> mVisibility;

    public TiledObjectOverlaySystem(StudioDrawContext ctx) {
        super(Aspect.all(
                EntityMetaComponent.class,
                TransformComponent.class,
                EntityIndexComponent.class
        ));
        this.ctx = ctx;
    }

    public void setLayerService(LayerService layerService) {
        this.layerService = layerService;
    }

    public void setSelectionService(SelectionService selectionService) {
        this.selectionService = selectionService;
    }

    public void setDisplayOffsetResolver(StudioDisplayOffsetResolver displayOffsetResolver) {
        this.displayOffsetResolver = displayOffsetResolver;
    }

    @Override
    protected void begin() {
        ctx.batch.setProjectionMatrix(ctx.cam.combined);
        ctx.batch.begin();
    }

    @Override
    protected void process(int entityId) {
        EntityMetaComponent meta = mEntityMeta.get(entityId);
        TransformComponent transform = mTransform.get(entityId);
        PolygonComponent polygon = mPolygon.getSafe(entityId, null);
        if (meta == null
                || transform == null
                || !shouldDrawShape(
                        meta.kind,
                        isObjectVisible(entityId),
                        isParentLayerVisible(entityId),
                        isSelected(entityId),
                        meta.kind == EntityKind.TILED_RECTANGLE && polygon != null)) {
            return;
        }

        switch (meta.kind) {
            case TILED_RECTANGLE -> {
                if (polygon != null) {
                    drawPath(entityId, transform, polygon.vertices, true);
                } else {
                    drawRectangle(entityId, transform);
                }
            }
            case TILED_POINT -> drawPoint(entityId, transform);
            case POLYGON -> drawPath(entityId, transform, polygon != null ? polygon.vertices : null, true);
            case POLYLINE -> drawPath(entityId, transform,
                    mPolyline.getSafe(entityId, null) != null ? mPolyline.get(entityId).vertices : null, false);
            default -> {
                // Tile Objects and unknown kinds use no passive shape overlay.
            }
        }
    }

    @Override
    protected void end() {
        ctx.batch.end();
    }

    private void drawRectangle(int entityId, TransformComponent transform) {
        DimensionsComponent dimensions = mDimensions.getSafe(entityId, null);
        if (dimensions == null) return;

        if (dimensions.width == 0f && dimensions.height == 0f) {
            float half = ZERO_RECT_SIZE_PX * 0.5f * ctx.wpp();
            rectangleCorners[0] = transform.x - half;
            rectangleCorners[1] = transform.y - half;
            rectangleCorners[2] = transform.x + half;
            rectangleCorners[3] = transform.y - half;
            rectangleCorners[4] = transform.x + half;
            rectangleCorners[5] = transform.y + half;
            rectangleCorners[6] = transform.x - half;
            rectangleCorners[7] = transform.y + half;
        } else {
            computeRectangleCorners(transform, dimensions, rectangleCorners);
        }
        if (displayOffsetResolver != null) {
            displayOffsetResolver.addTo(entityId, rectangleCorners, 4);
        }
        GizmoDrawHelper.drawDashedObb(ctx, rectangleCorners);
    }

    private void drawPoint(int entityId, TransformComponent transform) {
        float outerRadius = pointOuterRadiusWorld(ctx.wpp());
        float centerRadius = pointCenterRadiusWorld(ctx.wpp());
        float stroke = POINT_STROKE_PX * ctx.wpp();
        pointCenter(transform, pointCenter);
        if (displayOffsetResolver != null) {
            displayOffsetResolver.addTo(entityId, pointCenter, 1);
        }
        float x = pointCenter[0];
        float y = pointCenter[1];

        ctx.drawer.setColor(0.8f, 0.8f, 0.8f, 1f);
        ctx.drawer.circle(x, y, outerRadius, stroke);
        ctx.drawer.line(x - outerRadius, y, x + outerRadius, y, stroke);
        ctx.drawer.line(x, y - outerRadius, x, y + outerRadius, stroke);
        ctx.drawer.filledCircle(x, y, centerRadius);
    }

    private void drawPath(int entityId,
                          TransformComponent transform,
                          float[] localVertices,
                          boolean closed) {
        if (localVertices == null || localVertices.length < 4 || (localVertices.length & 1) != 0) return;
        ensurePathCapacity(localVertices.length);
        AuthoredGeometryTransform.transformVertices(transform, localVertices, pathVertices);
        int pointCount = localVertices.length / 2;
        if (displayOffsetResolver != null) {
            displayOffsetResolver.addTo(entityId, pathVertices, pointCount);
        }
        float stroke = POINT_STROKE_PX * ctx.wpp();
        ctx.drawer.setColor(0.8f, 0.8f, 0.8f, 1f);
        for (int i = 0; i < pointCount - 1; i++) {
            int offset = i * 2;
            ctx.drawer.line(pathVertices[offset], pathVertices[offset + 1],
                    pathVertices[offset + 2], pathVertices[offset + 3], stroke);
        }
        if (closed && pointCount >= 3) {
            int last = (pointCount - 1) * 2;
            ctx.drawer.line(pathVertices[last], pathVertices[last + 1],
                    pathVertices[0], pathVertices[1], stroke);
        }
    }

    private void ensurePathCapacity(int required) {
        if (pathVertices.length < required) pathVertices = new float[required];
    }

    static void computeRectangleCorners(TransformComponent transform,
                                        DimensionsComponent dimensions,
                                        float[] out) {
        if (transform == null || dimensions == null || out == null || out.length < 8) {
            throw new IllegalArgumentException("Rectangle corner output requires transform, dimensions, and 8 floats.");
        }
        out[0] = AuthoredGeometryTransform.worldX(transform, 0f, 0f);
        out[1] = AuthoredGeometryTransform.worldY(transform, 0f, 0f);
        out[2] = AuthoredGeometryTransform.worldX(transform, dimensions.width, 0f);
        out[3] = AuthoredGeometryTransform.worldY(transform, dimensions.width, 0f);
        out[4] = AuthoredGeometryTransform.worldX(transform, dimensions.width, dimensions.height);
        out[5] = AuthoredGeometryTransform.worldY(transform, dimensions.width, dimensions.height);
        out[6] = AuthoredGeometryTransform.worldX(transform, 0f, dimensions.height);
        out[7] = AuthoredGeometryTransform.worldY(transform, 0f, dimensions.height);
    }

    static float pointOuterRadiusWorld(float worldPerPixel) {
        return POINT_OUTER_RADIUS_PX * worldPerPixel;
    }

    static float pointCenterRadiusWorld(float worldPerPixel) {
        return POINT_CENTER_RADIUS_PX * worldPerPixel;
    }

    /** A Tiled Point is the Transform position; origin, rotation, and scale are irrelevant. */
    static void pointCenter(TransformComponent transform, float[] out) {
        if (transform == null || out == null || out.length < 2) {
            throw new IllegalArgumentException("Point center output requires transform and 2 floats.");
        }
        out[0] = transform.x;
        out[1] = transform.y;
    }

    static boolean shouldDrawShape(EntityKind kind,
                                   boolean objectVisible,
                                   boolean layerVisible,
                                   boolean selected) {
        return shouldDrawShape(kind, objectVisible, layerVisible, selected, false);
    }

    static boolean shouldDrawShape(EntityKind kind,
                                   boolean objectVisible,
                                   boolean layerVisible,
                                   boolean selected,
                                   boolean projectedRectangle) {
        return objectVisible
                && layerVisible
                && (kind == EntityKind.TILED_POINT
                || kind == EntityKind.POLYGON
                || kind == EntityKind.POLYLINE
                || (kind == EntityKind.TILED_RECTANGLE && (projectedRectangle || !selected)));
    }

    private boolean isObjectVisible(int entityId) {
        VisibilityComponent visibility = mVisibility.getSafe(entityId, null);
        return visibility == null || visibility.isVisible();
    }

    private boolean isSelected(int entityId) {
        return selectionService != null && selectionService.getSelectionSet().contains(entityId);
    }

    private boolean isParentLayerVisible(int entityId) {
        EntityIndexComponent index = mEntityIndex.getSafe(entityId, null);
        if (index == null || layerService == null) return true;

        int layerEntityId = layerService.getLayerEntity(index.getLayerIndex());
        if (layerEntityId < 0) return true;
        VisibilityComponent layerVisibility = mVisibility.getSafe(layerEntityId, null);
        return layerVisibility == null || layerVisibility.isVisible();
    }
}
