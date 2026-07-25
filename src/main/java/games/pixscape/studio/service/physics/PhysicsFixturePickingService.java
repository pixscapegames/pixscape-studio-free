package games.pixscape.studio.service.physics;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.service.PhysicsService;

/** Picks compiled fixture geometry while returning source-shape provenance. */
public final class PhysicsFixturePickingService {

    public static final class PickResult {
        public int bodyEntityId = PhysicsSelectionService.NO_BODY;
        public int physicsShapeId = PhysicsSelectionService.NO_SHAPE;
        public int partIndex = PhysicsSelectionService.NO_PART;

        public boolean hit() {
            return physicsShapeId > 0 && partIndex >= 0;
        }
    }

    private final PhysicsService physicsService;
    private final Vector2 tmpCenter = new Vector2();
    private final float[] vertexScratch = new float[16];

    public PhysicsFixturePickingService(PhysicsService physicsService) {
        if (physicsService == null) {
            throw new IllegalArgumentException("physicsService cannot be null.");
        }
        this.physicsService = physicsService;
    }

    public PickResult pick(
            int bodyEntityId, float worldX, float worldY, float toleranceWU) {
        PickResult result = new PickResult();
        PhysicsCompiledFixturesComponent compiled =
                physicsService.getCompiledFixturesComponent(bodyEntityId);
        if (compiled == null || !compiled.valid || compiled.fixtures == null) {
            return result;
        }

        for (int i = compiled.fixtures.size - 1; i >= 0; i--) {
            CompiledFixtureData fixture = compiled.fixtures.get(i);
            if (fixture != null
                    && hitTest(bodyEntityId, fixture, worldX, worldY, toleranceWU)) {
                result.bodyEntityId = bodyEntityId;
                result.physicsShapeId = fixture.physicsShapeId;
                result.partIndex = fixture.partIndex;
                return result;
            }
        }
        return result;
    }

    private boolean hitTest(
            int bodyEntityId,
            CompiledFixtureData fixture,
            float worldX,
            float worldY,
            float toleranceWU) {
        if (fixture.shapeType == PhysicsDirectGeometryData.SHAPE_CIRCLE) {
            if (!physicsService.computeCompiledFixtureCenterWU(
                    bodyEntityId, fixture, tmpCenter)) {
                return false;
            }
            float radius = physicsService.computeCompiledFixtureRadiusWU(fixture)
                    + Math.max(0f, toleranceWU);
            return tmpCenter.dst2(worldX, worldY) <= radius * radius;
        }

        int required = fixture.shapeType == PhysicsDirectGeometryData.SHAPE_BOX
                ? 8 : fixture.polygonVertexCount * 2;
        if (required <= 0 || required > vertexScratch.length) return false;
        int count = physicsService.computeCompiledFixtureVerticesWU(
                bodyEntityId, fixture, vertexScratch);
        if (count < 3) return false;
        if (Intersector.isPointInPolygon(
                vertexScratch, 0, count * 2, worldX, worldY)) {
            return true;
        }
        return isNearClosedPolyline(
                vertexScratch, count, worldX, worldY, toleranceWU);
    }

    private static boolean isNearClosedPolyline(
            float[] vertices,
            int vertexCount,
            float worldX,
            float worldY,
            float toleranceWU) {
        float tolerance = Math.max(0f, toleranceWU);
        float toleranceSquared = tolerance * tolerance;
        for (int i = 0; i < vertexCount; i++) {
            int next = (i + 1) % vertexCount;
            if (pointSegmentDistanceSquared(
                    worldX, worldY,
                    vertices[i * 2], vertices[i * 2 + 1],
                    vertices[next * 2], vertices[next * 2 + 1])
                    <= toleranceSquared) {
                return true;
            }
        }
        return false;
    }

    private static float pointSegmentDistanceSquared(
            float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax;
        float aby = by - ay;
        float lengthSquared = abx * abx + aby * aby;
        if (lengthSquared <= 1e-12f) {
            float dx = px - ax;
            float dy = py - ay;
            return dx * dx + dy * dy;
        }
        float t = ((px - ax) * abx + (py - ay) * aby) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        float dx = px - (ax + abx * t);
        float dy = py - (ay + aby * t);
        return dx * dx + dy * dy;
    }
}
