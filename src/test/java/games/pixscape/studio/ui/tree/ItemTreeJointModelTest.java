package games.pixscape.studio.ui.tree;

import com.artemis.World;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.DeleteEntitiesCommand;
import games.pixscape.studio.service.physics.PhysicsJointUiNames;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ItemTreeJointModelTest {
    @BeforeClass
    public static void loadVisUi() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadVisUi() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void prefabJointAppearsOnceUnderCanonicalBodyWithDedicatedPresentation() {
        World world = new World();
        try {
            int bodyA = body(world, 81);
            int bodyB = body(world, 81);
            int joint = joint(world, bodyA, bodyB, 81, PhysicsJointComponent.TYPE_REVOLUTE);
            world.process();

            IdVisTree tree = prefabTree(bodyA, bodyB, 81);
            ItemTreeJointSupport.attachJointNodes(world, tree, ignored -> false);

            EntityNode jointNode = tree.findJointNode(joint);
            assertEquals(EntityNode.NodeKind.JOINT, jointNode.getKind());
            assertEquals(joint, jointNode.getEntityId());
            assertEquals("Revolute joint", jointNode.getLabel().getText().toString());
            assertSame(VisUI.getSkin().getDrawable("joint"), jointNode.getIcon());
            assertSame(tree.findBodyNode(bodyA), jointNode.getParent());
            assertFalse(tree.findBodyNode(bodyB).getChildren().contains(jointNode, true));
            assertNull(tree.findNode(joint));

            IntMap<IntArray> allMembers = ItemTreePanel.collectPrefabMembers(world);
            assertTrue(allMembers.get(81).contains(bodyA));
            assertTrue(allMembers.get(81).contains(bodyB));
            assertTrue(allMembers.get(81).contains(joint));
            EntityNode membershipSnapshot = EntityNode.prefabInstance(
                    "Prefab",
                    null,
                    81,
                    allMembers.get(81),
                    new IntArray(new int[]{bodyA, bodyB}),
                    true);
            assertTrue(membershipSnapshot.getPrefabMemberIds().contains(joint));
            assertFalse(membershipSnapshot.getPrefabZOrderMemberIds().contains(joint));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void standaloneAndGearJointUseCanonicalSelectionContext() {
        World world = new World();
        try {
            int bodyA = body(world, -1);
            int bodyB = body(world, -1);
            int gear = joint(world, bodyA, bodyB, -1, PhysicsJointComponent.TYPE_GEAR);
            world.process();
            IdVisTree tree = standaloneTree(bodyA, bodyB);
            ItemTreeJointSupport.attachJointNodes(world, tree, ignored -> false);

            EntityNode gearNode = tree.findJointNode(gear);
            assertEquals("Gear joint", gearNode.getLabel().getText().toString());
            assertSame(tree.findBodyNode(bodyA), gearNode.getParent());

            PhysicsSelectionService selection = new PhysicsSelectionService();
            assertTrue(ItemTreeJointSupport.selectJointContext(world, selection, gear));
            assertEquals(bodyA, selection.getFocusedBodyEid());
            assertEquals(gear, selection.getSelectedJointEid());
            assertSame(gearNode,
                    ItemTreeJointSupport.resolveSelectedJointNode(tree, selection));
            assertFalse(ItemTreeJointSupport.isLogicalOrderMoveAllowed(world, gear));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void invalidCanonicalParentOmitsJointAndLockedLayerDisablesIt() {
        World world = new World();
        try {
            int bodyA = body(world, -1);
            int bodyB = body(world, -1);
            int lockedJoint = joint(
                    world, bodyA, bodyB, -1, PhysicsJointComponent.TYPE_DISTANCE);
            int invalidJoint = joint(
                    world, -1, bodyB, -1, PhysicsJointComponent.TYPE_MOTOR);
            world.process();
            IdVisTree tree = standaloneTree(bodyA, bodyB);
            ItemTreeJointSupport.attachJointNodes(world, tree, ignored -> true);

            EntityNode locked = tree.findJointNode(lockedJoint);
            assertFalse(locked.isSelectable());
            assertEquals(Color.DARK_GRAY, locked.getLabel().getColor());
            assertNull(tree.findJointNode(invalidJoint));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void allJointTypeLabelsAreStableAndTypeOnly() {
        assertEquals("Distance joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_DISTANCE));
        assertEquals("Revolute joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_REVOLUTE));
        assertEquals("Prismatic joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_PRISMATIC));
        assertEquals("Pulley joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_PULLEY));
        assertEquals("Mouse joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_MOUSE));
        assertEquals("Gear joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_GEAR));
        assertEquals("Wheel joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_WHEEL));
        assertEquals("Weld joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_WELD));
        assertEquals("Friction joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_FRICTION));
        assertEquals("Motor joint", PhysicsJointUiNames.typeName(PhysicsJointComponent.TYPE_MOTOR));
        assertEquals("Joint", PhysicsJointUiNames.typeName(-1));
    }

    @Test
    public void completePrefabSelectionDeletesAndRestoresBodiesAndJoint() {
        World world = new World();
        try {
            HistoryManager history = new HistoryManager(8);
            int bodyA = body(world, 91);
            int bodyB = body(world, 91);
            int joint = joint(world, bodyA, bodyB, 91, PhysicsJointComponent.TYPE_REVOLUTE);
            world.process();
            long bodyAHistory = history.historyIds().ensureForEntity(bodyA);
            long bodyBHistory = history.historyIds().ensureForEntity(bodyB);
            long jointHistory = history.historyIds().ensureForEntity(joint);

            IntArray completeSelection = ItemTreePanel.collectPrefabMembers(world).get(91);
            assertEquals(3, completeSelection.size);
            history.execute(new DeleteEntitiesCommand(
                    world, history.historyIds(), completeSelection));
            world.process();
            assertEquals(-1, history.historyIds().entityOfHistoryId(bodyAHistory));
            assertEquals(-1, history.historyIds().entityOfHistoryId(bodyBHistory));
            assertEquals(-1, history.historyIds().entityOfHistoryId(jointHistory));

            history.undo();
            world.process();
            int restoredA = history.historyIds().entityOfHistoryId(bodyAHistory);
            int restoredB = history.historyIds().entityOfHistoryId(bodyBHistory);
            int restoredJoint = history.historyIds().entityOfHistoryId(jointHistory);
            assertTrue(restoredA >= 0);
            assertTrue(restoredB >= 0);
            assertTrue(restoredJoint >= 0);
            assertEquals(91, world.getMapper(PrefabInstanceComponent.class)
                    .get(restoredA).instanceId);
            assertEquals(91, world.getMapper(PrefabInstanceComponent.class)
                    .get(restoredB).instanceId);
            assertEquals(91, world.getMapper(PrefabInstanceComponent.class)
                    .get(restoredJoint).instanceId);
            PhysicsJointComponent restored = world.getMapper(PhysicsJointComponent.class)
                    .get(restoredJoint);
            assertEquals(restoredA, restored.aEid);
            assertEquals(restoredB, restored.bEid);
        } finally {
            world.dispose();
        }
    }

    private static IdVisTree prefabTree(int bodyA, int bodyB, int instanceId) {
        IdVisTree tree = new IdVisTree();
        EntityNode prefab = EntityNode.prefabInstance(
                "Prefab", null, instanceId, new IntArray(new int[]{bodyA, bodyB}), true);
        tree.add(prefab);
        tree.registerPrefabInstanceNode(prefab);
        addBody(tree, prefab, bodyA);
        addBody(tree, prefab, bodyB);
        return tree;
    }

    private static IdVisTree standaloneTree(int bodyA, int bodyB) {
        IdVisTree tree = new IdVisTree();
        EntityNode root = new EntityNode("Layer", null, 1000, true, EntityNode.NodeKind.LAYER);
        tree.add(root);
        addBody(tree, root, bodyA);
        addBody(tree, root, bodyB);
        return tree;
    }

    private static void addBody(IdVisTree tree, EntityNode parent, int entityId) {
        EntityNode entity = new EntityNode("E" + entityId, null, entityId, true);
        EntityNode body = new EntityNode(
                "Dynamic body", null, entityId, true, EntityNode.NodeKind.BODY);
        entity.add(body);
        parent.add(entity);
        tree.registerNode(entity, entityId);
        tree.registerNode(body, entityId);
    }

    private static int body(World world, int prefabInstanceId) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = 0;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).name = "E" + entity;
        world.getMapper(PhysicsBodyComponent.class).create(entity).type = PhysicsBodyComponent.DYNAMIC;
        if (prefabInstanceId > 0) prefab(world, entity, prefabInstanceId);
        return entity;
    }

    private static int joint(
            World world, int bodyA, int bodyB, int prefabInstanceId, int type) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = 0;
        index.zIndex = 999;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).name = "Unnamed";
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(entity);
        joint.type = type;
        joint.aEid = bodyA;
        joint.bEid = bodyB;
        if (prefabInstanceId > 0) prefab(world, entity, prefabInstanceId);
        return entity;
    }

    private static void prefab(World world, int entity, int instanceId) {
        PrefabInstanceComponent prefab =
                world.getMapper(PrefabInstanceComponent.class).create(entity);
        prefab.instanceId = instanceId;
        prefab.prefabId = "Prefab";
    }
}
