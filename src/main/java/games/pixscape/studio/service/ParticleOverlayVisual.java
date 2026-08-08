package games.pixscape.studio.service;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.helper.StudioDrawContext;

/** Temporary shared Item Tree/viewport visual for particle emitters. */
public final class ParticleOverlayVisual {
    public static final float MARKER_SIZE_PX = 24f;

    private static Drawable drawable;

    public Drawable drawable() {
        return resolveDrawable();
    }

    public float markerSizePx() {
        return MARKER_SIZE_PX;
    }

    public void draw(StudioDrawContext ctx, TransformComponent transform) {
        Drawable marker = resolveDrawable();
        float size = ctx.pxToWorld(MARKER_SIZE_PX);
        float half = size * 0.5f;
        Color color = ctx.batch.getColor();
        float oldR = color.r;
        float oldG = color.g;
        float oldB = color.b;
        float oldA = color.a;
        ctx.batch.setColor(0.45f, 0.9f, 1f, 1f);
        marker.draw(ctx.batch, transform.x - half, transform.y - half, size, size);
        ctx.batch.setColor(oldR, oldG, oldB, oldA);
    }

    public static Drawable resolveDrawable() {
        if (drawable == null) {
            drawable = VisUI.getSkin().getDrawable("particle_icon16");
        }
        return drawable;
    }
}
