package games.pixscape.studio.history.commands;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.Initializer;

import java.util.function.Consumer;

/**
 * Creates one supported entity directly as a Game Object child in one history operation.
 */
public final class CreateGameObjectChildCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final IdentityRegistry identities;
    private final long parentHistoryId;
    private final int parentStableId;
    private final String childLabel;
    private final TransformComponent parentBefore;
    private final TransformComponent parentAfter;
    private final boolean promoteParent;
    private final ChildInitializer childInitializer;
    private final CreateEntityCommand createCommand;

    public CreateGameObjectChildCommand(
            World world,
            HistoryIdRegistry historyIds,
            IdentityRegistry identities,
            int parentEntityId,
            Initializer initializer,
            Consumer<Integer> onCreated) {
        if (world == null || historyIds == null || identities == null || initializer == null) {
            throw new IllegalArgumentException(
                    "World, identity registries and child initializer are required.");
        }
        requireParent(world, parentEntityId);
        if (initializer instanceof games.pixscape.studio.history.initializer.TiledMapInitializer
                || initializer instanceof games.pixscape.studio.history.initializer.GenericEntityInitializer generic
                && generic.isTiledStructure()) {
            throw new IllegalArgumentException("Tiled structures require dedicated creation and restoration operations.");
        }
        this.world = world;
        this.historyIds = historyIds;
        this.identities = identities;
        this.parentHistoryId = historyIds.ensureForEntity(parentEntityId);
        this.parentStableId = world.getMapper(PixscapeIdentityComponent.class)
                .get(parentEntityId).stableId;
        this.parentBefore = GameObjectHierarchyCommandSupport.copy(
                world.getMapper(TransformComponent.class).get(parentEntityId));
        this.promoteParent = !world.getMapper(GameObjectComponent.class).has(parentEntityId);
        this.parentAfter = promoteParent
                ? GameObjectHierarchyCommandSupport.promotedParentLocal(world, identities, parentEntityId)
                : parentBefore;
        String label = initializer.label();
        this.childLabel = label != null && !label.isBlank() ? label : "Entity";

        int root = GameObjectHierarchyCommandSupport.topLevelRoot(
                world, identities, parentEntityId);
        int effectiveLayer = world.getMapper(EntityIndexComponent.class).get(root).layerIndex;
        int localZ = nextLocalZ(world, parentStableId);
        this.childInitializer = new ChildInitializer(initializer, parentStableId, effectiveLayer, localZ);
        this.createCommand = new CreateEntityCommand(
                world, historyIds, childInitializer, onCreated);
    }

    @Override
    public String label() {
        return "Create " + childLabel + " in Game Object";
    }

    @Override
    public int additionalActiveLights() {
        return createCommand.additionalActiveLights();
    }

    @Override
    public void redo() {
        int parent = historyIds.entityOfHistoryId(parentHistoryId);
        requireParent(world, parent);
        if (world.getMapper(PixscapeIdentityComponent.class).get(parent).stableId != parentStableId) {
            throw new IllegalStateException("Game Object parent stable identity changed.");
        }
        childInitializer.setCurrentParent(parent);
        if (promoteParent) {
            world.getMapper(GameObjectComponent.class).create(parent);
            applyTransform(parent, parentAfter);
        }
        try {
            createCommand.redo();
        } catch (RuntimeException | Error failure) {
            if (promoteParent) {
                world.getMapper(GameObjectComponent.class).remove(parent);
                applyTransform(parent, parentBefore);
            }
            throw failure;
        }
    }

    @Override
    public void undo() {
        createCommand.undo();
        if (promoteParent) {
            int parent = historyIds.entityOfHistoryId(parentHistoryId);
            requireParent(world, parent);
            world.getMapper(GameObjectComponent.class).remove(parent);
            applyTransform(parent, parentBefore);
        }
    }

    public int getCreatedEntityId() {
        return createCommand.getCreatedEntityId();
    }

    /** Read-only UI preflight; the command repeats validation before mutation. */
    public static String parentRejection(World world, int parentEntityId) {
        try {
            requireParent(world, parentEntityId);
            return null;
        } catch (IllegalArgumentException | IllegalStateException rejected) {
            return rejected.getMessage();
        }
    }

    private static void requireParent(World world, int parentEntityId) {
        GameObjectHierarchyCommandSupport.requireActive(world, parentEntityId, "parent");
        TiledEntityRole.requireObjectParent(world, parentEntityId);
        GameObjectHierarchyCommandSupport.requireCore(world, parentEntityId, "parent");
        GameObjectTransformMath.requirePositiveUniformParentScale(
                world.getMapper(TransformComponent.class).get(parentEntityId));
        GameObjectHierarchyCommandSupport.requireSupportedMember(world, parentEntityId);
        if (world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).has(parentEntityId)) {
            GameObjectTransformMath.requireUnitParentScale(world.getMapper(TransformComponent.class).get(parentEntityId), "Physics");
        }

    }

    private static int nextLocalZ(World world, int parentStableId) {
        IntBag members = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class, EntityIndexComponent.class))
                .getEntities();
        int max = SortKey64.MIN_Z;
        boolean found = false;
        for (int i = 0; i < members.size(); i++) {
            int entityId = members.get(i);
            if (world.getMapper(GameObjectMemberComponent.class)
                    .get(entityId).parentStableId != parentStableId) {
                continue;
            }
            max = Math.max(max, world.getMapper(EntityIndexComponent.class).get(entityId).zIndex);
            found = true;
        }
        if (found && max >= SortKey64.MAX_Z) {
            throw new IllegalArgumentException("Game Object local z range is exhausted.");
        }
        return found ? max + 1 : 0;
    }

    private void applyTransform(int entityId, TransformComponent snapshot) {
        GameObjectHierarchyCommandSupport.apply(
                world.getMapper(TransformComponent.class).get(entityId), snapshot);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.geometry(entityId, GeometryDirty.ALL);
        }
    }

    private final class ChildInitializer implements Initializer {
        private final Initializer delegate;
        private int parentStableId;
        private int effectiveLayer;
        private int localZ;
        private int currentParent = -1;
        private boolean captured;

        private ChildInitializer(
                Initializer delegate,
                int parentStableId,
                int effectiveLayer,
                int localZ) {
            this.delegate = delegate;
            this.parentStableId = parentStableId;
            this.effectiveLayer = effectiveLayer;
            this.localZ = localZ;
        }

        private void setCurrentParent(int currentParent) {
            this.currentParent = currentParent;
        }

        @Override
        public void syncFrom(int sourceEid) {
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(sourceEid, null);
            if (member == null || member.parentStableId != parentStableId) {
                throw new IllegalStateException(
                        "Game Object child membership changed before creation undo.");
            }
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class)
                    .get(sourceEid);
            parentStableId = member.parentStableId;
            effectiveLayer = index.layerIndex;
            localZ = index.zIndex;
            delegate.syncFrom(sourceEid);
            captured = true;
        }

        @Override
        public int activeLightCount() {
            return delegate.activeLightCount();
        }

        @Override
        public void init(int targetEid) {
            delegate.init(targetEid);
            TiledEntityRole.requireGenericMember(world, targetEid);
            GameObjectHierarchyCommandSupport.requireCore(world, targetEid, "child");
            GameObjectHierarchyCommandSupport.requireSupportedMember(world, targetEid);
            if (world.getMapper(GameObjectComponent.class).has(targetEid)) {
                GameObjectTransformMath.requirePositiveUniformParentScale(
                        world.getMapper(TransformComponent.class).get(targetEid));
            }

            TransformComponent transform = world.getMapper(TransformComponent.class).get(targetEid);
            if (!captured) {
                transform.x = 0f;
                transform.y = 0f;
                transform.rotationRad = 0f;
                transform.refreshCaches();
            }
            if (world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).has(targetEid)) {
                GameObjectTransformMath.requireUnitParentScale(world.getMapper(TransformComponent.class).get(currentParent), "Physics");
                GameObjectHierarchyCommandSupport.requirePhysicsAncestry(world, identities, currentParent);
            }
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(targetEid);
            index.layerIndex = effectiveLayer;
            index.zIndex = localZ;
            world.getMapper(GameObjectMemberComponent.class)
                    .create(targetEid).parentStableId = parentStableId;
            if (!captured && SpatialRepresentationInitialization.hasFootprintOwner(world, currentParent)) {
                var representations = new SpatialRepresentationInitialization(world, historyIds);
                representations.prepareEntity(targetEid);
                representations.apply(false);
            }
        }

        @Override
        public String label() {
            return delegate.label();
        }
    }
}
