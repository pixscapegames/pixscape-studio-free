package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.batch.MeshBatchStudio;
import games.pixscape.studio.batch.TextureArrayMeshBatchStudio;
import games.pixscape.studio.configuration.SceneMeta;

public final class StudioRenderSubmitSystem extends BaseSystem implements ProfiledSystem, AmbientContributionRenderer.MaterialReplay {
    private static final int MAX_REPEAT_DRAWS_PER_SLOT = 1024;
    private static final float AXIS_EPSILON = 0.0001f;

    private final LayerStateSOA layerState;
    private final FrameRenderQueue frameQueue;
    private final OrthographicCamera cam;
    private final MetricsBatch metricsBatch;
    private final MeshBatchStudio standaloneBatch;
    private final RenderStats stats;
    private final RenderStatsSink statsSink;
    private final WorldLightComposition composition = new WorldLightComposition();
    private final AmbientContributionRenderer ambientContribution = new AmbientContributionRenderer();
    private final games.pixscape.runtime.render.lighting.LightingRenderer lighting =
            new games.pixscape.runtime.render.lighting.LightingRenderer();
    private StudioForwardLightingRenderer forward;
    private DynamicEntityRenderState dynamicDomain;
    private TiledMapRenderState tiledDomain;
    private LightParameterBinding lightParameters;
    private boolean frameSubmissionAllowed;
    private float time = 0f;
    private boolean rendererReported;
    private SceneMeta sceneSettings;
    public StudioRenderSubmitSystem setSceneSettings(SceneMeta scene){sceneSettings=scene;return this;}
    public StudioRenderSubmitSystem setRenderDomains(DynamicEntityRenderState dynamic,TiledMapRenderState tiled){
        dynamicDomain=dynamic;tiledDomain=tiled;return this;
    }
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private final int[] repeatRange = new int[4];


    public StudioRenderSubmitSystem(LayerStateSOA layerState,
                                    FrameRenderQueue frameQueue,
                                    OrthographicCamera cam,
                                    MetricsBatch batch,
                                    RenderStats stats,
                                    RenderStatsSink statsSink) {
        this(layerState,frameQueue,cam,batch,stats,statsSink,false);
    }
    public StudioRenderSubmitSystem(LayerStateSOA layerState,FrameRenderQueue frameQueue,OrthographicCamera cam,
                                    MetricsBatch batch,RenderStats stats,RenderStatsSink statsSink,boolean forwardLighting) {
        this.forward=forwardLighting?new StudioForwardLightingRenderer():null;
        this.layerState = layerState;
        this.frameQueue = frameQueue;
        this.cam = cam;
        this.metricsBatch = batch;
        this.stats = stats;
        this.statsSink = statsSink;
        this.standaloneBatch = new MeshBatchStudio(2048);
    }

    @Override
    protected void initialize() { lightParameters = new LightParameterBinding(world); }

    @Override
    protected boolean checkProcessing() { return frameSubmissionAllowed; }

    @Override
    protected void begin() {
        time += world.getDelta();
        cam.update();
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.STUDIO_RENDER_SUBMIT);
            try {
                render();
            } finally {
                profiler.end(SystemProfilePhases.STUDIO_RENDER_SUBMIT, startNs);
            }
        } else {
            render();
        }
    }

    @Override
    protected void end() {
        statsSink.accumulate(stats, Gdx.graphics.getDeltaTime());
    }

    public void prepareComposition() {
        SceneMeta meta=sceneSettings;
        prepareRendererSelection(meta==null?forward!=null:meta.forwardLighting,meta==null || meta.useNormalMaps);
    }

    /** Command-time validation/preparation also protects UI changes before publishing the choice. */
    public void prepareRendererSelection(boolean requested,boolean useNormalMaps) {
        lighting.setUseNormalMaps(useNormalMaps);
        if(requested){
            if(!(metricsBatch instanceof TextureArrayMeshBatchStudio arrayBatch))throw new IllegalArgumentException("Forward lighting requires the Studio color texture-array batch");
            boolean changed=forward==null;
            StudioForwardLightingRenderer candidate=changed?new StudioForwardLightingRenderer():forward;
            candidate.setRenderDomains(dynamicDomain,tiledDomain);
            try {candidate.setUseNormalMaps(useNormalMaps);candidate.prepare(world,arrayBatch.getBundle());lighting.prepareShadows(world);}
            catch(RuntimeException|Error failure){if(changed)candidate.dispose();throw failure;}
            forward=candidate;if(changed)rendererReported=false;
        }else{
            composition.prepare();ambientContribution.prepare();lighting.prepare(world,composition);
            if(forward!=null){forward.dispose();forward=null;rendererReported=false;}
        }
        if (!rendererReported) {
            Gdx.app.log("Lighting", "renderer=" + (forward != null ? "forward" : "legacy")
                    + " prepared (Studio; no automatic fallback)");
            rendererReported = true;
        }
    }
    public games.pixscape.runtime.render.lighting.ForwardLightingRenderer forwardRenderer(){return forward;}
    public games.pixscape.runtime.render.lighting.LightingRenderer lightingRenderer(){return lighting;}

    /** Scoped by WorldCanvas only around its draw-time World processing. */
    public void beginFrameSubmission() { frameSubmissionAllowed = true; }
    public void endFrameSubmission() { frameSubmissionAllowed = false; }

    @Override protected void dispose() { ambientContribution.dispose(); lighting.dispose(); composition.dispose();if(forward!=null)forward.dispose(); standaloneBatch.close(); }

    private void render() {
        stats.forwardLighting=forward!=null;
        frameSubmissionAllowed = false; // At most one submission inside the canvas scope.
        if(forward!=null){
            SceneMeta meta=sceneSettings;
            forward.render(frameQueue,layerState,cam,lighting,meta==null?1:meta.ambientMulR,meta==null?1:meta.ambientMulG,meta==null?1:meta.ambientMulB,stats);return;
        }
        Throwable failure = null;
        try {
            ambientContribution.beginFrame(frameQueue);
            composition.beginOriginal();
            if(frameQueue.lighting.timer!=null)frameQueue.lighting.timer.begin(0);
            int drawsBefore=stats.drawCalls;
            renderPass(LightCompositionPass.ORIGINAL, 0);
            stats.colorDraws+=stats.drawCalls-drawsBefore;
            if(frameQueue.lighting.timer!=null)frameQueue.lighting.timer.end(0);
            lighting.render(frameQueue, layerState, cam, composition, stats);
            drawsBefore=stats.drawCalls;
            if (frameQueue.lighting.diagnostic == 0)
                ambientContribution.render(frameQueue, cam, composition, stats, this);
            stats.ambientDraws+=stats.drawCalls-drawsBefore;
            if(frameQueue.lighting.timer!=null)frameQueue.lighting.timer.begin(4);
            SceneMeta meta = sceneSettings;
            boolean normal=frameQueue.lighting.diagnostic==0;
            composition.compose(normal?(meta != null ? meta.ambientMulR : 1f):0,
                    normal?(meta != null ? meta.ambientMulG : 1f):0, normal?(meta != null ? meta.ambientMulB : 1f):0, stats);
            if(frameQueue.lighting.timer!=null)frameQueue.lighting.timer.end(4);
        } catch (RuntimeException | Error error) {
            failure = error;
            metricsBatch.abort(error);
            standaloneBatch.abort(error);
            ambientContribution.abort();
            lighting.abort();
            throw error;
        } finally { composition.end(failure); }
    }

    private void renderPass(int pass, int first) {
        renderPass(pass, first, frameQueue.size);
    }
    @Override public void replayAmbientMaterials(int first, int end) {
        renderPass(LightCompositionPass.AMBIENT, first, end);
    }
    private void renderPass(int pass, int first, int end) {
        cam.update();

        AtlasRuntimeService.TextureArrayBundle activeBundle = null;
        if (metricsBatch instanceof TextureArrayMeshBatchStudio taBatch) {
            activeBundle = taBatch.getBundle();
        }

        int size = end;
        final boolean hasLayerMeta = layerState.maxLayerIndex() >= 0;
        final ShaderProgram standardAtlasShader = ShaderRegistry.get(ShaderMode.TEXTURE_ARRAY.defaultShaderName());

        boolean atlasOpen = false;
        boolean standaloneOpen = false;

        ShaderProgram curShader = null;
        int curShaderIdx = -1;
        int curBlendId = Integer.MIN_VALUE;
        float curPackedColor = Float.NaN;
        ShaderProgram standaloneShader = null;

        for (int i = first; i < size; i++) {
            boolean light = frameQueue.light[i] != 0;
            if (LightCompositionPass.skip(pass, light, frameQueue.blend[i])) continue;
            int layerIdx = frameQueue.layerIndex[i];
            if (hasLayerMeta) {
                if (layerIdx < 0 || layerIdx >= layerState.enabled.length) continue;
                if (layerIdx > layerState.maxLayerIndex() || !layerState.enabled[layerIdx]) continue;
            }

            final int texHandle = frameQueue.textureHandle[i];
            if (texHandle == 0) continue;

            boolean inAtlas = activeBundle != null && activeBundle.handle2layer.containsKey(texHandle);

            // Keep admission for E without breaking a compatible original-image batch.
            if (pass == LightCompositionPass.ORIGINAL && AmbientContributionRenderer.isIdentityOriginal(frameQueue, i)) {
                ShaderProgram standard = inAtlas ? standardAtlasShader : ShaderRegistry.get(ShaderMode.TEXTURE_2D.defaultShaderName());
                ShaderProgram actual = inAtlas ? ShaderRegistry.getByIdx(frameQueue.shader[i]) : standard;
                if (actual == null) actual = standard;
                if (standard != null && actual == standard && (inAtlas || TextureRegistry.getByHandle(texHandle) != null)) {
                    AmbientContributionRenderer.admit(frameQueue, i, actual, standard);
                    continue;
                }
            }

            if (inAtlas) {
                if (standaloneOpen) {
                    standaloneBatch.end(stats);
                    standaloneOpen = false;
                }

                if (!atlasOpen) {
                    metricsBatch.begin(cam.combined, stats);
                    atlasOpen = true;
                    curShaderIdx = -1;
                    curShader = null;
                    curBlendId = Integer.MIN_VALUE;
                    curPackedColor = Float.NaN;
                }

                final int shaderIdx = frameQueue.shader[i];
                if (shaderIdx != curShaderIdx) {
                    curShaderIdx = shaderIdx;
                    ShaderProgram sh = ShaderRegistry.getByIdx(shaderIdx);
                    if (sh == null) {
                        sh = standardAtlasShader;
                    }

                    if (sh != curShader) {
                        metricsBatch.setShader(sh, stats);
                        curShader = sh;

                        if (curShader != null) {
                            setUniform1f(curShader, "u_time", time);
                            setUniformLayerOffset(curShader);
                            setUniformAmbientMul(curShader);
                        }
                    }
                    metricsBatch.setParameterLayout(ShaderRegistry.getParameterLayout(shaderIdx), stats);
                    curBlendId = Integer.MIN_VALUE;
                }

                final int blendId = frameQueue.blend[i];
                if (pass == LightCompositionPass.ORIGINAL)
                    AmbientContributionRenderer.admit(frameQueue, i, curShader, standardAtlasShader);
                int materialPass = AmbientContributionRenderer.materialPass(pass, frameQueue, i);
                if (LightCompositionPass.skip(materialPass, light, blendId)) continue;
                int blendKey = blendId + (light ? 16 : 0) + materialPass * 32;
                if (blendKey != curBlendId) {
                    int flushesBeforeBlend = stats.flushes;
                    metricsBatch.flush(stats);
                    if (stats.flushes > flushesBeforeBlend) stats.flushStateChanges++;
                    LightCompositionPass.apply(materialPass, light, blendId, metricsBatch, curShader, stats);
                    curBlendId = blendKey;
                }

                float packedColor = AmbientContributionRenderer.originalColor(frameQueue, i, pass);
                if (packedColor != curPackedColor) {
                    metricsBatch.setPackedColor(packedColor);
                    curPackedColor = packedColor;
                }

                ShaderParamsComponent params = entityParameters(i);
                metricsBatch.setEntityParameters(light ? lightParameters.bind(frameQueue.sourceEntity[i],
                        ShaderRegistry.getParameterLayout(shaderIdx), params == null ? null : params.floats)
                        : params == null ? null : params.floats, stats);

                byte repeat = frameQueue.repeatFlags[i];
                if ((repeat & RenderRepeatFlags.ANY) == 0) {
                    drawAtlasEntry(i, texHandle);
                } else {
                    drawRepeatedAtlasEntry(i, texHandle, repeat);
                }
                continue;
            }

            if (atlasOpen) {
                metricsBatch.end(stats);
                atlasOpen = false;
            }

            Texture tex = TextureRegistry.getByHandle(texHandle);
            if (tex == null) continue;
            // An unpacked texture uses the temporary 2D preview shader until atlas repack.
            // Its material shader and parameter table become active on the atlas path above.

            final int blendId = frameQueue.blend[i];
            if (!standaloneOpen) {
                standaloneBatch.begin(cam.combined, stats);
                curBlendId = Integer.MIN_VALUE;
                standaloneShader = ShaderRegistry.get(ShaderMode.TEXTURE_2D.defaultShaderName());
                if (standaloneShader == null) {
                    throw new IllegalStateException("Missing standalone shader: "
                            + ShaderMode.TEXTURE_2D.defaultShaderName());
                }

                standaloneBatch.setShader(standaloneShader, stats);
                setUniformAmbientMul(standaloneShader);
                standaloneOpen = true;
            }

            if (pass == LightCompositionPass.ORIGINAL)
                AmbientContributionRenderer.admit(frameQueue, i, standaloneShader, standaloneShader);
            int materialPass = AmbientContributionRenderer.materialPass(pass, frameQueue, i);
            if (LightCompositionPass.skip(materialPass, light, blendId)) continue;
            int blendKey = blendId + (light ? 16 : 0) + materialPass * 32;
            if (blendKey != curBlendId) {
                standaloneBatch.flush(stats);
                LightCompositionPass.apply(materialPass, light, blendId, standaloneBatch, standaloneShader, stats);
                curBlendId = blendKey;
            }

            standaloneBatch.setPackedColor(AmbientContributionRenderer.originalColor(frameQueue, i, pass));

            byte repeat = frameQueue.repeatFlags[i];
            if ((repeat & RenderRepeatFlags.ANY) == 0) {
                drawStandaloneEntry(i, tex);
            } else {
                drawRepeatedStandaloneEntry(i, tex, repeat);
            }
        }

        if (atlasOpen) {
            metricsBatch.end(stats);
        }

        if (standaloneOpen) {
            standaloneBatch.end(stats);
        }
    }

    private void drawAtlasEntry(int index, int texHandle) {
        metricsBatch.draw(
                texHandle,
                frameQueue.x1[index], frameQueue.y1[index],
                frameQueue.x2[index], frameQueue.y2[index],
                frameQueue.x3[index], frameQueue.y3[index],
                frameQueue.x4[index], frameQueue.y4[index],
                frameQueue.u1[index], frameQueue.v1[index],
                frameQueue.u2[index], frameQueue.v2[index],
                stats
        );

        stats.drawnQuads++;
    }

    private void drawRepeatedAtlasEntry(int index, int texHandle, byte repeat) {
        if (!prepareRepeatRange(index, repeat)) {
            return;
        }

        float stepX = repeatedStepX(index);
        float stepY = repeatedStepY(index);

        for (int iy = repeatRange[2]; iy <= repeatRange[3]; iy++) {
            float dy = iy * stepY;

            for (int ix = repeatRange[0]; ix <= repeatRange[1]; ix++) {
                float dx = ix * stepX;

                metricsBatch.draw(
                        texHandle,
                        frameQueue.x1[index] + dx, frameQueue.y1[index] + dy,
                        frameQueue.x2[index] + dx, frameQueue.y2[index] + dy,
                        frameQueue.x3[index] + dx, frameQueue.y3[index] + dy,
                        frameQueue.x4[index] + dx, frameQueue.y4[index] + dy,
                        frameQueue.u1[index], frameQueue.v1[index],
                        frameQueue.u2[index], frameQueue.v2[index],
                        stats
                );

                stats.drawnQuads++;
            }
        }
    }

    private void drawStandaloneEntry(int index, Texture tex) {
        standaloneBatch.drawTex(
                tex,
                frameQueue.x1[index], frameQueue.y1[index],
                frameQueue.x2[index], frameQueue.y2[index],
                frameQueue.x3[index], frameQueue.y3[index],
                frameQueue.x4[index], frameQueue.y4[index],
                frameQueue.u1[index], frameQueue.v1[index],
                frameQueue.u2[index], frameQueue.v2[index],
                stats
        );

        stats.drawnQuads++;
    }

    private void drawRepeatedStandaloneEntry(int index, Texture tex, byte repeat) {
        if (!prepareRepeatRange(index, repeat)) {
            return;
        }

        float stepX = repeatedStepX(index);
        float stepY = repeatedStepY(index);

        for (int iy = repeatRange[2]; iy <= repeatRange[3]; iy++) {
            float dy = iy * stepY;

            for (int ix = repeatRange[0]; ix <= repeatRange[1]; ix++) {
                float dx = ix * stepX;

                standaloneBatch.drawTex(
                        tex,
                        frameQueue.x1[index] + dx, frameQueue.y1[index] + dy,
                        frameQueue.x2[index] + dx, frameQueue.y2[index] + dy,
                        frameQueue.x3[index] + dx, frameQueue.y3[index] + dy,
                        frameQueue.x4[index] + dx, frameQueue.y4[index] + dy,
                        frameQueue.u1[index], frameQueue.v1[index],
                        frameQueue.u2[index], frameQueue.v2[index],
                        stats
                );

                stats.drawnQuads++;
            }
        }
    }

    private boolean prepareRepeatRange(int index, byte repeat) {
        float x1 = frameQueue.x1[index];
        float y1 = frameQueue.y1[index];
        float x2 = frameQueue.x2[index];
        float y2 = frameQueue.y2[index];
        float x3 = frameQueue.x3[index];
        float y3 = frameQueue.y3[index];
        float x4 = frameQueue.x4[index];
        float y4 = frameQueue.y4[index];

        if (!isAxisAligned(x1, y1, x2, y2, x3, y3, x4, y4)) {
            setBaseRepeatRange();
            return true;
        }

        float baseMinX = min4(x1, x2, x3, x4);
        float baseMaxX = max4(x1, x2, x3, x4);
        float baseMinY = min4(y1, y2, y3, y4);
        float baseMaxY = max4(y1, y2, y3, y4);

        float stepX = baseMaxX - baseMinX;
        float stepY = baseMaxY - baseMinY;

        if (((repeat & RenderRepeatFlags.REPEAT_X) != 0 && stepX <= 0f)
                || ((repeat & RenderRepeatFlags.REPEAT_Y) != 0 && stepY <= 0f)) {
            setBaseRepeatRange();
            return true;
        }

        float viewportW = cam.viewportWidth * cam.zoom;
        float viewportH = cam.viewportHeight * cam.zoom;
        float viewportMinX = cam.position.x - viewportW * 0.5f;
        float viewportMaxX = cam.position.x + viewportW * 0.5f;
        float viewportMinY = cam.position.y - viewportH * 0.5f;
        float viewportMaxY = cam.position.y + viewportH * 0.5f;

        return calculateVisibleRange(
                viewportMinX,
                viewportMaxX,
                viewportMinY,
                viewportMaxY,
                baseMinX,
                baseMaxX,
                baseMinY,
                baseMaxY,
                repeat,
                MAX_REPEAT_DRAWS_PER_SLOT,
                repeatRange
        );
    }

    private void setBaseRepeatRange() {
        repeatRange[0] = 0;
        repeatRange[1] = 0;
        repeatRange[2] = 0;
        repeatRange[3] = 0;
    }

    private float repeatedStepX(int index) {
        return max4(frameQueue.x1[index], frameQueue.x2[index], frameQueue.x3[index], frameQueue.x4[index])
                - min4(frameQueue.x1[index], frameQueue.x2[index], frameQueue.x3[index], frameQueue.x4[index]);
    }

    private float repeatedStepY(int index) {
        return max4(frameQueue.y1[index], frameQueue.y2[index], frameQueue.y3[index], frameQueue.y4[index])
                - min4(frameQueue.y1[index], frameQueue.y2[index], frameQueue.y3[index], frameQueue.y4[index]);
    }

    private static boolean calculateVisibleRange(float viewportMinX,
                                                 float viewportMaxX,
                                                 float viewportMinY,
                                                 float viewportMaxY,
                                                 float baseMinX,
                                                 float baseMaxX,
                                                 float baseMinY,
                                                 float baseMaxY,
                                                 byte repeatFlags,
                                                 int maxDraws,
                                                 int[] outRange) {
        boolean repeatX = (repeatFlags & RenderRepeatFlags.REPEAT_X) != 0;
        boolean repeatY = (repeatFlags & RenderRepeatFlags.REPEAT_Y) != 0;

        if (!repeatX && !overlaps(baseMinX, baseMaxX, viewportMinX, viewportMaxX)) {
            return false;
        }
        if (!repeatY && !overlaps(baseMinY, baseMaxY, viewportMinY, viewportMaxY)) {
            return false;
        }

        float stepX = baseMaxX - baseMinX;
        float stepY = baseMaxY - baseMinY;

        if ((repeatX && stepX <= 0f) || (repeatY && stepY <= 0f)) {
            return false;
        }

        int minIx = repeatX ? floorToInt((viewportMinX - baseMaxX) / stepX) : 0;
        int maxIx = repeatX ? floorToInt((viewportMaxX - baseMinX) / stepX) : 0;
        int minIy = repeatY ? floorToInt((viewportMinY - baseMaxY) / stepY) : 0;
        int maxIy = repeatY ? floorToInt((viewportMaxY - baseMinY) / stepY) : 0;

        if (maxIx < minIx || maxIy < minIy) {
            return false;
        }

        long xCount = (long) maxIx - minIx + 1L;
        long yCount = (long) maxIy - minIy + 1L;
        long total = xCount * yCount;

        if (maxDraws > 0 && total > maxDraws) {
            if (repeatX && repeatY) {
                if (xCount >= maxDraws) {
                    maxIx = minIx + maxDraws - 1;
                    maxIy = minIy;
                } else {
                    long cappedY = Math.max(1L, maxDraws / xCount);
                    maxIy = minIy + (int) cappedY - 1;
                }
            } else if (repeatX) {
                maxIx = minIx + maxDraws - 1;
            } else if (repeatY) {
                maxIy = minIy + maxDraws - 1;
            }
        }

        outRange[0] = minIx;
        outRange[1] = maxIx;
        outRange[2] = minIy;
        outRange[3] = maxIy;
        return true;
    }

    private static boolean isAxisAligned(float x1, float y1,
                                         float x2, float y2,
                                         float x3, float y3,
                                         float x4, float y4) {
        return nearlyEqual(x1, x2)
                && nearlyEqual(x3, x4)
                && nearlyEqual(y1, y4)
                && nearlyEqual(y2, y3);
    }

    private static boolean nearlyEqual(float a, float b) {
        return Math.abs(a - b) <= AXIS_EPSILON;
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private static int floorToInt(float value) {
        return (int) Math.floor(value);
    }

    private static boolean overlaps(float minA, float maxA, float minB, float maxB) {
        return !(maxA < minB || minA > maxB);
    }

    private void setUniformAmbientMul(ShaderProgram shader) {
        if (shader == null || !shader.hasUniform("u_ambientMul")) return;
        shader.setUniformf("u_ambientMul", 1f, 1f, 1f);
    }

    private ShaderParamsComponent entityParameters(int index) {
        return frameQueue.shaderParameters[index];
    }

    private void setUniform1f(ShaderProgram shader, String name, float value) {
        if (shader != null && shader.hasUniform(name)) shader.setUniformf(name, value);
    }

    private void setUniformLayerOffset(ShaderProgram shader) {
        if (shader != null && shader.hasUniform("u_layerOffset")) {
            shader.setUniformf("u_layerOffset", 0f, 0f);
        }
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
