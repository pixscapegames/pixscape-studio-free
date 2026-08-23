package games.pixscape.studio.ui.tree;

import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.studio.service.SelectionService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemTreePanelMembershipTest {

    @Test
    public void authoredIdentityAndLayerMembershipQualifyEveryRegularItemKindExactlyOnce() {
        World world = new World();
        try {
            EntitySubscription items = world.getAspectSubscriptionManager()
                    .get(ItemTreePanel.layerItemAspect());

            int sprite = authoredItem(world, "Sprite", 0, 4);
            world.getMapper(AssetRefComponent.class).create(sprite);

            int animation = authoredItem(world, "Animation", 0, 3);
            world.getMapper(AssetRefComponent.class).create(animation);
            world.getMapper(AnimationComponent.class).create(animation);

            int particle = authoredItem(world, "Particle", 0, 2);
            world.getMapper(ParticleEmitterComponent.class).create(particle);

            int light = authoredItem(world, "Light", 0, 1);
            world.getMapper(PointLightComponent.class).create(light);

            int rectangle = authoredItem(world, "Rectangle", 0, 0);
            world.getMapper(TransformComponent.class).create(rectangle);
            world.getMapper(DimensionsComponent.class).create(rectangle);

            int point = authoredItem(world, "Point", 0, -1);
            world.getMapper(TransformComponent.class).create(point);

            int layer = authoredItem(world, "Layer", 0, 0);
            world.getMapper(LayerComponent.class).create(layer);

            int temporaryIndexOnly = world.create();
            world.getMapper(EntityIndexComponent.class).create(temporaryIndexOnly);

            int identityOnly = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(identityOnly);

            int physicsJoint = world.create();
            world.getMapper(PhysicsJointComponent.class).create(physicsJoint);

            world.process();

            IntBag members = items.getEntities();
            assertEquals(6, members.size());
            assertTrue(contains(members, sprite));
            assertTrue(contains(members, animation));
            assertTrue(contains(members, particle));
            assertTrue(contains(members, light));
            assertTrue(contains(members, rectangle));
            assertTrue(contains(members, point));
            assertFalse(contains(members, layer));
            assertFalse(contains(members, temporaryIndexOnly));
            assertFalse(contains(members, identityOnly));
            assertFalse(contains(members, physicsJoint));

            SelectionService selection = new SelectionService(world, null);
            selection.selectFromTree(rectangle);
            selection.selectFromTree(point);
            assertTrue(selection.getSelectionSet().contains(rectangle));
            assertTrue(selection.getSelectionSet().contains(point));
        } finally {
            world.dispose();
        }
    }

    private static int authoredItem(World world, String name, int layerIndex, int zIndex) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layerIndex;
        index.zIndex = zIndex;
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(entity);
        identity.stableId = entity + 1;
        identity.name = name;
        return entity;
    }

    private static boolean contains(IntBag entities, int expected) {
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            if (data[i] == expected) return true;
        }
        return false;
    }
}
