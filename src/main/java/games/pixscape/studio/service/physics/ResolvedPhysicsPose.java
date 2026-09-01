package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.system.GameObjectHierarchySystem;

/**
 * Resolves the logical Body pose used by Studio Physics overlays and interaction.
 * Physics fixture geometry remains Body-local; this helper only converts between that
 * local frame and the hierarchy-resolved logical world frame.
 */
public final class ResolvedPhysicsPose {
    public static final class Pose {
        public float x;
        public float y;
        public float rotationRad;
    }

    private final World world;
    private final GameObjectHierarchySystem hierarchy;
    private final Pose resolved = new Pose();

    public ResolvedPhysicsPose(World world) {
        this.world = world;
        this.hierarchy = world != null ? world.getSystem(GameObjectHierarchySystem.class) : null;
    }

    public boolean resolve(int entityId, Pose out) {
        if (world == null || out == null || entityId < 0) return false;
        TransformComponent authored = world.getMapper(TransformComponent.class)
                .getSafe(entityId, null);
        if (authored == null) return false;
        WorldTransformState state = hierarchy != null ? hierarchy.worldTransforms() : null;
        if (state != null && state.isResolved(entityId)) {
            out.x = state.x[entityId];
            out.y = state.y[entityId];
            out.rotationRad = state.rotationRad[entityId];
        } else {
            out.x = authored.x;
            out.y = authored.y;
            out.rotationRad = authored.rotationRad;
        }
        return true;
    }

    /** Remaps a point expressed in the authored Body world pose into the resolved Body world pose. */
    public boolean remapAuthoredWorldPoint(int entityId, Vector2 point) {
        if (point == null || world == null) return false;
        TransformComponent authored = world.getMapper(TransformComponent.class)
                .getSafe(entityId, null);
        if (authored == null || !resolve(entityId, resolved)) return false;
        float authoredCos = MathUtils.cos(authored.rotationRad);
        float authoredSin = MathUtils.sin(authored.rotationRad);
        float dx = point.x - authored.x;
        float dy = point.y - authored.y;
        float localX = authoredCos * dx + authoredSin * dy;
        float localY = -authoredSin * dx + authoredCos * dy;
        float resolvedCos = MathUtils.cos(resolved.rotationRad);
        float resolvedSin = MathUtils.sin(resolved.rotationRad);
        point.set(resolved.x + resolvedCos * localX - resolvedSin * localY,
                resolved.y + resolvedSin * localX + resolvedCos * localY);
        return true;
    }

    /**
     * Remaps authored-world fixture vertices into the resolved logical Body pose.
     * The caller retains ownership of the array; no geometry is persisted or allocated.
     */
    public boolean remapAuthoredWorldPoints(int entityId, float[] points, int pointCount) {
        if (points == null || pointCount < 0 || points.length < pointCount * 2
                || world == null) return false;
        TransformComponent authored = world.getMapper(TransformComponent.class)
                .getSafe(entityId, null);
        if (authored == null || !resolve(entityId, resolved)) return false;

        float authoredCos = MathUtils.cos(authored.rotationRad);
        float authoredSin = MathUtils.sin(authored.rotationRad);
        float resolvedCos = MathUtils.cos(resolved.rotationRad);
        float resolvedSin = MathUtils.sin(resolved.rotationRad);
        for (int i = 0; i < pointCount; i++) {
            int offset = i * 2;
            float dx = points[offset] - authored.x;
            float dy = points[offset + 1] - authored.y;
            float localX = authoredCos * dx + authoredSin * dy;
            float localY = -authoredSin * dx + authoredCos * dy;
            points[offset] = resolved.x + resolvedCos * localX - resolvedSin * localY;
            points[offset + 1] = resolved.y + resolvedSin * localX + resolvedCos * localY;
        }
        return true;
    }

    /** Converts a resolved-world pointer into Body-local world-unit geometry coordinates. */
    public boolean resolvedWorldToLocal(int entityId, float worldX, float worldY, Vector2 out) {
        if (out == null || !resolve(entityId, resolved)) return false;
        float cos = MathUtils.cos(resolved.rotationRad);
        float sin = MathUtils.sin(resolved.rotationRad);
        float dx = worldX - resolved.x;
        float dy = worldY - resolved.y;
        out.set(cos * dx + sin * dy, -sin * dx + cos * dy);
        return true;
    }
}
