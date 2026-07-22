package games.pixscape.studio.service.physics;

import com.artemis.World;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import games.pixscape.studio.FixtureIdentityTestSupport;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
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
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "authored polygon fixture is missing");
    }

    @Test
    public void rejectsFixtureClaimedBySpatialBlockAndAuthoredPolygon() {
        World world = FixtureIdentityTestSupport.newWorld();
        int body = world.create();
        FixtureDefData fixture = FixtureIdentityTestSupport.createFixture(world);
        world.getMapper(PhysicsFixturesComponent.class).create(body).fixtures.add(fixture);

        SpatialBlockData block = new SpatialBlockData();
        block.id = 3;
        block.physicsCollision = true;
        block.fixtureId = fixture.fixtureId;
        world.getMapper(SpatialBlocksComponent.class).create(body).blocks.add(block);

        AuthoredPolygonData polygon = new AuthoredPolygonData();
        polygon.authoringId = 8L;
        polygon.generatedFixtureIds = new int[]{fixture.fixtureId};
        world.getMapper(PhysicsAuthoringComponent.class).create(body).polygons.add(polygon);

        assertInvalid(world, "claimed by multiple");
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
