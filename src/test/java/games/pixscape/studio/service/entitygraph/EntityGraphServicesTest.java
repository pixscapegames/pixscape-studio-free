package games.pixscape.studio.service.entitygraph;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.configuration.ProjectConfig;
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
    public void instantiate_remapsJointBodyReferences() {
        World world = new World(new WorldConfiguration());
        HistoryManager hm = new HistoryManager(32);
        IdentityRegistry reg = new IdentityRegistry();
        reg.bind(world, new games.pixscape.studio.configuration.SceneMeta()); reg.rebuild();

        int a = body(world); int b = body(world); int j = distanceJoint(world, a, b);
        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b));

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                world, hm, reg, new games.pixscape.runtime.service.PhysicsService(
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
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
                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                .instantiate(graph, 0, 0f, 0f, "Test Instantiate");

        int pastedG = result.sourceToCreated().get(g, -1);
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).get(pastedG);
        Assert.assertEquals(result.sourceToCreated().get(j1, -1), gear.joint1Eid);
        Assert.assertEquals(result.sourceToCreated().get(j2, -1), gear.joint2Eid);
    }

    @Test
    public void capture_rejectsSpatialBlocksComponent() {
        World world = new World(new WorldConfiguration());
        int entity = body(world);
        world.getMapper(SpatialBlocksComponent.class).create(entity);

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new EntityGraphCaptureService(world).capture(arr(entity)));
        Assert.assertEquals(ActorPrefabSpatialScopeGuard.MESSAGE, failure.getMessage());
    }

    @Test
    public void capture_rejectsBlockPhysicsBindingsComponent() {
        World world = new World(new WorldConfiguration());
        int entity = body(world);
        world.getMapper(BlockPhysicsBindingsComponent.class).create(entity);

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new EntityGraphCaptureService(world).capture(arr(entity)));
        Assert.assertEquals(ActorPrefabSpatialScopeGuard.MESSAGE, failure.getMessage());
    }

    @Test
    public void capture_rejectsLinkedPhysicsShape() {
        World world = new World(new WorldConfiguration());
        int entity = body(world);
        world.getMapper(PhysicsShapesComponent.class).get(entity).shapes.first().directGeometry = null;

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new EntityGraphCaptureService(world).capture(arr(entity)));
        Assert.assertEquals(ActorPrefabSpatialScopeGuard.MESSAGE, failure.getMessage());
    }

    @Test
    public void capture_rejectsOutOfScopeDataIntroducedOnlyByJointClosure() {
        World world = new World(new WorldConfiguration());
        int bodyA = body(world);
        int bodyB = body(world);
        int joint = distanceJoint(world, bodyA, bodyB);
        world.getMapper(BlockPhysicsBindingsComponent.class).create(joint);

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new EntityGraphCaptureService(world).capture(arr(bodyA, bodyB)));
        Assert.assertEquals(ActorPrefabSpatialScopeGuard.MESSAGE, failure.getMessage());
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
                    world, null, new games.pixscape.studio.configuration.SceneMeta()))
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

    @Test
    public void manualLinkedGraphIsRejectedBeforeAllocatorsHistoryEntitiesAndProcess() {
        SentinelSystem sentinel = new SentinelSystem();
        World world = new World(new WorldConfigurationBuilder().with(sentinel).build());
        games.pixscape.studio.configuration.SceneMeta meta =
                new games.pixscape.studio.configuration.SceneMeta();
        meta.nextEntityStableId = 40;
        meta.nextPhysicsShapeId = 80;
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        int source = body(world);
        world.getMapper(PhysicsShapesComponent.class).get(source).shapes.first().directGeometry = null;
        GenericEntityInitializer initializer = new GenericEntityInitializer(world);
        initializer.syncFrom(source);
        EntityGraph manual = new EntityGraph(List.of(new EntityGraphEntry(source, initializer)));
        int entitiesBefore = count(world, Aspect.all());
        int stableHighWaterBefore = meta.nextEntityStableId;
        int shapeHighWaterBefore = meta.nextPhysicsShapeId;
        sentinel.processCount = 0;

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> new EntityGraphInstantiationService(
                        world,
                        history,
                        identities,
                        new games.pixscape.runtime.service.PhysicsService(world, null, meta))
                        .instantiate(manual, 0, 0f, 0f, "Manual linked graph"));

        Assert.assertEquals(ActorPrefabSpatialScopeGuard.MESSAGE, failure.getMessage());
        Assert.assertEquals(entitiesBefore, count(world, Aspect.all()));
        Assert.assertEquals(stableHighWaterBefore, meta.nextEntityStableId);
        Assert.assertEquals(shapeHighWaterBefore, meta.nextPhysicsShapeId);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertEquals(0, sentinel.processCount);
    }

    private static int count(World world, com.artemis.Aspect.Builder aspect) {
        return world.getAspectSubscriptionManager().get(aspect).getEntities().size();
    }

    private static final class SentinelSystem extends BaseSystem {
        int processCount;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }

    private static IntArray arr(int... ids) { IntArray a = new IntArray(); for (int id : ids) a.add(id); return a; }
    private static int body(World w){int e=w.create();w.getMapper(TransformComponent.class).create(e);w.getMapper(EntityIndexComponent.class).create(e);w.getMapper(PhysicsBodyComponent.class).create(e);PhysicsShapesComponent f=w.getMapper(PhysicsShapesComponent.class).create(e);PhysicsShapeData d=new PhysicsShapeData();d.directGeometry=new PhysicsDirectGeometryData();d.physicsShapeId=e+1;d.directGeometry.shapeType=PhysicsDirectGeometryData.SHAPE_BOX;f.shapes.add(d);return e;}
    private static int distanceJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_DISTANCE,a,b);w.getMapper(PhysicsDistanceJointComponent.class).create(e);return e;}
    private static int revoluteJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_REVOLUTE,a,b);w.getMapper(PhysicsRevoluteJointComponent.class).create(e);return e;}
    private static int prismaticJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_PRISMATIC,a,b);w.getMapper(PhysicsPrismaticJointComponent.class).create(e);return e;}
    private static int gearJoint(World w,int a,int b,int j1,int j2){int e=base(w,PhysicsJointComponent.TYPE_GEAR,a,b);PhysicsGearJointComponent g=w.getMapper(PhysicsGearJointComponent.class).create(e);g.joint1Eid=j1;g.joint2Eid=j2;return e;}
    private static int base(World w,int type,int a,int b){int e=w.create();PhysicsJointComponent j=w.getMapper(PhysicsJointComponent.class).create(e);j.type=type;j.aEid=a;j.bEid=b;return e;}
    private static void assertContains(EntityGraph g, int id){for (EntityGraphEntry e: g.entries()) if(e.sourceEntityId()==id) return; Assert.fail();}
    private static void assertNotContains(EntityGraph g, int id){for (EntityGraphEntry e: g.entries()) if(e.sourceEntityId()==id) Assert.fail();}
}
