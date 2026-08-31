package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.helper.StudioDrawContext;
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

    private final Vector2 tmpMouseWorld = new Vector2();
    private final Vector3 tmpMouse3 = new Vector3();
    private final Vector2 tmpDisplayOffset = new Vector2();
    private final Vector2 tmpWorldPosition = new Vector2();

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
        float x = tmpWorldPosition.x + tmpDisplayOffset.x;
        float y = tmpWorldPosition.y + tmpDisplayOffset.y;
        float rotationRad = resolveWorldRotation(entityId, transform);

        float sizeWorld = ICON_SIZE_PX * ctx.wpp();
        float half = sizeWorld * 0.5f;

        boolean isCone = mConeLight.has(entityId);
        lightIcon.draw(ctx.batch, x - half, y - half, sizeWorld, sizeWorld);

        boolean highlight = selectionService != null && selectionService.getSelectionSet().contains(entityId);
        if (!highlight && isHoveringIcon(x, y, half)) {
            highlight = true;
        }

        if (!highlight) {
            return;
        }

        if (isCone) {
            drawConeWedge(entityId, x, y, rotationRad);
        } else {
            PointLightComponent light = mLight.get(entityId);
            if (light != null && light.radius > 0f) {
                ctx.drawer.setColor(1f, 0.9f, 0.2f, 0.5f);
                ctx.drawer.circle(x, y, light.radius);
            }
        }

        drawRadiusHandle(entityId, x, y, rotationRad);

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
        float startAngle = rotationRad - halfAngleRad;
        float endAngle = rotationRad + halfAngleRad;

        float radius = light.radius;
        float thickness = ctx.pxToWorld(2f);

        ctx.drawer.setColor(1f, 0.9f, 0.2f, 0.5f);
        float x1 = x + MathUtils.cos(startAngle) * radius;
        float y1 = y + MathUtils.sin(startAngle) * radius;
        float x2 = x + MathUtils.cos(endAngle) * radius;
        float y2 = y + MathUtils.sin(endAngle) * radius;

        ctx.drawer.line(x, y, x1, y1, thickness);
        ctx.drawer.line(x, y, x2, y2, thickness);

        float arcSpan = endAngle - startAngle;
        int segments = Math.max(3, CONE_SEGMENTS);
        float step = arcSpan / segments;
        float prevX = x1;
        float prevY = y1;
        for (int i = 1; i <= segments; i++) {
            float ang = startAngle + step * i;
            float nx = x + MathUtils.cos(ang) * radius;
            float ny = y + MathUtils.sin(ang) * radius;
            ctx.drawer.line(prevX, prevY, nx, ny, thickness);
            prevX = nx;
            prevY = ny;
        }
    }

    private void drawRadiusHandle(int entityId, float x, float y, float rotationRad) {
        float radius = 0f;
        float dirX = 1f;
        float dirY = 0f;

        PointLightComponent point = mLight.getSafe(entityId, null);
        if (point != null) radius = point.radius;

        ConeLightComponent cone = mConeLight.getSafe(entityId, null);
        if (cone != null) {
            radius = cone.radius;
            dirX = MathUtils.cos(rotationRad);
            dirY = MathUtils.sin(rotationRad);
        }

        if (radius <= 0f) return;

        float hx = x + dirX * radius;
        float hy = y + dirY * radius;
        float half = ctx.pxToWorld(LIGHT_RADIUS_HANDLE_SIZE_PX * 0.5f);

        ctx.drawer.setColor(1f, 0.9f, 0.2f, 0.8f);
        ctx.drawer.line(x, y, hx, hy, ctx.pxToWorld(2f));
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
        out.set(resolvedWorldX(state, entityId, authored), resolvedWorldY(state, entityId, authored));
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
        tmpMouse3.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        worldCam.unproject(tmpMouse3);
        out.set(tmpMouse3.x, tmpMouse3.y);
    }
}
