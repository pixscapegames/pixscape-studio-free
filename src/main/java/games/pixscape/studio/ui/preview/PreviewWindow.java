package games.pixscape.studio.ui.preview;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneLoadHandle;
import games.pixscape.runtime.profiling.FrameSystemProfiler;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.optional.PhysicsMouseDragSystem;
import games.pixscape.studio.debug.StudioFrameProfiler;
import games.pixscape.studio.display.DisplayMetrics;
import games.pixscape.studio.logging.StudioLogLevel;
import java.nio.IntBuffer;
import games.pixscape.studio.ui.StudioStage;

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
    private final String sceneName;
    private final boolean forwardLighting;
    private PixscapeEngine engine;
    private JunctionOrbitDiagnostic junctionDiagnostic;
    private InteractiveShadowCapture interactiveCapture;
    private StudioFrameProfiler frameProfiler;
    private FrameSystemProfiler systemProfiler;
    private Stage uiStage;
    private PreviewInputAdapter previewInput;
    private RenderStatsOverlay statsOverlay;
    private PreviewLoadingUi loadingUi;
    private SceneLoadHandle sceneLoad;
    private StartupState startupState;
    private float loadingProgress;
    private OrthographicCamera worldCamera;
    private PhysicsMouseDragSystem dragSystem;
    private final DisplayMetrics displayMetrics = new DisplayMetrics();
    private boolean displayMetricsInitialized;
    private int pocCaptureFrames;
    private boolean pocCaptured;
    private final FrameTimePercentiles frameTimes = new FrameTimePercentiles(600);
    private final PreviewFrameCadence cadence = new PreviewFrameCadence();
    private static final long FRAME_STATS_REFRESH_NS = 250_000_000L; // 250ms
    private static final float CAMERA_PAN_SPEED_SCREEN = 450f; // visual screen speed
    private static final float CAMERA_DT_MAX = 1f / 30f;       // avoids large spikes
    private static final float CAMERA_ZOOM_SPEED = 1.5f; // zoom / seconde
    private static final float CAMERA_ZOOM_MIN = 0.2f;
    private static final float CAMERA_ZOOM_MAX = 10f;

    private static final boolean PREVIEW_PIXEL_SNAP = true;
    private static final float PREVIEW_PIXEL_SNAP_EPSILON = 0.0001f;
    private static final int POC_CAPTURE_FRAME = 120;
    private int pocBenchmarkFrame,pocBenchmarkCpuSamples,pocBenchmarkGpuSamples;
    private long pocBenchmarkCpuNs,pocBenchmarkGpuNs;
    private int[] pocBenchmarkQueries;
    private boolean[] pocBenchmarkQueryPending;
    private int[] pocBenchmarkQueryFrame;
    private int pocBenchmarkQueryIndex;
    private final IntBuffer pocBenchmarkQueryResult=BufferUtils.newIntBuffer(1);


    private Box2dWorldService box2d;


    public PreviewWindow(FileHandle userRootDir, String sceneName, boolean forwardLighting) {
        if (userRootDir == null) throw new GdxRuntimeException("userRootDir is null");
        if (sceneName == null || sceneName.isBlank()) throw new GdxRuntimeException("sceneName is blank");
        this.userRootDir = userRootDir;
        this.sceneName = sceneName;
        this.forwardLighting = forwardLighting;
    }

    @Override
    public void create() {
        junctionDiagnostic=JunctionOrbitDiagnostic.requested();
        Gdx.graphics.setForegroundFPS(0);
        Gdx.graphics.setContinuousRendering(true);
        PreviewDiagnosticLog.child("CADENCE_APPLIED",
                "configurationVsync=" + PreviewDesktopApplication.PREVIEW_VSYNC
                        + " requestedForegroundFps=0 continuousRendering="
                        + Gdx.graphics.isContinuousRendering());
        Gdx.app.log("PreviewCadence", "configurationVsync=" + PreviewDesktopApplication.PREVIEW_VSYNC
                + " foregroundFPS=0 continuousRendering=true");
        PreviewProcessProtocol.loading();
        refreshDisplayMetrics();
        loadingUi = new PreviewLoadingUi();
        startupState = StartupState.FIRST_FRAME;
        loadingProgress = 0f;
    }

    private void initializeProject() {
        interactiveCapture=new InteractiveShadowCapture(userRootDir,sceneName,forwardLighting);
        worldCamera = new OrthographicCamera();
        frameProfiler = StudioFrameProfiler.fromSystemProperties();
        systemProfiler = frameProfiler.createSystemProfiler();

        dragSystem = new PhysicsMouseDragSystem(worldCamera);
        dragSystem.setMaxForce(2000f);
        dragSystem.setFrequencyHz(5f);
        dragSystem.setDampingRatio(0.7f);
        dragSystem.setGrabRadiusMeters(0.25f);

        engine = new PixscapeEngine()
                .setForwardLighting(forwardLighting)
                .setWorldCamera(worldCamera)
                .setSystemProfiler(systemProfiler)
                .setConfigurationCustomizer(builder -> builder.with(dragSystem));
        StudioLogLevel.configure(engine);
        StudioLogLevel.setActivePreviewEngine(engine);
        engine.setPlatformTarget(PlatformTarget.DESKTOP_GL30);
        Gdx.app.log("PreviewWindow", "POC renderer="+(forwardLighting?"ForwardLightingRenderer":"LightingRenderer")
                +" runtime="+games.pixscape.runtime.render.lighting.LightingRenderer.class.getProtectionDomain().getCodeSource().getLocation());
        engine.loadProject(userRootDir);
        engine.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        loadingProgress = PROJECT_PROGRESS;
        sceneLoad = engine.beginLoadScene(sceneName);
    }

    private void initializePreview() {
        dragSystem.setLayerState(engine.getLayerState());

        applyPreviewNearestFiltering();
        box2d = engine.getBox2dWorldService();

        uiStage = new StudioStage(new ScreenViewport());
        previewInput = new PreviewInputAdapter();
        Gdx.input.setInputProcessor(new InputMultiplexer(
                engine.getHudInputProcessor(), previewInput, uiStage, dragSystem.inputProcessor()));

        resetFrameStatistics();
        statsOverlay = new RenderStatsOverlay(uiStage, engine.getRenderStats(), cadence,
                silhouetteCasterMode());
        if (Boolean.getBoolean("pixscape.poc.captureStats")) statsOverlay.toggleDetails();
        applyPocLightOverride();
        applyPocCasterOverride();
        focusPocCamera();

        // Preview is a top-level OS window; do not mark it floating/always-on-top.
        // It must yield normally when the user Alt+Tabs to another application.
    }

    @Override
    public void render() {
        // POC-only: freezes animation and simulation while preserving the renderer and manual camera controls.
        float dt = Boolean.getBoolean("pixscape.poc.freezeWorld") ? 0f : Gdx.graphics.getDeltaTime();
        if (startupState != StartupState.RUNNING) {
            renderStartup(dt);
            return;
        }

        handlePreviewCommands();

        long nowNs = System.nanoTime();
        frameTimes.onFrameStart(nowNs);
        cadence.onFrameStart(nowNs);

        // periodic percentile recompute
        if (frameTimes.computeIfDue(nowNs, FRAME_STATS_REFRESH_NS) && statsOverlay != null) {
            statsOverlay.setFrameTimes(frameTimes.p95Ms, frameTimes.p99Ms, frameTimes.maxMs);

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
        if(junctionDiagnostic!=null)junctionDiagnostic.before(engine);

        boolean benchmark=pocBenchmarkSamples()>0;long cpuStart=benchmark?System.nanoTime():0;boolean gpuTimer=benchmark&&beginPocBenchmarkGpuTimer();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        HdpiUtils.glViewport(0, 0, displayMetrics.logicalWidth(), displayMetrics.logicalHeight());
        try{
            engine.update(dt);
            if(interactiveCapture.pending())engine.render(() -> interactiveCapture.world(engine));
            else engine.render();
        }catch(games.pixscape.runtime.render.lighting.LightingAdmissionException refusal){
            pauseForAdmission(refusal);return;
        }
        if(gpuTimer)endPocBenchmarkGpuTimer();
        if(benchmark)recordPocBenchmark(System.nanoTime()-cpuStart);

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        if (statsOverlay != null && statsOverlay.isEnabled()) {
            uiStage.getViewport().apply(true);
            statsOverlay.render(nowNs);
        }
        capturePocFrame();
        if(junctionDiagnostic!=null)junctionDiagnostic.after(engine);
        interactiveCapture.after();


    }

    /** POC-only warm-up/sample loop. GPU queries are polled later, never waited on. */
    private static int pocBenchmarkSamples(){
        String raw=System.getProperty("pixscape.poc.benchmarkSamples");if(raw==null)return 0;
        try{return Math.max(0,Integer.parseInt(raw));}catch(NumberFormatException ignored){return 0;}
    }
    private static int pocBenchmarkWarmup(){
        String raw=System.getProperty("pixscape.poc.benchmarkWarmup","120");try{return Math.max(0,Integer.parseInt(raw));}catch(NumberFormatException ignored){return 120;}
    }
    private boolean beginPocBenchmarkGpuTimer(){
        if(Gdx.gl30==null||!Gdx.graphics.supportsExtension("GL_ARB_timer_query"))return false;
        if(pocBenchmarkQueries==null){pocBenchmarkQueries=new int[4];pocBenchmarkQueryPending=new boolean[4];pocBenchmarkQueryFrame=new int[4];Gdx.gl30.glGenQueries(4,pocBenchmarkQueries,0);}
        int slot=pocBenchmarkQueryIndex;
        if(pocBenchmarkQueryPending[slot]){pocBenchmarkQueryResult.clear();Gdx.gl30.glGetQueryObjectuiv(pocBenchmarkQueries[slot],GL30.GL_QUERY_RESULT_AVAILABLE,pocBenchmarkQueryResult);if(pocBenchmarkQueryResult.get(0)==0)return false;
            pocBenchmarkQueryResult.clear();Gdx.gl30.glGetQueryObjectuiv(pocBenchmarkQueries[slot],GL30.GL_QUERY_RESULT,pocBenchmarkQueryResult);
            int frame=pocBenchmarkQueryFrame[slot],warmup=pocBenchmarkWarmup(),samples=pocBenchmarkSamples();if(frame>warmup&&frame<=warmup+samples){pocBenchmarkGpuNs+=((long)pocBenchmarkQueryResult.get(0))&0xffffffffL;pocBenchmarkGpuSamples++;}pocBenchmarkQueryPending[slot]=false;}
        Gdx.gl30.glBeginQuery(0x88BF,pocBenchmarkQueries[slot]);return true;
    }
    private void endPocBenchmarkGpuTimer(){pocBenchmarkQueryPending[pocBenchmarkQueryIndex]=true;pocBenchmarkQueryFrame[pocBenchmarkQueryIndex]=pocBenchmarkFrame+1;Gdx.gl30.glEndQuery(0x88BF);pocBenchmarkQueryIndex=(pocBenchmarkQueryIndex+1)%4;}
    private void recordPocBenchmark(long cpuNs){
        int frame=++pocBenchmarkFrame,warmup=pocBenchmarkWarmup(),samples=pocBenchmarkSamples();
        if(frame>warmup&&frame<=warmup+samples){pocBenchmarkCpuNs+=cpuNs;pocBenchmarkCpuSamples++;}
        if(pocBenchmarkCpuSamples<samples)return;
        // Drain only already-completed deferred queries over a bounded tail; never block the CPU.
        if(pocBenchmarkQueries!=null&&pocBenchmarkGpuSamples<samples&&frame<=warmup+samples+16)return;
        String gpu=pocBenchmarkGpuSamples==0?"unavailable":String.format(java.util.Locale.ROOT,"%.6f",pocBenchmarkGpuNs/(double)pocBenchmarkGpuSamples/1_000_000d);
        Gdx.app.log("PreviewWindow","POC full benchmark: "+silhouetteCasterMode()+" samples="+samples+" cpuMs="+String.format(java.util.Locale.ROOT,"%.6f",pocBenchmarkCpuNs/(double)pocBenchmarkCpuSamples/1_000_000d)+" gpuMs="+gpu+" gpuSamples="+pocBenchmarkGpuSamples);
        Gdx.app.exit();
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
                        Throwable cause=sceneLoad.failure();
                        while(cause!=null){
                            if(cause instanceof games.pixscape.runtime.render.lighting.LightingAdmissionException refusal){pauseForAdmission(refusal);return;}
                            cause=cause.getCause();
                        }
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
                    PreviewProcessProtocol.ready();
                    return;
                case FAILED:
                    if(loadingUi!=null)loadingUi.render(dt,loadingProgress);
                    return;
                default:
                    throw new IllegalStateException("Unknown preview startup state: " + startupState);
            }
        } catch (games.pixscape.runtime.render.lighting.LightingAdmissionException refusal){
            pauseForAdmission(refusal);
        } catch (RuntimeException failure) {
            startupState = StartupState.FAILED;
            PreviewProcessProtocol.failed(failure);
            Gdx.app.error("PreviewWindow", "Preview startup failed.", failure);
            if (failure instanceof GdxRuntimeException runtimeFailure) throw runtimeFailure;
            throw new GdxRuntimeException("Preview startup failed.", failure);
        }
    }

    private void pauseForAdmission(games.pixscape.runtime.render.lighting.LightingAdmissionException refusal){
        if(interactiveCapture!=null)interactiveCapture.abort("Rendu refusé : "+refusal);
        startupState=StartupState.FAILED;
        PreviewProcessProtocol.failed(refusal);
        Gdx.app.error("PreviewWindow",refusal.getMessage(),refusal);
        if(loadingUi==null)loadingUi=new PreviewLoadingUi();
        loadingUi.showFailure(refusal.getMessage());
        Gdx.app.exit();
    }

    static float sceneProgress(float sceneProgress) {
        return PROJECT_PROGRESS + (1f - PROJECT_PROGRESS) * sceneProgress;
    }

    @Override
    public void pause() {
        if (previewInput != null) previewInput.cancelInput();
        if (dragSystem != null) dragSystem.cancelInput();
        resetFrameStatistics();
    }

    @Override
    public void resume() {
        resetFrameStatistics();
    }

    @Override
    public void resize(int width, int height) {
        refreshDisplayMetrics();
        if (loadingUi != null) loadingUi.resize(width, height);
        if (engine != null) engine.resize(width, height);
        if (uiStage != null) uiStage.getViewport().update(width, height, true);
    }

    private void refreshDisplayMetrics() {
        float previousScaleX = displayMetrics.scaleX();
        float previousScaleY = displayMetrics.scaleY();
        displayMetrics.update(
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight(),
                Gdx.graphics.getBackBufferWidth(),
                Gdx.graphics.getBackBufferHeight()
        );

        boolean scaleChanged = Math.abs(displayMetrics.scaleX() - previousScaleX) > 0.0001f
                || Math.abs(displayMetrics.scaleY() - previousScaleY) > 0.0001f;
        if (!displayMetricsInitialized || scaleChanged) {
            displayMetricsInitialized = true;
            Gdx.app.log("PreviewDisplay", String.format(
                    java.util.Locale.ROOT,
                    "logical=%dx%d framebuffer=%dx%d scale=%.2fx%.2f HiDPI=%s",
                    displayMetrics.logicalWidth(),
                    displayMetrics.logicalHeight(),
                    displayMetrics.framebufferWidth(),
                    displayMetrics.framebufferHeight(),
                    displayMetrics.scaleX(),
                    displayMetrics.scaleY(),
                    displayMetrics.isHiDpi() ? "yes" : "no"
            ));
        }
    }

    @Override
    public void dispose() {
        if(interactiveCapture!=null&&interactiveCapture.pending())interactiveCapture.restore();
        sceneLoad = null;
        if (dragSystem != null) dragSystem.cancelInput();
        if (pocBenchmarkQueries != null && Gdx.gl30 != null) {
            // The bounded benchmark is opt-in, but its four query objects are Preview-owned.
            // Release them explicitly when this GL context is still current.
            Gdx.gl30.glDeleteQueries(pocBenchmarkQueries.length, pocBenchmarkQueries, 0);
            pocBenchmarkQueries = null;
            pocBenchmarkQueryPending = null;
            pocBenchmarkQueryFrame = null;
        }
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

        if (engine != null) {
            StudioLogLevel.clearActivePreviewEngine(engine);
            engine.dispose();
            engine = null;
        }
        dragSystem = null;
    }

    /**
     * Direct control of the mono-camera runtime OrthographicCamera:
     * - arrow keys: movement
     * - +/-     : zoom in / out
     */
    private void handleCameraControls(float dt) {
        OrthographicCamera cam = engine.getCamera();
        if (cam == null) return;

        previewInput.synchronizeHudKeyboardFocus(engine.hasHudKeyboardFocus());

        float safeDt = Math.min(dt, CAMERA_DT_MAX);

        float dx = 0f;
        float dy = 0f;

        // Constant on-screen speed:
        // the larger the zoom (zoomed out), the more world units are traversed.
        float moveSpeed = CAMERA_PAN_SPEED_SCREEN * cam.zoom;

        if (previewInput.isPressed(Input.Keys.LEFT)) dx -= moveSpeed * safeDt;
        if (previewInput.isPressed(Input.Keys.RIGHT)) dx += moveSpeed * safeDt;
        if (previewInput.isPressed(Input.Keys.UP)) dy += moveSpeed * safeDt;
        if (previewInput.isPressed(Input.Keys.DOWN)) dy -= moveSpeed * safeDt;

        cam.position.x += dx;
        cam.position.y += dy;

        float zoomDelta = 0f;
        if (previewInput.isPressed(Input.Keys.PLUS) || previewInput.isPressed(Input.Keys.EQUALS)) {
            zoomDelta -= CAMERA_ZOOM_SPEED * safeDt;
        }
        if (previewInput.isPressed(Input.Keys.MINUS)) {
            zoomDelta += CAMERA_ZOOM_SPEED * safeDt;
        }
        if (previewInput.isPressed(Input.Keys.NUMPAD_ADD)) {
            zoomDelta -= CAMERA_ZOOM_SPEED * safeDt;
        }
        if (previewInput.isPressed(Input.Keys.NUMPAD_SUBTRACT)) {
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

    private void handlePreviewCommands() {
        int command;
        while ((command = previewInput.consumeCommand()) != -1) {
            if (command == Input.Keys.F9) {
                if (statsOverlay.isEnabled()) {
                    statsOverlay.setEnabled(false);
                } else {
                    statsOverlay.setEnabled(true);
                }
            } else if (command == Input.Keys.F10) {
                statsOverlay.toggleDetails();
            } else if (command == Input.Keys.F8) {
                interactiveCapture.request();
            }
        }
    }

    private void resetFrameStatistics() {
        frameTimes.reset();
        cadence.reset();
        if (statsOverlay != null) {
            statsOverlay.setFrameTimes(0, 0, 0);
            statsOverlay.refresh();
        }
    }

    static final class PreviewInputAdapter extends InputAdapter {
        private final com.badlogic.gdx.utils.IntSet pressed = new com.badlogic.gdx.utils.IntSet();
        private final com.badlogic.gdx.utils.IntSet heldCommands = new com.badlogic.gdx.utils.IntSet();
        private final com.badlogic.gdx.utils.IntArray commands = new com.badlogic.gdx.utils.IntArray(4);
        private int nextCommand;

        @Override public boolean keyDown(int keycode) {
            if (!isPreviewKey(keycode)) return false;
            if (isCommand(keycode)) {
                if (heldCommands.add(keycode)) commands.add(keycode);
            }
            else pressed.add(keycode);
            return true;
        }

        @Override public boolean keyUp(int keycode) {
            if (!isPreviewKey(keycode)) return false;
            pressed.remove(keycode);
            heldCommands.remove(keycode);
            return true;
        }

        boolean isPressed(int keycode) {
            if (!Gdx.input.isKeyPressed(keycode)) pressed.remove(keycode);
            return pressed.contains(keycode);
        }

        void synchronizeHudKeyboardFocus(boolean focused) {
            if (focused) pressed.clear();
        }

        int consumeCommand() {
            if (nextCommand < commands.size) return commands.get(nextCommand++);
            commands.clear(); nextCommand = 0;
            return -1;
        }

        void cancelInput() {
            pressed.clear(); heldCommands.clear(); commands.clear(); nextCommand = 0;
        }

        private static boolean isCommand(int keycode) {
            return keycode == Input.Keys.F8 || keycode == Input.Keys.F9 || keycode == Input.Keys.F10;
        }

        private static boolean isPreviewKey(int keycode) {
            return keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT
                    || keycode == Input.Keys.UP || keycode == Input.Keys.DOWN
                    || keycode == Input.Keys.PLUS || keycode == Input.Keys.EQUALS
                    || keycode == Input.Keys.MINUS || keycode == Input.Keys.NUMPAD_ADD
                    || keycode == Input.Keys.NUMPAD_SUBTRACT || isCommand(keycode);
        }
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

    private String silhouetteCasterMode() {
        World world = engine != null ? engine.getWorld() : null;
        games.pixscape.runtime.system.RenderSubmitSystem submit=world==null?null:world.getSystem(games.pixscape.runtime.system.RenderSubmitSystem.class);
        return "Caster: volume + alpha | self-shadow: "
                +(submit!=null&&submit.lightingRenderer().twoDepthSelfShadowExclusionActive()?"d1/id1+d2":"inactive/reference")
                +" | renderer: "+(forwardLighting?"forward":"legacy");
    }

    private void capturePocFrame() {
        String directory = System.getProperty("pixscape.poc.captureDir");
        if (pocCaptured || directory == null || directory.isBlank()
                || ++pocCaptureFrames < POC_CAPTURE_FRAME) return;
        String name = System.getProperty("pixscape.poc.captureName", "preview.png");
        if (!name.endsWith(".png")) name += ".png";
        try {
            FileHandle output = Gdx.files.absolute(directory).child(name);
            output.parent().mkdirs();
            Pixmap pixels = Pixmap.createFromFrameBuffer(
                    0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            try {
                Pixmap upright = new Pixmap(pixels.getWidth(), pixels.getHeight(), pixels.getFormat());
                try {
                    for (int y = 0; y < pixels.getHeight(); y++) {
                        for (int x = 0; x < pixels.getWidth(); x++) {
                            upright.drawPixel(x, y, pixels.getPixel(x, pixels.getHeight() - 1 - y));
                        }
                    }
                    PixmapIO.writePNG(output, upright);
                } finally {
                    upright.dispose();
                }
            } finally {
                pixels.dispose();
            }
            pocCaptured = true;
            Gdx.app.log("PreviewWindow", "POC capture written: " + output.path());
            logPocTiledSelfExclusion();
            logPocTwoDepthSelfExclusion();
        } catch (RuntimeException failure) {
            Gdx.app.error("PreviewWindow", "POC capture failed; Preview remains usable.", failure);
            pocCaptured = true;
        }
        if (Boolean.getBoolean("pixscape.poc.exitAfterCapture")) Gdx.app.exit();
    }

    /** Captures the scoped diagnostic cost after the rendered frame; normal Preview remains untouched. */
    private void logPocTiledSelfExclusion() {
        String block = System.getProperty("pixscape.poc.excludeTiledSelfShadowBlockId");
        if (block == null || engine == null || engine.getWorld() == null) return;
        games.pixscape.runtime.system.RenderSubmitSystem submit = engine.getWorld()
                .getSystem(games.pixscape.runtime.system.RenderSubmitSystem.class);
        if (submit == null) return;
        games.pixscape.runtime.render.lighting.LightingRenderer lighting = submit.lightingRenderer();
        Gdx.app.log("PreviewWindow", "POC tiled self exclusion: block=" + block
                + " extraShadowCpuNs=" + lighting.pocTiledSelfExclusionCpuNs
                + " extraShadowDraws=" + lighting.pocTiledSelfExclusionDraws
                + " lightingTextureBytes=" + lighting.textureBytes());
    }

    private void logPocTwoDepthSelfExclusion() {
        if (engine == null || engine.getWorld() == null) return;
        games.pixscape.runtime.system.RenderSubmitSystem submit = engine.getWorld()
                .getSystem(games.pixscape.runtime.system.RenderSubmitSystem.class);
        if (submit == null) return;
        games.pixscape.runtime.render.lighting.LightingRenderer lighting = submit.lightingRenderer();
        Gdx.app.log("PreviewWindow", "POC two-depth self exclusion: extraShadowCpuNs="
                + lighting.twoDepthShadowCpuNs + " extraShadowDraws=" + lighting.twoDepthShadowDraws
                + " extraShadowUploads=" + lighting.twoDepthShadowUploads
                + " extraShadowUploadBytes=" + lighting.twoDepthShadowUploadedBytes
                + " deferredGpuNs=" + lighting.twoDepthShadowGpuNs
                + " lightingTextureBytes=" + lighting.textureBytes());
    }

    private void focusPocCamera() {
        String rawX = System.getProperty("pixscape.poc.focusx");
        String rawY = System.getProperty("pixscape.poc.focusy");
        if (rawX == null || rawY == null || worldCamera == null) return;
        try {
            worldCamera.position.set(Float.parseFloat(rawX), Float.parseFloat(rawY), 0f);
            String rawZoom = System.getProperty("pixscape.poc.focuszoom");
            if (rawZoom != null) worldCamera.zoom = Float.parseFloat(rawZoom);
            worldCamera.update();
        } catch (NumberFormatException failure) {
            Gdx.app.error("PreviewWindow", "Ignoring invalid POC camera focus.", failure);
        }
    }

    /**
     * Capture-only pose override: it exists solely to reproduce a lighting observation.
     * It mutates the transient Preview ECS world after the scene has loaded; it never writes
     * the copied project, Studio preferences, or the authored light transform.
     */
    private void applyPocLightOverride() {
        String rawId = System.getProperty("pixscape.poc.lightStableId");
        String rawX = System.getProperty("pixscape.poc.lightX");
        String rawY = System.getProperty("pixscape.poc.lightY");
        String rawHeight = System.getProperty("pixscape.poc.lightHeight");
        String rawResolution = System.getProperty("pixscape.poc.lightResolution");
        if (rawId == null || (rawX == null && rawY == null && rawHeight == null) || engine == null) return;
        try {
            int stableId = Integer.parseInt(rawId);
            World world = engine.getWorld();
            int entity = engine.findEntityByStableId(stableId);
            if (entity < 0 || world == null) throw new IllegalArgumentException("light stable id not found: " + stableId);
            TransformComponent transform = world.getMapper(TransformComponent.class).getSafe(entity, null);
            PointLightComponent light = world.getMapper(PointLightComponent.class).getSafe(entity, null);
            if (transform == null || light == null) throw new IllegalArgumentException("entity is not a point light: " + stableId);
            boolean moved = false;
            if (rawX != null) { transform.x = Float.parseFloat(rawX); moved = true; }
            if (rawY != null) { transform.y = Float.parseFloat(rawY); moved = true; }
            if (rawHeight != null) light.height = Float.parseFloat(rawHeight);
            if (rawResolution != null) light.shadowResolution = Integer.parseInt(rawResolution);
            if (moved) {
                DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
                if (dirty != null) dirty.geometry(entity, GeometryDirty.POSITION);
            }
            Gdx.app.log("PreviewWindow", "POC transient light pose: stableId=" + stableId
                    + " screenX=" + transform.x + " screenY=" + transform.y + " height=" + light.height+" resolution="+light.shadowResolution);
        } catch (RuntimeException invalid) {
            Gdx.app.error("PreviewWindow", "Ignoring invalid transient POC light pose; authored scene remains unchanged.", invalid);
        }
    }

    /** Moves an existing caster only in the loaded Preview ECS for the d2 exterior-occluder witness. */
    private void applyPocCasterOverride() {
        String rawId=System.getProperty("pixscape.poc.casterStableId"),rawX=System.getProperty("pixscape.poc.casterX"),rawY=System.getProperty("pixscape.poc.casterY");
        if(rawId==null||(rawX==null&&rawY==null)||engine==null)return;
        try {
            int stableId=Integer.parseInt(rawId),entity=engine.findEntityByStableId(stableId);World world=engine.getWorld();
            if(entity<0||world==null)throw new IllegalArgumentException("caster stable id not found: "+stableId);
            TransformComponent transform=world.getMapper(TransformComponent.class).getSafe(entity,null);
            if(transform==null)throw new IllegalArgumentException("caster has no transform: "+stableId);
            if(rawX!=null)transform.x=Float.parseFloat(rawX);if(rawY!=null)transform.y=Float.parseFloat(rawY);
            DirtyTrackerSystem dirty=world.getSystem(DirtyTrackerSystem.class);if(dirty!=null)dirty.geometry(entity,GeometryDirty.POSITION);
            Gdx.app.log("PreviewWindow","POC transient exterior caster: stableId="+stableId+" screenX="+transform.x+" screenY="+transform.y);
        } catch(RuntimeException invalid){Gdx.app.error("PreviewWindow","Ignoring invalid transient exterior caster; authored scene remains unchanged.",invalid);}
    }

}
