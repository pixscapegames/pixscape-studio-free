package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class GameObjectTransformInvariantTest {
    @Test
    public void editCommandRejectsInvalidScaleAndPreservesRealOrigin() {
        World world = new World();
        try {
            int root = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(root);
            transform.scaleX = 1f;
            transform.scaleY = 1f;
            world.getMapper(GameObjectComponent.class).create(root);
            HistoryIdRegistry ids = new HistoryIdRegistry();
            EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
            assertNoop(new EditTransformCommand(
                    world, ids, root, TransformOp.SCALE, before,
                    before.withScaleX(2f)));
            assertNoop(new EditTransformCommand(
                    world, ids, root, TransformOp.SCALE, before,
                    before.withUniformScale(0f)));
            assertNoop(new EditTransformCommand(
                    world, ids, root, TransformOp.SCALE, before,
                    before.withUniformScale(-1f)));
            new EditTransformCommand(
                    world, ids, root, TransformOp.SCALE, before,
                    before.withUniformScale(2f));
            new EditTransformCommand(
                    world, ids, root, TransformOp.MOVE, before,
                    before.withOriginX(1f));
        } finally {
            world.dispose();
        }
    }

    private static void assertNoop(EditTransformCommand command) {
        assertTrue(command.isNoop());
    }
}
