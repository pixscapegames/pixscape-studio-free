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
        service.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        EventFlow.i().flush();
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
        service.activateSceneDocument(StudioEditingMode.NORMAL, 0);
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
        service.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        service.setMode(StudioEditingMode.PHYSICS, 1);
        service.reset(2);
        Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
    }

    @Test
    public void hudIsExclusiveAndExitsToNormal() {
        StudioEditingModeService service = new StudioEditingModeService();
        Assert.assertFalse(service.allowsWorldEditingActions());
        service.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        Assert.assertTrue(service.allowsWorldEditingActions());
        service.setMode(StudioEditingMode.TILED, 1);
        Assert.assertTrue(service.allowsWorldEditingActions());
        service.activateHudDocument(2);
        Assert.assertEquals(StudioEditingMode.HUD, service.getCurrentMode());
        Assert.assertFalse(service.allowsWorldEditingActions());

        service.activateSceneDocument(StudioEditingMode.NORMAL, 3);
        Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
        Assert.assertTrue(service.allowsWorldEditingActions());

        service.setMode(StudioEditingMode.PHYSICS, 4);
        Assert.assertEquals(StudioEditingMode.PHYSICS, service.getCurrentMode());
        Assert.assertTrue(service.allowsWorldEditingActions());
        service.setMode(StudioEditingMode.SPATIAL, 5);
        Assert.assertTrue(service.allowsWorldEditingActions());
        service.setMode(StudioEditingMode.LIGHTS, 6);
        Assert.assertTrue(service.allowsWorldEditingActions());
    }

    @Test
    public void genericModeApiCannotClaimHudDocumentAuthority() {
        StudioEditingModeService service = new StudioEditingModeService();
        Assert.assertThrows(IllegalArgumentException.class,
                () -> service.setMode(StudioEditingMode.HUD, 1));
        Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
    }

    @Test
    public void hudDocumentProjectionRejectsSceneSubmodeLeaksUntilSceneActivation() {
        StudioEditingModeService service = new StudioEditingModeService();
        service.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        service.setMode(StudioEditingMode.PHYSICS, 1);
        service.activateHudDocument(2);

        service.setModeActive(StudioEditingMode.TILED, true, 3);
        service.reset(4);
        Assert.assertEquals(StudioEditingMode.HUD, service.getCurrentMode());
        Assert.assertFalse(service.allowsWorldEditingActions());

        service.activateSceneDocument(StudioEditingMode.PHYSICS, 5);
        Assert.assertEquals(StudioEditingMode.PHYSICS, service.getCurrentMode());
        Assert.assertTrue(service.allowsWorldEditingActions());
    }

    @Test
    public void activeSceneSubmodeSinkTracksEffectiveSceneModeButNotHudProjection() {
        StudioEditingModeService service = new StudioEditingModeService();
        List<StudioEditingMode> projected = new ArrayList<>();
        service.setActiveSceneSubmodeSink(projected::add);
        service.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        projected.clear();

        service.setMode(StudioEditingMode.TILED, 1);
        service.setMode(StudioEditingMode.TILED, 2);
        service.activateHudDocument(3);

        Assert.assertEquals(List.of(StudioEditingMode.TILED, StudioEditingMode.TILED), projected);
        Assert.assertEquals(StudioEditingMode.HUD, service.getCurrentMode());
    }

    @Test
    public void physicsAndSpatialAuthoritiesDriveEntryAndExit() {
        StudioEditingModeService service = new StudioEditingModeService();
        service.activateSceneDocument(StudioEditingMode.NORMAL, 0);
        PhysicsSelectionService physics = new PhysicsSelectionService(service);
        SpatialBlockSelectionService spatial = new SpatialBlockSelectionService(service);

        physics.focusBody(7);
        Assert.assertEquals(StudioEditingMode.PHYSICS, service.getCurrentMode());
        spatial.enterMap(12);
        Assert.assertEquals(StudioEditingMode.SPATIAL, service.getCurrentMode());
        spatial.clear();
        Assert.assertEquals(StudioEditingMode.PHYSICS, service.getCurrentMode());
        physics.clear();
        Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
    }

    @Test
    public void noneIsNeutralButDoesNotAuthorizeSceneEditing() {
        EventFlow.i().flush();
        StudioEditingModeService service = new StudioEditingModeService();
        List<StudioEditingMode> events = new ArrayList<>();
        EventFlow.Listener<EventFlow.StudioEditingModeChanged> listener =
                event -> events.add(event.mode());
        EventFlow.i().subscribe(EventFlow.StudioEditingModeChanged.class, listener);
        try {
            Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
            Assert.assertFalse(service.hasActiveSceneDocument());
            Assert.assertFalse(service.hasActiveHudDocument());
            Assert.assertFalse(service.allowsWorldEditingActions());

            service.activateSceneDocument(StudioEditingMode.NORMAL, 1);
            Assert.assertTrue(service.hasActiveSceneDocument());
            Assert.assertTrue(service.allowsWorldEditingActions());

            service.deactivateDocument(2);
            Assert.assertEquals(StudioEditingMode.NORMAL, service.getCurrentMode());
            Assert.assertFalse(service.hasActiveSceneDocument());
            Assert.assertFalse(service.hasActiveHudDocument());
            Assert.assertFalse(service.allowsWorldEditingActions());
            EventFlow.i().flush();
            Assert.assertEquals(List.of(StudioEditingMode.NORMAL, StudioEditingMode.NORMAL), events);
        } finally {
            EventFlow.i().unsubscribe(EventFlow.StudioEditingModeChanged.class, listener);
        }
    }

    @Test
    public void hudTestEntryIsTransactionalAndSceneActivationForcesCleanup() {
        StudioEditingModeService service = new StudioEditingModeService();
        final boolean[] active = {false};
        final boolean[] allowEntry = {false};
        final int[] exits = {0};
        service.bindHudTestModeController(new StudioEditingModeService.HudTestModeController() {
            @Override public boolean canEnter() { return true; }
            @Override public boolean enter() {
                if (!allowEntry[0]) return false;
                active[0] = true;
                return true;
            }
            @Override public void exit() { active[0] = false; exits[0]++; }
            @Override public boolean isActive() { return active[0]; }
        });
        service.activateHudDocument(1);

        Assert.assertFalse(service.setHudTestMode(true));
        Assert.assertFalse(service.isHudTestMode());
        allowEntry[0] = true;
        Assert.assertTrue(service.setHudTestMode(true));
        Assert.assertTrue(service.isHudTestMode());

        service.activateSceneDocument(StudioEditingMode.NORMAL, 2);
        Assert.assertFalse(service.isHudTestMode());
        Assert.assertEquals(1, exits[0]);
    }
}
