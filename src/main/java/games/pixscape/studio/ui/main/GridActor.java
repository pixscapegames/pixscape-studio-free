package games.pixscape.studio.ui.main;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.helper.GridHelper;
import games.pixscape.studio.helper.StudioDrawContext;

public class GridActor extends Actor {

    public enum GridMode {
        FREE,
        TILED_ORTHO,
        TILED_ISO
    }

    private GridMode mode = GridMode.FREE;
    private float tiledWidth = 32f;
    private float tiledHeight = 32f;

    private TiledMapLayerData boundMap;

    private boolean hasBounds = false;

    private float minBoundX, minBoundY;
    private float maxBoundX, maxBoundY;

    private final StudioDrawContext ctx;

    public static float LENGTH_CELL = 20f;

    private final Color minor = new Color(1, 1, 1, 0.035f);
    private final Color major = new Color(1, 1, 1, 0.10f);
    private final Color axis = new Color(0.2f, 0.4f, 0.2f, 0.75f);

    public GridActor(StudioDrawContext ctx) {
        this.ctx = ctx;
        setTouchable(Touchable.disabled);
        toBack();
    }

    public void setFreeMode() {
        mode = GridMode.FREE;
        clearBounds();
    }

    public void setTiledMode(SceneMetaRuntime.TiledProjection projection,
                             float tileWidth,
                             float tileHeight) {
        mode = (projection == SceneMetaRuntime.TiledProjection.ISO)
                ? GridMode.TILED_ISO
                : GridMode.TILED_ORTHO;
        this.tiledWidth = tileWidth;
        this.tiledHeight = tileHeight;
    }

    public void setMapBounds(float minX, float minY, float maxX, float maxY) {
        this.minBoundX = minX;
        this.minBoundY = minY;
        this.maxBoundX = maxX;
        this.maxBoundY = maxY;
        this.hasBounds = true;
    }

    public void clearBounds() {
        hasBounds = false;
    }

    public void bindTo(TiledMapLayerData map) {
        this.boundMap = map;
    }

    public void unbind() {
        this.boundMap = null;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {

        switch (mode) {
            case FREE -> {
                int majorEvery = 10;
                GridHelper.drawGrid(
                        ctx,
                        LENGTH_CELL,
                        majorEvery,
                        minor,
                        major,
                        axis,
                        1f,
                        1.5f,
                        2f
                );
            }

            case TILED_ORTHO -> {
                if (boundMap == null) return;

                float minX = boundMap.originX;
                float minY = boundMap.originY;
                float maxX = minX + boundMap.mapWidth * boundMap.tileWidth;
                float maxY = minY + boundMap.mapHeight * boundMap.tileHeight;

                GridHelper.drawTiledOrthoGrid(
                        ctx,
                        boundMap.originX,
                        boundMap.originY,
                        boundMap.tileWidth,
                        boundMap.tileHeight,
                        major,
                        1.2f,
                        hasBounds,
                        hasBounds ? minBoundX : minX,
                        hasBounds ? minBoundY : minY,
                        hasBounds ? maxBoundX : maxX,
                        hasBounds ? maxBoundY : maxY
                );
            }

            case TILED_ISO -> {
                if (boundMap == null) return;

                GridHelper.drawTiledIsoGrid(
                        ctx,
                        boundMap,
                        major,
                        1.2f
                );
            }
        }
    }
}