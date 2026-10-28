package games.pixscape.studio.helper;

import games.pixscape.studio.input.InputManipulationContext;

public final class FixtureHandleHelper {

    private FixtureHandleHelper() {
    }

    public static InputManipulationContext.Handle detectBoxCornerHover(
            float worldUnitsPerPixel,
            float[] verts,
            float mx,
            float my,
            float hoverTolPx,
            float handleSizePx
    ) {
        if (verts == null || verts.length < 8) return InputManipulationContext.Handle.NONE;

        float halfWidthorld = (handleSizePx * 0.5f + hoverTolPx) * worldUnitsPerPixel;

        if (HandleHelper.insideSquare(mx, my, HandleLayout.swX(verts), HandleLayout.swY(verts), halfWidthorld)) {
            return InputManipulationContext.Handle.SW;
        }
        if (HandleHelper.insideSquare(mx, my, HandleLayout.seX(verts), HandleLayout.seY(verts), halfWidthorld)) {
            return InputManipulationContext.Handle.SE;
        }
        if (HandleHelper.insideSquare(mx, my, HandleLayout.neX(verts), HandleLayout.neY(verts), halfWidthorld)) {
            return InputManipulationContext.Handle.NE;
        }
        if (HandleHelper.insideSquare(mx, my, HandleLayout.nwX(verts), HandleLayout.nwY(verts), halfWidthorld)) {
            return InputManipulationContext.Handle.NW;
        }

        return InputManipulationContext.Handle.NONE;
    }

    public static int detectPolygonVertexHover(
            float worldUnitsPerPixel,
            float[] verts,
            int vertexCount,
            float mx,
            float my,
            float hoverTolPx,
            float handleSizePx
    ) {
        if (verts == null || vertexCount <= 0) return -1;

        float halfWidthorld = (handleSizePx * 0.5f + hoverTolPx) * worldUnitsPerPixel;

        for (int i = 0; i < vertexCount; i++) {
            float vx = verts[i * 2];
            float vy = verts[i * 2 + 1];
            if (HandleHelper.insideSquare(mx, my, vx, vy, halfWidthorld)) {
                return i;
            }
        }

        return -1;
    }
}
