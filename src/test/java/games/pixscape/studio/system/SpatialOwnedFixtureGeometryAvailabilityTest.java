package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.studio.service.physics.SpatialOwnedFixtureSupport;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpatialOwnedFixtureGeometryAvailabilityTest {

    @Test
    public void ownedFixtureHasNoCanvasGeometryEditingButCustomFixtureDoes() {
        World world = new World(new WorldConfiguration());
        int bodyEntityId = world.create();
        int blockId = 12;
        SpatialBlockData block = new SpatialBlockData();
        block.id = blockId;
        block.physicsCollision = true;
        block.fixtureId = 42;
        world.getMapper(SpatialBlocksComponent.class)
                .create(bodyEntityId)
                .blocks
                .add(block);

        assertFalse(PickingSystem.isFixtureGeometryEditable(
                world,
                bodyEntityId,
                block.fixtureId
        ));
        assertTrue(PickingSystem.isFixtureGeometryEditable(world, bodyEntityId, 77L));

        block.physicsCollision = false;
        assertTrue(PickingSystem.isFixtureGeometryEditable(
                world,
                bodyEntityId,
                block.fixtureId
        ));
    }
}
