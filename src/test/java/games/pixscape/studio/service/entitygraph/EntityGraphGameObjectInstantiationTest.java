package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EntityGraphGameObjectInstantiationTest {

    @Test
    public void pasteBuildsHierarchyWithFreshStableAndHistoryIdsAndUndoRedoRestoresThem() {
        Fixture f = new Fixture();
        try {
            int root = f.root(100, -1, 5f, 7f, 0.4f, 1.5f, 2f, 3f, 9, "source/tree");
            int child = f.member(200, 100, 4f, -2f, 0.2f, 1f, 0f, 0f, 3);
            f.properties.create(child).properties = new PropertySet()
                    .putObjectStableId("root", 100)
                    .putObjectStableId("unset", -1)
                    .putClass("links", "References",
                            new PropertySet().putObjectStableId("nestedRoot", 100));
            f.process();
            EntityGraph graph = f.capture(root);

            EntityGraphInstantiationResult result = f.instantiate(graph, 7, 20f, 30f);
            f.process();
            int copiedRoot = result.sourceToCreated().get(1, -1);
            int copiedChild = result.sourceToCreated().get(2, -1);
            assertEquals(2, result.createdIds().size);
            assertIds(result.createdRootIds(), copiedRoot);
            assertHierarchy(f, copiedRoot, copiedChild, 7, 25f, 37f, 4f, -2f, 9, 3);
            int rootStableId = f.identities.get(copiedRoot).stableId;
            int childStableId = f.identities.get(copiedChild).stableId;
            assertNotEquals(100, rootStableId);
            assertNotEquals(200, childStableId);
            assertNotEquals(1, rootStableId);
            assertNotEquals(2, childStableId);
            assertEquals("source/tree", f.gameObjects.get(copiedRoot).sourceAssetId);
            assertEquals(rootStableId, f.members.get(copiedChild).parentStableId);
            assertEquals(rootStableId, f.properties.get(copiedChild).properties
                    .getObjectStableId("root", -2));
            assertEquals(rootStableId, f.properties.get(copiedChild).properties
                    .getClassValue("links").properties().getObjectStableId("nestedRoot", -2));
            assertEquals(-1, f.properties.get(copiedChild).properties
                    .getObjectStableId("unset", -2));
            long rootHistoryId = f.history.historyIds().historyIdOfEntity(copiedRoot);
            long childHistoryId = f.history.historyIds().historyIdOfEntity(copiedChild);

            f.history.undo();
            f.process();
            assertFalse(f.world.getEntityManager().isActive(copiedRoot));
            assertFalse(f.world.getEntityManager().isActive(copiedChild));
            assertFalse(f.history.canUndo());

            f.history.redo();
            f.process();
            int restoredRoot = result.sourceToCreated().get(1, -1);
            int restoredChild = result.sourceToCreated().get(2, -1);
            assertEquals(2, result.createdIds().size);
            assertIds(result.createdRootIds(), restoredRoot);
            assertEquals(rootStableId, f.identities.get(restoredRoot).stableId);
            assertEquals(childStableId, f.identities.get(restoredChild).stableId);
            assertEquals(rootHistoryId, f.history.historyIds().historyIdOfEntity(restoredRoot));
            assertEquals(childHistoryId, f.history.historyIds().historyIdOfEntity(restoredChild));
            assertHierarchy(f, restoredRoot, restoredChild, 7, 25f, 37f, 4f, -2f, 9, 3);

            f.history.undo();
            f.process();
            f.history.redo();
            f.process();
            int secondRedoRoot = result.sourceToCreated().get(1, -1);
            int secondRedoChild = result.sourceToCreated().get(2, -1);
            assertEquals(2, result.createdIds().size);
            assertIds(result.createdIds(), secondRedoRoot, secondRedoChild);
            assertIds(result.createdRootIds(), secondRedoRoot);
            assertEquals(rootHistoryId, f.history.historyIds().historyIdOfEntity(secondRedoRoot));
            assertEquals(childHistoryId, f.history.historyIds().historyIdOfEntity(secondRedoChild));
        } finally {
            f.dispose();
        }
    }

    @Test
    public void repeatedPasteUsesIndependentStableIdsAndObjectReferencesWithoutMutatingGraph() {
        Fixture f = new Fixture();
        try {
            int root = f.root(100, -1, 0f, 0f, 0f, 1f, 0f, 0f, 2, "");
            int first = f.member(200, 100, 1f, 2f, 0f, 1f, 0f, 0f, 3);
            int sibling = f.member(300, 100, 3f, 4f, 0f, 1f, 0f, 0f, 4);
            f.properties.create(first).properties = new PropertySet()
                    .putObjectStableId("sibling", 300);
            f.process();
            EntityGraph graph = f.capture(root);

            EntityGraphInstantiationResult firstPaste = f.instantiate(graph, 1, 10f, 0f);
            f.process();
            EntityGraphInstantiationResult secondPaste = f.instantiate(graph, 1, 20f, 0f);
            f.process();
            int firstSibling = firstPaste.sourceToCreated().get(3, -1);
            int secondSibling = secondPaste.sourceToCreated().get(3, -1);
            int firstChild = firstPaste.sourceToCreated().get(2, -1);
            int secondChild = secondPaste.sourceToCreated().get(2, -1);
            int firstSiblingStable = f.identities.get(firstSibling).stableId;
            int secondSiblingStable = f.identities.get(secondSibling).stableId;
            assertNotEquals(firstSiblingStable, secondSiblingStable);
            assertEquals(firstSiblingStable, f.properties.get(firstChild).properties
                    .getObjectStableId("sibling", -1));
            assertEquals(secondSiblingStable, f.properties.get(secondChild).properties
                    .getObjectStableId("sibling", -1));
            assertEquals(3, graph.entries().get(1).customProperties()
                    .getObjectStableId("sibling", -1));
        } finally {
            f.dispose();
        }
    }

    @Test
    public void nestedClipboardRootReceivesOffsetOnceWhileItsDescendantsStayLocal() {
        Fixture f = new Fixture();
        try {
            int outer = f.root(100, -1, 10f, 20f, 0.5f, 1.5f, 2f, 1f, 4, "");
            int nested = f.root(200, 100, 3f, -2f, 0.2f, 1f, 0f, 0f, 5, "");
            int leaf = f.member(300, 200, 6f, 7f, 0.1f, 1f, 0f, 0f, 6);
            f.process();
            WorldTransformState sourceWorld = f.hierarchy.worldTransforms();
            float nestedWorldX = sourceWorld.x[nested];
            float nestedWorldY = sourceWorld.y[nested];
            EntityGraph graph = f.capture(nested);

            EntityGraphInstantiationResult result = f.instantiate(graph, 8, 11f, -9f);
            f.process();
            int copiedRoot = result.sourceToCreated().get(1, -1);
            int copiedLeaf = result.sourceToCreated().get(2, -1);
            assertEquals(nestedWorldX + 11f, f.transforms.get(copiedRoot).x, 0.0001f);
            assertEquals(nestedWorldY - 9f, f.transforms.get(copiedRoot).y, 0.0001f);
            assertEquals(6f, f.transforms.get(copiedLeaf).x, 0f);
            assertEquals(7f, f.transforms.get(copiedLeaf).y, 0f);
            assertEquals(f.identities.get(copiedRoot).stableId,
                    f.members.get(copiedLeaf).parentStableId);
            assertTrue(outer >= 0);
        } finally {
            f.dispose();
        }
    }

    @Test
    public void pastePreservesNestedGameObjectParentageAndLocalZValues() {
        Fixture f = new Fixture();
        try {
            int root = f.root(100, -1, 1f, 2f, 0f, 1f, 0f, 0f, 10, "");
            int nested = f.root(200, 100, 3f, 4f, 0.25f, 1f, 1f, 2f, 4, "nested/source");
            int leaf = f.member(300, 200, 5f, 6f, 0.5f, 1f, 0f, 0f, 2);
            f.process();
            EntityGraphInstantiationResult result = f.instantiate(f.capture(root), 9, 8f, -3f);
            f.process();

            int copiedRoot = result.sourceToCreated().get(1, -1);
            int copiedNested = result.sourceToCreated().get(2, -1);
            int copiedLeaf = result.sourceToCreated().get(3, -1);
            assertTrue(f.gameObjects.has(copiedNested));
            assertEquals(f.identities.get(copiedRoot).stableId,
                    f.members.get(copiedNested).parentStableId);
            assertEquals(f.identities.get(copiedNested).stableId,
                    f.members.get(copiedLeaf).parentStableId);
            assertEquals("nested/source", f.gameObjects.get(copiedNested).sourceAssetId);
            assertEquals(9f, f.transforms.get(copiedRoot).x, 0f);
            assertEquals(-1f, f.transforms.get(copiedRoot).y, 0f);
            assertEquals(3f, f.transforms.get(copiedNested).x, 0f);
            assertEquals(4f, f.transforms.get(copiedNested).y, 0f);
            assertEquals(5f, f.transforms.get(copiedLeaf).x, 0f);
            assertEquals(6f, f.transforms.get(copiedLeaf).y, 0f);
            assertEquals(10, f.indexes.get(copiedRoot).zIndex);
            assertEquals(4, f.indexes.get(copiedNested).zIndex);
            assertEquals(2, f.indexes.get(copiedLeaf).zIndex);
        } finally {
            f.dispose();
        }
    }

    @Test
    public void multipleGraphRootsReceiveTheSameOffsetAndKeepTheirRelativeLayout() {
        Fixture f = new Fixture();
        try {
            int firstRoot = f.root(100, -1, 2f, 3f, 0f, 1f, 0f, 0f, 1, "");
            int secondRoot = f.root(200, -1, 20f, -4f, 0f, 1f, 0f, 0f, 2, "");
            f.process();
            EntityGraph graph = f.capture(firstRoot, secondRoot);

            EntityGraphInstantiationResult result = f.instantiate(graph, 6, 9f, 11f);
            f.process();
            int copiedFirst = result.sourceToCreated().get(1, -1);
            int copiedSecond = result.sourceToCreated().get(2, -1);
            assertIds(result.createdRootIds(), copiedFirst, copiedSecond);
            assertEquals(11f, f.transforms.get(copiedFirst).x, 0f);
            assertEquals(14f, f.transforms.get(copiedFirst).y, 0f);
            assertEquals(29f, f.transforms.get(copiedSecond).x, 0f);
            assertEquals(7f, f.transforms.get(copiedSecond).y, 0f);
            assertEquals(18f, f.transforms.get(copiedSecond).x - f.transforms.get(copiedFirst).x, 0f);
            assertEquals(-7f, f.transforms.get(copiedSecond).y - f.transforms.get(copiedFirst).y, 0f);
        } finally {
            f.dispose();
        }
    }

    @Test
    public void ordinaryFlatGraphKeepsExistingPasteBehaviorAndReportsItsRoot() {
        Fixture f = new Fixture();
        try {
            int source = f.ordinary(100, 3f, 4f, 7);
            f.process();
            EntityGraph graph = new EntityGraphCaptureService(f.world)
                    .capture(new IntArray(new int[]{source}));

            EntityGraphInstantiationResult result = f.instantiate(graph, 5, 8f, -2f);
            f.process();
            int copied = result.sourceToCreated().get(source, -1);
            assertIds(result.createdIds(), copied);
            assertIds(result.createdRootIds(), copied);
            assertEquals(5, f.indexes.get(copied).layerIndex);
            assertEquals(11f, f.transforms.get(copied).x, 0f);
            assertEquals(2f, f.transforms.get(copied).y, 0f);
            assertEquals(7, f.indexes.get(copied).zIndex);
            assertNotEquals(f.identities.get(source).stableId, f.identities.get(copied).stableId);
        } finally {
            f.dispose();
        }
    }

    @Test
    public void cyclicPreparedHierarchyRejectsBeforeHistoryPublication() {
        Fixture f = new Fixture();
        try {
            int first = f.root(100, -1, 0f, 0f, 0f, 1f, 0f, 0f, 0, "");
            int second = f.root(200, -1, 0f, 0f, 0f, 1f, 0f, 0f, 0, "");
            f.process();
            GenericEntityInitializer firstInitializer = new GenericEntityInitializer(f.world);
            firstInitializer.syncFrom(first);
            GenericEntityInitializer secondInitializer = new GenericEntityInitializer(f.world);
            secondInitializer.syncFrom(second);
            EntityGraph graph = new EntityGraph(java.util.List.of(
                    new EntityGraphEntry(1, 2, true, firstInitializer, null),
                    new EntityGraphEntry(2, 1, true, secondInitializer, null)));
            int activeBefore = f.activeEntityCount();

            try {
                f.instantiate(graph, 0, 0f, 0f);
                fail("Expected cyclic graph rejection.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("cycle"));
            }
            assertEquals(activeBefore, f.activeEntityCount());
            assertFalse(f.history.canUndo());
        } finally {
            f.dispose();
        }
    }

    private static void assertHierarchy(Fixture f, int root, int child, int layer,
                                        float rootX, float rootY, float childX, float childY,
                                        int rootZ, int childZ) {
        assertTrue(f.gameObjects.has(root));
        assertFalse(f.members.has(root));
        assertTrue(f.members.has(child));
        assertEquals(layer, f.indexes.get(root).layerIndex);
        assertEquals(layer, f.indexes.get(child).layerIndex);
        assertEquals(rootX, f.transforms.get(root).x, 0f);
        assertEquals(rootY, f.transforms.get(root).y, 0f);
        assertEquals(childX, f.transforms.get(child).x, 0f);
        assertEquals(childY, f.transforms.get(child).y, 0f);
        assertEquals(rootZ, f.indexes.get(root).zIndex);
        assertEquals(childZ, f.indexes.get(child).zIndex);
    }

    private static void assertIds(IntArray actual, int... expected) {
        assertEquals(expected.length, actual.size);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual.get(i));
    }

    private static final class Fixture {
        final SceneMeta sceneMeta = new SceneMeta();
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        final GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem(32);
        final World world = new World(new WorldConfigurationBuilder().with(dirty, hierarchy).build());
        final HistoryManager history = new HistoryManager(32);
        final IdentityRegistry identityRegistry = new IdentityRegistry();
        final ComponentMapper<PixscapeIdentityComponent> identities = world.getMapper(PixscapeIdentityComponent.class);
        final ComponentMapper<TransformComponent> transforms = world.getMapper(TransformComponent.class);
        final ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        final ComponentMapper<GameObjectComponent> gameObjects = world.getMapper(GameObjectComponent.class);
        final ComponentMapper<GameObjectMemberComponent> members = world.getMapper(GameObjectMemberComponent.class);
        final ComponentMapper<CustomPropertiesComponent> properties = world.getMapper(CustomPropertiesComponent.class);
        final EntityGraphInstantiationService instantiator;

        Fixture() {
            sceneMeta.nextEntityStableId = 1_000;
            identityRegistry.bind(world, sceneMeta);
            instantiator = new EntityGraphInstantiationService(
                    world, history, identityRegistry, new PhysicsService(world, null, sceneMeta), () -> true);
        }

        int root(int stableId, int parentStableId, float x, float y, float rotation,
                 float scale, float originX, float originY, int z, String sourceAssetId) {
            int entity = base(stableId, parentStableId, x, y, rotation, scale, originX, originY, z);
            gameObjects.create(entity).sourceAssetId = sourceAssetId;
            return entity;
        }

        int member(int stableId, int parentStableId, float x, float y, float rotation,
                   float scale, float originX, float originY, int z) {
            return base(stableId, parentStableId, x, y, rotation, scale, originX, originY, z);
        }

        int ordinary(int stableId, float x, float y, int z) {
            return base(stableId, -1, x, y, 0f, 1f, 0f, 0f, z);
        }

        EntityGraph capture(int... entityIds) {
            return new EntityGraphCaptureService(world)
                    .captureGameObjectClipboard(new IntArray(entityIds));
        }

        EntityGraphInstantiationResult instantiate(EntityGraph graph, int layer, float dx, float dy) {
            return instantiator.instantiate(graph, layer, dx, dy, "Paste");
        }

        void process() {
            world.process();
        }

        int activeEntityCount() {
            return world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size();
        }

        void dispose() {
            world.dispose();
        }

        private int base(int stableId, int parentStableId, float x, float y, float rotation,
                         float scale, float originX, float originY, int z) {
            int entity = world.create();
            identities.create(entity).stableId = stableId;
            TransformComponent transform = transforms.create(entity);
            transform.x = x;
            transform.y = y;
            transform.rotationRad = rotation;
            transform.scaleX = scale;
            transform.scaleY = scale;
            transform.originX = originX;
            transform.originY = originY;
            indexes.create(entity).layerIndex = 0;
            indexes.get(entity).zIndex = z;
            if (parentStableId >= 0) members.create(entity).parentStableId = parentStableId;
            return entity;
        }
    }
}
