package games.pixscape.studio.service.entitygraph;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntityGraphGameObjectBoundaryTest {
    @Test
    public void genericCaptureDelegatesGameObjectRootsToTheHierarchyAwarePath() {
        World world = new World();
        try {
            int ordinary = authored(world, 1);
            int root = authored(world, 2);
            world.getMapper(GameObjectComponent.class).create(root);
            int child = authored(world, 3);
            world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 2;
            world.process();
            EntityGraphCaptureService capture = new EntityGraphCaptureService(world);

            assertEquals(3, capture.capture(new IntArray(new int[]{ordinary, root})).size());
            try {
                capture.capture(new IntArray(new int[]{child}));
                throw new AssertionError("Expected member-only V1 rejection.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("member alone"));
            }
        } finally {
            world.dispose();
        }
    }

    private static int authored(World world, int stableId) {
        int entity = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(TransformComponent.class).create(entity);
        return entity;
    }
}
