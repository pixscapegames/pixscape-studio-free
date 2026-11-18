package games.pixscape.studio.service.tiled;

import com.artemis.World;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.*;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.helper.GizmoDrawHelper;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditTiledObjectGeometryCommand;
import games.pixscape.studio.service.*;
import games.pixscape.studio.service.geometry.PolygonEditSession;

/** Canvas adapter for Tiled data. It never publishes preview arrays into the World. */
public final class TiledObjectEditor {
    private final World world;
    private final HistoryManager history;
    private final SelectionService selection;
    private final LayerService layers;
    private final StudioEditingModeService modes;
    private final TiledToolService tools;
    private final TiledObjectAuthoringService authoring;
    private final PolygonEditSession polygon = new PolygonEditSession();
    private final Vector2 point = new Vector2();
    private TiledObjectGeometry.Snapshot before, candidate;
    private int target = -1, layer = -1, selectedAtStart = -1, corner = -1;
    private long historyRevision;
    private boolean rectangleDrawing;
    private float startX, startY, mouseX, mouseY;
    private float handlePressX, handlePressY;
    private float[] rectanglePreview;
    private TiledToolService.Mode gestureMode;

    public TiledObjectEditor(World world, HistoryManager history, SelectionService selection,
                              LayerService layers, StudioEditingModeService modes, TiledToolService tools) {
        this.world = world;
        this.history = history;
        this.selection = selection;
        this.layers = layers;
        this.modes = modes;
        this.tools = tools;
        authoring = new TiledObjectAuthoringService(world, history);
    }

    public boolean allowsEditing() {
        return modes.allowsWorldEditingActions() && modes.getCurrentMode() == StudioEditingMode.NORMAL;
    }

    public boolean canCreate() {
        int layer = authoring.objectLayer(selection.getFirstSelectedEntityId());
        return allowsEditing() && selection.getSelectionSet().size == 1 && usable(layer);
    }

    public boolean handles(int e) {
        return allowsEditing() && selection.getSelectionSet().size == 1
                && TiledObjectGeometry.editable(world, e) && usable(e);
    }

    private boolean usable(int e) {
        if (e < 0 || !world.getEntityManager().isActive(e) || !selection.isSelectableInViewport(e)
                || !layers.isPublishedContentVisible(e)) {
            return false;
        }
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(e);
        if (index == null) {
            return false;
        }
        int owner = layers.getLayerEntity(index.layerIndex);
        LayerMetaComponent meta = owner < 0 ? null : world.getMapper(LayerMetaComponent.class).get(owner);
        return meta == null || !meta.locked;
    }

    public boolean active() {
        return rectangleDrawing || polygon.isActive() || polygon.dragging();
    }

    public void cancel() {
        polygon.cancel();
        rectangleDrawing = false;
        rectanglePreview = null;
        before = candidate = null;
        target = layer = selectedAtStart = corner = -1;
    }

    public boolean process(float mx, float my, float wpp, boolean pressed, boolean down,
                           boolean released, boolean escape, boolean backspace, boolean overUi) {
        mouseX = mx;
        mouseY = my;
        int selected = selection.getFirstSelectedEntityId();
        if (active() && (!allowsEditing() || selected != selectedAtStart || tools.getMode() != gestureMode
                || history.getMutationRevision() != historyRevision || !usable(target >= 0 ? target : layer))) {
            cancel();
        }
        if (escape && (active() || tools.isObjectTool())) {
            cancel();
            tools.setMode(TiledToolService.Mode.BRUSH);
            return true;
        }
        if (!allowsEditing()) {
            return false;
        }
        if (polygon.dragging()) {
            if ((down || released) && (mx != handlePressX || my != handlePressY)) {
                if (corner < 0) {
                    parentPoint(target, mx, my);
                } else {
                    TiledObjectGeometry.worldToLocal(world, target, mx, my, point);
                }
                if (Float.isFinite(point.x) && Float.isFinite(point.y)) {
                    if (corner < 0) {
                        candidate = TiledObjectGeometry.translate(before, point.x - startX, point.y - startY);
                    } else if (TiledObjectGeometry.rectangle(world, target)) {
                        candidate = TiledObjectGeometry.dragCorner(before, corner, point.x, point.y);
                    } else {
                        polygon.dragTo(point.x, point.y);
                        candidate = TiledObjectGeometry.withVertices(before, polygon.dragCandidate());
                    }
                }
            } else if (down || released) {
                candidate = before;
            }
            if (released || !down) {
                commit(target, before, candidate);
                cancel();
            }
            return true;
        }
        if (tools.isObjectTool()) {
            if (!canCreate()) {
                cancel();
                return false;
            }
            if (!active() && pressed && !overUi) {
                layer = authoring.objectLayer(selected);
                begin(selected);
                TiledObjectGeometry.worldToLocal(world, layer, mx, my, point);
                startX = point.x; startY = point.y;
                if (tools.is(TiledToolService.Mode.OBJECT_RECTANGLE)) {
                    rectangleDrawing = true;
                } else {
                    polygon.beginDraw();
                }
            }
            if (rectangleDrawing) {
                TiledObjectGeometry.worldToLocal(world, layer, mx, my, point);
                rectanglePreview = authoring.rectangleVertices(layer, startX, startY, point.x, point.y);
                if (released || (!down && !pressed)) {
                    float[] corners = rectanglePreview;
                    int destination = layer;
                    cancel();
                    if (Vector2.dst2(corners[0], corners[1], corners[2], corners[3]) > .0001f
                            && Vector2.dst2(corners[0], corners[1], corners[6], corners[7]) > .0001f) {
                        selectCreated(authoring.createRectangle(destination, corners));
                    }
                }
            } else if (polygon.isActive()) {
                PolygonEditSession.DrawAction result = polygon.drawInput(mx, my, pressed && !overUi,
                        backspace && !overUi, false, (GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX * .5f + 2) * wpp);
                if (result == PolygonEditSession.DrawAction.CLOSED) {
                    float[] vertices = polygon.toFloatArrayWorld();
                    for (int i = 0; i < vertices.length; i += 2) {
                        TiledObjectGeometry.worldToLocal(world, layer, vertices[i], vertices[i + 1], point);
                        vertices[i] = point.x; vertices[i + 1] = point.y;
                    }
                    int destination = layer;
                    cancel();
                    selectCreated(authoring.createPolygon(destination, vertices));
                }
            }
            return !overUi || active();
        }
        if (!overUi && pressed && handles(selected)) {
            TiledObjectGeometry.Snapshot snapshot = TiledObjectGeometry.capture(world, selected);
            float[] vertices = worldVertices(selected, snapshot);
            int vertex = PolygonEditSession.pickVertex(wpp, vertices, vertices.length / 2, mx, my, 2,
                    GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX);
            if (vertex >= 0 || com.badlogic.gdx.math.Intersector.isPointInPolygon(vertices, 0, vertices.length, mx, my)
                    || PolygonEditSession.pickEdge(vertices, mx, my, 4 * wpp) >= 0) {
                begin(selected);
                target = selected;
                before = candidate = snapshot;
                corner = vertex;
                handlePressX = mx;
                handlePressY = my;
                parentPoint(selected, mx, my);
                startX = point.x;
                startY = point.y;
                polygon.beginDrag(TiledObjectGeometry.vertices(snapshot), Math.max(0, vertex));
                return true;
            }
        }
        return false;
    }

    private void parentPoint(int e, float x, float y) {
        int parent = TiledObjectAuthoringService.parent(world, e);
        if (parent >= 0) {
            TiledObjectGeometry.worldToLocal(world, parent, x, y, point);
        } else {
            point.set(x, y);
        }
    }

    private void begin(int selected) {
        selectedAtStart = selected;
        historyRevision = history.getMutationRevision();
        gestureMode = tools.getMode();
    }

    private void selectCreated(int e) {
        tools.setMode(TiledToolService.Mode.BRUSH);
        selection.selectOnly(e);
    }

    public void commit(int e, TiledObjectGeometry.Snapshot before, TiledObjectGeometry.Snapshot after) {
        if (before == null || after == null) {
            return;
        }
        EditTiledObjectGeometryCommand command = new EditTiledObjectGeometryCommand(world, history.historyIds(), e, before, after);
        if (!command.isNoop()) {
            history.execute(command);
        }
    }

    public int vertexAt(float x, float y, float wpp) {
        int e = selection.getFirstSelectedEntityId();
        if (!handles(e) || TiledObjectGeometry.rectangle(world, e)) {
            return -1;
        }
        float[] vertices = worldVertices(e, TiledObjectGeometry.capture(world, e));
        return PolygonEditSession.pickVertex(wpp, vertices, vertices.length / 2, x, y, 2,
                GizmoDrawHelper.SHAPE_VERTEX_HANDLE_SIZE_PX);
    }

    public int edgeAt(float x, float y, float wpp) {
        int e = selection.getFirstSelectedEntityId();
        if (!handles(e) || TiledObjectGeometry.rectangle(world, e)) {
            return -1;
        }
        return PolygonEditSession.pickEdge(worldVertices(e, TiledObjectGeometry.capture(world, e)), x, y, 7 * wpp);
    }

    public void insertVertex(int e, int edge, float x, float y) {
        if (!handles(e)) {
            return;
        }
        TiledObjectGeometry.Snapshot before = TiledObjectGeometry.capture(world, e);
        TiledObjectGeometry.worldToLocal(world, e, x, y, point);
        commit(e, before, TiledObjectGeometry.withVertices(before,
                PolygonEditSession.insertVertex(before.vertices(), edge, point.x, point.y)));
    }

    public void removeVertex(int e, int vertex) {
        if (!handles(e)) {
            return;
        }
        TiledObjectGeometry.Snapshot before = TiledObjectGeometry.capture(world, e);
        commit(e, before, TiledObjectGeometry.withVertices(before,
                PolygonEditSession.removeVertex(before.vertices(), vertex)));
    }

    private float[] worldVertices(int e, TiledObjectGeometry.Snapshot snapshot) {
        float[] vertices = TiledObjectGeometry.vertices(snapshot);
        TransformComponent pose = new TransformComponent();
        snapshot.pose().apply(pose);
        pose.refreshCaches();
        int parent = TiledObjectAuthoringService.parent(world, e);
        for (int i = 0; i < vertices.length; i += 2) {
            float x = (vertices[i] - pose.originX) * pose.scaleX;
            float y = (vertices[i + 1] - pose.originY) * pose.scaleY;
            point.set(pose.x + pose.cos * x - pose.sin * y, pose.y + pose.sin * x + pose.cos * y);
            if (parent >= 0) {
                TiledObjectGeometry.localToWorld(world, parent, point.x, point.y, point);
            }
            vertices[i] = point.x; vertices[i + 1] = point.y;
        }
        return vertices;
    }

    public void draw(StudioDrawContext ctx) {
        if (!allowsEditing()) {
            return;
        }
        if (polygon.isActive()) {
            games.pixscape.studio.helper.PolygonEditorDrawHelper.drawSession(ctx, polygon, mouseX, mouseY);
        }
        if (rectanglePreview != null) {
            float[] vertices = rectanglePreview.clone();
            for (int i = 0; i < vertices.length; i += 2) {
                TiledObjectGeometry.localToWorld(world, layer, vertices[i], vertices[i + 1], point);
                vertices[i] = point.x; vertices[i + 1] = point.y;
            }
            drawContour(ctx, vertices);
        }
        int selected = selection.getFirstSelectedEntityId();
        if (handles(selected) && !tools.isObjectTool()) {
            float[] vertices = worldVertices(selected, candidate != null ? candidate : TiledObjectGeometry.capture(world, selected));
            drawContour(ctx, vertices);
            for (int i = 0; i < vertices.length; i += 2) {
                GizmoDrawHelper.drawPolygonVertexHandle(ctx, vertices[i], vertices[i + 1], Color.CYAN);
            }
        }
    }

    private static void drawContour(StudioDrawContext ctx, float[] vertices) {
        games.pixscape.studio.helper.PolygonEditorDrawHelper.drawContour(ctx, vertices, vertices.length / 2, Color.CYAN);
    }
}
