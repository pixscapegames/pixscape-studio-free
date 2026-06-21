package games.pixscape.studio.service.physics;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.FixtureIdSequence;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.service.PhysicsService;

public final class PhysicsFixturePickingService {

    private final PhysicsService physicsService;

    private final Vector2 tmpA = new Vector2();

    public PhysicsFixturePickingService(PhysicsService physicsService) {
        if (physicsService == null) {
            throw new IllegalArgumentException("physicsService cannot be null.");
        }
        this.physicsService = physicsService;
    }

    public int pickFixtureId(int bodyEid, float worldX, float worldY, float toleranceWU) {
        if (!physicsService.hasPhysics(bodyEid)) {
            return -1;
        }

        PhysicsFixturesComponent fixtures = physicsService.getFixturesComponent(bodyEid);
        if (fixtures == null || fixtures.fixtures == null || fixtures.fixtures.size == 0) {
            return -1;
        }

        float[] verts = new float[32];

        for (int i = fixtures.fixtures.size - 1; i >= 0; i--) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null) continue;

            FixtureIdSequence.i().ensure(fixture);

            if (hitTestFixture(bodyEid, fixture, worldX, worldY, toleranceWU, verts)) {
                return fixture.fixtureId;
            }
        }

        return -1;
    }

    private boolean hitTestFixture(
            int bodyEid,
            FixtureDefData fixture,
            float worldX,
            float worldY,
            float toleranceWU,
            float[] scratchVerts
    ) {
        if (fixture == null) return false;

        if (fixture.shapeType == FixtureDefData.SHAPE_CIRCLE) {
            if (!physicsService.computeFixtureCenterWU(bodyEid, fixture, tmpA)) {
                return false;
            }

            float r = physicsService.computeFixtureRadiusWU(fixture) + Math.max(0f, toleranceWU);
            return tmpA.dst2(worldX, worldY) <= r * r;
        }

        int neededFloats = fixture.shapeType == FixtureDefData.SHAPE_BOX
                ? 8
                : safePolyCount(fixture) * 2;

        if (neededFloats <= 0) return false;

        float[] verts = scratchVerts;
        if (verts == null || verts.length < neededFloats) {
            verts = new float[neededFloats];
        }

        int vertexCount = physicsService.computeFixtureVerticesWU(bodyEid, fixture, verts);
        if (vertexCount < 3) return false;

        int floatCount = vertexCount * 2;

        if (Intersector.isPointInPolygon(verts, 0, floatCount, worldX, worldY)) {
            return true;
        }

        return isNearClosedPolyline(verts, vertexCount, worldX, worldY, toleranceWU);
    }

    private static boolean isNearClosedPolyline(
            float[] verts,
            int vertexCount,
            float worldX,
            float worldY,
            float toleranceWU
    ) {
        if (verts == null || vertexCount < 2) return false;

        float tol = Math.max(0f, toleranceWU);
        float tol2 = tol * tol;

        for (int i = 0; i < vertexCount; i++) {
            int j = (i + 1) % vertexCount;

            float ax = verts[i * 2];
            float ay = verts[i * 2 + 1];
            float bx = verts[j * 2];
            float by = verts[j * 2 + 1];

            if (pointSegmentDst2(worldX, worldY, ax, ay, bx, by) <= tol2) {
                return true;
            }
        }

        return false;
    }

    private static float pointSegmentDst2(
            float px,
            float py,
            float ax,
            float ay,
            float bx,
            float by
    ) {
        float abx = bx - ax;
        float aby = by - ay;
        float apx = px - ax;
        float apy = py - ay;

        float abLen2 = abx * abx + aby * aby;
        if (abLen2 <= 1e-12f) {
            float dx = px - ax;
            float dy = py - ay;
            return dx * dx + dy * dy;
        }

        float t = (apx * abx + apy * aby) / abLen2;
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;

        float cx = ax + abx * t;
        float cy = ay + aby * t;

        float dx = px - cx;
        float dy = py - cy;
        return dx * dx + dy * dy;
    }

    private static int safePolyCount(FixtureDefData fixture) {
        if (fixture == null || fixture.polyVerts == null) return 0;
        return Math.max(0, Math.min(fixture.polyCount, fixture.polyVerts.length / 2));
    }
}
