package games.pixscape.studio.service.entitygraph;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.service.property.PropertyReferenceMapper;
import games.pixscape.studio.service.spatial.SpatialActorAdmission;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

public final class EntityGraphInstantiationService {


    private final World world;
    private final EditorDocumentType documentType;
    private final HistoryManager historyManager;
    private final IdentityRegistry identityRegistry;
    private final PhysicsService physicsService;
    private final BooleanSupplier scenePhysicsEnabled;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;
    private final IntConsumer onCreatedEntity;

    public EntityGraphInstantiationService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, PhysicsService physicsService,
            BooleanSupplier scenePhysicsEnabled) {
        this(world, historyManager, identityRegistry, physicsService,
                scenePhysicsEnabled, null);
    }

    public EntityGraphInstantiationService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, PhysicsService physicsService,
            BooleanSupplier scenePhysicsEnabled,
            IntConsumer onCreatedEntity) {
        this(world, historyManager, identityRegistry, physicsService, scenePhysicsEnabled, onCreatedEntity, EditorDocumentType.SCENE);
    }

    public EntityGraphInstantiationService(
            World world, HistoryManager historyManager, IdentityRegistry identityRegistry,
            PhysicsService physicsService, BooleanSupplier scenePhysicsEnabled,
            IntConsumer onCreatedEntity, EditorDocumentType documentType) {
        this.documentType = java.util.Objects.requireNonNull(documentType, "documentType");
        this.world = world;
        this.historyManager = historyManager;
        this.identityRegistry = identityRegistry;
        this.physicsService = physicsService;
        if (scenePhysicsEnabled == null) {
            throw new IllegalArgumentException("scenePhysicsEnabled must not be null.");
        }
        this.scenePhysicsEnabled = scenePhysicsEnabled;
        this.onCreatedEntity = onCreatedEntity;
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
    }

    public boolean isInstantiationAllowed(EntityGraph graph) {
        return scenePhysicsEnabled.getAsBoolean()
                || !EntityGraphPhysicsSupport.containsAuthoredPhysics(graph);
    }

    public EntityGraphInstantiationResult instantiate(
            EntityGraph graph,
            int activeLayerIndex,
            float dx,
            float dy,
            String commandName) {
        if (graph == null || graph.isEmpty()) {
            return EntityGraphInstantiationResult.empty();
        }
        if (!isInstantiationAllowed(graph)) {
            return EntityGraphInstantiationResult.empty();
        }

        SpatialActorAdmission.requireGraphDestination(world, graph, activeLayerIndex, documentType);
        if (!historyManager.prepareLightCreation(graph.activeLightCount(),
                () -> instantiate(graph, activeLayerIndex, dx, dy, commandName)))
            return EntityGraphInstantiationResult.empty();
        IntIntMap sourceToCreated = new IntIntMap();
        IntMap<GenericEntitySnapshotData> snapshots = new IntMap<>();
        List<PreparedEntity> preparedEntities = prepareEntities(
                graph, activeLayerIndex, dx, dy, snapshots);
        finalizePreparedEntities(preparedEntities, snapshots);
        List<PreparedJointRemap> preparedJointRemaps =
                prepareJointRemaps(snapshots);
        IntIntMap sourceToStable = prepareStableIdentities(preparedEntities);
        for (PreparedEntity prepared : preparedEntities) {
            prepared.initializer.setIdentityStableId(
                    sourceToStable.get(prepared.sourceEntityId, -1));
        }
        IntArray createdIds = fixedSlots(preparedEntities.size());
        IntArray createdRootIds = fixedSlots(rootCount(preparedEntities));
        List<Command> commands = new ArrayList<>();
        int rootSlot = 0;
        for (int entitySlot = 0; entitySlot < preparedEntities.size(); entitySlot++) {
            PreparedEntity prepared = preparedEntities.get(entitySlot);
            int resultEntitySlot = entitySlot;
            int parentStableId = prepared.parentSourceEntityId == -1
                    ? -1 : sourceToStable.get(prepared.parentSourceEntityId, -1);
            EntityGraphHierarchyInitializer initializer =
                    new EntityGraphHierarchyInitializer(
                            world,
                            prepared.initializer,
                            prepared.gameObjectRoot,
                            prepared.gameObjectSourceAssetId,
                            parentStableId,
                            remapProperties(prepared.customProperties, sourceToStable));
            // Standalone joint records are graph implementation details, not clipboard roots.
            // Selecting them after paste is misleading and makes a Game Object paste appear
            // to have an extra root.
            boolean resultRoot = prepared.parentSourceEntityId == -1
                    && !snapshots.get(prepared.sourceEntityId).hasJoint;
            int resultRootSlot = resultRoot ? rootSlot++ : -1;
            CreateEntityCommand cmd = new CreateEntityCommand(
                    world,
                    historyManager.historyIds(),
                    initializer,
                    createdEntityId -> {
                        createdIds.set(resultEntitySlot, createdEntityId);
                        sourceToCreated.put(prepared.sourceEntityId, createdEntityId);
                        if (resultRootSlot >= 0) {
                            createdRootIds.set(resultRootSlot, createdEntityId);
                        }
                        if (onCreatedEntity != null) {
                            onCreatedEntity.accept(createdEntityId);
                        }
                    }, historyManager.historyIds().allocateHistoryId());
            commands.add(cmd);
        }

        if (commands.isEmpty()) return EntityGraphInstantiationResult.empty();
        commands.add(new ApplyPreparedJointRemapsCommand(
                preparedJointRemaps, sourceToCreated));
        String label = isBlank(commandName) ? "Instantiate Entity Graph" : commandName;
        historyManager.execute(new CompositeCommand(label, commands));

        return new EntityGraphInstantiationResult(createdIds, sourceToCreated, createdRootIds);
    }

    private static IntArray fixedSlots(int count) {
        IntArray result = new IntArray(false, count);
        for (int i = 0; i < count; i++) result.add(-1);
        return result;
    }

    private static int rootCount(List<PreparedEntity> preparedEntities) {
        int count = 0;
        for (PreparedEntity prepared : preparedEntities) {
            if (prepared.parentSourceEntityId == -1
                    && !prepared.initializer.toSnapshotData(prepared.sourceEntityId).hasJoint) {
                count++;
            }
        }
        return count;
    }

    private List<PreparedEntity> prepareEntities(
            EntityGraph graph,
            int activeLayerIndex,
            float dx,
            float dy,
            IntMap<GenericEntitySnapshotData> snapshots) {
        List<EntityGraphEntry> orderedEntries = validateAndOrderHierarchy(graph);
        List<PreparedEntity> prepared = new ArrayList<>(orderedEntries.size());
        for (EntityGraphEntry entry : orderedEntries) {
            int sourceEntityId = entry.sourceEntityId();
            if (snapshots.containsKey(sourceEntityId)) {
                throw new IllegalArgumentException(
                        "Entity graph contains duplicate source entity "
                                + sourceEntityId + ".");
            }
            GenericEntityInitializer initializer = entry.initializerFor(world);
            GenericEntitySnapshotData snapshot = initializer.toSnapshotData(sourceEntityId);
            initializer.overrideLayerIndex(activeLayerIndex);
            if (entry.parentSourceEntityId() == -1) {
                initializer.translate(dx, dy);
            }
            snapshot = initializer.toSnapshotData(sourceEntityId);
            snapshots.put(sourceEntityId, snapshot);
            validateHierarchyEntry(entry, snapshot);
            prepared.add(new PreparedEntity(
                    sourceEntityId,
                    entry.parentSourceEntityId(),
                    entry.gameObjectRoot(),
                    entry.gameObjectSourceAssetId(),
                    entry.customProperties(),
                    initializer));
        }
        validateTiledHierarchy(orderedEntries, snapshots);
        validateGameObjectHierarchyPhysics(orderedEntries, snapshots);
        validateSpatialMapAdmission(orderedEntries, snapshots);
        return prepared;
    }

    private void validateSpatialMapAdmission(List<EntityGraphEntry> entries,
                                             IntMap<GenericEntitySnapshotData> snapshots) {
        int mapSource = -1;
        for (EntityGraphEntry entry : entries) {
            var tiled = snapshots.get(entry.sourceEntityId()).tiledEntity;
            if (tiled != null && games.pixscape.runtime.service.SpatialScenePolicy.enabled(tiled.tiles)) {
                int parent = entry.parentSourceEntityId();
                if (mapSource >= 0 && mapSource != parent) {
                    throw new IllegalArgumentException("Spatial Depth must belong to a single Tiled map.");
                }
                mapSource = parent;
            }
        }
        if (mapSource < 0) {
            return;
        }
        var tiles = world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(
                games.pixscape.runtime.component.TiledLayerComponent.class)).getEntities();
        for (int i = 0; i < tiles.size(); i++) {
            if (games.pixscape.runtime.service.SpatialScenePolicy.enabled(world.getMapper(
                    games.pixscape.runtime.component.TiledLayerComponent.class).get(tiles.get(i)))) {
                throw new IllegalArgumentException("Another Tiled map already uses Spatial Depth. Disable its Spatial Depth layers before pasting this map.");
            }
        }
    }



    private IntIntMap prepareStableIdentities(List<PreparedEntity> preparedEntities) {
        IntIntMap sourceToStable = new IntIntMap(preparedEntities.size());
        for (PreparedEntity prepared : preparedEntities) {
            sourceToStable.put(prepared.sourceEntityId, identityRegistry.allocateStableId());
        }
        return sourceToStable;
    }

    private static PropertySet remapProperties(
            PropertySet source, IntIntMap sourceToStable) {
        return PropertyReferenceMapper.remap(source, sourceId -> {
            if (sourceId == -1) return -1;
            if (sourceToStable.containsKey(sourceId)) return sourceToStable.get(sourceId, -1);
            throw new IllegalArgumentException(
                    "Entity graph contains an unresolved OBJECT source ID " + sourceId + ".");
        });
    }

    private void finalizePreparedEntities(
            List<PreparedEntity> prepared,
            IntMap<GenericEntitySnapshotData> snapshots) {
        snapshots.clear();
        for (PreparedEntity entity : prepared) {
            entity.initializer.allocateFreshPhysicsShapeIds(physicsService);
            GenericEntitySnapshotData snapshot =
                    entity.initializer.toSnapshotData(entity.sourceEntityId);
            validatePreparedPhysics(snapshot, entity.sourceEntityId);
            entity.initializer.preparePhysicsCandidate();
            snapshots.put(entity.sourceEntityId, snapshot);
        }
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
        for (PhysicsShapeData shape : snapshot.shapes) {
            if (shape == null) {
                throw new IllegalArgumentException(
                        "Entity graph source " + sourceEntityId
                                + " contains a null physics shape.");
            }
        }
    }

    private static List<EntityGraphEntry> validateAndOrderHierarchy(EntityGraph graph) {
        IntMap<EntityGraphEntry> entriesBySourceId = new IntMap<>(graph.size());
        for (EntityGraphEntry entry : graph.entries()) {
            if (entriesBySourceId.containsKey(entry.sourceEntityId())) {
                throw new IllegalArgumentException("Entity graph contains duplicate source IDs.");
            }
            entriesBySourceId.put(entry.sourceEntityId(), entry);
        }
        IntIntMap visitState = new IntIntMap(graph.size());
        List<EntityGraphEntry> ordered = new ArrayList<>(graph.size());
        for (EntityGraphEntry entry : graph.entries()) {
            appendParentBeforeChild(entry, entriesBySourceId, visitState, ordered);
        }
        return ordered;
    }

    private static void appendParentBeforeChild(
            EntityGraphEntry entry,
            IntMap<EntityGraphEntry> entriesBySourceId,
            IntIntMap visitState,
            List<EntityGraphEntry> ordered) {
        int sourceId = entry.sourceEntityId();
        int state = visitState.get(sourceId, 0);
        if (state == 2) return;
        if (state == 1) {
            throw new IllegalArgumentException("Entity graph hierarchy contains a cycle.");
        }
        visitState.put(sourceId, 1);
        if (entry.parentSourceEntityId() != -1) {
            EntityGraphEntry parent = entriesBySourceId.get(entry.parentSourceEntityId());
            if (parent == null) {
                throw new IllegalArgumentException("Entity graph member references a missing parent.");
            }
            if (!parent.gameObjectRoot()) {
                throw new IllegalArgumentException(
                        "Entity graph member parent must be a Game Object root.");
            }
            appendParentBeforeChild(parent, entriesBySourceId, visitState, ordered);
        }
        visitState.put(sourceId, 2);
        ordered.add(entry);
    }

    private static void validateHierarchyEntry(
            EntityGraphEntry entry, GenericEntitySnapshotData snapshot) {
        if (entry.gameObjectRoot()) {
            if (!snapshot.hasTransform || !snapshot.hasEntityIndex) {
                throw new IllegalArgumentException(
                        "Game Object graph root requires TransformComponent and EntityIndexComponent.");
            }
            if (!isPositiveUniformScale(snapshot.scaleX, snapshot.scaleY)) {
                throw new IllegalArgumentException(
                        "Game Object graph root scale must be finite, positive, and uniform.");
            }
        }
    }

    private static TiledEntityRole tiledRole(GenericEntitySnapshotData snapshot) {
        var tiled = snapshot == null ? null : snapshot.tiledEntity;
        if (tiled == null) {
            return TiledEntityRole.NONE;
        }
        if (tiled.map != null) {
            return TiledEntityRole.MAP;
        }
        if (tiled.layer != null) {
            return switch (tiled.layer.kind) {
                case TILE -> TiledEntityRole.TILE_LAYER;
                case OBJECT -> TiledEntityRole.OBJECT_LAYER;
                case IMAGE -> TiledEntityRole.IMAGE_LAYER;
            };
        }
        return TiledEntityRole.NONE;
    }

    /** Detached validation before allocating identities, creating entities or adding history. */
    private static void validateTiledHierarchy(List<EntityGraphEntry> entries,
                                               IntMap<GenericEntitySnapshotData> snapshots) {
        for (var entry : entries) {
            var snapshot = snapshots.get(entry.sourceEntityId());
            var role = tiledRole(snapshot);
            if (entry.parentSourceEntityId() >= 0) {
                TiledEntityRole.requireStructuralRelation(
                        role, tiledRole(snapshots.get(entry.parentSourceEntityId())));
            } else if (role != TiledEntityRole.NONE
                    && role != TiledEntityRole.MAP) {
                throw new IllegalArgumentException("Clipboard Tiled layer requires its owning map.");
            }
            if (role == TiledEntityRole.NONE) {
                continue;
            }
            if (!snapshot.hasTransform || snapshot.spatialFootprint != null
                    || snapshot.hasPhysicsBody && role != TiledEntityRole.TILE_LAYER
                    || !entry.gameObjectSourceAssetId().isEmpty()
                    || entry.gameObjectRoot() != (role == TiledEntityRole.MAP
                        || role == TiledEntityRole.OBJECT_LAYER)) {
                throw new IllegalArgumentException("Clipboard Tiled structure has incompatible actor/composition capabilities.");
            }
            TiledEntityRole.requireTransform(role, snapshot.rotationRad,
                    snapshot.scaleX, snapshot.scaleY, snapshot.originX, snapshot.originY);
        }
    }

    private static void validateGameObjectHierarchyPhysics(
            List<EntityGraphEntry> entries,
            IntMap<GenericEntitySnapshotData> snapshots) {
        IntMap<EntityGraphEntry> bySourceId = new IntMap<EntityGraphEntry>(entries.size());
        boolean containsGameObjectHierarchy = false;
        for (EntityGraphEntry entry : entries) {
            bySourceId.put(entry.sourceEntityId(), entry);
            containsGameObjectHierarchy |= entry.gameObjectRoot() || entry.parentSourceEntityId() != -1;
        }
        if (!containsGameObjectHierarchy) return;
        for (EntityGraphEntry entry : entries) {
            GenericEntitySnapshotData snapshot = snapshots.get(entry.sourceEntityId());
            if (snapshot == null) continue;
            boolean hierarchyEntry = entry.gameObjectRoot() || entry.parentSourceEntityId() != -1;
            if (hierarchyEntry && snapshot.shapes != null
                    && tiledRole(snapshot) != TiledEntityRole.TILE_LAYER) {
                for (PhysicsShapeData shape : snapshot.shapes) {
                    if (shape != null && shape.spatialBlockId > 0) {
                        throw new IllegalArgumentException(
                                "Game Object clipboard hierarchies do not support "
                                        + "Physics shapes linked to Scene Spatial blocks (spatialBlockId > 0).");
                    }
                }
            }
            if (!snapshot.hasPhysicsBody) continue;
            int parentSourceId = entry.parentSourceEntityId();
            while (parentSourceId != -1) {
                EntityGraphEntry parent = bySourceId.get(parentSourceId);
                GenericEntitySnapshotData parentSnapshot = snapshots.get(parentSourceId);
                if (parent == null || parentSnapshot == null || !parent.gameObjectRoot()
                        || Float.compare(parentSnapshot.scaleX, 1f) != 0
                        || Float.compare(parentSnapshot.scaleY, 1f) != 0) {
                    throw new IllegalArgumentException(
                            "Physics in a Game Object hierarchy requires every ancestor scale to be (1,1).");
                }
                parentSourceId = parent.parentSourceEntityId();
            }
        }
    }

    private static boolean isPositiveUniformScale(float scaleX, float scaleY) {
        return !Float.isNaN(scaleX) && !Float.isInfinite(scaleX)
                && !Float.isNaN(scaleY) && !Float.isInfinite(scaleY)
                && scaleX > 0f && Float.compare(scaleX, scaleY) == 0;
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
        final int parentSourceEntityId;
        final boolean gameObjectRoot;
        final String gameObjectSourceAssetId;
        final PropertySet customProperties;
        final GenericEntityInitializer initializer;

        PreparedEntity(int sourceEntityId, int parentSourceEntityId,
                       boolean gameObjectRoot, String gameObjectSourceAssetId,
                       PropertySet customProperties, GenericEntityInitializer initializer) {
            this.sourceEntityId = sourceEntityId;
            this.parentSourceEntityId = parentSourceEntityId;
            this.gameObjectRoot = gameObjectRoot;
            this.gameObjectSourceAssetId = gameObjectSourceAssetId;
            this.customProperties = customProperties != null ? customProperties.copy() : null;
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
