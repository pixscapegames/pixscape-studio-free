package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudHorizontalAnchor;
import games.pixscape.runtime.hud.document.HudVerticalAnchor;

/** Converts visual FREE bounds to the exact authored anchor/pivot/offset representation. */
public final class HudFreeTransform {
    public record Snapshot(HudHorizontalAnchor horizontalAnchor, HudVerticalAnchor verticalAnchor,
                           float pivotX, float pivotY, float parentWidth, float parentHeight,
                           float authoredWidth, float authoredHeight,
                           HudTransformGeometry.Bounds visualBounds) {}

    public record Result(float offsetX, float offsetY, float width, float height) {}

    private HudFreeTransform() {}

    public static Snapshot snapshot(HudFreePlacement placement, float authoredWidth,
                                    float authoredHeight, float parentWidth, float parentHeight,
                                    HudTransformGeometry.Bounds visualBounds) {
        if (placement == null) throw new IllegalArgumentException("FREE placement is required.");
        return new Snapshot(placement.horizontalAnchor, placement.verticalAnchor,
                placement.pivotX, placement.pivotY, parentWidth, parentHeight,
                authoredWidth, authoredHeight, visualBounds);
    }

    public static Result resolve(Snapshot initial, HudTransformGeometry.Bounds visualBounds,
                                 boolean writeWidth, boolean writeHeight) {
        float offsetX = visualBounds.x() - anchorX(initial.horizontalAnchor, initial.parentWidth)
                + visualBounds.width() * initial.pivotX;
        float offsetY = visualBounds.y() - anchorY(initial.verticalAnchor, initial.parentHeight)
                + visualBounds.height() * initial.pivotY;
        return new Result(offsetX, offsetY,
                writeWidth ? visualBounds.width() : initial.authoredWidth,
                writeHeight ? visualBounds.height() : initial.authoredHeight);
    }

    private static float anchorX(HudHorizontalAnchor anchor, float parentWidth) {
        return anchor == HudHorizontalAnchor.LEFT ? 0f
                : anchor == HudHorizontalAnchor.CENTER ? parentWidth / 2f : parentWidth;
    }

    private static float anchorY(HudVerticalAnchor anchor, float parentHeight) {
        return anchor == HudVerticalAnchor.BOTTOM ? 0f
                : anchor == HudVerticalAnchor.CENTER ? parentHeight / 2f : parentHeight;
    }
}
