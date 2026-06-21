package games.pixscape.studio.service;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.Viewport;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;

/**
 * Centralizes conversions between coordinate spaces (screen, UI stage, world).
 */
public final class CoordSpaces {

    private final OrthographicCamera worldCamera;
    private final Viewport worldViewport;
    private final Stage worldStage;
    private final Stage uiStage;

    private final Vector2 tmp2 = new Vector2();
    private final Vector3 tmp3 = new Vector3();

    public CoordSpaces(OrthographicCamera worldCamera,
                       Viewport worldViewport,
                       Stage worldStage,
                       Stage uiStage) {
        this.worldCamera = worldCamera;
        this.worldViewport = worldViewport;
        this.worldStage = worldStage;
        this.uiStage = uiStage;
    }

    public Vector2 screenToWorld(float screenX, float screenY, Vector2 out) {
        tmp3.set(screenX, screenY, 0f);
        worldViewport.unproject(tmp3);
        return out.set(tmp3.x, tmp3.y);
    }

    public Vector2 screenToWorldLogical(float screenX, float screenY, int layerIndex, LayerService layerService, Vector2 out) {
        screenToWorld(screenX, screenY, out);
        return renderToWorldLogical(out.x, out.y, layerIndex, layerService, out);
    }

    public Vector2 renderToWorldLogical(float renderX, float renderY, int layerIndex, LayerService layerService, Vector2 out) {
        Vector2 offset = computeLayerRenderOffset(layerIndex, layerService, tmp2);
        return out.set(renderX - offset.x, renderY - offset.y);
    }

    public Vector2 screenToWorld(Vector2 screen, Vector2 out) {
        return screenToWorld(screen.x, screen.y, out);
    }

    public Vector2 screenToWorldStage(float screenX, float screenY, Vector2 out) {
        if (worldStage == null) {
            return screenToWorld(screenX, screenY, out);
        }
        tmp2.set(screenX, screenY);
        worldStage.screenToStageCoordinates(tmp2);
        return out.set(tmp2);
    }

    public Vector2 screenToUi(float screenX, float screenY, Vector2 out) {
        if (uiStage == null) {
            return out.set(screenX, screenY);
        }
        tmp2.set(screenX, screenY);
        uiStage.screenToStageCoordinates(tmp2);
        return out.set(tmp2);
    }

    public Vector2 worldToScreen(float worldX, float worldY, Vector2 out) {
        tmp3.set(worldX, worldY, 0f);
        worldCamera.project(tmp3);
        return out.set(tmp3.x, tmp3.y);
    }

    private Vector2 computeLayerRenderOffset(int layerIndex, LayerService layerService, Vector2 out) {
        // Studio canvas uses logical editing space; parallax is applied only in Preview/runtime.
        return out.set(0f, 0f);
    }

    private SceneMeta currentSceneMeta() {
        ProjectConfig cfg = ProjectConfig.getInstance();
        return cfg != null ? cfg.getCurrentSceneMeta() : null;
    }
}
