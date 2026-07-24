package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsBodyCompiler;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
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
    private final PhysicsService physicsService;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;

    public EntityGraphInstantiationService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, PhysicsService physicsService) {
        this.world = world;
        this.historyManager = historyManager;
        this.identityRegistry = identityRegistry;
        this.physicsService = physicsService;
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

        IntArray createdIds = new IntArray();
        IntIntMap sourceToCreated = new IntIntMap();
        IntMap<GenericEntitySnapshotData> snapshots = new IntMap<>();
        List<PreparedEntity> preparedEntities = prepareEntities(
                graph, activeLayerIndex, dx, dy, snapshots);
        List<PreparedJointRemap> preparedJointRemaps =
                prepareJointRemaps(snapshots);
        List<Command> commands = new ArrayList<>();
        for (PreparedEntity prepared : preparedEntities) {
            CreateEntityCommand cmd = new CreateEntityCommand(
                    world,
                    historyManager.historyIds(),
                    prepared.initializer,
                    createdEntityId -> {
                createdIds.add(createdEntityId);
                sourceToCreated.put(prepared.sourceEntityId, createdEntityId);
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

    private List<PreparedEntity> prepareEntities(
            EntityGraph graph,
            int activeLayerIndex,
            float dx,
            float dy,
            IntMap<GenericEntitySnapshotData> snapshots) {
        List<PreparedEntity> prepared = new ArrayList<>();
        for (EntityGraphEntry entry : graph.entries()) {
            int sourceEntityId = entry.sourceEntityId();
            if (snapshots.containsKey(sourceEntityId)) {
                throw new IllegalArgumentException(
                        "Entity graph contains duplicate source entity "
                                + sourceEntityId + ".");
            }
            GenericEntityInitializer initializer = entry.initializer().duplicate();
            initializer.allocateFreshPhysicsShapeIds(physicsService);
            initializer.overrideLayerIndex(activeLayerIndex);
            initializer.translate(dx, dy);
            initializer.setIdentityStableId(identityRegistry.allocateStableId());
            GenericEntitySnapshotData snapshot =
                    initializer.toSnapshotData(sourceEntityId);
            validatePreparedPhysics(snapshot, sourceEntityId);
            snapshots.put(sourceEntityId, snapshot);
            prepared.add(new PreparedEntity(sourceEntityId, initializer));
        }
        return prepared;
    }

    private static List<PreparedJointRemap> prepareJointRemaps(
            IntMap<GenericEntitySnapshotData> snapshots) {
        List<PreparedJointRemap> remaps = new ArrayList<>();
        for (IntMap.Entry<GenericEntitySnapshotData> entry : snapshots) {
            int sourceId = entry.key;
            GenericEntitySnapshotData snapshot = entry.value;
            if (!snapshot.hasJoint) continue;
            if (!hasSpecificJointData(snapshot)) {
                throw new IllegalArgumentException(
                        "Joint source " + sourceId
                                + " is missing data for joint type "
                                + snapshot.jointType + ".");
            }
            requireBodyEndpoint(
                    snapshots, sourceId, snapshot.jointAEid, "aEid");
            requireBodyEndpoint(
                    snapshots, sourceId, snapshot.jointBEid, "bEid");
            if (snapshot.jointAEid == snapshot.jointBEid) {
                throw new IllegalArgumentException(
                        "Joint source " + sourceId + " has identical body endpoints.");
            }
            if (snapshot.jointType == PhysicsJointComponent.TYPE_GEAR) {
                if (snapshot.gearJoint1Eid == snapshot.gearJoint2Eid) {
                    throw new IllegalArgumentException(
                            "Gear joint source " + sourceId
                                    + " has identical joint dependencies.");
                }
                requireGearSource(
                        snapshots,
                        sourceId,
                        snapshot.gearJoint1Eid,
                        "joint1Eid");
                requireGearSource(
                        snapshots,
                        sourceId,
                        snapshot.gearJoint2Eid,
                        "joint2Eid");
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
            GenericEntitySnapshotData snapshot, int sourceEntityId) {
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

    private static void requireBodyEndpoint(
            IntMap<GenericEntitySnapshotData> snapshots,
            int jointSourceId,
            int referencedSourceId,
            String field) {
        GenericEntitySnapshotData endpoint = snapshots.get(referencedSourceId);
        if (endpoint == null) {
            throw new IllegalArgumentException(
                    "Joint source " + jointSourceId + " references missing "
                            + field + " source " + referencedSourceId + ".");
        }
        if (!endpoint.hasPhysicsBody) {
            throw new IllegalArgumentException(
                    "Joint source " + jointSourceId + " references "
                            + field + " source " + referencedSourceId
                            + " without PhysicsBodyComponent.");
        }
        if (endpoint.shapes == null || endpoint.shapes.size == 0) {
            throw new IllegalArgumentException(
                    "Joint source " + jointSourceId + " references "
                            + field + " source " + referencedSourceId
                            + " without non-empty PhysicsShapesComponent.");
        }
    }

    private static void requireGearSource(
            IntMap<GenericEntitySnapshotData> snapshots,
            int gearSourceId,
            int referencedSourceId,
            String field) {
        if (referencedSourceId == gearSourceId) {
            throw new IllegalArgumentException(
                    "Gear joint source " + gearSourceId + " has "
                            + field + " referencing itself.");
        }
        GenericEntitySnapshotData source = snapshots.get(referencedSourceId);
        if (source == null) {
            throw new IllegalArgumentException(
                    "Gear joint source " + gearSourceId + " references missing "
                            + field + " source " + referencedSourceId + ".");
        }
        if (!source.hasJoint) {
            throw new IllegalArgumentException(
                    "Gear joint source " + gearSourceId + " references "
                            + field + " source " + referencedSourceId
                            + " without PhysicsJointComponent.");
        }
        if (source.jointType != PhysicsJointComponent.TYPE_REVOLUTE
                && source.jointType != PhysicsJointComponent.TYPE_PRISMATIC) {
            throw new IllegalArgumentException(
                    "Gear joint source " + gearSourceId + " references "
                            + field + " source " + referencedSourceId
                            + " which must be revolute or prismatic.");
        }
        if (!hasSpecificJointData(source)) {
            throw new IllegalArgumentException(
                    "Gear joint source " + gearSourceId + " references "
                            + field + " source " + referencedSourceId
                            + " without its specific joint component.");
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
                remap.targetJointId = requireCreatedMapping(
                        sourceToCreated, remap.sourceJointId);
                remap.targetBodyAId = requireCreatedMapping(
                        sourceToCreated, remap.sourceBodyAId);
                remap.targetBodyBId = requireCreatedMapping(
                        sourceToCreated, remap.sourceBodyBId);
                if (remap.gear) {
                    remap.targetJoint1Id = requireCreatedMapping(
                            sourceToCreated, remap.sourceJoint1Id);
                    remap.targetJoint2Id = requireCreatedMapping(
                            sourceToCreated, remap.sourceJoint2Id);
                }
            }
            for (PreparedJointRemap remap : remaps) {
                PhysicsJointComponent base = mJointBase.get(remap.targetJointId);
                if (base == null) {
                    throw new IllegalStateException(
                            "Prepared joint target " + remap.targetJointId
                                    + " lost PhysicsJointComponent.");
                }
                base.aEid = remap.targetBodyAId;
                base.bEid = remap.targetBodyBId;
                if (remap.gear) {
                    PhysicsGearJointComponent gear =
                            mGear.get(remap.targetJointId);
                    if (gear == null) {
                        throw new IllegalStateException(
                                "Prepared gear target " + remap.targetJointId
                                        + " lost PhysicsGearJointComponent.");
                    }
                    gear.joint1Eid = remap.targetJoint1Id;
                    gear.joint2Eid = remap.targetJoint2Id;
                }
            }
        }

        @Override
        public void undo() {
            // Joint components belong to the created entities and are removed by child undo.
        }
    }

    private static int requireCreatedMapping(
            IntIntMap sourceToCreated, int sourceEntityId) {
        if (!sourceToCreated.containsKey(sourceEntityId)) {
            throw new IllegalStateException(
                    "Missing created entity mapping for prepared source "
                            + sourceEntityId + ".");
        }
        return sourceToCreated.get(sourceEntityId, -1);
    }

    private static final class PreparedEntity {
        final int sourceEntityId;
        final GenericEntityInitializer initializer;

        PreparedEntity(
                int sourceEntityId, GenericEntityInitializer initializer) {
            this.sourceEntityId = sourceEntityId;
            this.initializer = initializer;
        }
    }

    private static final class PreparedJointRemap {
        final int sourceJointId;
        final int sourceBodyAId;
        final int sourceBodyBId;
        final boolean gear;
        final int sourceJoint1Id;
        final int sourceJoint2Id;
        int targetJointId;
        int targetBodyAId;
        int targetBodyBId;
        int targetJoint1Id;
        int targetJoint2Id;

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
