package games.pixscape.studio.service.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public final class PolygonDrawSession {

    public enum Mode {
        CREATE,
        EDIT
    }

    private static final float CLOSE_EPSILON = 1e-6f;

    private boolean active;
    private Mode mode = Mode.CREATE;
    private int bodyEid = -1;
    private int physicsShapeId = -1;

    private final Array<Vector2> points = new Array<>();
    private boolean closed;

    // useful for EDIT commit
    private float[] beforeVerts = new float[0];
    private int beforeCount = 0;

    public void beginCreate(int bodyEid) {
        this.active = true;
        this.mode = Mode.CREATE;
        this.bodyEid = bodyEid;
        this.physicsShapeId = -1;
        this.closed = false;
        this.beforeVerts = new float[0];
        this.beforeCount = 0;
        clearPoints();
    }

    /**
     * Starts an edit session for an existing polygon.
     * The session starts empty (redraw), but keeps the previous geometry
     * to allow undo/redo through ReplacePolygonVerticesCommand.
     */
    public void beginEdit(int bodyEid, int physicsShapeId, float[] existingVerts, int existingCount) {
        this.active = true;
        this.mode = Mode.EDIT;
        this.bodyEid = bodyEid;
        this.physicsShapeId = physicsShapeId;
        this.closed = false;
        this.beforeCount = Math.max(0, existingCount);
        this.beforeVerts = copyVerts(existingVerts, this.beforeCount);
        clearPoints();
    }

    public void cancel() {
        this.active = false;
        this.mode = Mode.CREATE;
        this.bodyEid = -1;
        this.physicsShapeId = -1;
        this.closed = false;
        this.beforeVerts = new float[0];
        this.beforeCount = 0;
        clearPoints();
    }

    public void reset() {
        this.closed = false;
        clearPoints();
    }

    public boolean isActive() {
        return active;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isCreateMode() {
        return mode == Mode.CREATE;
    }

    public boolean isEditMode() {
        return mode == Mode.EDIT;
    }

    public int getBodyEid() {
        return bodyEid;
    }

    public long getFixtureId() {
        return physicsShapeId;
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
        if (!active || closed) return;
        points.add(new Vector2(x, y));
    }

    public boolean removeLastPoint() {
        if (!active || closed || points.size == 0) return false;
        points.pop();
        return true;
    }

    public boolean tryCloseFromPoint(float x, float y, float closeRadiusWorld) {
        if (!canClose()) return false;

        Vector2 first = firstPoint();
        if (first == null) return false;

        float dx = x - first.x;
        float dy = y - first.y;
        float r2 = closeRadiusWorld * closeRadiusWorld;

        if (dx * dx + dy * dy > r2) return false;

        close();
        return true;
    }

    public void close() {
        if (!canClose()) return;
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
        if (last == null) return false;
        return Math.abs(last.x - x) <= CLOSE_EPSILON
                && Math.abs(last.y - y) <= CLOSE_EPSILON;
    }

    public float[] getBeforeVerts() {
        return copyVerts(beforeVerts, beforeCount);
    }

    public int getBeforeCount() {
        return beforeCount;
    }

    private void clearPoints() {
        for (int i = 0; i < points.size; i++) {
            Vector2 p = points.get(i);
            if (p != null) p.setZero();
        }
        points.clear();
    }

    private static float[] copyVerts(float[] verts, int count) {
        int floatCount = Math.max(0, count) * 2;
        float[] out = new float[floatCount];
        if (verts != null && floatCount > 0) {
            System.arraycopy(verts, 0, out, 0, Math.min(floatCount, verts.length));
        }
        return out;
    }
}
