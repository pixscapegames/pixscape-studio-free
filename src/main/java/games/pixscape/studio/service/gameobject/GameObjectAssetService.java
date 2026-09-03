package games.pixscape.studio.service.gameobject;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsPulleyJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.gameobject.GameObjectAssetId;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.commands.ConvertSelectionToGameObjectCommand;
import games.pixscape.studio.history.commands.ReorderLogicalLayerCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;
import games.pixscape.studio.service.property.PropertyReferenceMapper;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.helper.AuthoredGeometryTransform;
import games.pixscape.studio.helper.GameObjectGizmoGeometry;
import games.pixscape.studio.model.EntityKind;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Studio publication and diagnostic-reading boundary for {@code .gameobject} assets. */
public final class GameObjectAssetService {
    /** The one authoritative interpretation of a Game Object publication selection. */
    public enum SelectionMode {
        CONVERT_SELECTION,
        SAVE_EXISTING_GAME_OBJECT,
        UNAVAILABLE
    }

    /**
     * Effective roots are normalized before this result is published: a selected descendant
     * of a selected Game Object root is never captured a second time.
     */
    public record SelectionClassification(
            SelectionMode mode, IntArray effectiveSelection, String rejection, String actionLabel) {
        public boolean isAvailable() {
            return mode != SelectionMode.UNAVAILABLE;
        }
    }

    private static final String CONVERT_ACTION_LABEL = "Convert Selection to Game Object…";
    private static final String SAVE_ACTION_LABEL = "Save as Game Object Asset…";
    private final World world;
    private final HistoryManager historyManager;
    private final IdentityRegistry identityRegistry;
    private final IntConsumer onCreatedEntity;
    private final IntConsumer rootSelection;
    private final SelectionService selectionService;
    private final PhysicsService physicsService;
    private final BooleanSupplier scenePhysicsEnabled;
    private final GameObjectAssetLoader loader = new GameObjectAssetLoader();
    private final GameObjectEntityDataMapper mapper = new GameObjectEntityDataMapper();
    private final GameObjectJointDataMapper jointMapper = new GameObjectJointDataMapper();

    public GameObjectAssetService(World world) {
        this(world, null, null, null, null, null, null, () -> true);
    }

    public GameObjectAssetService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, IntConsumer onCreatedEntity,
            IntConsumer rootSelection) {
        this(world, historyManager, identityRegistry, onCreatedEntity, rootSelection, null, null, () -> true);
    }

    public GameObjectAssetService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, IntConsumer onCreatedEntity,
            IntConsumer rootSelection, SelectionService selectionService) {
        this(world, historyManager, identityRegistry, onCreatedEntity, rootSelection,
                selectionService, null, () -> true);
    }

    public GameObjectAssetService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, IntConsumer onCreatedEntity,
            IntConsumer rootSelection, SelectionService selectionService,
            PhysicsService physicsService) {
        this(world, historyManager, identityRegistry, onCreatedEntity, rootSelection,
                selectionService, physicsService, () -> true);
    }

    public GameObjectAssetService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, IntConsumer onCreatedEntity,
            IntConsumer rootSelection, SelectionService selectionService,
            PhysicsService physicsService, BooleanSupplier scenePhysicsEnabled) {
        if (world == null) throw new IllegalArgumentException("World is required.");
        this.world = world;
        this.historyManager = historyManager;
        this.identityRegistry = identityRegistry;
        this.onCreatedEntity = onCreatedEntity;
        this.rootSelection = rootSelection;
        this.selectionService = selectionService;
        this.physicsService = physicsService;
        if (scenePhysicsEnabled == null) throw new IllegalArgumentException("scenePhysicsEnabled is required.");
        this.scenePhysicsEnabled = scenePhysicsEnabled;
    }

    public void saveGameObject(FileHandle file, EntityGraph graph) {
        if (graph == null || graph.isEmpty()) {
            throw new IllegalArgumentException("Game Object hierarchy is required.");
        }
        GameObjectAsset asset = createAsset(graph);
        loader.save(file, asset);
    }

    public GameObjectAsset loadGameObjectAsset(FileHandle file) {
        return loader.load(file);
    }

    /**
     * Returns whether this authored hierarchy contains state that can only exist on a
     * Spatial-enabled Layer. The check deliberately walks every asset member: Game
     * Object nesting is an authoring hierarchy, not a Spatial aggregate.
     */
    public boolean requiresSpatialLayer(GameObjectAsset asset) {
        if (asset == null || asset.entities == null) return false;
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            if (entity == null) continue;
            if (entity.spatialHeight != null) return true;
            if (entity.physicsShapes == null) continue;
            for (GameObjectAsset.PhysicsShapeData shape : entity.physicsShapes) {
                if (shape != null && shape.spatialFootprint) return true;
            }
        }
        return false;
    }

    /** Compatibility preflight for callers that specifically require a wrapping conversion. */
    public boolean canConvertSelectionToGameObject(IntArray selection) {
        return classifySelection(selection).mode == SelectionMode.CONVERT_SELECTION;
    }

    /** Returns the current preflight reason, or {@code null} when the requested action is available. */
    public String conversionRejection(IntArray selection) {
        return classifySelection(selection).rejection;
    }

    /**
     * Classifies selection shape and validates the corresponding operation boundary.
     * Consumers use this result for wording, enabled state and execution; they do not
     * independently infer whether a selected Game Object should be wrapped or saved.
     */
    public SelectionClassification classifySelection(IntArray selection) {
        IntArray effective = normalizeSelection(selection);
        if (effective.size == 0) {
            return unavailable(effective, "Select one or more supported top-level entities.",
                    CONVERT_ACTION_LABEL);
        }

        int only = effective.size == 1 ? effective.first() : -1;
        boolean existingGameObject = only >= 0 && world.getMapper(GameObjectComponent.class).has(only);
        String actionLabel = existingGameObject ? SAVE_ACTION_LABEL : CONVERT_ACTION_LABEL;
        if (existingGameObject && world.getMapper(GameObjectMemberComponent.class).has(only)) {
            return unavailable(effective,
                    "Nested Game Objects cannot be saved as standalone assets.", actionLabel);
        }

        try {
            if (existingGameObject) {
                prepareExistingGameObjectSave(only);
                return new SelectionClassification(SelectionMode.SAVE_EXISTING_GAME_OBJECT,
                        effective, null, actionLabel);
            }
            prepareConversion(effective, "gameobjects/selection.gameobject");
            return new SelectionClassification(SelectionMode.CONVERT_SELECTION,
                    effective, null, actionLabel);
        } catch (RuntimeException failure) {
            return unavailable(effective, failure.getMessage(), actionLabel);
        }
    }

    /** Publishes a new immutable asset then wraps the same selected scene entities in its root. */
    public void convertSelectionToGameObject(
            FileHandle gameObjectFile, FileHandle previewFile, String logicalAssetId) {
        if (historyManager == null || identityRegistry == null || selectionService == null) {
            throw new IllegalStateException("Game Object conversion dependencies are not configured.");
        }
        if (gameObjectFile == null || previewFile == null) {
            throw new IllegalArgumentException("Game Object asset and preview paths are required.");
        }
        if (gameObjectFile.exists()) {
            throw new IllegalArgumentException("A Game Object asset already exists: " + gameObjectFile.path());
        }
        String canonicalId = GameObjectAssetId.normalize(logicalAssetId);
        SelectionClassification classification = classifySelection(selectionService.getSelectionSnapshot());
        if (classification.mode != SelectionMode.CONVERT_SELECTION) {
            throw unavailableOperation(classification, "converted");
        }
        ConversionPlan plan = prepareConversion(classification.effectiveSelection, canonicalId);
        GameObjectAsset asset = createConversionAsset(plan);
        ConvertSelectionToGameObjectCommand command = new ConvertSelectionToGameObjectCommand(
                world, historyManager.historyIds(), identityRegistry, selectionService,
                plan.selectedTopToBottom, plan.order, plan.rootX, plan.rootY,
                plan.rootOriginX, plan.rootOriginY, canonicalId);
        try {
            loader.save(gameObjectFile, asset);
            GameObjectPreviewWriter.writePlaceholder(previewFile);
            historyManager.execute(command);
        } catch (RuntimeException | Error failure) {
            if (previewFile.exists()) previewFile.delete();
            if (gameObjectFile.exists()) gameObjectFile.delete();
            throw failure;
        }
    }

    /**
     * Publishes one existing top-level Game Object hierarchy without changing Scene state.
     * In particular, provenance, IDs, transforms, membership, selection and History are untouched.
     */
    public void saveExistingGameObjectAsAsset(
            FileHandle gameObjectFile, FileHandle previewFile, String logicalAssetId) {
        saveExistingGameObjectAsAsset(gameObjectFile, previewFile, logicalAssetId,
                GameObjectPreviewWriter::writePlaceholder);
    }

    /** Package-visible writer seam keeps publication semantics testable without a native Pixmap backend. */
    void saveExistingGameObjectAsAsset(
            FileHandle gameObjectFile, FileHandle previewFile, String logicalAssetId,
            Consumer<FileHandle> previewWriter) {
        if (selectionService == null) {
            throw new IllegalStateException("Game Object save dependencies are not configured.");
        }
        if (gameObjectFile == null || previewFile == null || previewWriter == null) {
            throw new IllegalArgumentException("Game Object asset and preview paths are required.");
        }
        if (gameObjectFile.exists()) {
            throw new IllegalArgumentException("A Game Object asset already exists: " + gameObjectFile.path());
        }
        GameObjectAssetId.normalize(logicalAssetId);
        SelectionClassification classification = classifySelection(selectionService.getSelectionSnapshot());
        if (classification.mode != SelectionMode.SAVE_EXISTING_GAME_OBJECT) {
            throw unavailableOperation(classification, "saved");
        }
        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureForGameObject(classification.effectiveSelection);
        if (graph.isEmpty()) {
            throw new IllegalStateException("Selected Game Object hierarchy is unavailable for asset publication.");
        }
        try {
            saveGameObject(gameObjectFile, graph);
            previewWriter.accept(previewFile);
        } catch (RuntimeException | Error failure) {
            if (previewFile.exists()) previewFile.delete();
            if (gameObjectFile.exists()) gameObjectFile.delete();
            throw failure;
        }
    }

    /** Instantiates one validated asset as a real hierarchy in one atomic History command. */
    public EntityGraphInstantiationResult instantiateGameObject(
            FileHandle file, String logicalAssetId, int destinationLayer,
            float rootWorldX, float rootWorldY) {
        if (historyManager == null || identityRegistry == null) {
            throw new IllegalStateException("Game Object instantiation dependencies are not configured.");
        }
        GameObjectAsset asset = loader.load(file);
        if (!scenePhysicsEnabled.getAsBoolean() && containsAuthoredPhysics(asset)) {
            throw new IllegalStateException(
                    "Cannot instantiate a Game Object with authored Physics while scene Physics is disabled.");
        }
        String canonicalId = GameObjectAssetId.normalize(logicalAssetId);
        IntIntMap sourceToStable = new IntIntMap(asset.entities.size());
        for (GameObjectAsset.GameObjectEntityData data : asset.entities) {
            sourceToStable.put(data.sourceEntityId, identityRegistry.allocateStableId());
        }

        List<GameObjectAsset.GameObjectEntityData> ordered = topologicalOrder(asset);
        IntArray createdIds = new IntArray(false, ordered.size());
        IntIntMap sourceToCreated = new IntIntMap(ordered.size());
        IntIntMap jointToCreated = new IntIntMap(asset.joints.size());
        List<Command> commands = new ArrayList<>(ordered.size() + asset.joints.size() + 2);
        for (GameObjectAsset.GameObjectEntityData data : ordered) {
            EntityGraphEntry entry = mapper.toGraphEntry(world, data);
            GenericEntityInitializer generic = entry.initializer();
            if (data.physicsBody != null) {
                if (physicsService == null) {
                    throw new IllegalStateException(
                            "Game Object Physics asset instantiation requires PhysicsService.");
                }
                generic.allocateFreshPhysicsShapeIds(physicsService);
                generic.preparePhysicsCandidate();
            }
            generic.setIdentityStableId(sourceToStable.get(data.sourceEntityId, -1));
            generic.overrideLayerIndex(destinationLayer);
            if (data.sourceEntityId == asset.rootSourceEntityId) {
                generic.translate(rootWorldX - data.transform.x, rootWorldY - data.transform.y);
                generic.overrideZIndex(SortKey64.MAX_Z);
            }
            int parentStableId = data.parentSourceEntityId == -1
                    ? -1 : sourceToStable.get(data.parentSourceEntityId, -1);
            PropertySet properties = data.customProperties != null
                    ? remapProperties(data.customProperties, sourceToStable) : null;
            GameObjectAssetEntityInitializer initializer =
                    new GameObjectAssetEntityInitializer(
                            world, generic, data, parentStableId,
                            data.sourceEntityId == asset.rootSourceEntityId ? canonicalId : "",
                            properties);
            commands.add(new CreateEntityCommand(
                    world, historyManager.historyIds(), initializer, entityId -> {
                createdIds.add(entityId);
                sourceToCreated.put(data.sourceEntityId, entityId);
                if (onCreatedEntity != null) onCreatedEntity.accept(entityId);
            }));
        }
        for (GameObjectAsset.GameObjectJointData data : asset.joints) {
            GenericEntityInitializer generic = jointMapper.toInitializer(world, data);
            generic.setIdentityStableId(identityRegistry.allocateStableId());
            commands.add(new CreateEntityCommand(world, historyManager.historyIds(), generic, entityId -> {
                createdIds.add(entityId);
                jointToCreated.put(data.jointLocalId, entityId);
                if (onCreatedEntity != null) onCreatedEntity.accept(entityId);
            }));
        }
        if (!asset.joints.isEmpty()) {
            commands.add(new RemapAssetJointsCommand(asset.joints, asset.rootSourceEntityId,
                    sourceToCreated, jointToCreated));
        }
        commands.add(ReorderLogicalLayerCommand.normalizeAfterCreation(
                world, historyManager.historyIds(), destinationLayer,
                new LayerLogicalOrderService(world),
                () -> currentCreatedEntityIds(sourceToCreated)));
        if (rootSelection != null) {
            commands.add(new Command() {
                @Override public String label() { return "Select Game Object Root"; }
                @Override public void redo() {
                    rootSelection.accept(sourceToCreated.get(asset.rootSourceEntityId, -1));
                }
                @Override public void undo() { rootSelection.accept(-1); }
            });
        }
        historyManager.execute(new CompositeCommand("Instantiate Game Object", commands));
        return new EntityGraphInstantiationResult(createdIds, sourceToCreated);
    }

    private static boolean containsAuthoredPhysics(GameObjectAsset asset) {
        if (!asset.joints.isEmpty()) return true;
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            if (entity.physicsBody != null || !entity.physicsShapes.isEmpty()) return true;
        }
        return false;
    }

    /** Runs after all hierarchy Bodies and standalone joint entities have been created. */
    private final class RemapAssetJointsCommand implements Command {
        private final List<GameObjectAsset.GameObjectJointData> joints;
        private final int rootLocalEntityId;
        private final IntIntMap entityMap;
        private final IntIntMap jointMap;

        private RemapAssetJointsCommand(List<GameObjectAsset.GameObjectJointData> joints, int rootLocalEntityId,
                                        IntIntMap entityMap, IntIntMap jointMap) {
            this.joints = joints; this.rootLocalEntityId = rootLocalEntityId;
            this.entityMap = entityMap; this.jointMap = jointMap;
        }

        @Override public String label() { return "Remap Game Object Joints"; }

        @Override public void redo() {
            ComponentMapper<PhysicsJointComponent> bases = world.getMapper(PhysicsJointComponent.class);
            ComponentMapper<PhysicsGearJointComponent> gears = world.getMapper(PhysicsGearJointComponent.class);
            for (GameObjectAsset.GameObjectJointData source : joints) {
                int target = requiredMapping(jointMap, source.jointLocalId, "joint");
                PhysicsJointComponent base = bases.getSafe(target, null);
                if (base == null) throw new IllegalStateException("Created Game Object joint lost its base component.");
                base.aEid = requiredMapping(entityMap, source.bodyALocalEntityId, "Body A");
                base.bEid = requiredMapping(entityMap, source.bodyBLocalEntityId, "Body B");
                if (source.type == PhysicsJointComponent.TYPE_GEAR) {
                    PhysicsGearJointComponent gear = gears.getSafe(target, null);
                    if (gear == null) throw new IllegalStateException("Created Game Object Gear joint lost its typed component.");
                    gear.joint1Eid = requiredMapping(jointMap, source.gear.jointALocalId, "Gear source");
                    gear.joint2Eid = requiredMapping(jointMap, source.gear.jointBLocalId, "Gear source");
                }
                if (source.type == PhysicsJointComponent.TYPE_PULLEY) {
                    PhysicsPulleyJointComponent pulley = world.getMapper(PhysicsPulleyJointComponent.class)
                            .getSafe(target, null);
                    if (pulley == null) throw new IllegalStateException("Created Game Object Pulley joint lost its typed component.");
                    TransformComponent root = world.getMapper(TransformComponent.class).get(
                            requiredMapping(entityMap, rootLocalEntityId, "root"));
                    float ppm = pixelsPerMeter();
                    float[] a = rootLocalMetersToWorldMeters(root,
                            source.pulley.groundAnchorALocalX, source.pulley.groundAnchorALocalY, ppm);
                    float[] b = rootLocalMetersToWorldMeters(root,
                            source.pulley.groundAnchorBLocalX, source.pulley.groundAnchorBLocalY, ppm);
                    pulley.groundAx = a[0]; pulley.groundAy = a[1]; pulley.groundBx = b[0]; pulley.groundBy = b[1];
                }
                DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
                if (dirty != null) dirty.joint(target, JointDirtyBits.ALL);
            }
        }

        @Override public void undo() { }
    }

    private static int requiredMapping(IntIntMap map, int key, String label) {
        if (!map.containsKey(key)) throw new IllegalStateException("Missing Game Object " + label + " mapping.");
        return map.get(key, -1);
    }

    private static float[] rootLocalMetersToWorldMeters(TransformComponent root,
                                                          float localX, float localY, float ppm) {
        float cos = com.badlogic.gdx.math.MathUtils.cos(root.rotationRad);
        float sin = com.badlogic.gdx.math.MathUtils.sin(root.rotationRad);
        float frameX = root.x + root.originX - cos * root.scaleX * root.originX
                + sin * root.scaleY * root.originY;
        float frameY = root.y + root.originY - sin * root.scaleX * root.originX
                - cos * root.scaleY * root.originY;
        float localWuX = localX * ppm;
        float localWuY = localY * ppm;
        return new float[]{(frameX + cos * root.scaleX * localWuX - sin * root.scaleY * localWuY) / ppm,
                (frameY + sin * root.scaleX * localWuX + cos * root.scaleY * localWuY) / ppm};
    }

    /** Produces a flat diagnostic graph. Scene publication uses {@link #instantiateGameObject}. */
    public EntityGraph loadGameObject(FileHandle file) {
        GameObjectAsset asset = loader.load(file);
        List<EntityGraphEntry> entries = new ArrayList<>(asset.entities.size());
        for (GameObjectAsset.GameObjectEntityData data : asset.entities) {
            entries.add(mapper.toGraphEntry(world, data));
        }
        return new EntityGraph(entries);
    }

    private GameObjectAsset createAsset(EntityGraph graph) {
        IntMap<Integer> stableToSource = new IntMap<>();
        IntMap<Integer> sourceToAsset = new IntMap<>();
        int nextAssetId = 1;
        for (EntityGraphEntry entry : graph.entries()) {
            int entityId = entry.sourceEntityId();
            requireAssetSupported(entityId);
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                    .getSafe(entityId, null);
            if (identity == null || identity.stableId <= 0) {
                throw new IllegalArgumentException("Game Object asset entity " + entityId
                        + " requires a positive scene stable ID for capture remapping.");
            }
            if (stableToSource.containsKey(identity.stableId)) {
                throw new IllegalArgumentException("Game Object capture contains duplicate stableId "
                        + identity.stableId + ".");
            }
            stableToSource.put(identity.stableId, nextAssetId);
            sourceToAsset.put(entityId, nextAssetId);
            nextAssetId++;
        }
        IntArray capturedJoints = requireInternalJointClosure(sourceToAsset);

        GameObjectAsset asset = new GameObjectAsset();
        for (EntityGraphEntry entry : graph.entries()) {
            int entityId = entry.sourceEntityId();
            GameObjectAsset.GameObjectEntityData data =
                    mapper.fromGraphEntry(world, entry, stableToSource);
            data.sourceEntityId = sourceToAsset.get(entityId);
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(entityId, null);
            if (member == null) {
                data.parentSourceEntityId = -1;
                if (!world.getMapper(GameObjectComponent.class).has(entityId)) {
                    throw new IllegalArgumentException("Top-level asset entity " + entityId
                            + " must be a real Game Object root.");
                }
                if (asset.rootSourceEntityId != -1) {
                    throw new IllegalArgumentException("Game Object capture has more than one "
                            + "top-level root.");
                }
                asset.rootSourceEntityId = data.sourceEntityId;
            } else {
                if (!stableToSource.containsKey(member.parentStableId)) {
                    throw new IllegalArgumentException("Game Object asset entity " + entityId
                            + " references parent stableId " + member.parentStableId
                            + " outside the captured hierarchy.");
                }
                data.parentSourceEntityId = stableToSource.get(member.parentStableId);
            }
            asset.entities.add(data);
        }
        appendCapturedJoints(asset, sourceToAsset, capturedJoints);
        loader.validate(asset, null);
        return asset;
    }

    private SelectionClassification unavailable(
            IntArray effectiveSelection, String reason, String actionLabel) {
        return new SelectionClassification(SelectionMode.UNAVAILABLE, effectiveSelection, reason, actionLabel);
    }

    private static IllegalArgumentException unavailableOperation(
            SelectionClassification classification, String verb) {
        String reason = classification.rejection;
        return new IllegalArgumentException(reason != null && !reason.isBlank()
                ? reason : "The selected Game Object cannot be " + verb + ".");
    }

    private IntArray normalizeSelection(IntArray selection) {
        IntArray requested = new IntArray(false, selection != null ? selection.size : 0);
        if (selection == null) return requested;
        IntSet requestedIds = new IntSet(selection.size);
        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (requestedIds.add(entityId)) requested.add(entityId);
        }

        IntMap<Integer> entityByStableId = new IntMap<>();
        com.artemis.utils.IntBag identities = world.getAspectSubscriptionManager().get(
                com.artemis.Aspect.all(PixscapeIdentityComponent.class)).getEntities();
        ComponentMapper<PixscapeIdentityComponent> identityMapper =
                world.getMapper(PixscapeIdentityComponent.class);
        for (int i = 0; i < identities.size(); i++) {
            int entityId = identities.get(i);
            PixscapeIdentityComponent identity = identityMapper.getSafe(entityId, null);
            if (identity != null && identity.stableId > 0) {
                entityByStableId.put(identity.stableId, entityId);
            }
        }

        IntArray effective = new IntArray(false, requested.size);
        ComponentMapper<GameObjectMemberComponent> members =
                world.getMapper(GameObjectMemberComponent.class);
        ComponentMapper<GameObjectComponent> gameObjects = world.getMapper(GameObjectComponent.class);
        for (int i = 0; i < requested.size; i++) {
            int entityId = requested.get(i);
            if (!hasSelectedGameObjectAncestor(entityId, requestedIds, entityByStableId,
                    members, gameObjects)) {
                effective.add(entityId);
            }
        }
        return effective;
    }

    private static boolean hasSelectedGameObjectAncestor(
            int entityId, IntSet selected, IntMap<Integer> entityByStableId,
            ComponentMapper<GameObjectMemberComponent> members,
            ComponentMapper<GameObjectComponent> gameObjects) {
        GameObjectMemberComponent member = members.getSafe(entityId, null);
        IntSet visitedParents = new IntSet();
        while (member != null && visitedParents.add(member.parentStableId)) {
            Integer parent = entityByStableId.get(member.parentStableId);
            if (parent == null) return false;
            if (selected.contains(parent) && gameObjects.has(parent)) return true;
            member = members.getSafe(parent, null);
        }
        return false;
    }

    private void prepareExistingGameObjectSave(int root) {
        requireConvertibleTopLevel(root);
        if (!world.getMapper(GameObjectComponent.class).has(root)) {
            throw new IllegalArgumentException("Select a Game Object root to save as an asset.");
        }
        if (world.getMapper(GameObjectMemberComponent.class).has(root)) {
            throw new IllegalArgumentException("Nested Game Objects cannot be saved as standalone assets.");
        }
        IntArray hierarchy = collectConversionHierarchy(new IntArray(new int[]{root}));
        for (int i = 0; i < hierarchy.size; i++) requireAssetSupported(hierarchy.get(i));
        requireInternalJointClosure(toLocalIds(hierarchy));
        requireConvertiblePhysicsHierarchy(hierarchy);
    }

    private ConversionPlan prepareConversion(IntArray selection, String canonicalId) {
        if (selection == null || selection.size == 0) {
            throw new IllegalArgumentException("Select one or more supported top-level entities.");
        }
        ComponentMapper<GameObjectMemberComponent> members = world.getMapper(GameObjectMemberComponent.class);
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        int layer = -1;
        IntSet selected = new IntSet(selection.size);
        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (!selected.add(entityId)) continue;
            requireConvertibleTopLevel(entityId);
            if (members.has(entityId)) {
                throw new IllegalArgumentException("A Game Object member cannot be converted independently.");
            }
            EntityIndexComponent index = indexes.get(entityId);
            if (layer < 0) layer = index.layerIndex;
            else if (layer != index.layerIndex) {
                throw new IllegalArgumentException("Selected entities must belong to the same Layer.");
            }
            if (identities.get(entityId).stableId <= 0) {
                throw new IllegalArgumentException("Selected entity requires a positive stable identity.");
            }
        }
        LayerLogicalOrderService.LayerOrder order = new LayerLogicalOrderService(world).derive(layer);
        IntArray selectedTopToBottom = new IntArray(false, selected.size);
        int first = -1;
        int last = -1;
        for (int i = 0; i < order.items().size(); i++) {
            int entityId = order.items().get(i).entityId();
            if (selected.contains(entityId)) {
                if (first < 0) first = i;
                last = i;
                selectedTopToBottom.add(entityId);
            }
        }
        if (selectedTopToBottom.size != selected.size) {
            throw new IllegalArgumentException("Selection contains a non-logical scene item.");
        }
        if (last - first + 1 != selectedTopToBottom.size) {
            throw new IllegalArgumentException(
                    "Selected entities must form one contiguous range in Layer order.");
        }
        IntArray conversionHierarchy = collectConversionHierarchy(selectedTopToBottom);
        requireInternalJointClosure(toLocalIds(conversionHierarchy));
        requireConvertiblePhysicsHierarchy(conversionHierarchy);
        Bounds bounds = computeBounds(selectedTopToBottom);
        return new ConversionPlan(order, selectedTopToBottom, bounds.minX, bounds.minY,
                (bounds.maxX - bounds.minX) * .5f, (bounds.maxY - bounds.minY) * .5f,
                canonicalId);
    }

    private GameObjectAsset createConversionAsset(ConversionPlan plan) {
        IntArray hierarchy = collectConversionHierarchy(plan.selectedTopToBottom);
        IntMap<Integer> stableToSource = new IntMap<>();
        IntMap<Integer> entityToSource = new IntMap<>();
        int nextSource = 2;
        for (int i = 0; i < hierarchy.size; i++) {
            int entityId = hierarchy.get(i);
            requireConvertibleSupported(entityId);
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                    .getSafe(entityId, null);
            if (identity == null || identity.stableId <= 0 || stableToSource.containsKey(identity.stableId)) {
                throw new IllegalArgumentException("Game Object conversion has malformed identity data.");
            }
            stableToSource.put(identity.stableId, nextSource);
            entityToSource.put(entityId, nextSource++);
        }
        IntArray capturedJoints = requireInternalJointClosure(entityToSource);
        requireConvertiblePhysicsHierarchy(hierarchy);
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        asset.entities.add(newRootAssetData(plan));
        ComponentMapper<GameObjectMemberComponent> members = world.getMapper(GameObjectMemberComponent.class);
        for (int i = 0; i < hierarchy.size; i++) {
            int entityId = hierarchy.get(i);
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(entityId);
            GameObjectAsset.GameObjectEntityData data = mapper.fromGraphEntry(
                    world, new EntityGraphEntry(entityId, initializer), stableToSource);
            data.sourceEntityId = entityToSource.get(entityId);
            GameObjectMemberComponent member = members.getSafe(entityId, null);
            if (member == null) {
                data.parentSourceEntityId = 1;
                applyLocalTransform(data.transform, plan, world.getMapper(TransformComponent.class).get(entityId),
                        world.getMapper(GameObjectComponent.class).has(entityId));
                data.entityIndex.zIndex = localZ(plan.selectedTopToBottom, entityId);
            } else {
                Integer parentSource = stableToSource.get(member.parentStableId);
                if (parentSource == null) {
                    throw new IllegalArgumentException("Game Object hierarchy has a parent outside conversion.");
                }
                data.parentSourceEntityId = parentSource;
            }
            asset.entities.add(data);
        }
        appendCapturedJoints(asset, entityToSource, capturedJoints);
        loader.validate(asset, null);
        return asset;
    }

    private static GameObjectAsset.GameObjectEntityData newRootAssetData(ConversionPlan plan) {
        GameObjectAsset.GameObjectEntityData root = new GameObjectAsset.GameObjectEntityData();
        root.sourceEntityId = 1;
        root.parentSourceEntityId = -1;
        root.gameObject = new GameObjectAsset.GameObjectData();
        root.transform = new GameObjectAsset.TransformData();
        root.transform.x = plan.rootX; root.transform.y = plan.rootY;
        root.transform.originX = plan.rootOriginX; root.transform.originY = plan.rootOriginY;
        root.transform.scaleX = 1f; root.transform.scaleY = 1f;
        root.entityIndex = new GameObjectAsset.EntityIndexData();
        root.entityIndex.zIndex = 0;
        root.meta = new GameObjectAsset.MetaData(); root.meta.kind = EntityKind.GAME_OBJECT.name();
        root.identity = new GameObjectAsset.IdentityData(); root.identity.name = "Game Object";
        return root;
    }

    private static void applyLocalTransform(GameObjectAsset.TransformData target, ConversionPlan plan,
                                            TransformComponent worldTransform, boolean childIsRoot) {
        TransformComponent root = new TransformComponent();
        root.x = plan.rootX; root.y = plan.rootY; root.originX = plan.rootOriginX;
        root.originY = plan.rootOriginY; root.scaleX = root.scaleY = 1f; root.refreshCaches();
        TransformComponent local = GameObjectTransformMath.worldToLocal(
                root, worldTransform, childIsRoot, new TransformComponent());
        target.x = local.x; target.y = local.y; target.rotationRad = local.rotationRad;
        target.scaleX = local.scaleX; target.scaleY = local.scaleY;
        target.originX = local.originX; target.originY = local.originY;
    }

    private IntArray collectConversionHierarchy(IntArray selectedTopToBottom) {
        IntArray result = new IntArray(false, selectedTopToBottom.size);
        IntSet knownEntities = new IntSet();
        IntSet knownStableIds = new IntSet();
        ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<GameObjectMemberComponent> members = world.getMapper(GameObjectMemberComponent.class);
        for (int i = 0; i < selectedTopToBottom.size; i++) {
            int entityId = selectedTopToBottom.get(i);
            if (knownEntities.add(entityId)) {
                result.add(entityId);
                knownStableIds.add(identities.get(entityId).stableId);
            }
        }
        boolean changed;
        do {
            changed = false;
            com.artemis.utils.IntBag bag = world.getAspectSubscriptionManager().get(
                    com.artemis.Aspect.all(GameObjectMemberComponent.class)).getEntities();
            for (int i = 0; i < bag.size(); i++) {
                int entityId = bag.get(i);
                if (knownEntities.contains(entityId) || !knownStableIds.contains(members.get(entityId).parentStableId)) continue;
                requireConvertibleSupported(entityId);
                PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
                if (identity == null || identity.stableId <= 0) {
                    throw new IllegalArgumentException("Game Object hierarchy has malformed identity data.");
                }
                knownEntities.add(entityId); knownStableIds.add(identity.stableId); result.add(entityId); changed = true;
            }
        } while (changed);
        return result;
    }

    private void requireConvertibleTopLevel(int entityId) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)
                || world.getMapper(LayerComponent.class).has(entityId)
                || !world.getMapper(EntityIndexComponent.class).has(entityId)
                || !world.getMapper(TransformComponent.class).has(entityId)
                || !world.getMapper(PixscapeIdentityComponent.class).has(entityId)) {
            throw new IllegalArgumentException("Selection contains a non-authorable scene entity.");
        }
        requireConvertibleSupported(entityId);
    }

    private Bounds computeBounds(IntArray selectedTopToBottom) {
        Bounds bounds = new Bounds();
        GameObjectGizmoGeometry gizmo = new GameObjectGizmoGeometry(world);
        float[] corners = new float[8];
        for (int i = 0; i < selectedTopToBottom.size; i++) {
            int entityId = selectedTopToBottom.get(i);
            boolean wrote = world.getMapper(GameObjectComponent.class).has(entityId)
                    && gizmo.writeWorldCorners(entityId, 0f, corners);
            if (!wrote) wrote = writeEntityBounds(entityId, corners);
            if (wrote) bounds.include(corners);
            else {
                TransformComponent transform = world.getMapper(TransformComponent.class).get(entityId);
                bounds.include(transform.x, transform.y);
            }
        }
        if (!bounds.found) throw new IllegalArgumentException("Selection has no authored bounds.");
        return bounds;
    }

    private boolean writeEntityBounds(int entityId, float[] out) {
        OrientedBoundsComponent bounds = world.getMapper(OrientedBoundsComponent.class).getSafe(entityId, null);
        if (bounds != null) {
            OrientedBoundsHelper.toCorners(bounds, out);
            return true;
        }
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).getSafe(entityId, null);
        TransformComponent transform = world.getMapper(TransformComponent.class).getSafe(entityId, null);
        if (dimensions == null || transform == null) return false;
        out[0] = AuthoredGeometryTransform.worldX(transform, 0f, 0f);
        out[1] = AuthoredGeometryTransform.worldY(transform, 0f, 0f);
        out[2] = AuthoredGeometryTransform.worldX(transform, dimensions.width, 0f);
        out[3] = AuthoredGeometryTransform.worldY(transform, dimensions.width, 0f);
        out[4] = AuthoredGeometryTransform.worldX(transform, dimensions.width, dimensions.height);
        out[5] = AuthoredGeometryTransform.worldY(transform, dimensions.width, dimensions.height);
        out[6] = AuthoredGeometryTransform.worldX(transform, 0f, dimensions.height);
        out[7] = AuthoredGeometryTransform.worldY(transform, 0f, dimensions.height);
        return true;
    }

    private static int localZ(IntArray topToBottom, int entityId) {
        for (int i = 0; i < topToBottom.size; i++) {
            if (topToBottom.get(i) == entityId) return topToBottom.size - 1 - i;
        }
        throw new IllegalArgumentException("Converted hierarchy has an unknown top-level item.");
    }

    private record ConversionPlan(LayerLogicalOrderService.LayerOrder order, IntArray selectedTopToBottom,
                                  float rootX, float rootY, float rootOriginX, float rootOriginY,
                                  String canonicalId) { }

    private static final class Bounds {
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        private boolean found;
        private void include(float x, float y) {
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); found = true;
        }
        private void include(float[] corners) {
            for (int i = 0; i < 8; i += 2) include(corners[i], corners[i + 1]);
        }
    }

    private static List<GameObjectAsset.GameObjectEntityData> topologicalOrder(
            GameObjectAsset asset) {
        IntMap<GameObjectAsset.GameObjectEntityData> byId = new IntMap<>();
        for (GameObjectAsset.GameObjectEntityData data : asset.entities) {
            byId.put(data.sourceEntityId, data);
        }
        List<GameObjectAsset.GameObjectEntityData> ordered = new ArrayList<>(asset.entities.size());
        appendChildren(asset.rootSourceEntityId, byId, asset.entities, ordered);
        return ordered;
    }

    private static void appendChildren(
            int sourceId, IntMap<GameObjectAsset.GameObjectEntityData> byId,
            List<GameObjectAsset.GameObjectEntityData> all,
            List<GameObjectAsset.GameObjectEntityData> ordered) {
        ordered.add(byId.get(sourceId));
        for (GameObjectAsset.GameObjectEntityData candidate : all) {
            if (candidate.parentSourceEntityId == sourceId) {
                appendChildren(candidate.sourceEntityId, byId, all, ordered);
            }
        }
    }

    private static PropertySet remapProperties(
            PropertySet source, IntIntMap sourceToStable) {
        return PropertyReferenceMapper.remap(source,
                sourceId -> sourceId == -1 ? -1 : sourceToStable.get(sourceId, -1));
    }

    private static IntArray currentCreatedEntityIds(IntIntMap sourceToCreated) {
        IntArray result = new IntArray(false, sourceToCreated.size);
        for (IntIntMap.Entry entry : sourceToCreated) result.add(entry.value);
        return result;
    }

    private void requireAssetSupported(int entityId) {
        if (world.getMapper(TiledLayerComponent.class).has(entityId)) {
            throw unsupported(entityId, "Tiled Maps");
        }
        if (world.getMapper(ParticleEmitterComponent.class).has(entityId)) {
            throw unsupported(entityId, "ParticleEmitter");
        }
        if (world.getMapper(PhysicsJointComponent.class).has(entityId)) {
            throw unsupported(entityId, "Physics joints");
        }
        requireNoSpatialLinkedPhysicsShapes(entityId);
        if (world.getMapper(SpatialBlocksComponent.class).has(entityId)
                || world.getMapper(SpatialShapesComponent.class).has(entityId)) {
            throw unsupported(entityId, "Scene-local Spatial structures");
        }
    }

    /**
     * Discovers all internal Scene joints for the captured Body closure. A joint with exactly one
     * endpoint in the closure would otherwise be silently lost from a reusable asset, so it is
     * rejected before any publication or History mutation.
     */
    private IntArray requireInternalJointClosure(IntMap<Integer> capturedEntityToLocal) {
        IntSet captured = new IntSet(capturedEntityToLocal.size);
        for (IntMap.Entry<Integer> entry : capturedEntityToLocal) captured.add(entry.key);
        IntArray internal = new IntArray(false, 8);
        IntSet internalSet = new IntSet();
        com.artemis.utils.IntBag joints = world.getAspectSubscriptionManager().get(
                Aspect.all(PhysicsJointComponent.class)).getEntities();
        ComponentMapper<PhysicsJointComponent> bases = world.getMapper(PhysicsJointComponent.class);
        for (int i = 0; i < joints.size(); i++) {
            int jointEntityId = joints.get(i);
            PhysicsJointComponent joint = bases.getSafe(jointEntityId, null);
            if (joint == null) continue;
            boolean aInside = captured.contains(joint.aEid);
            boolean bInside = captured.contains(joint.bEid);
            if (aInside != bInside) {
                throw new IllegalArgumentException(
                        "Game Object contains a Physics joint connected to an entity outside the Game Object.");
            }
            if (aInside) {
                internal.add(jointEntityId);
                internalSet.add(jointEntityId);
            }
        }
        ComponentMapper<PhysicsGearJointComponent> gears = world.getMapper(PhysicsGearJointComponent.class);
        for (int i = 0; i < internal.size; i++) {
            int jointEntityId = internal.get(i);
            PhysicsJointComponent base = bases.get(jointEntityId);
            if (base.type != PhysicsJointComponent.TYPE_GEAR) continue;
            PhysicsGearJointComponent gear = gears.getSafe(jointEntityId, null);
            if (gear == null || !internalSet.contains(gear.joint1Eid) || !internalSet.contains(gear.joint2Eid)) {
                throw new IllegalArgumentException(
                        "Game Object Gear joint depends on a joint outside the Game Object.");
            }
        }
        return internal;
    }

    private static IntMap<Integer> toLocalIds(IntArray entities) {
        IntMap<Integer> result = new IntMap<Integer>(entities.size);
        for (int i = 0; i < entities.size; i++) result.put(entities.get(i), i + 1);
        return result;
    }

    private void appendCapturedJoints(GameObjectAsset asset, IntMap<Integer> entityToLocal,
                                      IntArray jointEntities) {
        IntIntMap jointToLocal = new IntIntMap(jointEntities.size);
        for (int i = 0; i < jointEntities.size; i++) jointToLocal.put(jointEntities.get(i), i + 1);
        for (int i = 0; i < jointEntities.size; i++) {
            GameObjectAsset.GameObjectJointData joint = jointMapper.fromScene(world, jointEntities.get(i), i + 1,
                    toIntIntMap(entityToLocal), jointToLocal);
            localizePulleyGroundAnchors(asset, joint);
            asset.joints.add(joint);
        }
    }

    /** Converts current Scene world-meter pulley anchors to the asset root's local meter frame. */
    private void localizePulleyGroundAnchors(GameObjectAsset asset, GameObjectAsset.GameObjectJointData joint) {
        if (joint.type != PhysicsJointComponent.TYPE_PULLEY || joint.pulley == null) return;
        GameObjectAsset.GameObjectEntityData root = null;
        for (GameObjectAsset.GameObjectEntityData entity : asset.entities) {
            if (entity.sourceEntityId == asset.rootSourceEntityId) { root = entity; break; }
        }
        if (root == null || root.transform == null) {
            throw new IllegalStateException("Game Object Pulley capture requires the asset root transform.");
        }
        float ppm = pixelsPerMeter();
        float[] a = worldMetersToRootLocalMeters(root.transform,
                joint.pulley.groundAnchorALocalX, joint.pulley.groundAnchorALocalY, ppm);
        float[] b = worldMetersToRootLocalMeters(root.transform,
                joint.pulley.groundAnchorBLocalX, joint.pulley.groundAnchorBLocalY, ppm);
        joint.pulley.groundAnchorALocalX = a[0]; joint.pulley.groundAnchorALocalY = a[1];
        joint.pulley.groundAnchorBLocalX = b[0]; joint.pulley.groundAnchorBLocalY = b[1];
    }

    private float pixelsPerMeter() {
        if (physicsService == null) throw new IllegalStateException(
                "Game Object Pulley capture requires PhysicsService.");
        float ppm = 1f / physicsService.pxToM(1f);
        if (Float.isNaN(ppm) || Float.isInfinite(ppm) || ppm <= 0f) {
            throw new IllegalStateException("Current scene pixelsPerMeter must be finite and positive.");
        }
        return ppm;
    }

    private static float[] worldMetersToRootLocalMeters(GameObjectAsset.TransformData root,
                                                          float worldX, float worldY, float ppm) {
        float cos = com.badlogic.gdx.math.MathUtils.cos(root.rotationRad);
        float sin = com.badlogic.gdx.math.MathUtils.sin(root.rotationRad);
        float frameX = root.x + root.originX - cos * root.scaleX * root.originX
                + sin * root.scaleY * root.originY;
        float frameY = root.y + root.originY - sin * root.scaleX * root.originX
                - cos * root.scaleY * root.originY;
        float dx = worldX * ppm - frameX;
        float dy = worldY * ppm - frameY;
        float determinant = root.scaleX * root.scaleY;
        return new float[]{(cos * root.scaleY * dx + sin * root.scaleY * dy) / determinant / ppm,
                (-sin * root.scaleX * dx + cos * root.scaleX * dy) / determinant / ppm};
    }

    private static IntIntMap toIntIntMap(IntMap<Integer> values) {
        IntIntMap result = new IntIntMap(values.size);
        for (IntMap.Entry<Integer> entry : values) result.put(entry.key, entry.value);
        return result;
    }

    private void requireNoSpatialLinkedPhysicsShapes(int entityId) {
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class)
                .getSafe(entityId, null);
        if (shapes == null || shapes.shapes == null) return;
        for (int i = 0; i < shapes.shapes.size; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape != null && shape.spatialBlockId > 0) {
                throw new IllegalArgumentException(
                        "Game Object assets cannot contain Physics shapes linked to Scene Spatial blocks (spatialBlockId > 0).");
            }
        }
    }

    private void requireConvertiblePhysicsHierarchy(IntArray hierarchy) {
        IntMap<Integer> entityByStableId = new IntMap<Integer>(hierarchy.size);
        ComponentMapper<PixscapeIdentityComponent> identities =
                world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<GameObjectMemberComponent> members =
                world.getMapper(GameObjectMemberComponent.class);
        ComponentMapper<TransformComponent> transforms =
                world.getMapper(TransformComponent.class);
        for (int i = 0; i < hierarchy.size; i++) {
            int entityId = hierarchy.get(i);
            PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
            if (identity == null || identity.stableId <= 0) {
                throw new IllegalArgumentException("Game Object hierarchy has malformed identity data.");
            }
            entityByStableId.put(identity.stableId, entityId);
        }
        for (int i = 0; i < hierarchy.size; i++) {
            int entityId = hierarchy.get(i);
            if (!world.getMapper(PhysicsBodyComponent.class).has(entityId)) continue;
            GameObjectMemberComponent member = members.getSafe(entityId, null);
            while (member != null) {
                Integer parentEntityId = entityByStableId.get(member.parentStableId);
                TransformComponent parent = parentEntityId != null
                        ? transforms.getSafe(parentEntityId, null) : null;
                if (parent == null || Float.compare(parent.scaleX, 1f) != 0
                        || Float.compare(parent.scaleY, 1f) != 0) {
                    throw new IllegalArgumentException(
                            "Physics in a Game Object hierarchy requires every ancestor scale to be (1,1).");
                }
                member = members.getSafe(parentEntityId, null);
            }
        }
    }

    private void requireConvertibleSupported(int entityId) {
        requireAssetSupported(entityId);
    }

    private static IllegalArgumentException unsupported(int entityId, String domain) {
        return new IllegalArgumentException("Game Object asset entity " + entityId
                + " uses unsupported component domain " + domain + ".");
    }
}
