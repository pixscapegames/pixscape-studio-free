package games.pixscape.studio.service.tiled;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.commands.EditTransformCommand;
import games.pixscape.studio.model.EntityKind;

/** Authored contours are the sole authority. Rectangle edge lengths are derived, not persisted. */
public final class TiledObjectGeometry {
    private TiledObjectGeometry() { }

    public record Snapshot(float[] vertices, float width, float height, EditTransformCommand.Snapshot pose) {
        public Snapshot {
            vertices = vertices == null ? null : vertices.clone();
        }

        public boolean sameAs(Snapshot other) {
            return other != null && java.util.Arrays.equals(vertices, other.vertices)
                    && width == other.width && height == other.height && pose.sameAs(other.pose);
        }
    }

    public static boolean editable(World world, int e) {
        if (e < 0 || !world.getEntityManager().isActive(e)) {
            return false;
        }
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).get(e);
        return meta != null && world.getMapper(TransformComponent.class).has(e)
                && world.getMapper(DimensionsComponent.class).has(e)
                && (meta.kind == EntityKind.TILED_RECTANGLE || meta.kind == EntityKind.POLYGON);
    }

    public static boolean rectangle(World world, int e) {
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).get(e);
        return meta != null && meta.kind == EntityKind.TILED_RECTANGLE;
    }

    public static Snapshot capture(World world, int e) {
        PolygonComponent polygon = world.getMapper(PolygonComponent.class).get(e);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(e);
        return new Snapshot(polygon == null ? null : polygon.vertices, dimensions.width, dimensions.height,
                EditTransformCommand.Snapshot.capture(world.getMapper(TransformComponent.class).get(e)));
    }

    public static float[] vertices(Snapshot state) {
        return state.vertices != null ? state.vertices.clone()
                : new float[]{0, 0, state.width, 0, state.width, state.height, 0, state.height};
    }

    public static void validate(float[] vertices) {
        if (vertices == null || vertices.length < 6 || (vertices.length & 1) != 0) {
            throw new IllegalArgumentException("A polygon requires at least three XY pairs.");
        }
        for (float value : vertices) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Object coordinates must be finite.");
            }
        }
    }

    public static Snapshot withVertices(Snapshot before, float[] vertices) {
        validate(vertices);
        float minX = vertices[0], maxX = minX, minY = vertices[1], maxY = minY;
        for (int i = 2; i < vertices.length; i += 2) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            maxY = Math.max(maxY, vertices[i + 1]);
        }
        return new Snapshot(vertices, maxX - minX, maxY - minY, before.pose);
    }

    public static Snapshot translate(Snapshot before, float dx, float dy) {
        TransformComponent pose = new TransformComponent();
        before.pose.apply(pose);
        return new Snapshot(before.vertices, before.width, before.height,
                before.pose.withX(pose.x + dx).withY(pose.y + dy));
    }

    /** Width follows edge 0->1, height edge 0->3, in authored local projected pixels. */
    public static float edgeLength(Snapshot state, boolean width) {
        if (state.vertices == null) {
            return width ? state.width : state.height;
        }
        int end = width ? 2 : 6;
        return Vector2.dst(state.vertices[0], state.vertices[1], state.vertices[end], state.vertices[end + 1]);
    }

    public static Snapshot resize(Snapshot before, float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Rectangle edge lengths must be finite and positive.");
        }
        if (before.vertices == null) {
            return new Snapshot(null, width, height, before.pose);
        }
        float oldWidth = edgeLength(before, true), oldHeight = edgeLength(before, false);
        if (before.vertices.length != 8 || oldWidth == 0 || oldHeight == 0) {
            throw new IllegalArgumentException("Rectangle axes are degenerate.");
        }
        float[] p = before.vertices;
        float ux = (p[2] - p[0]) * width / oldWidth, uy = (p[3] - p[1]) * width / oldWidth;
        float vx = (p[6] - p[0]) * height / oldHeight, vy = (p[7] - p[1]) * height / oldHeight;
        return withVertices(before, new float[]{p[0], p[1], p[0] + ux, p[1] + uy,
                p[0] + ux + vx, p[1] + uy + vy, p[0] + vx, p[1] + vy});
    }

    /** Keeps the opposite corner fixed and preserves both projected rectangle axes. */
    public static Snapshot dragCorner(Snapshot before, int corner, float x, float y) {
        float[] p = vertices(before);
        int opposite = ((corner + 2) % 4) * 2;
        float ux = p[2] - p[0], uy = p[3] - p[1];
        float vx = p[6] - p[0], vy = p[7] - p[1];
        float determinant = ux * vy - uy * vx;
        if (Math.abs(determinant) < 1e-8f) {
            return before;
        }
        float dx = x - p[opposite], dy = y - p[opposite + 1];
        float a = (dx * vy - dy * vx) / determinant;
        float b = (ux * dy - uy * dx) / determinant;
        boolean right = corner == 1 || corner == 2, top = corner >= 2;
        a = right ? Math.max(.0001f, a) : Math.min(-.0001f, a);
        b = top ? Math.max(.0001f, b) : Math.min(-.0001f, b);
        float left = right ? 0 : a, bottom = top ? 0 : b;
        float ox = p[opposite] + left * ux + bottom * vx;
        float oy = p[opposite + 1] + left * uy + bottom * vy;
        ux *= Math.abs(a); uy *= Math.abs(a);
        vx *= Math.abs(b); vy *= Math.abs(b);
        if (before.vertices != null) {
            return withVertices(before, new float[]{ox, oy, ox + ux, oy + uy,
                    ox + ux + vx, oy + uy + vy, ox + vx, oy + vy});
        }
        TransformComponent t = new TransformComponent();
        before.pose.apply(t);
        t.refreshCaches();
        t.x += t.cos * ox * t.scaleX - t.sin * oy * t.scaleY;
        t.y += t.sin * ox * t.scaleX + t.cos * oy * t.scaleY;
        return new Snapshot(null, Math.abs(ux), Math.abs(vy), EditTransformCommand.Snapshot.capture(t));
    }

    public static Vector2 localToWorld(World world, int e, float x, float y, Vector2 out) {
        TransformComponent t = world.getMapper(TransformComponent.class).get(e);
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        WorldTransformState state = hierarchy == null ? null : hierarchy.worldTransforms();
        if (state != null && state.isResolved(e)) {
            if (!world.getMapper(GameObjectComponent.class).has(e)) {
                x -= t.originX;
                y -= t.originY;
            }
            return out.set(state.m00[e] * x + state.m01[e] * y + state.m02[e],
                    state.m10[e] * x + state.m11[e] * y + state.m12[e]);
        }
        t.refreshCaches();
        x -= t.originX;
        y -= t.originY;
        return out.set(t.x + t.cos * x * t.scaleX - t.sin * y * t.scaleY,
                t.y + t.sin * x * t.scaleX + t.cos * y * t.scaleY);
    }

    public static Vector2 worldToLocal(World world, int e, float x, float y, Vector2 out) {
        TransformComponent t = world.getMapper(TransformComponent.class).get(e);
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        WorldTransformState state = hierarchy == null ? null : hierarchy.worldTransforms();
        if (state != null && state.isResolved(e)) {
            float determinant = state.m00[e] * state.m11[e] - state.m01[e] * state.m10[e];
            if (Math.abs(determinant) < 1e-10f) {
                return out.set(Float.NaN, Float.NaN);
            }
            x -= state.m02[e]; y -= state.m12[e];
            out.set((state.m11[e] * x - state.m01[e] * y) / determinant,
                    (-state.m10[e] * x + state.m00[e] * y) / determinant);
            if (!world.getMapper(GameObjectComponent.class).has(e)) {
                out.add(t.originX, t.originY);
            }
            return out;
        }
        t.refreshCaches();
        x -= t.x; y -= t.y;
        return out.set((t.cos * x + t.sin * y) / t.scaleX + t.originX,
                (-t.sin * x + t.cos * y) / t.scaleY + t.originY);
    }
}
