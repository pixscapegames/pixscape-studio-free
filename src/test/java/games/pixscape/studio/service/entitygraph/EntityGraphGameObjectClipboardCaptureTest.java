package games.pixscape.studio.service.entitygraph;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.hierarchy.GameObjectTransformMath;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EntityGraphGameObjectClipboardCaptureTest {

    @Test
    public void captureBuildsDeterministicGraphLocalHierarchyAndNormalizesNestedRootToWorldPose() {
        Fixture f = new Fixture();
        try {
            int outer = f.gameObject(10, -1, 10f, 20f, (float) Math.toRadians(90), 2f, 0f, 0f);
            int nested = f.gameObject(20, 10, 4f, 3f, (float) Math.toRadians(20), 1.5f, 2f, 1f);
            int leaf = f.member(30, 20, 7f, 8f);
            int standalone = f.standalone(5, -5f, 6f);
            f.world.process();

            PropertySet nestedClass = new PropertySet()
                    .putObjectStableId("nested", 20);
            f.properties.create(leaf).properties = new PropertySet()
                    .putObjectStableId("root", 10)
                    .putObjectStableId("none", -1)
                    .putClass("links", "References", nestedClass);

            EntityGraph graph = new EntityGraphCaptureService(f.world).captureGameObjectClipboard(
                    new IntArray(new int[]{nested, standalone, outer, nested}));

            assertEquals(4, graph.size());
            EntityGraphEntry standaloneEntry = graph.entries().get(0);
            EntityGraphEntry outerEntry = graph.entries().get(1);
            EntityGraphEntry nestedEntry = graph.entries().get(2);
            EntityGraphEntry leafEntry = graph.entries().get(3);
            assertEntry(standaloneEntry, 1, -1, false);
            assertEntry(outerEntry, 2, -1, true);
            assertEntry(nestedEntry, 3, 2, true);
            assertEntry(leafEntry, 4, 3, false);

            GenericEntitySnapshotData nestedSnapshot = nestedEntry.initializer().toSnapshotData(0);
            assertEquals(4f, nestedSnapshot.x, 0f);
            assertEquals(3f, nestedSnapshot.y, 0f);
            assertEquals(4f, f.transforms.get(nested).x, 0f);
            assertEquals(3f, f.transforms.get(nested).y, 0f);

            PropertySet copied = leafEntry.customProperties();
            assertEquals(2, copied.getObjectStableId("root", -2));
            assertEquals(-1, copied.getObjectStableId("none", -2));
            assertEquals(3, copied.getClassValue("links").properties()
                    .getObjectStableId("nested", -2));

            f.properties.get(leaf).properties = null;
            EntityGraph nestedOnly = new EntityGraphCaptureService(f.world)
                    .captureGameObjectClipboard(new IntArray(new int[]{nested}));
            GenericEntitySnapshotData nestedRootSnapshot = nestedOnly.entries().get(0)
                    .initializer().toSnapshotData(0);
            WorldTransformState worldTransforms = f.hierarchy.worldTransforms();
            assertEquals(worldTransforms.x[nested], nestedRootSnapshot.x, 0f);
            assertEquals(worldTransforms.y[nested], nestedRootSnapshot.y, 0f);
            assertEquals(worldTransforms.rotationRad[nested], nestedRootSnapshot.rotationRad, 0f);
            assertEquals(worldTransforms.scaleX[nested], nestedRootSnapshot.scaleX, 0f);
            assertEquals(worldTransforms.scaleY[nested], nestedRootSnapshot.scaleY, 0f);
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void selectionNormalizerRejectsAnOrdinaryMemberSelectedAlone() {
        Fixture f = new Fixture();
        try {
            int root = f.gameObject(10, -1, 0f, 0f, 0f, 1f, 0f, 0f);
            int member = f.member(20, 10, 0f, 0f);
            f.world.process();

            try {
                new ClipboardSelectionNormalizer(f.world).normalize(new IntArray(new int[]{member}));
                fail("Expected V1 to reject copying a Game Object member alone.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("member alone"));
            }
            assertFalse(new ClipboardSelectionNormalizer(f.world)
                    .normalize(new IntArray(new int[]{root})).isEmpty());
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void selectionNormalizerKeepsStandaloneAndRootsAndDropsCoveredDescendantsDeterministically() {
        Fixture f = new Fixture();
        try {
            int outer = f.gameObject(40, -1, 0f, 0f, 0f, 1f, 0f, 0f);
            int nested = f.gameObject(50, 40, 0f, 0f, 0f, 1f, 0f, 0f);
            int child = f.member(60, 50, 0f, 0f);
            int standalone = f.standalone(10, 0f, 0f);
            int independent = f.gameObject(20, -1, 0f, 0f, 0f, 1f, 0f, 0f);
            f.world.process();
            ClipboardSelectionNormalizer normalizer = new ClipboardSelectionNormalizer(f.world);

            assertIds(normalizer.normalize(new IntArray(new int[]{standalone})), standalone);
            assertIds(normalizer.normalize(new IntArray(new int[]{outer})), outer);
            assertIds(normalizer.normalize(new IntArray(new int[]{nested})), nested);
            assertIds(normalizer.normalize(new IntArray(new int[]{outer, child})), outer);
            assertIds(normalizer.normalize(new IntArray(new int[]{nested, outer})), outer);
            assertIds(normalizer.normalize(new IntArray(new int[]{outer, independent, standalone})),
                    standalone, independent, outer);
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void capturePreservesTopLevelRootAndCapturesDeepHierarchyOnceWithGraphLocalParents() {
        Fixture f = new Fixture();
        try {
            int root = f.gameObject(100, -1, 3f, 4f, 0.3f, 1.25f, 2f, 1f);
            int child = f.member(200, 100, 5f, 6f);
            int nested = f.gameObject(300, 100, 7f, 8f, 0.2f, 1f, 0f, 0f);
            int deepLeaf = f.member(400, 300, 9f, 10f);
            f.world.process();

            EntityGraph graph = new EntityGraphCaptureService(f.world)
                    .captureGameObjectClipboard(new IntArray(new int[]{root, child, root}));

            assertEquals(4, graph.size());
            assertEntry(graph.entries().get(0), 1, -1, true);
            assertEntry(graph.entries().get(1), 2, 1, false);
            assertEntry(graph.entries().get(2), 3, 1, true);
            assertEntry(graph.entries().get(3), 4, 3, false);
            GenericEntitySnapshotData rootSnapshot = graph.entries().get(0).initializer()
                    .toSnapshotData(0);
            assertEquals(3f, rootSnapshot.x, 0f);
            assertEquals(4f, rootSnapshot.y, 0f);
            assertEquals(0.3f, rootSnapshot.rotationRad, 0f);
            assertEquals(1.25f, rootSnapshot.scaleX, 0f);
            assertEquals(2f, rootSnapshot.originX, 0f);
            assertEquals(1f, rootSnapshot.originY, 0f);
            assertEquals(5f, graph.entries().get(1).initializer().toSnapshotData(0).x, 0f);
            assertEquals(9f, graph.entries().get(3).initializer().toSnapshotData(0).x, 0f);

            EntityGraph repeat = new EntityGraphCaptureService(f.world)
                    .captureGameObjectClipboard(new IntArray(new int[]{child, root}));
            assertGraphShape(graph, repeat);
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void captureOfOneTopLevelGameObjectProducesOneUnparentedGraphRoot() {
        Fixture f = new Fixture();
        try {
            int root = f.gameObject(10, -1, 12f, -4f, 0.4f, 1.5f, 3f, 2f);
            f.world.process();

            EntityGraph graph = new EntityGraphCaptureService(f.world)
                    .captureGameObjectClipboard(new IntArray(new int[]{root}));
            assertEquals(1, graph.size());
            assertEntry(graph.entries().get(0), 1, -1, true);
            GenericEntitySnapshotData snapshot = graph.entries().get(0).initializer()
                    .toSnapshotData(0);
            assertEquals(12f, snapshot.x, 0f);
            assertEquals(-4f, snapshot.y, 0f);
            assertEquals(0.4f, snapshot.rotationRad, 0f);
            assertEquals(1.5f, snapshot.scaleX, 0f);
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void nestedRootUsesWorldPoseForTranslatedRotatedScaledAndCombinedParents() {
        assertNestedRootWorldPose(10f, 20f, 0f, 1f, 0f, 0f);
        assertNestedRootWorldPose(0f, 0f, (float) Math.toRadians(90), 1f, 2f, 3f);
        assertNestedRootWorldPose(0f, 0f, 0f, 2f, 0f, 0f);
        assertNestedRootWorldPose(10f, -7f, (float) Math.toRadians(35), 1.5f, 2f, -1f);
    }

    @Test
    public void normalizesInternalObjectReferencesIncludingSiblingRootAndNestedClassValues() {
        Fixture f = new Fixture();
        try {
            int root = f.gameObject(100, -1, 0f, 0f, 0f, 1f, 0f, 0f);
            int firstChild = f.member(200, 100, 0f, 0f);
            int sibling = f.member(300, 100, 0f, 0f);
            PropertySet nested = new PropertySet().putObjectStableId("nestedSibling", 300);
            f.properties.create(firstChild).properties = new PropertySet()
                    .putObjectStableId("sibling", 300)
                    .putObjectStableId("root", 100)
                    .putObjectStableId("unset", -1)
                    .putClass("links", "References", nested);
            f.world.process();

            EntityGraph graph = new EntityGraphCaptureService(f.world)
                    .captureGameObjectClipboard(new IntArray(new int[]{root}));
            PropertySet copied = graph.entries().get(1).customProperties();
            assertEquals(3, copied.getObjectStableId("sibling", -2));
            assertEquals(1, copied.getObjectStableId("root", -2));
            assertEquals(-1, copied.getObjectStableId("unset", -2));
            assertEquals(3, copied.getClassValue("links").properties()
                    .getObjectStableId("nestedSibling", -2));
            assertFalse(copied.getObjectStableId("sibling", -2) == 300);
            assertFalse(copied.getObjectStableId("root", -2) == 100);
            assertTrue(graph.entries().get(2).sourceEntityId() == 3);
            assertTrue(sibling >= 0);
        } finally {
            f.world.dispose();
        }
    }

    @Test
    public void captureRejectsExternalObjectReferencesBeforeReturningAGraph() {
        Fixture f = new Fixture();
        try {
            int root = f.gameObject(10, -1, 0f, 0f, 0f, 1f, 0f, 0f);
            int leaf = f.member(20, 10, 0f, 0f);
            f.properties.create(leaf).properties = new PropertySet().putObjectStableId("external", 999);
            f.world.process();

            try {
                new EntityGraphCaptureService(f.world).captureGameObjectClipboard(new IntArray(new int[]{root}));
                fail("Expected external OBJECT reference rejection.");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("external OBJECT"));
            }
            assertEquals(999, f.properties.get(leaf).properties.getObjectStableId("external", -1));
            assertEquals(0f, f.transforms.get(leaf).x, 0f);
        } finally {
            f.world.dispose();
        }
    }

    private static void assertEntry(EntityGraphEntry entry, int sourceId, int parentSourceId,
                                    boolean gameObjectRoot) {
        assertEquals(sourceId, entry.sourceEntityId());
        assertEquals(parentSourceId, entry.parentSourceEntityId());
        assertEquals(gameObjectRoot, entry.gameObjectRoot());
    }

    private static void assertIds(IntArray actual, int... expected) {
        assertEquals(expected.length, actual.size);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], actual.get(i));
    }

    private static void assertNestedRootWorldPose(float parentX, float parentY, float parentRotation,
                                                  float parentScale, float parentOriginX,
                                                  float parentOriginY) {
        Fixture f = new Fixture();
        try {
            int parent = f.gameObject(10, -1, parentX, parentY, parentRotation, parentScale,
                    parentOriginX, parentOriginY);
            int nested = f.gameObject(20, 10, 4f, -3f, 0.2f, 1.25f, 3f, 2f);
            int leaf = f.member(30, 20, 8f, 9f);
            TransformComponent expected = new TransformComponent();
            GameObjectTransformMath.localToWorld(f.transforms.get(parent), f.transforms.get(nested),
                    true, expected);
            f.world.process();

            EntityGraph graph = new EntityGraphCaptureService(f.world)
                    .captureGameObjectClipboard(new IntArray(new int[]{nested}));
            GenericEntitySnapshotData rootSnapshot = graph.entries().get(0).initializer()
                    .toSnapshotData(0);
            GenericEntitySnapshotData leafSnapshot = graph.entries().get(1).initializer()
                    .toSnapshotData(0);
            assertEquals(expected.x, rootSnapshot.x, 0.0001f);
            assertEquals(expected.y, rootSnapshot.y, 0.0001f);
            assertEquals(expected.rotationRad, rootSnapshot.rotationRad, 0.0001f);
            assertEquals(expected.scaleX, rootSnapshot.scaleX, 0.0001f);
            assertEquals(expected.scaleY, rootSnapshot.scaleY, 0.0001f);
            assertEquals(3f, rootSnapshot.originX, 0f);
            assertEquals(2f, rootSnapshot.originY, 0f);
            assertEquals(4f, f.transforms.get(nested).x, 0f);
            assertEquals(-3f, f.transforms.get(nested).y, 0f);
            assertEquals(8f, f.transforms.get(leaf).x, 0f);
            assertEquals(9f, leafSnapshot.y, 0f);
        } finally {
            f.world.dispose();
        }
    }

    private static void assertGraphShape(EntityGraph first, EntityGraph second) {
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            EntityGraphEntry left = first.entries().get(i);
            EntityGraphEntry right = second.entries().get(i);
            assertEquals(left.sourceEntityId(), right.sourceEntityId());
            assertEquals(left.parentSourceEntityId(), right.parentSourceEntityId());
            assertEquals(left.gameObjectRoot(), right.gameObjectRoot());
        }
    }

    private static final class Fixture {
        final GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem(16);
        final World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(16), hierarchy).build());
        final ComponentMapper<TransformComponent> transforms = world.getMapper(TransformComponent.class);
        final ComponentMapper<CustomPropertiesComponent> properties = world.getMapper(CustomPropertiesComponent.class);

        Fixture() {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 1_000;
            new IdentityRegistry().bind(world, meta);
        }

        int gameObject(int stableId, int parentStableId, float x, float y, float rotation,
                       float scale, float originX, float originY) {
            int entity = base(stableId, parentStableId, x, y);
            TransformComponent transform = transforms.get(entity);
            transform.rotationRad = rotation;
            transform.scaleX = scale;
            transform.scaleY = scale;
            transform.originX = originX;
            transform.originY = originY;
            world.getMapper(GameObjectComponent.class).create(entity);
            return entity;
        }

        int member(int stableId, int parentStableId, float x, float y) {
            return base(stableId, parentStableId, x, y);
        }

        int standalone(int stableId, float x, float y) {
            return base(stableId, -1, x, y);
        }

        private int base(int stableId, int parentStableId, float x, float y) {
            int entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
            TransformComponent transform = transforms.create(entity);
            transform.x = x;
            transform.y = y;
            world.getMapper(EntityIndexComponent.class).create(entity);
            if (parentStableId >= 0) {
                world.getMapper(GameObjectMemberComponent.class).create(entity)
                        .parentStableId = parentStableId;
            }
            return entity;
        }
    }
}
