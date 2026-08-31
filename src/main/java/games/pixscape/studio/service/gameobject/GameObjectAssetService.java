package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
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
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.Command;
import games.pixscape.studio.history.commands.CompositeCommand;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.commands.ReorderLogicalLayerCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;

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
    private final GameObjectAssetLoader loader = new GameObjectAssetLoader();
    private final GameObjectEntityDataMapper mapper = new GameObjectEntityDataMapper();

    public GameObjectAssetService(World world) {
        this(world, null, null, null, null);
    }

    public GameObjectAssetService(
            World world, HistoryManager historyManager,
            IdentityRegistry identityRegistry, IntConsumer onCreatedEntity,
            IntConsumer rootSelection) {
        if (world == null) throw new IllegalArgumentException("World is required.");
        this.world = world;
        this.historyManager = historyManager;
        this.identityRegistry = identityRegistry;
        this.onCreatedEntity = onCreatedEntity;
        this.rootSelection = rootSelection;
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
        PropertySet result = new PropertySet(source.size());
        com.badlogic.gdx.utils.Array<String> names = new com.badlogic.gdx.utils.Array<>();
        source.copyNamesTo(names);
        for (String name : names) {
            PropertyValue value = source.valueCopy(name);
            if (value.type() == PropertyType.OBJECT) {
                int sourceId = value.asObjectStableId();
                result.putObjectStableId(name,
                        sourceId == -1 ? -1 : sourceToStable.get(sourceId, -1));
            } else if (value.type() == PropertyType.CLASS) {
                result.putClass(name, value.className(),
                        remapProperties(value.classPropertiesCopy(), sourceToStable));
            } else {
                result.put(name, value);
            }
        }
        return result;
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
