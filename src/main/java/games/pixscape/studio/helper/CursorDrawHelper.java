package games.pixscape.studio.helper;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.kotcrab.vis.ui.VisUI;

public final class CursorDrawHelper {

    private CursorDrawHelper() {
    }

    private static Drawable move;
    private static Drawable resize;
    private static Drawable rotate;

    // taille logique du curseur en pixels
    private static final float SIZE_PX = 32f;

    // --------------------------------------------------------------------
    // Init (called at boot)
    // --------------------------------------------------------------------

    public static void init() {
        move = VisUI.getSkin().getDrawable("move");
        resize = VisUI.getSkin().getDrawable("resize");
        rotate = VisUI.getSkin().getDrawable("rotate");
    }

    // --------------------------------------------------------------------
    // Draw (WORLD SPACE)
    // --------------------------------------------------------------------

    public static void draw(
            StudioDrawContext ctx,
            Vector2 mouseWorld,
            CursorKind kind,
            float rotationRad
    ) {
        if (kind == CursorKind.NONE) return;

        Drawable d = switch (kind) {
            case MOVE -> move;
            case RESIZE -> resize;
            case ROTATE -> rotate;
            default -> null;
        };
        if (d == null) return;

        float sizeWorld = HandleHelper.pxToWorld(ctx.cam, SIZE_PX);
        float halfWidthWorld = sizeWorld * 0.5f;

        float x = mouseWorld.x - halfWidthWorld;
        float y = mouseWorld.y - halfWidthWorld;

        float previousPackedColor = ctx.batch.getPackedColor();
        ctx.batch.setColor(Color.WHITE);

        try {
            if (rotationRad == 0f) {
                d.draw(ctx.batch, x, y, sizeWorld, sizeWorld);
                return;
            }

            if (!(d instanceof TextureRegionDrawable trd)) {
                d.draw(ctx.batch, x, y, sizeWorld, sizeWorld);
                return;
            }

            ctx.batch.draw(
                    trd.getRegion(),
                    x, y,
                    halfWidthWorld, halfWidthWorld,
                    sizeWorld, sizeWorld,
                    1f, 1f,
                    rotationRad * MathUtils.radiansToDegrees
            );
        } finally {
            ctx.batch.setPackedColor(previousPackedColor);
        }
    }
}