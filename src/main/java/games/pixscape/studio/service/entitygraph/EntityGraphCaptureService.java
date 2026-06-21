package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
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
    private final ComponentMapper<PointLightComponent> mPointLight;
    private final ComponentMapper<ConeLightComponent> mConeLight;
    private final ComponentMapper<PhysicsJointComponent> mJointBase;

    public EntityGraphCaptureService(World world) {
        this.world = world;
        this.mTransform = world.getMapper(TransformComponent.class);
        this.mEntityIndex = world.getMapper(EntityIndexComponent.class);
        this.mPointLight = world.getMapper(PointLightComponent.class);
        this.mConeLight = world.getMapper(ConeLightComponent.class);
        this.mJointBase = world.getMapper(PhysicsJointComponent.class);
    }

    public EntityGraph capture(IntArray selection) {
        IntArray supported = collectSupportedSelection(selection);
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
            if (isClipboardSupported(entityId)) supported.add(entityId);
        }
        return supported;
    }

    private boolean isClipboardSupported(int entityId) {
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) return false;
        if (mPointLight.has(entityId) || mConeLight.has(entityId)) return false;
        if (mJointBase.has(entityId)) return true;
        if (!mEntityIndex.has(entityId)) return false;
        return mTransform.has(entityId);
    }
}
