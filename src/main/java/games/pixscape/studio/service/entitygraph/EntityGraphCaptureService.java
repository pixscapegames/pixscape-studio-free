package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.IntMap;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.service.ClipboardPhysicsJointGraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EntityGraphCaptureService {
    private final World world;
    private final ComponentMapper<TransformComponent> mTransform;
    private final ComponentMapper<EntityIndexComponent> mEntityIndex;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final ComponentMapper<GameObjectComponent> mGameObject;
    private final ComponentMapper<GameObjectMemberComponent> mGameObjectMember;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;

    public EntityGraphCaptureService(World world) {
        this.world = world;
        this.mTransform = world.getMapper(TransformComponent.class);
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.mGameObject = world.getMapper(GameObjectComponent.class);
        this.mGameObjectMember = world.getMapper(GameObjectMemberComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
    }

    public EntityGraph capture(IntArray selection) {
        IntArray normalized = new ClipboardSelectionNormalizer(world).normalize(selection);
        return captureNormalizedClipboard(normalized);
    }

    /** Captures one complete real Game Object hierarchy for asset publication. */
    public EntityGraph captureForGameObject(IntArray selection) {
        IntArray hierarchy = collectGameObjectHierarchy(selection);
        if (hierarchy.size == 0) return EntityGraph.empty();
        List<EntityGraphEntry> entries = new ArrayList<>(hierarchy.size);
        for (int i = 0; i < hierarchy.size; i++) {
            int entityId = hierarchy.get(i);
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(entityId);
            entries.add(new EntityGraphEntry(entityId, initializer));
        }
        return new EntityGraph(entries);
    }

    /** Captures a V1 clipboard selection with graph-local hierarchy ownership. */
    public EntityGraph captureGameObjectClipboard(IntArray selection) {
        IntArray roots = new ClipboardSelectionNormalizer(world).normalize(selection);
        return captureNormalizedClipboard(roots);
    }

    /** Captures already-normalized V1 clipboard roots without changing Scene state. */
    public EntityGraph captureNormalizedClipboard(IntArray roots) {
        if (roots.size == 0) return EntityGraph.empty();
        if (!containsGameObjectRoot(roots)) {
            return captureSupportedSelection(collectSupportedSelection(roots));
        }
        IntArray captureRoots = augmentStandalonePhysicsRoots(roots);
        IntArray entities = new IntArray(false, captureRoots.size);
        IntArray parents = new IntArray(false, captureRoots.size);
        IntSet knownStableIds = new IntSet();
        IntMap<IntArray> childrenByParentStableId = collectChildrenByParentStableId();
        for (int i = 0; i < captureRoots.size; i++) {
            if (!mGameObject.has(captureRoots.get(i)) && !isCaptureSupported(captureRoots.get(i))) {
                throw new IllegalArgumentException(
                        "Clipboard selection contains an unsupported standalone entity.");
            }
            collectClipboardSubtree(captureRoots.get(i), -1, entities, parents, knownStableIds,
                    childrenByParentStableId);
        }
        IntMap<Integer> stableToSource = new IntMap<>();
        for (int i = 0; i < entities.size; i++) stableToSource.put(mIdentity.get(entities.get(i)).stableId, i + 1);
        List<EntityGraphEntry> entries = new ArrayList<>(entities.size);
        for (int i = 0; i < entities.size; i++) {
            int entityId = entities.get(i);
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(entityId);
            if (parents.get(i) == -1 && mGameObjectMember.has(entityId)) normalizeNestedRootToWorldPose(entityId, initializer);
            CustomPropertiesComponent properties = world.getMapper(CustomPropertiesComponent.class).getSafe(entityId, null);
            GameObjectComponent gameObject = mGameObject.getSafe(entityId, null);
            entries.add(new EntityGraphEntry(
                    i + 1,
                    parents.get(i),
                    gameObject != null,
                    gameObject != null ? gameObject.sourceAssetId : "",
                    initializer,
                    ClipboardPropertyReferenceNormalizer.normalize(properties != null ? properties.properties : null, stableToSource)));
        }
        return new EntityGraph(entries);
    }

    /**
     * Preserves ordinary clipboard joint augmentation for standalone roots in a mixed
     * selection. Game Object roots and their descendants are deliberately excluded:
     * Physics remains a standalone clipboard domain in V1.
     */
    private IntArray augmentStandalonePhysicsRoots(IntArray roots) {
        IntArray standaloneRoots = new IntArray(false, roots.size);
        for (int i = 0; i < roots.size; i++) {
            int entityId = roots.get(i);
            if (!mGameObject.has(entityId)) standaloneRoots.add(entityId);
        }
        IntArray augmentedStandalone = ClipboardPhysicsJointGraph.filterCopyableSelection(
                world, standaloneRoots);
        IntSet included = new IntSet();
        IntArray result = new IntArray(false, roots.size + augmentedStandalone.size);
        for (int i = 0; i < roots.size; i++) {
            int entityId = roots.get(i);
            if (included.add(entityId)) result.add(entityId);
        }
        for (int i = 0; i < augmentedStandalone.size; i++) {
            int entityId = augmentedStandalone.get(i);
            if (included.add(entityId)) result.add(entityId);
        }
        return result;
    }

    private boolean containsGameObjectRoot(IntArray roots) {
        for (int i = 0; i < roots.size; i++) {
            if (mGameObject.has(roots.get(i))) return true;
        }
        return false;
    }

    private IntMap<IntArray> collectChildrenByParentStableId() {
        IntMap<IntArray> childrenByParentStableId = new IntMap<>();
        IntBag members = world.getAspectSubscriptionManager().get(
                Aspect.all(GameObjectMemberComponent.class)).getEntities();
        for (int i = 0; i < members.size(); i++) {
            int child = members.get(i);
            GameObjectMemberComponent member = mGameObjectMember.get(child);
            PixscapeIdentityComponent identity = mIdentity.getSafe(child, null);
            if (member == null || identity == null || identity.stableId <= 0) continue;
            IntArray children = childrenByParentStableId.get(member.parentStableId);
            if (children == null) {
                children = new IntArray(false, 1);
                childrenByParentStableId.put(member.parentStableId, children);
            }
            children.add(child);
        }
        for (IntMap.Entry<IntArray> entry : childrenByParentStableId.entries()) {
            sortChildrenByStableId(entry.value);
        }
        return childrenByParentStableId;
    }

    private void sortChildrenByStableId(IntArray children) {
        long[] order = new long[children.size];
        for (int i = 0; i < children.size; i++) {
            int entityId = children.get(i);
            order[i] = ((long) mIdentity.get(entityId).stableId << 32)
                    | (entityId & 0xffffffffL);
        }
        Arrays.sort(order);
        for (int i = 0; i < children.size; i++) children.set(i, (int) order[i]);
    }

    private void collectClipboardSubtree(int entityId, int parentSourceId,
                                         IntArray entities, IntArray parents, IntSet stableIds,
                                         IntMap<IntArray> childrenByParentStableId) {
        if (!world.getEntityManager().isActive(entityId)) throw new IllegalArgumentException("Clipboard entity is inactive.");
        PixscapeIdentityComponent identity = mIdentity.getSafe(entityId, null);
        if (identity == null || identity.stableId <= 0) {
            throw new IllegalArgumentException("Clipboard entity requires a stable identity.");
        }
        if (!stableIds.add(identity.stableId)) {
            throw new IllegalArgumentException("Clipboard hierarchy contains a duplicate stable identity.");
        }
        int sourceId = entities.size + 1;
        entities.add(entityId);
        parents.add(parentSourceId);
        if (!mGameObject.has(entityId)) return;
        IntArray children = childrenByParentStableId.get(identity.stableId);
        if (children == null) return;
        for (int i = 0; i < children.size; i++) {
            collectClipboardSubtree(children.get(i), sourceId, entities, parents, stableIds,
                    childrenByParentStableId);
        }
    }

    private void normalizeNestedRootToWorldPose(int entityId, GenericEntityInitializer initializer) {
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        WorldTransformState state = hierarchy != null ? hierarchy.worldTransforms() : null;
        if (state == null || !state.isResolved(entityId)) throw new IllegalStateException("Nested Game Object clipboard root has no resolved world transform.");
        GenericEntitySnapshotData snapshot = initializer.toSnapshotData(0);
        snapshot.x = state.x[entityId]; snapshot.y = state.y[entityId];
        snapshot.rotationRad = state.rotationRad[entityId]; snapshot.scaleX = state.scaleX[entityId]; snapshot.scaleY = state.scaleY[entityId];
        initializer.applySnapshotData(snapshot);
    }

    private EntityGraph captureSupportedSelection(IntArray supported) {
        supported = ClipboardPhysicsJointGraph.filterCopyableSelection(world, supported);
        if (supported.size == 0) return EntityGraph.empty();

        List<EntityGraphEntry> entries = new ArrayList<>(supported.size);
        for (int i = 0; i < supported.size; i++) {
            int entityId = supported.get(i);
            GenericEntityInitializer init = new GenericEntityInitializer(world);
            init.syncFrom(entityId);
            entries.add(new EntityGraphEntry(entityId, init));
        }
        return new EntityGraph(entries);
    }

    private IntArray collectSupportedSelection(IntArray selection) {
        IntArray supported = new IntArray();
        if (selection == null || selection.size == 0) return supported;

        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (isCaptureSupported(entityId)) supported.add(entityId);
        }
        return supported;
    }

    private IntArray collectGameObjectHierarchy(IntArray selection) {
        IntArray hierarchy = new IntArray();
        if (selection == null || selection.size != 1) return hierarchy;
        int root = selection.first();
        if (!world.getEntityManager().isActive(root)
                || !mGameObject.has(root)
                || mGameObjectMember.has(root)) {
            return hierarchy;
        }
        PixscapeIdentityComponent rootIdentity = mIdentity.getSafe(root, null);
        if (rootIdentity == null || rootIdentity.stableId <= 0) return hierarchy;

        hierarchy.add(root);
        IntSet capturedStableIds = new IntSet();
        capturedStableIds.add(rootIdentity.stableId);
        IntSet capturedEntities = new IntSet();
        capturedEntities.add(root);
        IntBag members = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class))
                .getEntities();
        boolean changed;
        do {
            changed = false;
            int[] ids = members.getData();
            for (int i = 0; i < members.size(); i++) {
                int entityId = ids[i];
                if (capturedEntities.contains(entityId)) continue;
                GameObjectMemberComponent member = mGameObjectMember.get(entityId);
                if (!capturedStableIds.contains(member.parentStableId)) continue;
                PixscapeIdentityComponent identity = mIdentity.getSafe(entityId, null);
                if (identity == null || identity.stableId <= 0) return new IntArray();
                capturedEntities.add(entityId);
                capturedStableIds.add(identity.stableId);
                hierarchy.add(entityId);
                changed = true;
            }
        } while (changed);
        return hierarchy;
    }

    private boolean isCaptureSupported(int entityId) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return false;
        // Tiled maps require their dedicated deep snapshot; generic capture would be partial.
        if (mTiled.has(entityId)) return false;
        // Ordinary Game Object members cannot be independent clipboard roots in V1.
        if (mGameObject.has(entityId) || mGameObjectMember.has(entityId)) return false;
        if (mJointBase.has(entityId)) return true;
        if (!mEntityIndex.has(entityId)) return false;
        return mTransform.has(entityId);
    }
}
