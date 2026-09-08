package games.pixscape.studio.helper;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.helper.QuadGeometryHelper;
import games.pixscape.runtime.hierarchy.GameObjectCompositionState;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.system.GameObjectCompositionSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;

/** Builds exact descendant bounds in a selected Game Object root's local coordinate system. */
public final class GameObjectGizmoGeometry {
    private final ComponentMapper<GameObjectComponent> gameObjects;
    private final ComponentMapper<OrientedBoundsComponent> orientedBounds;
    private final ComponentMapper<QuadDeformComponent> quadDeforms;
    private final ComponentMapper<TransformComponent> transforms;
    private final GameObjectHierarchySystem hierarchy;
    private final GameObjectCompositionSystem composition;
    private final IntArray stack = new IntArray(false, 16);
    private final float[] exactWorldCorners = new float[8];
    private final float[] localBounds = new float[4];

    public GameObjectGizmoGeometry(World world) {
        if (world == null) throw new IllegalArgumentException("World is required.");
        gameObjects = world.getMapper(GameObjectComponent.class);
        orientedBounds = world.getMapper(OrientedBoundsComponent.class);
        quadDeforms = world.getMapper(QuadDeformComponent.class);
        transforms = world.getMapper(TransformComponent.class);
        hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        composition = world.getSystem(GameObjectCompositionSystem.class);
    }

    /** Writes BL, BR, TR and TL in world space, preserving the selected root's orientation. */
    public boolean writeWorldCorners(
            int rootEntityId, float fallbackHalfWorld, float[] out8) {
        if (out8 == null || out8.length < 8 || hierarchy == null) return false;
        WorldTransformState worldState = hierarchy.worldTransforms();
        if (!worldState.isResolved(rootEntityId)) return false;
        if (!writeLocalBounds(rootEntityId, localBounds)) {
            writeFallbackLocalBounds(rootEntityId, fallbackHalfWorld, worldState, localBounds);
        }
        transformRectangle(worldState, rootEntityId, localBounds, out8);
        return true;
    }

    /** Writes local minX, minY, maxX and maxY from exact rendered descendant corners. */
    public boolean writeLocalBounds(int rootEntityId, float[] out4) {
        if (out4 == null || out4.length < 4 || hierarchy == null || composition == null) {
            return false;
        }
        WorldTransformState worldState = hierarchy.worldTransforms();
        if (!worldState.isResolved(rootEntityId)) return false;
        GameObjectTopologyState topology = hierarchy.topology();
        GameObjectCompositionState compositionState = composition.state();
        if (rootEntityId < 0 || rootEntityId >= topology.firstChildEntityId.length
                || rootEntityId >= compositionState.hierarchyVisible.length
                || !compositionState.hierarchyVisible[rootEntityId]) {
            return false;
        }

        float r00 = worldState.m00[rootEntityId];
        float r01 = worldState.m01[rootEntityId];
        float r02 = worldState.m02[rootEntityId];
        float r10 = worldState.m10[rootEntityId];
        float r11 = worldState.m11[rootEntityId];
        float r12 = worldState.m12[rootEntityId];
        float determinant = r00 * r11 - r01 * r10;
        if (Math.abs(determinant) <= 0.0001f) return false;
        float inverseDeterminant = 1f / determinant;

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        boolean found = false;

        stack.clear();
        for (int child = topology.firstChildEntityId[rootEntityId]; child >= 0;
             child = topology.nextSiblingEntityId[child]) {
            stack.add(child);
        }
        while (stack.size > 0) {
            int entityId = stack.pop();
            if (entityId < 0 || entityId >= compositionState.hierarchyVisible.length
                    || !compositionState.hierarchyVisible[entityId]) {
                continue;
            }
            if (gameObjects.has(entityId)) {
                for (int child = topology.firstChildEntityId[entityId]; child >= 0;
                     child = topology.nextSiblingEntityId[child]) {
                    stack.add(child);
                }
                continue;
            }
            if (!composition.contributesDrawableBounds(entityId)) continue;
            if (!writeExactWorldCorners(entityId, worldState, exactWorldCorners)) continue;
            for (int corner = 0; corner < 4; corner++) {
                float dx = exactWorldCorners[corner * 2] - r02;
                float dy = exactWorldCorners[corner * 2 + 1] - r12;
                float localX = (r11 * dx - r01 * dy) * inverseDeterminant;
                float localY = (-r10 * dx + r00 * dy) * inverseDeterminant;
                minX = Math.min(minX, localX);
                minY = Math.min(minY, localY);
                maxX = Math.max(maxX, localX);
                maxY = Math.max(maxY, localY);
                found = true;
            }
        }

        if (!found) return false;
        out4[0] = minX;
        out4[1] = minY;
        out4[2] = maxX;
        out4[3] = maxY;
        return true;
    }

    private boolean writeExactWorldCorners(
            int entityId, WorldTransformState worldState, float[] out8) {
        OrientedBoundsComponent bounds = orientedBounds.getSafe(entityId, null);
        if (bounds == null) return false;
        QuadDeformComponent deform = quadDeforms.getSafe(entityId, null);
        TransformComponent transform = transforms.getSafe(entityId, null);
        if (deform != null && transform != null && worldState.isResolved(entityId)) {
            QuadGeometryHelper.toWorldCorners(
                    bounds, transform, deform,
                    worldState.scaleX[entityId], worldState.scaleY[entityId], out8);
        } else {
            OrientedBoundsHelper.toCorners(bounds, out8);
        }
        return true;
    }

    private void writeFallbackLocalBounds(
            int rootEntityId,
            float fallbackHalfWorld,
            WorldTransformState worldState,
            float[] out4) {
        TransformComponent transform = transforms.getSafe(rootEntityId, null);
        float centerX = transform != null ? transform.originX : 0f;
        float centerY = transform != null ? transform.originY : 0f;
        float scale = Math.abs(worldState.scaleX[rootEntityId]);
        float halfLocal = scale > 0.0001f ? fallbackHalfWorld / scale : fallbackHalfWorld;
        out4[0] = centerX - halfLocal;
        out4[1] = centerY - halfLocal;
        out4[2] = centerX + halfLocal;
        out4[3] = centerY + halfLocal;
    }

    static void transformRectangle(
            WorldTransformState state, int entityId, float[] bounds, float[] out8) {
        float m00 = state.m00[entityId];
        float m01 = state.m01[entityId];
        float m02 = state.m02[entityId];
        float m10 = state.m10[entityId];
        float m11 = state.m11[entityId];
        float m12 = state.m12[entityId];
        writePoint(out8, 0, bounds[0], bounds[1], m00, m01, m02, m10, m11, m12);
        writePoint(out8, 2, bounds[2], bounds[1], m00, m01, m02, m10, m11, m12);
        writePoint(out8, 4, bounds[2], bounds[3], m00, m01, m02, m10, m11, m12);
        writePoint(out8, 6, bounds[0], bounds[3], m00, m01, m02, m10, m11, m12);
    }

    private static void writePoint(
            float[] out, int offset, float x, float y,
            float m00, float m01, float m02, float m10, float m11, float m12) {
        out[offset] = m00 * x + m01 * y + m02;
        out[offset + 1] = m10 * x + m11 * y + m12;
    }
}
