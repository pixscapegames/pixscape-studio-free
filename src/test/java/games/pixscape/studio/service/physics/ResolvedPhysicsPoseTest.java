package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyFlushSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Regression coverage for hierarchy Body overlays and Body-local shape interaction. */
public class ResolvedPhysicsPoseTest {
    private static final float EPSILON = 0.0001f;

    private World world;
    private IdentityRegistry identities;
    private GameObjectHierarchySystem hierarchy;

    @Before
    public void setUp() {
        hierarchy = new GameObjectHierarchySystem(16);
        world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(16), hierarchy, new DirtyFlushSystem())
                .build());
        identities = new IdentityRegistry();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        identities.bind(world, meta);
    }

    @After
    public void tearDown() {
        identities.bind(null, null);
        world.dispose();
    }

    @Test
    public void nestedParentTranslationRotationAndOriginResolveBodyAndInvertToBodyLocal() {
        int root = entity(1, true, -1);
        int nested = entity(2, true, 1);
        int body = entity(3, false, 2);

        TransformComponent rootTransform = transform(root);
        rootTransform.x = 100f;
        rootTransform.y = -40f;
        rootTransform.rotationRad = MathUtils.PI / 3f;
        rootTransform.originX = 17f;
        rootTransform.originY = -11f;
        rootTransform.refreshCaches();

        TransformComponent nestedTransform = transform(nested);
        nestedTransform.x = 25f;
        nestedTransform.y = 12f;
        nestedTransform.rotationRad = -MathUtils.PI / 4f;
        nestedTransform.originX = 4f;
        nestedTransform.originY = 9f;
        nestedTransform.refreshCaches();

        TransformComponent bodyLocal = transform(body);
        bodyLocal.x = 6f;
        bodyLocal.y = -8f;
        bodyLocal.rotationRad = 0.2f;
        bodyLocal.refreshCaches();

        world.process();
        ResolvedPhysicsPose pose = new ResolvedPhysicsPose(world);
        ResolvedPhysicsPose.Pose resolved = new ResolvedPhysicsPose.Pose();
        Assert.assertTrue(pose.resolve(body, resolved));

        Assert.assertEquals(hierarchy.worldTransforms().x[body], resolved.x, EPSILON);
        Assert.assertEquals(hierarchy.worldTransforms().y[body], resolved.y, EPSILON);
        Assert.assertEquals(hierarchy.worldTransforms().rotationRad[body], resolved.rotationRad, EPSILON);
        Assert.assertNotEquals(bodyLocal.x, resolved.x, EPSILON);
        Assert.assertNotEquals(bodyLocal.y, resolved.y, EPSILON);

        Vector2 authoredBodyCenter = new Vector2(bodyLocal.x, bodyLocal.y);
        Assert.assertTrue(pose.remapAuthoredWorldPoint(body, authoredBodyCenter));
        Assert.assertEquals(resolved.x, authoredBodyCenter.x, EPSILON);
        Assert.assertEquals(resolved.y, authoredBodyCenter.y, EPSILON);

        Vector2 local = new Vector2();
        Assert.assertTrue(pose.resolvedWorldToLocal(body, resolved.x, resolved.y, local));
        Assert.assertEquals(0f, local.x, EPSILON);
        Assert.assertEquals(0f, local.y, EPSILON);
    }

    @Test
    public void parentMovementChangesResolvedPoseWithoutChangingBodyLocalTransform() {
        int root = entity(1, true, -1);
        int body = entity(2, false, 1);
        TransformComponent local = transform(body);
        local.x = 4f;
        local.y = 9f;
        local.rotationRad = 0.5f;
        local.refreshCaches();

        world.process();
        ResolvedPhysicsPose pose = new ResolvedPhysicsPose(world);
        ResolvedPhysicsPose.Pose before = new ResolvedPhysicsPose.Pose();
        Assert.assertTrue(pose.resolve(body, before));

        transform(root).x += 30f;
        transform(root).y -= 15f;
        world.process();

        ResolvedPhysicsPose.Pose after = new ResolvedPhysicsPose.Pose();
        Assert.assertTrue(pose.resolve(body, after));
        Assert.assertEquals(before.x + 30f, after.x, EPSILON);
        Assert.assertEquals(before.y - 15f, after.y, EPSILON);
        Assert.assertEquals(4f, local.x, EPSILON);
        Assert.assertEquals(9f, local.y, EPSILON);
        Assert.assertEquals(0.5f, local.rotationRad, EPSILON);
    }

    private int entity(int stableId, boolean gameObject, int parentStableId) {
        int entity = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(TransformComponent.class).create(entity);
        if (gameObject) world.getMapper(GameObjectComponent.class).create(entity);
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = parentStableId;
        }
        return entity;
    }

    private TransformComponent transform(int entity) {
        return world.getMapper(TransformComponent.class).get(entity);
    }
}
