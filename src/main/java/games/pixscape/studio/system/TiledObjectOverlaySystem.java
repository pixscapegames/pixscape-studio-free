package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.studio.component.TiledObjectComponent;
import games.pixscape.studio.helper.GizmoDrawHelper;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.service.LayerService;

/** Passive Studio overlays for imported Tiled Rectangle and Point objects. */
public final class TiledObjectOverlaySystem extends IteratingSystem {

    private static final float POINT_OUTER_RADIUS_PX = 6f;
    private static final float POINT_CENTER_RADIUS_PX = 2f;
    private static final float POINT_STROKE_PX = 1.25f;
    private static final float ZERO_RECT_SIZE_PX = 7f;

    private final StudioDrawContext ctx;
    private final float[] rectangleCorners = new float[8];
    private final float[] pointCenter = new float[2];

    private LayerService layerService;

    private ComponentMapper<TiledObjectComponent> mTiledObject;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<DimensionsComponent> mDimensions;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<VisibilityComponent> mVisibility;

    public TiledObjectOverlaySystem(StudioDrawContext ctx) {
        super(Aspect.all(
                TiledObjectComponent.class,
                TransformComponent.class,
                EntityIndexComponent.class
        ));
        this.ctx = ctx;
    }

    public void setLayerService(LayerService layerService) {
        this.layerService = layerService;
    }

    @Override
    protected void begin() {
        ctx.batch.setProjectionMatrix(ctx.cam.combined);
        ctx.batch.begin();
    }

    @Override
    protected void process(int entityId) {
        TiledObjectComponent tiledObject = mTiledObject.get(entityId);
        TransformComponent transform = mTransform.get(entityId);
        if (tiledObject == null
                || transform == null
                || !shouldDrawShape(
                        tiledObject.kind,
                        isObjectVisible(entityId),
                        isParentLayerVisible(entityId))) {
            return;
        }

        switch (tiledObject.kind) {
            case RECTANGLE -> drawRectangle(entityId, transform);
            case POINT -> drawPoint(transform);
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
        GizmoDrawHelper.drawDashedObb(ctx, rectangleCorners);
    }

    private void drawPoint(TransformComponent transform) {
        float outerRadius = pointOuterRadiusWorld(ctx.wpp());
        float centerRadius = pointCenterRadiusWorld(ctx.wpp());
        float stroke = POINT_STROKE_PX * ctx.wpp();
        pointCenter(transform, pointCenter);
        float x = pointCenter[0];
        float y = pointCenter[1];

        ctx.drawer.setColor(0.8f, 0.8f, 0.8f, 1f);
        ctx.drawer.circle(x, y, outerRadius, stroke);
        ctx.drawer.line(x - outerRadius, y, x + outerRadius, y, stroke);
        ctx.drawer.line(x, y - outerRadius, x, y + outerRadius, stroke);
        ctx.drawer.filledCircle(x, y, centerRadius);
    }

    static void computeRectangleCorners(TransformComponent transform,
                                        DimensionsComponent dimensions,
                                        float[] out) {
        if (transform == null || dimensions == null || out == null || out.length < 8) {
            throw new IllegalArgumentException("Rectangle corner output requires transform, dimensions, and 8 floats.");
        }
        writeCorner(transform, 0f, 0f, out, 0);
        writeCorner(transform, dimensions.width, 0f, out, 2);
        writeCorner(transform, dimensions.width, dimensions.height, out, 4);
        writeCorner(transform, 0f, dimensions.height, out, 6);
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

    static boolean shouldDrawShape(TiledObjectComponent.Kind kind,
                                   boolean objectVisible,
                                   boolean layerVisible) {
        return objectVisible
                && layerVisible
                && (kind == TiledObjectComponent.Kind.RECTANGLE || kind == TiledObjectComponent.Kind.POINT);
    }

    private static void writeCorner(TransformComponent transform,
                                    float localX,
                                    float localY,
                                    float[] out,
                                    int offset) {
        float x = (localX - transform.originX) * transform.scaleX;
        float y = (localY - transform.originY) * transform.scaleY;
        out[offset] = transform.x + transform.cos * x - transform.sin * y;
        out[offset + 1] = transform.y + transform.sin * x + transform.cos * y;
    }

    private boolean isObjectVisible(int entityId) {
        VisibilityComponent visibility = mVisibility.getSafe(entityId, null);
        return visibility == null || visibility.isVisible();
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
