package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.helper.AuthoredGeometryTransform;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.Initializer;

import java.util.function.Consumer;

/** Creates one supported entity directly as a Game Object child in one history operation. */
public final class CreateGameObjectChildCommand implements Command {
    private final World world;
    private final HistoryIdRegistry historyIds;
    private final IdentityRegistry identities;
    private final long parentHistoryId;
    private final int parentStableId;
    private final String childLabel;
    private final TransformComponent parentBefore;
    private TransformComponent parentAfter;
    private boolean pivotInitialized;
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
        this.world = world;
        this.historyIds = historyIds;
        this.identities = identities;
        this.parentHistoryId = historyIds.ensureForEntity(parentEntityId);
        this.parentStableId = world.getMapper(PixscapeIdentityComponent.class)
                .get(parentEntityId).stableId;
        this.parentBefore = GameObjectHierarchyCommandSupport.copy(
                world.getMapper(TransformComponent.class).get(parentEntityId));
        String label = initializer.label();
        this.childLabel = label != null && !label.isBlank() ? label : "Entity";

        int root = GameObjectHierarchyCommandSupport.topLevelRoot(
                world, identities, parentEntityId);
        int effectiveLayer = world.getMapper(EntityIndexComponent.class).get(root).layerIndex;
        int localZ = nextLocalZ(world, parentStableId);
        GameObjectComponent parentGameObject = world.getMapper(GameObjectComponent.class)
                .get(parentEntityId);
        boolean emptySceneOnlyParent = (parentGameObject.sourceAssetId == null
                || parentGameObject.sourceAssetId.isBlank())
                && !hasDirectChildren(world, parentStableId);
        this.childInitializer = new ChildInitializer(
                initializer, parentStableId, effectiveLayer, localZ,
                emptySceneOnlyParent);
        this.createCommand = new CreateEntityCommand(
                world, historyIds, childInitializer, onCreated);
    }

    @Override
    public String label() {
        return "Create " + childLabel + " in Game Object";
    }

    @Override
    public void redo() {
        int parent = historyIds.entityOfHistoryId(parentHistoryId);
        requireParent(world, parent);
        if (world.getMapper(PixscapeIdentityComponent.class).get(parent).stableId != parentStableId) {
            throw new IllegalStateException("Game Object parent stable identity changed.");
        }
        childInitializer.setCurrentParent(parent);
        if (pivotInitialized) {
            applyTransform(parent, parentAfter);
        }
        try {
            createCommand.redo();
        } catch (RuntimeException | Error failure) {
            if (pivotInitialized) applyTransform(parent, parentBefore);
            throw failure;
        }
    }

    @Override
    public void undo() {
        createCommand.undo();
        if (pivotInitialized) {
            int parent = historyIds.entityOfHistoryId(parentHistoryId);
            requireParent(world, parent);
            applyTransform(parent, parentBefore);
        }
    }

    public int getCreatedEntityId() {
        return createCommand.getCreatedEntityId();
    }

    private static void requireParent(World world, int parentEntityId) {
        GameObjectHierarchyCommandSupport.requireActive(world, parentEntityId, "parent");
        GameObjectHierarchyCommandSupport.requireCore(world, parentEntityId, "parent");
        if (!world.getMapper(GameObjectComponent.class).has(parentEntityId)) {
            throw new IllegalArgumentException("Hierarchy parent must be a real Game Object root.");
        }
        GameObjectTransformMath.requirePositiveUniformParentScale(
                world.getMapper(TransformComponent.class).get(parentEntityId));
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

    private static boolean hasDirectChildren(World world, int parentStableId) {
        IntBag members = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class)).getEntities();
        for (int i = 0; i < members.size(); i++) {
            if (world.getMapper(GameObjectMemberComponent.class)
                    .get(members.get(i)).parentStableId == parentStableId) {
                return true;
            }
        }
        return false;
    }

    private void initializePivotFromFirstVisualChild(int parentEntityId, int childEntityId) {
        TransformComponent childWorld = GameObjectHierarchyCommandSupport.copy(
                world.getMapper(TransformComponent.class).get(childEntityId));
        GameObjectMemberComponent parentMember = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(parentEntityId, null);
        TransformComponent grandParentWorld = null;
        if (parentMember != null) {
            int grandParent = identities.findByStableId(parentMember.parentStableId);
            GameObjectHierarchyCommandSupport.requireActive(world, grandParent, "grandparent");
            grandParentWorld = GameObjectHierarchyCommandSupport.worldTransform(
                    world, identities, grandParent);
        }

        VisualBounds bounds = visualBounds(
                world, childEntityId, childWorld, grandParentWorld);
        if (bounds == null) return;

        TransformComponent authoredParentAfter = GameObjectHierarchyCommandSupport.copy(
                world.getMapper(TransformComponent.class).get(parentEntityId));
        authoredParentAfter.x = bounds.minX;
        authoredParentAfter.y = bounds.minY;
        authoredParentAfter.originX = (bounds.maxX - bounds.minX) * 0.5f;
        authoredParentAfter.originY = (bounds.maxY - bounds.minY) * 0.5f;
        authoredParentAfter.refreshCaches();

        TransformComponent parentWorldAfter = parentMember == null
                ? GameObjectHierarchyCommandSupport.copy(authoredParentAfter)
                : GameObjectTransformMath.localToWorld(
                        grandParentWorld, authoredParentAfter, true,
                        new TransformComponent());

        TransformComponent childLocal = GameObjectTransformMath.worldToLocal(
                parentWorldAfter, childWorld, new TransformComponent());
        applyTransform(parentEntityId, authoredParentAfter);
        applyTransform(childEntityId, childLocal);
        parentAfter = GameObjectHierarchyCommandSupport.copy(authoredParentAfter);
        pivotInitialized = true;
    }

    private static VisualBounds visualBounds(
            World world, int entityId, TransformComponent transform,
            TransformComponent coordinateParentWorld) {
        boolean visual = world.getMapper(TextureRegionComponent.class).has(entityId)
                || world.getMapper(PointLightComponent.class).has(entityId)
                || world.getMapper(ConeLightComponent.class).has(entityId);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class)
                .getSafe(entityId, null);
        if (!visual || dimensions == null) return null;
        VisualBounds bounds = new VisualBounds();
        includeVisualCorner(bounds, transform, coordinateParentWorld, 0f, 0f);
        includeVisualCorner(bounds, transform, coordinateParentWorld, dimensions.width, 0f);
        includeVisualCorner(
                bounds, transform, coordinateParentWorld, dimensions.width, dimensions.height);
        includeVisualCorner(bounds, transform, coordinateParentWorld, 0f, dimensions.height);
        return bounds;
    }

    private static void includeVisualCorner(
            VisualBounds bounds,
            TransformComponent visualTransform,
            TransformComponent coordinateParentWorld,
            float localX,
            float localY) {
        float x = AuthoredGeometryTransform.worldX(visualTransform, localX, localY);
        float y = AuthoredGeometryTransform.worldY(visualTransform, localX, localY);
        if (coordinateParentWorld != null) {
            float p00 = coordinateParentWorld.cos * coordinateParentWorld.scaleX;
            float p01 = -coordinateParentWorld.sin * coordinateParentWorld.scaleY;
            float p10 = coordinateParentWorld.sin * coordinateParentWorld.scaleX;
            float p11 = coordinateParentWorld.cos * coordinateParentWorld.scaleY;
            float frameX = coordinateParentWorld.x + coordinateParentWorld.originX
                    - p00 * coordinateParentWorld.originX
                    - p01 * coordinateParentWorld.originY;
            float frameY = coordinateParentWorld.y + coordinateParentWorld.originY
                    - p10 * coordinateParentWorld.originX
                    - p11 * coordinateParentWorld.originY;
            float determinant = p00 * p11 - p01 * p10;
            float dx = x - frameX;
            float dy = y - frameY;
            x = (p11 * dx - p01 * dy) / determinant;
            y = (-p10 * dx + p00 * dy) / determinant;
        }
        bounds.include(x, y);
    }

    private static final class VisualBounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        private void include(float x, float y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
    }

    private void applyTransform(int entityId, TransformComponent snapshot) {
        GameObjectHierarchyCommandSupport.apply(
                world.getMapper(TransformComponent.class).get(entityId), snapshot);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.geometry(entityId, GeometryDirty.ALL);
    }

    private final class ChildInitializer implements Initializer {
        private final Initializer delegate;
        private final boolean firstChildPivotCandidate;
        private int parentStableId;
        private int effectiveLayer;
        private int localZ;
        private int currentParent = -1;
        private boolean captured;

        private ChildInitializer(
                Initializer delegate,
                int parentStableId,
                int effectiveLayer,
                int localZ,
                boolean firstChildPivotCandidate) {
            this.delegate = delegate;
            this.parentStableId = parentStableId;
            this.effectiveLayer = effectiveLayer;
            this.localZ = localZ;
            this.firstChildPivotCandidate = firstChildPivotCandidate;
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
        public void init(int targetEid) {
            delegate.init(targetEid);
            GameObjectHierarchyCommandSupport.requireCore(world, targetEid, "child");
            GameObjectHierarchyCommandSupport.requireSupportedMember(world, targetEid);
            if (world.getMapper(GameObjectComponent.class).has(targetEid)) {
                GameObjectTransformMath.requirePositiveUniformParentScale(
                        world.getMapper(TransformComponent.class).get(targetEid));
            }

            TransformComponent transform = world.getMapper(TransformComponent.class).get(targetEid);
            if (!captured && firstChildPivotCandidate) {
                initializePivotFromFirstVisualChild(currentParent, targetEid);
            }
            if (!captured && !pivotInitialized) {
                transform.x = 0f;
                transform.y = 0f;
                transform.rotationRad = 0f;
                transform.refreshCaches();
            }
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(targetEid);
            index.layerIndex = effectiveLayer;
            index.zIndex = localZ;
            world.getMapper(GameObjectMemberComponent.class)
                    .create(targetEid).parentStableId = parentStableId;
        }

        @Override
        public String label() {
            return delegate.label();
        }
    }
}
