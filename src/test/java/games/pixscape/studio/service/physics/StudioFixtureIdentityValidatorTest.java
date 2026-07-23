package games.pixscape.studio.service.physics;

import com.artemis.World;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import games.pixscape.studio.FixtureIdentityTestSupport;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import org.junit.Assert;
import org.junit.Test;

public class StudioFixtureIdentityValidatorTest {
    @Test
    public void rejectsMissingAuthoredFixtureReference() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        AuthoredPolygonData polygon = new AuthoredPolygonData();
        polygon.authoringId = 9L;
        polygon.generatedFixtureIds = new int[]{4};
        polygon.convexParts.add(part(0f));
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "authored polygon fixture is missing");
    }

    @Test
    public void rejectsFixtureClaimedBySpatialBlockAndAuthoredPolygon() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        FixtureDefData fixture = polygonFixture(world, 0f);
        world.getMapper(PhysicsFixturesComponent.class).create(body).fixtures.add(fixture);

        SpatialBlockData block = new SpatialBlockData();
        block.id = 3;
        block.physicsCollision = true;
        block.fixtureId = fixture.fixtureId;
        world.getMapper(SpatialBlocksComponent.class).create(body).blocks.add(block);

        AuthoredPolygonData polygon = polygonFor(fixture, 8L);
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "claimed by multiple");
    }

    @Test
    public void rejectsGeneratedFixtureCardinalityMismatch() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        FixtureDefData fixture = polygonFixture(world, 0f);
        world.getMapper(PhysicsFixturesComponent.class).create(body).fixtures.add(fixture);
        AuthoredPolygonData polygon = polygonFor(fixture, 10L);
        polygon.convexParts.add(part(2f));
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "cardinality mismatch");
    }

    @Test
    public void rejectsExcessGeneratedFixtureIds() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        FixtureDefData first = polygonFixture(world, 0f);
        FixtureDefData second = polygonFixture(world, 2f);
        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(body);
        fixtures.fixtures.add(first);
        fixtures.fixtures.add(second);
        AuthoredPolygonData polygon = polygonFor(first, 13L);
        polygon.generatedFixtureIds = new int[]{first.fixtureId, second.fixtureId};
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "cardinality mismatch");
    }

    @Test
    public void rejectsDuplicateGeneratedFixtureInsidePolygon() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        FixtureDefData fixture = polygonFixture(world, 0f);
        world.getMapper(PhysicsFixturesComponent.class).create(body).fixtures.add(fixture);
        AuthoredPolygonData polygon = polygonFor(fixture, 11L);
        polygon.convexParts.add(part(0f));
        polygon.generatedFixtureIds = new int[]{fixture.fixtureId, fixture.fixtureId};
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "duplicated inside the polygon");
    }

    @Test
    public void rejectsFixtureGeometryThatDoesNotMatchSamePartIndex() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        FixtureDefData fixture = polygonFixture(world, 0f);
        world.getMapper(PhysicsFixturesComponent.class).create(body).fixtures.add(fixture);
        AuthoredPolygonData polygon = polygonFor(fixture, 12L);
        polygon.convexParts.get(0).verts[0] = 9f;
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "geometry does not match");
    }

    @Test
    public void rejectsFixtureIdsInWrongConvexPartOrder() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        FixtureDefData first = polygonFixture(world, 0f);
        FixtureDefData second = polygonFixture(world, 2f);
        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(body);
        fixtures.fixtures.add(first);
        fixtures.fixtures.add(second);
        AuthoredPolygonData polygon = polygonFor(first, 14L);
        polygon.convexParts.add(part(2f));
        polygon.generatedFixtureIds = new int[]{second.fixtureId, first.fixtureId};
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "same index");
    }

    private static AuthoredPolygonData polygonFor(FixtureDefData fixture, long authoringId) {
        AuthoredPolygonData polygon = new AuthoredPolygonData();
        polygon.authoringId = authoringId;
        polygon.generatedFixtureIds = new int[]{fixture.fixtureId};
        polygon.convexParts.add(part(0f));
        return polygon;
    }

    private static FixtureDefData polygonFixture(World world, float x) {
        FixtureDefData fixture = FixtureIdentityTestSupport.createFixture(world);
        fixture.shapeType = FixtureDefData.SHAPE_POLYGON;
        fixture.polyCount = 3;
        fixture.polyVerts = new float[]{x, 0f, x + 1f, 0f, x, 1f};
        return fixture;
    }

    private static ConvexPolygonPartData part(float x) {
        ConvexPolygonPartData part = new ConvexPolygonPartData();
        part.count = 3;
        part.verts = new float[]{x, 0f, x + 1f, 0f, x, 1f};
        return part;
    }

    private static void assertInvalid(World world, String message) {
        FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
        try {
            StudioFixtureIdentityValidator.validate(world, allocator.sceneMeta(), "test-scene");
            Assert.fail("Expected Studio fixture validation to fail");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }
}
