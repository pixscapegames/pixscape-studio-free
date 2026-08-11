package games.pixscape.studio.history.commands;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.LongArray;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.Initializer;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Regression coverage for particle history transitions that intentionally omit structural flushes. */
public class ParticleHistoryIdentityNoFlushTest {
    private static final int STRESS_CYCLES = Integer.getInteger(
            "pixscape.test.particleHistoryStressCycles", 500);

    @Test
    public void createImmediateUndoRedoThenProcess() {
        Harness h = new Harness();
        CreateEntityCommand command = h.particleCommand(1001, "effects/fire.p", "main");

        h.history.execute(command);
        h.recordDeleteRequest(h.currentEntity);
        h.history.undo();
        h.history.redo();
        h.assertCheckpoint("create/undo/redo before process", false);
        h.processAndAssert("create/undo/redo process");

        Assert.assertTrue(h.hasParticle(h.currentEntity));
        Assert.assertEquals(1001, h.stableId(h.currentEntity));
    }

    @Test
    public void createProcessThenUndoRedoWithoutIntermediateProcess() {
        Harness h = new Harness();
        h.history.execute(h.particleCommand(1002, "effects/fire.p", "main"));
        h.processAndAssert("initial create");
        int original = h.currentEntity;

        h.recordDeleteRequest(original);
        h.history.undo();
        h.history.redo();
        h.assertCheckpoint("processed create then undo/redo", false);
        h.processAndAssert("processed create then undo/redo process");

        Assert.assertNotEquals(original, h.currentEntity);
        Assert.assertTrue(h.recorder.containsPrefix("removed eid=" + original + " active=true stableId=1002"));
        Assert.assertTrue(h.recorder.containsPrefix("inserted eid=" + h.currentEntity + " active=true stableId=1002"));
        Assert.assertTrue(h.recorder.indexOfPrefix("removed eid=" + original)
                < h.recorder.indexOfPrefix("inserted eid=" + h.currentEntity));
        h.printDiagnostics("processed create undo/redo");
    }

    @Test
    public void deleteExistingParticleThenUndoRedoBeforeProcess() {
        Harness h = new Harness();
        int original = h.createExistingParticle(1003, "effects/smoke.p", "main");
        long historyId = h.historyIds.historyIdOfEntity(original);
        h.processAndAssert("existing particle");

        DeleteEntitiesCommand delete = new DeleteEntitiesCommand(h.world, h.historyIds, ids(original), h::recordCreated);
        h.recordDeleteRequest(original);
        h.history.execute(delete);
        h.history.undo();
        int restored = h.historyIds.entityOfHistoryId(historyId);
        h.recordDeleteRequest(restored);
        h.history.redo();
        h.assertCheckpoint("delete/undo/redo before process", false);
        h.processAndAssert("delete/undo/redo process");

        Assert.assertEquals(-1, h.historyIds.entityOfHistoryId(historyId));
        Assert.assertFalse(h.world.getEntityManager().isActive(original));
        Assert.assertFalse(h.world.getEntityManager().isActive(restored));
    }

    @Test
    public void rapidAlternatingCyclesBeforeOneProcess() {
        Harness h = new Harness();
        h.history.execute(h.particleCommand(1004, "effects/sparks.p", "main"));
        h.processAndAssert("rapid initial create");
        long historyId = h.historyIds.historyIdOfEntity(h.currentEntity);

        h.deleteCurrentAndUndo();
        h.history.redo();
        h.deleteCurrentAndUndo();
        h.history.redo();
        h.assertCheckpoint("undo/redo/undo/redo", false);
        h.processAndAssert("rapid alternating process");

        Assert.assertEquals(historyId, h.historyIds.historyIdOfEntity(h.currentEntity));
        Assert.assertEquals(1004, h.stableId(h.currentEntity));
    }

    @Test
    public void compositeParticleAndSpriteNoFlush() {
        Harness h = new Harness();
        List<Command> children = new ArrayList<>();
        children.add(h.particleCommand(1005, "effects/fire.p", "main"));
        children.add(h.spriteCommand(1006));
        CompositeCommand composite = new CompositeCommand("Particle and sprite", children);

        h.history.execute(composite);
        h.processAndAssert("composite create");
        h.recordAllMappedDeletes();
        h.history.undo();
        h.history.redo();
        h.assertCheckpoint("composite undo/redo", false);
        h.processAndAssert("composite undo/redo process");

        Assert.assertEquals(1, h.countParticles());
        Assert.assertEquals(2, h.countLogicalIdentities());
    }

    @Test
    public void twoParticlesSharingEffectAndAtlasKeepDistinctIdentities() {
        Harness h = new Harness();
        h.history.execute(h.particleCommand(1007, "effects/fire.p", "main"));
        h.history.execute(h.particleCommand(1008, "effects/fire.p", "main"));
        h.processAndAssert("two shared-asset particles");

        Assert.assertEquals(2, h.countParticles());
        Assert.assertEquals(2, h.countLogicalIdentities());
    }

    @Test
    public void particleAndOrdinarySpriteControlCase() {
        Harness h = new Harness();
        h.history.execute(h.particleCommand(1009, "effects/fire.p", "main"));
        h.history.execute(h.spriteCommand(1010));
        h.processAndAssert("particle and sprite");

        Assert.assertEquals(1, h.countParticles());
        Assert.assertEquals(2, h.countLogicalIdentities());
    }

    @Test
    public void createDeleteTraversalTargetsCurrentParticleIncarnation() {
        TraversalResult result = runProcessSeparatedTraversal(true, 1011);

        Assert.assertNotEquals(result.originalEntity, result.deleteRestoredEntity);
        Assert.assertNotEquals(result.deleteRestoredEntity, result.createRedoneEntity);
        Assert.assertNull(result.harness.world
                .getMapper(PixscapeIdentityComponent.class)
                .getSafe(result.deleteRestoredEntity, null));
    }

    @Test
    public void createDeleteTraversalWithoutFlushDoesNotDuplicateParticleIdentity() {
        Harness h = new Harness();
        int stableId = 1012;
        h.history.execute(h.particleCommand(stableId, "effects/fire.p", "main"));
        h.processAndAssert("execute Create particle");
        int original = h.currentEntity;
        long historyId = h.historyIds.historyIdOfEntity(original);

        h.recordDeleteRequest(original);
        h.history.execute(new DeleteEntitiesCommand(
                h.world,
                h.historyIds,
                ids(original),
                h::recordCreated
        ));
        h.processAndAssert("execute Delete Entities");
        Assert.assertEquals(original, h.occupyRecycledEntitySlot());

        h.history.undo();
        int deleteRestored = h.historyIds.entityOfHistoryId(historyId);
        h.recordState("undo Delete Entities before process", historyId, stableId);
        h.recordDeleteRequest(deleteRestored);
        h.history.undo();
        h.recordState("undo Create particle before process", historyId, stableId);
        h.history.redo();
        int createRedone = h.historyIds.entityOfHistoryId(historyId);
        h.recordState("redo Create particle before process", historyId, stableId);
        h.assertCheckpoint("undo D / undo C / redo C before one process", false);
        h.processAndAssert("undo D / undo C / redo C process");

        Assert.assertNotEquals(deleteRestored, createRedone);
        h.assertLogicalState("no-flush create restored", historyId, stableId, createRedone, 1);
    }

    @Test
    public void createDeleteFullStackTraversalRemainsUniqueForOneHundredCycles() {
        Harness h = new Harness();
        int stableId = 1013;
        h.history.execute(h.particleCommand(stableId, "effects/fire.p", "main"));
        h.processAndAssert("repetition execute Create particle");
        int original = h.currentEntity;
        long historyId = h.historyIds.historyIdOfEntity(original);
        h.recordDeleteRequest(original);
        h.history.execute(new DeleteEntitiesCommand(
                h.world,
                h.historyIds,
                ids(original),
                h::recordCreated
        ));
        h.processAndAssert("repetition execute Delete Entities");

        undoToBeginning(h, historyId, stableId, "initial");
        for (int iteration = 0; iteration < 100; iteration++) {
            redoToEnd(h, historyId, stableId, "iteration " + iteration);
            undoToBeginning(h, historyId, stableId, "iteration " + iteration);
        }
    }

    @Test
    public void createDeleteTraversalTargetsCurrentSpriteIncarnation() {
        TraversalResult result = runProcessSeparatedTraversal(false, 1014);

        Assert.assertNotEquals(result.originalEntity, result.deleteRestoredEntity);
        Assert.assertNotEquals(result.deleteRestoredEntity, result.createRedoneEntity);
        Assert.assertNull(result.harness.world
                .getMapper(PixscapeIdentityComponent.class)
                .getSafe(result.deleteRestoredEntity, null));
    }

    @Test
    public void createRedoRejectsAnAlreadyActiveCurrentIncarnation() {
        Harness h = new Harness();
        CreateEntityCommand create = h.particleCommand(
                1015,
                "effects/fire.p",
                "main"
        );
        h.history.execute(create);
        h.processAndAssert("redo guard execute Create particle");
        int currentEntity = h.currentEntity;
        long historyId = h.historyIds.historyIdOfEntity(currentEntity);

        try {
            create.redo();
            Assert.fail("Expected active-incarnation history invariant failure");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(
                    "Cannot redo CreateEntityCommand for historyId " + historyId
                            + ": current incarnation entity " + currentEntity
                            + " is still active.",
                    expected.getMessage()
            );
        }

        Assert.assertEquals(currentEntity, h.historyIds.entityOfHistoryId(historyId));
        h.assertLogicalState("redo guard", historyId, 1015, currentEntity, 1);
    }

    @Test
    public void deterministicParticleHistoryStress() {
        Harness h = new Harness();
        h.history.execute(h.particleCommand(2001, "effects/fire.p", "main"));
        h.processAndAssert("stress initial particle");

        for (int i = 0; i < STRESS_CYCLES; i++) {
            h.recordDeleteRequest(h.currentEntity);
            h.history.undo();
            h.processAndAssert("separated undo iteration " + i);
            h.history.redo();
            h.processAndAssert("separated redo iteration " + i);
        }

        h.resetTimings();
        for (int i = 0; i < STRESS_CYCLES; i++) {
            h.recordDeleteRequest(h.currentEntity);
            h.timedUndo();
            h.timedRedo();
            h.timedProcessAndAssert("no-flush pair iteration " + i);
        }
        h.printTimings();

        for (int i = 0; i < STRESS_CYCLES; i++) {
            h.recordDeleteRequest(h.currentEntity);
            h.history.undo();
            h.history.redo();
            h.recordDeleteRequest(h.currentEntity);
            h.history.undo();
            h.history.redo();
            h.processAndAssert("rapid create/restore iteration " + i);
        }

        int existing = h.createExistingParticle(3001, "effects/smoke.p", "main");
        long existingHistoryId = h.historyIds.historyIdOfEntity(existing);
        h.processAndAssert("stress delete seed");
        DeleteEntitiesCommand delete = new DeleteEntitiesCommand(h.world, h.historyIds, ids(existing), h::recordCreated);
        h.recordDeleteRequest(existing);
        h.history.execute(delete);
        h.processAndAssert("stress initial delete");
        for (int i = 0; i < STRESS_CYCLES; i++) {
            h.history.undo();
            int restored = h.historyIds.entityOfHistoryId(existingHistoryId);
            h.recordDeleteRequest(restored);
            h.history.redo();
            h.processAndAssert("delete/restore iteration " + i);
            h.history.undo();
            h.processAndAssert("delete restore ready iteration " + i);
            h.recordDeleteRequest(h.historyIds.entityOfHistoryId(existingHistoryId));
            h.history.redo();
            h.processAndAssert("delete ready iteration " + i);
        }

        h.history.execute(h.spriteCommand(4001));
        h.processAndAssert("alternating sprite seed");
        for (int i = 0; i < STRESS_CYCLES; i++) {
            h.recordDeleteRequest(h.currentEntity);
            h.history.undo();
            h.history.redo();
            h.processAndAssert("alternating sprite iteration " + i);
        }
    }

    private static TraversalResult runProcessSeparatedTraversal(
            boolean particle,
            int stableId
    ) {
        Harness h = new Harness();
        CreateEntityCommand create = particle
                ? h.particleCommand(stableId, "effects/fire.p", "main")
                : h.spriteCommand(stableId);
        String kind = particle ? "particle" : "sprite";

        h.history.execute(create);
        int original = h.currentEntity;
        long historyId = h.historyIds.historyIdOfEntity(original);
        h.recordState("execute Create " + kind + " before process", historyId, stableId);
        h.processAndAssert("execute Create " + kind);
        h.assertLogicalState("after Create " + kind, historyId, stableId, original, 1);

        DeleteEntitiesCommand delete = new DeleteEntitiesCommand(
                h.world,
                h.historyIds,
                ids(original),
                h::recordCreated
        );
        h.recordDeleteRequest(original);
        h.history.execute(delete);
        h.recordState("execute Delete Entities before process", historyId, stableId);
        h.processAndAssert("execute Delete Entities");
        h.assertLogicalState("after Delete Entities", historyId, stableId, -1, 0);
        Assert.assertEquals(original, h.occupyRecycledEntitySlot());

        h.history.undo();
        int deleteRestored = h.historyIds.entityOfHistoryId(historyId);
        h.recordState("undo Delete Entities before process", historyId, stableId);
        h.processAndAssert("undo Delete Entities");
        h.assertLogicalState(
                "after undo Delete Entities",
                historyId,
                stableId,
                deleteRestored,
                1
        );

        h.recordDeleteRequest(deleteRestored);
        h.history.undo();
        h.recordState("undo Create " + kind + " before process", historyId, stableId);
        Assert.assertEquals(-1, h.historyIds.entityOfHistoryId(historyId));
        Assert.assertTrue(h.world.getEntityManager().isActive(deleteRestored));
        h.processAndAssert("undo Create " + kind);
        h.assertLogicalState("after undo Create " + kind, historyId, stableId, -1, 0);
        Assert.assertEquals(deleteRestored, h.occupyRecycledEntitySlot());

        h.history.redo();
        int createRedone = h.historyIds.entityOfHistoryId(historyId);
        h.recordState("redo Create " + kind + " before process", historyId, stableId);
        h.processAndAssert("redo Create " + kind);
        h.assertLogicalState(
                "after redo Create " + kind,
                historyId,
                stableId,
                createRedone,
                1
        );

        h.recordDeleteRequest(createRedone);
        h.history.redo();
        h.recordState("redo Delete Entities before process", historyId, stableId);
        h.processAndAssert("redo Delete Entities");
        h.assertLogicalState("after redo Delete Entities", historyId, stableId, -1, 0);
        h.printStateTrace(kind + " create/delete traversal");

        return new TraversalResult(h, original, deleteRestored, createRedone);
    }

    private static void undoToBeginning(
            Harness h,
            long historyId,
            int stableId,
            String iteration
    ) {
        h.occupyRecycledEntitySlot();
        h.history.undo();
        int restored = h.historyIds.entityOfHistoryId(historyId);
        h.recordState(iteration + " undo Delete Entities before process", historyId, stableId);
        h.processAndAssert(iteration + " undo Delete Entities");
        h.assertLogicalState(iteration + " restored", historyId, stableId, restored, 1);

        h.recordDeleteRequest(restored);
        h.history.undo();
        h.recordState(iteration + " undo Create particle before process", historyId, stableId);
        h.processAndAssert(iteration + " undo Create particle");
        h.assertLogicalState(iteration + " beginning", historyId, stableId, -1, 0);
    }

    private static void redoToEnd(
            Harness h,
            long historyId,
            int stableId,
            String iteration
    ) {
        h.occupyRecycledEntitySlot();
        h.history.redo();
        int recreated = h.historyIds.entityOfHistoryId(historyId);
        h.recordState(iteration + " redo Create particle before process", historyId, stableId);
        h.processAndAssert(iteration + " redo Create particle");
        h.assertLogicalState(iteration + " recreated", historyId, stableId, recreated, 1);

        h.recordDeleteRequest(recreated);
        h.history.redo();
        h.recordState(iteration + " redo Delete Entities before process", historyId, stableId);
        h.processAndAssert(iteration + " redo Delete Entities");
        h.assertLogicalState(iteration + " end", historyId, stableId, -1, 0);
    }

    private static IntArray ids(int... values) {
        IntArray out = new IntArray(false, values.length);
        for (int value : values) out.add(value);
        return out;
    }

    private static final class Harness {
        final World world = new World(new WorldConfiguration());
        final HistoryManager history = new HistoryManager(64);
        final HistoryIdRegistry historyIds = history.historyIds();
        final IdentityRegistry identities = new IdentityRegistry();
        final LifecycleRecorder recorder;
        final IntSet knownEntities = new IntSet();
        final LongArray knownHistoryIds = new LongArray();
        final Map<Long, Integer> expectedStableIds = new HashMap<>();
        final List<String> stateTrace = new ArrayList<>();
        long undoNs;
        long redoNs;
        long processNs;
        int undoCalls;
        int redoCalls;
        int processCalls;
        int currentEntity = -1;

        Harness() {
            SceneMeta meta = new SceneMeta();
            meta.nextEntityStableId = 10_000;
            identities.bind(world, meta);
            recorder = new LifecycleRecorder(world);
        }

        CreateEntityCommand particleCommand(int stableId, String effect, String atlas) {
            GenericEntityInitializer delegate = new GenericEntityInitializer(world)
                    .configureParticleEmitter(effect, atlas, 1f, 2f, 0, "Particle " + stableId);
            delegate.setIdentityStableId(stableId);
            return command(new RecordingInitializer(delegate, recorder));
        }

        CreateEntityCommand spriteCommand(int stableId) {
            GenericEntityInitializer delegate = new GenericEntityInitializer(world)
                    .configureStandaloneSprite(stableId, "main", 16, 16, stableId,
                            stableId, 8f, 8f, 0, 0, 0, "Sprite " + stableId, 0);
            delegate.setIdentityStableId(stableId);
            return command(new RecordingInitializer(delegate, recorder));
        }

        private CreateEntityCommand command(Initializer initializer) {
            return new CreateEntityCommand(world, historyIds, initializer, this::recordCreated);
        }

        int createExistingParticle(int stableId, String effect, String atlas) {
            int entity = world.create();
            recordCreated(entity);
            GenericEntityInitializer initializer = new GenericEntityInitializer(world)
                    .configureParticleEmitter(effect, atlas, 0f, 0f, 0, "Existing " + stableId);
            initializer.setIdentityStableId(stableId);
            initializer.init(entity);
            recorder.identityCreated(entity);
            long historyId = historyIds.ensureForEntity(entity);
            rememberHistoryId(historyId);
            rememberExpectedStableId(historyId, stableId);
            return entity;
        }

        void recordCreated(int entity) {
            currentEntity = entity;
            knownEntities.add(entity);
            recorder.entityCreated(entity);
            long historyId = historyIds.historyIdOfEntity(entity);
            if (historyId > 0) {
                rememberHistoryId(historyId);
                PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class)
                        .getSafe(entity, null);
                if (identity != null) rememberExpectedStableId(historyId, identity.stableId);
            }
        }

        void recordDeleteRequest(int entity) {
            if (entity >= 0) recorder.deleteRequested(entity);
        }

        void recordAllMappedDeletes() {
            IntSet.IntSetIterator iterator = knownEntities.iterator();
            while (iterator.hasNext) {
                int entity = iterator.next();
                if (world.getEntityManager().isActive(entity)
                        && historyIds.historyIdOfEntity(entity) > 0) {
                    recordDeleteRequest(entity);
                }
            }
        }

        void deleteCurrentAndUndo() {
            recordDeleteRequest(currentEntity);
            history.undo();
        }

        void processAndAssert(String operation) {
            try {
                world.process();
                recorder.clearCompletedDeletes();
                assertCheckpoint(operation, true);
            } catch (AssertionError | RuntimeException failure) {
                throw new AssertionError(operation + "\n" + recorder.dump(), failure);
            }
        }

        void timedUndo() {
            long start = System.nanoTime();
            history.undo();
            undoNs += System.nanoTime() - start;
            undoCalls++;
        }

        void timedRedo() {
            long start = System.nanoTime();
            history.redo();
            redoNs += System.nanoTime() - start;
            redoCalls++;
        }

        void timedProcessAndAssert(String operation) {
            long start = System.nanoTime();
            processAndAssert(operation);
            processNs += System.nanoTime() - start;
            processCalls++;
        }

        void printDiagnostics(String label) {
            if (Boolean.getBoolean("pixscape.debug.particleHistory")) {
                System.out.println("[particle-history] " + label + "\n" + recorder.dump());
            }
        }

        int occupyRecycledEntitySlot() {
            return world.create();
        }

        void recordState(String operation, long historyId, int stableId) {
            int mapped = historyId > 0L
                    ? historyIds.entityOfHistoryId(historyId)
                    : -1;
            stateTrace.add(operation
                    + " direction=" + history.peekUndoLabel() + " <-undo | redo-> "
                    + history.peekRedoLabel()
                    + " historyId=" + historyId
                    + " mappedEntity=" + mapped
                    + " stableId=" + stableId
                    + " identityIndex=" + identities.findByStableId(stableId)
                    + " incarnations=" + stableIdIncarnations(stableId));
        }

        void assertLogicalState(
                String operation,
                long historyId,
                int stableId,
                int expectedMappedEntity,
                int expectedCount
        ) {
            recordState(operation, historyId, stableId);
            try {
                Assert.assertEquals(operation + ": history mapping",
                        expectedMappedEntity,
                        historyIds.entityOfHistoryId(historyId));
                Assert.assertEquals(operation + ": logical stable-id count",
                        expectedCount,
                        countStableId(stableId));
                Assert.assertEquals(operation + ": identity index",
                        expectedMappedEntity,
                        identities.findByStableId(stableId));
                assertCheckpoint(operation, true);
            } catch (AssertionError failure) {
                throw new AssertionError(operation + "\n" + String.join("\n", stateTrace), failure);
            }
        }

        void printStateTrace(String label) {
            if (!Boolean.getBoolean("pixscape.debug.particleHistory")) return;
            System.out.println("[particle-history] " + label + "\n"
                    + String.join("\n", stateTrace));
        }

        private int countStableId(int stableId) {
            int count = 0;
            ComponentMapper<PixscapeIdentityComponent> mapper =
                    world.getMapper(PixscapeIdentityComponent.class);
            IntSet.IntSetIterator iterator = knownEntities.iterator();
            while (iterator.hasNext) {
                int entity = iterator.next();
                PixscapeIdentityComponent identity = mapper.getSafe(entity, null);
                if (world.getEntityManager().isActive(entity)
                        && !recorder.isPendingDelete(entity)
                        && identity != null
                        && identity.stableId == stableId) {
                    count++;
                }
            }
            return count;
        }

        private String stableIdIncarnations(int stableId) {
            List<String> incarnations = new ArrayList<>();
            ComponentMapper<PixscapeIdentityComponent> mapper =
                    world.getMapper(PixscapeIdentityComponent.class);
            IntSet.IntSetIterator iterator = knownEntities.iterator();
            while (iterator.hasNext) {
                int entity = iterator.next();
                PixscapeIdentityComponent identity = mapper.getSafe(entity, null);
                if (identity == null || identity.stableId != stableId) continue;
                incarnations.add("entity=" + entity
                        + ",active=" + world.getEntityManager().isActive(entity)
                        + ",deleteRequested=" + recorder.isPendingDelete(entity));
            }
            return incarnations.toString();
        }

        void printTimings() {
            if (!Boolean.getBoolean("pixscape.debug.particleHistory")) return;
            System.out.println("[particle-history] undo calls=" + undoCalls
                    + " totalNs=" + undoNs + " averageNs=" + average(undoNs, undoCalls));
            System.out.println("[particle-history] redo calls=" + redoCalls
                    + " totalNs=" + redoNs + " averageNs=" + average(redoNs, redoCalls));
            System.out.println("[particle-history] world.process calls=" + processCalls
                    + " totalNs=" + processNs + " averageNs=" + average(processNs, processCalls));
            System.out.println("[particle-history] initializer.syncFrom calls=" + recorder.syncCalls
                    + " totalNs=" + recorder.syncNs + " averageNs="
                    + average(recorder.syncNs, recorder.syncCalls));
            System.out.println("[particle-history] initializer.init calls=" + recorder.initCalls
                    + " totalNs=" + recorder.initNs + " averageNs="
                    + average(recorder.initNs, recorder.initCalls));
        }

        void resetTimings() {
            undoNs = 0L;
            redoNs = 0L;
            processNs = 0L;
            undoCalls = 0;
            redoCalls = 0;
            processCalls = 0;
            recorder.syncNs = 0L;
            recorder.initNs = 0L;
            recorder.syncCalls = 0;
            recorder.initCalls = 0;
        }

        private static long average(long total, int calls) {
            return calls == 0 ? 0L : total / calls;
        }

        void assertCheckpoint(String operation, boolean requireRegistryAgreement) {
            IntMap<Integer> stableOwners = new IntMap<>();
            Set<Long> mappedHistoryIds = new HashSet<>();
            ComponentMapper<PixscapeIdentityComponent> mIdentity = world.getMapper(PixscapeIdentityComponent.class);
            IntSet.IntSetIterator iterator = knownEntities.iterator();
            while (iterator.hasNext) {
                int entity = iterator.next();
                if (!world.getEntityManager().isActive(entity)) continue;
                PixscapeIdentityComponent identity = mIdentity.getSafe(entity, null);
                if (identity == null || recorder.isPendingDelete(entity)) continue;
                if (identity.stableId != IdentityRegistry.UNASSIGNED_STABLE_ID) {
                    Integer previous = stableOwners.put(identity.stableId, entity);
                    Assert.assertNull(operation + ": stableId " + identity.stableId
                            + " belongs to both " + previous + " and " + entity, previous);
                    if (requireRegistryAgreement) {
                        Assert.assertEquals(operation + ": IdentityRegistry disagreement for "
                                + identity.stableId, entity, identities.findByStableId(identity.stableId));
                    }
                }
                long historyId = historyIds.historyIdOfEntity(entity);
                if (historyId > 0) {
                    Assert.assertTrue(operation + ": historyId " + historyId + " is multiply bound",
                            mappedHistoryIds.add(historyId));
                    Assert.assertEquals(operation + ": reverse history mapping mismatch",
                            entity, historyIds.entityOfHistoryId(historyId));
                }
            }
            if (requireRegistryAgreement) {
                for (IntMap.Entry<Integer> entry : stableOwners) {
                    Assert.assertEquals(entry.value.intValue(), identities.findByStableId(entry.key));
                }
                IntSet historyEntities = new IntSet();
                for (int i = 0; i < knownHistoryIds.size; i++) {
                    long historyId = knownHistoryIds.get(i);
                    int entity = historyIds.entityOfHistoryId(historyId);
                    if (entity < 0) continue;
                    Assert.assertTrue(operation + ": historyId " + historyId
                                    + " retains inactive entity " + entity,
                            world.getEntityManager().isActive(entity));
                    Assert.assertTrue(operation + ": entity " + entity
                                    + " is bound to multiple history IDs",
                            historyEntities.add(entity));
                    Assert.assertEquals(operation + ": history reverse mapping mismatch",
                            historyId, historyIds.historyIdOfEntity(entity));
                    Integer expectedStableId = expectedStableIds.get(historyId);
                    if (expectedStableId != null) {
                        Assert.assertEquals(operation + ": historyId " + historyId
                                        + " silently changed stableId",
                                expectedStableId.intValue(), stableId(entity));
                    }
                }
            }
        }

        int countParticles() {
            int count = 0;
            IntSet.IntSetIterator iterator = knownEntities.iterator();
            while (iterator.hasNext) {
                int entity = iterator.next();
                if (world.getEntityManager().isActive(entity) && !recorder.isPendingDelete(entity)
                        && hasParticle(entity)) count++;
            }
            return count;
        }

        int countLogicalIdentities() {
            int count = 0;
            ComponentMapper<PixscapeIdentityComponent> mapper = world.getMapper(PixscapeIdentityComponent.class);
            IntSet.IntSetIterator iterator = knownEntities.iterator();
            while (iterator.hasNext) {
                int entity = iterator.next();
                if (world.getEntityManager().isActive(entity) && !recorder.isPendingDelete(entity)
                        && mapper.getSafe(entity, null) != null) count++;
            }
            return count;
        }

        boolean hasParticle(int entity) {
            return world.getMapper(ParticleEmitterComponent.class).getSafe(entity, null) != null;
        }

        int stableId(int entity) {
            return world.getMapper(PixscapeIdentityComponent.class).get(entity).stableId;
        }

        int findByStableIdDirect(int stableId) {
            ComponentMapper<PixscapeIdentityComponent> mapper = world.getMapper(PixscapeIdentityComponent.class);
            IntSet.IntSetIterator iterator = knownEntities.iterator();
            while (iterator.hasNext) {
                int entity = iterator.next();
                PixscapeIdentityComponent identity = mapper.getSafe(entity, null);
                if (world.getEntityManager().isActive(entity) && !recorder.isPendingDelete(entity)
                        && identity != null && identity.stableId == stableId) return entity;
            }
            return -1;
        }

        private void rememberHistoryId(long historyId) {
            if (historyId > 0 && !knownHistoryIds.contains(historyId)) knownHistoryIds.add(historyId);
        }

        private void rememberExpectedStableId(long historyId, int stableId) {
            if (historyId <= 0 || stableId == IdentityRegistry.UNASSIGNED_STABLE_ID) return;
            Integer previous = expectedStableIds.putIfAbsent(historyId, stableId);
            Assert.assertTrue("historyId " + historyId + " changed stableId from "
                    + previous + " to " + stableId, previous == null || previous == stableId);
        }
    }

    private static final class TraversalResult {
        final Harness harness;
        final int originalEntity;
        final int deleteRestoredEntity;
        final int createRedoneEntity;

        TraversalResult(
                Harness harness,
                int originalEntity,
                int deleteRestoredEntity,
                int createRedoneEntity
        ) {
            this.harness = harness;
            this.originalEntity = originalEntity;
            this.deleteRestoredEntity = deleteRestoredEntity;
            this.createRedoneEntity = createRedoneEntity;
        }
    }

    private static final class RecordingInitializer implements Initializer {
        private final Initializer delegate;
        private final LifecycleRecorder recorder;

        RecordingInitializer(Initializer delegate, LifecycleRecorder recorder) {
            this.delegate = delegate;
            this.recorder = recorder;
        }

        @Override
        public void syncFrom(int sourceEid) {
            long start = System.nanoTime();
            delegate.syncFrom(sourceEid);
            recorder.syncNs += System.nanoTime() - start;
            recorder.syncCalls++;
        }

        @Override
        public void init(int targetEid) {
            recorder.entityCreated(targetEid);
            long start = System.nanoTime();
            delegate.init(targetEid);
            recorder.initNs += System.nanoTime() - start;
            recorder.initCalls++;
            recorder.identityCreated(targetEid);
        }

        @Override
        public String label() {
            return delegate.label();
        }
    }

    private static final class LifecycleRecorder implements EntitySubscription.SubscriptionListener {
        private final World world;
        private final ComponentMapper<PixscapeIdentityComponent> identities;
        private final List<String> events = new ArrayList<>();
        private final IntSet pendingDeletes = new IntSet();
        private final IntSet recordedCreates = new IntSet();
        long syncNs;
        long initNs;
        int syncCalls;
        int initCalls;

        LifecycleRecorder(World world) {
            this.world = world;
            identities = world.getMapper(PixscapeIdentityComponent.class);
            world.getAspectSubscriptionManager()
                    .get(Aspect.all(PixscapeIdentityComponent.class))
                    .addSubscriptionListener(this);
        }

        void entityCreated(int entity) {
            if (recordedCreates.add(entity)) add("created", entity);
        }

        void identityCreated(int entity) {
            add("identity-created", entity);
        }

        void deleteRequested(int entity) {
            pendingDeletes.add(entity);
            add("delete-requested", entity);
        }

        boolean isPendingDelete(int entity) {
            return pendingDeletes.contains(entity);
        }

        void clearCompletedDeletes() {
            IntSet.IntSetIterator iterator = pendingDeletes.iterator();
            while (iterator.hasNext) {
                if (!world.getEntityManager().isActive(iterator.next())) iterator.remove();
            }
        }

        @Override
        public void inserted(IntBag entities) {
            recordBag("inserted", entities);
        }

        @Override
        public void removed(IntBag entities) {
            recordBag("removed", entities);
        }

        private void recordBag(String action, IntBag bag) {
            int[] data = bag.getData();
            for (int i = 0; i < bag.size(); i++) add(action, data[i]);
        }

        private void add(String action, int entity) {
            PixscapeIdentityComponent identity = identities.getSafe(entity, null);
            events.add(action + " eid=" + entity
                    + " active=" + world.getEntityManager().isActive(entity)
                    + " stableId=" + (identity != null ? identity.stableId : -1));
        }

        boolean containsPrefix(String prefix) {
            return indexOfPrefix(prefix) >= 0;
        }

        int indexOfPrefix(String prefix) {
            for (int i = 0; i < events.size(); i++) {
                if (events.get(i).startsWith(prefix)) return i;
            }
            return -1;
        }

        String dump() {
            return String.join("\n", events);
        }
    }
}
