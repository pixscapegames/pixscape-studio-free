package games.pixscape.studio.scene;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SceneContextEventIsolationTest {
    @Test
    public void inactiveContextIgnoresActiveContextSelectionEvents() {
        EventFlow.i().discardPending();
        StudioEditingModeService modes = new StudioEditingModeService();
        SceneEditorContext active = initialized("scene/a", modes);
        SceneEditorContext inactive = initialized("scene/b", modes);
        active.physicsSelectionService().focusBody(11);
        inactive.physicsSelectionService().focusBody(22);
        active.attached();

        EventFlow.i().publish(new EventFlow.TiledMapEditingTargetChanged(11, 99));
        EventFlow.i().flush();

        assertEquals(11, active.physicsSelectionService().getFocusedBodyEid());
        assertEquals(22, inactive.physicsSelectionService().getFocusedBodyEid());
    }

    @Test
    public void submodeIsIndependentAcrossSceneContexts() {
        StudioEditingModeService modes = new StudioEditingModeService();
        SceneEditorContext first = initialized("scene/a", modes);
        SceneEditorContext second = initialized("scene/b", modes);
        first.rememberSceneSubmode(StudioEditingMode.PHYSICS);
        second.rememberSceneSubmode(StudioEditingMode.TILED);

        assertEquals(StudioEditingMode.PHYSICS, first.sceneSubmode());
        assertEquals(StudioEditingMode.TILED, second.sceneSubmode());
    }

    private static SceneEditorContext initialized(String identity,
                                                  StudioEditingModeService modes) {
        SceneEditorContext context = new SceneEditorContext(identity, modes);
        context.initializeWorld(
                new World(new WorldConfiguration()), new TiledAllocatorService(), null);
        return context;
    }
}
