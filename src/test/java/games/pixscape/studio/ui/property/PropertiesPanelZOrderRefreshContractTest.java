package games.pixscape.studio.ui.property;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class PropertiesPanelZOrderRefreshContractTest {
    @Test
    public void layerScopedZOrderEventRefreshesOnlyTheBoundEntityZLabel() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/property/PropertiesPanel.java"));

        assertTrue(source.contains(
                "EventFlow.i().subscribe(EventFlow.EntityZOrderChanged.class"));
        assertTrue(source.contains("boundEntity < 0"));
        assertTrue(source.contains("index.layerIndex == evt.layerIndex()"));
        assertTrue(source.contains("entityProperties.refreshZIndex();"));
    }
}
