package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** Adds authored physics to an existing entity. */
public final class AddPhysicsBodyCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsService physicsService;
    private final long bodyHistoryId;
    private final int bodyType;
    private final boolean createDefaultShape;
    private final boolean transformExistedBefore;
    private final boolean noop;
    private PhysicsShapeData createdDefaultShape;

    public AddPhysicsBodyCommand(
            World world,
            HistoryIdRegistry historyIds,
            PhysicsService physicsService,
            int bodyEntityId,
            int bodyType,
            boolean createDefaultShape) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsService = physicsService;
        this.bodyType = sanitizeBodyType(bodyType);
        this.createDefaultShape = createDefaultShape;

        boolean valid = world != null
                && historyIds != null
                && physicsService != null
                && bodyEntityId >= 0
                && world.getEntityManager().isActive(bodyEntityId)
                && !world.getMapper(PhysicsBodyComponent.class).has(bodyEntityId);
        this.bodyHistoryId = valid ? historyIds.ensureForEntity(bodyEntityId) : -1L;
        this.transformExistedBefore = valid
                && world.getMapper(TransformComponent.class).has(bodyEntityId);
        this.noop = !valid || bodyHistoryId <= 0L;
    }

    @Override
    public String label() {
        return "Add Physics Body";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override
    public void redo() {
        redoOutcome();
    }

    @Override
    public CommandOutcome executeOutcome() {
        return redoOutcome();
    }

    @Override
    public CommandOutcome redoOutcome() {
        int entityId = resolveEntity();
        if (noop || entityId < 0
                || world.getMapper(PhysicsBodyComponent.class).has(entityId)) {
            return CommandOutcome.NO_CHANGE;
        }

        if (!world.getMapper(TransformComponent.class).has(entityId)) {
            TransformComponent transform =
                    world.getMapper(TransformComponent.class).create(entityId);
            transform.scaleX = 1f;
            transform.scaleY = 1f;
        }

        PhysicsBodyComponent body =
                world.getMapper(PhysicsBodyComponent.class).create(entityId);
        PhysicsService.initDefaultBody(body);
        body.type = world.getMapper(TiledLayerComponent.class).has(entityId)
                ? PhysicsBodyComponent.STATIC
                : bodyType;

        Array<PhysicsShapeData> candidate =
                FixtureCommandSupport.copyFixtures(world, entityId);
        if (createDefaultShape && candidate.size == 0) {
            if (createdDefaultShape == null) {
                int physicsShapeId = physicsService.allocateNewPhysicsShapeId();
                createdDefaultShape =
                        PhysicsService.createDefaultShape(physicsShapeId).copy();
            }
            candidate.add(createdDefaultShape.copy());
        }
        publish(entityId, PhysicsService.prepareBodyCandidate(candidate));
        FixtureCommandSupport.markDirty(world, entityId);
        FixtureCommandSupport.publishStructureChanged(entityId, this);
        return CommandOutcome.APPLIED;
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        int entityId = resolveEntity();
        if (noop || entityId < 0
                || !world.getMapper(PhysicsBodyComponent.class).has(entityId)) {
            return CommandOutcome.NO_CHANGE;
        }

        physicsService.removePhysics(entityId);
        if (!transformExistedBefore
                && world.getMapper(TransformComponent.class).has(entityId)) {
            world.getMapper(TransformComponent.class).remove(entityId);
        }
        FixtureCommandSupport.publishStructureChanged(entityId, this);
        return CommandOutcome.APPLIED;
    }

    private void publish(int entityId, PreparedPhysicsBodyCandidate prepared) {
        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).has(entityId)
                        ? world.getMapper(PhysicsShapesComponent.class).get(entityId)
                        : world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).has(entityId)
                        ? world.getMapper(PhysicsCompiledFixturesComponent.class).get(entityId)
                        : world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);
    }

    private int resolveEntity() {
        if (historyIds == null || world == null || bodyHistoryId <= 0L) return -1;
        int entityId = historyIds.entityOfHistoryId(bodyHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId)
                ? entityId
                : -1;
    }

    private static int sanitizeBodyType(int bodyType) {
        if (bodyType == PhysicsBodyComponent.STATIC
                || bodyType == PhysicsBodyComponent.KINEMATIC
                || bodyType == PhysicsBodyComponent.DYNAMIC) {
            return bodyType;
        }
        return PhysicsBodyComponent.DYNAMIC;
    }
}
