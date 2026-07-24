package games.pixscape.studio.service.physics;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsBodyCompiler;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.PhysicsCompiledFixtureCachePublisher;
import games.pixscape.studio.event.EventFlow;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSelectionReconcilerTest {
    @Test
    public void switchingWorldsWithCollidingIdsClearsSelection() {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        Assert.assertEquals(first.body, second.body);
        Assert.assertEquals(first.shape.physicsShapeId, second.shape.physicsShapeId);
        PhysicsSelectionService selection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(selection);
        reconciler.bindWorld(first.world);
        reconciler.bindSceneContext("scene-a");
        selection.setSelectedShape(first.body, first.shape.physicsShapeId);

        reconciler.bindWorld(second.world);

        assertCleared(selection);
    }

    @Test
    public void bindingNullClearsSelection() {
        Fixture fixture = new Fixture();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(selection);
        reconciler.bindWorld(fixture.world);
        selection.setSelectedShape(fixture.body, fixture.shape.physicsShapeId);

        reconciler.bindWorld(null);

        assertCleared(selection);
    }

    @Test
    public void changingSceneContextInSameWorldClearsSelection() {
        Fixture fixture = new Fixture();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(selection);
        reconciler.bindWorld(fixture.world);
        reconciler.bindSceneContext("scene-a");
        selection.setSelectedShape(fixture.body, fixture.shape.physicsShapeId);

        reconciler.bindSceneContext("scene-b");

        assertCleared(selection);
    }

    @Test
    public void recompilationPreservesSourceSelectionAndResetsOnlyMissingPart() {
        Fixture fixture = new Fixture();
        publishParts(fixture, 0, 1);
        PhysicsSelectionService selection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(selection);
        reconciler.bindWorld(fixture.world);
        reconciler.bindSceneContext("scene-a");
        selection.setSelectedShape(fixture.body, fixture.shape.physicsShapeId, 1);

        publishParts(fixture, 0);
        reconciler.reconcile();

        Assert.assertEquals(fixture.body, selection.getFocusedBodyEid());
        Assert.assertEquals(fixture.shape.physicsShapeId,
                selection.getSelectedPhysicsShapeId());
        Assert.assertEquals(PhysicsSelectionService.NO_PART,
                selection.getSelectedPartIndex());
        Assert.assertEquals(PhysicsSelectionService.NO_PART,
                selection.getHoveredPartIndex());
    }

    @Test
    public void removedSelectedAndHoveredJointAreReconciled() {
        Fixture fixture = new Fixture();
        int joint = fixture.world.create();
        fixture.world.getMapper(PhysicsJointComponent.class).create(joint);
        PhysicsSelectionService selection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(selection);
        reconciler.bindWorld(fixture.world);
        selection.setSelectedJoint(fixture.body, joint);

        fixture.world.delete(joint);
        fixture.world.process();
        reconciler.reconcile();

        Assert.assertEquals(PhysicsSelectionService.NO_JOINT,
                selection.getSelectedJointEid());
        Assert.assertEquals(PhysicsSelectionService.NO_JOINT,
                selection.getHoveredJointEid());
        Assert.assertEquals(fixture.body, selection.getFocusedBodyEid());
    }

    @Test
    public void removedHoveredSourceIsReconciledAndPublishesChange() {
        Fixture fixture = new Fixture();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(selection);
        reconciler.bindWorld(fixture.world);
        selection.setHoveredShape(fixture.body, fixture.shape.physicsShapeId);
        EventFlow.i().flush();
        int[] reconciledEvents = {0};
        EventFlow.Listener<EventFlow.PhysicsSelectionReconciled> listener =
                event -> reconciledEvents[0]++;
        EventFlow.i().subscribe(EventFlow.PhysicsSelectionReconciled.class, listener);

        try {
            fixture.shapes.shapes.clear();
            reconciler.reconcile();
            EventFlow.i().flush();

            Assert.assertFalse(selection.hasHoveredShape());
            Assert.assertEquals(1, reconciledEvents[0]);
        } finally {
            EventFlow.i().unsubscribe(
                    EventFlow.PhysicsSelectionReconciled.class, listener);
        }
    }

    @Test
    public void removedSelectedSourceClearsSelectionAndPublishesFixtureEvent() {
        Fixture fixture = new Fixture();
        PhysicsSelectionService selection = new PhysicsSelectionService();
        PhysicsSelectionReconciler reconciler =
                new PhysicsSelectionReconciler(selection);
        reconciler.bindWorld(fixture.world);
        selection.setSelectedShape(fixture.body, fixture.shape.physicsShapeId);
        EventFlow.i().flush();
        int[] clearedEvents = {0};
        EventFlow.Listener<EventFlow.FixtureSelectionCleared> listener =
                event -> clearedEvents[0]++;
        EventFlow.i().subscribe(EventFlow.FixtureSelectionCleared.class, listener);

        try {
            fixture.shapes.shapes.clear();
            reconciler.reconcile();
            EventFlow.i().flush();

            Assert.assertFalse(selection.hasSelectedShape());
            Assert.assertFalse(selection.hasHoveredShape());
            Assert.assertEquals(fixture.body, selection.getFocusedBodyEid());
            Assert.assertEquals(1, clearedEvents[0]);
        } finally {
            EventFlow.i().unsubscribe(
                    EventFlow.FixtureSelectionCleared.class, listener);
        }
    }

    private static void publishParts(Fixture fixture, int... partIndices) {
        Array<CompiledFixtureData> candidate =
                new Array<>(true, partIndices.length, CompiledFixtureData.class);
        for (int partIndex : partIndices) {
            CompiledFixtureData part = new CompiledFixtureData();
            part.physicsShapeId = fixture.shape.physicsShapeId;
            part.partIndex = partIndex;
            part.shapeType = CompiledFixtureData.SHAPE_CIRCLE;
            part.radius = 1f;
            candidate.add(part);
        }
        PhysicsCompiledFixturesComponent compiled =
                fixture.world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .getSafe(fixture.body, null);
        if (compiled == null) {
            compiled = fixture.world.getMapper(PhysicsCompiledFixturesComponent.class)
                    .create(fixture.body);
        }
        new PhysicsCompiledFixtureCachePublisher().publish(
                compiled, new PhysicsBodyCompiler().prepare(candidate));
    }

    private static void assertCleared(PhysicsSelectionService selection) {
        Assert.assertEquals(PhysicsSelectionService.NO_BODY,
                selection.getFocusedBodyEid());
        Assert.assertEquals(PhysicsSelectionService.NO_SHAPE,
                selection.getSelectedPhysicsShapeId());
        Assert.assertEquals(PhysicsSelectionService.NO_JOINT,
                selection.getSelectedJointEid());
        Assert.assertEquals(PhysicsSelectionService.NO_SHAPE,
                selection.getHoveredPhysicsShapeId());
        Assert.assertEquals(PhysicsSelectionService.NO_JOINT,
                selection.getHoveredJointEid());
    }

    private static final class Fixture {
        final World world = new World(new WorldConfiguration());
        final int body = world.create();
        final PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).create(body);
        final PhysicsShapeData shape = new PhysicsShapeData();

        Fixture() {
            world.getMapper(PhysicsBodyComponent.class).create(body);
            shape.physicsShapeId = 31;
            shape.shapeType = PhysicsShapeData.SHAPE_CIRCLE;
            shape.radius = 1f;
            shapes.add(shape);
        }
    }
}
