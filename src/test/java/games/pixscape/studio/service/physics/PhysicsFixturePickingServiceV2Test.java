package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.system.DirtyFlushSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsFixturePickingServiceV2Test {
    @Test
    public void picksBoxCircleAndConvexPolygonFromCompiledCache() {
        PhysicsShapeData box = shape(1, PhysicsGeometryData.SHAPE_BOX);
        PhysicsShapeData circle = shape(2, PhysicsGeometryData.SHAPE_CIRCLE);
        PhysicsShapeData polygon = shape(3, PhysicsGeometryData.SHAPE_POLYGON);
        polygon.geometry.polygonVertices =
                new float[]{0f, 0f, 1f, 0f, 0f, 1f};
        polygon.geometry.polygonVertexCount = 3;

        Assert.assertTrue(pickSingle(box, 0f, 0f).hit());
        Assert.assertTrue(pickSingle(circle, 0f, 0f).hit());
        Assert.assertTrue(pickSingle(polygon, 20f, 20f).hit());
    }

    @Test
    public void reverseCacheOrderWinsAndAuthoredMutationDoesNotAffectStablePicks() {
        Fixture fixture = fixture(
                shape(11, PhysicsGeometryData.SHAPE_CIRCLE),
                shape(12, PhysicsGeometryData.SHAPE_CIRCLE));
        PhysicsFixturePickingService picker =
                new PhysicsFixturePickingService(fixture.physics);

        Assert.assertEquals(12, picker.pick(fixture.body, 0f, 0f, 0f).physicsShapeId);
        fixture.shapes.shapes.get(1).geometry.offsetX = 1000f;
        for (int i = 0; i < 1000; i++) {
            PhysicsFixturePickingService.PickResult result =
                    picker.pick(fixture.body, 0f, 0f, 0f);
            Assert.assertEquals(12, result.physicsShapeId);
        }
    }

    @Test
    public void compiledCachePickSurvivesCompleteAuthoredShapeRemoval() {
        Fixture fixture = fixture(shape(31, PhysicsGeometryData.SHAPE_BOX));
        PhysicsFixturePickingService picker =
                new PhysicsFixturePickingService(fixture.physics);
        PhysicsFixturePickingService.PickResult before =
                picker.pick(fixture.body, 0f, 0f, 0f);
        Assert.assertTrue(before.hit());

        fixture.world.getMapper(PhysicsShapesComponent.class).remove(fixture.body);

        PhysicsFixturePickingService.PickResult after =
                picker.pick(fixture.body, 0f, 0f, 0f);
        Assert.assertTrue(after.hit());
        Assert.assertEquals(before.bodyEntityId, after.bodyEntityId);
        Assert.assertEquals(before.physicsShapeId, after.physicsShapeId);
        Assert.assertEquals(before.partIndex, after.partIndex);
    }

    @Test
    public void overlapOfTwoCompiledPartsUsesHighestPartIndex() {
        Fixture fixture = fixture(shape(21, PhysicsGeometryData.SHAPE_BOX));
        PhysicsCompiledFixturesComponent cache =
                fixture.world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .get(fixture.body);
        cache.fixtures.clear();
        cache.fixtures.add(compiledBox(21, 0));
        cache.fixtures.add(compiledBox(21, 1));
        cache.valid = true;

        PhysicsFixturePickingService.PickResult result =
                new PhysicsFixturePickingService(fixture.physics)
                        .pick(fixture.body, 0f, 0f, 0f);

        Assert.assertEquals(21, result.physicsShapeId);
        Assert.assertEquals(1, result.partIndex);
    }

    @Test
    public void concavePickReturnsSourceIdentityAndCompiledPart() {
        GdxNativesLoader.load();
        World world = new World(new WorldConfiguration());
        int bodyEntityId = world.create();
        world.getMapper(TransformComponent.class).create(bodyEntityId);
        world.getMapper(PhysicsBodyComponent.class).create(bodyEntityId);
        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).create(bodyEntityId);
        PhysicsShapeData polygon = new PhysicsShapeData();
        polygon.geometry = new PhysicsGeometryData();
        polygon.physicsShapeId = 17;
        polygon.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        polygon.geometry.polygonVertices =
                new float[]{0f, 0f, 2f, 0f, 2f, 2f, 1f, 1f, 0f, 2f};
        polygon.geometry.polygonVertexCount = 5;
        shapes.shapes.add(polygon);
        PhysicsService.publishPreparedCandidate(
                shapes,
                world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .create(bodyEntityId),
                PhysicsService.prepareBodyCandidate(shapes.shapes));

        PhysicsService physics = new PhysicsService(
                world, new Box2dWorldService(100f, new Vector2()));
        PhysicsFixturePickingService.PickResult result =
                new PhysicsFixturePickingService(physics)
                        .pick(bodyEntityId, 25f, 25f, 0f);

        Assert.assertTrue(result.hit());
        Assert.assertEquals(17, result.physicsShapeId);
        Assert.assertTrue(result.partIndex >= 0);
    }

    @Test
    public void hierarchyBodyPicksAtResolvedWorldPoseNotRawLocalPose() {
        GdxNativesLoader.load();
        GameObjectHierarchySystem hierarchy = new GameObjectHierarchySystem(8);
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(8), hierarchy, new DirtyFlushSystem())
                .build());
        IdentityRegistry identities = new IdentityRegistry();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        identities.bind(world, meta);
        try {
            int root = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(root).stableId = 1;
            world.getMapper(EntityIndexComponent.class).create(root);
            TransformComponent rootTransform = world.getMapper(TransformComponent.class).create(root);
            rootTransform.x = 200f;
            rootTransform.y = -50f;
            rootTransform.refreshCaches();
            world.getMapper(GameObjectComponent.class).create(root);

            int body = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(body).stableId = 2;
            world.getMapper(EntityIndexComponent.class).create(body);
            TransformComponent bodyTransform = world.getMapper(TransformComponent.class).create(body);
            bodyTransform.x = 10f;
            bodyTransform.y = 5f;
            bodyTransform.refreshCaches();
            world.getMapper(GameObjectMemberComponent.class).create(body).parentStableId = 1;
            world.getMapper(PhysicsBodyComponent.class).create(body);
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(body);
            shapes.shapes.add(shape(77, PhysicsGeometryData.SHAPE_BOX));
            PhysicsService.publishPreparedCandidate(
                    shapes,
                    world.getMapper(PhysicsCompiledFixturesComponent.class).create(body),
                    PhysicsService.prepareBodyCandidate(shapes.shapes));
            world.process();

            PhysicsService physics = new PhysicsService(
                    world, new Box2dWorldService(100f, new Vector2()));
            PhysicsFixturePickingService picker = new PhysicsFixturePickingService(world, physics);

            Assert.assertTrue(picker.pick(body, 210f, -45f, 0f).hit());
            Assert.assertFalse(picker.pick(body, 10f, 5f, 0f).hit());
        } finally {
            identities.bind(null, null);
            world.dispose();
        }
    }

    private static PhysicsFixturePickingService.PickResult pickSingle(
            PhysicsShapeData shape, float worldX, float worldY) {
        Fixture fixture = fixture(shape);
        return new PhysicsFixturePickingService(fixture.physics)
                .pick(fixture.body, worldX, worldY, 0f);
    }

    private static Fixture fixture(PhysicsShapeData... sourceShapes) {
        GdxNativesLoader.load();
        World world = new World(new WorldConfiguration());
        int body = world.create();
        world.getMapper(TransformComponent.class).create(body);
        world.getMapper(PhysicsBodyComponent.class).create(body);
        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).create(body);
        for (PhysicsShapeData shape : sourceShapes) {
            shapes.shapes.add(shape);
        }
        PhysicsService.publishPreparedCandidate(
                shapes,
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(body),
                PhysicsService.prepareBodyCandidate(shapes.shapes));
        PhysicsService physics = new PhysicsService(
                world, new Box2dWorldService(100f, new Vector2()));
        return new Fixture(world, body, shapes, physics);
    }

    private static PhysicsShapeData shape(int id, int type) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = id;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = type;
        return shape;
    }

    private static CompiledFixtureData compiledBox(int id, int partIndex) {
        CompiledFixtureData fixture = new CompiledFixtureData();
        fixture.physicsShapeId = id;
        fixture.partIndex = partIndex;
        fixture.shapeType = PhysicsGeometryData.SHAPE_BOX;
        fixture.halfWidth = 0.5f;
        fixture.halfHeight = 0.5f;
        return fixture;
    }

    private static final class Fixture {
        final World world;
        final int body;
        final PhysicsShapesComponent shapes;
        final PhysicsService physics;

        Fixture(
                World world,
                int body,
                PhysicsShapesComponent shapes,
                PhysicsService physics) {
            this.world = world;
            this.body = body;
            this.shapes = shapes;
            this.physics = physics;
        }
    }
}
