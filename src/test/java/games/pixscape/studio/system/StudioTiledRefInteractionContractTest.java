package games.pixscape.studio.system;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class StudioTiledRefInteractionContractTest {

    @Test
    public void spatialTileTintUsesTiledRenderRefBeforeLegacySlotFallback() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/GizmoSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("map.tiledRenderRefForTile(gx, gy)"));
        assertTrue(source.contains("drawSpatialTileTintSlot(map.slotForTile(gx, gy), colorPacked)"));
        assertTrue(source.indexOf("map.tiledRenderRefForTile(gx, gy)")
                < source.indexOf("drawSpatialTileTintSlot(map.slotForTile(gx, gy), colorPacked)"));
        assertTrue(source.contains("tiledState.isRenderableRef(tiledRenderRef)"));
    }

    @Test
    public void spatialAnchorPickingUsesTiledRenderRefBeforeLegacySlotFallback() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/PickingSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("int tiledRenderRef = map.tiledRenderRefForTile(gx, gy);"));
        assertTrue(source.contains("int slot = map.slotForTile(gx, gy);"));
        assertTrue(source.indexOf("int tiledRenderRef = map.tiledRenderRefForTile(gx, gy);")
                < source.indexOf("int slot = map.slotForTile(gx, gy);"));
        assertTrue(source.contains("tiledState.isRenderableRef(tiledRenderRef)"));
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
        assertTrue(source.contains("renderState,\n                                            tiledState,"));
    }
}
