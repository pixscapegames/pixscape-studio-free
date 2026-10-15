package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.helper.LightGizmoGeometry;
import games.pixscape.studio.helper.ConeLightGizmoGeometry;
import games.pixscape.studio.history.commands.EditLightingCommand.Property;
import games.pixscape.studio.ui.config.EditorOverlayPalette;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.IconResolver;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.StudioDisplayOffsetResolver;

public final class LightIconOverlaySystem extends IteratingSystem {

    private static final float ICON_SIZE_PX = 24f;
    private static final float HOVER_TOL_PX = 4f;
    private static final int CONE_SEGMENTS = 24;
    private static final float LIGHT_RADIUS_HANDLE_SIZE_PX = 10f;

    private final StudioDrawContext ctx;
    private final OrthographicCamera worldCam;
    private final Drawable lightIcon;

    private LayerService layerService;
    private SelectionService selectionService;
    private StudioDisplayOffsetResolver displayOffsetResolver;

    private ComponentMapper<PointLightComponent> mLight;
    private ComponentMapper<ConeLightComponent> mConeLight;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<VisibilityComponent> mVisibility;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private GameObjectHierarchySystem gameObjectHierarchy;
    private ComponentMapper<DimensionsComponent> mDimensions;
    private boolean drawingAllowed;
    private int hoveredValueEntity = -1;
    private Property hoveredValueProperty;
    private boolean valueDragging;

    public void setDrawingAllowed(boolean allowed) { drawingAllowed = allowed; }
    @Override protected boolean checkProcessing() { return drawingAllowed; }

    public void setValueHandle(int entity, Property property, boolean dragging) {
        hoveredValueEntity = entity;
        hoveredValueProperty = property;
        valueDragging = dragging;
    }

    private final Vector2 tmpMouseWorld = new Vector2();
    private final Vector2 tmpDisplayOffset = new Vector2();
    private final Vector2 tmpWorldPosition = new Vector2();
    private final Vector2 tmpRadiusControl = new Vector2();

    public LightIconOverlaySystem(StudioDrawContext ctx,
                                  OrthographicCamera worldCam) {
        super(Aspect.all(TransformComponent.class)
                .one(PointLightComponent.class, ConeLightComponent.class));
        this.ctx = ctx;
        this.worldCam = worldCam;
        this.lightIcon = IconResolver.iconForEntity(EntityKind.POINT_LIGHT);
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
    protected void initialize() {
        gameObjectHierarchy = world.getSystem(GameObjectHierarchySystem.class);
    }

    @Override
    protected void begin() {
        ctx.batch.setProjectionMatrix(ctx.cam.combined);
        ctx.batch.begin();
        readMouseWorld(tmpMouseWorld);
    }

    @Override
    protected void process(int entityId) {
        if (!isEntityVisible(entityId)) {
            return;
        }

        TransformComponent transform = mTransform.get(entityId);
        if (transform == null) {
            return;
        }

        resolveWorldPosition(entityId, transform, tmpWorldPosition);
        resolveDisplayOffset(entityId, tmpDisplayOffset);
        float groundX = tmpWorldPosition.x + tmpDisplayOffset.x;
        float groundY = tmpWorldPosition.y + tmpDisplayOffset.y;
        PointLightComponent point = mLight.getSafe(entityId, null);
        ConeLightComponent cone = mConeLight.getSafe(entityId, null);
        float height = point != null ? point.height : cone.height;
        LightGizmoGeometry.source(tmpWorldPosition.x, tmpWorldPosition.y, height, tmpWorldPosition);
        float x = tmpWorldPosition.x + tmpDisplayOffset.x;
        float y = tmpWorldPosition.y + tmpDisplayOffset.y;
        float rotationRad = resolveWorldRotation(entityId, transform);

        float sizeWorld = ICON_SIZE_PX * ctx.wpp();
        float half = sizeWorld * 0.5f;

        boolean isCone = mConeLight.has(entityId);
        // Items tree uses this same drawable with a neutral tint, independent of emitted RGB.
        float previousColor = ctx.batch.getPackedColor();
        ctx.batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        lightIcon.draw(ctx.batch, x - half, y - half, sizeWorld, sizeWorld);
        ctx.batch.setPackedColor(previousColor);

        boolean highlight = selectionService != null && selectionService.getSelectionSet().contains(entityId);
        if (!highlight && isHoveringIcon(x, y, half)) {
            highlight = true;
        }

        if (!highlight) {
            return;
        }

        // Both contours are horizontal range sections at source Z, not ground footprints.
        if (isCone) {
            drawConeWedge(entityId, x, y, rotationRad);
        } else {
            PointLightComponent light = mLight.get(entityId);
            if (light != null && light.radius > 0f) {
                drawPointSection(x, y, light.radius);
            }
        }

        drawRadiusHandle(entityId, x, y, rotationRad, x, y);
        boolean selected = selectionService != null && selectionService.getSelectionSet().contains(entityId);
        ctx.drawer.setColor(selected ? EditorOverlayPalette.WALL_SELECTED_COLOR : EditorOverlayPalette.WALL_HOVER_COLOR);
        ctx.drawer.circle(x, y, half + ctx.pxToWorld(2f), ctx.pxToWorld(1f));
        if (selected) {
            drawValueHandles(entityId, x, y, groundX, groundY);
            if (isCone) drawInclinationHandle(entityId, x, y, rotationRad, cone.inclinationDeg);
        }

    }

    private void drawValueHandles(int entity, float x, float y, float gx, float gy) {
        float p = ctx.wpp(), thickness = 1.5f * p;
        ctx.drawer.setColor(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
        if (Vector2.dst2(x, y, gx, gy) > p * p) ctx.drawer.line(x, y, gx, gy, p);
        // Runtime XY projects to (1,.5) and (-1,.5), regardless of scene map metrics.
        ctx.drawer.line(gx - 5f*p, gy - 2.5f*p, gx + 5f*p, gy + 2.5f*p, p);
        ctx.drawer.line(gx + 5f*p, gy - 2.5f*p, gx - 5f*p, gy + 2.5f*p, p);
        float hy = y - LightGizmoGeometry.HEIGHT_OFFSET_PX*p;
        valueColor(entity, Property.HEIGHT);
        ctx.drawer.line(x, hy - 7f*p, x, hy + 7f*p, thickness);
        ctx.drawer.line(x - 4f*p, hy + 3f*p, x, hy + 7f*p, thickness);
        ctx.drawer.line(x + 4f*p, hy + 3f*p, x, hy + 7f*p, thickness);
        ctx.drawer.line(x - 4f*p, hy - 3f*p, x, hy - 7f*p, thickness);
        ctx.drawer.line(x + 4f*p, hy - 3f*p, x, hy - 7f*p, thickness);
        valueColor(entity, Property.INTENSITY);
        float ix = x + LightGizmoGeometry.INTENSITY_OFFSET_X_PX*p;
        float iy = y + LightGizmoGeometry.INTENSITY_OFFSET_Y_PX*p;
        ctx.drawer.line(ix - 10f*p, iy, ix + 10f*p, iy, thickness);
        ctx.drawer.line(ix, iy - 5f*p, ix, iy + 5f*p, thickness);
    }

    private void valueColor(int entity, Property property) {
        ctx.drawer.setColor(hoveredValueEntity == entity && hoveredValueProperty == property
                ? (valueDragging ? EditorOverlayPalette.WALL_SELECTED_COLOR : EditorOverlayPalette.WALL_HOVER_COLOR)
                : EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
    }

    private void drawInclinationHandle(int entity, float x, float y, float rotation, float inclination) {
        float wpp = ctx.wpp();
        ctx.drawer.setColor(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
        ConeLightGizmoGeometry.inclinationArcPoint(x, y, rotation, -90f, wpp, tmpRadiusControl);
        float previousX = tmpRadiusControl.x, previousY = tmpRadiusControl.y;
        for (int i = 1; i <= CONE_SEGMENTS; i++) {
            ConeLightGizmoGeometry.inclinationArcPoint(x, y, rotation,
                    -90f + 180f * i / CONE_SEGMENTS, wpp, tmpRadiusControl);
            ctx.drawer.line(previousX, previousY, tmpRadiusControl.x, tmpRadiusControl.y, wpp);
            previousX = tmpRadiusControl.x; previousY = tmpRadiusControl.y;
        }
        ConeLightGizmoGeometry.inclinationArcPoint(x, y, rotation, inclination, wpp, tmpRadiusControl);
        valueColor(entity, Property.INCLINATION);
        ctx.drawer.line(x + ConeLightGizmoGeometry.INCLINATION_OFFSET_X_PX * wpp,
                y + ConeLightGizmoGeometry.INCLINATION_OFFSET_Y_PX * wpp,
                tmpRadiusControl.x, tmpRadiusControl.y, wpp);
        ctx.drawer.circle(tmpRadiusControl.x, tmpRadiusControl.y, 4f * wpp, 1.5f * wpp);
    }

    @Override
    protected void end() {
        ctx.batch.end();
    }

    private void drawConeWedge(int entityId, float x, float y, float rotationRad) {
        ConeLightComponent light = mConeLight.get(entityId);
        if (light == null || light.radius <= 0f || light.coneAngleDeg <= 0f) {
            return;
        }

        float halfAngleRad = MathUtils.degreesToRadians * light.coneAngleDeg * 0.5f;
        float radius = light.radius;
        float thickness = ctx.pxToWorld(2f);

        ctx.drawer.setColor(1f, 0.9f, 0.2f, 0.5f);
        ConeLightGizmoGeometry.tiltedSectionPoint(x,y,rotationRad,light.inclinationDeg,-halfAngleRad,radius,tmpRadiusControl);
        float x1=tmpRadiusControl.x,y1=tmpRadiusControl.y;
        ConeLightGizmoGeometry.tiltedSectionPoint(x,y,rotationRad,light.inclinationDeg,halfAngleRad,radius,tmpRadiusControl);
        float x2=tmpRadiusControl.x,y2=tmpRadiusControl.y;

        ctx.drawer.line(x, y, x1, y1, thickness);
        ctx.drawer.line(x, y, x2, y2, thickness);

        float arcSpan = 2f * halfAngleRad;
        int segments = Math.max(3, CONE_SEGMENTS);
        float step = arcSpan / segments;
        float prevX = x1;
        float prevY = y1;
        for (int i = 1; i <= segments; i++) {
            float ang = -halfAngleRad + step * i;
            ConeLightGizmoGeometry.tiltedSectionPoint(x,y,rotationRad,light.inclinationDeg,ang,radius,tmpRadiusControl);
            float nx=tmpRadiusControl.x,ny=tmpRadiusControl.y;
            ctx.drawer.line(prevX, prevY, nx, ny, thickness);
            prevX = nx;
            prevY = ny;
        }
        if (light.inclinationDeg != 0f) {
            ConeLightGizmoGeometry.tiltedSectionPoint(x,y,rotationRad,light.inclinationDeg,0f,radius,tmpRadiusControl);
            ctx.drawer.line(x,y,tmpRadiusControl.x,tmpRadiusControl.y,thickness);
        }
    }

    private void drawPointSection(float x, float y, float radius) {
        ctx.drawer.setColor(1f, 0.9f, 0.2f, 0.5f);
        LightGizmoGeometry.pointSectionPoint(0f, 0f, 0f, radius, tmpRadiusControl);
        float radiusX = tmpRadiusControl.x;
        LightGizmoGeometry.pointSectionPoint(0f, 0f, MathUtils.HALF_PI, radius, tmpRadiusControl);
        ctx.drawer.ellipse(x, y, radiusX, tmpRadiusControl.y, 0f, ctx.pxToWorld(1f));
    }

    private void drawRadiusHandle(int entityId, float x, float y, float rotationRad, float sourceX, float sourceY) {
        float radius = 0f;

        PointLightComponent point = mLight.getSafe(entityId, null);
        if (point != null) radius = point.radius;

        ConeLightComponent cone = mConeLight.getSafe(entityId, null);
        if (cone != null) {
            radius = cone.radius;
        }

        if (radius <= 0f) return;

        if (cone != null) {
            ConeLightGizmoGeometry.sectionPoint(x,y,rotationRad,0f,radius,tmpRadiusControl);
        } else {
            LightGizmoGeometry.pointSectionPoint(x,y,0f,radius,tmpRadiusControl);
        }
        float hx = tmpRadiusControl.x, hy = tmpRadiusControl.y;
        float half = ctx.pxToWorld(LIGHT_RADIUS_HANDLE_SIZE_PX * 0.5f);

        ctx.drawer.setColor(1f, 0.9f, 0.2f, 0.8f);
        ctx.drawer.line(x, y, hx, hy, ctx.pxToWorld(2f));
        LightGizmoGeometry.radiusControl(sourceX, sourceY, hx, hy, ctx.wpp(), tmpRadiusControl);
        if (Vector2.dst2(hx,hy,tmpRadiusControl.x,tmpRadiusControl.y)>ctx.wpp()*ctx.wpp())
            ctx.drawer.line(hx,hy,tmpRadiusControl.x,tmpRadiusControl.y,ctx.pxToWorld(1f));
        hx=tmpRadiusControl.x;hy=tmpRadiusControl.y;
        ctx.drawer.line(hx - half, hy - half, hx + half, hy - half, ctx.pxToWorld(2f));
        ctx.drawer.line(hx + half, hy - half, hx + half, hy + half, ctx.pxToWorld(2f));
        ctx.drawer.line(hx + half, hy + half, hx - half, hy + half, ctx.pxToWorld(2f));
        ctx.drawer.line(hx - half, hy + half, hx - half, hy - half, ctx.pxToWorld(2f));
    }

    private void resolveDisplayOffset(int entityId, Vector2 out) {
        if (displayOffsetResolver == null) {
            out.set(0f, 0f);
            return;
        }
        displayOffsetResolver.resolve(entityId, out);
    }

    private void resolveWorldPosition(int entityId, TransformComponent authored, Vector2 out) {
        WorldTransformState state = gameObjectHierarchy != null
                ? gameObjectHierarchy.worldTransforms() : null;
        LightGizmoGeometry.ground(state, entityId, authored, mDimensions.getSafe(entityId, null), out);
    }

    private float resolveWorldRotation(int entityId, TransformComponent authored) {
        WorldTransformState state = gameObjectHierarchy != null
                ? gameObjectHierarchy.worldTransforms() : null;
        return resolvedWorldRotation(state, entityId, authored);
    }

    static float resolvedWorldX(WorldTransformState state, int entityId, TransformComponent authored) {
        return state != null && state.isResolved(entityId) ? state.x[entityId] : authored.x;
    }

    static float resolvedWorldY(WorldTransformState state, int entityId, TransformComponent authored) {
        return state != null && state.isResolved(entityId) ? state.y[entityId] : authored.y;
    }

    static float resolvedWorldRotation(WorldTransformState state, int entityId, TransformComponent authored) {
        return state != null && state.isResolved(entityId)
                ? state.rotationRad[entityId] : authored.rotationRad;
    }

    private boolean isHoveringIcon(float x, float y, float halfWidthorld) {
        float tolWorld = ctx.pxToWorld(HOVER_TOL_PX);
        float half = halfWidthorld + tolWorld;
        return Math.abs(tmpMouseWorld.x - x) <= half && Math.abs(tmpMouseWorld.y - y) <= half;
    }

    private boolean isEntityVisible(int e) {
        if (!isLayerVisible(e)) return false;
        return !mVisibility.has(e) || mVisibility.get(e).isVisible();
    }

    private boolean isLayerVisible(int e) {
        if (mEntityIndex == null || !mEntityIndex.has(e)) return true;
        int layerIndex = mEntityIndex.get(e).getLayerIndex();
        int layerEntityId = layerService != null ? layerService.getLayerEntity(layerIndex) : -1;
        if (layerEntityId == -1) return true;
        VisibilityComponent layerVis = mVisibility.getSafe(layerEntityId, null);
        return layerVis == null || layerVis.isVisible();
    }

    private void readMouseWorld(Vector2 out) {
        ctx.screenToWorld(Gdx.input.getX(), Gdx.input.getY(), out);
    }
}
