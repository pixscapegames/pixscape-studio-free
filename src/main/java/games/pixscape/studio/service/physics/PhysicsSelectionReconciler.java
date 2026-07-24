package games.pixscape.studio.service.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.studio.event.EventFlow;

import java.util.Objects;

/**
 * Explicit ECS reconciliation boundary for the passive physics selection state.
 */
public final class PhysicsSelectionReconciler {
    private final PhysicsSelectionService selection;

    private World world;
    private Object sceneContext;
    private ComponentMapper<PhysicsBodyComponent> bodies;
    private ComponentMapper<PhysicsShapesComponent> shapes;
    private ComponentMapper<PhysicsCompiledFixturesComponent> compiledFixtures;
    private ComponentMapper<PhysicsJointComponent> joints;

    public PhysicsSelectionReconciler(PhysicsSelectionService selection) {
        if (selection == null) {
            throw new IllegalArgumentException("PhysicsSelectionService is required.");
        }
        this.selection = selection;
    }

    public void bindWorld(World nextWorld) {
        if (nextWorld == world && nextWorld != null) return;
        SelectionSnapshot before = snapshot();
        world = nextWorld;
        sceneContext = null;
        if (world == null) {
            bodies = null;
            shapes = null;
            compiledFixtures = null;
            joints = null;
        } else {
            bodies = world.getMapper(PhysicsBodyComponent.class);
            shapes = world.getMapper(PhysicsShapesComponent.class);
            compiledFixtures = world.getMapper(PhysicsCompiledFixturesComponent.class);
            joints = world.getMapper(PhysicsJointComponent.class);
        }
        selection.clear();
        publishChanges(before);
    }

    public void bindSceneContext(Object nextSceneContext) {
        if (Objects.equals(sceneContext, nextSceneContext)) return;
        SelectionSnapshot before = snapshot();
        sceneContext = nextSceneContext;
        selection.clear();
        publishChanges(before);
    }

    public void clearSceneContext() {
        SelectionSnapshot before = snapshot();
        sceneContext = null;
        selection.clear();
        publishChanges(before);
    }

    public void reconcile() {
        if (world == null) return;
        SelectionSnapshot before = snapshot();

        reconcileFocusedBody();
        if (selection.hasFocusedBody()) {
            reconcileSelectedShape();
            reconcileSelectedJoint();
        }
        reconcileHoveredShape();
        reconcileHoveredJoint();

        publishChanges(before);
    }

    private void reconcileFocusedBody() {
        int bodyEntityId = selection.getFocusedBodyEid();
        if (bodyEntityId == PhysicsSelectionService.NO_BODY) return;
        if (!isActive(bodyEntityId) || bodies.getSafe(bodyEntityId, null) == null) {
            selection.clear();
        }
    }

    private void reconcileSelectedShape() {
        if (!selection.hasSelectedShape()) return;
        int bodyEntityId = selection.getFocusedBodyEid();
        int physicsShapeId = selection.getSelectedPhysicsShapeId();
        if (!containsSource(bodyEntityId, physicsShapeId)) {
            selection.clearSelectedShape();
            if (selection.getHoveredBodyEid() == bodyEntityId
                    && selection.getHoveredPhysicsShapeId() == physicsShapeId) {
                selection.clearHoveredShape();
            }
            return;
        }
        if (!containsCompiledPart(
                bodyEntityId, physicsShapeId, selection.getSelectedPartIndex())) {
            selection.resetSelectedPartIndex();
        }
    }

    private void reconcileHoveredShape() {
        if (!selection.hasHoveredShape()) return;
        int bodyEntityId = selection.getHoveredBodyEid();
        int physicsShapeId = selection.getHoveredPhysicsShapeId();
        if (!isActive(bodyEntityId)
                || bodies.getSafe(bodyEntityId, null) == null
                || !containsSource(bodyEntityId, physicsShapeId)) {
            selection.clearHoveredShape();
            return;
        }
        if (!containsCompiledPart(
                bodyEntityId, physicsShapeId, selection.getHoveredPartIndex())) {
            selection.resetHoveredPartIndex();
        }
    }

    private void reconcileSelectedJoint() {
        if (!selection.hasSelectedJoint()) return;
        int jointEntityId = selection.getSelectedJointEid();
        if (!isActive(jointEntityId) || joints.getSafe(jointEntityId, null) == null) {
            selection.clearSelectedJoint();
        }
    }

    private void reconcileHoveredJoint() {
        if (!selection.hasHoveredJoint()) return;
        int jointEntityId = selection.getHoveredJointEid();
        if (!isActive(jointEntityId) || joints.getSafe(jointEntityId, null) == null) {
            selection.clearHoveredJoint();
        }
    }

    private boolean containsSource(int bodyEntityId, int physicsShapeId) {
        PhysicsShapesComponent sources = shapes.getSafe(bodyEntityId, null);
        if (sources == null || sources.shapes == null) return false;
        for (int i = 0; i < sources.shapes.size; i++) {
            PhysicsShapeData source = sources.shapes.get(i);
            if (source != null && source.physicsShapeId == physicsShapeId) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCompiledPart(
            int bodyEntityId, int physicsShapeId, int partIndex) {
        if (partIndex == PhysicsSelectionService.NO_PART) return true;
        PhysicsCompiledFixturesComponent compiled =
                compiledFixtures.getSafe(bodyEntityId, null);
        if (compiled == null || !compiled.valid || compiled.fixtures == null) {
            return true;
        }
        for (int i = 0; i < compiled.fixtures.size; i++) {
            CompiledFixtureData fixture = compiled.fixtures.get(i);
            if (fixture != null
                    && fixture.physicsShapeId == physicsShapeId
                    && fixture.partIndex == partIndex) {
                return true;
            }
        }
        return false;
    }

    private boolean isActive(int entityId) {
        return entityId >= 0 && world.getEntityManager().isActive(entityId);
    }

    private SelectionSnapshot snapshot() {
        return new SelectionSnapshot(
                selection.getFocusedBodyEid(),
                selection.getHoveredBodyEid(),
                selection.getHoveredPhysicsShapeId(),
                selection.getHoveredPartIndex(),
                selection.getHoveredJointEid(),
                selection.getSelectedPhysicsShapeId(),
                selection.getSelectedPartIndex(),
                selection.getSelectedJointEid());
    }

    private void publishChanges(SelectionSnapshot before) {
        SelectionSnapshot after = snapshot();
        if (before.equals(after)) return;

        int sourceTag = EventFlow.tag(this);
        EventFlow.i().publish(new EventFlow.PhysicsSelectionReconciled(sourceTag));
        if (before.selectedPhysicsShapeId != PhysicsSelectionService.NO_SHAPE
                && after.selectedPhysicsShapeId == PhysicsSelectionService.NO_SHAPE) {
            EventFlow.i().publish(new EventFlow.FixtureSelectionCleared(sourceTag));
        } else if (before.selectedPhysicsShapeId == after.selectedPhysicsShapeId
                && before.selectedPartIndex != after.selectedPartIndex
                && after.selectedPhysicsShapeId != PhysicsSelectionService.NO_SHAPE) {
            EventFlow.i().publish(new EventFlow.FixtureSelectionChanged(
                    after.focusedBodyEid,
                    after.selectedPhysicsShapeId,
                    sourceTag));
        }
    }

    private static final class SelectionSnapshot {
        private final int focusedBodyEid;
        private final int hoveredBodyEid;
        private final int hoveredPhysicsShapeId;
        private final int hoveredPartIndex;
        private final int hoveredJointEid;
        private final int selectedPhysicsShapeId;
        private final int selectedPartIndex;
        private final int selectedJointEid;

        private SelectionSnapshot(
                int focusedBodyEid,
                int hoveredBodyEid,
                int hoveredPhysicsShapeId,
                int hoveredPartIndex,
                int hoveredJointEid,
                int selectedPhysicsShapeId,
                int selectedPartIndex,
                int selectedJointEid) {
            this.focusedBodyEid = focusedBodyEid;
            this.hoveredBodyEid = hoveredBodyEid;
            this.hoveredPhysicsShapeId = hoveredPhysicsShapeId;
            this.hoveredPartIndex = hoveredPartIndex;
            this.hoveredJointEid = hoveredJointEid;
            this.selectedPhysicsShapeId = selectedPhysicsShapeId;
            this.selectedPartIndex = selectedPartIndex;
            this.selectedJointEid = selectedJointEid;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SelectionSnapshot)) return false;
            SelectionSnapshot that = (SelectionSnapshot) other;
            return focusedBodyEid == that.focusedBodyEid
                    && hoveredBodyEid == that.hoveredBodyEid
                    && hoveredPhysicsShapeId == that.hoveredPhysicsShapeId
                    && hoveredPartIndex == that.hoveredPartIndex
                    && hoveredJointEid == that.hoveredJointEid
                    && selectedPhysicsShapeId == that.selectedPhysicsShapeId
                    && selectedPartIndex == that.selectedPartIndex
                    && selectedJointEid == that.selectedJointEid;
        }

        @Override
        public int hashCode() {
            int result = focusedBodyEid;
            result = 31 * result + hoveredBodyEid;
            result = 31 * result + hoveredPhysicsShapeId;
            result = 31 * result + hoveredPartIndex;
            result = 31 * result + hoveredJointEid;
            result = 31 * result + selectedPhysicsShapeId;
            result = 31 * result + selectedPartIndex;
            result = 31 * result + selectedJointEid;
            return result;
        }
    }
}
