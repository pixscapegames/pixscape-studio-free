package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeleteGameObjectHierarchyCommandTest {
    @Test public void deletesAndRestoresLeafSprite() { assertLeaf(EntityKind.SPRITE); }
    @Test public void deletesAndRestoresLeafAnimation() { assertLeaf(EntityKind.ANIMATION); }
    @Test public void deletesAndRestoresLeafPointLight() { assertLeaf(EntityKind.POINT_LIGHT); }
    @Test public void deletesAndRestoresLeafConeLight() { assertLeaf(EntityKind.CONE_LIGHT); }

    @Test
    public void nestedGameObjectDeletionCascadesAndNeverOrphansDescendants() {
        Fixture f = new Fixture();
        try {
            int top = f.root(10, -1);
            int nested = f.root(20, 10);
            int sprite = f.member(30, 20, EntityKind.SPRITE, 7);
            f.process();
            long nestedHistory = f.history.historyIds().ensureForEntity(nested);
            long spriteHistory = f.history.historyIds().ensureForEntity(sprite);

            f.history.execute(new DeleteGameObjectHierarchyCommand(
                    f.world, f.history.historyIds(), new IntArray(new int[]{nested}), null));
            f.process();
            assertTrue(f.world.getEntityManager().isActive(top));
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(nestedHistory));
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(spriteHistory));

            f.history.undo();
            f.process();
            int restoredNested = f.history.historyIds().entityOfHistoryId(nestedHistory);
            int restoredSprite = f.history.historyIds().entityOfHistoryId(spriteHistory);
            assertTrue(restoredNested >= 0);
            assertTrue(restoredSprite >= 0);
            assertEquals(10, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(restoredNested).parentStableId);
            assertEquals(20, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(restoredSprite).parentStableId);
        } finally {
            f.close();
        }
    }

    @Test
    public void rootAndSelectedDescendantAreDeletedOnlyOnceInOneAtomicCommand() {
        Fixture f = new Fixture();
        try {
            int root = f.root(10, -1);
            int child = f.member(20, 10, EntityKind.POINT_LIGHT, 2);
            f.process();
            long rootHistory = f.history.historyIds().ensureForEntity(root);
            long childHistory = f.history.historyIds().ensureForEntity(child);

            f.history.execute(new DeleteGameObjectHierarchyCommand(
                    f.world, f.history.historyIds(),
                    new IntArray(new int[]{child, root}), null));
            f.process();
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(rootHistory));
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(childHistory));

            f.history.undo();
            f.process();
            assertTrue(f.history.historyIds().entityOfHistoryId(rootHistory) >= 0);
            int restoredChild = f.history.historyIds().entityOfHistoryId(childHistory);
            assertTrue(restoredChild >= 0);
            assertEquals(10, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(restoredChild).parentStableId);
        } finally {
            f.close();
        }
    }

    @Test
    public void mixedMembersAndStandaloneEntityDeleteAndUndoAsOneHistoryStep() {
        Fixture f = new Fixture();
        try {
            f.root(10, -1);
            int point = f.member(20, 10, EntityKind.POINT_LIGHT, 2);
            int cone = f.member(21, 10, EntityKind.CONE_LIGHT, 3);
            int standalone = f.core(30, EntityKind.SPRITE, 8);
            f.process();
            long pointHistory = f.history.historyIds().ensureForEntity(point);
            long coneHistory = f.history.historyIds().ensureForEntity(cone);
            long standaloneHistory = f.history.historyIds().ensureForEntity(standalone);

            Command command = DeleteEntitiesCommandFactory.create(
                    f.world, f.history.historyIds(),
                    new IntArray(new int[]{point, standalone, cone}), null);
            f.history.execute(command);
            f.process();
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(pointHistory));
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(coneHistory));
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(standaloneHistory));

            f.history.undo();
            f.process();
            int restoredPoint = f.history.historyIds().entityOfHistoryId(pointHistory);
            int restoredCone = f.history.historyIds().entityOfHistoryId(coneHistory);
            assertTrue(restoredPoint >= 0);
            assertTrue(restoredCone >= 0);
            assertTrue(f.history.historyIds().entityOfHistoryId(standaloneHistory) >= 0);
            assertEquals(10, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(restoredPoint).parentStableId);
            assertEquals(10, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(restoredCone).parentStableId);
            assertFalse(f.history.canUndo());
            assertTrue(f.history.canRedo());

            f.history.redo();
            f.process();
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(pointHistory));
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(coneHistory));
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(standaloneHistory));
        } finally {
            f.close();
        }
    }

    private static void assertLeaf(EntityKind kind) {
        Fixture f = new Fixture();
        try {
            f.root(10, -1);
            int child = f.member(20, 10, kind, 7);
            TransformComponent authored = f.world.getMapper(TransformComponent.class).get(child);
            authored.x = 3.5f;
            authored.y = -4.25f;
            authored.rotationRad = 0.75f;
            authored.scaleX = 1.5f;
            authored.scaleY = 1.5f;
            authored.originX = 2f;
            authored.originY = 3f;
            authored.refreshCaches();
            f.process();
            long historyId = f.history.historyIds().ensureForEntity(child);

            f.history.execute(new DeleteGameObjectHierarchyCommand(
                    f.world, f.history.historyIds(), new IntArray(new int[]{child}), null));
            f.process();
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(historyId));
            assertEquals(-1, f.identities.findByStableId(20));

            f.history.undo();
            f.process();
            int restored = f.history.historyIds().entityOfHistoryId(historyId);
            assertTrue(restored >= 0);
            assertEquals(20, f.world.getMapper(PixscapeIdentityComponent.class)
                    .get(restored).stableId);
            assertEquals(10, f.world.getMapper(GameObjectMemberComponent.class)
                    .get(restored).parentStableId);
            assertEquals(7, f.world.getMapper(EntityIndexComponent.class).get(restored).zIndex);
            TransformComponent transform = f.world.getMapper(TransformComponent.class).get(restored);
            assertEquals(3.5f, transform.x, 0f);
            assertEquals(-4.25f, transform.y, 0f);
            assertEquals(0.75f, transform.rotationRad, 0f);
            assertEquals(1.5f, transform.scaleX, 0f);
            assertEquals(2f, transform.originX, 0f);
            assertMarker(f.world, restored, kind);

            f.history.redo();
            f.process();
            assertEquals(-1, f.history.historyIds().entityOfHistoryId(historyId));
            assertFalse(f.world.getEntityManager().isActive(restored));
        } finally {
            f.close();
        }
    }

    private static void assertMarker(World world, int entityId, EntityKind kind) {
        assertEquals(kind, world.getMapper(EntityMetaComponent.class).get(entityId).kind);
        assertEquals(kind == EntityKind.ANIMATION,
                world.getMapper(AnimationComponent.class).has(entityId));
        assertEquals(kind == EntityKind.POINT_LIGHT,
                world.getMapper(PointLightComponent.class).has(entityId));
        assertEquals(kind == EntityKind.CONE_LIGHT,
                world.getMapper(ConeLightComponent.class).has(entityId));
    }

    private static final class Fixture {
        final World world = new World();
        final HistoryManager history = new HistoryManager(8);
        final IdentityRegistry identities = new IdentityRegistry();

        Fixture() {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 100;
            identities.bind(world, meta);
        }

        int root(int stableId, int parentStableId) {
            int entityId = core(stableId, EntityKind.GAME_OBJECT, 0);
            world.getMapper(GameObjectComponent.class).create(entityId).sourceAssetId = "";
            if (parentStableId > 0) {
                world.getMapper(GameObjectMemberComponent.class)
                        .create(entityId).parentStableId = parentStableId;
            }
            return entityId;
        }

        int member(int stableId, int parentStableId, EntityKind kind, int z) {
            int entityId = core(stableId, kind, z);
            world.getMapper(GameObjectMemberComponent.class)
                    .create(entityId).parentStableId = parentStableId;
            switch (kind) {
                case ANIMATION -> world.getMapper(AnimationComponent.class).create(entityId);
                case POINT_LIGHT -> world.getMapper(PointLightComponent.class).create(entityId);
                case CONE_LIGHT -> world.getMapper(ConeLightComponent.class).create(entityId);
                default -> { }
            }
            return entityId;
        }

        int core(int stableId, EntityKind kind, int z) {
            int entityId = world.create();
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                    .create(entityId);
            identity.stableId = stableId;
            identity.name = kind.name();
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class)
                    .create(entityId);
            index.layerIndex = 4;
            index.zIndex = z;
            TransformComponent transform = world.getMapper(TransformComponent.class)
                    .create(entityId);
            transform.scaleX = 1f;
            transform.scaleY = 1f;
            transform.refreshCaches();
            EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).create(entityId);
            meta.kind = kind;
            meta.note = "";
            return entityId;
        }

        void process() {
            world.process();
            identities.rebuild();
        }

        void close() {
            identities.bind(null, null);
            world.dispose();
        }
    }
}
