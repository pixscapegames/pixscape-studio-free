package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntMap;
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
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;

import java.util.ArrayList;
import java.util.List;

/** Studio publication and diagnostic-reading boundary for {@code .gameobject} assets. */
public final class GameObjectAssetService {
    private final World world;
    private final GameObjectAssetLoader loader = new GameObjectAssetLoader();
    private final GameObjectEntityDataMapper mapper = new GameObjectEntityDataMapper();

    public GameObjectAssetService(World world) {
        if (world == null) throw new IllegalArgumentException("World is required.");
        this.world = world;
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
     * Produces a flat diagnostic/preview graph only. Real scene hierarchy instantiation belongs
     * to Stage 5B and must not use this method as a publication path.
     */
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
