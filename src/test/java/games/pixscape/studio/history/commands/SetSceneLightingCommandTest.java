package games.pixscape.studio.history.commands;

import com.badlogic.gdx.utils.Json;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Test;

import static org.junit.Assert.*;

public class SetSceneLightingCommandTest {
    private final com.artemis.World world = new com.artemis.World();

    @org.junit.After
    public void dispose() {
        world.dispose();
    }

    @Test
    public void oneCommandPreparesThenPublishesOnlyItsDocumentAndRoundTrips() {
        SceneMeta a = new SceneMeta("A", "a.json"), b = new SceneMeta("B", "b.json");
        assertFalse(a.lightingEnabled);
        assertTrue(new Json().fromJson(SceneMeta.class, "{}").lightingEnabled);
        a.forwardLighting = true;
        a.ambientMulR = .3f;
        int[] prepared = {0}, saved = {0};
        HistoryManager history = new HistoryManager(16);
        history.execute(new SetSceneLightingCommand(world, a, true, value -> {
            assertNotEquals(a.lightingEnabled, value);
            prepared[0]++;
        }, () -> saved[0]++));
        assertTrue(a.lightingEnabled);
        assertFalse(b.lightingEnabled);
        history.undo();
        assertFalse(a.lightingEnabled);
        history.redo();
        assertTrue(a.lightingEnabled);
        assertEquals(3, prepared[0]);
        assertEquals(3, saved[0]);
        assertTrue(a.forwardLighting);
        assertEquals(.3f, a.ambientMulR, 0);
        SceneMetaRuntime copy = new SceneMetaRuntime(a);
        assertTrue(new Json().fromJson(SceneMetaRuntime.class, new Json().toJson(copy)).lightingEnabled);
    }

    @Test
    public void failedActivationRetainsOffAndTheOtherSettings() {
        SceneMeta a = new SceneMeta("A", "a.json");
        a.forwardLighting = true;
        SetSceneLightingCommand command = new SetSceneLightingCommand(world, a, true, value -> {
            throw new IllegalStateException("unsupported shader");
        }, null);
        assertThrows(IllegalStateException.class, command::redo);
        assertFalse(a.lightingEnabled);
        assertTrue(a.forwardLighting);
    }
}
