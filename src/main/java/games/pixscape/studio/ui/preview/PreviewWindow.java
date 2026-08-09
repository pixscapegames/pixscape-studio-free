package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;
import games.pixscape.runtime.profiling.FrameSystemProfiler;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.optional.PhysicsMouseDragSystem;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.debug.StudioFrameProfiler;
import games.pixscape.studio.logging.StudioLogLevel;

public final class PreviewWindow extends ApplicationAdapter {

    private static final float PROJECT_PROGRESS = 0.15f;

    private enum StartupState {
        FIRST_FRAME,
        PROJECT,
        SCENE,
        READY,
        RUNNING,
        FAILED
    }

    private final FileHandle userRootDir;
    private PixscapeEngine engine;
    private StudioFrameProfiler frameProfiler;
    private FrameSystemProfiler systemProfiler;
    private Stage uiStage;
    private SpriteBatch uiBatch;
    private RenderStatsOverlay statsOverlay;
    private PreviewLoadingUi loadingUi;
    private SceneLoadHandle sceneLoad;
    private StartupState startupState;
    private float loadingProgress;
    private OrthographicCamera worldCamera;
    private PhysicsMouseDragSystem dragSystem;
    private boolean benchMode = false;
    private final FrameTimePercentiles frameTimes = new FrameTimePercentiles(600);
    private static final long FRAME_STATS_REFRESH_NS = 250_000_000L; // 250ms
    private static final boolean PREVIEW_VSYNC_NORMAL = true;

    private static final float CAMERA_PAN_SPEED_SCREEN = 450f; // visual screen speed
    private static final float CAMERA_DT_MAX = 1f / 30f;       // avoids large spikes
    private static final float CAMERA_ZOOM_SPEED = 1.5f; // zoom / seconde
    private static final float CAMERA_ZOOM_MIN = 0.2f;
    private static final float CAMERA_ZOOM_MAX = 10f;

    private static final boolean PREVIEW_PIXEL_SNAP = true;
    private static final float PREVIEW_PIXEL_SNAP_EPSILON = 0.0001f;


    private Box2dWorldService box2d;


    public PreviewWindow(FileHandle userRootDir) {
        if (userRootDir == null) throw new GdxRuntimeException("userRootDir is null");
        this.userRootDir = userRootDir;
    }

    @Override
    public void create() {
        loadingUi = new PreviewLoadingUi();
        startupState = StartupState.FIRST_FRAME;
        loadingProgress = 0f;
    }

    private void initializeProject() {
        worldCamera = new OrthographicCamera();
        frameProfiler = StudioFrameProfiler.fromSystemProperties();
        systemProfiler = frameProfiler.createSystemProfiler();

        dragSystem = new PhysicsMouseDragSystem(worldCamera);
        dragSystem.setMaxForce(2000f);
        dragSystem.setFrequencyHz(5f);
        dragSystem.setDampingRatio(0.7f);
        dragSystem.setGrabRadiusMeters(0.25f);

        engine = new PixscapeEngine()
                .setWorldCamera(worldCamera)
                .setSystemProfiler(systemProfiler)
                .setConfigurationCustomizer(builder -> builder.with(dragSystem));
        StudioLogLevel.configure(engine);
        StudioLogLevel.setActivePreviewEngine(engine);
        engine.setPlatformTarget(PlatformTarget.DESKTOP_GL30);
        engine.loadProject(userRootDir);
        engine.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        loadingProgress = PROJECT_PROGRESS;
        sceneLoad = engine.beginLoadScene(ProjectConfig.getInstance().getCurrentSceneName());
    }

    private void initializePreview() {
        dragSystem.setLayerState(engine.getLayerState());

        applyPreviewNearestFiltering();
        box2d = engine.getBox2dWorldService();

        uiBatch = new SpriteBatch();
        uiStage = new Stage(new ScreenViewport(), uiBatch);

        statsOverlay = new RenderStatsOverlay(uiStage, engine.getRenderStats());

        benchMode = false;
        Gdx.graphics.setVSync(PREVIEW_VSYNC_NORMAL);
        // Preview is a top-level OS window; do not mark it floating/always-on-top.
        // It must yield normally when the user Alt+Tabs to another application.
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        if (startupState != StartupState.RUNNING) {
            renderStartup(dt);
            return;
        }

        handleBenchToggle();

        long nowNs = System.nanoTime();
        frameTimes.onFrameStart(nowNs);

        // periodic percentile recompute
        if (frameTimes.computeIfDue(nowNs, FRAME_STATS_REFRESH_NS) && statsOverlay != null) {
            statsOverlay.setFrameTimes(frameTimes.avgMs, frameTimes.p95Ms, frameTimes.p99Ms, frameTimes.maxMs);

            if (box2d != null) {
                statsOverlay.setBox2dStats(
                        box2d.lastStepTimeNs / 1_000_000.0,
                        box2d.lastSubsteps,
                        box2d.bodyCount,
                        box2d.contactCount,
                        box2d.jointCount
                );
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
                    loadingUi.render(dt, loadingProgress);
                    startupState = StartupState.PROJECT;
                    return;
                case PROJECT:
                    initializeProject();
                    startupState = StartupState.SCENE;
                    loadingUi.render(dt, loadingProgress);
                    return;
                case SCENE:
                    sceneLoad.update();
                    loadingProgress = sceneProgress(sceneLoad.progress());
                    if (sceneLoad.isFailed()) {
                        startupState = StartupState.FAILED;
                        throw new GdxRuntimeException("Desktop preview scene loading failed.",
                                sceneLoad.failure());
                    }
                    if (sceneLoad.isReady()) {
                        loadingProgress = 1f;
                        startupState = StartupState.READY;
                    }
                    loadingUi.render(dt, loadingProgress);
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
                    throw new IllegalStateException("Unknown preview startup state: " + startupState);
            }
        } catch (Throwable failure) {
            startupState = StartupState.FAILED;
            Gdx.app.error("PreviewWindow", "Preview startup failed.", failure);
            if (failure instanceof GdxRuntimeException runtimeFailure) throw runtimeFailure;
            throw new GdxRuntimeException("Preview startup failed.", failure);
        }
    }

    static float sceneProgress(float sceneProgress) {
        return PROJECT_PROGRESS + (1f - PROJECT_PROGRESS) * sceneProgress;
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

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
            StudioLogLevel.clearActivePreviewEngine(engine);
            engine.dispose();
            engine = null;
        }
        PreviewLauncher.notifyClosed();
    }

    /**
     * Direct control of the mono-camera runtime OrthographicCamera:
     * - arrow keys: movement
     * - +/-     : zoom in / out
     */
    private void handleCameraControls(float dt) {
        OrthographicCamera cam = engine.getCamera();
        if (cam == null) return;

        float safeDt = Math.min(dt, CAMERA_DT_MAX);

        float dx = 0f;
        float dy = 0f;

        // Constant on-screen speed:
        // the larger the zoom (zoomed out), the more world units are traversed.
        float moveSpeed = CAMERA_PAN_SPEED_SCREEN * cam.zoom;

        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx -= moveSpeed * safeDt;
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += moveSpeed * safeDt;
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) dy += moveSpeed * safeDt;
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy -= moveSpeed * safeDt;

        cam.position.x += dx;
        cam.position.y += dy;

        float zoomDelta = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.PLUS) || Gdx.input.isKeyPressed(Input.Keys.EQUALS)) {
            zoomDelta -= CAMERA_ZOOM_SPEED * safeDt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.MINUS)) {
            zoomDelta += CAMERA_ZOOM_SPEED * safeDt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.NUMPAD_ADD)) {
            zoomDelta -= CAMERA_ZOOM_SPEED * safeDt;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.NUMPAD_SUBTRACT)) {
            zoomDelta += CAMERA_ZOOM_SPEED * safeDt;
        }

        if (zoomDelta != 0f) {
            float newZoom = cam.zoom + zoomDelta;
            if (newZoom < CAMERA_ZOOM_MIN) newZoom = CAMERA_ZOOM_MIN;
            if (newZoom > CAMERA_ZOOM_MAX) newZoom = CAMERA_ZOOM_MAX;
            cam.zoom = newZoom;
        }

        cam.update();
    }

    private void handleBenchToggle() {
        if (!Gdx.input.isKeyJustPressed(Input.Keys.F9)) return;

        benchMode = !benchMode;

        if (statsOverlay != null) statsOverlay.setEnabled(benchMode);

        frameTimes.reset();

        // Preview : bench => VSync OFF, sinon PREVIEW_VSYNC_NORMAL
        boolean previewVsync = !benchMode && PREVIEW_VSYNC_NORMAL;
        Gdx.graphics.setVSync(previewVsync);

        // Studio: bench => VSync OFF (otherwise Studio can cap the preview)
        PreviewLauncher.setStudioVSync(!benchMode);
    }

    private void snapCameraToPixelGrid(OrthographicCamera cam) {
        if (cam == null || !PREVIEW_PIXEL_SNAP) return;

        float step = cameraWorldUnitsPerScreenPixel(cam);
        if (step <= PREVIEW_PIXEL_SNAP_EPSILON) return;

        cam.position.x = snap(cam.position.x, step);
        cam.position.y = snap(cam.position.y, step);
    }

    private static float cameraWorldUnitsPerScreenPixel(OrthographicCamera cam) {
        return cam.zoom;
    }

    private static float snap(float value, float step) {
        return Math.round(value / step) * step;
    }

    private void applyPreviewNearestFiltering() {
        if (engine == null) return;

        var atlasService = engine.getAtlasRuntimeService();
        if (atlasService == null) return;

        var tags = atlasService.listTags();
        for (int i = 0; i < tags.size; i++) {
            var atlas = atlasService.getAtlas(tags.get(i));
            if (atlas == null) continue;

            var textures = games.pixscape.runtime.service.AtlasRuntimeService.getPageTextures(atlas);
            for (int t = 0; t < textures.size; t++) {
                textures.get(t).setFilter(
                        Texture.TextureFilter.Nearest,
                        Texture.TextureFilter.Nearest
                );
            }

            var bundle = atlasService.bundle(tags.get(i));
            if (bundle != null && bundle.textureArray != null) {
                bundle.textureArray.setFilter(
                        Texture.TextureFilter.Nearest,
                        Texture.TextureFilter.Nearest
                );
            }
        }
    }

}
