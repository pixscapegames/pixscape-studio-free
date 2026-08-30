package games.pixscape.studio.service.entitygraph;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class EntityGraphGameObjectBoundaryTest {
    @Test
    public void anyRootOrMemberRejectsTheWholeGenericClipboardCapture() {
        World world = new World();
        try {
            int ordinary = authored(world, 1);
            int root = authored(world, 2);
            world.getMapper(GameObjectComponent.class).create(root);
            int child = authored(world, 3);
            world.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 2;
            world.process();
            EntityGraphCaptureService capture = new EntityGraphCaptureService(world);

            assertTrue(capture.capture(new IntArray(new int[]{ordinary, root})).isEmpty());
            assertTrue(capture.capture(new IntArray(new int[]{child})).isEmpty());
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
