package games.pixscape.studio.helper;

import games.pixscape.runtime.component.TransformComponent;

/** Shared allocation-free transform math for generic authored local vertices. */
public final class AuthoredGeometryTransform {
    private AuthoredGeometryTransform() {
    }

    public static float worldX(TransformComponent transform, float localX, float localY) {
        float x = (localX - transform.originX) * transform.scaleX;
        float y = (localY - transform.originY) * transform.scaleY;
        return transform.x + transform.cos * x - transform.sin * y;
    }

    public static float worldY(TransformComponent transform, float localX, float localY) {
        float x = (localX - transform.originX) * transform.scaleX;
        float y = (localY - transform.originY) * transform.scaleY;
        return transform.y + transform.sin * x + transform.cos * y;
    }

    public static void transformVertices(TransformComponent transform,
                                         float[] vertices,
                                         float[] out) {
        if (transform == null || vertices == null || out == null || out.length < vertices.length) {
            throw new IllegalArgumentException("Vertex transform requires matching input and output arrays.");
        }
        for (int i = 0; i < vertices.length; i += 2) {
            out[i] = worldX(transform, vertices[i], vertices[i + 1]);
            out[i + 1] = worldY(transform, vertices[i], vertices[i + 1]);
        }
    }
}
