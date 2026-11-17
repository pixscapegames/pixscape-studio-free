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
                Assert.assertFalse(world.getMapper(SpatialRepresentationComponent.class).has(actor));
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
            Assert.assertFalse(world.getMapper(SpatialRepresentationComponent.class).has(actor));
        } finally { world.dispose(); }
    }
    @Test
    public void footprintEditUndoRedoLeavesCollisionAndCompiledGenerationUnchanged() {
        World world = new World(new WorldConfiguration());
        try {
            int actor = world.create();
            HistoryManager history = new HistoryManager(16);
            PhysicsService physics = new PhysicsService(world, null, new SceneMeta());
            physics.ensurePhysics(actor);
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(actor);
            var shape = PhysicsService.createDefaultShape(physics.allocateNewPhysicsShapeId());
            shape.geometry.halfWidth = 3f;
            shape.density = 2f;
            shape.friction = .6f;
            shape.restitution = .25f;
            shape.sensor = true;
            shape.maskBits = 7;
            shapes.shapes.add(shape);
            FixtureCommandSupport.prepareAndPublish(world, actor, shapes.shapes);
            var compiled = world.getMapper(games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent.class).get(actor);
            int generation = compiled.generation;
            var collisionBefore = shapes.shapes.first().copy();
            history.execute(new ToggleSpatialActorCommand(world, history.historyIds(), actor, true, true, new SpatialFootprintComponent()));
            SpatialFootprintComponent footprint = world.getMapper(SpatialFootprintComponent.class).get(actor);
            var before = EditSpatialFootprintCommand.Snapshot.capture(footprint);
            history.execute(new EditSpatialFootprintCommand(world, history.historyIds(), actor, before,
                    before.withRadius(32).withOffsetX(85).withOffsetY(-12)));
            for (int i = 0; i < 3; i++) {
                Assert.assertEquals(32, footprint.radiusPx, 0);
                Assert.assertEquals(85, footprint.localOffsetXPx, 0);
                Assert.assertEquals(generation, compiled.generation);
                Assert.assertTrue(collisionBefore.contentEquals(shapes.shapes.first()));
                history.undo();
                Assert.assertEquals(before.radius(), footprint.radiusPx, 0);
                history.redo();
            }
            history.execute(new ToggleSpatialActorCommand(world, history.historyIds(), actor, false, true, null));
            Assert.assertFalse(world.getMapper(SpatialFootprintComponent.class).has(actor));
            Assert.assertTrue(collisionBefore.contentEquals(shapes.shapes.first()));
            Assert.assertEquals(generation, compiled.generation);
            history.undo();
            Assert.assertEquals(85, world.getMapper(SpatialFootprintComponent.class).get(actor).localOffsetXPx, 0);
        } finally {
            world.dispose();
        }
    }
}
