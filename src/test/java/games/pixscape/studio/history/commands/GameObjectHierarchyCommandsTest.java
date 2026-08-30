package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryIdRegistry;
import org.junit.Test;

import static org.junit.Assert.*;

public class GameObjectHierarchyCommandsTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void attachAndDetachPreserveWorldTransformOriginAndBothIdentities() {
        Fixture f = new Fixture();
        try {
            int parent = f.entity(10, 2, 4, 6f, -2f, 0.4f, 2f, 2f, true);
            int child = f.entity(20, 5, 9, 13f, 8f, 1.1f, 3f, -4f, false);
            f.transform(parent).originX = 9f;
            f.transform(parent).originY = 6f;
            f.transform(parent).refreshCaches();
            TransformComponent childTransform = f.transform(child);
            childTransform.originX = 7f;
            childTransform.originY = -5f;
            childTransform.refreshCaches();
            f.process();
            long historyId = f.historyIds.ensureForEntity(child);

            AddEntityToGameObjectCommand attach = new AddEntityToGameObjectCommand(
                    f.world, f.historyIds, f.identities, child, parent, 3);
            attach.redo();

            assertEquals(10, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(child).parentStableId);
            assertEquals(3, f.index(child).zIndex);
            assertEquals(20, f.identity(child).stableId);
            assertEquals(historyId, f.historyIds.historyIdOfEntity(child));
            assertWorld(f, child, 13f, 8f, 1.1f, 3f, -4f, 7f, -5f);

            attach.undo();
            assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(child));
            assertEquals(5, f.index(child).layerIndex);
            assertEquals(9, f.index(child).zIndex);
            assertWorld(f, child, 13f, 8f, 1.1f, 3f, -4f, 7f, -5f);

            attach.redo();
            RemoveEntityFromGameObjectCommand detach =
                    new RemoveEntityFromGameObjectCommand(
                            f.world, f.historyIds, f.identities, child, 5);
            detach.redo();
            assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(child));
            assertEquals(2, f.index(child).layerIndex);
            assertEquals(5, f.index(child).zIndex);
            assertWorld(f, child, 13f, 8f, 1.1f, 3f, -4f, 7f, -5f);
            detach.undo();
            assertEquals(10, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(child).parentStableId);
            assertWorld(f, child, 13f, 8f, 1.1f, 3f, -4f, 7f, -5f);
        } finally {
            f.close();
        }
    }

    @Test
    public void nestedAttachPreservesWorldAndCycleIsRejectedBeforeMutation() {
        Fixture f = new Fixture();
        try {
            int outer = f.entity(1, 0, 0, 5f, 7f, 0.3f, 2f, 2f, true);
            int inner = f.entity(2, 0, 1, -4f, 3f, -0.2f, 1f, 1f, true);
            f.transform(outer).originX = 8f;
            f.transform(outer).originY = 5f;
            f.transform(outer).refreshCaches();
            f.transform(inner).originX = 3f;
            f.transform(inner).originY = 7f;
            f.transform(inner).refreshCaches();
            f.process();
            AddEntityToGameObjectCommand nested = new AddEntityToGameObjectCommand(
                    f.world, f.historyIds, f.identities, inner, outer, 0);
            nested.redo();
            assertWorld(f, inner, -4f, 3f, -0.2f, 1f, 1f, 3f, 7f);

            try {
                new AddEntityToGameObjectCommand(
                        f.world, f.historyIds, f.identities, outer, inner, 0);
                fail("Expected cycle rejection");
            } catch (IllegalArgumentException expected) {
                assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(outer));
            }
        } finally {
            f.close();
        }
    }

    @Test
    public void unsupportedMemberIsRejectedWithoutMutation() {
        Fixture f = new Fixture();
        try {
            int root = f.entity(1, 0, 0, 0f, 0f, 0f, 1f, 1f, true);
            int particle = f.entity(2, 0, 1, 2f, 3f, 0f, 1f, 1f, false);
            f.world.getMapper(ParticleEmitterComponent.class).create(particle);
            f.process();
            TransformComponent before = GameObjectHierarchyCommandSupport.copy(f.transform(particle));
            try {
                new AddEntityToGameObjectCommand(
                        f.world, f.historyIds, f.identities, particle, root, 0);
                fail("Expected unsupported member rejection");
            } catch (IllegalArgumentException expected) {
                assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(particle));
                assertEquals(before.x, f.transform(particle).x, 0f);
            }
        } finally {
            f.close();
        }
    }

    private static void assertWorld(Fixture f, int entity, float x, float y,
                                    float rotation, float sx, float sy,
                                    float ox, float oy) {
        TransformComponent world = GameObjectHierarchyCommandSupport.worldTransform(
                f.world, f.identities, entity);
        assertEquals(x, world.x, EPSILON);
        assertEquals(y, world.y, EPSILON);
        assertEquals(rotation, world.rotationRad, EPSILON);
        assertEquals(sx, world.scaleX, EPSILON);
        assertEquals(sy, world.scaleY, EPSILON);
        assertEquals(ox, world.originX, EPSILON);
        assertEquals(oy, world.originY, EPSILON);
    }

    private static final class Fixture {
        final World world = new World();
        final IdentityRegistry identities = new IdentityRegistry();
        final HistoryIdRegistry historyIds = new HistoryIdRegistry();

        Fixture() {
            SceneMetaRuntime sceneMeta = new SceneMetaRuntime();
            sceneMeta.nextEntityStableId = 1000;
            identities.bind(world, sceneMeta);
        }

        int entity(int stableId, int layer, int z, float x, float y,
                   float rotation, float sx, float sy, boolean gameObject) {
            int entity = world.create();
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                    .create(entity);
            identity.stableId = stableId;
            identity.name = "Entity " + stableId;
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = layer;
            index.zIndex = z;
            TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
            transform.x = x;
            transform.y = y;
            transform.rotationRad = rotation;
            transform.scaleX = sx;
            transform.scaleY = sy;
            transform.refreshCaches();
            if (gameObject) world.getMapper(GameObjectComponent.class).create(entity);
            return entity;
        }

        void process() { world.process(); identities.rebuild(); }
        TransformComponent transform(int entity) { return world.getMapper(TransformComponent.class).get(entity); }
        EntityIndexComponent index(int entity) { return world.getMapper(EntityIndexComponent.class).get(entity); }
        PixscapeIdentityComponent identity(int entity) { return world.getMapper(PixscapeIdentityComponent.class).get(entity); }
        void close() { identities.bind(null, null); world.dispose(); }
    }
}
