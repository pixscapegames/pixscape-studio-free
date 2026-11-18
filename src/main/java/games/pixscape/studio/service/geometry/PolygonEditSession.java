package games.pixscape.studio.service.geometry;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class PolygonEditSession {

    private static final float CLOSE_EPSILON = 1e-6f;
    private boolean active;
    private final Array<Vector2> points = new Array<>();
    private boolean closed;

    private float[] dragBefore;
    private float[] dragCandidate;
    private int dragIndex = -1;

    public void beginDraw() {
        cancel();
        active = true;
    }

    public void cancel() {
        active = false;
        closed = false;
        clearPoints();
        cancelDrag();
    }

    public void reset() {
        this.closed = false;
        clearPoints();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isClosed() {
        return closed;
    }

    public int pointCount() {
        return points.size;
    }

    public Array<Vector2> points() {
        return points;
    }

    public boolean canClose() {
        return active && !closed && points.size >= 3;
    }

    public Vector2 firstPoint() {
        return points.size == 0 ? null : points.first();
    }

    public Vector2 lastPoint() {
        return points.size == 0 ? null : points.peek();
    }

    public void addPoint(float x, float y) {
        if (!active || closed) {
            return;
        }
        points.add(new Vector2(x, y));
    }

    public boolean removeLastPoint() {
        if (!active || closed || points.size == 0) {
            return false;
        }
        points.pop();
        return true;
    }

    public boolean tryCloseFromPoint(float x, float y, float closeRadiusWorld) {
        if (!canClose()) {
            return false;
        }

        Vector2 first = firstPoint();
        if (first == null) {
            return false;
        }

        float dx = x - first.x;
        float dy = y - first.y;
        float r2 = closeRadiusWorld * closeRadiusWorld;

        if (dx * dx + dy * dy > r2) {

            return false;

        }

        close();
        return true;
    }

    public void close() {
        if (!canClose()) {
            return;
        }
        closed = true;
    }

    public boolean isValidPolygon() {
        return closed && points.size >= 3;
    }

    public float[] toFloatArrayWorld() {
        float[] out = new float[points.size * 2];
        for (int i = 0; i < points.size; i++) {
            Vector2 p = points.get(i);
            out[i * 2] = p.x;
            out[i * 2 + 1] = p.y;
        }
        return out;
    }

    public boolean wouldDuplicateLast(float x, float y) {
        Vector2 last = lastPoint();
        if (last == null) {
            return false;
        }
        return Math.abs(last.x - x) <= CLOSE_EPSILON
                && Math.abs(last.y - y) <= CLOSE_EPSILON;
    }

    public enum DrawAction { CONTINUE, CANCELLED, CLOSED }

    public DrawAction drawInput(float x, float y, boolean click, boolean backspace,
                                boolean escape, float closeRadius) {
        if (escape) {
            cancel();
            return DrawAction.CANCELLED;
        }
        if (backspace) {
            removeLastPoint();
        } else if (click) {
            if (tryCloseFromPoint(x, y, closeRadius)) {
                return DrawAction.CLOSED;
            }
            if (!wouldDuplicateLast(x, y)) {
                addPoint(x, y);
            }
        }
        return DrawAction.CONTINUE;
    }

    public void beginDrag(float[] vertices, int index) {
        if (vertices == null || index < 0 || index * 2 + 1 >= vertices.length) {
            throw new IllegalArgumentException("Invalid vertex drag.");
        }
        dragBefore = vertices.clone();
        dragCandidate = vertices.clone();
        dragIndex = index;
    }

    public boolean dragging() { return dragIndex >= 0; }
    public float[] dragBefore() { return dragBefore; }
    public float[] dragCandidate() { return dragCandidate; }

    public void dragTo(float x, float y) {
        if (dragging() && Float.isFinite(x) && Float.isFinite(y)) {
            dragCandidate[dragIndex * 2] = x;
            dragCandidate[dragIndex * 2 + 1] = y;
        }
    }

    public void cancelDrag() {
        dragIndex = -1;
        dragBefore = null;
        dragCandidate = null;
    }

    public static int pickVertex(float wpp, float[] vertices, int count, float x, float y,
                                 float tolerancePx, float sizePx) {
        float radius = (sizePx * .5f + tolerancePx) * wpp;
        for (int i = 0; vertices != null && i < count; i++) {
            if (Math.abs(x - vertices[i * 2]) <= radius && Math.abs(y - vertices[i * 2 + 1]) <= radius) {
                return i;
            }
        }
        return -1;
    }

    public static int pickEdge(float[] vertices, float x, float y, float radius) {
        int best = -1;
        float distance = radius * radius;
        for (int i = 0; i < vertices.length; i += 2) {
            int next = (i + 2) % vertices.length;
            float dx = vertices[next] - vertices[i], dy = vertices[next + 1] - vertices[i + 1];
            float length2 = dx * dx + dy * dy;
            float t = length2 == 0 ? 0 : Math.max(0, Math.min(1,
                    ((x - vertices[i]) * dx + (y - vertices[i + 1]) * dy) / length2));
            float ex = x - vertices[i] - t * dx, ey = y - vertices[i + 1] - t * dy;
            float d = ex * ex + ey * ey;
            if (d <= distance) {
                distance = d;
                best = i / 2;
            }
        }
        return best;
    }

    public static float[] insertVertex(float[] vertices, int edge, float x, float y) {
        int offset = (edge + 1) * 2;
        float[] result = new float[vertices.length + 2];
        System.arraycopy(vertices, 0, result, 0, offset);
        result[offset] = x;
        result[offset + 1] = y;
        System.arraycopy(vertices, offset, result, offset + 2, vertices.length - offset);
        return result;
    }

    public static float[] removeVertex(float[] vertices, int index) {
        if (vertices.length <= 6) {
            throw new IllegalArgumentException("A polygon requires at least three vertices.");
        }
        float[] result = new float[vertices.length - 2];
        System.arraycopy(vertices, 0, result, 0, index * 2);
        System.arraycopy(vertices, index * 2 + 2, result, index * 2, result.length - index * 2);
        return result;
    }

    private void clearPoints() {
        for (int i = 0; i < points.size; i++) {
            Vector2 p = points.get(i);
            if (p != null) {
                p.setZero();
            }
        }
        points.clear();
    }

}
