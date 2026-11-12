package games.pixscape.studio.ui.tree;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class ItemTreeJointSupportTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void orphanIsAnErrorInsteadOfAnInvisibleTreeEntry() {
        for (boolean removeA : new boolean[]{true, false}) {
            try (Fixture f = new Fixture()) {
                f.world.getMapper(PhysicsBodyComponent.class).remove(removeA ? f.a : f.b);
                assertThrows(IllegalArgumentException.class,
                        () -> ItemTreeJointSupport.attachJointNodes(f.world, f.tree, e -> false));
                assertThrows(IllegalArgumentException.class,
                        () -> ItemTreeJointSupport.selectJointContext(f.world, f.selection, f.joint));
            }
        }
    }

    @Test public void validJointIsAttachedToBodyAndRemainsSelectable() {
        try (Fixture f = new Fixture()) {
            ItemTreeJointSupport.attachJointNodes(f.world, f.tree, e -> false);
            EntityNode node = f.tree.findJointNode(f.joint);
            assertNotNull(node);
            assertSame(f.tree.findBodyNode(f.a), node.getParent());
            assertTrue(ItemTreeJointSupport.selectJointContext(f.world, f.selection, f.joint));
            assertEquals(f.joint, f.selection.getSelectedJointEid());
            assertEquals(f.a, f.selection.getFocusedBodyEid());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world = new World(new WorldConfiguration());
        final IdVisTree tree = new IdVisTree();
        final PhysicsSelectionService selection = new PhysicsSelectionService();
        final int a = world.create(), b = world.create(), joint = world.create();
        Fixture() {
            world.getMapper(PhysicsBodyComponent.class).create(a);
            world.getMapper(PhysicsBodyComponent.class).create(b);
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(joint);
            base.aEid = a; base.bEid = b;
            world.process();
            EntityNode body = new EntityNode("Body", null, a, true, EntityNode.NodeKind.BODY);
            tree.add(body); tree.registerNode(body, a);
        }
        @Override public void close() { selection.dispose(); world.dispose(); }
    }
}
