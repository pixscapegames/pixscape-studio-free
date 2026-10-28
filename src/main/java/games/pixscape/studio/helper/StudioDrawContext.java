package games.pixscape.studio.helper;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import space.earlygrey.shapedrawer.ShapeDrawer;

import java.util.Objects;

public final class StudioDrawContext {
    public final ShapeDrawer drawer;
    public final OrthographicCamera cam;
    public final SpriteBatch batch;
    public final Viewport viewport;

    public StudioDrawContext(SpriteBatch batch,
                             ShapeDrawer drawer,
                             OrthographicCamera cam,
                             Viewport viewport) {
        this.batch = batch;
        this.drawer = drawer;
        this.cam = Objects.requireNonNull(cam, "cam");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        if (viewport.getCamera() != cam) {
            throw new IllegalArgumentException("StudioDrawContext camera must own its Viewport.");
        }
    }

    public int screenWidth() {
        return Math.max(1, viewport.getScreenWidth());
    }

    public int screenHeight() {
        return Math.max(1, viewport.getScreenHeight());
    }

    public float wpp() {
        return (cam.viewportWidth * cam.zoom) / screenWidth();
    }

    public float pxToWorld(float px) {
        return px * wpp();
    }

    public Vector2 screenToWorld(float screenX, float screenY, Vector2 out) {
        out.set(screenX, screenY);
        viewport.unproject(out);
        return out;
    }
}

