package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.spatial.ActorSpatialGeometry;
import games.pixscape.runtime.spatial.SpatialActorGeometry;
import games.pixscape.runtime.service.SpatialActorPose;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.studio.service.SpatialActorShapeSupport;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.history.commands.EditActorSpatialRectangleCommand;
import games.pixscape.studio.history.commands.EditSpatialFootprintCommand;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.ui.config.EditorOverlayPalette;
import games.pixscape.studio.ui.main.WorldCanvas;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Native Stage gesture ownership. The overlay never modifies the actor transform or physics.
 */
public final class ActorSpatialRectangleCanvasEditor extends Actor {
    private final WorldCanvas canvas;
    private final BooleanSupplier available;
    private final Vector2 scratch = new Vector2(), pivot = new Vector2();
    private final Vector2[] handles = {new Vector2(), new Vector2(), new Vector2(), new Vector2(), new Vector2(), new Vector2()};
    private static final float HIT_RADIUS = 9f;
    private ActorSpatialGeometry geometry;
    private final ActorSpatialGeometry dragGeometry = new ActorSpatialGeometry();
    private final float[] localPoint = new float[2];
    private float displayX, displayY, localPressX, localPressY;
    private int owner = -1, gestureOwner = -1, dragTriangle;
    private long entityHistoryId, ownerHistoryId;
    private boolean visual, hadRepresentation, gestureFootprintInspection;
    private ActorSpatialRectangles rectanglesBefore;
    private SpatialRepresentationComponent gestureRepresentation;
    private ShapeDrawer drawer;
    private SceneEditorContext gestureContext;
    private ActorSpatialRectangleData before, candidate, current;
    private SpatialFootprintComponent footprintBefore, gestureFootprint, currentFootprint;
    private final SpatialActorGeometry.Footprint resolvedFootprint = new SpatialActorGeometry.Footprint();
    private float footprintX, footprintY, footprintRadius, ownerAngle;
    private int entity = -1, asset, handle = -1, pointer = -1;
    private String clip;
    private float pressX, pressY, factor;
    private Stage keyboardStage;
    private Actor previousFocus;

    public ActorSpatialRectangleCanvasEditor(WorldCanvas canvas, BooleanSupplier available) {
        this.canvas = canvas;
        this.available = available;
        setName("actor-spatial-rectangle-editor");
        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int p, int button) {
                if (button != Input.Buttons.LEFT || handle >= 0 || !positions()) {
                    return false;
                }
                int picked = pick(x, y);
                if (picked < 0 || !inside(x, y)) {
                    return false;
                }
                keyboardStage = getStage();
                previousFocus = keyboardStage.getKeyboardFocus();
                keyboardStage.setKeyboardFocus(ActorSpatialRectangleCanvasEditor.this);
                if (!positions()) {
                    restoreFocus();
                    return false;
                }
                gestureContext = canvas.getAttachedSceneContext();
                entity = selected(gestureContext);
                gestureFootprintInspection = inspectingFootprint(gestureContext);
                asset = asset(gestureContext, entity);
                clip = clip(gestureContext, entity);
                gestureOwner = owner;
                entityHistoryId = gestureContext.historyManager().historyIds().ensureForEntity(entity);
                ownerHistoryId = gestureContext.historyManager().historyIds().ensureForEntity(owner);
                footprintBefore = currentFootprint.copy();
                gestureFootprint = currentFootprint;
                before = picked < 4 ? current.copy() : null;
                candidate = before == null ? null : before.copy();
                handle = picked;
                pointer = p;
                factor = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ? .1f : 1f;
                grid(x, y, scratch);
                pressX = scratch.x;
                pressY = scratch.y;
                if (handle >= 1 && handle <= 3) {
                    var d = canvas.getDynamicEntityState();
                    int slot = d.renderSlotForEntity(entity);
                    dragGeometry.update(before, d.x1[slot], d.y1[slot], d.x2[slot], d.y2[slot],
                            d.x3[slot], d.y3[slot], d.x4[slot], d.y4[slot],
                            d.u1[slot] > d.u2[slot], d.v1[slot] > d.v2[slot]);
                    float u = before.x + before.width * (handle == 2 ? 1f : .5f);
                    float v = before.y + before.height * (handle == 3 ? 1f : .5f);
                    dragTriangle = dragGeometry.triangleAt(u, v);
                    if (!dragGeometry.worldToLocal(pressX - displayX, pressY - displayY, dragTriangle, localPoint, 0)) {
                        handle = -1;
                        restoreFocus();
                        return false;
                    }
                    localPressX = localPoint[0];
                    localPressY = localPoint[1];
                }
                if (handle < 4) {
                    var h = spatial(gestureContext, entity);
                    hadRepresentation = h != null;
                    rectanglesBefore = h == null || h.rectangles == null ? null : h.rectangles.copy();
                    gestureRepresentation = gestureContext.world().getMapper(SpatialRepresentationComponent.class).create(entity);
                    if (gestureRepresentation.rectangles == null) {
                        gestureRepresentation.rectangles = new ActorSpatialRectangles();
                    }
                    gestureRepresentation.rectangles.put(asset, clip, candidate);
                }
                event.stop();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int p) {
                if (handle < 0 || p != pointer) {
                    return;
                }
                if (!sameTarget() || !positions()) {
                    cancel();
                    return;
                }
                grid(x, y, scratch);
                float dx = (scratch.x - pressX) * factor, dy = (scratch.y - pressY) * factor;
                if (handle == 0) {
                    candidate.pivotX = before.pivotX + dx;
                    candidate.pivotY = before.pivotY + dy;
                }
                if (handle >= 1 && handle <= 3) {
                    if (!dragGeometry.worldToLocal(scratch.x - displayX, scratch.y - displayY, dragTriangle, localPoint, 0)) {
                        cancel();
                        return;
                    }
                    float du = (localPoint[0] - localPressX) * factor;
                    float dv = (localPoint[1] - localPressY) * factor;
                    if (handle == 1) {
                        candidate.x = before.x + du;
                        candidate.y = before.y + dv;
                    } else if (handle == 2) {
                        candidate.width = Math.max(.0001f, before.width + du);
                    } else {
                        candidate.height = Math.max(.0001f, before.height + dv);
                    }
                }
                if (handle == 4) {
                    float c = com.badlogic.gdx.math.MathUtils.cos(ownerAngle);
                    float s = com.badlogic.gdx.math.MathUtils.sin(ownerAngle);
                    float norm = c * c + s * s;
                    gestureFootprint.localOffsetXPx = footprintBefore.localOffsetXPx + (c * dx + s * dy) / norm;
                    gestureFootprint.localOffsetYPx = footprintBefore.localOffsetYPx + (c * dy - s * dx) / norm;
                }
                if (handle == 5) {
                    float radius = footprintBefore.radiusPx + dx;
                    if (Float.isFinite(radius) && radius > 0f) {
                        gestureFootprint.radiusPx = radius;
                    }
                }
                event.stop();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int p, int button) {
                if (handle >= 0 && p == pointer) {
                    finish(event.isTouchFocusCancel());
                    event.stop();
                }
            }

            @Override
            public boolean keyDown(InputEvent event, int key) {
                if (key == Input.Keys.ESCAPE && handle >= 0) {
                    cancel();
                    event.stop();
                    return true;
                }
                return false;
            }
        });
    }

    private static int selected(SceneEditorContext c) {
        if (c == null || c.isDisposed()) {
            return -1;
        }
        int inspected = c.selectionService().getInspectedFootprintOwner();
        return inspected >= 0 ? inspected : c.selectionService().getSelectionSet().size == 1
                ? c.selectionService().getFirstSelectedEntityId() : -1;
    }

    private static boolean inspectingFootprint(SceneEditorContext c) {
        return c != null && !c.isDisposed() && c.selectionService().getInspectedFootprintOwner() >= 0;
    }

    private static int asset(SceneEditorContext c, int e) {
        AssetRefComponent a = c.world().getMapper(AssetRefComponent.class).getSafe(e, null);
        return a == null ? 0 : a.assetId;
    }

    private static String clip(SceneEditorContext c, int e) {
        AnimationComponent a = c.world().getMapper(AnimationComponent.class).getSafe(e, null);
        return a == null ? null : a.currentClip;
    }

    private static SpatialRepresentationComponent spatial(SceneEditorContext c, int e) {
        return c == null || c.isDisposed() || e < 0 || !c.world().getEntityManager().isActive(e) ? null : c.world().getMapper(SpatialRepresentationComponent.class).getSafe(e, null);
    }

    private boolean positions() {
        if (!available.getAsBoolean() || getStage() == null) {
            return false;
        }
        SceneEditorContext ctx = canvas.getAttachedSceneContext();
        int e = selected(ctx);
        owner = SpatialActorShapeSupport.owner(ctx == null ? null : ctx.world(), e);
        if (owner < 0) {
            return false;
        }
        var h = spatial(ctx, e);
        current = h == null || h.rectangles == null ? null : h.rectangles.get(asset(ctx, e), clip(ctx, e));
        var d = canvas.getDynamicEntityState();
        int slot = d == null ? -1 : d.renderSlotForEntity(e);
        visual = !inspectingFootprint(ctx) && current != null && slot >= 0 && d.visible[slot] && d.enabled[slot]
                && ctx.world().getMapper(games.pixscape.runtime.component.DimensionsComponent.class).has(e);
        displayX = visual ? d.offsetX[slot] : 0f;
        displayY = visual ? d.offsetY[slot] : 0f;
        TransformComponent t = ctx.world().getMapper(TransformComponent.class).getSafe(owner, null);
        if (t == null) {
            return false;
        }
        var hierarchy = ctx.world().getSystem(GameObjectHierarchySystem.class);
        var poses = hierarchy == null ? null : hierarchy.worldTransforms();
        boolean resolved = poses != null && poses.isResolved(owner);
        ownerAngle = resolved ? poses.rotationRad[owner] : t.rotationRad;
        currentFootprint = ctx.world().getMapper(SpatialFootprintComponent.class).getSafe(owner, null);
        if (!SpatialActorPose.resolve(owner, t, currentFootprint, poses, resolvedFootprint)) {
            return false;
        }
        footprintX = resolvedFootprint.footX;
        footprintY = resolvedFootprint.footY;
        screenPoint(footprintX, footprintY, handles[4]);
        screenPoint(footprintX + currentFootprint.radiusPx, footprintY, handles[5]);
        footprintRadius = handles[4].dst(handles[5]);
        if (visual) {
            geometry = d.prepareActorSpatialGeometry(slot, current);
            screenPoint(SpatialActorPose.originX(owner, t, poses) + current.pivotX,
                    SpatialActorPose.originY(owner, t, poses) + current.pivotY, pivot);
            handles[0].set(pivot);
            imagePoint(current.x + current.width * .5f, current.y + current.height * .5f, handles[1]);
            imagePoint(current.x + current.width, current.y + current.height * .5f, handles[2]);
            imagePoint(current.x + current.width * .5f, current.y + current.height, handles[3]);
        } else {
            for (int i = 0; i < 4; i++) {
                handles[i].set(Float.NaN, Float.NaN);
            }
        }
        return true;
    }

    private void imagePoint(float u, float v, Vector2 out) {
        geometry.localToWorld(u, v, localPoint, 0);
        screenPoint(localPoint[0], localPoint[1], out);
    }

    private void screenPoint(float x, float y, Vector2 out) {
        out.set(x + displayX, y + displayY);
        canvas.getGridStage().stageToScreenCoordinates(out);
        getStage().screenToStageCoordinates(out);
    }

    private void grid(float x, float y, Vector2 out) {
        out.set(x, y);
        getStage().stageToScreenCoordinates(out);
        canvas.getGridStage().screenToStageCoordinates(out);
    }

    private int pick(float x, float y) {
        return pickHandle(handles, x, y);
    }

    /**
     * Nearest displayed handle wins within nine UI units, independent of world zoom.
     * Exact ties use array order: pivot, rectangle center, width, height, footprint center, radius.
     * Only the six handles of this Stage actor are examined; no scene/global picking pass.
     */
    static int pickHandle(Vector2[] points, float x, float y) {
        int result = -1;
        float best = HIT_RADIUS * HIT_RADIUS;
        for (int i = 0; i < points.length; i++) {
            float distance = points[i].dst2(x, y);
            if (distance < best) {
                best = distance;
                result = i;
            }
        }
        return result;
    }

    private boolean inside(float x, float y) {
        scratch.set(x, y);
        getStage().stageToScreenCoordinates(scratch);
        var v = canvas.getGridStage().getViewport();
        float top = Gdx.graphics.getHeight() - v.getScreenY() - v.getScreenHeight();
        return scratch.x >= v.getScreenX() && scratch.x < v.getScreenX() + v.getScreenWidth() && scratch.y >= top && scratch.y < top + v.getScreenHeight();
    }

    @Override
    public Actor hit(float x, float y, boolean touchable) {
        return isVisible() && positions() && inside(x, y) && pick(x, y) >= 0 ? this : null;
    }

    private boolean sameTarget() {
        var ctx = canvas.getAttachedSceneContext();
        var h = spatial(ctx, entity);
        if (!available.getAsBoolean() || ctx == null || ctx.isDisposed() || ctx != gestureContext
                || selected(ctx) != entity
                || inspectingFootprint(ctx) != gestureFootprintInspection
                || ctx.historyManager().historyIds().entityOfHistoryId(entityHistoryId) != entity
                || ctx.historyManager().historyIds().entityOfHistoryId(ownerHistoryId) != gestureOwner
                || SpatialActorShapeSupport.owner(ctx.world(), entity) != gestureOwner
                || handle < 4 && (asset(ctx, entity) != asset || !Objects.equals(clip, clip(ctx, entity)))) {
            return false;
        }
        return handle >= 4 ? ctx.world().getMapper(SpatialFootprintComponent.class).getSafe(gestureOwner, null) == gestureFootprint
                : h == gestureRepresentation && h.rectangles != null && h.rectangles.get(asset, clip) == candidate;
    }

    @Override
    public void act(float delta) {
        if (handle >= 0 && (!sameTarget() || getStage() == null || getStage().getKeyboardFocus() != this)) {
            cancel();
        }
    }

    public boolean isDragging() {
        return handle >= 0;
    }

    public void cancel() {
        if (handle < 0) {
            return;
        }
        finish(true);
        if (getStage() != null) {
            getStage().cancelTouchFocus(this);
        }
    }

    private void finish(boolean cancelled) {
        cancelled |= !sameTarget();
        var h = spatial(gestureContext, entity);
        if (handle >= 4 && gestureContext != null && !gestureContext.isDisposed()
                && gestureContext.historyManager().historyIds().entityOfHistoryId(ownerHistoryId) == gestureOwner
                && gestureContext.world().getMapper(SpatialFootprintComponent.class).getSafe(gestureOwner, null) == gestureFootprint) {
            var after = EditSpatialFootprintCommand.Snapshot.capture(gestureFootprint);
            gestureFootprint.set(footprintBefore);
            if (!cancelled) {
                var command = new EditSpatialFootprintCommand(gestureContext.world(), gestureContext.historyManager().historyIds(),
                        gestureOwner, EditSpatialFootprintCommand.Snapshot.capture(footprintBefore), after);
                if (!command.isNoop()) {
                    gestureContext.historyManager().execute(command);
                    gestureContext.markExplicitSaveRequired();
                }
            }
        } else if (gestureContext != null && !gestureContext.isDisposed()
                && gestureContext.historyManager().historyIds().entityOfHistoryId(entityHistoryId) == entity
                && h != null && h == gestureRepresentation && h.rectangles != null && h.rectangles.get(asset, clip) == candidate) {
            if (hadRepresentation) {
                h.rectangles = rectanglesBefore;
            } else {
                gestureContext.world().getMapper(SpatialRepresentationComponent.class).remove(entity);
            }
            if (!cancelled) {
                var command = new EditActorSpatialRectangleCommand(gestureContext.world(),
                        gestureContext.historyManager().historyIds(), entity, asset, clip, candidate, gestureContext::markExplicitSaveRequired);
                if (!command.isNoop()) {
                    gestureContext.historyManager().execute(command);
                }
            }
        }
        handle = -1;
        pointer = -1;
        gestureContext = null;
        before = candidate = null;
        footprintBefore = gestureFootprint = null;
        gestureRepresentation = null;
        rectanglesBefore = null;
        restoreFocus();
    }

    private void restoreFocus() {
        if (keyboardStage != null && keyboardStage.getKeyboardFocus() == this) {
            keyboardStage.setKeyboardFocus(previousFocus != null && previousFocus.getStage() == keyboardStage ? previousFocus : null);
        }
        keyboardStage = null;
        previousFocus = null;
    }

    @Override
    protected void setStage(Stage stage) {
        if (stage != getStage()) {
            cancel();
        }
        super.setStage(stage);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!positions()) {
            return;
        }
        Texture white = TextureRegistry.getByHandle(TextureRegistry.WHITE_HANDLE);
        if (white == null) {
            return;
        }
        if (drawer == null) {
            drawer = new ShapeDrawer(batch, new TextureRegion(white, 0, 0, 1, 1));
        }
        var v = canvas.getGridStage().getViewport();
        scratch.set(v.getScreenX(), Gdx.graphics.getHeight() - v.getScreenY());
        getStage().screenToStageCoordinates(scratch);
        float x = scratch.x, y = scratch.y;
        scratch.set(v.getScreenX() + v.getScreenWidth(), Gdx.graphics.getHeight() - v.getScreenY() - v.getScreenHeight());
        getStage().screenToStageCoordinates(scratch);
        batch.flush();
        if (!clipBegin(x, y, scratch.x - x, scratch.y - y)) {
            return;
        }
        float color = batch.getPackedColor();
        try {
            drawer.setColor(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
            if (visual) {
                for (int i = 0; i < geometry.vertexCount; i += 3) {
                    edge(i, i + 1);
                    edge(i + 1, i + 2);
                    edge(i + 2, i);
                }
            }
            drawer.setColor(com.badlogic.gdx.graphics.Color.CYAN);
            drawer.circle(handles[4].x, handles[4].y, footprintRadius, 1.5f);
            scratch.set(Gdx.input.getX(), Gdx.input.getY());
            getStage().screenToStageCoordinates(scratch);
            int hover = handle < 0 ? pick(scratch.x, scratch.y) : -1;
            if (!inside(scratch.x, scratch.y)) {
                hover = -1;
            }
            int highlighted = handle >= 0 ? handle : hover;
            // Draw the pending/active glyph last so its shape remains legible on coincident centers.
            for (int pass = 0; pass <= handles.length; pass++) {
                int i = pass == handles.length ? highlighted : pass;
                if (i < 0 || !visual && i < 4 || pass < handles.length && i == highlighted) {
                    continue;
                }
                drawer.setColor(i == 0 ? EditorOverlayPalette.SPATIAL_ACTOR_INFLUENCE_COLOR
                        : i >= 4 ? com.badlogic.gdx.graphics.Color.CYAN : EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);
                // Preserve the semantic color; the outer ring identifies the one pending/active target.
                float hx = handles[i].x, hy = handles[i].y;
                if (i == 0) {
                    drawer.circle(hx, hy, 5, 1.5f);
                    drawer.line(hx - 8, hy, hx + 8, hy, 1.5f);
                    drawer.line(hx, hy - 8, hx, hy + 8, 1.5f);
                } else if (i == 4) {
                    drawer.line(hx - 6, hy, hx + 6, hy, 1.5f);
                    drawer.line(hx, hy - 6, hx, hy + 6, 1.5f);
                } else if (i == 5) {
                    drawer.circle(hx, hy, 5, 1.5f);
                } else {
                    drawer.rectangle(hx - 4, hy - 4, 8, 8, 1.5f);
                }
                if (handle == i || hover == i) {
                    drawer.setColor(handle == i ? EditorOverlayPalette.WALL_SELECTED_COLOR : EditorOverlayPalette.WALL_HOVER_COLOR);
                    drawer.circle(hx, hy, 10, 1.5f);
                }
            }
        } finally {
            batch.setPackedColor(color);
            batch.flush();
            clipEnd();
        }
    }

    private void edge(int a, int b) {
        screenPoint(geometry.vertices[4 * a], geometry.vertices[4 * a + 1], scratch);
        float x = scratch.x, y = scratch.y;
        screenPoint(geometry.vertices[4 * b], geometry.vertices[4 * b + 1], scratch);
        drawer.line(x, y, scratch.x, scratch.y, 1.5f);
    }
}
