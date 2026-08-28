package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.configuration.SceneMeta;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NewLayerDialogChoicesTest {

    @Test
    public void choicesContainOnlyClassicAndOptionalTiled() {
        SceneMeta meta = new SceneMeta();

        assertChoices(meta, "Classic");

        meta.tiledEnabled = true;
        assertChoices(meta, "Classic", "Tiled");

        meta.physicsEnabled = true;
        assertChoices(meta, "Classic", "Tiled");
    }

    private static void assertChoices(SceneMeta meta, String... expected) {
        Array<String> actual = NewLayerDialog.availableLayerTypes(meta);
        assertEquals(Array.with(expected), actual);
    }
}
