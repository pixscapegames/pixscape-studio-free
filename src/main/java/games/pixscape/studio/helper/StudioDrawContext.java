package games.pixscape.studio.helper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import space.earlygrey.shapedrawer.ShapeDrawer;

public final class StudioDrawContext {
    public final ShapeDrawer drawer;
    public OrthographicCamera cam;
    public final SpriteBatch batch;

    public StudioDrawContext(SpriteBatch batch, ShapeDrawer drawer, OrthographicCamera cam) {
        this.batch = batch;
        this.drawer = drawer;
        this.cam = cam;
    }

    public StudioDrawContext setCam(OrthographicCamera cam) {
        this.cam = cam;
        return this;
    }

    public int screenWidth() {
        return Gdx.graphics.getWidth();
    }

    public int screenHeight() {
        return Gdx.graphics.getHeight();
    }

    public float wpp() {
        return (cam.viewportWidth * cam.zoom) / screenWidth();
    }

    public float pxToWorld(float px) {
        return px * wpp();
    }
}

