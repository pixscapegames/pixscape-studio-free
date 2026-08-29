package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpatialActorLayerToggleTest {

    @Test
    public void existingClassicLayerCanToggleSpatialWithUndoAndRedo() {
        Fixture fixture = new Fixture();
        int layerEntity = fixture.addOrdinaryLayer("Actors");
        LayerComponent layer = fixture.layer(layerEntity);
        assertFalse(layer.spatialEnabled);
        fixture.dirty.clearAll();

        fixture.history.execute(fixture.toggle(layerEntity, true));
        assertTrue(layer.spatialEnabled);
        assertTrue(fixture.dirty.isDirty(layerEntity, DirtyBits.LAYER));
        assertTrue(fixture.dirty.isDirty(layerEntity, DirtyBits.ORDER));

        fixture.dirty.clearAll();
        fixture.history.undo();
        assertFalse(layer.spatialEnabled);
        assertTrue(fixture.dirty.isDirty(layerEntity, DirtyBits.LAYER));
        assertTrue(fixture.dirty.isDirty(layerEntity, DirtyBits.ORDER));

        fixture.history.redo();
        assertTrue(layer.spatialEnabled);
    }

    @Test
    public void disablingLayerLeavesEntitySpatialAndPhysicsStateUntouched() {
        Fixture fixture = new Fixture();
        int layerEntity = fixture.addOrdinaryLayer("Actors");
        fixture.layer(layerEntity).spatialEnabled = true;
        int actor = fixture.addSpatialPhysicsActor(0);

        SpatialHeightComponent height = fixture.world.getMapper(SpatialHeightComponent.class).get(actor);
        PhysicsBodyComponent body = fixture.world.getMapper(PhysicsBodyComponent.class).get(actor);
        PhysicsShapesComponent shapes = fixture.world.getMapper(PhysicsShapesComponent.class).get(actor);
        PhysicsShapeData footprint = shapes.shapes.first();

        fixture.history.execute(fixture.toggle(layerEntity, false));
        assertFalse(fixture.layer(layerEntity).spatialEnabled);
        assertSame(height, fixture.world.getMapper(SpatialHeightComponent.class).get(actor));
        assertEquals(2.5f, height.altitude, 0f);
        assertEquals(7f, height.height, 0f);
        assertSame(body, fixture.world.getMapper(PhysicsBodyComponent.class).get(actor));
        assertSame(shapes, fixture.world.getMapper(PhysicsShapesComponent.class).get(actor));
        assertSame(footprint, shapes.shapes.first());

        fixture.history.undo();
        assertTrue(fixture.layer(layerEntity).spatialEnabled);
        fixture.history.redo();
        assertFalse(fixture.layer(layerEntity).spatialEnabled);
        assertSame(footprint, shapes.shapes.first());
    }

    @Test
    public void secondOrdinarySpatialLayerIsRejectedWithoutPollutingHistory() {
        Fixture fixture = new Fixture();
        int first = fixture.addOrdinaryLayer("First");
        int second = fixture.addOrdinaryLayer("Second");
        fixture.history.execute(fixture.toggle(first, true));
        int cursorBefore = fixture.history.getCursor();
        String undoBefore = fixture.history.peekUndoLabel();

        fixture.history.execute(fixture.toggle(second, true));

        assertTrue(fixture.layer(first).spatialEnabled);
        assertFalse(fixture.layer(second).spatialEnabled);
        assertEquals(cursorBefore, fixture.history.getCursor());
        assertEquals(undoBefore, fixture.history.peekUndoLabel());
    }

    @Test
    public void redoRevalidatesSpatialUniquenessAtomically() {
        Fixture fixture = new Fixture();
        int first = fixture.addOrdinaryLayer("First");
        int second = fixture.addOrdinaryLayer("Second");
        ToggleSpatialActorLayerCommand command = fixture.toggle(second, true);

        fixture.history.execute(command);
        fixture.history.undo();
        fixture.layer(first).spatialEnabled = true;
        int cursorBefore = fixture.history.getCursor();

        fixture.history.redo();

        assertTrue(fixture.layer(first).spatialEnabled);
        assertFalse(fixture.layer(second).spatialEnabled);
        assertEquals(cursorBefore, fixture.history.getCursor());
        assertTrue(fixture.history.canRedo());
    }

    @Test
    public void spatialDepthMapOnOrdinaryLayerDoesNotOccupyActorLayerSlot() {
        Fixture fixture = new Fixture();
        int owningLayer = fixture.addOrdinaryLayer("Maps");
        int actors = fixture.addOrdinaryLayer("Actors");
        int map = fixture.world.create();
        fixture.world.getMapper(EntityIndexComponent.class)
                .create(map).layerIndex = fixture.layer(owningLayer).layerIndex;
        TiledLayerComponent tiled = fixture.world.getMapper(TiledLayerComponent.class)
                .create(map);
        tiled.spatialEnabled = true;

        assertFalse(fixture.layer(owningLayer).spatialEnabled);
        assertFalse(fixture.service.hasOtherSpatialActorLayer(actors));

        fixture.history.execute(fixture.toggle(actors, true));

        assertTrue(fixture.layer(actors).spatialEnabled);
        assertFalse(fixture.layer(owningLayer).spatialEnabled);
        assertTrue(tiled.spatialEnabled);
    }

    @Test
    public void newlyCreatedOrdinaryLayerStartsWithoutSpatialParticipation() {
        Fixture fixture = new Fixture();

        fixture.history.execute(new CreateLayerCommand(
                fixture.service, 0, "Ordinary", null));

        LayerComponent layer = fixture.layer(fixture.service.getLayerEntity(0));
        assertFalse(layer.spatialEnabled);

        fixture.history.undo();
        assertEquals(0, fixture.service.count());

        fixture.history.redo();
        layer = fixture.layer(fixture.service.getLayerEntity(0));
        assertFalse(layer.spatialEnabled);
    }

    private static final class Fixture {
        private final DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        private final World world = new World(new WorldConfiguration().setSystem(dirty));
        private final HistoryManager history = new HistoryManager(8);
        private final LayerService service;

        private Fixture() {
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, new SceneMetaRuntime());
            service = new LayerService(world, null, history.historyIds(), identities);
        }

        private int addOrdinaryLayer(String name) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = service.count();
            layer.spatialEnabled = false;
            world.getMapper(LayerMetaComponent.class).create(entity).name = name;
            world.process();
            return entity;
        }

        private int addSpatialPhysicsActor(int layerIndex) {
            int entity = world.create();
            EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
            index.layerIndex = layerIndex;
            SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).create(entity);
            height.altitude = 2.5f;
            height.height = 7f;
            PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(entity);
            body.gravityScale = 0f;
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entity);
            PhysicsShapeData footprint = new PhysicsShapeData();
            footprint.physicsShapeId = 12;
            footprint.spatialFootprint = true;
            shapes.shapes.add(footprint);
            return entity;
        }

        private ToggleSpatialActorLayerCommand toggle(int layerEntity, boolean enabled) {
            return new ToggleSpatialActorLayerCommand(
                    world, history.historyIds(), service, layerEntity, enabled);
        }

        private LayerComponent layer(int entity) {
            return world.getMapper(LayerComponent.class).getSafe(entity, null);
        }
    }
}
