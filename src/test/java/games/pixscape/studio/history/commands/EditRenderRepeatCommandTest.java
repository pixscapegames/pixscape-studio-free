package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EditRenderRepeatCommandTest {
    @Test
    public void staleActivationRejectsBeforeChangingRotationOrHistory() {
        World world = new World();
        try {
            int entity = sprite(world);
            TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
            transform.rotationRad = .8f;
            HistoryManager history = new HistoryManager(8);
            EditRenderRepeatCommand command = new EditRenderRepeatCommand(world, history.historyIds(), entity,
                    EditRenderRepeatCommand.Snapshot.disabled(), new EditRenderRepeatCommand.Snapshot(true, false));
            world.getMapper(games.pixscape.runtime.component.QuadDeformComponent.class).create(entity).trX = 2;
            history.execute(command);
            Assert.assertFalse(history.canUndo());
            Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entity));
            Assert.assertEquals(.8f, transform.rotationRad, 0);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void animationAndTiledRolesCannotActivateRepeat() {
        World world = new World();
        try {
            int entity = sprite(world);
            world.getMapper(games.pixscape.runtime.component.AnimationComponent.class).create(entity);
            HistoryManager history = new HistoryManager(8);
            history.execute(new EditRenderRepeatCommand(world, history.historyIds(), entity,
                    EditRenderRepeatCommand.Snapshot.disabled(), new EditRenderRepeatCommand.Snapshot(true, true)));
            Assert.assertFalse(history.canUndo());
            Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entity));
            world.getMapper(games.pixscape.runtime.component.AnimationComponent.class).remove(entity);
            world.getMapper(games.pixscape.runtime.component.TiledMapComponent.class).create(entity);
            Assert.assertNotNull(EditRenderRepeatCommand.activationRejection(world, entity));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void editRenderRepeat_addsRemovesAndRestoresComponent() {
        World world = new World(new WorldConfiguration().setSystem(new DirtyTrackerSystem(128)));
        HistoryManager history = new HistoryManager(8);
        int entityId = sprite(world);
        history.historyIds().ensureForEntity(entityId);

        EditRenderRepeatCommand enableX = new EditRenderRepeatCommand(
                world,
                history.historyIds(),
                entityId,
                EditRenderRepeatCommand.Snapshot.disabled(),
                new EditRenderRepeatCommand.Snapshot(true, false)
        );

        history.execute(enableX);
        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertTrue(repeat.repeatX);
        Assert.assertFalse(repeat.repeatY);
        Assert.assertTrue(world.getSystem(DirtyTrackerSystem.class).materialEntities().contains(entityId));

        history.undo();
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entityId));

        history.redo();
        repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertTrue(repeat.repeatX);
        Assert.assertFalse(repeat.repeatY);

        EditRenderRepeatCommand enableBoth = new EditRenderRepeatCommand(
                world,
                history.historyIds(),
                entityId,
                EditRenderRepeatCommand.Snapshot.capture(repeat),
                new EditRenderRepeatCommand.Snapshot(true, true)
        );
        history.execute(enableBoth);
        repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertTrue(repeat.repeatX);
        Assert.assertTrue(repeat.repeatY);

        EditRenderRepeatCommand disableBoth = new EditRenderRepeatCommand(
                world,
                history.historyIds(),
                entityId,
                EditRenderRepeatCommand.Snapshot.capture(repeat),
                EditRenderRepeatCommand.Snapshot.disabled()
        );
        history.execute(disableBoth);
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entityId));

        history.undo();
        repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertTrue(repeat.repeatX);
        Assert.assertTrue(repeat.repeatY);
    }

    @Test
    public void editRenderRepeat_marksPreviewRequiredOnRedoAndUndo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int entityId = sprite(world);
        history.historyIds().ensureForEntity(entityId);
        int[] calls = {0};

        EditRenderRepeatCommand command = new EditRenderRepeatCommand(
                world,
                history.historyIds(),
                entityId,
                EditRenderRepeatCommand.Snapshot.disabled(),
                new EditRenderRepeatCommand.Snapshot(false, true),
                () -> calls[0]++
        );

        history.execute(command);
        history.undo();
        history.redo();

        Assert.assertEquals(3, calls[0]);
    }

    @Test
    public void enablingRepeatXOnRotatedSprite_resetsRotationToZero() {
        World world = new World(new WorldConfiguration().setSystem(new DirtyTrackerSystem(128)));
        HistoryManager history = new HistoryManager(8);
        int entityId = sprite(world);
        history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.rotationRad = 0.75f;

        history.execute(new EditRenderRepeatCommand(
                world,
                history.historyIds(),
                entityId,
                EditRenderRepeatCommand.Snapshot.disabled(),
                new EditRenderRepeatCommand.Snapshot(true, false)
        ));

        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertTrue(repeat.repeatX);
        Assert.assertFalse(repeat.repeatY);
        Assert.assertEquals(0f, transform.rotationRad, 0.0001f);

        history.undo();
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entityId));
        Assert.assertEquals(0.75f, transform.rotationRad, 0.0001f);
    }

    @Test
    public void enablingRepeatYOnRotatedSprite_resetsRotationToZero() {
        World world = new World(new WorldConfiguration().setSystem(new DirtyTrackerSystem(128)));
        HistoryManager history = new HistoryManager(8);
        int entityId = sprite(world);
        history.historyIds().ensureForEntity(entityId);

        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.rotationRad = -0.5f;

        history.execute(new EditRenderRepeatCommand(
                world,
                history.historyIds(),
                entityId,
                EditRenderRepeatCommand.Snapshot.disabled(),
                new EditRenderRepeatCommand.Snapshot(false, true)
        ));

        RenderRepeatComponent repeat = world.getMapper(RenderRepeatComponent.class).get(entityId);
        Assert.assertFalse(repeat.repeatX);
        Assert.assertTrue(repeat.repeatY);
        Assert.assertEquals(0f, transform.rotationRad, 0.0001f);
    }
    private static int sprite(World world) {
        int entity = world.create();
        world.getMapper(games.pixscape.studio.component.EntityMetaComponent.class).create(entity).kind =
                games.pixscape.studio.model.EntityKind.SPRITE;
        world.getMapper(TransformComponent.class).create(entity);
        world.getMapper(games.pixscape.runtime.component.TextureRegionComponent.class).create(entity);
        world.getMapper(games.pixscape.runtime.component.RenderMaterialComponent.class).create(entity);
        return entity;
    }

}
