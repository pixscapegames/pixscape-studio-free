package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

/** Atomically enables or disables the authored Spatial Actor state for one entity. */
public final class ToggleSpatialActorCommand
        implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsService physicsService;
    private final long entityHistoryId;
    private final boolean enable;
    private final boolean bodyExisted;
    private final BodySnapshot bodyBefore;
    private final SpatialSnapshot spatialBefore;
    private final Array<PhysicsShapeData> shapesBefore;
    private final Array<PhysicsShapeData> shapesAfter;
    private final boolean noop;

    public ToggleSpatialActorCommand(
            World world,
            HistoryIdRegistry historyIds,
            PhysicsService physicsService,
            int entityId,
            boolean enable,
            boolean eligibleForActivation,
            PhysicsShapeData defaultFootprint) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsService = physicsService;
        this.enable = enable;

        boolean valid = world != null
                && historyIds != null
                && physicsService != null
                && entityId >= 0
                && world.getEntityManager().isActive(entityId)
                && (!enable || eligibleForActivation);
        this.entityHistoryId = valid ? historyIds.ensureForEntity(entityId) : -1L;

        PhysicsBodyComponent body = valid
                ? world.getMapper(PhysicsBodyComponent.class).getSafe(entityId, null)
                : null;
        this.bodyExisted = body != null;
        this.bodyBefore = body != null ? BodySnapshot.capture(body) : null;
        SpatialHeightComponent spatial = valid
                ? world.getMapper(SpatialHeightComponent.class).getSafe(entityId, null)
                : null;
        this.spatialBefore = SpatialSnapshot.capture(spatial);
        this.shapesBefore = valid ? copyShapes(entityId) : new Array<>(true, 0, PhysicsShapeData.class);
        this.shapesAfter = valid ? copy(shapesBefore) : new Array<>(true, 0, PhysicsShapeData.class);

        boolean prepared = valid && entityHistoryId > 0L;
        if (prepared && enable) {
            int markedIndex = findMarked(shapesAfter, 0);
            if (markedIndex >= 0 && findMarked(shapesAfter, markedIndex + 1) >= 0) {
                prepared = false;
            } else if (markedIndex >= 0) {
                prepared = structurallyValidMarked(shapesAfter.get(markedIndex));
            } else if (defaultFootprint == null) {
                prepared = false;
            } else {
                PhysicsShapeData created = defaultFootprint.copy();
                created.physicsShapeId = physicsService.allocateNewPhysicsShapeId();
                created.spatialFootprint = true;
                prepared = structurallyValidMarked(created);
                if (prepared) shapesAfter.add(created);
            }
        } else if (prepared) {
            int markedIndex = findMarked(shapesAfter, 0);
            if (markedIndex >= 0) {
                if (findMarked(shapesAfter, markedIndex + 1) >= 0) {
                    prepared = false;
                } else {
                    shapesAfter.removeIndex(markedIndex);
                }
            }
        }
        this.noop = !prepared || sameTargetState();
    }

    @Override
    public String label() {
        return enable ? "Enable Spatial Actor" : "Disable Spatial Actor";
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
        return apply(shapesAfter, enable, true);
    }

    @Override
    public void undo() {
        undoOutcome();
    }

    @Override
    public CommandOutcome undoOutcome() {
        return apply(shapesBefore, spatialBefore != null, false);
    }

    private CommandOutcome apply(
            Array<PhysicsShapeData> targetShapes,
            boolean targetSpatial,
            boolean applyingAfter) {
        int entityId = resolveEntity();
        if (noop || entityId < 0) return CommandOutcome.NO_CHANGE;

        PreparedPhysicsBodyCandidate prepared;
        try {
            prepared = prepare(entityId, targetShapes);
        } catch (RuntimeException invalid) {
            return CommandOutcome.NO_CHANGE;
        }

        ComponentMapper<PhysicsBodyComponent> bodies = world.getMapper(PhysicsBodyComponent.class);
        if (applyingAfter && !bodyExisted && !bodies.has(entityId)) {
            PhysicsBodyComponent body = bodies.create(entityId);
            PhysicsService.initDefaultBody(body);
        }
        if (!applyingAfter && !bodyExisted) {
            physicsService.removePhysics(entityId);
        } else {
            PhysicsBodyComponent body = bodies.has(entityId) ? bodies.get(entityId) : bodies.create(entityId);
            if (applyingAfter && enable) {
                if (bodyExisted) bodyBefore.apply(body);
                body.gravityScale = 0f;
            } else if (bodyBefore != null) {
                bodyBefore.apply(body);
            }
            publish(entityId, prepared);
        }

        ComponentMapper<SpatialHeightComponent> heights = world.getMapper(SpatialHeightComponent.class);
        if (targetSpatial) {
            SpatialHeightComponent height = heights.has(entityId)
                    ? heights.get(entityId) : heights.create(entityId);
            if (applyingAfter && spatialBefore == null) {
                height.altitude = 0f;
                height.height = 1f;
            } else if (spatialBefore != null) {
                spatialBefore.apply(height);
            }
        } else if (heights.has(entityId)) {
            heights.remove(entityId);
        }

        FixtureCommandSupport.markDirty(world, entityId);
        FixtureCommandSupport.publishStructureChanged(entityId, this);
        EventFlow.i().publish(new EventFlow.SpatialHeightChanged(entityId, EventFlow.tag(this)));
        return CommandOutcome.APPLIED;
    }

    private PreparedPhysicsBodyCandidate prepare(int entityId, Array<PhysicsShapeData> targetShapes) {
        FixtureCommandSupport.validateLinkedRelations(
                entityId,
                world.getMapper(PhysicsShapesComponent.class).getSafe(entityId, null),
                targetShapes);
        return FixtureCommandSupport.containsLinkedShape(targetShapes)
                ? PhysicsService.prepareBodyCandidate(
                        world, entityId, targetShapes,
                        FixtureCommandSupport.requireCurrentPixelsPerMeter())
                : PhysicsService.prepareBodyCandidate(targetShapes);
    }

    private void publish(int entityId, PreparedPhysicsBodyCandidate prepared) {
        ComponentMapper<PhysicsShapesComponent> shapes = world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PhysicsCompiledFixturesComponent> compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class);
        PhysicsShapesComponent targetShapes = shapes.has(entityId)
                ? shapes.get(entityId) : shapes.create(entityId);
        PhysicsCompiledFixturesComponent targetCompiled = compiled.has(entityId)
                ? compiled.get(entityId) : compiled.create(entityId);
        PhysicsService.publishPreparedCandidate(targetShapes, targetCompiled, prepared);
    }

    private int resolveEntity() {
        if (world == null || historyIds == null || entityHistoryId <= 0L) return -1;
        int entityId = historyIds.entityOfHistoryId(entityHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId) ? entityId : -1;
    }

    private Array<PhysicsShapeData> copyShapes(int entityId) {
        return FixtureCommandSupport.copyFixtures(world, entityId);
    }

    private boolean sameTargetState() {
        if (enable) return spatialBefore != null && shapesEqual(shapesBefore, shapesAfter)
                && bodyExisted && bodyBefore.gravityScale == 0f;
        return spatialBefore == null && shapesEqual(shapesBefore, shapesAfter);
    }

    private static boolean structurallyValidMarked(PhysicsShapeData shape) {
        try {
            shape.validateStructure();
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static int findMarked(Array<PhysicsShapeData> shapes, int start) {
        for (int i = start; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape != null && shape.spatialFootprint) return i;
        }
        return -1;
    }

    private static Array<PhysicsShapeData> copy(Array<PhysicsShapeData> source) {
        Array<PhysicsShapeData> copy = new Array<>(true, source.size, PhysicsShapeData.class);
        for (int i = 0; i < source.size; i++) copy.add(source.get(i).copy());
        return copy;
    }

    private static boolean shapesEqual(Array<PhysicsShapeData> left, Array<PhysicsShapeData> right) {
        if (left.size != right.size) return false;
        for (int i = 0; i < left.size; i++) {
            if (!left.get(i).contentEquals(right.get(i))) return false;
        }
        return true;
    }

    private static final class SpatialSnapshot {
        final float altitude;
        final float height;

        private SpatialSnapshot(float altitude, float height) {
            this.altitude = altitude;
            this.height = height;
        }

        static SpatialSnapshot capture(SpatialHeightComponent height) {
            return height == null ? null : new SpatialSnapshot(height.altitude, height.height);
        }

        void apply(SpatialHeightComponent target) {
            target.altitude = altitude;
            target.height = height;
        }
    }

    private static final class BodySnapshot {
        final int type;
        final boolean fixedRotation;
        final boolean bullet;
        final boolean allowSleep;
        final boolean awake;
        final float gravityScale;
        final float linearDamping;
        final float angularDamping;

        private BodySnapshot(PhysicsBodyComponent body) {
            type = body.type;
            fixedRotation = body.fixedRotation;
            bullet = body.bullet;
            allowSleep = body.allowSleep;
            awake = body.awake;
            gravityScale = body.gravityScale;
            linearDamping = body.linearDamping;
            angularDamping = body.angularDamping;
        }

        static BodySnapshot capture(PhysicsBodyComponent body) {
            return new BodySnapshot(body);
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
