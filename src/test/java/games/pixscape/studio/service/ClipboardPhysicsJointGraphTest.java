package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.service.PhysicsService;
import org.junit.Assert;
import org.junit.Test;

public class ClipboardPhysicsJointGraphTest {

    @Test
    public void selectedBodiesAutoIncludeDistanceJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_DISTANCE); }
    @Test
    public void selectedBodiesAutoIncludeWheelJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_WHEEL); }
    @Test
    public void selectedBodiesAutoIncludeRevoluteJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_REVOLUTE); }
    @Test
    public void selectedBodiesAutoIncludePrismaticJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_PRISMATIC); }
    @Test
    public void selectedBodiesAutoIncludeFrictionJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_FRICTION); }
    @Test
    public void selectedBodiesAutoIncludeMotorJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_MOTOR); }
    @Test
    public void selectedBodiesAutoIncludeWeldJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_WELD); }
    @Test
    public void selectedBodiesAutoIncludePulleyJoint() { autoIncludeRegularJoint(PhysicsJointComponent.TYPE_PULLEY); }

    @Test
    public void oneSelectedBodyDoesNotAutoIncludeDistanceJoint() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int joint = w.distanceJoint(a, b);
        assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a)), joint);
    }

    @Test
    public void oneSelectedBodyDoesNotAutoIncludeWheelJoint() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int joint = w.wheelJoint(a, b);
        assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a)), joint);
    }

    @Test
    public void selectedBodyPlusJointButMissingOtherBodySkipsJoint() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int joint = w.revoluteJoint(a, b);
        assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, joint)), joint);
    }

    @Test
    public void selectedJointAloneIsSkipped() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int joint = w.prismaticJoint(a, b);
        assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(joint)), joint);
    }

    @Test
    public void selectedBodiesAutoIncludeTwoWheelJoints() {
        W w = new W();
        int chassis = w.bodyWithDefaultFixture();
        int leftWheel = w.bodyWithDefaultFixture();
        int rightWheel = w.bodyWithDefaultFixture();
        int leftJoint = w.wheelJoint(chassis, leftWheel);
        int rightJoint = w.wheelJoint(chassis, rightWheel);

        IntArray out = ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(chassis, leftWheel, rightWheel));

        assertContains(out, leftJoint);
        assertContains(out, rightJoint);
    }

    @Test
    public void selectedBodiesAutoIncludeGearWhenSourceJointsAccepted() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int c = w.bodyWithDefaultFixture();
        int revolute = w.revoluteJoint(a, b);
        int prismatic = w.prismaticJoint(b, c);
        int gear = w.gearJoint(a, c, revolute, prismatic);

        IntArray out = ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b, c));

        assertContains(out, revolute);
        assertContains(out, prismatic);
        assertContains(out, gear);
    }

    @Test
    public void gearNotIncludedIfOneSourceJointIsNotAccepted() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int c = w.bodyWithDefaultFixture();
        int revolute = w.revoluteJoint(a, b);
        int badPrismatic = w.baseJoint(PhysicsJointComponent.TYPE_PRISMATIC, b, c);
        int gear = w.gearJoint(a, c, revolute, badPrismatic);

        IntArray out = ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b, c));

        assertContains(out, revolute);
        assertNotContains(out, badPrismatic);
        assertNotContains(out, gear);
    }

    @Test
    public void gearExplicitlySelectedButMissingSourceJointIsSkipped() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int c = w.bodyWithDefaultFixture();
        int revolute = w.revoluteJoint(a, b);
        int missingSpecific = w.baseJoint(PhysicsJointComponent.TYPE_PRISMATIC, b, c);
        int gear = w.gearJoint(a, c, revolute, missingSpecific);

        IntArray out = ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b, c, gear));

        assertNotContains(out, gear);
    }

    @Test
    public void outputOrderCreatesBodiesBeforeJoints() {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int c = w.bodyWithDefaultFixture();
        int revolute = w.revoluteJoint(a, b);
        int prismatic = w.prismaticJoint(b, c);
        int gear = w.gearJoint(a, c, revolute, prismatic);

        IntArray out = ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b, c));

        assertBefore(out, a, revolute);
        assertBefore(out, b, revolute);
        assertBefore(out, b, prismatic);
        assertBefore(out, c, prismatic);
        assertBefore(out, revolute, gear);
        assertBefore(out, prismatic, gear);
    }

    @Test public void jointWithSameBodyEndpointsIsSkipped() { W w = new W(); int a = w.bodyWithDefaultFixture(); int j = w.distanceJoint(a, a); assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, j)), j); }
    @Test public void bodyWithoutFixturesMakesJointNotCopyable() { W w = new W(); int a = w.bodyWithoutFixtures(); int b = w.bodyWithDefaultFixture(); int j = w.distanceJoint(a, b); assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b, j)), j); }
    @Test public void missingSpecificComponentIsSkippedForDistance() { missingSpecific(PhysicsJointComponent.TYPE_DISTANCE); }
    @Test public void missingSpecificComponentIsSkippedForWheel() { missingSpecific(PhysicsJointComponent.TYPE_WHEEL); }
    @Test public void missingSpecificComponentIsSkippedForGear() { W w = new W(); int a=w.bodyWithDefaultFixture(); int b=w.bodyWithDefaultFixture(); int j=w.baseJoint(PhysicsJointComponent.TYPE_GEAR,a,b); assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a,b,j)), j); }
    @Test public void unknownJointTypeIsSkipped() { W w = new W(); int a = w.bodyWithDefaultFixture(); int b = w.bodyWithDefaultFixture(); int j = w.baseJoint(999, a, b); assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b, j)), j); }

    @Test
    public void remapNonGearJointUpdatesBodyRefsToNewIds() {
        W w = new W(); int a = w.bodyWithDefaultFixture(); int b = w.bodyWithDefaultFixture(); int joint = w.distanceJoint(a, b);
        IntIntMap map = new IntIntMap(); map.put(a, 100); map.put(b, 200);
        Assert.assertTrue(ClipboardPhysicsJointGraph.remapJointReferences(w.world, joint, map));
        PhysicsJointComponent base = w.world.getMapper(PhysicsJointComponent.class).get(joint);
        Assert.assertEquals(100, base.aEid); Assert.assertEquals(200, base.bEid); Assert.assertNotEquals(a, base.aEid); Assert.assertNotEquals(b, base.bEid);
    }

    @Test
    public void remapGearJointUpdatesBodyRefsAndSourceJointRefsToNewIds() {
        W w = new W(); int a = w.bodyWithDefaultFixture(); int b = w.bodyWithDefaultFixture(); int c = w.bodyWithDefaultFixture(); int j1 = w.revoluteJoint(a, b); int j2 = w.prismaticJoint(b, c); int gear = w.gearJoint(a, c, j1, j2);
        IntIntMap map = new IntIntMap(); map.put(a, 10); map.put(c, 30); map.put(j1, 40); map.put(j2, 50);
        Assert.assertTrue(ClipboardPhysicsJointGraph.remapJointReferences(w.world, gear, map));
        PhysicsJointComponent base = w.world.getMapper(PhysicsJointComponent.class).get(gear); PhysicsGearJointComponent gc = w.world.getMapper(PhysicsGearJointComponent.class).get(gear);
        Assert.assertEquals(10, base.aEid); Assert.assertEquals(30, base.bEid); Assert.assertEquals(40, gc.joint1Eid); Assert.assertEquals(50, gc.joint2Eid);
        Assert.assertNotEquals(a, base.aEid); Assert.assertNotEquals(c, base.bEid); Assert.assertNotEquals(j1, gc.joint1Eid); Assert.assertNotEquals(j2, gc.joint2Eid);
    }

    @Test public void remapFailsWhenDependencyMissing() { W w = new W(); int a = w.bodyWithDefaultFixture(); int b = w.bodyWithDefaultFixture(); int j = w.distanceJoint(a,b); IntIntMap m = new IntIntMap(); m.put(a,11); Assert.assertFalse(ClipboardPhysicsJointGraph.remapJointReferences(w.world,j,m)); }
    @Test public void remapGearFailureDoesNotPartiallyMutateRefs() { W w = new W(); int a=w.bodyWithDefaultFixture(); int b=w.bodyWithDefaultFixture(); int c=w.bodyWithDefaultFixture(); int j1=w.revoluteJoint(a,b); int j2=w.prismaticJoint(b,c); int g=w.gearJoint(a,c,j1,j2); PhysicsJointComponent beforeB=w.world.getMapper(PhysicsJointComponent.class).get(g); PhysicsGearJointComponent beforeG=w.world.getMapper(PhysicsGearJointComponent.class).get(g); int oa=beforeB.aEid,ob=beforeB.bEid,oj1=beforeG.joint1Eid,oj2=beforeG.joint2Eid; IntIntMap m=new IntIntMap(); m.put(a,10); m.put(c,30); m.put(j1,40); Assert.assertFalse(ClipboardPhysicsJointGraph.remapJointReferences(w.world,g,m)); PhysicsJointComponent afterB=w.world.getMapper(PhysicsJointComponent.class).get(g); PhysicsGearJointComponent afterG=w.world.getMapper(PhysicsGearJointComponent.class).get(g); Assert.assertEquals(oa,afterB.aEid); Assert.assertEquals(ob,afterB.bEid); Assert.assertEquals(oj1,afterG.joint1Eid); Assert.assertEquals(oj2,afterG.joint2Eid); }
    @Test public void remapFailsWhenRemappedAEqualsRemappedB() { W w = new W(); int a=w.bodyWithDefaultFixture(); int b=w.bodyWithDefaultFixture(); int j=w.wheelJoint(a,b); IntIntMap m=new IntIntMap(); m.put(a,9); m.put(b,9); Assert.assertFalse(ClipboardPhysicsJointGraph.remapJointReferences(w.world,j,m)); }
    @Test public void remapFailsWhenGearSourceRemapsToSameJoint() { W w = new W(); int a=w.bodyWithDefaultFixture(); int b=w.bodyWithDefaultFixture(); int c=w.bodyWithDefaultFixture(); int j1=w.revoluteJoint(a,b); int j2=w.prismaticJoint(b,c); int g=w.gearJoint(a,c,j1,j2); IntIntMap m=new IntIntMap(); m.put(a,10); m.put(c,30); m.put(j1,40); m.put(j2,40); Assert.assertFalse(ClipboardPhysicsJointGraph.remapJointReferences(w.world,g,m)); }

    @Test(expected = IllegalArgumentException.class) public void filterNullWorldThrows() { ClipboardPhysicsJointGraph.filterCopyableSelection(null, new IntArray()); }
    @Test(expected = IllegalArgumentException.class) public void filterNullSelectionThrows() { W w = new W(); ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, null); }
    @Test(expected = IllegalArgumentException.class) public void remapNullMapThrows() { W w = new W(); ClipboardPhysicsJointGraph.remapJointReferences(w.world, w.bodyWithDefaultFixture(), null); }

    private static void autoIncludeRegularJoint(int type) {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int joint = w.jointOfType(type, a, b);
        assertContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b)), joint);
    }

    private static void missingSpecific(int type) {
        W w = new W();
        int a = w.bodyWithDefaultFixture();
        int b = w.bodyWithDefaultFixture();
        int joint = w.baseJoint(type, a, b);
        assertNotContains(ClipboardPhysicsJointGraph.filterCopyableSelection(w.world, arr(a, b, joint)), joint);
    }

    private static final class W {
        final World world = games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
        int bodyWithDefaultFixture() { int eid = bodyWithoutFixtures(); world.getMapper(PhysicsFixturesComponent.class).get(eid).fixtures.add(games.pixscape.studio.FixtureIdentityTestSupport.createFixture(world)); return eid; }
        int bodyWithoutFixtures() { int eid = world.create(); world.getMapper(TransformComponent.class).create(eid); world.getMapper(EntityIndexComponent.class).create(eid); world.getMapper(PhysicsBodyComponent.class).create(eid); world.getMapper(PhysicsFixturesComponent.class).create(eid); return eid; }
        int baseJoint(int type, int a, int b) { int eid = world.create(); world.getMapper(EntityIndexComponent.class).create(eid); PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(eid); joint.type = type; joint.aEid = a; joint.bEid = b; return eid; }
        int jointOfType(int type, int a, int b) { switch (type) { case PhysicsJointComponent.TYPE_DISTANCE: return distanceJoint(a,b); case PhysicsJointComponent.TYPE_WHEEL: return wheelJoint(a,b); case PhysicsJointComponent.TYPE_REVOLUTE: return revoluteJoint(a,b); case PhysicsJointComponent.TYPE_PRISMATIC: return prismaticJoint(a,b); case PhysicsJointComponent.TYPE_FRICTION: return frictionJoint(a,b); case PhysicsJointComponent.TYPE_MOTOR: return motorJoint(a,b); case PhysicsJointComponent.TYPE_WELD: return weldJoint(a,b); case PhysicsJointComponent.TYPE_PULLEY: return pulleyJoint(a,b); default: throw new IllegalArgumentException(); } }
        int distanceJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_DISTANCE, a, b); world.getMapper(PhysicsDistanceJointComponent.class).create(eid); return eid; }
        int wheelJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_WHEEL, a, b); world.getMapper(PhysicsWheelJointComponent.class).create(eid); return eid; }
        int revoluteJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_REVOLUTE, a, b); world.getMapper(PhysicsRevoluteJointComponent.class).create(eid); return eid; }
        int prismaticJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_PRISMATIC, a, b); world.getMapper(PhysicsPrismaticJointComponent.class).create(eid); return eid; }
        int frictionJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_FRICTION, a, b); world.getMapper(PhysicsFrictionJointComponent.class).create(eid); return eid; }
        int motorJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_MOTOR, a, b); world.getMapper(PhysicsMotorJointComponent.class).create(eid); return eid; }
        int weldJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_WELD, a, b); world.getMapper(PhysicsWeldJointComponent.class).create(eid); return eid; }
        int pulleyJoint(int a, int b) { int eid = baseJoint(PhysicsJointComponent.TYPE_PULLEY, a, b); world.getMapper(PhysicsPulleyJointComponent.class).create(eid); return eid; }
        int gearJoint(int a, int b, int j1, int j2) { int eid = baseJoint(PhysicsJointComponent.TYPE_GEAR, a, b); PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).create(eid); gear.joint1Eid = j1; gear.joint2Eid = j2; return eid; }
    }

    private static IntArray arr(int... values) { IntArray out = new IntArray(); for (int value : values) out.add(value); return out; }
    private static void assertContains(IntArray values, int expected) { Assert.assertTrue(contains(values, expected)); }
    private static void assertNotContains(IntArray values, int expected) { Assert.assertFalse(contains(values, expected)); }
    private static void assertBefore(IntArray values, int first, int second) { Assert.assertTrue(indexOf(values, first) < indexOf(values, second)); }
    private static boolean contains(IntArray values, int expected) { return indexOf(values, expected) >= 0; }
    private static int indexOf(IntArray values, int expected) { for (int i=0;i<values.size;i++) if (values.get(i)==expected) return i; return -1; }
}
