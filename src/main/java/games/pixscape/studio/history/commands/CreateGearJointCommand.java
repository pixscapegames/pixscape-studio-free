package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.history.HistoryIdRegistry;

public final class CreateGearJointCommand implements Command {

    private final World world;
    private final PhysicsService physicsService;
    private final HistoryIdRegistry historyIds;
    private final long joint1HistoryId;
    private final long joint2HistoryId;
    private final float ratio;

    private long historyId = -1L;
    private int lastJointEntityId = -1;
    private int createdJointEntityId = -1;

    public CreateGearJointCommand(World world,
                                  PhysicsService physicsService,
                                  HistoryIdRegistry historyIds,
                                  int joint1EntityId,
                                  int joint2EntityId,
                                  float ratio) {
        this.world = world;
        this.physicsService = physicsService;
        this.historyIds = historyIds;
        this.joint1HistoryId = historyIds.ensureForEntity(joint1EntityId);
        this.joint2HistoryId = historyIds.ensureForEntity(joint2EntityId);
        this.ratio = ratio;
    }

    @Override
    public String label() {
        return "Create Gear Joint";
    }

    @Override
    public void redo() {
        int jointEntityId = createJoint();
        if (jointEntityId < 0 || !world.getEntityManager().isActive(jointEntityId)) {
            throw new IllegalStateException("Failed to create gear joint for history command.");
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
        int joint1EntityId = historyIds.entityOfHistoryId(joint1HistoryId);
        int joint2EntityId = historyIds.entityOfHistoryId(joint2HistoryId);
        return physicsService.createGearJoint(joint1EntityId, joint2EntityId, ratio);
    }
}
