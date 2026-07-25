package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class EditPhysicsBodyCommandTest {
    @Before
    public void activateSceneAllocator() {
        games.pixscape.studio.configuration.ProjectConfig config =
                new games.pixscape.studio.configuration.ProjectConfig();
        config.createSceneMeta("Main");
        games.pixscape.studio.configuration.ProjectConfig.setInstance(config);
    }

    @Test
    public void editGravityScaleUndoRedoRestoresExactValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = createBody(world, historyIds);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(entityId);
        body.gravityScale = 1f;

        EditPhysicsBodyCommand.Snapshot before = EditPhysicsBodyCommand.Snapshot.capture(body);
        EditPhysicsBodyCommand.Snapshot after = before.withGravityScale(2.5f);

        EditPhysicsBodyCommand command = new EditPhysicsBodyCommand(world, historyIds, entityId, before, after);
        command.redo();
        Assert.assertEquals(2.5f, body.gravityScale, 0f);

        command.undo();
        Assert.assertEquals(1f, body.gravityScale, 0f);

        command.redo();
        Assert.assertEquals(2.5f, body.gravityScale, 0f);
    }

    @Test
    public void editDampingUndoRedoRestoresBothDampings() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = createBody(world, historyIds);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(entityId);
        body.linearDamping = 0.1f;
        body.angularDamping = 0.2f;

        EditPhysicsBodyCommand.Snapshot before = EditPhysicsBodyCommand.Snapshot.capture(body);
        EditPhysicsBodyCommand.Snapshot after = before
                .withLinearDamping(3.25f)
                .withAngularDamping(4.75f);

        EditPhysicsBodyCommand command = new EditPhysicsBodyCommand(world, historyIds, entityId, before, after);

        command.redo();
        assertBodyDamping(body, 3.25f, 4.75f);

        command.undo();
        assertBodyDamping(body, 0.1f, 0.2f);

        command.redo();
        assertBodyDamping(body, 3.25f, 4.75f);
    }

    @Test
    public void toggleFlagsUndoRedoRestoresExactBooleanValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = createBody(world, historyIds);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(entityId);
        body.fixedRotation = false;
        body.bullet = false;
        body.allowSleep = true;

        EditPhysicsBodyCommand.Snapshot before = EditPhysicsBodyCommand.Snapshot.capture(body);
        EditPhysicsBodyCommand.Snapshot after = before
                .withFixedRotation(true)
                .withBullet(true)
                .withAllowSleep(false);

        EditPhysicsBodyCommand command = new EditPhysicsBodyCommand(world, historyIds, entityId, before, after);

        command.redo();
        assertBodyFlags(body, true, true, false);

        command.undo();
        assertBodyFlags(body, false, false, true);

        command.redo();
        assertBodyFlags(body, true, true, false);
    }

    @Test
    public void editBodyTypeUndoRedoRestoresExactType() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = createBody(world, historyIds);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(entityId);
        body.type = PhysicsBodyComponent.DYNAMIC;

        EditPhysicsBodyCommand.Snapshot before = EditPhysicsBodyCommand.Snapshot.capture(body);
        EditPhysicsBodyCommand.Snapshot after = before.withType(PhysicsBodyComponent.KINEMATIC);

        EditPhysicsBodyCommand command = new EditPhysicsBodyCommand(world, historyIds, entityId, before, after);

        command.redo();
        Assert.assertEquals(PhysicsBodyComponent.KINEMATIC, body.type);

        command.undo();
        Assert.assertEquals(PhysicsBodyComponent.DYNAMIC, body.type);

        command.redo();
        Assert.assertEquals(PhysicsBodyComponent.KINEMATIC, body.type);
    }

    @Test
    public void noopEditDoesNotCreateMeaningfulHistoryMutation() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        int entityId = createBody(world, historyIds);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(entityId);
        EditPhysicsBodyCommand.Snapshot before = EditPhysicsBodyCommand.Snapshot.capture(body);
        EditPhysicsBodyCommand.Snapshot after = EditPhysicsBodyCommand.Snapshot.capture(body);

        EditPhysicsBodyCommand command = new EditPhysicsBodyCommand(world, historyIds, entityId, before, after);
        Assert.assertTrue(command.isNoop());

        HistoryManager history = new HistoryManager(32);
        executeIfMeaningful(history, command);

        Assert.assertFalse(history.canUndo());
        Assert.assertEquals(0, history.getCursor());
    }

    @Test
    public void mixedChainEnableThenEditUndoRedoKeepsExactEcsValues() {
        World world = new World(new WorldConfiguration());
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        HistoryManager history = new HistoryManager(64);

        int entityId = world.create();
        historyIds.ensureForEntity(entityId);
        world.getMapper(TransformComponent.class).create(entityId);

        AddPhysicsBodyCommand enable = new AddPhysicsBodyCommand(
                world,
                historyIds,
                new games.pixscape.runtime.service.PhysicsService(
                        world, null,
                        games.pixscape.studio.configuration.ProjectConfig.getInstance()
                                .getCurrentSceneMeta()),
                entityId,
                PhysicsBodyComponent.DYNAMIC,
                true
        );
        history.execute(enable);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(entityId);
        EditPhysicsBodyCommand.Snapshot baseline = EditPhysicsBodyCommand.Snapshot.capture(body);

        EditPhysicsBodyCommand editA = new EditPhysicsBodyCommand(
                world,
                historyIds,
                entityId,
                baseline,
                baseline.withGravityScale(3f).withLinearDamping(0.6f)
        );

        history.execute(editA);
        assertBodyState(body, PhysicsBodyComponent.DYNAMIC, 3f, 0.6f, 0f, false, false, true);

        EditPhysicsBodyCommand.Snapshot afterA = EditPhysicsBodyCommand.Snapshot.capture(body);
        EditPhysicsBodyCommand editB = new EditPhysicsBodyCommand(
                world,
                historyIds,
                entityId,
                afterA,
                afterA
                        .withType(PhysicsBodyComponent.STATIC)
                        .withAngularDamping(0.9f)
                        .withFixedRotation(true)
                        .withBullet(true)
                        .withAllowSleep(false)
        );

        history.execute(editB);
        assertBodyState(body, PhysicsBodyComponent.STATIC, 3f, 0.6f, 0.9f, true, true, false);

        history.undo();
        assertBodyState(body, PhysicsBodyComponent.DYNAMIC, 3f, 0.6f, 0f, false, false, true);

        history.undo();
        assertBodyState(body, PhysicsBodyComponent.DYNAMIC, 1f, 0f, 0f, false, false, true);

        history.undo();
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(entityId));

        history.redo();
        body = world.getMapper(PhysicsBodyComponent.class).get(entityId);
        assertBodyState(body, PhysicsBodyComponent.DYNAMIC, 1f, 0f, 0f, false, false, true);

        history.redo();
        assertBodyState(body, PhysicsBodyComponent.DYNAMIC, 3f, 0.6f, 0f, false, false, true);

        history.redo();
        assertBodyState(body, PhysicsBodyComponent.STATIC, 3f, 0.6f, 0.9f, true, true, false);
    }

    private static int createBody(World world, HistoryIdRegistry historyIds) {
        int entityId = world.create();
        historyIds.ensureForEntity(entityId);
        world.getMapper(TransformComponent.class).create(entityId);

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
        body.type = PhysicsBodyComponent.DYNAMIC;
        body.fixedRotation = false;
        body.bullet = false;
        body.allowSleep = true;
        body.gravityScale = 1f;
        body.linearDamping = 0f;
        body.angularDamping = 0f;

        world.getMapper(PhysicsShapesComponent.class).create(entityId);
        return entityId;
    }

    private static void executeIfMeaningful(HistoryManager history, Command command) {
        if (command == null) return;
        if (command instanceof HistoryManager.SupportsNoop supportsNoop && supportsNoop.isNoop()) {
            return;
        }
        history.execute(command);
    }

    private static void assertBodyDamping(PhysicsBodyComponent body, float linear, float angular) {
        Assert.assertEquals(linear, body.linearDamping, 0f);
        Assert.assertEquals(angular, body.angularDamping, 0f);
    }

    private static void assertBodyFlags(PhysicsBodyComponent body,
                                        boolean fixedRotation,
                                        boolean bullet,
                                        boolean allowSleep) {
        Assert.assertEquals(fixedRotation, body.fixedRotation);
        Assert.assertEquals(bullet, body.bullet);
        Assert.assertEquals(allowSleep, body.allowSleep);
    }

    private static void assertBodyState(PhysicsBodyComponent body,
                                        int type,
                                        float gravityScale,
                                        float linearDamping,
                                        float angularDamping,
                                        boolean fixedRotation,
                                        boolean bullet,
                                        boolean allowSleep) {
        Assert.assertNotNull(body);
        Assert.assertEquals(type, body.type);
        Assert.assertEquals(gravityScale, body.gravityScale, 0f);
        Assert.assertEquals(linearDamping, body.linearDamping, 0f);
        Assert.assertEquals(angularDamping, body.angularDamping, 0f);
        Assert.assertEquals(fixedRotation, body.fixedRotation);
        Assert.assertEquals(bullet, body.bullet);
        Assert.assertEquals(allowSleep, body.allowSleep);
    }
}
