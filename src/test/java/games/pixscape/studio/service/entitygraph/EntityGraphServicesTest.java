package games.pixscape.studio.service.entitygraph;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class EntityGraphServicesTest {

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
        IdentityRegistry reg = new IdentityRegistry(); reg.bind(world); reg.rebuild();

        int a = body(world); int b = body(world); int j = distanceJoint(world, a, b);
        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b));

        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(world, hm, reg)
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
        IdentityRegistry reg = new IdentityRegistry(); reg.bind(world); reg.rebuild();

        int a = body(world); int b = body(world); int c = body(world);
        int j1 = revoluteJoint(world, a, b);
        int j2 = prismaticJoint(world, b, c);
        int g = gearJoint(world, a, c, j1, j2);

        EntityGraph graph = new EntityGraphCaptureService(world).capture(arr(a, b, c));
        EntityGraphInstantiationResult result = new EntityGraphInstantiationService(world, hm, reg)
                .instantiate(graph, 0, 0f, 0f, "Test Instantiate");

        int pastedG = result.sourceToCreated().get(g, -1);
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).get(pastedG);
        Assert.assertEquals(result.sourceToCreated().get(j1, -1), gear.joint1Eid);
        Assert.assertEquals(result.sourceToCreated().get(j2, -1), gear.joint2Eid);
    }

    private static IntArray arr(int... ids) { IntArray a = new IntArray(); for (int id : ids) a.add(id); return a; }
    private static int body(World w){int e=w.create();w.getMapper(TransformComponent.class).create(e);w.getMapper(EntityIndexComponent.class).create(e);w.getMapper(PhysicsBodyComponent.class).create(e);PhysicsShapesComponent f=w.getMapper(PhysicsShapesComponent.class).create(e);PhysicsShapeData d=new PhysicsShapeData();d.shapeType=PhysicsShapeData.SHAPE_BOX;f.shapes.add(d);return e;}
    private static int distanceJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_DISTANCE,a,b);w.getMapper(PhysicsDistanceJointComponent.class).create(e);return e;}
    private static int revoluteJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_REVOLUTE,a,b);w.getMapper(PhysicsRevoluteJointComponent.class).create(e);return e;}
    private static int prismaticJoint(World w,int a,int b){int e=base(w,PhysicsJointComponent.TYPE_PRISMATIC,a,b);w.getMapper(PhysicsPrismaticJointComponent.class).create(e);return e;}
    private static int gearJoint(World w,int a,int b,int j1,int j2){int e=base(w,PhysicsJointComponent.TYPE_GEAR,a,b);PhysicsGearJointComponent g=w.getMapper(PhysicsGearJointComponent.class).create(e);g.joint1Eid=j1;g.joint2Eid=j2;return e;}
    private static int base(World w,int type,int a,int b){int e=w.create();PhysicsJointComponent j=w.getMapper(PhysicsJointComponent.class).create(e);j.type=type;j.aEid=a;j.bEid=b;return e;}
    private static void assertContains(EntityGraph g, int id){for (EntityGraphEntry e: g.entries()) if(e.sourceEntityId()==id) return; Assert.fail();}
    private static void assertNotContains(EntityGraph g, int id){for (EntityGraphEntry e: g.entries()) if(e.sourceEntityId()==id) Assert.fail();}
}
