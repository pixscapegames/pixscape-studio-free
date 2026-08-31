package games.pixscape.studio.service.gameobject;

import com.artemis.ComponentMapper;
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
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.gameobject.GameObjectAssetId;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
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
import java.util.function.IntConsumer;

/** Studio publication and diagnostic-reading boundary for {@code .gameobject} assets. */
public final class GameObjectAssetService {
    private final World world;
    private final HistoryManager historyManager;
    private final IdentityRegistry identityRegistry;
    private final IntConsumer onCreatedEntity;
    private final IntConsumer rootSelection;
    private final SelectionService selectionService;
    private final GameObjectAssetLoader loader = new GameObjectAssetLoader();
    private final GameObjectEntityDataMapper mapper = new GameObjectEntityDataMapper();

    public GameObjectAssetService(World world) {
        this(world, null, null, null, null, null);
    }

    public GameObjectAssetService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, IntConsumer onCreatedEntity,
            IntConsumer rootSelection) {
        this(world, historyManager, identityRegistry, onCreatedEntity, rootSelection, null);
    }

    public GameObjectAssetService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, IntConsumer onCreatedEntity,
            IntConsumer rootSelection, SelectionService selectionService) {
        if (world == null) throw new IllegalArgumentException("World is required.");
        this.world = world;
        this.historyManager = historyManager;
        this.identityRegistry = identityRegistry;
        this.onCreatedEntity = onCreatedEntity;
        this.rootSelection = rootSelection;
        this.selectionService = selectionService;
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

    /** Cheap command-boundary preflight used only to enable the conversion action. */
    public boolean canConvertSelectionToGameObject(IntArray selection) {
        try {
            prepareConversion(selection, "gameobjects/selection.gameobject");
            return true;
        } catch (RuntimeException ignored) {
            return false;
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
        ConversionPlan plan = prepareConversion(selectionService.getSelectionSnapshot(), canonicalId);
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

    /** Instantiates one validated asset as a real hierarchy in one atomic History command. */
    public EntityGraphInstantiationResult instantiateGameObject(
            FileHandle file, String logicalAssetId, int destinationLayer,
            float rootWorldX, float rootWorldY) {
        if (historyManager == null || identityRegistry == null) {
            throw new IllegalStateException("Game Object instantiation dependencies are not configured.");
        }
        GameObjectAsset asset = loader.load(file);
        String canonicalId = GameObjectAssetId.normalize(logicalAssetId);
        IntIntMap sourceToStable = new IntIntMap(asset.entities.size());
        for (GameObjectAsset.GameObjectEntityData data : asset.entities) {
            sourceToStable.put(data.sourceEntityId, identityRegistry.allocateStableId());
        }

        List<GameObjectAsset.GameObjectEntityData> ordered = topologicalOrder(asset);
        IntArray createdIds = new IntArray(false, ordered.size());
        IntIntMap sourceToCreated = new IntIntMap(ordered.size());
        List<Command> commands = new ArrayList<>(ordered.size() + 1);
        for (GameObjectAsset.GameObjectEntityData data : ordered) {
            EntityGraphEntry entry = mapper.toGraphEntry(world, data);
            GenericEntityInitializer generic = entry.initializer();
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
            requireSupported(entityId);
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
        loader.validate(asset, null);
        return asset;
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
            requireSupported(entityId);
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                    .getSafe(entityId, null);
            if (identity == null || identity.stableId <= 0 || stableToSource.containsKey(identity.stableId)) {
                throw new IllegalArgumentException("Game Object conversion has malformed identity data.");
            }
            stableToSource.put(identity.stableId, nextSource);
            entityToSource.put(entityId, nextSource++);
        }
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
                requireSupported(entityId);
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
        requireSupported(entityId);
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

    private void requireSupported(int entityId) {
        if (world.getMapper(TiledLayerComponent.class).has(entityId)) {
            throw unsupported(entityId, "Tiled Maps");
        }
        if (world.getMapper(ParticleEmitterComponent.class).has(entityId)) {
            throw unsupported(entityId, "ParticleEmitter");
        }
        if (world.getMapper(PhysicsBodyComponent.class).has(entityId)
                || world.getMapper(PhysicsShapesComponent.class).has(entityId)
                || world.getMapper(PhysicsJointComponent.class).has(entityId)) {
            throw unsupported(entityId, "Physics");
        }
        if (world.getMapper(SpatialHeightComponent.class).has(entityId)
                || world.getMapper(SpatialBlocksComponent.class).has(entityId)
                || world.getMapper(SpatialShapesComponent.class).has(entityId)) {
            throw unsupported(entityId, "Spatial");
        }
    }

    private static IllegalArgumentException unsupported(int entityId, String domain) {
        return new IllegalArgumentException("Game Object asset entity " + entityId
                + " uses unsupported component domain " + domain + ".");
    }
}
