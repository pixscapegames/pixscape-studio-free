package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.GameObjectHierarchySystem;

public final class GameObjectHierarchyCommandSupport {
    private static final int MAX_DEPTH = 1024;

    private GameObjectHierarchyCommandSupport() {
    }

    static void requireAttachCandidate(
            World world, IdentityRegistry identities, int childEntityId, int parentEntityId) {
        requireActive(world, childEntityId, "child");
        requireActive(world, parentEntityId, "parent");
        if (childEntityId == parentEntityId) {
            throw new IllegalArgumentException("A Game Object cannot contain itself.");
        }
        requireCore(world, childEntityId, "child");
        requireCore(world, parentEntityId, "parent");
        if (!world.getMapper(GameObjectComponent.class).has(parentEntityId)) {
            throw new IllegalArgumentException("Hierarchy parent must be a Game Object root.");
        }
        if (world.getMapper(GameObjectMemberComponent.class).has(childEntityId)) {
            throw new IllegalArgumentException("Child is already a Game Object member.");
        }
        requireSupportedMember(world, childEntityId);
        if (world.getMapper(GameObjectComponent.class).has(childEntityId)) {
            GameObjectTransformMath.requirePositiveUniformParentScale(
                    world.getMapper(TransformComponent.class).get(childEntityId));
        }
        GameObjectTransformMath.requirePositiveUniformParentScale(
                world.getMapper(TransformComponent.class).get(parentEntityId));
        rejectCycle(world, identities, childEntityId, parentEntityId);
        requireProspectivePhysicsAncestry(world, identities, childEntityId, parentEntityId);
    }

    static void requireSupportedMember(World world, int entityId) {
        if (world.getMapper(TiledLayerComponent.class).has(entityId)
                || world.getMapper(ParticleEmitterComponent.class).has(entityId)
                || world.getMapper(SpatialBlocksComponent.class).has(entityId)
                || world.getMapper(SpatialShapesComponent.class).has(entityId)) {
            throw new IllegalArgumentException(
                    "Tiled and Scene-local Spatial structures are not supported as Game Object members.");
        }
    }

    static TransformComponent worldTransform(
            World world, IdentityRegistry identities, int entityId) {
        TransformComponent authored = world.getMapper(TransformComponent.class)
                .getSafe(entityId, null);
        if (authored == null) {
            throw new IllegalArgumentException("Hierarchy entity requires TransformComponent.");
        }
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(entityId, null);
        if (member == null) return copy(authored);
        int parentEntityId = identities.findByStableId(member.parentStableId);
        requireActive(world, parentEntityId, "parent");
        TransformComponent parentWorld = worldTransform(world, identities, parentEntityId, 1);
        return GameObjectTransformMath.localToWorld(
                parentWorld, authored,
                world.getMapper(GameObjectComponent.class).has(entityId),
                new TransformComponent());
    }

    private static TransformComponent worldTransform(
            World world, IdentityRegistry identities, int entityId, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Hierarchy depth limit exceeded.");
        TransformComponent authored = world.getMapper(TransformComponent.class).get(entityId);
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(entityId, null);
        if (member == null) return copy(authored);
        int parent = identities.findByStableId(member.parentStableId);
        requireActive(world, parent, "parent");
        return GameObjectTransformMath.localToWorld(
                worldTransform(world, identities, parent, depth + 1), authored,
                world.getMapper(GameObjectComponent.class).has(entityId),
                new TransformComponent());
    }

    static int topLevelRoot(World world, IdentityRegistry identities, int entityId) {
        int current = entityId;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(current, null);
            if (member == null) return current;
            current = identities.findByStableId(member.parentStableId);
            requireActive(world, current, "parent");
        }
        throw new IllegalArgumentException("Hierarchy depth limit exceeded.");
    }

    static void apply(TransformComponent target, TransformComponent source) {
        target.x = source.x;
        target.y = source.y;
        target.rotationRad = source.rotationRad;
        target.scaleX = source.scaleX;
        target.scaleY = source.scaleY;
        target.originX = source.originX;
        target.originY = source.originY;
        target.refreshCaches();
    }

    static TransformComponent copy(TransformComponent source) {
        TransformComponent copy = new TransformComponent();
        apply(copy, source);
        return copy;
    }

    /** Validates the current Game Object frames above a prospective or existing Physics Body. */
    static void requirePhysicsAncestry(
            World world, IdentityRegistry identities, int entityId) {
        int current = entityId;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(current, null);
            if (member == null) return;
            int parent = identities.findByStableId(member.parentStableId);
            requireActive(world, parent, "Physics ancestor");
            TransformComponent parentTransform = world.getMapper(TransformComponent.class)
                    .getSafe(parent, null);
            try {
                GameObjectTransformMath.requireUnitParentScale(parentTransform, "Physics");
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Physics hierarchy requires every Game Object ancestor to use scale (1,1).", ex);
            }
            current = parent;
        }
        throw new IllegalArgumentException("Hierarchy depth limit exceeded.");
    }

    static boolean subtreeContainsPhysics(
            World world, IdentityRegistry identities, int entityId) {
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        if (hierarchy != null) {
            return hierarchy.containsPhysicsInSubtree(entityId);
        }
        if (world.getMapper(PhysicsBodyComponent.class).has(entityId)) return true;

        com.artemis.utils.IntBag bodies = world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsBodyComponent.class)).getEntities();
        int[] data = bodies.getData();
        for (int i = 0, n = bodies.size(); i < n; i++) {
            int current = data[i];
            for (int depth = 0; depth < MAX_DEPTH; depth++) {
                if (current == entityId) return true;
                GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                        .getSafe(current, null);
                if (member == null) break;
                current = identities.findByStableId(member.parentStableId);
                if (current < 0) break;
            }
        }
        return false;
    }

    /** Returns whether a strict hierarchy descendant, not the entity itself, has Physics. */
    static boolean hasPhysicsDescendant(
            World world, IdentityRegistry identities, int entityId) {
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        if (hierarchy != null) {
            return hierarchy.containsPhysicsInDescendants(entityId);
        }
        if (identities == null) return false;
        com.artemis.utils.IntBag bodies = world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsBodyComponent.class)).getEntities();
        int[] data = bodies.getData();
        for (int i = 0, n = bodies.size(); i < n; i++) {
            int current = data[i];
            for (int depth = 0; depth < MAX_DEPTH; depth++) {
                GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                        .getSafe(current, null);
                if (member == null) break;
                current = identities.findByStableId(member.parentStableId);
                if (current == entityId) return true;
                if (current < 0) break;
            }
        }
        return false;
    }

    public static boolean canApplyScale(
            World world, int entityId, float scaleX, float scaleY) {
        if (world == null || entityId < 0
                || !world.getMapper(GameObjectComponent.class).has(entityId)) {
            return true;
        }
        return !hasPhysicsDescendant(world, IdentityRegistry.boundTo(world), entityId)
                || (scaleX == 1f && scaleY == 1f);
    }

    /** True only for a Game Object frame strictly above a Physics descendant. */
    public static boolean isPhysicsAncestorScaleLocked(World world, int entityId) {
        return world != null && entityId >= 0
                && world.getMapper(GameObjectComponent.class).has(entityId)
                && hasPhysicsDescendant(world, IdentityRegistry.boundTo(world), entityId);
    }

    static void requireScaleAllowedForPhysicsDescendants(
            World world, int entityId, float scaleX, float scaleY) {
        if (!canApplyScale(world, entityId, scaleX, scaleY)) {
            throw new IllegalArgumentException(
                    "A Game Object ancestor of Physics must keep scale (1,1).");
        }
    }

    private static void requireProspectivePhysicsAncestry(
            World world, IdentityRegistry identities, int childEntityId, int parentEntityId) {
        if (!subtreeContainsPhysics(world, identities, childEntityId)) return;
        int current = parentEntityId;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            TransformComponent transform = world.getMapper(TransformComponent.class).get(current);
            try {
                GameObjectTransformMath.requireUnitParentScale(transform, "Physics");
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Cannot reparent Physics under a non-unit Game Object ancestor.", ex);
            }
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(current, null);
            if (member == null) return;
            current = identities.findByStableId(member.parentStableId);
            requireActive(world, current, "prospective Physics ancestor");
        }
        throw new IllegalArgumentException("Hierarchy depth limit exceeded.");
    }

    private static void rejectCycle(
            World world, IdentityRegistry identities, int childEntityId, int parentEntityId) {
        int current = parentEntityId;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            if (current == childEntityId) {
                throw new IllegalArgumentException("Game Object hierarchy cycle rejected.");
            }
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(current, null);
            if (member == null) return;
            current = identities.findByStableId(member.parentStableId);
            requireActive(world, current, "ancestor");
        }
        throw new IllegalArgumentException("Hierarchy depth limit exceeded.");
    }

    static PixscapeIdentityComponent requireCore(World world, int entityId, String role) {
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                .getSafe(entityId, null);
        if (identity == null || identity.stableId <= 0
                || !world.getMapper(TransformComponent.class).has(entityId)
                || !world.getMapper(EntityIndexComponent.class).has(entityId)) {
            throw new IllegalArgumentException(
                    "Game Object " + role + " requires stable identity, Transform and EntityIndex.");
        }
        return identity;
    }

    static void requireActive(World world, int entityId, String role) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            throw new IllegalArgumentException("Game Object " + role + " is not active.");
        }
    }
}
