package games.pixscape.studio.system;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EditorOverlayStyleContractTest {

    @Test
    public void spatialTileHoverSelectionAndLinkedTilesUseIdenticalPackedTint() throws Exception {
        String gizmo = source("src/main/java/games/pixscape/studio/system/GizmoSystem.java");

        assertTrue(occurrences(gizmo, "EditorOverlayPalette.spatialTileHighlightPacked()") >= 3);
        assertFalse(gizmo.contains("EditorOverlayPalette.PHYSICS_SELECTED_COLOR.r"));
    }

    @Test
    public void canvasGeometryHandlesUseSharedWhiteHandleColor() throws Exception {
        String gizmo = source("src/main/java/games/pixscape/studio/system/GizmoSystem.java");
        String helper = source("src/main/java/games/pixscape/studio/helper/GizmoDrawHelper.java");

        assertTrue(gizmo.contains("EditorOverlayPalette.HANDLE_COLOR"));
        assertTrue(helper.contains("ctx.drawer.setColor(EditorOverlayPalette.HANDLE_COLOR);"));
        assertFalse(gizmo.contains("? EditorOverlayPalette.HANDLE_COLOR :"));
    }

    @Test
    public void directWallHoverAndSelectionDrawCompleteWallWithoutStructurePromotion() throws Exception {
        String gizmo = source("src/main/java/games/pixscape/studio/system/GizmoSystem.java");

        assertTrue(gizmo.contains("hoveredBlock.id != selectedBlockId"));
        assertTrue(gizmo.contains("ctx.drawer.setColor(EditorOverlayPalette.WALL_HOVER_COLOR);"));
        assertTrue(gizmo.contains("SpatialWallWireframe.write("));
        assertTrue(gizmo.contains("ctx.drawer.setColor(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR);"));
        assertFalse(gizmo.contains("isJoinedSpatialEdge"));
        assertFalse(gizmo.contains("groupRole("));
        assertFalse(gizmo.contains("hoveredStructureId"));
    }

    @Test
    public void activeEditPreviewSuppressesOtherWallHoverAndUsesCandidate() throws Exception {
        String gizmo = source("src/main/java/games/pixscape/studio/system/GizmoSystem.java");

        assertTrue(gizmo.contains("&& !spatialBlockSelectionService.isEditPreviewActive()"));
        assertTrue(gizmo.contains("activeSession.candidate()"));
        assertTrue(gizmo.contains("EditorOverlayPalette.spatialWallColor("));
    }

    @Test
    public void pickingAndRenderingShareHoverIdAndClearItOutsideTheCanvas() throws Exception {
        String picking = source("src/main/java/games/pixscape/studio/system/PickingSystem.java");
        String gizmo = source("src/main/java/games/pixscape/studio/system/GizmoSystem.java");

        assertTrue(picking.contains("spatialBlockSelectionService.setHoveredBlock(target.blockId);"));
        assertTrue(gizmo.contains("spatialBlockSelectionService.getHoveredBlockId()"));
        assertTrue(picking.contains("spatialBlockSelectionService.clearHover();"));
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) count++;
        return count;
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
