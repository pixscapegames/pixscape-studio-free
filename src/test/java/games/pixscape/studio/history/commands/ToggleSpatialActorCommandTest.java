package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class ToggleSpatialActorCommandTest {
    @Test public void activationUndoRedoNeverCreatesPhysics() {
        World world = new World(new WorldConfiguration());
        try {
            int actor = world.create();
            HistoryManager history = new HistoryManager(16);
            SpatialFootprintComponent initial = new SpatialFootprintComponent();
            initial.radiusPx = 25;
            initial.localOffsetXPx = 12;
            initial.altitude = 4;
            ToggleSpatialActorCommand command = new ToggleSpatialActorCommand(world, history.historyIds(),
                    actor, true, true, initial);
            history.execute(command);
            for (int i = 0; i < 4; i++) {
                Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(actor));
                Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(actor));
                Assert.assertEquals(25, world.getMapper(SpatialFootprintComponent.class).get(actor).radiusPx, 0);
                Assert.assertNotNull(world.getMapper(SpatialRepresentationComponent.class).get(actor).rectangles.staticRectangle);
                history.undo();
                Assert.assertFalse(world.getMapper(SpatialFootprintComponent.class).has(actor));
                history.redo();
            }
        } finally { world.dispose(); }
    }

    @Test public void activationAndPhysicsRemovalPreserveIndependentSettings() {
        World world = new World(new WorldConfiguration());
        try {
            int actor = world.create();
            HistoryManager history = new HistoryManager(16);
            PhysicsService physics = new PhysicsService(world, null, new SceneMeta());
            physics.ensurePhysics(actor);
            PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(actor);
            body.gravityScale = 2;
            body.fixedRotation = false;
            Object shapes = world.getMapper(PhysicsShapesComponent.class).get(actor).shapes;
            history.execute(new ToggleSpatialActorCommand(world, history.historyIds(),
                    actor, true, true, new SpatialFootprintComponent()));
            Assert.assertEquals(2, body.gravityScale, 0);
            Assert.assertFalse(body.fixedRotation);
            Assert.assertSame(shapes, world.getMapper(PhysicsShapesComponent.class).get(actor).shapes);
            physics.removePhysics(actor);
            Assert.assertNotNull(world.getMapper(SpatialFootprintComponent.class).get(actor));
            Assert.assertNotNull(world.getMapper(SpatialRepresentationComponent.class).get(actor));
        } finally { world.dispose(); }
    }
    @Test
    public void footprintEditUndoRedoPublishesItsLinkedCollision() {
        World world = new World(new WorldConfiguration());
        try {
            int actor = world.create();
            HistoryManager history = new HistoryManager(16);
            SpatialFootprintComponent footprint = world.getMapper(SpatialFootprintComponent.class).create(actor);
            footprint.radiusPx = 20;
            PhysicsService physics = new PhysicsService(world, null, new SceneMeta());
            physics.ensurePhysics(actor);
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(actor);
            shapes.shapes.first().useSpatialFootprint = true;
            shapes.shapes.first().geometry = null;
            var compiled = world.getMapper(games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent.class).get(actor);
            PhysicsService.publishPreparedCandidate(shapes, compiled,
                    PhysicsService.prepareBodyCandidate(world, actor, shapes.shapes, 40));
            var before = EditSpatialFootprintCommand.Snapshot.capture(footprint);
            history.execute(new EditSpatialFootprintCommand(world, history.historyIds(), actor,
                    before, before.withRadius(60).withAltitude(9), 40));
            for (int i = 0; i < 4; i++) {
                Assert.assertEquals(60, footprint.radiusPx, 0);
                Assert.assertEquals(1.5f, compiled.fixtures.first().radius, 0);
                Assert.assertEquals(9, footprint.altitude, 0);
                history.undo();
                Assert.assertEquals(20, footprint.radiusPx, 0);
                Assert.assertEquals(.5f, compiled.fixtures.first().radius, 0);
                Assert.assertEquals(0, footprint.altitude, 0);
                history.redo();
            }
        } finally {
            world.dispose();
        }
    }

}
