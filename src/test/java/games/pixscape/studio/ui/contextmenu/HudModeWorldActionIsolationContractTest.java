package games.pixscape.studio.ui.contextmenu;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class HudModeWorldActionIsolationContractTest {
    @Test
    public void canvasMenuGatesEveryWorldActionGroupBeforeBuildingIt() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/contextmenu/StudioContextMenu.java");
        int gate = source.indexOf("if (!editingModeService.allowsWorldEditingActions()) return false;");
        int build = source.indexOf("buildMenu();", gate);

        Assert.assertTrue("HUD gate must run before menu construction", gate >= 0 && build > gate);
        Assert.assertTrue(source.contains("showAddLightMenu();"));
        Assert.assertTrue(source.contains("showAddTiledMapMenu();"));
        Assert.assertTrue(source.contains("showShapeMenu();"));
        Assert.assertTrue(source.contains("showSpatialBlocksMenu()"));
        Assert.assertTrue(source.contains("showJointsMenu();"));
        Assert.assertTrue(source.contains("showEditMenu();"));
    }

    @Test
    public void otherReachableWorldControlsUseTheSameModeAuthority() throws Exception {
        assertUsesGate("src/main/java/games/pixscape/studio/ui/layer/LayersPanel.java");
        assertUsesGate("src/main/java/games/pixscape/studio/ui/main/ToolBar.java");
        assertUsesGate("src/main/java/games/pixscape/studio/ui/main/TopMenuBar.java");
        assertUsesGate("src/main/java/games/pixscape/studio/ui/main/BottomMenuBar.java");
        assertUsesGate("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        assertUsesGate("src/main/java/games/pixscape/studio/ui/tree/ItemTreePanel.java");
    }

    private static void assertUsesGate(String path) throws Exception {
        Assert.assertTrue(path + " must use centralized HUD isolation",
                read(path).contains("allowsWorldEditingActions()"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
