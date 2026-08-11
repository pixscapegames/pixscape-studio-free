package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.history.HistoryIdRegistry;

/**
 * History command for creating a physics joint with stable identity through HistoryIdRegistry.
 */
public final class CreateJointCommand implements Command {
    private static final boolean DEBUG_WHEEL_CREATE = Boolean.getBoolean("pixscape.debug.wheelJointCreate");

    private final World world;
    private final PhysicsService physicsService;
    private final HistoryIdRegistry historyIds;
    private final int jointType;
    private final long aHistoryId;
    private final long bHistoryId;
    private final float worldX;
    private final float worldY;

    private long historyId = -1L;
    private int lastJointEntityId = -1;
    private int createdJointEntityId = -1;

    public CreateJointCommand(World world,
                              PhysicsService physicsService,
                              HistoryIdRegistry historyIds,
                              int jointType,
                              int aEntityId,
                              int bEntityId,
                              float worldX,
                              float worldY) {
        this.world = world;
        this.physicsService = physicsService;
        this.historyIds = historyIds;
        this.jointType = jointType;
        this.aHistoryId = historyIds.ensureForEntity(aEntityId);
        this.bHistoryId = historyIds.ensureForEntity(bEntityId);
        this.worldX = worldX;
        this.worldY = worldY;
    }

    @Override
    public String label() {
        return "Create Joint";
    }

    @Override
    public void redo() {
        int jointEntityId = createJoint();
        if (jointEntityId < 0 || !world.getEntityManager().isActive(jointEntityId)) {
            throw new IllegalStateException("Failed to create joint for history command.");
        }

        if (historyId <= 0L) {
            historyId = historyIds.ensureForEntity(jointEntityId);
        } else {
            historyIds.bind(jointEntityId, historyId);
        }

        lastJointEntityId = jointEntityId;
        createdJointEntityId = jointEntityId;
    }

    @Override
    public void undo() {
        if (lastJointEntityId >= 0 && world.getEntityManager().isActive(lastJointEntityId)) {
            IdentityRegistry.unindexEntityImmediately(world, lastJointEntityId);
            physicsService.deleteJoint(lastJointEntityId);
            historyIds.unbindEntity(lastJointEntityId);
        }
    }

    public int getCreatedJointEntityId() {
        return createdJointEntityId;
    }

    private int createJoint() {
        int aEntityId = historyIds.entityOfHistoryId(aHistoryId);
        int bEntityId = historyIds.entityOfHistoryId(bHistoryId);
        int created = switch (jointType) {
            case PhysicsJointComponent.TYPE_DISTANCE -> physicsService.createDistanceJoint(aEntityId, bEntityId);
            case PhysicsJointComponent.TYPE_REVOLUTE ->
                    physicsService.createRevoluteJoint(aEntityId, bEntityId, worldX, worldY);
            case PhysicsJointComponent.TYPE_PRISMATIC ->
                    physicsService.createPrismaticJoint(aEntityId, bEntityId, worldX, worldY);
            case PhysicsJointComponent.TYPE_WHEEL ->
                    physicsService.createWheelJoint(aEntityId, bEntityId, worldX, worldY);
            case PhysicsJointComponent.TYPE_FRICTION ->
                    physicsService.createFrictionJoint(aEntityId, bEntityId, worldX, worldY);
            case PhysicsJointComponent.TYPE_MOTOR -> physicsService.createMotorJoint(aEntityId, bEntityId);
            case PhysicsJointComponent.TYPE_WELD ->
                    physicsService.createWeldJoint(aEntityId, bEntityId, worldX, worldY);
            case PhysicsJointComponent.TYPE_PULLEY -> createDefaultPulleyJoint(aEntityId, bEntityId);
            default -> -1;
        };
        if (DEBUG_WHEEL_CREATE && jointType == PhysicsJointComponent.TYPE_WHEEL) {
            debugWheelJointCreate(aEntityId, bEntityId, created);
        }
        return created;
    }

    private void debugWheelJointCreate(int requestedA, int requestedB, int createdJointEid) {
        if (!DEBUG_WHEEL_CREATE) return;
        PhysicsJointComponent joint = createdJointEid >= 0 ? world.getMapper(PhysicsJointComponent.class).getSafe(createdJointEid, null) : null;
        int resolvedA = joint == null ? -1 : joint.aEid;
        int resolvedB = joint == null ? -1 : joint.bEid;
        PhysicsBodyComponent bodyA = resolvedA >= 0 ? world.getMapper(PhysicsBodyComponent.class).getSafe(resolvedA, null) : null;
        PhysicsBodyComponent bodyB = resolvedB >= 0 ? world.getMapper(PhysicsBodyComponent.class).getSafe(resolvedB, null) : null;
        PhysicsShapesComponent fixturesA = resolvedA >= 0 ? world.getMapper(PhysicsShapesComponent.class).getSafe(resolvedA, null) : null;
        PhysicsShapesComponent fixturesB = resolvedB >= 0 ? world.getMapper(PhysicsShapesComponent.class).getSafe(resolvedB, null) : null;
        boolean activeA = resolvedA >= 0 && world.getEntityManager().isActive(resolvedA);
        boolean activeB = resolvedB >= 0 && world.getEntityManager().isActive(resolvedB);
        Gdx.app.log(
                "WheelJointCreate",
                "requestedPair=(" + requestedA + "," + requestedB + ")"
                        + " clickWorld=(" + worldX + "," + worldY + ")"
                        + " createdJointEid=" + createdJointEid
                        + " base=(" + resolvedA + "," + resolvedB + ")"
                        + " anchorsA=(" + (joint == null ? "null" : joint.anchorAx + "," + joint.anchorAy) + ")"
                        + " anchorsB=(" + (joint == null ? "null" : joint.anchorBx + "," + joint.anchorBy) + ")"
                        + " active=(" + activeA + "," + activeB + ")"
                        + " hasBody=(" + (bodyA != null) + "," + (bodyB != null) + ")"
                        + " hasShapes=(" + (fixturesA != null) + "," + (fixturesB != null) + ")"
        );
    }

    private int createDefaultPulleyJoint(int aEntityId, int bEntityId) {
        if (aEntityId < 0 || bEntityId < 0 || aEntityId == bEntityId) return -1;

        var mT = world.getMapper(TransformComponent.class);
        TransformComponent ta = mT.getSafe(aEntityId, null);
        TransformComponent tb = mT.getSafe(bEntityId, null);
        if (ta == null || tb == null) return -1;

        float anchorAWuX = ta.x;
        float anchorAWuY = ta.y;
        float anchorBWuX = tb.x;
        float anchorBWuY = tb.y;

        float span = Math.abs(anchorBWuX - anchorAWuX);
        float supportOffsetY = Math.max(120f, span * 0.20f);
        float supportY = Math.max(worldY, Math.max(anchorAWuY, anchorBWuY) + supportOffsetY);

        return physicsService.createPulleyJoint(
                aEntityId, bEntityId,
                anchorAWuX, anchorAWuY,
                anchorBWuX, anchorBWuY,
                anchorAWuX, supportY,
                anchorBWuX, supportY,
                1f
        );
    }
}
