package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.configuration.SceneMeta;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NewLayerDialogChoicesTest {

    @Test
    public void choicesFollowSceneFeaturesAndSpatialUniqueness() {
        SceneMeta meta = new SceneMeta();

        assertChoices(meta, false, "Classic");

        meta.tiledEnabled = true;
        assertChoices(meta, false, "Classic", "Tiled");

        meta.physicsEnabled = true;
        assertChoices(meta, false, "Classic", "Physics", "Spatial", "Tiled");
        assertChoices(meta, true, "Classic", "Physics", "Tiled");
    }

    private static void assertChoices(SceneMeta meta, boolean hasSpatialActorLayer, String... expected) {
        Array<String> actual = NewLayerDialog.availableLayerTypes(meta, hasSpatialActorLayer);
        assertEquals(Array.with(expected), actual);
    }
}
