package games.pixscape.studio.ui.config;

import com.badlogic.gdx.graphics.Color;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

public class EditorOverlayPaletteTest {

    @Test
    public void spatialWallRulesUseNeutralGreenWhiteHoverAndYellowSelection() {
        assertSame(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR,
                EditorOverlayPalette.spatialWallColor(false, false, false, true));
        assertSame(EditorOverlayPalette.WALL_HOVER_COLOR,
                EditorOverlayPalette.spatialWallColor(false, true, false, true));
        assertSame(EditorOverlayPalette.WALL_SELECTED_COLOR,
                EditorOverlayPalette.spatialWallColor(true, true, false, true));
    }

    @Test
    public void previewValidityOverridesSelectionWithoutChangingCoverageSemantics() {
        assertSame(EditorOverlayPalette.VALID_PREVIEW_COLOR,
                EditorOverlayPalette.spatialWallColor(true, true, true, true));
        assertSame(EditorOverlayPalette.INVALID_PREVIEW_COLOR,
                EditorOverlayPalette.spatialWallColor(true, true, true, false));
    }

    @Test
    public void tileHighlightUsesOneStrongGreenPackedTint() {
        Color tint = EditorOverlayPalette.SPATIAL_TILE_HIGHLIGHT_COLOR;

        assertEquals(tint.toFloatBits(), EditorOverlayPalette.spatialTileHighlightPacked(), 0f);
        assertEquals(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR.r, tint.r, 0f);
        assertEquals(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR.g, tint.g, 0f);
        assertEquals(EditorOverlayPalette.SPATIAL_NEUTRAL_COLOR.b, tint.b, 0f);
        assertEquals(0.50f, tint.a, 0f);
        assertNotEquals(EditorOverlayPalette.PHYSICS_SELECTED_COLOR.toFloatBits(),
                EditorOverlayPalette.spatialTileHighlightPacked());
    }

    @Test
    public void everyEditableHandleUsesSharedOpaqueWhite() {
        assertEquals(Color.WHITE, EditorOverlayPalette.HANDLE_COLOR);
        assertEquals(1f, EditorOverlayPalette.HANDLE_COLOR.a, 0f);
    }
}
