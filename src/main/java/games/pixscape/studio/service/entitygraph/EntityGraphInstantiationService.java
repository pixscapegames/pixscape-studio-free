package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsBodyCompiler;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;

import java.util.ArrayList;
import java.util.List;

public final class EntityGraphInstantiationService {
    private final World world;
    private final HistoryManager historyManager;
    private final IdentityRegistry identityRegistry;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;

    public EntityGraphInstantiationService(World world, HistoryManager historyManager, IdentityRegistry identityRegistry) {
        this.world = world;
        this.historyManager = historyManager;
        this.identityRegistry = identityRegistry;
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
    }

    public EntityGraphInstantiationResult instantiate(EntityGraph graph,
                                                      int activeLayerIndex,
                                                      float dx,
                                                      float dy,
                                                      String commandName) {
        if (graph == null || graph.isEmpty()) {
            return EntityGraphInstantiationResult.empty();
        }

        List<Command> commands = new ArrayList<>();
        IntArray createdIds = new IntArray();
        IntIntMap sourceToCreated = new IntIntMap();
        List<PreparedJointRemap> preparedJointRemaps = prepareJointRemaps(graph);

        for (EntityGraphEntry entry : graph.entries()) {
            GenericEntityInitializer init = entry.initializer().duplicate();
            init.allocateFreshPhysicsShapeIds();
            validatePreparedPhysics(init, entry.sourceEntityId());
            init.overrideLayerIndex(activeLayerIndex);
            init.translate(dx, dy);
            init.setIdentityStableId(identityRegistry.allocateStableId());

            int sourceId = entry.sourceEntityId();
            CreateEntityCommand cmd = new CreateEntityCommand(world, historyManager.historyIds(), init, createdEntityId -> {
                createdIds.add(createdEntityId);
                sourceToCreated.put(sourceId, createdEntityId);
            });
            commands.add(cmd);
        }

        if (commands.isEmpty()) return EntityGraphInstantiationResult.empty();
        commands.add(new ApplyPreparedJointRemapsCommand(
                preparedJointRemaps, sourceToCreated));

        String label = isBlank(commandName) ? "Instantiate Entity Graph" : commandName;
        historyManager.execute(new CompositeCommand(label, commands));

        return new EntityGraphInstantiationResult(createdIds, sourceToCreated);
    }

    private static List<PreparedJointRemap> prepareJointRemaps(EntityGraph graph) {
        IntSet sources = new IntSet(Math.max(1, graph.size()));
        List<PreparedJointRemap> remaps = new ArrayList<>();
        for (EntityGraphEntry entry : graph.entries()) {
            if (!sources.add(entry.sourceEntityId())) {
                throw new IllegalArgumentException(
                        "Entity graph contains duplicate source entity "
                                + entry.sourceEntityId() + ".");
            }
        }

        for (EntityGraphEntry entry : graph.entries()) {
            int sourceId = entry.sourceEntityId();
            GenericEntitySnapshotData snapshot =
                    entry.initializer().toSnapshotData(sourceId);
            if (!snapshot.hasJoint) continue;
            if (!hasSpecificJointData(snapshot)) {
                throw new IllegalArgumentException(
                        "Joint source " + sourceId
                                + " is missing data for joint type "
                                + snapshot.jointType + ".");
            }
            requireMappedEndpoint(sources, sourceId, snapshot.jointAEid, "aEid");
            requireMappedEndpoint(sources, sourceId, snapshot.jointBEid, "bEid");
            if (snapshot.jointAEid == snapshot.jointBEid) {
                throw new IllegalArgumentException(
                        "Joint source " + sourceId + " has identical body endpoints.");
            }
            if (snapshot.jointType == PhysicsJointComponent.TYPE_GEAR) {
                if (!snapshot.hasGearJoint) {
                    throw new IllegalArgumentException(
                            "Gear joint source " + sourceId
                                    + " is missing PhysicsGearJointComponent data.");
                }
                requireMappedEndpoint(
                        sources, sourceId, snapshot.gearJoint1Eid, "joint1Eid");
                requireMappedEndpoint(
                        sources, sourceId, snapshot.gearJoint2Eid, "joint2Eid");
                if (snapshot.gearJoint1Eid == snapshot.gearJoint2Eid) {
                    throw new IllegalArgumentException(
                            "Gear joint source " + sourceId
                                    + " has identical joint dependencies.");
                }
            }
            remaps.add(new PreparedJointRemap(
                    sourceId,
                    snapshot.jointAEid,
                    snapshot.jointBEid,
                    snapshot.jointType == PhysicsJointComponent.TYPE_GEAR,
                    snapshot.gearJoint1Eid,
                    snapshot.gearJoint2Eid));
        }
        return remaps;
    }

    private static void validatePreparedPhysics(
            GenericEntityInitializer initializer, int sourceEntityId) {
        GenericEntitySnapshotData snapshot =
                initializer.toSnapshotData(sourceEntityId);
        if (snapshot.shapes == null || snapshot.shapes.size == 0) return;
        PhysicsShapesComponent shapes = new PhysicsShapesComponent();
        for (PhysicsShapeData shape : snapshot.shapes) {
            if (shape == null) {
                throw new IllegalArgumentException(
                        "Entity graph source " + sourceEntityId
                                + " contains a null physics shape.");
            }
            shapes.add(shape.copy());
        }
        new PhysicsBodyCompiler().compile(shapes);
    }

    private static boolean hasSpecificJointData(GenericEntitySnapshotData snapshot) {
        switch (snapshot.jointType) {
            case PhysicsJointComponent.TYPE_DISTANCE:
                return snapshot.hasDistanceJoint;
            case PhysicsJointComponent.TYPE_REVOLUTE:
                return snapshot.hasRevoluteJoint;
            case PhysicsJointComponent.TYPE_PRISMATIC:
                return snapshot.hasPrismaticJoint;
            case PhysicsJointComponent.TYPE_WHEEL:
                return snapshot.hasWheelJoint;
            case PhysicsJointComponent.TYPE_FRICTION:
                return snapshot.hasFrictionJoint;
            case PhysicsJointComponent.TYPE_MOTOR:
                return snapshot.hasMotorJoint;
            case PhysicsJointComponent.TYPE_WELD:
                return snapshot.hasWeldJoint;
            case PhysicsJointComponent.TYPE_PULLEY:
                return snapshot.hasPulleyJoint;
            case PhysicsJointComponent.TYPE_GEAR:
                return snapshot.hasGearJoint;
            default:
                return false;
        }
    }

    private static void requireMappedEndpoint(
            IntSet sources, int jointSourceId, int referencedSourceId, String field) {
        if (!sources.contains(referencedSourceId)) {
            throw new IllegalArgumentException(
                    "Joint source " + jointSourceId + " references missing "
                            + field + " source " + referencedSourceId + ".");
        }
    }

    private final class ApplyPreparedJointRemapsCommand implements Command {
        private final List<PreparedJointRemap> remaps;
        private final IntIntMap sourceToCreated;

        private ApplyPreparedJointRemapsCommand(
                List<PreparedJointRemap> remaps, IntIntMap sourceToCreated) {
            this.remaps = remaps;
            this.sourceToCreated = sourceToCreated;
        }

        @Override
        public String label() {
            return "Remap Instantiated Joints";
        }

        @Override
        public void redo() {
            ComponentMapper<PhysicsGearJointComponent> mGear =
                    world.getMapper(PhysicsGearJointComponent.class);
            for (PreparedJointRemap remap : remaps) {
                int pastedId = sourceToCreated.get(remap.sourceJointId, -1);
                PhysicsJointComponent base = mJointBase.get(pastedId);
                base.aEid = sourceToCreated.get(remap.sourceBodyAId, -1);
                base.bEid = sourceToCreated.get(remap.sourceBodyBId, -1);
                if (remap.gear) {
                    PhysicsGearJointComponent gear = mGear.get(pastedId);
                    gear.joint1Eid =
                            sourceToCreated.get(remap.sourceJoint1Id, -1);
                    gear.joint2Eid =
                            sourceToCreated.get(remap.sourceJoint2Id, -1);
                }
            }
        }

        @Override
        public void undo() {
            // Joint components belong to the created entities and are removed by child undo.
        }
    }

    private static final class PreparedJointRemap {
        final int sourceJointId;
        final int sourceBodyAId;
        final int sourceBodyBId;
        final boolean gear;
        final int sourceJoint1Id;
        final int sourceJoint2Id;

        PreparedJointRemap(
                int sourceJointId,
                int sourceBodyAId,
                int sourceBodyBId,
                boolean gear,
                int sourceJoint1Id,
                int sourceJoint2Id) {
            this.sourceJointId = sourceJointId;
            this.sourceBodyAId = sourceBodyAId;
            this.sourceBodyBId = sourceBodyBId;
            this.gear = gear;
            this.sourceJoint1Id = sourceJoint1Id;
            this.sourceJoint2Id = sourceJoint2Id;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
