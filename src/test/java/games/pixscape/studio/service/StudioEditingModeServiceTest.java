package games.pixscape.studio.service;

import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StudioEditingModeServiceTest {
    @Test
    public void publishesOnlyRealChanges() {
        EventFlow.i().flush();
        StudioEditingModeService service = new StudioEditingModeService();
        int tag = EventFlow.tag(service);
        List<StudioEditingMode> modes = new ArrayList<>();
        EventFlow.Listener<EventFlow.StudioEditingModeChanged> listener = event -> {
            if (event.sourceTag() == tag) modes.add(event.mode());
        };
        EventFlow.i().subscribe(EventFlow.StudioEditingModeChanged.class, listener);
        try {
            Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
            service.setModeActive(StudioEditingMode.TILED, true, tag);
            service.setModeActive(StudioEditingMode.TILED, true, tag);
            EventFlow.i().flush();

            Assert.assertEquals(List.of(StudioEditingMode.TILED), modes);
        } finally {
            EventFlow.i().unsubscribe(EventFlow.StudioEditingModeChanged.class, listener);
        }
    }

    @Test
    public void resolvesPriorityAndFallsBackToRemainingContext() {
        StudioEditingModeService service = new StudioEditingModeService();
        service.setModeActive(StudioEditingMode.TILED, true, 1);
        service.setModeActive(StudioEditingMode.PHYSICS, true, 1);
        service.setModeActive(StudioEditingMode.SPATIAL, true, 1);
        service.setModeActive(StudioEditingMode.LIGHTS, true, 1);
        Assert.assertEquals(StudioEditingMode.LIGHTS, service.getCurrentMode());

        service.setModeActive(StudioEditingMode.LIGHTS, false, 1);
        Assert.assertEquals(StudioEditingMode.SPATIAL, service.getCurrentMode());
        service.setModeActive(StudioEditingMode.SPATIAL, false, 1);
        Assert.assertEquals(StudioEditingMode.PHYSICS, service.getCurrentMode());
        service.setModeActive(StudioEditingMode.PHYSICS, false, 1);
        Assert.assertEquals(StudioEditingMode.TILED, service.getCurrentMode());
        service.setModeActive(StudioEditingMode.TILED, false, 1);
        Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
    }

    @Test
    public void resetClearsAStaleSceneContext() {
        StudioEditingModeService service = new StudioEditingModeService();
        service.setMode(StudioEditingMode.PHYSICS, 1);
        service.reset(2);
        Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
    }

    @Test
    public void physicsAndSpatialAuthoritiesDriveEntryAndExit() {
        StudioEditingModeService service = new StudioEditingModeService();
        PhysicsSelectionService physics = new PhysicsSelectionService(service);
        SpatialBlockSelectionService spatial = new SpatialBlockSelectionService(service);

        physics.focusBody(7);
        Assert.assertEquals(StudioEditingMode.PHYSICS, service.getCurrentMode());
        spatial.enterLayer(12);
        Assert.assertEquals(StudioEditingMode.SPATIAL, service.getCurrentMode());
        spatial.clear();
        Assert.assertEquals(StudioEditingMode.PHYSICS, service.getCurrentMode());
        physics.clear();
        Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
    }
}
