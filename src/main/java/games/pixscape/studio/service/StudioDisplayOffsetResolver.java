package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.helper.ParallaxHelper;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;

/** Resolves transient display-space parallax offsets without mutating authored geometry. */
public final class StudioDisplayOffsetResolver {

    private final DynamicEntityRenderState renderState;
    private final LayerStateSOA layerState;
    private final OrthographicCamera camera;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final Vector2 scratchOffset = new Vector2();

    public StudioDisplayOffsetResolver(World world,
                                       DynamicEntityRenderState renderState,
                                       LayerStateSOA layerState,
                                       OrthographicCamera camera) {
        if (world == null) throw new IllegalArgumentException("World is required.");
        this.renderState = renderState;
        this.layerState = layerState;
        this.camera = camera;
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
    }

    public void resolve(int entityId, Vector2 out) {
        if (out == null) throw new IllegalArgumentException("Output vector is required.");
        out.set(0f, 0f);

        int renderSlot = renderState != null ? renderState.renderSlotForEntity(entityId)
                : DynamicEntityRenderState.NO_SLOT;
        if (renderSlot != DynamicEntityRenderState.NO_SLOT
                && renderSlot < renderState.activeCount
                && renderState.offsetX != null
                && renderState.offsetY != null) {
            out.set(renderState.offsetX[renderSlot], renderState.offsetY[renderSlot]);
            return;
        }

        if (camera == null || layerState == null || !mEntityIndex.has(entityId)) return;

        int layerIndex = mEntityIndex.get(entityId).getLayerIndex();
        if (layerIndex < 0 || layerIndex >= layerState.capacity() || !layerState.enabled[layerIndex]) return;

        float factorX = layerState.parallaxX[layerIndex];
        float factorY = layerState.parallaxY[layerIndex];
        ParallaxHelper.computeParallaxOffset(
                camera.position.x,
                camera.position.y,
                Float.isNaN(factorX) ? 1f : factorX,
                Float.isNaN(factorY) ? 1f : factorY,
                out);
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
