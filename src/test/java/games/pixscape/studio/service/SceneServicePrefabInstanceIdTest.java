package games.pixscape.studio.service;

import com.badlogic.gdx.utils.Json;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SceneServicePrefabInstanceIdTest {
    @Test
    public void allocationResolvesTheCurrentSceneOnEveryCallAndPersistsHighWaterMark() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("A");
        config.getSceneMeta("A").nextPrefabInstanceId = 5;
        config.createSceneMeta("B");
        config.getSceneMeta("B").nextPrefabInstanceId = 20;

        assertEquals(20, SceneService.allocatePrefabInstanceId(config));
        assertEquals(21, config.getSceneMeta("B").nextPrefabInstanceId);
        config.setCurrentSceneByName("A");
        assertEquals(5, SceneService.allocatePrefabInstanceId(config));
        assertEquals(6, config.getSceneMeta("A").nextPrefabInstanceId);

        Json json = new Json();
        SceneMeta loaded = json.fromJson(SceneMeta.class, json.toJson(config.getSceneMeta("B")));
        assertEquals(21, loaded.nextPrefabInstanceId);
        assertEquals(1, json.fromJson(SceneMeta.class, "{}").nextPrefabInstanceId);
    }

    @Test
    public void invalidAndExhaustedStateRejectBeforeMutation() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        SceneMeta meta = config.getCurrentSceneMeta();
        for (int invalid : new int[]{0, -1, Integer.MAX_VALUE}) {
            meta.nextPrefabInstanceId = invalid;
            try {
                SceneService.allocatePrefabInstanceId(config);
                fail("Expected invalid allocator state to fail: " + invalid);
            } catch (IllegalStateException expected) {
                assertEquals(invalid, meta.nextPrefabInstanceId);
            }
        }
    }
}
