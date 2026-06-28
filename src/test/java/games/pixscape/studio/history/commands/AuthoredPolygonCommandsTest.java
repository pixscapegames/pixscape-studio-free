package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.FixtureIdSequence;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import org.junit.Assert;
import org.junit.Test;

public class AuthoredPolygonCommandsTest {

    @Test
    public void applyAuthoredPolygonCommandUndoRedoRestoresFixturesAndSelection() {
        Harness h = Harness.create();
        FixtureDefData original = h.addFixture(FixtureDefData.SHAPE_BOX);

        ApplyAuthoredPolygonCommand command = new ApplyAuthoredPolygonCommand(
                h.world,
                h.historyIds,
                h.selection,
                h.bodyEid,
                101L,
                decagon(),
                10,
                original,
                original.fixtureId
        );

        command.redo();
        AuthoredPolygonData applied = h.requireAuthored(101L);
        Assert.assertTrue(applied.generatedFixtureIds.length > 0);
        Assert.assertEquals(applied.generatedFixtureIds[0], h.selection.getSelectedFixtureId());

        command.undo();
        Assert.assertTrue(h.containsFixture(original.fixtureId));
        Assert.assertNull(h.findAuthored(101L));

        command.redo();
        AuthoredPolygonData reapplied = h.requireAuthored(101L);
        Assert.assertTrue(reapplied.generatedFixtureIds.length > 0);
        Assert.assertEquals(reapplied.generatedFixtureIds[0], h.selection.getSelectedFixtureId());
    }

    @Test
    public void deleteAuthoredPolygonCommandUndoRedoRemovesAndRestoresGeneratedFixtures() {
        Harness h = Harness.create();
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(h.world);
        AuthoredPolygonData authored = service.applyAuthoredPolygonReplacingFixture(
                h.bodyEid,
                202L,
                decagon(),
                10,
                h.addFixture(FixtureDefData.SHAPE_POLYGON),
                -1L
        );

        h.selection.setSelectedFixture(h.bodyEid, authored.generatedFixtureIds[0]);
        DeleteAuthoredPolygonCommand command = new DeleteAuthoredPolygonCommand(
                h.world,
                h.historyIds,
                h.selection,
                h.bodyEid,
                202L
        );

        command.redo();
        Assert.assertNull(h.findAuthored(202L));
        for (int id : authored.generatedFixtureIds) {
            Assert.assertFalse(h.containsFixture(id));
        }
        Assert.assertEquals(PhysicsSelectionService.NO_FIXTURE, h.selection.getSelectedFixtureId());

        command.undo();
        AuthoredPolygonData restored = h.requireAuthored(202L);
        for (int id : restored.generatedFixtureIds) {
            Assert.assertTrue(h.containsFixture(id));
        }

        command.redo();
        Assert.assertNull(h.findAuthored(202L));
    }

    @Test
    public void moveAuthoredPolygonCommandMovesOffsetsForAuthoringAndGeneratedFixtures() {
        Harness h = Harness.create();
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(h.world);
        AuthoredPolygonData authored = service.applyAuthoredPolygonReplacingFixture(
                h.bodyEid,
                303L,
                decagon(),
                10,
                h.addFixture(FixtureDefData.SHAPE_POLYGON),
                -1L
        );

        int selectedFixture = authored.generatedFixtureIds[0];

        MoveAuthoredPolygonCommand command = new MoveAuthoredPolygonCommand(
                h.world,
                h.historyIds,
                h.selection,
                h.bodyEid,
                303L,
                selectedFixture,
                0f,
                0f,
                2.5f,
                -1.25f
        );

        command.redo();
        assertOffsets(h.requireAuthored(303L), 2.5f, -1.25f);
        assertGeneratedOffsets(h, h.requireAuthored(303L), 2.5f, -1.25f);

        command.undo();
        assertOffsets(h.requireAuthored(303L), 0f, 0f);
        assertGeneratedOffsets(h, h.requireAuthored(303L), 0f, 0f);

        command.redo();
        assertOffsets(h.requireAuthored(303L), 2.5f, -1.25f);
    }

    @Test
    public void moveAuthoredPolygonVertexCommandUndoRedoRegeneratesFixturesAndKeepsSelectionValid() {
        Harness h = Harness.create();
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(h.world);
        FixtureDefData material = h.addFixture(FixtureDefData.SHAPE_POLYGON);

        AuthoredPolygonData authored = service.applyAuthoredPolygonReplacingFixture(
                h.bodyEid,
                404L,
                decagon(),
                10,
                material,
                -1L
        );

        int selectedFixture = authored.generatedFixtureIds[0];
        h.selection.setSelectedFixture(h.bodyEid, selectedFixture);

        float[] beforeVerts = authored.sourceVerts.clone();
        int beforeCount = authored.sourceCount;
        float[] afterVerts = {
                1f, 0f,
                0.3f, 0.9f,
                -0.8f, 0.6f,
                -1f, -0.1f,
                -0.2f, -0.9f,
                0.8f, -0.7f
        };

        MoveAuthoredPolygonVertexCommand command = new MoveAuthoredPolygonVertexCommand(
                h.world,
                h.historyIds,
                h.selection,
                h.bodyEid,
                404L,
                selectedFixture,
                beforeVerts,
                beforeCount,
                afterVerts,
                6,
                material,
                false
        );

        command.redo();
        AuthoredPolygonData after = h.requireAuthored(404L);
        Assert.assertArrayEquals(afterVerts, after.sourceVerts, 0f);
        assertFixtureVertexCountsWithinLimit(h, after);

        command.undo();
        AuthoredPolygonData restored = h.requireAuthored(404L);
        Assert.assertArrayEquals(beforeVerts, restored.sourceVerts, 0f);
        assertFixtureVertexCountsWithinLimit(h, restored);

        command.redo();
        AuthoredPolygonData redone = h.requireAuthored(404L);
        Assert.assertArrayEquals(afterVerts, redone.sourceVerts, 0f);
        assertFixtureVertexCountsWithinLimit(h, redone);
        Assert.assertTrue(h.selection.getSelectedFixtureId() > 0L);
    }

    private static void assertFixtureVertexCountsWithinLimit(Harness h, AuthoredPolygonData polygon) {
        for (int fixtureId : polygon.generatedFixtureIds) {
            FixtureDefData fixture = h.fixtureById(fixtureId);
            Assert.assertNotNull(fixture);
            Assert.assertEquals(FixtureDefData.SHAPE_POLYGON, fixture.shapeType);
            Assert.assertTrue(fixture.polyCount <= 8);
        }
    }

    private static void assertOffsets(AuthoredPolygonData polygon, float x, float y) {
        Assert.assertEquals(x, polygon.offsetX, 0f);
        Assert.assertEquals(y, polygon.offsetY, 0f);
    }

    private static void assertGeneratedOffsets(Harness h, AuthoredPolygonData polygon, float x, float y) {
        for (int fixtureId : polygon.generatedFixtureIds) {
            FixtureDefData fixture = h.fixtureById(fixtureId);
            Assert.assertNotNull(fixture);
            Assert.assertEquals(x, fixture.offsetX, 0f);
            Assert.assertEquals(y, fixture.offsetY, 0f);
        }
    }

    private static float[] decagon() {
        float[] verts = new float[20];
        for (int i = 0; i < 10; i++) {
            double t = (Math.PI * 2d * i) / 10d;
            verts[i * 2] = MathUtils.cos(t);
            verts[i * 2 + 1] = MathUtils.sin(t);
        }
        return verts;
    }

    private static final class Harness {
        final World world = new World(new WorldConfiguration());
        final HistoryIdRegistry historyIds = new HistoryIdRegistry();
        final PhysicsSelectionService selection = new PhysicsSelectionService();
        final int bodyEid = world.create();

        private Harness() {
            historyIds.ensureForEntity(bodyEid);
            world.getMapper(PhysicsFixturesComponent.class).create(bodyEid);
        }

        static Harness create() {
            return new Harness();
        }

        FixtureDefData addFixture(int shapeType) {
            FixtureDefData fixture = new FixtureDefData();
            fixture.shapeType = shapeType;
            fixture.density = 1.7f;
            fixture.friction = 0.52f;
            fixture.restitution = 0.13f;
            FixtureIdSequence.i().ensure(fixture);
            world.getMapper(PhysicsFixturesComponent.class).get(bodyEid).fixtures.add(fixture);
            return fixture;
        }

        AuthoredPolygonData requireAuthored(long authoringId) {
            AuthoredPolygonData authored = findAuthored(authoringId);
            Assert.assertNotNull(authored);
            return authored;
        }

        AuthoredPolygonData findAuthored(long authoringId) {
            PhysicsAuthoringComponent authoring = world.getMapper(PhysicsAuthoringComponent.class).getSafe(bodyEid, null);
            if (authoring == null) {
                return null;
            }
            for (AuthoredPolygonData polygon : authoring.polygons) {
                if (polygon != null && polygon.authoringId == authoringId) {
                    return polygon;
                }
            }
            return null;
        }

        boolean containsFixture(long fixtureId) {
            return fixtureById(fixtureId) != null;
        }

        FixtureDefData fixtureById(long fixtureId) {
            for (FixtureDefData fixture : world.getMapper(PhysicsFixturesComponent.class).get(bodyEid).fixtures) {
                if (fixture != null && fixture.fixtureId == fixtureId) {
                    return fixture;
                }
            }
            return null;
        }
    }
}
