package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.service.physics.PhysicsSelectionService;

public final class FixtureCommandSupport {

    private FixtureCommandSupport() {
    }

    static int resolveBodyEntityId(World world, HistoryIdRegistry historyIds, long bodyHistoryId) {
        if (world == null || historyIds == null || bodyHistoryId <= 0L) return -1;
        int entityId = historyIds.entityOfHistoryId(bodyHistoryId);
        if (entityId < 0) return -1;
        if (!world.getEntityManager().isActive(entityId)) return -1;
        return entityId;
    }

    static long toHistoryId(HistoryIdRegistry historyIds, int entityId) {
        if (historyIds == null || entityId < 0) return -1L;
        return historyIds.ensureForEntity(entityId);
    }

    static PhysicsShapesComponent getFixtures(World world, int entityId, boolean create) {
        if (world == null || entityId < 0) return null;
        ComponentMapper<PhysicsShapesComponent> mapper = world.getMapper(PhysicsShapesComponent.class);
        if (mapper.has(entityId)) return mapper.get(entityId);
        return create ? mapper.create(entityId) : null;
    }

    public static int indexOfFixture(PhysicsShapesComponent fixtures, int physicsShapeId) {
        if (fixtures == null || physicsShapeId <= 0L) return -1;
        for (int i = 0, n = fixtures.shapes.size; i < n; i++) {
            PhysicsShapeData fixture = fixtures.shapes.get(i);
            if (fixture == null) continue;
            if (fixture.physicsShapeId == physicsShapeId) return i;
        }
        return -1;
    }

    static PhysicsShapeData fixtureById(PhysicsShapesComponent fixtures, int physicsShapeId) {
        int index = indexOfFixture(fixtures, physicsShapeId);
        return index >= 0 ? fixtures.shapes.get(index) : null;
    }

    public static Array<PhysicsShapeData> copyFixtures(World world, int entityId) {
        PhysicsShapesComponent fixtures = getFixtures(world, entityId, false);
        Array<PhysicsShapeData> copy =
                new Array<>(
                        true,
                        fixtures != null && fixtures.shapes != null ? fixtures.shapes.size : 0,
                        PhysicsShapeData.class);
        if (fixtures != null && fixtures.shapes != null) {
            for (int i = 0; i < fixtures.shapes.size; i++) {
                PhysicsShapeData fixture = fixtures.shapes.get(i);
                copy.add(fixture != null ? fixture.copy() : null);
            }
        }
        return copy;
    }

    public static void prepareAndPublish(
            World world, int entityId, Array<PhysicsShapeData> candidateShapes) {
        ComponentMapper<PhysicsShapesComponent> shapesMapper =
                world.getMapper(PhysicsShapesComponent.class);
        PhysicsShapesComponent currentShapes =
                shapesMapper.getSafe(entityId, null);
        validateLinkedRelations(entityId, currentShapes, candidateShapes);
        PreparedPhysicsBodyCandidate prepared = containsLinkedShape(candidateShapes)
                ? PhysicsService.prepareBodyCandidate(
                        world,
                        entityId,
                        candidateShapes,
                        requireCurrentPixelsPerMeter())
                : PhysicsService.prepareBodyCandidate(candidateShapes);
        ComponentMapper<PhysicsCompiledFixturesComponent> compiledMapper =
                world.getMapper(PhysicsCompiledFixturesComponent.class);
        PhysicsShapesComponent targetShapes = shapesMapper.has(entityId)
                ? shapesMapper.get(entityId)
                : shapesMapper.create(entityId);
        PhysicsCompiledFixturesComponent targetCompiled = compiledMapper.has(entityId)
                ? compiledMapper.get(entityId)
                : compiledMapper.create(entityId);
        PhysicsService.publishPreparedCandidate(targetShapes, targetCompiled, prepared);
    }

    static float requireCurrentPixelsPerMeter() {
        ProjectConfig config = ProjectConfig.getInstance();
        if (config == null) {
            throw new IllegalStateException(
                    "ProjectConfig is required to compile physics fixtures.");
        }
        SceneMeta meta = config.getCurrentSceneMeta();
        if (meta == null) {
            throw new IllegalStateException(
                    "Current scene metadata is required to compile physics fixtures.");
        }
        float pixelsPerMeter = meta.pixelsPerMeter;
        if (Float.isNaN(pixelsPerMeter)
                || Float.isInfinite(pixelsPerMeter)
                || pixelsPerMeter <= 0f) {
            throw new IllegalStateException(
                    "Current scene pixelsPerMeter must be finite and positive, got "
                            + pixelsPerMeter + ".");
        }
        return pixelsPerMeter;
    }

    static boolean containsLinkedShape(Array<PhysicsShapeData> shapes) {
        if (shapes == null) return false;
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape != null && shape.spatialBlockId > 0) return true;
        }
        return false;
    }

    static void validateLinkedRelations(
            int bodyEntityId,
            PhysicsShapesComponent current,
            Array<PhysicsShapeData> candidates) {
        Array<PhysicsShapeData> currentShapes =
                current != null ? current.shapes : null;

        if (currentShapes != null) {
            for (int i = 0; i < currentShapes.size; i++) {
                PhysicsShapeData linked = currentShapes.get(i);
                if (!isLinked(linked)) continue;
                int candidateCount = countByPhysicsShapeId(
                        candidates, linked.physicsShapeId);
                if (candidateCount == 0) {
                    PhysicsShapeData changedId =
                            findLinkedBySpatialBlockId(
                                    candidates, linked.spatialBlockId);
                    if (changedId != null) {
                        throw linkedMutation(bodyEntityId, linked.physicsShapeId,
                                "changing physicsShapeId is not supported");
                    }
                    throw linkedMutation(bodyEntityId, linked.physicsShapeId,
                            "deleting a linked shape is not supported");
                }
                if (candidateCount > 1) {
                    throw linkedMutation(bodyEntityId, linked.physicsShapeId,
                            "duplicating a linked shape is not supported");
                }
                PhysicsShapeData candidate =
                        findByPhysicsShapeId(candidates, linked.physicsShapeId);
                if (!isLinked(candidate)) {
                    throw linkedMutation(bodyEntityId, linked.physicsShapeId,
                            "converting linked to manual is not supported");
                }
                if (candidate.spatialBlockId != linked.spatialBlockId) {
                    throw linkedMutation(bodyEntityId, linked.physicsShapeId,
                            "changing spatialBlockId is not supported");
                }
                if (candidate.geometry != null) {
                    throw linkedMutation(bodyEntityId, linked.physicsShapeId,
                            "writing linked geometry is not supported");
                }
            }
        }

        if (candidates != null) {
            for (int i = 0; i < candidates.size; i++) {
                PhysicsShapeData candidate = candidates.get(i);
                if (!isLinked(candidate)) continue;
                if (candidate.geometry != null) {
                    throw linkedMutation(bodyEntityId, candidate.physicsShapeId,
                            "writing linked geometry is not supported");
                }
                PhysicsShapeData existing = findByPhysicsShapeId(
                        currentShapes, candidate.physicsShapeId);
                if (existing == null) {
                    PhysicsShapeData sameBlock = findLinkedBySpatialBlockId(
                            currentShapes, candidate.spatialBlockId);
                    String operation = sameBlock != null
                            ? "duplicating or changing physicsShapeId of a linked shape is not supported"
                            : "adding a linked shape is not supported";
                    throw linkedMutation(
                            bodyEntityId, candidate.physicsShapeId, operation);
                }
                if (!isLinked(existing)) {
                    throw linkedMutation(bodyEntityId, candidate.physicsShapeId,
                            "converting manual to linked is not supported");
                }
            }
            validateLinkedOrder(bodyEntityId, currentShapes, candidates);
        }
    }

    private static void validateLinkedOrder(
            int bodyEntityId,
            Array<PhysicsShapeData> current,
            Array<PhysicsShapeData> candidates) {
        int candidateIndex = 0;
        for (int i = 0; current != null && i < current.size; i++) {
            PhysicsShapeData shape = current.get(i);
            if (!isLinked(shape)) continue;
            while (candidateIndex < candidates.size
                    && !isLinked(candidates.get(candidateIndex))) {
                candidateIndex++;
            }
            if (candidateIndex >= candidates.size
                    || candidates.get(candidateIndex).physicsShapeId
                    != shape.physicsShapeId) {
                throw linkedMutation(bodyEntityId, shape.physicsShapeId,
                        "reordering linked shapes is not supported");
            }
            candidateIndex++;
        }
    }

    private static boolean isLinked(PhysicsShapeData shape) {
        return shape != null && shape.spatialBlockId > 0;
    }

    private static PhysicsShapeData findByPhysicsShapeId(
            Array<PhysicsShapeData> shapes, int physicsShapeId) {
        if (shapes == null) return null;
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape != null && shape.physicsShapeId == physicsShapeId) {
                return shape;
            }
        }
        return null;
    }

    private static PhysicsShapeData findLinkedBySpatialBlockId(
            Array<PhysicsShapeData> shapes, int spatialBlockId) {
        if (shapes == null) return null;
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (isLinked(shape) && shape.spatialBlockId == spatialBlockId) {
                return shape;
            }
        }
        return null;
    }

    private static int countByPhysicsShapeId(
            Array<PhysicsShapeData> shapes, int physicsShapeId) {
        if (shapes == null) return 0;
        int count = 0;
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape != null && shape.physicsShapeId == physicsShapeId) count++;
        }
        return count;
    }

    private static IllegalArgumentException linkedMutation(
            int bodyEntityId, int physicsShapeId, String operation) {
        return new IllegalArgumentException(
                "Body entity " + bodyEntityId + ", physicsShapeId "
                        + physicsShapeId + ": " + operation + ".");
    }

    static PhysicsShapeData deepCopyWithFreshId(
            games.pixscape.runtime.service.PhysicsService physicsService,
            PhysicsShapeData source) {
        if (source == null) return null;
        PhysicsShapeData copy = source.copy();
        copy.physicsShapeId = physicsService.allocateNewPhysicsShapeId();
        return copy;
    }

    static void focusAndSelect(PhysicsSelectionService selection, int bodyEid, int physicsShapeId) {
        if (selection == null) return;

        selection.focusBody(bodyEid);
        if (physicsShapeId > 0L) {
            selection.setSelectedShape(bodyEid, physicsShapeId);
            EventFlow.i().publish(new EventFlow.FixtureSelectionChanged(bodyEid, physicsShapeId, EventFlow.tag(selection)));
        } else {
            selection.clearSelectionOnly();
            EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(EventFlow.tag(selection)));
        }
    }

    static void restoreSelection(World world,
                                 HistoryIdRegistry historyIds,
                                 PhysicsSelectionService selection,
                                 long previousFocusedBodyHistoryId,
                                 int previousSelectedFixtureId) {
        if (selection == null) return;

        if (previousFocusedBodyHistoryId <= 0L) {
            selection.clear();
            return;
        }

        int focusedBodyEid = resolveBodyEntityId(world, historyIds, previousFocusedBodyHistoryId);
        if (focusedBodyEid < 0) {
            selection.clear();
            return;
        }

        selection.focusBody(focusedBodyEid);
        if (previousSelectedFixtureId <= 0L) {
            selection.clearSelectionOnly();
            return;
        }

        PhysicsShapesComponent fixtures = getFixtures(world, focusedBodyEid, false);
        if (indexOfFixture(fixtures, previousSelectedFixtureId) >= 0) {
            selection.setSelectedShape(focusedBodyEid, previousSelectedFixtureId);
        } else {
            selection.clearSelectionOnly();
        }
    }

    public static void markDirty(World world, int entityId) {
        if (world == null || entityId < 0) return;
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.physics(entityId, PhysicsDirtyBits.FIXTURE);
        }
    }

    public static void publishStructureChanged(int entityId, Object source) {
        if (entityId < 0) return;
        EventFlow.i().publish(new EventFlow.PhysicsBodyStructureChanged(entityId, EventFlow.tag(source)));
    }
}
