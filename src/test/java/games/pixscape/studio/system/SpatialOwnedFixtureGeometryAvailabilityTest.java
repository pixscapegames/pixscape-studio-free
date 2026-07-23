package games.pixscape.studio.system;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.spatial.SpatialBlockData;
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
        world.getMapper(SpatialBlocksComponent.class)
                .create(bodyEntityId)
                .blocks
                .add(block);

        assertFalse(PickingSystem.isFixtureGeometryEditable(
                world,
                bodyEntityId,
                SpatialOwnedFixtureSupport.fixtureIdForBlock(blockId)
        ));
        assertTrue(PickingSystem.isFixtureGeometryEditable(world, bodyEntityId, 77L));

        block.physicsCollision = false;
        assertTrue(PickingSystem.isFixtureGeometryEditable(
                world,
                bodyEntityId,
                SpatialOwnedFixtureSupport.fixtureIdForBlock(blockId)
        ));
    }
}
