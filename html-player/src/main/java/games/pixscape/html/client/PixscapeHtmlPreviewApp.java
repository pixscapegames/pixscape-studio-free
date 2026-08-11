package games.pixscape.html.client;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.optional.PhysicsMouseDragSystem;

public final class PixscapeHtmlPreviewApp extends ApplicationAdapter {

    static final float HTML_PRELOAD_SHARE = 0.10f;
    private static final float PROJECT_SHARE = 0.10f;

    private enum StartupState {
        FIRST_FRAME,
        PROJECT,
        SCENE,
        READY,
        RUNNING,
        FAILED
    }

    private PixscapeEngine engine;
    private Box2dWorldService box2d;
    private PhysicsMouseDragSystem dragSystem;
    private SceneLoadHandle sceneLoad;
    private StartupState startupState;
    private PreviewLoadingUi loadingUi;
    private float coreProgress;

    private Stage uiStage;
    private SpriteBatch uiBatch;
    private RenderStatsOverlay statsOverlay;

    private boolean benchMode;
    private final FrameTimePercentiles frameTimes = new FrameTimePercentiles(600);

    private static final long FRAME_STATS_REFRESH_NS = 250_000_000L;
    private static final float CAMERA_PAN_SPEED_SCREEN = 450f;
    private static final float CAMERA_DT_MAX = 1f / 30f;
    private static final float CAMERA_ZOOM_SPEED = 1.5f;
    private static final float CAMERA_ZOOM_MIN = 0.2f;
    private static final float CAMERA_ZOOM_MAX = 10f;

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_INFO);
        Gdx.input.setCatchKey(Input.Keys.SPACE, true);
        loadingUi = new PreviewLoadingUi();
        startupState = StartupState.FIRST_FRAME;
    }

    private void initializeProject() {
        FileHandle projectJson = Gdx.files.internal(
                PixscapeEngine.RUNTIME_DIR_NAME + "/project.json");
        if (!projectJson.exists()) {
            throw new GdxRuntimeException("Missing HTML preview runtime project: "
                    + PixscapeEngine.RUNTIME_DIR_NAME + "/project.json");
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        OrthographicCamera worldCamera = new OrthographicCamera();
        dragSystem = new PhysicsMouseDragSystem(worldCamera);
        dragSystem.setMaxForce(2000f);
        dragSystem.setFrequencyHz(5f);
        dragSystem.setDampingRatio(0.7f);
        dragSystem.setGrabRadiusMeters(0.25f);

        engine = new PixscapeEngine()
                .setWorldCamera(worldCamera)
                .setConfigurationCustomizer(builder -> builder.with(dragSystem));
        engine.setPlatformTarget(PlatformTarget.HTML_WEBGL2);
        engine.loadProject(projectJson.parent().parent());
        engine.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        coreProgress = PROJECT_SHARE;
        sceneLoad = engine.beginLoadScene(null);
    }

    private void initializePreview() {
        dragSystem.setLayerState(engine.getLayerState());
        box2d = engine.getBox2dWorldService();
        uiBatch = new SpriteBatch();
        uiStage = new Stage(new ScreenViewport(), uiBatch);
        Gdx.input.setInputProcessor(new InputMultiplexer(new PreviewInputAdapter(), uiStage));
        statsOverlay = new RenderStatsOverlay(uiStage, engine.getRenderStats());
        statsOverlay.setEnabled(false);
        benchMode = false;
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        if (startupState != StartupState.RUNNING) {
            renderStartup(dt);
            return;
        }

        handleBenchToggle();
        long nowNs = System.currentTimeMillis() * 1_000_000L;
        frameTimes.onFrameStart(nowNs);
        if (frameTimes.computeIfDue(nowNs, FRAME_STATS_REFRESH_NS) && statsOverlay != null) {
            statsOverlay.setFrameTimes(frameTimes.avgMs, frameTimes.p95Ms,
                    frameTimes.p99Ms, frameTimes.maxMs);
            if (box2d != null) {
                statsOverlay.setBox2dStats(box2d.lastStepTimeNs / 1_000_000.0,
                        box2d.lastSubsteps, box2d.bodyCount, box2d.contactCount,
                        box2d.jointCount);
            }
        }

        handleCameraControls(dt);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        engine.update(dt);
        engine.render();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        if (statsOverlay != null) {
            uiStage.getViewport().apply(true);
            statsOverlay.render(nowNs);
        }
    }

    private void renderStartup(float dt) {
        try {
            switch (startupState) {
                case FIRST_FRAME:
                    loadingUi.render(dt, displayedProgress());
                    startupState = StartupState.PROJECT;
                    return;
                case PROJECT:
                    initializeProject();
                    startupState = StartupState.SCENE;
                    loadingUi.render(dt, displayedProgress());
                    return;
                case SCENE:
                    sceneLoad.update();
                    coreProgress = PROJECT_SHARE
                            + (1f - PROJECT_SHARE) * sceneLoad.progress();
                    if (sceneLoad.isFailed()) {
                        startupState = StartupState.FAILED;
                        throw new GdxRuntimeException("HTML preview scene loading failed.",
                                sceneLoad.failure());
                    }
                    if (sceneLoad.isReady()) {
                        coreProgress = 1f;
                        startupState = StartupState.READY;
                    }
                    loadingUi.render(dt, displayedProgress());
                    return;
                case READY:
                    initializePreview();
                    sceneLoad = null;
                    loadingUi.dispose();
                    loadingUi = null;
                    startupState = StartupState.RUNNING;
                    return;
                case FAILED:
                    return;
                default:
                    throw new IllegalStateException("Unknown HTML preview startup state: " + startupState);
            }
        } catch (Throwable failure) {
            startupState = StartupState.FAILED;
            Gdx.app.error("PixscapeHtmlPreviewApp", "HTML preview startup failed.", failure);
            if (failure instanceof GdxRuntimeException) throw (GdxRuntimeException) failure;
            throw new GdxRuntimeException("HTML preview startup failed.", failure);
        }
    }

    private float displayedProgress() {
        return HTML_PRELOAD_SHARE + (1f - HTML_PRELOAD_SHARE) * coreProgress;
    }

    @Override
    public void resize(int width, int height) {
        if (loadingUi != null) loadingUi.resize(width, height);
        if (engine != null) engine.resize(width, height);
        if (uiStage != null) uiStage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        sceneLoad = null;
        if (loadingUi != null) {
            loadingUi.dispose();
            loadingUi = null;
        }
        if (statsOverlay != null) {
            statsOverlay.dispose();
            statsOverlay = null;
        }
        if (uiStage != null) {
            uiStage.dispose();
            uiStage = null;
        }
        if (uiBatch != null) {
            uiBatch.dispose();
            uiBatch = null;
        }
        if (engine != null) {
            engine.dispose();
            engine = null;
        }
    }

    private void handleCameraControls(float dt) {
        OrthographicCamera cam = engine.getCamera();
        if (cam == null) return;
        float safeDt = Math.min(dt, CAMERA_DT_MAX);
        float moveSpeed = CAMERA_PAN_SPEED_SCREEN * cam.zoom;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) cam.position.x -= moveSpeed * safeDt;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) cam.position.x += moveSpeed * safeDt;
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) cam.position.y += moveSpeed * safeDt;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) cam.position.y -= moveSpeed * safeDt;

        float zoomDelta = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.PLUS)
                || Gdx.input.isKeyPressed(Input.Keys.EQUALS)
                || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_ADD)) {
            zoomDelta -= CAMERA_ZOOM_SPEED * safeDt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.MINUS)
                || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_SUBTRACT)) {
            zoomDelta += CAMERA_ZOOM_SPEED * safeDt;
        }
        cam.zoom = Math.max(CAMERA_ZOOM_MIN, Math.min(CAMERA_ZOOM_MAX, cam.zoom + zoomDelta));
        cam.update();
    }

    private void handleBenchToggle() {
        if (!Gdx.input.isKeyJustPressed(Input.Keys.F9)) return;
        benchMode = !benchMode;
        if (statsOverlay != null) statsOverlay.setEnabled(benchMode);
        frameTimes.reset();
    }

    private static final class PreviewInputAdapter extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            return isPreviewKey(keycode);
        }

        @Override
        public boolean keyUp(int keycode) {
            return isPreviewKey(keycode);
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            return true;
        }

        private static boolean isPreviewKey(int keycode) {
            return keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT
                    || keycode == Input.Keys.UP || keycode == Input.Keys.DOWN
                    || keycode == Input.Keys.PLUS || keycode == Input.Keys.EQUALS
                    || keycode == Input.Keys.MINUS || keycode == Input.Keys.NUMPAD_ADD
                    || keycode == Input.Keys.NUMPAD_SUBTRACT || keycode == Input.Keys.F9;
        }
    }
}
