package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SpatialActorLayerCreationTest {

    @Test
    public void spatialCreationUsesOrdinaryLayerIdentityAndSurvivesUndoRedo() {
        Fixture fixture = new Fixture();
        CreateLayerCommand command = fixture.spatialCommand("Actors", null);

        fixture.history.execute(command);

        assertFalse(command.wasRejected());
        assertEquals(1, fixture.service.count());
        assertSpatial(fixture.layer(0));
        assertEquals("Spatial", LayerService.typeDisplayName(LayerComponent.TYPE_CLASSIC, true));
        assertEquals("(Spatial)", LayerService.typeSuffixLabel(LayerComponent.TYPE_CLASSIC, true));

        fixture.history.undo();
        assertEquals(0, fixture.service.count());

        fixture.history.redo();
        assertEquals(1, fixture.service.count());
        assertSpatial(fixture.layer(0));
    }

    @Test
    public void staleSpatialCreationIsRejectedWithoutMutationOrHistoryEntry() {
        Fixture fixture = new Fixture();
        fixture.history.execute(fixture.spatialCommand("First", null));
        int cursorBefore = fixture.history.getCursor();
        AtomicInteger selected = new AtomicInteger(-1);
        CreateLayerCommand duplicate = fixture.spatialCommand("Duplicate", selected::set);

        fixture.history.execute(duplicate);

        assertTrue(duplicate.wasRejected());
        assertEquals(1, fixture.service.count());
        assertEquals(cursorBefore, fixture.history.getCursor());
        assertEquals(-1, selected.get());
        assertEquals("Create Layer", fixture.history.peekUndoLabel());
    }

    @Test
    public void directStaleCommandRedoIsAlsoRejected() {
        Fixture fixture = new Fixture();
        fixture.history.execute(fixture.spatialCommand("First", null));
        CreateLayerCommand duplicate = fixture.spatialCommand("Duplicate", null);

        duplicate.redo();

        assertTrue(duplicate.wasRejected());
        assertEquals(1, fixture.service.count());
    }

    @Test
    public void deleteUndoRestoresSpatialIdentity() {
        Fixture fixture = new Fixture();
        fixture.history.execute(fixture.spatialCommand("Spatial", null));
        int spatialEntity = fixture.service.getLayerEntity(0);

        fixture.history.execute(new DeleteLayerCommand(fixture.service, spatialEntity, null));
        assertEquals(0, fixture.service.count());

        fixture.history.undo();
        assertEquals(1, fixture.service.count());
        assertSpatial(fixture.layer(0));
    }

    @Test
    public void ordinaryLayerRemainsAvailableBesideSpatial() {
        Fixture fixture = new Fixture();
        fixture.history.execute(fixture.spatialCommand("Spatial", null));

        fixture.history.execute(new CreateLayerCommand(
                fixture.service, fixture.service.count(), "Ordinary",
                LayerComponent.TYPE_CLASSIC, false, null));

        assertEquals(2, fixture.service.count());
        LayerComponent ordinary = fixture.layer(1);
        assertEquals(LayerComponent.TYPE_CLASSIC, ordinary.type);
        assertFalse(ordinary.spatialEnabled);
        assertEquals("Classic", LayerService.typeDisplayName(ordinary.type, ordinary.spatialEnabled));
    }

    @Test
    public void malformedDuplicateSpatialLayersArePresentedButBlockFurtherCreation() {
        Fixture fixture = new Fixture();
        fixture.addMalformedSpatial(0);
        fixture.addMalformedSpatial(1);
        fixture.world.process();
        fixture.service.rebuildFromWorld();

        assertTrue(fixture.service.hasSpatialActorLayer());
        assertEquals(2, fixture.service.getLayerUIs().size);
        assertTrue(fixture.service.getLayerUIs().get(0).spatialEnabled());
        assertTrue(fixture.service.getLayerUIs().get(1).spatialEnabled());
        assertEquals("Spatial", LayerService.typeDisplayName(
                fixture.service.getLayerUIs().get(1).type(),
                fixture.service.getLayerUIs().get(1).spatialEnabled()));

        CreateLayerCommand duplicate = fixture.spatialCommand("Third", null);
        fixture.history.execute(duplicate);
        assertTrue(duplicate.wasRejected());
        assertEquals(2, fixture.service.count());
        assertFalse(fixture.history.canUndo());
    }

    @Test
    public void tiledSpatialFlagDoesNotClaimTheSingleActorLayerSlot() {
        assertFalse(LayerService.isSpatialActorLayer(LayerComponent.TYPE_TILED, true));
        assertTrue(LayerService.isSpatialActorLayer(LayerComponent.TYPE_CLASSIC, true));
    }

    private static void assertSpatial(LayerComponent layer) {
        assertNotNull(layer);
        assertEquals(LayerComponent.TYPE_CLASSIC, layer.type);
        assertTrue(layer.spatialEnabled);
    }

    private static final class Fixture {
        private final World world = new World(new WorldConfiguration());
        private final HistoryManager history = new HistoryManager(8);
        private final LayerService service;

        private Fixture() {
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, new SceneMetaRuntime());
            service = new LayerService(world, null, history.historyIds(), identities);
        }

        private CreateLayerCommand spatialCommand(String name, java.util.function.IntConsumer selected) {
            return new CreateLayerCommand(service, service.count(), name,
                    LayerComponent.TYPE_CLASSIC, true, selected);
        }

        private LayerComponent layer(int index) {
            return world.getMapper(LayerComponent.class).getSafe(service.getLayerEntity(index), null);
        }

        private void addMalformedSpatial(int index) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = index;
            layer.type = LayerComponent.TYPE_CLASSIC;
            layer.spatialEnabled = true;
            world.getMapper(LayerMetaComponent.class).create(entity).name = "Spatial " + index;
        }
    }
}
