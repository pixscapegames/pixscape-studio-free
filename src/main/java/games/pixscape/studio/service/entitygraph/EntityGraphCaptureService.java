package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntSet;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.ClipboardPhysicsJointGraph;

import java.util.ArrayList;
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
        if (containsGameObjectHierarchy(selection)) return EntityGraph.empty();
        return captureSupportedSelection(collectSupportedSelection(selection));
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

    private boolean containsGameObjectHierarchy(IntArray selection) {
        if (selection == null) return false;
        for (int i = 0; i < selection.size; i++) {
            int entityId = selection.get(i);
            if (mGameObject.has(entityId) || mGameObjectMember.has(entityId)) return true;
        }
        return false;
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
        // Hierarchy copy needs stable-ID remapping; reject roots and members until that command exists.
        if (mGameObject.has(entityId) || mGameObjectMember.has(entityId)) return false;
        if (mJointBase.has(entityId)) return true;
        if (!mEntityIndex.has(entityId)) return false;
        return mTransform.has(entityId);
    }
}
