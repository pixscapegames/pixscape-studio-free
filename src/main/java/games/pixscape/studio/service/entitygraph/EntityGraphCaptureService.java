package games.pixscape.studio.service.entitygraph;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
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
    private final ComponentMapper<PhysicsShapesComponent> mPhysicsShapes;
    private final ComponentMapper<TiledLayerComponent> mTiled;
    private final ComponentMapper<GameObjectComponent> mGameObject;
    private final ComponentMapper<GameObjectMemberComponent> mGameObjectMember;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;

    public EntityGraphCaptureService(World world) {
        this.world = world;
        this.mTransform = world.getMapper(TransformComponent.class);
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
        this.mPhysicsShapes = world.getMapper(PhysicsShapesComponent.class);
        this.mTiled = world.getMapper(TiledLayerComponent.class);
        this.mGameObject = world.getMapper(GameObjectComponent.class);
        this.mGameObjectMember = world.getMapper(GameObjectMemberComponent.class);
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
    }

    public EntityGraph capture(IntArray selection) {
        IntArray normalized = new ClipboardSelectionNormalizer(world).normalize(selection);
        return captureNormalizedClipboard(normalized);
    }

    /**
     * Captures one complete real Game Object hierarchy for asset publication.
     */
    public EntityGraph captureForGameObject(IntArray selection) {
        IntArray hierarchy = collectGameObjectHierarchy(selection);
        if (hierarchy.size == 0) return EntityGraph.empty();
        List<EntityGraphEntry> entries = new ArrayList<>(hierarchy.size);
        for (int i = 0; i < hierarchy.size; i++) {
            int entityId = hierarchy.get(i);
            TiledEntityRole.requireGenericMember(world, entityId);
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(entityId);
            entries.add(new EntityGraphEntry(entityId, initializer));
        }
        return new EntityGraph(entries);
    }

    /**
     * Captures a V1 clipboard selection with graph-local hierarchy ownership.
     */
    public EntityGraph captureGameObjectClipboard(IntArray selection) {
        IntArray roots = new ClipboardSelectionNormalizer(world).normalize(selection);
        return captureNormalizedClipboard(roots);
    }

    /**
     * Captures already-normalized V1 clipboard roots without changing Scene state.
     */
    public EntityGraph captureNormalizedClipboard(IntArray roots) {
        return captureNormalizedClipboard(roots, false);
    }

    public EntityGraph captureVisualOnly(IntArray selection) {
        return captureNormalizedClipboard(new ClipboardSelectionNormalizer(world).normalize(selection), true);
    }

    public EntityGraph captureNormalizedClipboard(IntArray roots, boolean visualOnly) {
        if (roots.size == 0) return EntityGraph.empty();
        if (!containsGameObjectRoot(roots)) {
            return captureSupportedSelection(collectSupportedSelection(roots), visualOnly);
        }
        IntArray captureRoots = roots;
        IntArray entities = new IntArray(false, captureRoots.size);
        IntArray parents = new IntArray(false, captureRoots.size);
        IntSet knownStableIds = new IntSet();
        IntMap<IntArray> childrenByParentStableId = collectChildrenByParentStableId();
        for (int i = 0; i < captureRoots.size; i++) {
            if (visualOnly && mJointBase.has(captureRoots.get(i))) {
                continue;
            }
            if (!mGameObject.has(captureRoots.get(i)) && !isCaptureSupported(captureRoots.get(i))) {
                throw new IllegalArgumentException(
                        "Clipboard selection contains an unsupported standalone entity.");
            }
            collectClipboardSubtree(captureRoots.get(i), -1, entities, parents, knownStableIds,
                    childrenByParentStableId);
        }
        IntArray internalJoints = visualOnly ? new IntArray() : requireSupportedGameObjectHierarchyPhysics(entities);
        IntMap<Integer> stableToSource = new IntMap<>();
        for (int i = 0; i < entities.size; i++) stableToSource.put(mIdentity.get(entities.get(i)).stableId, i + 1);
        List<EntityGraphEntry> entries = new ArrayList<>(entities.size);
        for (int i = 0; i < entities.size; i++) {
            int entityId = entities.get(i);
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(entityId);
            normalizeCapturedPose(entityId, parents.get(i) < 0 ? -1 : entities.get(parents.get(i) - 1), initializer);
            if (visualOnly) {
                initializer.withoutPhysics();
            }
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
        appendClipboardJoints(entries, entities, internalJoints);
        return new EntityGraph(entries);
    }

    /**
     * Returns the complete authored closure represented by a normalized clipboard
     * capture. Cut must delete this closure as well: internal joint entities do not
     * belong to the Game Object hierarchy, so deleting only the selected roots would
     * otherwise leave dangling scene joints behind.
     */
    public IntArray captureNormalizedClipboardCutEntities(IntArray roots) {
        if (roots == null || roots.size == 0) return new IntArray(false, 0);
        // Standalone clipboard has its own permissive identity contract.  Do not route
        // it through hierarchy collection, which correctly requires stable identities
        // for Game Object ownership but would reject ordinary standalone entities.
        if (!containsGameObjectRoot(roots)) {
            return ClipboardPhysicsJointGraph.filterCopyableSelection(world, roots);
        }
        IntArray captureRoots = roots;
        IntArray entities = new IntArray(false, captureRoots.size);
        IntArray parents = new IntArray(false, captureRoots.size);
        IntSet knownStableIds = new IntSet();
        IntMap<IntArray> childrenByParentStableId = collectChildrenByParentStableId();
        for (int i = 0; i < captureRoots.size; i++) {
            int entityId = captureRoots.get(i);
            if (!mGameObject.has(entityId) && !isCaptureSupported(entityId)) {
                throw new IllegalArgumentException(
                        "Clipboard selection contains an unsupported standalone entity.");
            }
            collectClipboardSubtree(entityId, -1, entities, parents, knownStableIds,
                    childrenByParentStableId);
        }
        IntArray internalJoints = requireSupportedGameObjectHierarchyPhysics(entities);
        IntSet includedEntities = new IntSet(entities.size + internalJoints.size);
        for (int i = 0; i < entities.size; i++) includedEntities.add(entities.get(i));
        for (int i = 0; i < internalJoints.size; i++) {
            int jointEntityId = internalJoints.get(i);
            if (includedEntities.add(jointEntityId)) entities.add(jointEntityId);
        }
        return entities;
    }

    private boolean containsGameObjectRoot(IntArray roots) {
        for (int i = 0; i < roots.size; i++) {
            if (mGameObject.has(roots.get(i))) return true;
        }
        return false;
    }

    /**
     * Requires the full joint-connected scope before capturing any physical data.
     */
    private IntArray requireSupportedGameObjectHierarchyPhysics(IntArray capturedEntities) {
        for (int i = 0; i < capturedEntities.size; i++) {
            int entityId = capturedEntities.get(i);
            if ((mGameObject.has(entityId) || mGameObjectMember.has(entityId)) && !mTiled.has(entityId)) {
                PhysicsShapesComponent shapes = mPhysicsShapes.getSafe(entityId, null);
                if (shapes != null && shapes.shapes != null) {
                    for (int shapeIndex = 0; shapeIndex < shapes.shapes.size; shapeIndex++) {
                        PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                        if (shape != null && shape.spatialBlockId > 0) {
                            throw new IllegalArgumentException(
                                    "Game Object clipboard hierarchies do not support "
                                            + "Physics shapes linked to Scene Spatial blocks (spatialBlockId > 0).");
                        }
                    }
                }
            }
        }
        IntSet scope = new IntSet(capturedEntities.size);
        for (int i = 0; i < capturedEntities.size; i++) {
            scope.add(capturedEntities.get(i));
        }
        return CompositionJointScope.classify(world, scope);
    }

    private void appendClipboardJoints(List<EntityGraphEntry> entries, IntArray entityIds, IntArray jointEntities) {
        if (jointEntities.size == 0) return;
        IntIntMap entityToSource = new IntIntMap(entityIds.size);
        for (int i = 0; i < entityIds.size; i++) entityToSource.put(entityIds.get(i), i + 1);
        IntIntMap jointToSource = new IntIntMap(jointEntities.size);
        int nextJointSource = entries.size() + 1;
        for (int i = 0; i < jointEntities.size; i++) {
            int jointEntityId = jointEntities.get(i);
            int existingSource = entityToSource.get(jointEntityId, -1);
            jointToSource.put(jointEntityId,
                    existingSource > 0 ? existingSource : nextJointSource++);
        }
        for (int i = 0; i < jointEntities.size; i++) {
            int jointEntityId = jointEntities.get(i);
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(jointEntityId);
            int sourceId = jointToSource.get(jointEntityId, -1);
            GenericEntitySnapshotData snapshot = initializer.toSnapshotData(sourceId);
            snapshot.jointAEid = entityToSource.get(snapshot.jointAEid, -1);
            snapshot.jointBEid = entityToSource.get(snapshot.jointBEid, -1);
            if (snapshot.jointType == PhysicsJointComponent.TYPE_GEAR) {
                snapshot.gearJoint1Eid = jointToSource.get(snapshot.gearJoint1Eid, -1);
                snapshot.gearJoint2Eid = jointToSource.get(snapshot.gearJoint2Eid, -1);
            }
            initializer.applySnapshotData(snapshot);
            int existingEntryIndex = sourceId - 1;
            if (existingEntryIndex < entries.size()) {
                EntityGraphEntry existing = entries.get(existingEntryIndex);
                entries.set(existingEntryIndex, new EntityGraphEntry(
                        existing.sourceEntityId(), existing.parentSourceEntityId(),
                        false, "", initializer, existing.customProperties()));
            } else {
                entries.add(new EntityGraphEntry(sourceId, -1, false, "", initializer, null));
            }
        }
    }

    private IntMap<IntArray> collectChildrenByParentStableId() {
        IntMap<IntArray> childrenByParentStableId =
                CurrentGameObjectRelations.collectCurrentChildrenByParentStableId(world,
                        games.pixscape.runtime.service.IdentityRegistry.boundTo(world));
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
        if (!world.getEntityManager().isActive(entityId))
            throw new IllegalArgumentException("Clipboard entity is inactive.");
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

    private void normalizeCapturedPose(int entityId, int parentEntity, GenericEntityInitializer initializer) {
        games.pixscape.runtime.system.PhysicsPoseAuthority authority =
                world.getSystem(games.pixscape.runtime.system.PhysicsPoseAuthority.class);
        boolean simulated = authority != null && authority.isRuntimePhysics();
        if (!simulated && (parentEntity >= 0 || !mGameObjectMember.has(entityId))) {
            return;
        }
        GameObjectHierarchySystem hierarchy = world.getSystem(GameObjectHierarchySystem.class);
        WorldTransformState poses = hierarchy == null ? null : hierarchy.worldTransforms();
        if (poses == null || !poses.isResolved(entityId)) {
            if (parentEntity < 0 && mGameObjectMember.has(entityId)) {
                throw new IllegalStateException("Copied member has no resolved world pose.");
            }
            return;
        }
        TransformComponent pose = resolvedTransform(entityId, poses);
        if (parentEntity >= 0) {
            if (!poses.isResolved(parentEntity)) {
                throw new IllegalStateException("Copied parent has no resolved world pose.");
            }
            pose = games.pixscape.runtime.hierarchy.GameObjectTransformMath.worldToLocal(
                    resolvedTransform(parentEntity, poses), pose, mGameObject.has(entityId), new TransformComponent());
        }
        GenericEntitySnapshotData snapshot = initializer.toSnapshotData(0);
        snapshot.x = pose.x;
        snapshot.y = pose.y;
        snapshot.rotationRad = pose.rotationRad;
        snapshot.scaleX = pose.scaleX;
        snapshot.scaleY = pose.scaleY;
        initializer.applySnapshotData(snapshot);
    }

    private TransformComponent resolvedTransform(int entity, WorldTransformState poses) {
        TransformComponent result = new TransformComponent();
        result.x = poses.x[entity];
        result.y = poses.y[entity];
        result.rotationRad = poses.rotationRad[entity];
        result.scaleX = poses.scaleX[entity];
        result.scaleY = poses.scaleY[entity];
        TransformComponent authored = mTransform.get(entity);
        result.originX = authored.originX;
        result.originY = authored.originY;
        result.refreshCaches();
        return result;
    }

    private EntityGraph captureSupportedSelection(IntArray supported, boolean visualOnly) {
        if (!visualOnly) {
            supported = ClipboardPhysicsJointGraph.filterCopyableSelection(world, supported);
        }
        List<EntityGraphEntry> entries = new ArrayList<>(supported.size);
        for (int i = 0; i < supported.size; i++) {
            int entityId = supported.get(i);
            if (visualOnly && mJointBase.has(entityId)) {
                continue;
            }
            GenericEntityInitializer init = new GenericEntityInitializer(world);
            init.syncFrom(entityId);
            if (!mJointBase.has(entityId)) {
                normalizeCapturedPose(entityId, -1, init);
            }
            if (visualOnly) {
                init.withoutPhysics();
            }
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
        if (mGameObject.has(entityId)) {
            return false;
        }
        if (mJointBase.has(entityId)) return true;
        if (!mEntityIndex.has(entityId)) return false;
        return mTransform.has(entityId);
    }
}
