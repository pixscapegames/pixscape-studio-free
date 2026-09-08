package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;

/**
 * Studio authoring coordinates intentionally have no parallax display offset. Layer parallax is
 * preserved as authored metadata for Runtime and preview worlds.
 */
public final class StudioDisplayOffsetResolver {

    private final Vector2 scratchOffset = new Vector2();

    public StudioDisplayOffsetResolver(World world,
                                       DynamicEntityRenderState renderState,
                                       LayerStateSOA layerState,
                                       com.badlogic.gdx.graphics.OrthographicCamera camera) {
        if (world == null) throw new IllegalArgumentException("World is required.");
    }

    public void resolve(int entityId, Vector2 out) {
        if (out == null) throw new IllegalArgumentException("Output vector is required.");
        out.set(0f, 0f);
    }

    public void addTo(int entityId, Vector2 point) {
        if (point == null) return;
        resolve(entityId, scratchOffset);
        point.add(scratchOffset);
    }

    public void subtractFrom(int entityId, Vector2 point) {
        if (point == null) return;
        resolve(entityId, scratchOffset);
        point.sub(scratchOffset);
    }

    public void addTo(int entityId, float[] vertices, int vertexCount) {
        if (vertices == null || vertexCount <= 0) return;
        resolve(entityId, scratchOffset);
        int limit = Math.min(vertices.length & ~1, vertexCount * 2);
        for (int i = 0; i < limit; i += 2) {
            vertices[i] += scratchOffset.x;
            vertices[i + 1] += scratchOffset.y;
        }
    }
}
