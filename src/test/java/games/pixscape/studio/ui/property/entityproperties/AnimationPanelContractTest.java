package games.pixscape.studio.ui.property.entityproperties;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AnimationPanelContractTest {
    @Test
    public void entityPanelUsesMultiAssetControlsWithoutClipAuthoring() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/property/entityproperties/AnimationPanel.java"));

        assertTrue(source.contains("new Button(VisUI.getSkin(), \"add\")"));
        assertTrue(source.contains("new Button(VisUI.getSkin(), \"delete\")"));
        assertTrue(source.contains("deleteButton.setDisabled(items.size <= 1)"));
        assertTrue(source.contains("ctx.assetMetaLookup.apply(assetId) instanceof AnimationAssetMeta"));
        assertFalse(source.contains("Edit clips"));
        assertFalse(source.contains("new VisCheckBox(\"Flip\")"));
    }
}
