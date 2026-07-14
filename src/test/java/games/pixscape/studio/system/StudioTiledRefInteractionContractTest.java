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
        assertFalse(source.contains(legacySlotLookupName()));
        assertFalse(source.contains("drawSpatialTileTintSlot"));
    }

    @Test
    public void spatialAnchorPickingUsesCanonicalPickerWithVisibleRefOverhangFallback() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/PickingSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains(
                "SpatialCellPicker.pickForSpatialSelection(tiled.data, tiledState, mx, my, spatialCell)"));
        assertFalse(source.contains("findTopmostRenderedTileAnchor"));
        assertFalse(source.contains(legacySlotLookupName()));
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
        assertTrue(source.contains("dynamicEntityState = new DynamicEntityRenderState();"));
        assertTrue(source.contains("tiledState = new TiledMapRenderState();"));
        assertTrue(source.contains("tiledFallbackSystem = new TiledFallbackSystem(\n                                            tiledState,"));
    }

    @Test
    public void spatialGizmoConsumesCompiledStructureEnvelopeWithoutPerWallBoxes() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/GizmoSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("spatialStructureGeometryCache.synchronize(layerEntityId, component, tiled.data)"));
        assertTrue(source.contains("drawCompiledStructure("));
        assertFalse(source.contains("drawBlockVolume("));
        assertFalse(source.contains("drawBlockOutline("));
    }

    @Test
    public void spatialInteractivePreviewUsesDetachedSessionInsteadOfAuthoredWallMutation() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/PickingSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("wallEditSession().updateMove("));
        assertTrue(source.contains("wallEditSession().updateResize("));
        assertTrue(source.contains("wallEditSession().updateHeight("));
        assertFalse(source.contains("applySpatialBlockSnapshot("));
        assertTrue(source.contains("spatialPointer.crossedDragThreshold(Gdx.input.getX(), Gdx.input.getY())"));
        assertTrue(source.contains("SpatialPointerInteraction.Target.SELECTED_FOOTPRINT"));
    }

    @Test
    public void spatialPreviewDrawsTransientCandidateWithoutPublishingItToCompiledCache() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/GizmoSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("SpatialWallWireframe.write("));
        assertTrue(source.contains("drawSpatialWallVolume(ctx.pxToWorld(2.5f))"));
        assertTrue(source.contains("activeSession.candidate()"));
        assertTrue(source.contains("SpatialWallThicknessInheritance.apply("));
    }

    @Test
    public void spatialNonWallPressAndEscapeUseAuthoritativeSelectionClearPath() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/PickingSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("SpatialPointerInteraction.clearsWallSelection(target.type)"));
        assertTrue(source.contains("spatialBlockSelectionService.clearSelectionOnly();"));
        assertTrue(source.contains("processSpatialEscape()"));
        assertFalse(source.contains("historyManager.execute(new ClearSpatial"));
    }

    private static String legacySlotLookupName() {
        return "slotFor" + "Tile(";
    }
}
