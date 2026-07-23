package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.studio.component.physics.AuthoredPolygonData;
import games.pixscape.studio.component.physics.PhysicsAuthoringComponent;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.physics.PhysicsPolygonAuthoringService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

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

    @Test
    public void vertexMoveRestoresExactFixtureIdsAcrossCardinalityChangesWithoutAllocatingOnHistoryReplay() {
        Harness h = Harness.create();
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(h.world);
        FixtureDefData material = new FixtureDefData();
        AuthoredPolygonData original = service.applyAuthoredPolygon(
                h.bodyEid, 505L, triangle(), 3);
        int[] beforeIds = original.generatedFixtureIds.clone();
        PhysicsAuthoringBodySnapshot before = PhysicsAuthoringBodySnapshot.capture(
                h.world, h.selection, h.bodyEid);

        AuthoredPolygonData liveAfter = service.applyAuthoredPolygonReplacingFixture(
                h.bodyEid, 505L, decagon(), 10, material, -1L);
        int[] afterIds = liveAfter.generatedFixtureIds.clone();
        Assert.assertTrue(afterIds.length > beforeIds.length);

        MoveAuthoredPolygonVertexCommand command = new MoveAuthoredPolygonVertexCommand(
                h.world, h.historyIds, h.selection, h.bodyEid, 505L,
                afterIds[0], triangle(), 3, decagon(), 10, material, true, before);
        command.redo();
        int highWater = h.nextFixtureId();

        for (int i = 0; i < 5; i++) {
            command.undo();
            Assert.assertArrayEquals(beforeIds, h.requireAuthored(505L).generatedFixtureIds);
            Assert.assertEquals(highWater, h.nextFixtureId());
            command.redo();
            Assert.assertArrayEquals(afterIds, h.requireAuthored(505L).generatedFixtureIds);
            Assert.assertEquals(highWater, h.nextFixtureId());
        }

        MoveAuthoredPolygonVertexCommand decrease = new MoveAuthoredPolygonVertexCommand(
                h.world, h.historyIds, h.selection, h.bodyEid, 505L,
                afterIds[0], decagon(), 10, triangle(), 3, material, false);
        decrease.redo();
        int[] decreasedIds = h.requireAuthored(505L).generatedFixtureIds.clone();
        Assert.assertTrue(decreasedIds.length < afterIds.length);
        int decreasedHighWater = h.nextFixtureId();
        decrease.undo();
        Assert.assertArrayEquals(afterIds, h.requireAuthored(505L).generatedFixtureIds);
        decrease.redo();
        Assert.assertArrayEquals(decreasedIds, h.requireAuthored(505L).generatedFixtureIds);
        Assert.assertEquals(decreasedHighWater, h.nextFixtureId());
    }

    @Test
    public void sameCardinalityInitialEditKeepsFixtureIdAndHistoryBranchNeverReusesDiscardedIds() {
        Harness h = Harness.create();
        HistoryManager history = new HistoryManager(16);
        history.historyIds().ensureForEntity(h.bodyEid);
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(h.world);
        AuthoredPolygonData original = service.applyAuthoredPolygon(h.bodyEid, 606L, triangle(), 3);
        int originalId = original.generatedFixtureIds[0];

        MoveAuthoredPolygonVertexCommand sameCardinality = new MoveAuthoredPolygonVertexCommand(
                h.world, history.historyIds(), h.selection, h.bodyEid, 606L, originalId,
                triangle(), 3, shiftedTriangle(), 3, new FixtureDefData(), false);
        history.execute(sameCardinality);
        Assert.assertArrayEquals(new int[]{originalId},
                h.requireAuthored(606L).generatedFixtureIds);

        MoveAuthoredPolygonVertexCommand discarded = new MoveAuthoredPolygonVertexCommand(
                h.world, history.historyIds(), h.selection, h.bodyEid, 606L, originalId,
                shiftedTriangle(), 3, decagon(), 10, new FixtureDefData(), false);
        history.execute(discarded);
        int[] discardedIds = h.requireAuthored(606L).generatedFixtureIds.clone();
        history.undo();
        Assert.assertTrue(history.canRedo());

        MoveAuthoredPolygonVertexCommand branch = new MoveAuthoredPolygonVertexCommand(
                h.world, history.historyIds(), h.selection, h.bodyEid, 606L, originalId,
                shiftedTriangle(), 3, scaledDecagon(), 10, new FixtureDefData(), false);
        history.execute(branch);
        int[] branchIds = h.requireAuthored(606L).generatedFixtureIds.clone();
        Assert.assertFalse(history.canRedo());
        for (int branchId : branchIds) {
            for (int discardedId : discardedIds) {
                Assert.assertNotEquals(discardedId, branchId);
            }
        }
    }

    @Test
    public void sameMultiPartCardinalityUsesIndexFallbackAndReplaysExactIds() {
        Harness h = Harness.create();
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(h.world);
        AuthoredPolygonData original = service.applyAuthoredPolygon(
                h.bodyEid, 616L, decagon(), 10);
        int[] originalIds = original.generatedFixtureIds.clone();
        Assert.assertTrue(originalIds.length > 1);
        MoveAuthoredPolygonVertexCommand command = new MoveAuthoredPolygonVertexCommand(
                h.world, h.historyIds, h.selection, h.bodyEid, 616L, originalIds[0],
                decagon(), 10, scaledDecagon(), 10, new FixtureDefData(), false);
        command.redo();
        int[] editedIds = h.requireAuthored(616L).generatedFixtureIds.clone();
        Assert.assertArrayEquals(originalIds, editedIds);
        int highWater = h.nextFixtureId();
        command.undo();
        Assert.assertArrayEquals(originalIds, h.requireAuthored(616L).generatedFixtureIds);
        command.redo();
        Assert.assertArrayEquals(editedIds, h.requireAuthored(616L).generatedFixtureIds);
        Assert.assertEquals(highWater, h.nextFixtureId());
    }

    @Test
    public void authoredFixtureIdentitySurvivesSaveReloadAfterUndoAndRedo() {
        World world = new World(games.pixscape.studio.FixtureIdentityTestSupport.configuration()
                .setSystem(new WorldSerializationManager()));
        HistoryIdRegistry historyIds = new HistoryIdRegistry();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        int body = world.create();
        historyIds.ensureForEntity(body);
        world.getMapper(PhysicsFixturesComponent.class).create(body);
        PhysicsPolygonAuthoringService service = new PhysicsPolygonAuthoringService(world);
        AuthoredPolygonData original = service.applyAuthoredPolygon(body, 707L, triangle(), 3);
        int[] beforeIds = original.generatedFixtureIds.clone();
        MoveAuthoredPolygonVertexCommand command = new MoveAuthoredPolygonVertexCommand(
                world, historyIds, selection, body, 707L, beforeIds[0], triangle(), 3,
                decagon(), 10, new FixtureDefData(), false);
        command.redo();
        int[] afterIds = authored(world, 707L).generatedFixtureIds.clone();

        command.undo();
        int highWater = world.getSystem(FixtureIdAllocatorSystem.class).sceneMeta().nextFixtureId;
        assertRoundTripIds(world, beforeIds, highWater, "authored-fixture-after-undo");
        command.redo();
        assertRoundTripIds(world, afterIds, highWater, "authored-fixture-after-redo");
    }

    private static void assertRoundTripIds(World world, int[] expectedIds,
                                           int expectedNextFixtureId, String name) {
        world.process();
        File dir = new File(System.getProperty("java.io.tmpdir"), "pixscape-studio-tests");
        Assert.assertTrue(dir.exists() || dir.mkdirs());
        FileHandle file = new FileHandle(new File(dir, name + ".json"));
        SceneService.saveScene(world, file, false);
        World loaded = new World(games.pixscape.studio.FixtureIdentityTestSupport.configuration()
                .setSystem(new WorldSerializationManager()));
        games.pixscape.studio.FixtureIdentityTestSupport.copyHighWater(world, loaded);
        games.pixscape.studio.FixtureIdentityTestSupport.loadScene(loaded, file, false);
        loaded.process();
        Assert.assertArrayEquals(expectedIds, authored(loaded, 707L).generatedFixtureIds);
        Assert.assertEquals(expectedNextFixtureId,
                loaded.getSystem(FixtureIdAllocatorSystem.class).sceneMeta().nextFixtureId);
    }

    private static AuthoredPolygonData authored(World world, long authoringId) {
        IntBag bodies = world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(PhysicsAuthoringComponent.class)).getEntities();
        for (int i = 0; i < bodies.size(); i++) {
            PhysicsAuthoringComponent component = world.getMapper(PhysicsAuthoringComponent.class)
                    .get(bodies.get(i));
            for (AuthoredPolygonData polygon : component.polygons) {
                if (polygon != null && polygon.authoringId == authoringId) return polygon;
            }
        }
        Assert.fail("Missing authored polygon " + authoringId);
        return null;
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
            verts[i * 2] = (float) Math.cos(t);
            verts[i * 2 + 1] = (float) Math.sin(t);
        }
        return verts;
    }

    private static float[] scaledDecagon() {
        float[] verts = decagon();
        for (int i = 0; i < verts.length; i++) verts[i] *= 2f;
        return verts;
    }

    private static float[] triangle() {
        return new float[]{0f, 0f, 2f, 0f, 0f, 2f};
    }

    private static float[] shiftedTriangle() {
        return new float[]{0f, 0f, 3f, 0f, 0f, 2f};
    }

    private static final class Harness {
        final World world = games.pixscape.studio.FixtureIdentityTestSupport.newWorld();
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
            fixture.fixtureId = games.pixscape.studio.FixtureIdentityTestSupport.allocate(world);
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

        int nextFixtureId() {
            return world.getSystem(FixtureIdAllocatorSystem.class).sceneMeta().nextFixtureId;
        }
    }
}
