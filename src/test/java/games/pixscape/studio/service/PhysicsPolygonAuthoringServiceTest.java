package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.FixtureIdSequence;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsPolygonAuthoringServiceTest {

    @Test
    public void applyAuthoredPolygonReplacingFixtureCreatesAuthoringAndGeneratedFixtures() {
        World world = new World(new WorldConfiguration());
        int bodyEid = world.create();
        FixtureDefData sourceFixture = newFixture(FixtureDefData.SHAPE_BOX);
        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(bodyEid);
        fixtures.fixtures.add(sourceFixture);

        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(world);
        float[] sourceVerts = decagon();

        AuthoredPolygonData authored = service.applyAuthoredPolygonReplacingFixture(
                bodyEid,
                77L,
                sourceVerts,
                10,
                sourceFixture,
                sourceFixture.fixtureId
        );

        PhysicsAuthoringComponent authoring = world.getMapper(PhysicsAuthoringComponent.class).getSafe(bodyEid, null);
        Assert.assertNotNull(authoring);
        Assert.assertEquals(1, authoring.polygons.size);

        Assert.assertNotNull(authored);
        Assert.assertEquals(10, authored.sourceCount);
        Assert.assertArrayEquals(sourceVerts, authored.sourceVerts, 0f);
        Assert.assertTrue(authored.convexParts.size > 0);
        Assert.assertTrue(authored.generatedFixtureIds.length > 0);

        FixtureDefData reused = fixtureById(fixtures, sourceFixture.fixtureId);
        Assert.assertNotNull(reused);
        Assert.assertEquals(FixtureDefData.SHAPE_POLYGON, reused.shapeType);
        Assert.assertTrue(reused.polyCount <= 8);
        Assert.assertTrue(containsId(authored.generatedFixtureIds, sourceFixture.fixtureId));

        for (int generatedFixtureId : authored.generatedFixtureIds) {
            FixtureDefData generated = fixtureById(fixtures, generatedFixtureId);
            Assert.assertNotNull(generated);
            Assert.assertEquals(FixtureDefData.SHAPE_POLYGON, generated.shapeType);
            Assert.assertTrue(generated.polyCount <= 8);
            Assert.assertEquals(sourceFixture.density, generated.density, 0f);
            Assert.assertEquals(sourceFixture.friction, generated.friction, 0f);
            Assert.assertEquals(sourceFixture.restitution, generated.restitution, 0f);
            Assert.assertEquals(sourceFixture.offsetX, generated.offsetX, 0f);
            Assert.assertEquals(sourceFixture.offsetY, generated.offsetY, 0f);
            Assert.assertEquals(sourceFixture.angleDeg, generated.angleDeg, 0f);
        }
    }

    private static boolean containsId(int[] ids, long id) {
        if (ids == null) return false;
        for (int candidate : ids) {
            if (candidate == id) return true;
        }
        return false;
    }

    @Test
    public void reapplyAndRemoveAuthoredPolygonReplacesGeneratedFixtureSet() {
        World world = new World(new WorldConfiguration());
        int bodyEid = world.create();
        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(bodyEid);

        FixtureDefData material = newFixture(FixtureDefData.SHAPE_POLYGON);
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(world);

        AuthoredPolygonData first = service.applyAuthoredPolygonReplacingFixture(
                bodyEid,
                200L,
                decagon(),
                10,
                material,
                -1L
        );
        int[] firstGeneratedIds = first.generatedFixtureIds.clone();

        AuthoredPolygonData second = service.applyAuthoredPolygonReplacingFixture(
                bodyEid,
                200L,
                new float[] {
                        0f, 0f,
                        3f, 0f,
                        3f, 1f,
                        1.5f, 0.4f,
                        3f, 3f,
                        0f, 3f
                },
                6,
                material,
                -1L
        );

        Assert.assertEquals(second.generatedFixtureIds.length, fixtures.fixtures.size);

        for (int newId : second.generatedFixtureIds) {
            FixtureDefData generated = fixtureById(fixtures, newId);
            Assert.assertNotNull(generated);
            Assert.assertEquals(FixtureDefData.SHAPE_POLYGON, generated.shapeType);
            Assert.assertTrue(generated.polyCount <= 8);
        }

        for (int newId : second.generatedFixtureIds) {
            Assert.assertNotNull(fixtureById(fixtures, newId));
        }

        Assert.assertTrue(service.removeAuthoredPolygon(bodyEid, 200L));
        Assert.assertFalse(service.hasAuthoring(bodyEid));
        for (int removedId : second.generatedFixtureIds) {
            Assert.assertNull(fixtureById(fixtures, removedId));
        }

        Assert.assertEquals(0, fixtures.fixtures.size);
    }

    private static FixtureDefData newFixture(int shapeType) {
        FixtureDefData fixture = new FixtureDefData();
        fixture.shapeType = shapeType;
        fixture.density = 3f;
        fixture.friction = 0.65f;
        fixture.restitution = 0.25f;
        fixture.offsetX = 0.6f;
        fixture.offsetY = -0.4f;
        fixture.angleDeg = 12f;
        FixtureIdSequence.i().ensure(fixture);
        return fixture;
    }

    private static boolean containsFixture(PhysicsFixturesComponent fixtures, long fixtureId) {
        return fixtureById(fixtures, fixtureId) != null;
    }

    private static FixtureDefData fixtureById(PhysicsFixturesComponent fixtures, long fixtureId) {
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) {
                return fixture;
            }
        }
        return null;
    }

    private static float[] decagon() {
        float[] verts = new float[20];
        for (int i = 0; i < 10; i++) {
            float t = (MathUtils.PI2 * i) / 10f;
            verts[i * 2] = MathUtils.cos(t);
            verts[i * 2 + 1] = MathUtils.sin(t);
        }
        return verts;
    }
}
