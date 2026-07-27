package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.prefab.PrefabAsset;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;

/** Central scope boundary shared by actor prefabs and clipboard entity graphs. */
public final class ActorPrefabSpatialScopeGuard {
    public static final String MESSAGE =
            "Actor prefabs and clipboard data do not support tiled spatial blocks or "
                    + "linked block physics.";

    private ActorPrefabSpatialScopeGuard() {
    }

    public static void requireSupportedClosure(World world, IntArray entityIds) {
        if (world == null) throw new IllegalArgumentException("world must not be null");
        if (entityIds == null) {
            throw new IllegalArgumentException("entityIds must not be null");
        }

        ComponentMapper<SpatialBlocksComponent> blocks =
                world.getMapper(SpatialBlocksComponent.class);
        ComponentMapper<BlockPhysicsBindingsComponent> bindings =
                world.getMapper(BlockPhysicsBindingsComponent.class);
        ComponentMapper<PhysicsShapesComponent> shapes =
                world.getMapper(PhysicsShapesComponent.class);
        for (int i = 0; i < entityIds.size; i++) {
            int entityId = entityIds.get(i);
            if (blocks.has(entityId) || bindings.has(entityId)) {
                throw unsupported();
            }
            PhysicsShapesComponent physicsShapes =
                    shapes.getSafe(entityId, null);
            if (physicsShapes != null
                    && containsLinkedShape(physicsShapes.shapes)) {
                throw unsupported();
            }
        }
    }

    public static void requireSupportedGraph(EntityGraph graph) {
        if (graph == null) throw new IllegalArgumentException("graph must not be null");
        for (EntityGraphEntry entry : graph.entries()) {
            GenericEntitySnapshotData snapshot =
                    entry.initializer().toSnapshotData(entry.sourceEntityId());
            if (containsLinkedShape(snapshot.shapes)) {
                throw unsupported();
            }
        }
    }

    public static void requireSupportedPrefab(PrefabAsset asset) {
        if (asset == null) throw new IllegalArgumentException("asset must not be null");
        for (PrefabAsset.PrefabEntityData entity : asset.entities) {
            if (entity != null && containsLinkedShape(entity.physicsShapes)) {
                throw unsupported();
            }
        }
    }

    private static boolean containsLinkedShape(
            com.badlogic.gdx.utils.Array<PhysicsShapeData> shapes) {
        if (shapes == null) return false;
        for (PhysicsShapeData shape : shapes) {
            if (shape != null && shape.directGeometry == null) {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException(MESSAGE);
    }
}
