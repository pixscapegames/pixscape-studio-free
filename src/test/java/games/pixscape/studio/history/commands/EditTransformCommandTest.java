package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EditTransformCommandTest {

    @Test
    public void redoUndoRedo_appliesAfterThenBeforeThenAfterExactly() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = world.create();
        historyIds.ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.x = 1f;
        transform.y = 2f;
        transform.rotationRad = 3f;
        transform.scaleX = 4f;
        transform.scaleY = 5f;
        transform.originX = 6f;
        transform.originY = 7f;

        EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
        EditTransformCommand.Snapshot after = new EditTransformCommand.Snapshot(10f, 11f, 12f, -2f, -3f, 14f, 15f);

        EditTransformCommand command = new EditTransformCommand(world, historyIds, entityId, TransformOp.ORIGIN, before, after);

        command.redo();
        assertTransform(transform, 10f, 11f, 12f, -2f, -3f, 14f, 15f);

        command.undo();
        assertTransform(transform, 1f, 2f, 3f, 4f, 5f, 6f, 7f);

        command.redo();
        assertTransform(transform, 10f, 11f, 12f, -2f, -3f, 14f, 15f);
    }

    @Test
    public void noopWhenBeforeEqualsAfter_isReportedAndIgnoredByHistoryGuard() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = world.create();
        historyIds.ensureForEntity(entityId);
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);

        EditTransformCommand.Snapshot before = EditTransformCommand.Snapshot.capture(transform);
        EditTransformCommand.Snapshot after = EditTransformCommand.Snapshot.capture(transform);

        EditTransformCommand command = new EditTransformCommand(world, historyIds, entityId, TransformOp.MOVE, before, after);
        Assert.assertTrue(command.isNoop());

        HistoryManager history = new HistoryManager(8);
        executeIfMeaningful(history, command);

        Assert.assertFalse(history.canUndo());
    }

    @Test
    public void historyExecuteUndoRedo_keepsExactBeforeAfterSnapshots() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.x = -4f;
        transform.y = -5f;
        transform.rotationRad = 0.1f;
        transform.scaleX = 1.25f;
        transform.scaleY = -1.5f;
        transform.originX = 3f;
        transform.originY = 4f;

        EditTransformCommand command = new EditTransformCommand(
                world,
                history.historyIds(),
                entityId,
                TransformOp.SCALE,
                EditTransformCommand.Snapshot.capture(transform),
                new EditTransformCommand.Snapshot(-1f, -2f, 0.5f, -7f, 9f, -11f, 13f)
        );

        history.execute(command);
        assertTransform(transform, -1f, -2f, 0.5f, -7f, 9f, -11f, 13f);

        history.undo();
        assertTransform(transform, -4f, -5f, 0.1f, 1.25f, -1.5f, 3f, 4f);

        history.redo();
        assertTransform(transform, -1f, -2f, 0.5f, -7f, 9f, -11f, 13f);
    }

    @Test
    public void redo_publishesEntityChangedWithExactTransformOp() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = world.create();
        historyIds.ensureForEntity(entityId);
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);

        List<EventFlow.EntityChanged> events = new ArrayList<>();
        EventFlow.Listener<EventFlow.EntityChanged> listener = events::add;
        EventFlow.i().subscribe(EventFlow.EntityChanged.class, listener);
        EventFlow.i().flush();

        try {
            EditTransformCommand command = new EditTransformCommand(
                    world,
                    historyIds,
                    entityId,
                    TransformOp.ORIGIN,
                    EditTransformCommand.Snapshot.capture(transform),
                    new EditTransformCommand.Snapshot(0f, 0f, 0f, 1f, 1f, 8f, 9f)
            );

            command.redo();
            EventFlow.i().flush();

            Assert.assertFalse(events.isEmpty());
            EventFlow.EntityChanged last = events.get(events.size() - 1);
            Assert.assertEquals(entityId, last.entityId());
            Assert.assertEquals(TransformOp.ORIGIN, last.op());
        } finally {
            EventFlow.i().unsubscribe(EventFlow.EntityChanged.class, listener);
        }
    }

    @Test
    public void rotatingRepeatableSpriteAwayFromZero_clearsRepeatAndUndoRestoresIt() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.rotationRad = 0f;
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entityId);
        repeat.repeatX = true;
        repeat.repeatY = true;

        history.execute(new EditTransformCommand(
                world,
                history.historyIds(),
                entityId,
                TransformOp.ROTATE,
                EditTransformCommand.Snapshot.capture(transform),
                EditTransformCommand.Snapshot.capture(transform).withRotationRad(0.25f)
        ));

        Assert.assertEquals(0.25f, transform.rotationRad, 0.0001f);
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entityId));

        history.undo();
        Assert.assertEquals(0f, transform.rotationRad, 0.0001f);
        repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertTrue(repeat.repeatX);
        Assert.assertTrue(repeat.repeatY);
    }

    @Test
    public void rotatingBackToZero_doesNotAutomaticallyRestoreRepeat() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.rotationRad = 0f;
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entityId);
        repeat.repeatX = true;

        history.execute(new EditTransformCommand(
                world,
                history.historyIds(),
                entityId,
                TransformOp.ROTATE,
                EditTransformCommand.Snapshot.capture(transform),
                EditTransformCommand.Snapshot.capture(transform).withRotationRad(0.5f)
        ));
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entityId));

        history.execute(new EditTransformCommand(
                world,
                history.historyIds(),
                entityId,
                TransformOp.ROTATE,
                EditTransformCommand.Snapshot.capture(transform),
                EditTransformCommand.Snapshot.capture(transform).withRotationRad(0f)
        ));

        Assert.assertEquals(0f, transform.rotationRad, 0.0001f);
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entityId));
    }

    @Test
    public void gizmoRotatingRepeatableSprite_clearsRepeat() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = world.create();
        long historyId = history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).create(entityId);
        repeat.repeatY = true;

        GizmoTransformCommand command = new GizmoTransformCommand(world, history.historyIds(), TransformOp.ROTATE);
        command.addEntry(
                historyId,
                GizmoTransformCommand.Snapshot.of(transform),
                new GizmoTransformCommand.Snapshot(0f, 0f, 0.35f, 1f, 1f, 0f, 0f)
        );

        history.execute(command);
        Assert.assertEquals(0.35f, transform.rotationRad, 0.0001f);
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entityId));

        history.undo();
        Assert.assertEquals(0f, transform.rotationRad, 0.0001f);
        repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertFalse(repeat.repeatX);
        Assert.assertTrue(repeat.repeatY);
    }

    private static void executeIfMeaningful(HistoryManager history, Command command) {
        if (command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) return;
        history.execute(command);
    }

    private static void assertTransform(TransformComponent t,
                                        float x,
                                        float y,
                                        float rotationRad,
                                        float scaleX,
                                        float scaleY,
                                        float originX,
                                        float originY) {
        Assert.assertEquals(x, t.x, 0.0001f);
        Assert.assertEquals(y, t.y, 0.0001f);
        Assert.assertEquals(rotationRad, t.rotationRad, 0.0001f);
        Assert.assertEquals(scaleX, t.scaleX, 0.0001f);
        Assert.assertEquals(scaleY, t.scaleY, 0.0001f);
        Assert.assertEquals(originX, t.originX, 0.0001f);
        Assert.assertEquals(originY, t.originY, 0.0001f);
    }
}
