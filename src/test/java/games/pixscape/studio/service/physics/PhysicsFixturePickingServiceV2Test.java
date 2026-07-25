package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsFixturePickingServiceV2Test {
    @Test
    public void picksBoxCircleAndConvexPolygonFromCompiledCache() {
        PhysicsShapeData box = shape(1, PhysicsDirectGeometryData.SHAPE_BOX);
        PhysicsShapeData circle = shape(2, PhysicsDirectGeometryData.SHAPE_CIRCLE);
        PhysicsShapeData polygon = shape(3, PhysicsDirectGeometryData.SHAPE_POLYGON);
        polygon.directGeometry.polygonVertices =
                new float[]{0f, 0f, 1f, 0f, 0f, 1f};
        polygon.directGeometry.polygonVertexCount = 3;

        Assert.assertTrue(pickSingle(box, 0f, 0f).hit());
        Assert.assertTrue(pickSingle(circle, 0f, 0f).hit());
        Assert.assertTrue(pickSingle(polygon, 20f, 20f).hit());
    }

    @Test
    public void reverseCacheOrderWinsAndAuthoredMutationDoesNotAffectStablePicks() {
        Fixture fixture = fixture(
                shape(11, PhysicsDirectGeometryData.SHAPE_CIRCLE),
                shape(12, PhysicsDirectGeometryData.SHAPE_CIRCLE));
        PhysicsFixturePickingService picker =
                new PhysicsFixturePickingService(fixture.physics);

        Assert.assertEquals(12, picker.pick(fixture.body, 0f, 0f, 0f).physicsShapeId);
        fixture.shapes.shapes.get(1).directGeometry.offsetX = 1000f;
        for (int i = 0; i < 1000; i++) {
            PhysicsFixturePickingService.PickResult result =
                    picker.pick(fixture.body, 0f, 0f, 0f);
            Assert.assertEquals(12, result.physicsShapeId);
        }
    }

    @Test
    public void compiledCachePickSurvivesCompleteAuthoredShapeRemoval() {
        Fixture fixture = fixture(shape(31, PhysicsDirectGeometryData.SHAPE_BOX));
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
        Fixture fixture = fixture(shape(21, PhysicsDirectGeometryData.SHAPE_BOX));
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
        polygon.directGeometry = new PhysicsDirectGeometryData();
        polygon.physicsShapeId = 17;
        polygon.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
        polygon.directGeometry.polygonVertices =
                new float[]{0f, 0f, 2f, 0f, 2f, 2f, 1f, 1f, 0f, 2f};
        polygon.directGeometry.polygonVertexCount = 5;
        shapes.add(polygon);
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
            shapes.add(shape);
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
        shape.directGeometry = new PhysicsDirectGeometryData();
        shape.directGeometry.shapeType = type;
        return shape;
    }

    private static CompiledFixtureData compiledBox(int id, int partIndex) {
        CompiledFixtureData fixture = new CompiledFixtureData();
        fixture.physicsShapeId = id;
        fixture.partIndex = partIndex;
        fixture.shapeType = PhysicsDirectGeometryData.SHAPE_BOX;
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
