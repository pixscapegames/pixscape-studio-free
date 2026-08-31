package games.pixscape.studio.service.entitygraph;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.PrefabInstanceComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EntityGraphServicesTest {
    @Before
    public void activateSceneAllocator() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
    }

    @Test
    public void capture_includesJointWhenBodiesSelected() {
        World world = new World(new WorldConfiguration());
        int a = body(world); int b = body(world); int j = distanceJoint(world, a, b);

        EntityGraphCaptureService svc = new EntityGraphCaptureService(world);
        EntityGraph graph = svc.capture(arr(a, b));

        assertContains(graph, a); assertContains(graph, b); assertContains(graph, j);
    }

    @Test
    public void capture_excludesJointWhenBodyMissing() {
        World world = new World(new WorldConfiguration());
        int a = body(world); int b = body(world); int j = distanceJoint(world, a, b);

        EntityGraphCaptureService svc = new EntityGraphCaptureService(world);
        EntityGraph graph = svc.capture(arr(a));

        assertContains(graph, a); assertNotContains(graph, j);
    }

    @Test
    public void gameObjectCaptureRequiresARealSelectedRoot() {
        World world = new World(new WorldConfiguration());
        int point = light(world, false);
        int cone = light(world, true);
        EntityGraphCaptureService service = new EntityGraphCaptureService(world);

        EntityGraph generic = service.capture(arr(point, cone));
        EntityGraph gameObject = service.captureForGameObject(arr(point, cone));

        assertContains(generic, point);
        assertContains(generic, cone);
        assertNotContains(gameObject, point);
        assertNotContains(gameObject, cone);
    }

    @Test
    public void genericAndGameObjectCaptureExcludeTiledMapRoots() {
        World world = new World(new WorldConfiguration());
        int map = world.create();
        world.getMapper(EntityIndexComponent.class).create(map).layerIndex = 0;
        world.getMapper(TransformComponent.class).create(map);
        world.getMapper(TiledLayerComponent.class).create(map);
        EntityGraphCaptureService service = new EntityGraphCaptureService(world);

        Assert.assertTrue(service.capture(arr(map)).isEmpty());
        Assert.assertTrue(service.captureForGameObject(arr(map)).isEmpty());
    }

    @Test
    public void instantiate_remapsJointBodyReferences() {
        World world = new World(new WorldConfiguration());
        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta()); reg.rebuild();

        int a = body(world); int b = body(world); int j = distanceJoint(world, a, b);
        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b));

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()), () -> true)
                .instantiate(graph, 0, 0f, 0f, "Test Instantiate");

        int pastedJ = result.sourceToCreated().get(j, -1);
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).get(pastedJ);
        Assert.assertEquals(result.sourceToCreated().get(a, -1), joint.aEid);
        Assert.assertEquals(result.sourceToCreated().get(b, -1), joint.bEid);
    }

    @Test
    public void instantiate_remapsGearJointReferences() {
        World world = new World(new WorldConfiguration());
        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta()); reg.rebuild();

        int a = body(world); int b = body(world); int c = body(world);
        int j1 = revoluteJoint(world, a, b);
        int j2 = prismaticJoint(world, b, c);
        int g = gearJoint(world, a, c, j1, j2);

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b, c));
        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()), () -> true)
                .instantiate(graph, 0, 0f, 0f, "Test Instantiate");

        int pastedG = result.sourceToCreated().get(g, -1);
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).get(pastedG);
        Assert.assertEquals(result.sourceToCreated().get(j1, -1), gear.joint1Eid);
        Assert.assertEquals(result.sourceToCreated().get(j2, -1), gear.joint2Eid);
    }

    @Test
    public void instantiatePrefabAssignsOneLogicalInstanceBeforeHistoryAndRedoPreservesIt() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        identities.rebuild();
        int targetLayer = world.create();
        world.getMapper(games.pixscape.runtime.component.LayerComponent.class)
                .create(targetLayer).layerIndex = 7;
        world.getMapper(games.pixscape.studio.component.LayerMetaComponent.class)
                .create(targetLayer).name = "Target";

        int a = body(world);
        int b = body(world);
        world.getMapper(EntityIndexComponent.class).get(a).layerIndex = 2;
        world.getMapper(EntityIndexComponent.class).get(a).zIndex = 14;
        world.getMapper(EntityIndexComponent.class).get(b).layerIndex = 4;
        world.getMapper(EntityIndexComponent.class).get(b).zIndex = 9;
        int existing = body(world);
        world.getMapper(EntityIndexComponent.class).get(existing).layerIndex = 7;
        world.getMapper(EntityIndexComponent.class).get(existing).zIndex = 12;
        world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class)
                .create(existing).name = "Existing";
        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b));
        EntityGraphInstantiationService service = new EntityGraphInstantiationService(
                world, history, identities, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()), () -> true);

        EntityGraphInstantiationResult result = service.instantiatePrefab(
                graph, 7, 0f, 0f, "Drop Castle", 41, "Castle");
        Assert.assertEquals(2, result.createdIds().size);
        for (int i = 0; i < result.createdIds().size; i++) {
            int entityId = result.createdIds().get(i);
            Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(entityId));
            Assert.assertEquals(1,
                    world.getMapper(PhysicsShapesComponent.class).get(entityId).shapes.size);
        }
        assertPrefabMembers(world, 41, "Castle", 7, 2);
        Assert.assertEquals(1, history.getCursor());
        int droppedAZ = world.getMapper(EntityIndexComponent.class)
                .get(result.sourceToCreated().get(a, -1)).zIndex;
        int droppedBZ = world.getMapper(EntityIndexComponent.class)
                .get(result.sourceToCreated().get(b, -1)).zIndex;
        int existingAfterDrop = world.getMapper(EntityIndexComponent.class).get(existing).zIndex;
        Assert.assertEquals(1, Math.abs(droppedAZ - droppedBZ));
        Assert.assertTrue(existingAfterDrop < Math.min(droppedAZ, droppedBZ)
                || existingAfterDrop > Math.max(droppedAZ, droppedBZ));
        Assert.assertEquals(3, new java.util.HashSet<>(java.util.List.of(
                droppedAZ, droppedBZ, existingAfterDrop)).size());
        Assert.assertEquals(0, Math.min(existingAfterDrop, Math.min(droppedAZ, droppedBZ)));
        Assert.assertEquals(2, Math.max(existingAfterDrop, Math.max(droppedAZ, droppedBZ)));

        history.undo();
        world.process();
        assertPrefabMembers(world, 41, "Castle", 7, 0);
        Assert.assertEquals(12, world.getMapper(EntityIndexComponent.class).get(existing).zIndex);
        history.redo();
        world.process();
        assertPrefabMembers(world, 41, "Castle", 7, 2);
        Assert.assertEquals(existingAfterDrop,
                world.getMapper(EntityIndexComponent.class).get(existing).zIndex);
    }

    @Test
    public void prefabDropNormalizesCompleteMaterializedLayerUsingSourceOrder() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        identities.rebuild();
        int targetLayer = world.create();
        world.getMapper(games.pixscape.runtime.component.LayerComponent.class)
                .create(targetLayer).layerIndex = 7;
        world.getMapper(games.pixscape.studio.component.LayerMetaComponent.class)
                .create(targetLayer).name = "Target";

        IntArray existing = new IntArray();
        for (int z : new int[]{9, 8, 7, 6, 5, 0}) {
            int entityId = body(world);
            world.getMapper(EntityIndexComponent.class).get(entityId).layerIndex = 7;
            world.getMapper(EntityIndexComponent.class).get(entityId).zIndex = z;
            world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class)
                    .create(entityId).name = "Existing " + z;
            existing.add(entityId);
        }

        IntArray sources = new IntArray();
        for (int z : new int[]{139, 141, 142, 140}) {
            int entityId = body(world);
            world.getMapper(EntityIndexComponent.class).get(entityId).layerIndex = 2;
            world.getMapper(EntityIndexComponent.class).get(entityId).zIndex = z;
            world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class)
                    .create(entityId).name = "Prefab " + z;
            sources.add(entityId);
        }
        EntityGraph graph = new EntityGraphCaptureService(world).capture(sources);

        // Match Studio: ItemTreePanel has already materialized this subscription.
        world.process();
        LayerLogicalOrderService logicalOrder = new LayerLogicalOrderService(world);
        logicalOrder.derive(7);

        EntityGraphInstantiationService service = new EntityGraphInstantiationService(
                world, history, identities, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()), () -> true);
        EntityGraphInstantiationResult result = service.instantiatePrefab(
                graph, 7, 0f, 0f, "Drop Observed Prefab", 41, "Observed");

        assertZValues(world, result.createdIds(), 6, 8, 9, 7);
        assertZValues(world, existing, 5, 4, 3, 2, 1, 0);
        assertCompactLayer(world, 7, result.createdIds(), existing);
        world.process();
        Assert.assertArrayEquals(
                new int[]{9, 8, 7, 6},
                prefabZTopToBottom(world, logicalOrder, 7, 41));

        history.undo();
        world.process();
        assertZValues(world, existing, 9, 8, 7, 6, 5, 0);
        assertPrefabMembers(world, 41, "Observed", 7, 0);

        history.redo();
        assertCompactLayer(world, 7, result.createdIds(), existing);
        assertZValues(world, existing, 5, 4, 3, 2, 1, 0);
        world.process();
        Assert.assertArrayEquals(
                new int[]{9, 8, 7, 6},
                prefabZTopToBottom(world, logicalOrder, 7, 41));
    }

    @Test
    public void clipboardInstantiationAlwaysStripsMembershipAndLeavesOriginalsGrouped() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        identities.rebuild();

        int a = body(world);
        int b = body(world);
        for (int entity : new int[]{a, b}) {
            PrefabInstanceComponent prefab =
                    world.getMapper(PrefabInstanceComponent.class).create(entity);
            prefab.instanceId = 10;
            prefab.prefabId = "Castle";
        }
        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b));
        EntityGraphInstantiationResult pasted = new EntityGraphInstantiationService(
                world, history, identities, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()), () -> true)
                .instantiateForClipboard(
                        graph, 3, 0f, 0f, "Paste",
                        EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL);

        Assert.assertEquals(2, pasted.createdIds().size);
        for (int i = 0; i < pasted.createdIds().size; i++) {
            int entity = pasted.createdIds().get(i);
            Assert.assertFalse(world.getMapper(PrefabInstanceComponent.class).has(entity));
            Assert.assertEquals(3, world.getMapper(EntityIndexComponent.class).get(entity).layerIndex);
        }
        Assert.assertEquals(10, world.getMapper(PrefabInstanceComponent.class).get(a).instanceId);
        Assert.assertEquals(10, world.getMapper(PrefabInstanceComponent.class).get(b).instanceId);
    }

    @Test
    public void invalidJointGraphFailsDuringPreparationWithoutProcessingWorld() {
        SentinelSystem sentinel = new SentinelSystem();
        World world = new World(new WorldConfigurationBuilder()
                .with(sentinel)
                .build());
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        identities.rebuild();

        int bodyA = body(world);
        int bodyB = body(world);
        int joint = distanceJoint(world, bodyA, bodyB);
        EntityGraph captured =
                new EntityGraphCaptureService(world).capture(arr(bodyA, bodyB));
        List<EntityGraphEntry> incompleteEntries = new ArrayList<>();
        for (EntityGraphEntry entry : captured.entries()) {
            if (entry.sourceEntityId() != bodyB) {
                incompleteEntries.add(entry);
            }
        }
        EntityGraph incomplete = new EntityGraph(incompleteEntries);
        world.process();
        sentinel.processCount = 0;
        int entitiesBefore = count(world, Aspect.all());
        int shapesBefore = count(world, Aspect.all(PhysicsShapesComponent.class));
        int jointsBefore = count(world, Aspect.all(PhysicsJointComponent.class));

        try {
            new EntityGraphInstantiationService(
                    world, history, identities, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()), () -> true)
                    .instantiate(incomplete, 0, 0f, 0f, "Invalid graph");
            Assert.fail("Missing joint endpoint mapping must reject the graph.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "references missing bEid source"));
        }

        Assert.assertEquals(entitiesBefore, count(world, Aspect.all()));
        Assert.assertEquals(
                shapesBefore,
                count(world, Aspect.all(PhysicsShapesComponent.class)));
        Assert.assertEquals(
                jointsBefore,
                count(world, Aspect.all(PhysicsJointComponent.class)));
        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertEquals(0, history.getCursor());
        Assert.assertTrue(world.getEntityManager().isActive(bodyA));
        Assert.assertTrue(world.getEntityManager().isActive(bodyB));
        Assert.assertTrue(world.getEntityManager().isActive(joint));
        Assert.assertEquals(0, sentinel.processCount);
    }

    private static int count(World world, com.artemis.Aspect.Builder aspect) {
        return world.getAspectSubscriptionManager().get(aspect).getEntities().size();
    }

    private static void assertPrefabMembers(
            World world, int instanceId, String prefabId, int layerIndex, int expectedCount) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PrefabInstanceComponent.class)).getEntities();
        int matching = 0;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            PrefabInstanceComponent prefab =
                    world.getMapper(PrefabInstanceComponent.class).get(entity);
            if (prefab.instanceId != instanceId) continue;
            matching++;
            Assert.assertEquals(prefabId, prefab.prefabId);
            Assert.assertEquals(layerIndex,
                    world.getMapper(EntityIndexComponent.class).get(entity).layerIndex);
        }
        Assert.assertEquals(expectedCount, matching);
    }

    private static void assertCompactLayer(
            World world, int layerIndex, IntArray first, IntArray second) {
        int[] seen = new int[10];
        com.badlogic.gdx.utils.IntSet seenEntities = new com.badlogic.gdx.utils.IntSet();
        int count = 0;
        StringBuilder actual = new StringBuilder();
        for (IntArray entities : new IntArray[]{first, second}) {
            for (int i = 0; i < entities.size; i++) {
                int entityId = entities.get(i);
                if (!world.getEntityManager().isActive(entityId)
                        || !seenEntities.add(entityId)) continue;
                EntityIndexComponent index = world.getMapper(EntityIndexComponent.class)
                        .getSafe(entityId, null);
                if (index == null || index.layerIndex != layerIndex) continue;
                if (actual.length() > 0) actual.append(',');
                actual.append(index.zIndex);
                Assert.assertTrue("zIndex outside compact layer: " + actual,
                        index.zIndex >= 0 && index.zIndex < seen.length);
                seen[index.zIndex]++;
                count++;
            }
        }
        Assert.assertEquals(seen.length, count);
        for (int z = 0; z < seen.length; z++) {
            Assert.assertEquals("non-unique compact zIndex " + z + " in: " + actual,
                    1, seen[z]);
        }
    }

    private static void assertZValues(World world, IntArray entities, int... expected) {
        int activeIndex = 0;
        for (int i = 0; i < entities.size; i++) {
            int entityId = entities.get(i);
            if (!world.getEntityManager().isActive(entityId)) continue;
            Assert.assertTrue("more active entities than expected", activeIndex < expected.length);
            Assert.assertEquals(expected[activeIndex++],
                    world.getMapper(EntityIndexComponent.class).get(entityId).zIndex);
        }
        Assert.assertEquals(expected.length, activeIndex);
    }

    private static int[] prefabZTopToBottom(
            World world, LayerLogicalOrderService service,
            int layerIndex, int prefabInstanceId) {
        for (LayerLogicalOrderService.LogicalItem item : service.derive(layerIndex).items()) {
            if (!item.isPrefab() || item.prefabInstanceId() != prefabInstanceId) continue;
            IntArray members = item.members();
            int[] values = new int[members.size];
            for (int i = 0; i < members.size; i++) {
                values[i] = world.getMapper(EntityIndexComponent.class)
                        .get(members.get(i)).zIndex;
            }
            return values;
        }
        return new int[0];
    }

    private static final class SentinelSystem extends BaseSystem {
        int processCount;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }

    private static IntArray arr(int... ids) { IntArray a = new IntArray(); for (int id : ids) a.add(id); return a; }
    private static int light(World w, boolean cone) {int e=w.create();w.getMapper(TransformComponent.class).create(e);w.getMapper(EntityIndexComponent.class).create(e);if(cone)w.getMapper(ConeLightComponent.class).create(e);else w.getMapper(PointLightComponent.class).create(e);return e;}
    private static int body(World w){int e=w.create();w.getMapper(TransformComponent.class).create(e);w.getMapper(EntityIndexComponent.class).create(e);w.getMapper(PhysicsBodyComponent.class).create(e);PhysicsShapesComponent f=w.getMapper(PhysicsShapesComponent.class).create(e);PhysicsShapeData d=new PhysicsShapeData();d.geometry=new PhysicsGeometryData();d.physicsShapeId=e+1;d.geometry.shapeType=PhysicsGeometryData.SHAPE_BOX;f.shapes.add(d);return e;}
    private static int distanceJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_DISTANCE,a,b);w.getMapper(PhysicsDistanceJointComponent.class).create(e);return e;}
    private static int revoluteJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_REVOLUTE,a,b);w.getMapper(PhysicsRevoluteJointComponent.class).create(e);return e;}
    private static int prismaticJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_PRISMATIC,a,b);w.getMapper(PhysicsPrismaticJointComponent.class).create(e);return e;}
    private static int gearJoint(World w,int a,int b,int j1,int j2){int e=base(w,PhysicsJointComponent.TYPE_GEAR,a,b);PhysicsGearJointComponent g=w.getMapper(PhysicsGearJointComponent.class).create(e);g.joint1Eid=j1;g.joint2Eid=j2;return e;}
    private static int base(World w,int type,int a,int b){int e=w.create();PhysicsJointComponent j=w.getMapper(PhysicsJointComponent.class).create(e);j.type=type;j.aEid=a;j.bEid=b;return e;}
    private static void assertContains(EntityGraph g, int id){for (EntityGraphEntry e: g.entries()) if(e.sourceEntityId()==id) return; Assert.fail();}
    private static void assertNotContains(EntityGraph g, int id){for (EntityGraphEntry e: g.entries()) if(e.sourceEntityId()==id) Assert.fail();}
}
