package games.pixscape.studio.component.physics;

/**
 * Serialized convex polygon part generated from an authored polygon.
 * <p>
 * Pure data class. Validation is handled by the polygon authoring service.
 */
public final class ConvexPolygonPartData {

    /**
     * Local body coordinates, in meters.
     */
    public float[] verts = new float[0];

    /**
     * Number of vertices in verts.
     */
    public int count = 0;
}