package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** Removes authored physics while preserving the owning entity. */
public final class RemovePhysicsBodyCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsService physicsService;
    private final long bodyHistoryId;
    private final BodySnapshot bodySnapshot;
    private final Array<PhysicsShapeData> shapeSnapshots =
            new Array<>(true, 4, PhysicsShapeData.class);
    private final DeleteEntitiesCommand jointDeleteCommand;
    private final boolean containsLinkedShape;
    private final boolean noop;

    public RemovePhysicsBodyCommand(
            World world,
            HistoryIdRegistry historyIds,
            PhysicsService physicsService,
            int bodyEntityId) {
        this(world, historyIds, physicsService, bodyEntityId, false);
    }

    /**
     * @param allowTiledLinkedShapes whether a Tiled Map owner may remove its complete
     *                               collision body, including Spatial Block links
     */
    public RemovePhysicsBodyCommand(
            World world,
            HistoryIdRegistry historyIds,
            PhysicsService physicsService,
            int bodyEntityId,
            boolean allowTiledLinkedShapes) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsService = physicsService;

        PhysicsBodyComponent body = world != null && bodyEntityId >= 0
                ? world.getMapper(PhysicsBodyComponent.class)
                        .getSafe(bodyEntityId, null)
                : null;
        boolean valid = world != null
                && historyIds != null
                && physicsService != null
                && body != null
                && world.getEntityManager().isActive(bodyEntityId);
        this.bodyHistoryId = valid ? historyIds.ensureForEntity(bodyEntityId) : -1L;
        this.bodySnapshot = valid ? BodySnapshot.capture(body) : null;

        if (valid) {
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class)
                    .getSafe(bodyEntityId, null);
            if (shapes != null && shapes.shapes != null) {
                for (int i = 0; i < shapes.shapes.size; i++) {
                    PhysicsShapeData shape = shapes.shapes.get(i);
                    if (shape != null) shapeSnapshots.add(shape.copy());
                }
            }
        }
        boolean linkedShapeFound = false;
        for (int i = 0; i < shapeSnapshots.size; i++) {
            if (shapeSnapshots.get(i).spatialBlockId > 0) {
                linkedShapeFound = true;
                break;
            }
        }
        this.containsLinkedShape = linkedShapeFound;
        boolean linkedRemovalAllowed = !containsLinkedShape
                || (allowTiledLinkedShapes
                && valid
                && world.getMapper(TiledLayerComponent.class).has(bodyEntityId));

        IntArray jointIds = valid && linkedRemovalAllowed
                ? physicsService.collectJointsAffectedByBodyRemoval(
                        bodyEntityId, new IntArray(false, 8))
                : new IntArray();
        this.jointDeleteCommand = valid && linkedRemovalAllowed
                ? new DeleteEntitiesCommand(
                        world, historyIds, jointIds, this::markRestoredJointDirty)
                : null;
        this.noop = !valid || !linkedRemovalAllowed || bodyHistoryId <= 0L;
    }

    @Override
    public String label() {
        return "Remove Physics Body";
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
                || !world.getMapper(PhysicsBodyComponent.class).has(entityId)) {
            return CommandOutcome.NO_CHANGE;
        }

        jointDeleteCommand.redo();
        physicsService.removePhysics(entityId);
        EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(
                EventFlow.tag(this)));
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
                || world.getMapper(PhysicsBodyComponent.class).has(entityId)) {
            return CommandOutcome.NO_CHANGE;
        }

        PhysicsBodyComponent body =
                world.getMapper(PhysicsBodyComponent.class).create(entityId);
        bodySnapshot.apply(body);

        Array<PhysicsShapeData> candidate =
                new Array<>(true, shapeSnapshots.size, PhysicsShapeData.class);
        for (int i = 0; i < shapeSnapshots.size; i++) {
            candidate.add(shapeSnapshots.get(i).copy());
        }
        PreparedPhysicsBodyCandidate prepared = containsLinkedShape
                ? PhysicsService.prepareBodyCandidate(
                        world,
                        entityId,
                        candidate,
                        FixtureCommandSupport.requireCurrentPixelsPerMeter())
                : PhysicsService.prepareBodyCandidate(candidate);
        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).has(entityId)
                        ? world.getMapper(PhysicsShapesComponent.class).get(entityId)
                        : world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).has(entityId)
                        ? world.getMapper(PhysicsCompiledFixturesComponent.class).get(entityId)
                        : world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);
        FixtureCommandSupport.markDirty(world, entityId);

        jointDeleteCommand.undo();
        FixtureCommandSupport.publishStructureChanged(entityId, this);
        return CommandOutcome.APPLIED;
    }

    private int resolveEntity() {
        if (historyIds == null || world == null || bodyHistoryId <= 0L) return -1;
        int entityId = historyIds.entityOfHistoryId(bodyHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId)
                ? entityId
                : -1;
    }

    private void markRestoredJointDirty(int jointEntityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.joint(jointEntityId, JointDirtyBits.ALL);
    }

    private static final class BodySnapshot {
        int type;
        boolean fixedRotation;
        boolean bullet;
        boolean allowSleep;
        boolean awake;
        float gravityScale;
        float linearDamping;
        float angularDamping;

        static BodySnapshot capture(PhysicsBodyComponent body) {
            BodySnapshot snapshot = new BodySnapshot();
            snapshot.type = body.type;
            snapshot.fixedRotation = body.fixedRotation;
            snapshot.bullet = body.bullet;
            snapshot.allowSleep = body.allowSleep;
            snapshot.awake = body.awake;
            snapshot.gravityScale = body.gravityScale;
            snapshot.linearDamping = body.linearDamping;
            snapshot.angularDamping = body.angularDamping;
            return snapshot;
        }

        void apply(PhysicsBodyComponent body) {
            body.type = type;
            body.fixedRotation = fixedRotation;
            body.bullet = bullet;
            body.allowSleep = allowSleep;
            body.awake = awake;
            body.gravityScale = gravityScale;
            body.linearDamping = linearDamping;
            body.angularDamping = angularDamping;
        }
    }
}
