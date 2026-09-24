package games.pixscape.studio.ui.docking;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;

public class DockingArchitectureContractTest {
    @Test
    public void dockManagerIsIndependentOfDocumentModesAndHudPanels() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/docking/DockManager.java");

        assertFalse(source.contains("games.pixscape.studio.ui.hud"));
        assertFalse(source.contains("EditorDocumentType"));
        assertFalse(source.contains("EditorDocumentManager"));
        assertFalse(source.contains("LayersPanel"));
        assertFalse(source.contains("HudWidgetsPanel"));
        assertFalse(source.contains("DocumentRightBottomProjection"));
        assertFalse(source.contains("configureDocumentRightBottom"));
        assertFalse(source.contains("projectRightBottomForDocument"));
    }

    @Test
    public void workspacePolicyDoesNotMutateDockManagerInternals() throws Exception {
        String app = read("src/main/java/games/pixscape/studio/ui/main/StudioApplicationAdapter.java");
        String coordinator = read(
                "src/main/java/games/pixscape/studio/ui/main/DocumentDockPanelCoordinator.java");

        assertFalse(app.contains("configureDocumentRightBottom"));
        assertFalse(app.contains("rightBottom.clearChildren"));
        assertFalse(app.contains("rightSplit.setWidgets"));
        assertFalse(coordinator.contains("rightBottom"));
        assertFalse(coordinator.contains("rightTop"));
        assertFalse(coordinator.contains("rightSplit"));
        assertFalse(coordinator.contains("topArea"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
