package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.initializer.AbstractCommonInitializer;
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
    private final HistoryIdRegistry historyIds;
    private final List<Snapshot> snapshots = new ArrayList<>();
    private final List<JointEntry> jointSnapshots = new ArrayList<>();
    private final IntConsumer onRestoredEntity;

    public DeleteGameObjectHierarchyCommand(
            World world,
            HistoryIdRegistry historyIds,
            IntArray requestedEntities,
            IntConsumer onRestoredEntity) {
        this.world = world;
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
            GenericEntityInitializer initializer = new GenericEntityInitializer(world);
            initializer.syncFrom(entityId);
            snapshots.add(new Snapshot(historyIds.ensureForEntity(entityId), initializer, -1,
                    false, "", null));
            removedEntities.add(entityId);
        }

        IntSet capturedJoints = new IntSet();
        IntArray affectedJoints = PhysicsService.collectJointsAffectedByEntityRemoval(
                world, removedEntities, new IntArray(false, 8));
        for (int i = 0; i < explicitlySelectedJoints.size; i++) {
            captureJoint(explicitlySelectedJoints.get(i), capturedJoints);
        }
        for (int i = 0; i < affectedJoints.size; i++) {
            captureJoint(affectedJoints.get(i), capturedJoints);
        }
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
                world.getMapper(CustomPropertiesComponent.class).create(entityId)
                        .properties.copyFrom(snapshot.rootProperties);
            }
            if (snapshot.parentStableId > 0) {
                world.getMapper(GameObjectMemberComponent.class)
                        .create(entityId).parentStableId = snapshot.parentStableId;
            }
            historyIds.bind(entityId, snapshot.historyId);
            if (onRestoredEntity != null) onRestoredEntity.accept(entityId);
        }
        IntArray restoredJointIds = new IntArray(false, jointSnapshots.size());
        for (JointEntry joint : jointSnapshots) {
            int entityId = world.create();
            joint.initializer.init(entityId);
            historyIds.bind(entityId, joint.historyId);
            restoredJointIds.add(entityId);
            if (onRestoredEntity != null) onRestoredEntity.accept(entityId);
        }
        for (int i = 0; i < jointSnapshots.size(); i++) {
            jointSnapshots.get(i).references.restore(world, historyIds, restoredJointIds.get(i));
        }
    }

    private void delete(long historyId) {
        int entityId = historyIds.entityOfHistoryId(historyId);
        if (entityId >= 0 && world.getEntityManager().isActive(entityId)) {
            IdentityRegistry.unindexEntityImmediately(world, entityId);
            world.delete(entityId);
        }
        historyIds.unbindHistoryId(historyId);
    }

    private void captureJoint(int entityId, IntSet captured) {
        if (!captured.add(entityId)) return;
        GenericEntityInitializer initializer = new GenericEntityInitializer(world);
        initializer.syncFrom(entityId);
        jointSnapshots.add(new JointEntry(historyIds.ensureForEntity(entityId), initializer,
                JointHistorySnapshot.capture(world, historyIds, entityId)));
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
        IntBag members = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class)).getEntities();
        ComponentMapper<GameObjectMemberComponent> memberMapper =
                world.getMapper(GameObjectMemberComponent.class);
        int[] data = members.getData();
        for (int i = 0; i < members.size(); i++) {
            GameObjectMemberComponent member = memberMapper.get(data[i]);
            if (member != null && member.parentStableId == identity.stableId) {
                collectPreOrder(data[i], included, collected, depth + 1);
            }
        }
    }

    private int findByStableId(int stableId) {
        if (stableId <= 0) return -1;
        IntBag identities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PixscapeIdentityComponent.class)).getEntities();
        ComponentMapper<PixscapeIdentityComponent> mapper =
                world.getMapper(PixscapeIdentityComponent.class);
        int[] data = identities.getData();
        for (int i = 0; i < identities.size(); i++) {
            PixscapeIdentityComponent identity = mapper.get(data[i]);
            if (identity != null && identity.stableId == stableId) return data[i];
        }
        return -1;
    }

    private void capturePreOrder(int entityId, IntSet included, IntSet captured, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Game Object hierarchy depth limit exceeded.");
        }
        if (!included.contains(entityId) || !captured.add(entityId)) return;

        boolean gameObject = world.getMapper(GameObjectComponent.class).has(entityId);
        AbstractCommonInitializer initializer = new GenericEntityInitializer(world);
        initializer.syncFrom(entityId);
        GameObjectMemberComponent member = world.getMapper(GameObjectMemberComponent.class)
                .getSafe(entityId, null);
        GameObjectComponent root = gameObject
                ? world.getMapper(GameObjectComponent.class).get(entityId) : null;
        CustomPropertiesComponent properties = gameObject
                ? world.getMapper(CustomPropertiesComponent.class).getSafe(entityId, null) : null;
        PropertySet rootProperties = null;
        if (gameObject) {
            rootProperties = properties != null && properties.properties != null
                    ? properties.properties.copy() : new PropertySet();
        }
        snapshots.add(new Snapshot(
                historyIds.ensureForEntity(entityId), initializer,
                member != null ? member.parentStableId : -1, gameObject,
                root != null && root.sourceAssetId != null ? root.sourceAssetId : "",
                rootProperties));

        if (!gameObject) return;
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                .get(entityId);
        IntBag members = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class)).getEntities();
        ComponentMapper<GameObjectMemberComponent> memberMapper =
                world.getMapper(GameObjectMemberComponent.class);
        int[] data = members.getData();
        for (int i = 0; i < members.size(); i++) {
            GameObjectMemberComponent child = memberMapper.get(data[i]);
            if (child != null && child.parentStableId == identity.stableId) {
                capturePreOrder(data[i], included, captured, depth + 1);
            }
        }
    }

    private record Snapshot(
            long historyId,
            AbstractCommonInitializer initializer,
            int parentStableId,
            boolean gameObject,
            String sourceAssetId,
            PropertySet rootProperties) {
    }

    private record JointEntry(long historyId, GenericEntityInitializer initializer,
                              JointHistorySnapshot references) {
    }
}
