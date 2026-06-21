package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.FixtureIdSequence;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.Assert;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class ClipboardPhysicsAuthoringTest {

    @Test
    public void copyPastePreservesPhysicsAuthoringAndGeneratedFixtureIdsMapping() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        ClipboardService clipboard = new ClipboardService(newTestCanvas(world, selection, history));

        int source = createPhysicsEntityWithAuthoring(world);
        selection.selectOnly(source);

        Assert.assertTrue(clipboard.copySelection());
        Assert.assertTrue(clipboard.paste());

        IntArray selected = selection.getSelectionSnapshot();
        Assert.assertEquals(1, selected.size);

        int pasted = selected.first();
        Assert.assertNotEquals(source, pasted);

        PhysicsAuthoringComponent pastedAuthoring = world.getMapper(PhysicsAuthoringComponent.class).getSafe(pasted, null);
        PhysicsFixturesComponent pastedFixtures = world.getMapper(PhysicsFixturesComponent.class).getSafe(pasted, null);

        Assert.assertNotNull(pastedAuthoring);
        Assert.assertNotNull(pastedFixtures);
        Assert.assertEquals(1, pastedAuthoring.polygons.size);

        AuthoredPolygonData polygon = pastedAuthoring.polygons.first();
        Assert.assertEquals(4, polygon.sourceCount);
        Assert.assertEquals(1, polygon.convexParts.size);
        Assert.assertEquals(1, polygon.generatedFixtureIds.length);

        FixtureDefData generated = fixtureById(pastedFixtures, polygon.generatedFixtureIds[0]);
        Assert.assertNotNull(generated);
        Assert.assertEquals(FixtureDefData.SHAPE_POLYGON, generated.shapeType);
        Assert.assertEquals(4, generated.polyCount);

        int matchingFixtureIds = 0;
        for (int id : polygon.generatedFixtureIds) {
            if (fixtureById(pastedFixtures, id) != null) {
                matchingFixtureIds++;
            }
        }
        Assert.assertEquals(polygon.generatedFixtureIds.length, matchingFixtureIds);
        Assert.assertEquals(1, countNonGeneratedFixtures(pastedFixtures, polygon));
    }

    private static int createPhysicsEntityWithAuthoring(World world) {
        int eid = world.create();

        world.getMapper(TransformComponent.class).create(eid);
        world.getMapper(EntityIndexComponent.class).create(eid);
        world.getMapper(PhysicsBodyComponent.class).create(eid);

        PhysicsFixturesComponent fixtures = world.getMapper(PhysicsFixturesComponent.class).create(eid);
        FixtureDefData generatedFixture = new FixtureDefData();
        generatedFixture.shapeType = FixtureDefData.SHAPE_POLYGON;
        generatedFixture.polyCount = 4;
        generatedFixture.polyVerts = new float[] {0f, 0f, 2f, 0f, 2f, 1f, 0f, 1f};
        FixtureIdSequence.i().ensure(generatedFixture);
        fixtures.fixtures.add(generatedFixture);

        FixtureDefData independent = new FixtureDefData();
        independent.shapeType = FixtureDefData.SHAPE_BOX;
        FixtureIdSequence.i().ensure(independent);
        fixtures.fixtures.add(independent);

        PhysicsAuthoringComponent authoring = world.getMapper(PhysicsAuthoringComponent.class).create(eid);
        AuthoredPolygonData polygon = new AuthoredPolygonData();
        polygon.authoringId = 11L;
        polygon.sourceCount = 4;
        polygon.sourceVerts = new float[] {0f, 0f, 2f, 0f, 2f, 1f, 0f, 1f};
        polygon.generatedFixtureIds = new int[] {generatedFixture.fixtureId};

        ConvexPolygonPartData part = new ConvexPolygonPartData();
        part.count = 4;
        part.verts = polygon.sourceVerts.clone();
        polygon.convexParts.add(part);

        authoring.polygons.add(polygon);
        return eid;
    }

    private static int countNonGeneratedFixtures(PhysicsFixturesComponent fixtures, AuthoredPolygonData polygon) {
        int count = 0;
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId != polygon.generatedFixtureIds[0]) {
                count++;
            }
        }
        return count;
    }

    private static FixtureDefData fixtureById(PhysicsFixturesComponent fixtures, long fixtureId) {
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) {
                return fixture;
            }
        }
        return null;
    }

    private static WorldCanvas newTestCanvas(World world,
                                              SelectionService selection,
                                              HistoryManager history) throws Exception {
        Unsafe unsafe = getUnsafe();
        WorldCanvas canvas = (WorldCanvas) unsafe.allocateInstance(WorldCanvas.class);
        setFieldUnsafe(unsafe, canvas, "world", world);
        setFieldUnsafe(unsafe, canvas, "selectionService", selection);
        setFieldUnsafe(unsafe, canvas, "historyManager", history);
        return canvas;
    }

    private static Unsafe getUnsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setFieldUnsafe(Unsafe unsafe, Object target, String fieldName, Object value) throws Exception {
        Field field = WorldCanvas.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putObject(target, offset, value);
    }
}
