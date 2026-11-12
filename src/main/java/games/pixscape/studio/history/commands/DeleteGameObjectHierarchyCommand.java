package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.Initializer;
import games.pixscape.studio.history.initializer.TiledMapInitializer;
import games.pixscape.studio.service.entitygraph.CurrentGameObjectRelations;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Deletes selected Game Object hierarchies and any standalone entities selected with them.
 * Dependent joints are captured before deletion; undo restores bodies before their joints.
 */
public final class DeleteGameObjectHierarchyCommand implements Command {
    private static final int MAX_DEPTH = 1024;

    private final World world;
    private final IdentityRegistry identities;
    private final TiledAllocatorService tiledAllocator;
    private IntMap<IntArray> children;
    private final HistoryIdRegistry historyIds;
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final List<JointEntry> jointSnapshots = new ArrayList<>();
    private final IntConsumer onRestoredEntity;

    public DeleteGameObjectHierarchyCommand(
            World world,
            HistoryIdRegistry historyIds,
            IntArray requestedEntities,
            IntConsumer onRestoredEntity) {
        this(world, historyIds, requestedEntities, onRestoredEntity, null);
    }

    public DeleteGameObjectHierarchyCommand(
            World world, HistoryIdRegistry historyIds, IntArray requestedEntities,
            IntConsumer onRestoredEntity, TiledAllocatorService tiledAllocator) {
        this.world = world;
        this.identities = IdentityRegistry.boundTo(world);
        this.tiledAllocator = tiledAllocator;
        this.children = CurrentGameObjectRelations.collectCurrentChildrenByParentStableId(world, identities);
        this.historyIds = historyIds;
        this.onRestoredEntity = onRestoredEntity;

        if (requestedEntities == null || requestedEntities.isEmpty()) {
            throw new IllegalArgumentException("Game Object hierarchy deletion requires a target.");
        }

        IntSet included = new IntSet();
        IntArray collected = new IntArray();
        IntArray standalone = new IntArray();
        IntArray explicitlySelectedJoints = new IntArray();
        for (int i = 0; i < requestedEntities.size; i++) {
            int entityId = requestedEntities.get(i);
            GameObjectHierarchyCommandSupport.requireActive(world, entityId, "delete target");
            boolean member = world.getMapper(GameObjectMemberComponent.class).has(entityId);
            boolean root = world.getMapper(GameObjectComponent.class).has(entityId);
            if (!member && !root) {
                if (world.getMapper(PhysicsJointComponent.class).has(entityId)) {
                    explicitlySelectedJoints.add(entityId);
                } else {
                    standalone.add(entityId);
                }
                continue;
            }
            collectPreOrder(entityId, included, collected, 0);
        }

        for (int i = 0; i < collected.size; i++) {
            int entityId = collected.get(i);
            GameObjectHierarchyCommandSupport.requireCore(world, entityId, "delete target");
            GameObjectHierarchyCommandSupport.requireSupportedMember(world, entityId);
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(entityId, null);
            if (member != null && findByStableId(member.parentStableId) < 0) {
                throw new IllegalArgumentException(
                        "Game Object hierarchy delete requires an active stable parent.");
            }
        }

        IntSet captured = new IntSet();
        for (int i = 0; i < collected.size; i++) {
            int entityId = collected.get(i);
            GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                    .getSafe(entityId, null);
            int parent = member != null ? findByStableId(member.parentStableId) : -1;
            if (parent < 0 || !included.contains(parent)) {
                capturePreOrder(entityId, included, captured, 0);
            }
        }

        IntArray removedEntities = new IntArray(collected);
        for (int i = 0; i < standalone.size; i++) {
            int entityId = standalone.get(i);
            if (!captured.add(entityId)) continue;
            Initializer initializer;
            if (world.getMapper(TiledLayerComponent.class).has(entityId)) {
                initializer = new TiledMapInitializer(world, tiledAllocator);
            } else {
                initializer = new GenericEntityInitializer(world);
            }
            initializer.syncFrom(entityId);
            if (initializer instanceof GenericEntityInitializer generic) generic.preparePhysicsCandidate();
            snapshots.add(new Snapshot(historyIds.ensureForEntity(entityId), initializer, -1,
                    false, "", captureProperties(entityId)));
            removedEntities.add(entityId);
        }

        IntSet capturedJoints = new IntSet();
        // Capture the current dependency index once; redo never recaptures restored entities.
        removedEntities.addAll(explicitlySelectedJoints);
        IntArray affectedJoints = PhysicsService.collectJointsAffectedByEntityRemoval(
                world, removedEntities, new IntArray(false, 8));
        for (int i = 0; i < explicitlySelectedJoints.size; i++) {
            captureJoint(explicitlySelectedJoints.get(i), capturedJoints);
        }
        for (int i = 0; i < affectedJoints.size; i++) {
            captureJoint(affectedJoints.get(i), capturedJoints);
        }
        children = null; // Preparation-only adjacency, never retained as a scene cache.
    }

    @Override
    public String label() {
        return snapshots.size() == 1 ? "Delete Game Object Member" : "Delete Game Object Hierarchy";
    }

    @Override
    public void redo() {
        for (int i = jointSnapshots.size() - 1; i >= 0; i--) {
            delete(jointSnapshots.get(i).historyId);
        }
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            delete(snapshots.get(i).historyId);
        }
    }

    @Override
    public void undo() {
        for (Snapshot snapshot : snapshots) {
            int entityId = world.create();
            snapshot.initializer.init(entityId);
            if (snapshot.gameObject) {
                world.getMapper(GameObjectComponent.class).create(entityId)
                        .sourceAssetId = snapshot.sourceAssetId;
            }
            if (snapshot.properties != null) {
                world.getMapper(CustomPropertiesComponent.class).create(entityId)
                        .properties.copyFrom(snapshot.properties);
            }
            if (snapshot.parentStableId > 0) {
                world.getMapper(GameObjectMemberComponent.class)
                        .create(entityId).parentStableId = snapshot.parentStableId;
            }
            identities.indexEntityImmediately(entityId);
            historyIds.bind(entityId, snapshot.historyId);
            if (onRestoredEntity != null) onRestoredEntity.accept(entityId);
        }
        IntArray restoredJointIds = new IntArray(false, jointSnapshots.size());
        for (JointEntry joint : jointSnapshots) {
            int entityId = world.create();
            joint.initializer.init(entityId);
            identities.indexEntityImmediately(entityId);
            historyIds.bind(entityId, joint.historyId);
            restoredJointIds.add(entityId);
            if (onRestoredEntity != null) onRestoredEntity.accept(entityId);
        }
        for (int i = 0; i < jointSnapshots.size(); i++) {
            jointSnapshots.get(i).references.restore(world, historyIds, restoredJointIds.get(i));
        }
        // Gear sources and endpoints must all be remapped before publishing any relation.
        Box2dSyncSystem physics = world.getSystem(Box2dSyncSystem.class);
        if (physics != null) for (int i = 0; i < restoredJointIds.size; i++) {
            physics.publishJointRelations(restoredJointIds.get(i));
        }
    }

    private void delete(long historyId) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId >= 0 && world.getEntityManager().isActive(entityId)) {
            Box2dSyncSystem physics = world.getSystem(Box2dSyncSystem.class);
            if (physics != null) physics.jointDependencies().remove(entityId);
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(entityId, null);
            if (tiled != null && tiledAllocator != null) tiledAllocator.freeLayer(tiled);
            IdentityRegistry.unindexEntityImmediately(world, entityId);
            world.delete(entityId);
        }
        historyIds.unbindHistoryId(historyId);
    }

    private PropertySet captureProperties(int entityId) {
        var properties = world.getMapper(CustomPropertiesComponent.class).getSafe(entityId, null);
        if (properties == null) return null;
        if (properties.properties == null) throw new IllegalArgumentException("Custom properties require a PropertySet.");
        return properties.properties.copy();
    }

    private void captureJoint(int entityId, IntSet captured) {
        if (!captured.add(entityId)) return;
        var joint = world.getMapper(PhysicsJointComponent.class).get(entityId);
        requireBody(joint.aEid);
        requireBody(joint.bEid);
        GenericEntityInitializer initializer = new GenericEntityInitializer(world);
        initializer.syncFrom(entityId);
        jointSnapshots.add(new JointEntry(historyIds.ensureForEntity(entityId), initializer,
                JointHistorySnapshot.capture(world, historyIds, entityId)));
    }

    private void requireBody(int entityId) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)
                || !world.getMapper(PhysicsBodyComponent.class).has(entityId)) {
            throw new IllegalArgumentException("Joint deletion requires two existing bodies.");
        }
    }

    private void collectPreOrder(
            int entityId, IntSet included, IntArray collected, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Game Object hierarchy depth limit exceeded.");
        }
        if (!included.add(entityId)) return;
        collected.add(entityId);
        if (!world.getMapper(GameObjectComponent.class).has(entityId)) return;

        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                .getSafe(entityId, null);
        if (identity == null || identity.stableId <= 0) {
            throw new IllegalArgumentException("Game Object hierarchy delete requires stable identity.");
        }
        IntArray currentChildren = children.get(identity.stableId);
        if (currentChildren != null) for (int i = 0; i < currentChildren.size; i++) {
            collectPreOrder(currentChildren.get(i), included, collected, depth + 1);
        }
    }

    private int findByStableId(int stableId) {
        return identities.findByStableId(stableId);
    }

    private void capturePreOrder(int entityId, IntSet included, IntSet captured, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Game Object hierarchy depth limit exceeded.");
        }
        if (!included.contains(entityId) || !captured.add(entityId)) return;

        boolean gameObject = world.getMapper(GameObjectComponent.class).has(entityId);
        GenericEntityInitializer initializer = new GenericEntityInitializer(world);
        initializer.syncFrom(entityId);
        initializer.preparePhysicsCandidate();
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(entityId, null);
        GameObjectComponent root = gameObject
                ? world.getMapper(GameObjectComponent.class).get(entityId) : null;
        snapshots.add(new Snapshot(
                historyIds.ensureForEntity(entityId), initializer,
                member != null ? member.parentStableId : -1, gameObject,
                root != null && root.sourceAssetId != null ? root.sourceAssetId : "",
                captureProperties(entityId)));

        if (!gameObject) return;
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                .get(entityId);
        IntArray currentChildren = children.get(identity.stableId);
        if (currentChildren != null) for (int i = 0; i < currentChildren.size; i++) {
            capturePreOrder(currentChildren.get(i), included, captured, depth + 1);
        }
    }

    private record Snapshot(
            long historyId,
            Initializer initializer,
            int parentStableId,
            boolean gameObject,
            String sourceAssetId,
            PropertySet properties) {
    }

    private record JointEntry(long historyId, GenericEntityInitializer initializer,
                              JointHistorySnapshot references) {
    }
}
