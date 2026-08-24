package games.pixscape.studio.ui.tree;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.Color;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.service.physics.PhysicsJointUiNames;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

import java.util.function.IntPredicate;

final class ItemTreeJointSupport {
    private ItemTreeJointSupport() {
    }

    static void attachJointNodes(
            World world,
            IdVisTree tree,
            IntPredicate layerLocked) {
        ComponentMapper<PhysicsJointComponent> joints =
                world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsBodyComponent> bodies =
                world.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<EntityIndexComponent> indexes =
                world.getMapper(EntityIndexComponent.class);
        IntBag jointEntities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsJointComponent.class)).getEntities();
        int[] data = jointEntities.getData();
        for (int i = 0; i < jointEntities.size(); i++) {
            int jointEntityId = data[i];
            PhysicsJointComponent joint = joints.getSafe(jointEntityId, null);
            if (joint == null
                    || joint.aEid < 0
                    || !world.getEntityManager().isActive(joint.aEid)
                    || bodies.getSafe(joint.aEid, null) == null) {
                continue;
            }
            EntityNode bodyNode = tree.findBodyNode(joint.aEid);
            if (bodyNode == null) continue;

            EntityIndexComponent index = indexes.getSafe(joint.aEid, null);
            boolean locked = index != null && layerLocked.test(index.layerIndex);
            EntityNode jointNode = new EntityNode(
                    PhysicsJointUiNames.typeName(joint.type),
                    VisUI.getSkin().getDrawable("joint"),
                    jointEntityId,
                    !locked,
                    EntityNode.NodeKind.JOINT);
            jointNode.getLabel().setColor(locked ? Color.DARK_GRAY : Color.WHITE);
            bodyNode.add(jointNode);
            tree.registerJointNode(jointNode, jointEntityId);
        }
    }

    static boolean selectJointContext(
            World world,
            PhysicsSelectionService selection,
            int jointEntityId) {
        if (jointEntityId < 0 || !world.getEntityManager().isActive(jointEntityId)) {
            return false;
        }
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class)
                .getSafe(jointEntityId, null);
        if (joint == null
                || joint.aEid < 0
                || !world.getEntityManager().isActive(joint.aEid)
                || world.getMapper(PhysicsBodyComponent.class).getSafe(joint.aEid, null) == null) {
            return false;
        }
        selection.setSelectedJoint(joint.aEid, jointEntityId);
        return true;
    }

    static EntityNode resolveSelectedJointNode(
            IdVisTree tree,
            PhysicsSelectionService selection) {
        int jointEntityId = selection.getSelectedJointEid();
        return jointEntityId < 0 ? null : tree.findJointNode(jointEntityId);
    }

    static boolean isLogicalOrderMoveAllowed(World world, int entityId) {
        return !world.getMapper(PhysicsJointComponent.class).has(entityId);
    }
}
