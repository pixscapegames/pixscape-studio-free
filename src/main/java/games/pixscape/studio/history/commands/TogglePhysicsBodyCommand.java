package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryIdRegistry;

/**
 * Enables or disables a body without conflating disable with destructive removal.
 */
public final class TogglePhysicsBodyCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final PhysicsService physicsService;
    private final long bodyHistoryId;
    private final boolean enable;
    private final int bodyType;
    private final boolean createDefaultShape;
    private final boolean hadBody;
    private final PhysicsBodyState bodyBefore;
    private final Array<PhysicsShapeData> shapesBefore = new Array<>();
    private PhysicsShapeData createdDefaultShape;

    public TogglePhysicsBodyCommand(
            World world,
            HistoryIdRegistry historyIds,
            PhysicsService physicsService,
            int bodyEntityId,
            boolean enable,
            int bodyType,
            boolean createDefaultShape) {
        this.world = world;
        this.historyIds = historyIds;
        this.physicsService = physicsService;
        this.bodyHistoryId = historyIds.ensureForEntity(bodyEntityId);
        this.enable = enable;
        this.bodyType = sanitizeBodyType(bodyType);
        this.createDefaultShape = createDefaultShape;

        PhysicsBodyComponent body =
                world.getMapper(PhysicsBodyComponent.class).getSafe(bodyEntityId, null);
        this.hadBody = body != null;
        this.bodyBefore = body != null ? PhysicsBodyState.capture(body) : null;

        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).getSafe(bodyEntityId, null);
        if (shapes != null && shapes.shapes != null) {
            for (int i = 0; i < shapes.shapes.size; i++) {
                PhysicsShapeData shape = shapes.shapes.get(i);
                if (shape != null) shapesBefore.add(shape.copy());
            }
        }
    }

    @Override
    public String label() {
        return enable ? "Enable Physics Body" : "Disable Physics Body";
    }

    @Override
    public void redo() {
        int entityId = resolveBodyEntityId();
        if (entityId < 0) return;

        Array<PhysicsShapeData> candidate =
                FixtureCommandSupport.copyFixtures(world, entityId);
        if (enable && !hadBody && createDefaultShape && candidate.size == 0) {
            if (createdDefaultShape == null) {
                createdDefaultShape = PhysicsService.createDefaultShape(
                        physicsService.allocateNewPhysicsShapeId()).copy();
            }
            candidate.add(createdDefaultShape.copy());
        }
        PreparedPhysicsBodyCandidate prepared =
                PhysicsService.prepareBodyCandidate(candidate);

        if (enable) {
            PhysicsBodyComponent body = ensureBodyStructure(entityId);
            publishPrepared(entityId, prepared);
            body.enabled = true;
        } else {
            PhysicsBodyComponent body =
                    world.getMapper(PhysicsBodyComponent.class).getSafe(entityId, null);
            if (body != null) {
                publishPrepared(entityId, prepared);
                body.enabled = false;
            }
        }
        markDirty(entityId);
    }

    @Override
    public void undo() {
        int entityId = resolveBodyEntityId();
        if (entityId < 0) return;

        if (!hadBody) {
            removeCreatedPhysics(entityId);
        } else {
            Array<PhysicsShapeData> candidate =
                    new Array<>(true, shapesBefore.size, PhysicsShapeData.class);
            for (int i = 0; i < shapesBefore.size; i++) {
                candidate.add(shapesBefore.get(i).copy());
            }
            PreparedPhysicsBodyCandidate prepared =
                    PhysicsService.prepareBodyCandidate(candidate);
            PhysicsBodyComponent body =
                    world.getMapper(PhysicsBodyComponent.class).has(entityId)
                            ? world.getMapper(PhysicsBodyComponent.class).get(entityId)
                            : world.getMapper(PhysicsBodyComponent.class).create(entityId);
            bodyBefore.apply(body);
            publishPrepared(entityId, prepared);
        }
        markDirty(entityId);
    }

    private PhysicsBodyComponent ensureBodyStructure(int entityId) {
        var bodyMapper = world.getMapper(PhysicsBodyComponent.class);
        PhysicsBodyComponent body =
                bodyMapper.has(entityId) ? bodyMapper.get(entityId) : bodyMapper.create(entityId);
        if (!hadBody) {
            initDefaultBody(body, bodyType);
        }

        var transformMapper = world.getMapper(TransformComponent.class);
        if (!transformMapper.has(entityId)) {
            TransformComponent transform = transformMapper.create(entityId);
            transform.scaleX = 1f;
            transform.scaleY = 1f;
        }

        return body;
    }

    private void publishPrepared(int entityId, PreparedPhysicsBodyCandidate prepared) {
        var shapesMapper = world.getMapper(PhysicsShapesComponent.class);
        var compiledMapper = world.getMapper(PhysicsCompiledFixturesComponent.class);
        PhysicsShapesComponent shapes = shapesMapper.has(entityId)
                ? shapesMapper.get(entityId)
                : shapesMapper.create(entityId);
        PhysicsCompiledFixturesComponent compiled = compiledMapper.has(entityId)
                ? compiledMapper.get(entityId)
                : compiledMapper.create(entityId);
        PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);
    }

    private void removeCreatedPhysics(int entityId) {
        var bodyMapper = world.getMapper(PhysicsBodyComponent.class);
        var shapesMapper = world.getMapper(PhysicsShapesComponent.class);
        var compiledMapper = world.getMapper(PhysicsCompiledFixturesComponent.class);
        var spatialFootprintMapper = world.getMapper(SpatialPhysicsFootprintComponent.class);
        var runtimeMapper = world.getMapper(PhysicsRuntimeBodyComponent.class);
        if (runtimeMapper.has(entityId)) runtimeMapper.remove(entityId);
        if (compiledMapper.has(entityId)) compiledMapper.remove(entityId);
        if (spatialFootprintMapper.has(entityId)) spatialFootprintMapper.remove(entityId);
        if (shapesMapper.has(entityId)) shapesMapper.remove(entityId);
        if (bodyMapper.has(entityId)) bodyMapper.remove(entityId);
    }

    private void markDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.physics(entityId, PhysicsDirtyBits.ALL);
    }

    private int resolveBodyEntityId() {
        int entityId = historyIds.entityOfHistoryId(bodyHistoryId);
        return entityId >= 0 && world.getEntityManager().isActive(entityId)
                ? entityId
                : -1;
    }

    private static int sanitizeBodyType(int bodyType) {
        return switch (bodyType) {
            case PhysicsBodyComponent.STATIC,
                 PhysicsBodyComponent.KINEMATIC,
                 PhysicsBodyComponent.DYNAMIC -> bodyType;
            default -> PhysicsBodyComponent.DYNAMIC;
        };
    }

    private static void initDefaultBody(PhysicsBodyComponent body, int bodyType) {
        body.type = sanitizeBodyType(bodyType);
        body.fixedRotation = false;
        body.bullet = false;
        body.allowSleep = true;
        body.awake = true;
        body.gravityScale = 1f;
        body.linearDamping = 0f;
        body.angularDamping = 0f;
        body.enabled = true;
    }

    private static final class PhysicsBodyState {
        int type;
        boolean fixedRotation;
        boolean bullet;
        boolean allowSleep;
        boolean awake;
        float gravityScale;
        float linearDamping;
        float angularDamping;
        boolean enabled;

        static PhysicsBodyState capture(PhysicsBodyComponent body) {
            PhysicsBodyState state = new PhysicsBodyState();
            state.type = body.type;
            state.fixedRotation = body.fixedRotation;
            state.bullet = body.bullet;
            state.allowSleep = body.allowSleep;
            state.awake = body.awake;
            state.gravityScale = body.gravityScale;
            state.linearDamping = body.linearDamping;
            state.angularDamping = body.angularDamping;
            state.enabled = body.enabled;
            return state;
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
            body.enabled = enabled;
        }
    }
}
