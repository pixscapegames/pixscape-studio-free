package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryIdRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * History command for enabling/disabling physics on a body entity.
 * When disabling, dependent joints are deleted while preserving identity/history.
 * <p>
 * Default initialization settings:
 * - bodyType: PhysicsBodyComponent.STATIC / KINEMATIC / DYNAMIC
 * - hasDefaultFixture: true => creates a default BOX fixture if no snapshot exists,
 * false => creates only the body + empty fixtures component.
 */
public final class TogglePhysicsBodyCommand implements Command {

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long bodyHistoryId;
    private final boolean enable;
    private final int bodyType;
    private final boolean hasDefaultFixture;
    private final PhysicsBodyState bodySnapshot;
    private final PhysicsFixturesState fixturesSnapshot;
    private final List<DeleteJointCommand> dependentJointCommands;
    private final IntArray spatialPhysicsBlockIds = new IntArray();

    public TogglePhysicsBodyCommand(World world,
                                    HistoryIdRegistry historyIds,
                                    int bodyEntityId,
                                    boolean enable,
                                    int bodyType,
                                    boolean hasDefaultFixture) {
        this.world = world;
        this.historyIds = historyIds;
        this.bodyHistoryId = historyIds.ensureForEntity(bodyEntityId);
        this.enable = enable;
        this.bodyType = sanitizeBodyType(bodyType);
        this.hasDefaultFixture = hasDefaultFixture;

        var mBody = world.getMapper(PhysicsBodyComponent.class);
        var mFixtures = world.getMapper(PhysicsFixturesComponent.class);
        this.bodySnapshot = mBody.has(bodyEntityId) ? PhysicsBodyState.capture(mBody.get(bodyEntityId)) : null;
        this.fixturesSnapshot = mFixtures.has(bodyEntityId)
                ? PhysicsFixturesState.capture(mFixtures.get(bodyEntityId))
                : null;

        this.dependentJointCommands = new ArrayList<>();
        if (!enable) {
            this.dependentJointCommands.addAll(captureDependentJointDeletes());
            SpatialBlocksComponent blocks =
                    world.getMapper(SpatialBlocksComponent.class).getSafe(bodyEntityId, null);
            if (blocks != null && blocks.blocks != null) {
                for (int i = 0; i < blocks.blocks.size; i++) {
                    SpatialBlockData block = blocks.blocks.get(i);
                    if (block != null && block.physicsCollision) {
                        spatialPhysicsBlockIds.add(block.id);
                    }
                }
            }
        }
    }

    @Override
    public String label() {
        return enable ? "Enable Physics" : "Disable Physics";
    }

    @Override
    public void redo() {
        if (enable) {
            enablePhysics();
        } else {
            for (DeleteJointCommand deleteJoint : dependentJointCommands) {
                deleteJoint.redo();
            }
            setSpatialCollisionFlags(false);
            disablePhysics();
        }
        markDirty();
        markSpatialChanged();
    }

    @Override
    public void undo() {
        if (enable) {
            disablePhysics();
        } else {
            enablePhysics();
            setSpatialCollisionFlags(true);
            for (int i = dependentJointCommands.size() - 1; i >= 0; i--) {
                dependentJointCommands.get(i).undo();
            }
        }
        markDirty();
        markSpatialChanged();
    }

    private List<DeleteJointCommand> captureDependentJointDeletes() {
        List<DeleteJointCommand> commands = new ArrayList<>();
        var mJoint = world.getMapper(PhysicsJointComponent.class);
        IntBag joints = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class))
                .getEntities();

        int currentBodyEntityId = resolveBodyEntityId();
        if (currentBodyEntityId < 0) return commands;

        int[] data = joints.getData();
        for (int i = 0, n = joints.size(); i < n; i++) {
            int jointEntityId = data[i];
            PhysicsJointComponent joint = mJoint.getSafe(jointEntityId, null);
            if (joint == null) continue;
            if (joint.aEid == currentBodyEntityId || joint.bEid == currentBodyEntityId) {
                commands.add(new DeleteJointCommand(world, historyIds, jointEntityId));
            }
        }
        return commands;
    }

    private void enablePhysics() {
        int entityId = resolveBodyEntityId();
        if (entityId < 0) return;

        var mBody = world.getMapper(PhysicsBodyComponent.class);
        var mFixtures = world.getMapper(PhysicsFixturesComponent.class);
        var mTransform = world.getMapper(TransformComponent.class);

        PhysicsBodyComponent body = mBody.has(entityId) ? mBody.get(entityId) : mBody.create(entityId);
        PhysicsFixturesComponent fixtures = mFixtures.has(entityId) ? mFixtures.get(entityId) : mFixtures.create(entityId);

        // Global invariant: every physics body must have a TransformComponent.
        if (!mTransform.has(entityId)) {
            TransformComponent transform = mTransform.create(entityId);
            initDefaultTransform(transform);
        }

        if (bodySnapshot != null) {
            bodySnapshot.apply(body);
        } else {
            initDefaultBody(body, bodyType);
        }

        fixtures.fixtures.clear();
        if (fixturesSnapshot != null) {
            fixturesSnapshot.apply(fixtures);
        } else if (hasDefaultFixture) {
            fixtures.fixtures.add(PhysicsService.createDefaultFixture());
        }

        historyIds.ensureForEntity(entityId);
    }

    private static void initDefaultTransform(TransformComponent t) {
        t.x = 0f;
        t.y = 0f;
        t.rotationRad = 0f;
        t.scaleX = 1f;
        t.scaleY = 1f;
        t.originX = 0f;
        t.originY = 0f;
    }

    private void disablePhysics() {
        int entityId = resolveBodyEntityId();
        if (entityId < 0) return;

        var mBody = world.getMapper(PhysicsBodyComponent.class);
        var mFixtures = world.getMapper(PhysicsFixturesComponent.class);
        var mRuntimeBody = world.getMapper(PhysicsRuntimeBodyComponent.class);
        if (mFixtures.has(entityId)) mFixtures.remove(entityId);
        if (mBody.has(entityId)) mBody.remove(entityId);
        if (mRuntimeBody.has(entityId)) mRuntimeBody.remove(entityId);
    }

    private void markDirty() {
        int entityId = resolveBodyEntityId();
        if (entityId < 0) return;

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(entityId, PhysicsDirtyBits.ALL);
        }
    }

    private void setSpatialCollisionFlags(boolean enabled) {
        if (spatialPhysicsBlockIds.size == 0) return;
        int entityId = resolveBodyEntityId();
        if (entityId < 0) return;
        SpatialBlocksComponent blocks =
                world.getMapper(SpatialBlocksComponent.class).getSafe(entityId, null);
        if (blocks == null || blocks.blocks == null) return;
        boolean changed = false;
        for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData block = blocks.blocks.get(i);
            if (block == null || !spatialPhysicsBlockIds.contains(block.id)) continue;
            if (block.physicsCollision != enabled) {
                block.physicsCollision = enabled;
                changed = true;
            }
        }
        if (changed) blocks.revision++;
    }

    private void markSpatialChanged() {
        if (spatialPhysicsBlockIds.size == 0) return;
        int entityId = resolveBodyEntityId();
        if (entityId >= 0) SpatialBlockCommandSupport.markChanged(world, entityId, this);
    }

    private int resolveBodyEntityId() {
        int entityId = historyIds.entityOfHistoryId(bodyHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
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

    private static final class PhysicsFixturesState {
        private final Array<FixtureDefData> fixtures = new Array<>();

        static PhysicsFixturesState capture(PhysicsFixturesComponent source) {
            PhysicsFixturesState state = new PhysicsFixturesState();
            for (FixtureDefData fixture : source.fixtures) {
                if (fixture != null) state.fixtures.add(fixture.copy());
            }
            return state;
        }

        void apply(PhysicsFixturesComponent target) {
            target.fixtures.clear();
            for (FixtureDefData fixture : fixtures) {
                if (fixture != null) target.fixtures.add(fixture.copy());
            }
        }
    }
}
