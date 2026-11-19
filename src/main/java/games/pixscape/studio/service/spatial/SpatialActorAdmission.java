package games.pixscape.studio.service.spatial;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.SpatialScenePolicy;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.service.entitygraph.EntityGraph;

/** Cold editing admission. Dormant representations do not make spatial actors. */
public final class SpatialActorAdmission {
    private SpatialActorAdmission() {
    }

    public static String layerRejection(World world, int layer) {
        int actorLayer = SpatialScenePolicy.actorLayerIndex(world,
                world.getAspectSubscriptionManager().get(Aspect.all(LayerComponent.class)).getEntities());
        return layer >= 0 && layer == actorLayer ? null
                : "Spatial actors require the Spatial actor layer. Choose that layer before pasting or moving this object.";
    }

    public static void requireLayer(World world, int layer) {
        String rejection = layerRejection(world, layer);
        if (rejection != null) {
            throw new IllegalArgumentException(rejection);
        }
    }

    public static void requireGraphDestination(World world, EntityGraph graph, int layer, EditorDocumentType documentType) {
        if (documentType == EditorDocumentType.GAME_OBJECT) {
            return;
        }
        for (var entry : graph.entries()) {
            if (entry.initializer().toSnapshotData(entry.sourceEntityId()).spatialFootprint != null) {
                requireLayer(world, layer);
                return;
            }
        }
    }

    public static void requireAssetDestination(World world, GameObjectAsset asset, int layer, EditorDocumentType documentType) {
        if (documentType == EditorDocumentType.GAME_OBJECT) {
            return;
        }
        for (var entity : asset.entities) {
            if (entity.spatialFootprint != null) {
                requireLayer(world, layer);
                return;
            }
        }
    }

    public static boolean hasFootprintOwner(World world, int entity) {
        var footprints = world.getMapper(SpatialFootprintComponent.class);
        var members = world.getMapper(GameObjectMemberComponent.class);
        var identities = IdentityRegistry.boundTo(world);
        for (int depth = 0; entity >= 0 && depth < 1024; depth++) {
            if (footprints.has(entity)) {
                return true;
            }
            var member = members.getSafe(entity, null);
            if (member == null) {
                return false;
            }
            entity = identities == null ? -1 : identities.findByStableId(member.parentStableId);
        }
        return false;
    }

    /** Evaluate ownership after reparenting: the old parent's footprint is irrelevant. */
    public static void requireMoveDestination(World world, int root, int newParent, int layer,
                                             IntMap<IntArray> children) {
        requireMoveDestination(world, root, newParent, layer, children, EditorDocumentType.SCENE);
    }

    public static void requireMoveDestination(World world, int root, int newParent, int layer,
                                             IntMap<IntArray> children, EditorDocumentType documentType) {
        if (documentType == EditorDocumentType.GAME_OBJECT) {
            return;
        }
        if (hasFootprintOwner(world, newParent)) {
            requireLayer(world, layer);
            return;
        }
        IntArray pending = IntArray.with(root);
        IntSet visited = new IntSet();
        var identities = world.getMapper(PixscapeIdentityComponent.class);
        var footprints = world.getMapper(SpatialFootprintComponent.class);
        while (pending.size > 0) {
            int entity = pending.pop();
            if (!visited.add(entity)) {
                throw new IllegalArgumentException("Game Object hierarchy contains a cycle.");
            }
            if (footprints.has(entity)) {
                requireLayer(world, layer);
                return;
            }
            var identity = identities.getSafe(entity, null);
            var descendants = identity == null ? null : children.get(identity.stableId);
            if (descendants != null) {
                pending.addAll(descendants);
            }
        }
    }
}
