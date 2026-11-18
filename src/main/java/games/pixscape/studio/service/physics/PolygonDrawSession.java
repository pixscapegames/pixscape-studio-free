package games.pixscape.studio.service.physics;

import games.pixscape.studio.service.geometry.PolygonEditSession;

/** Physics target metadata; gestures and candidate arrays belong to the shared editor. */
public final class PolygonDrawSession extends PolygonEditSession {
    public enum Mode { CREATE, EDIT }
    private Mode mode = Mode.CREATE;
    private int bodyEid = -1;
    private int physicsShapeId = -1;
    private float[] beforeVerts = new float[0];

    public void beginCreate(int bodyEid) {
        beginDraw();
        this.bodyEid = bodyEid;
    }

    public void beginEdit(int bodyEid, int physicsShapeId, float[] vertices, int count) {
        beginDraw();
        mode = Mode.EDIT;
        this.bodyEid = bodyEid;
        this.physicsShapeId = physicsShapeId;
        beforeVerts = java.util.Arrays.copyOf(vertices, count * 2);
    }

    @Override
    public void cancel() {
        super.cancel();
        mode = Mode.CREATE;
        bodyEid = -1;
        physicsShapeId = -1;
        beforeVerts = new float[0];
    }

    public Mode getMode() { return mode; }
    public boolean isCreateMode() { return mode == Mode.CREATE; }
    public boolean isEditMode() { return mode == Mode.EDIT; }
    public int getBodyEid() { return bodyEid; }
    public long getFixtureId() { return physicsShapeId; }
    public float[] getBeforeVerts() { return beforeVerts.clone(); }
    public int getBeforeCount() { return beforeVerts.length / 2; }
}
