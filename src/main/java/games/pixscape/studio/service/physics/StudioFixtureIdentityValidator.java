package games.pixscape.studio.service.physics;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.loading.FixtureIdentityValidator;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;

/** Validates fixture ownership references that only exist in Studio authoring state. */
public final class StudioFixtureIdentityValidator {
    private static final float GEOMETRY_EPSILON = 1e-6f;

    private StudioFixtureIdentityValidator() {
    }

    public static void validate(World world, SceneMetaRuntime meta, String sceneLabel) {
        FixtureIdentityValidator.validate(world, meta, sceneLabel);
        String scene = sceneLabel != null ? sceneLabel
                : meta != null && meta.name != null ? meta.name : "<unnamed>";

        IntIntMap fixtureBodies = collectFixtureBodies(world);
        IntSet claims = collectSpatialClaims(world);
        ComponentMapper<PhysicsAuthoringComponent> mAuthoring =
                world.getMapper(PhysicsAuthoringComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsAuthoringComponent.class)).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int body = data[i];
            PhysicsAuthoringComponent authoring = mAuthoring.get(body);
            if (authoring == null || authoring.polygons == null) continue;
            for (int polygonIndex = 0; polygonIndex < authoring.polygons.size; polygonIndex++) {
                AuthoredPolygonData polygon = authoring.polygons.get(polygonIndex);
                if (polygon == null) continue;
                int partCount = polygon.convexParts != null ? polygon.convexParts.size : 0;
                int idCount = polygon.generatedFixtureIds != null
                        ? polygon.generatedFixtureIds.length : 0;
                if (partCount != idCount) {
                    fail(scene, body, 0, polygon, polygonIndex, -1,
                            "convexParts/generatedFixtureIds cardinality mismatch; parts="
                                    + partCount + ", ids=" + idCount);
                }

                IntSet polygonClaims = new IntSet();
                for (int partIndex = 0; partIndex < idCount; partIndex++) {
                    int fixtureId = polygon.generatedFixtureIds[partIndex];
                    if (fixtureId <= 0) {
                        fail(scene, body, fixtureId, polygon, polygonIndex, partIndex,
                                "generated fixture reference must be strictly positive");
                    }
                    if (!polygonClaims.add(fixtureId)) {
                        fail(scene, body, fixtureId, polygon, polygonIndex, partIndex,
                                "generated fixture reference is duplicated inside the polygon");
                    }
                    if (fixtureBodies.get(fixtureId, -1) != body) {
                        fail(scene, body, fixtureId, polygon, polygonIndex, partIndex,
                                "authored polygon fixture is missing from its body");
                    }
                    FixtureDefData fixture = findFixture(world, body, fixtureId);
                    ConvexPolygonPartData part = polygon.convexParts.get(partIndex);
                    String mismatch = geometryMismatch(part, fixture);
                    if (mismatch != null) {
                        fail(scene, body, fixtureId, polygon, polygonIndex, partIndex,
                                "fixture geometry does not match convex part at the same index: "
                                        + mismatch);
                    }
                    if (!claims.add(fixtureId)) {
                        fail(scene, body, fixtureId, polygon, polygonIndex, partIndex,
                                "fixture is claimed by multiple authored associations");
                    }
                }
            }
        }
    }

    private static FixtureDefData findFixture(World world, int body, int fixtureId) {
        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class)
                .getSafe(body, null);
        if (fixtures == null || fixtures.fixtures == null) return null;
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) return fixture;
        }
        return null;
    }

    private static String geometryMismatch(ConvexPolygonPartData part, FixtureDefData fixture) {
        if (part == null) return "convex part is null";
        if (fixture == null) return "fixture is null";
        if (fixture.shapeType != FixtureDefData.SHAPE_POLYGON) {
            return "fixture shapeType=" + fixture.shapeType + " is not polygon";
        }
        if (part.count != fixture.polyCount) {
            return "vertexCount part=" + part.count + ", fixture=" + fixture.polyCount;
        }
        int length = Math.max(0, part.count) * 2;
        if (part.verts == null || part.verts.length < length) {
            return "convex part vertex array is incomplete";
        }
        if (fixture.polyVerts == null || fixture.polyVerts.length < length) {
            return "fixture vertex array is incomplete";
        }
        for (int i = 0; i < length; i++) {
            if (Math.abs(part.verts[i] - fixture.polyVerts[i]) > GEOMETRY_EPSILON) {
                return "coordinate=" + i + ", part=" + part.verts[i]
                        + ", fixture=" + fixture.polyVerts[i];
            }
        }
        return null;
    }

    private static IntIntMap collectFixtureBodies(World world) {
        IntIntMap fixtureBodies = new IntIntMap();
        ComponentMapper<PhysicsFixturesComponent> mapper =
                world.getMapper(PhysicsFixturesComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsFixturesComponent.class)).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int body = data[i];
            PhysicsFixturesComponent fixtures = mapper.get(body);
            if (fixtures == null || fixtures.fixtures == null) continue;
            for (FixtureDefData fixture : fixtures.fixtures) {
                if (fixture != null) fixtureBodies.put(fixture.fixtureId, body);
            }
        }
        return fixtureBodies;
    }

    private static IntSet collectSpatialClaims(World world) {
        IntSet claims = new IntSet();
        ComponentMapper<SpatialBlocksComponent> mapper = world.getMapper(SpatialBlocksComponent.class);
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class)).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            SpatialBlocksComponent blocks = mapper.get(data[i]);
            if (blocks == null || blocks.blocks == null) continue;
            for (SpatialBlockData block : blocks.blocks) {
                if (block != null && block.physicsCollision) claims.add(block.fixtureId);
            }
        }
        return claims;
    }

    private static void fail(String scene, int body, int fixtureId, String reason) {
        throw new IllegalStateException(
                "Invalid Studio fixture identity state: scene=" + scene + ", body=" + body
                        + ", fixtureId=" + fixtureId + ", reason=" + reason);
    }

    private static void fail(String scene, int body, int fixtureId,
                             AuthoredPolygonData polygon, int polygonIndex,
                             int partIndex, String reason) {
        fail(scene, body, fixtureId,
                "authoringId=" + polygon.authoringId + ", polygonIndex=" + polygonIndex
                        + ", partIndex=" + partIndex + ", " + reason);
    }
}
