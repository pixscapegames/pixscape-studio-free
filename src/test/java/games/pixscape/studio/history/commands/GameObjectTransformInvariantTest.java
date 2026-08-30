package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;

import static org.junit.Assert.fail;

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
            expectRejected(() -> new EditTransformCommand(
                    world, ids, root, TransformOp.SCALE, before,
                    before.withScaleX(2f)));
            expectRejected(() -> new EditTransformCommand(
                    world, ids, root, TransformOp.SCALE, before,
                    before.withUniformScale(0f)));
            expectRejected(() -> new EditTransformCommand(
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

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            fail("Expected invalid Game Object transform rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
