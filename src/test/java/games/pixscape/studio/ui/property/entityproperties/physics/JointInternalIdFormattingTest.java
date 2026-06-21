package games.pixscape.studio.ui.property.entityproperties.physics;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import org.junit.Assert;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class JointInternalIdFormattingTest {

    @Test
    public void jointPropertiesFormatsBodyRefsUsingStableIdOnly() throws Exception {
        World world = new World(new WorldConfiguration());
        int bodyEid = world.create();

        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(bodyEid);
        identity.stableId = 7001;

        JointProperties properties = allocate(JointProperties.class);
        setField(properties, JointProperties.class, "world", world);
        setField(properties, JointProperties.class, "mIdentity", world.getMapper(PixscapeIdentityComponent.class));

        String formatted = (String) invoke(properties, JointProperties.class, "formatInternalId", new Class[] {int.class}, bodyEid);
        Assert.assertEquals("7001", formatted);
        Assert.assertNotEquals(String.valueOf(bodyEid), formatted);

        int missingIdentityEid = world.create();
        String missing = (String) invoke(properties, JointProperties.class, "formatInternalId", new Class[] {int.class}, missingIdentityEid);
        Assert.assertEquals("-", missing);
    }

    @Test
    public void gearJointPropertiesFormatsJointRefsUsingStableIdOnly() throws Exception {
        World world = new World(new WorldConfiguration());

        int sourceJoint = world.create();
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(sourceJoint);
        base.type = PhysicsJointComponent.TYPE_REVOLUTE;
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(sourceJoint);
        identity.stableId = 99123;

        int gearJoint = world.create();
        PhysicsGearJointComponent gear = world.getMapper(PhysicsGearJointComponent.class).create(gearJoint);
        gear.joint1Eid = sourceJoint;
        gear.joint2Eid = sourceJoint;

        GearJointPropertiesPanel panel = allocate(GearJointPropertiesPanel.class);
        setField(panel, GearJointPropertiesPanel.class, "world", world);
        setField(panel, GearJointPropertiesPanel.class, "mJointBase", world.getMapper(PhysicsJointComponent.class));
        setField(panel, GearJointPropertiesPanel.class, "mIdentity", world.getMapper(PixscapeIdentityComponent.class));
        setField(panel, GearJointPropertiesPanel.class, "mGear", world.getMapper(PhysicsGearJointComponent.class));

        String formatted = (String) invoke(panel, GearJointPropertiesPanel.class, "formatJointRef", new Class[] {int.class}, sourceJoint);
        Assert.assertEquals("REVOLUTE #99123", formatted);
        Assert.assertFalse(formatted.contains("#" + sourceJoint));

        int noIdentityJoint = world.create();
        world.getMapper(PhysicsJointComponent.class).create(noIdentityJoint).type = PhysicsJointComponent.TYPE_PRISMATIC;
        String missing = (String) invoke(panel, GearJointPropertiesPanel.class, "formatJointRef", new Class[] {int.class}, noIdentityJoint);
        Assert.assertEquals("-", missing);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe) field.get(null);
        return (T) unsafe.allocateInstance(type);
    }

    private static void setField(Object target, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object invoke(Object target,
                                 Class<?> type,
                                 String method,
                                 Class<?>[] argTypes,
                                 Object... args) throws Exception {
        Method m = type.getDeclaredMethod(method, argTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }
}
