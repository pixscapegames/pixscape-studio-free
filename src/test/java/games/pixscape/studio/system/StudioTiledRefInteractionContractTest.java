package games.pixscape.studio.system;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioTiledRefInteractionContractTest {

    @Test
    public void spatialTileTintUsesTiledRenderRefWithoutLegacySlotFallback() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/GizmoSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("map.tiledRenderRefForTile(gx, gy)"));
        assertTrue(source.contains("tiledState.isRenderableRef(tiledRenderRef)"));
        assertFalse(source.contains("slotForTile("));
        assertFalse(source.contains("drawSpatialTileTintSlot"));
    }

    @Test
    public void spatialAnchorPickingUsesTiledRenderRefWithoutLegacySlotFallback() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/PickingSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("int tiledRenderRef = map.tiledRenderRefForTile(gx, gy);"));
        assertTrue(source.contains("tiledState.isRenderableRef(tiledRenderRef)"));
        assertFalse(source.contains("slotForTile("));
        assertFalse(source.contains("isRenderableTileSlot"));
    }

    @Test
    public void worldCanvasWiresStudioToolsToTiledMapRenderState() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("new GizmoSystem("));
        assertTrue(source.contains("new PickingSystem("));
        assertTrue(source.contains("new TiledFallbackSystem("));
        assertTrue(source.contains("renderState,\n                tiledState,"));
        assertTrue(source.contains("tiledFallbackSystem = new TiledFallbackSystem(\n                                            tiledState,"));
    }
}
