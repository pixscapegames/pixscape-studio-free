package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;
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
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;

public final class StudioRenderSubmitSystem extends BaseSystem implements ProfiledSystem {

    private final RenderStateSOA state;
    private final LayerStateSOA layerState;
    private final DrawList drawList;
    private final OrthographicCamera cam;

    private final MetricsBatch metricsBatch;
    private final MeshBatchStudio standaloneBatch;
    private final RenderStats stats;
    private final RenderStatsSink statsSink;
    private float time = 0f;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    private ComponentMapper<ShaderParamsComponent> mShaderParams;

    public StudioRenderSubmitSystem(RenderStateSOA state,
                                    LayerStateSOA layerState,
                                    DrawList drawList,
                                    OrthographicCamera cam,
                                    MetricsBatch batch,
                                    RenderStats stats,
                                    RenderStatsSink statsSink) {
        this.state = state;
        this.layerState = layerState;
        this.drawList = drawList;
        this.cam = cam;
        this.metricsBatch = batch;
        this.stats = stats;
        this.statsSink = statsSink;
        this.standaloneBatch = new MeshBatchStudio(2048);
    }

    public RenderStateSOA getState() {
        return state;
    }

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

    private void render() {
        cam.update();

        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        AtlasRuntimeService.TextureArrayBundle activeBundle = null;
        if (metricsBatch instanceof TextureArrayMeshBatchStudio taBatch) {
            activeBundle = taBatch.getBundle();
        }

        int[] slots = drawList.data();
        int size = drawList.size;
        final boolean hasLayerMeta = layerState.maxLayerIndex() >= 0;

        boolean atlasOpen = false;
        boolean standaloneOpen = false;

        ShaderProgram curShader = null;
        int curShaderIdx = -1;
        int curBlendId = Integer.MIN_VALUE;
        float curPackedColor = Float.NaN;
        int lastParamsHash = 0;
        boolean hasLastParamsHash = false;

        for (int i = 0; i < size; i++) {
            final int slot = slots[i];

            int layerIdx = state.layerIndex[slot];
            if (hasLayerMeta) {
                if (layerIdx < 0 || layerIdx >= 31) continue;
                if (layerIdx > layerState.maxLayerIndex() || !layerState.enabled[layerIdx]) continue;
            }

            final int texHandle = state.textureHandle[slot];
            if (texHandle == 0) continue;

            boolean inAtlas = activeBundle != null && activeBundle.handle2layer.containsKey(texHandle);

            // Studio canvas uses logical editing space; parallax is applied only in Preview/runtime.
            float ox = 0f;
            float oy = 0f;

            if (inAtlas) {
                if (standaloneOpen) {
                    standaloneBatch.end(stats);
                    standaloneOpen = false;
                }

                if (!atlasOpen) {
                    metricsBatch.begin(cam.combined, stats);
                    atlasOpen = true;
                    curShaderIdx = -1;
                    curBlendId = Integer.MIN_VALUE;
                    curPackedColor = Float.NaN;
                    hasLastParamsHash = false;
                }

                final int shaderIdx = state.shader[slot];
                if (shaderIdx != curShaderIdx) {
                    curShaderIdx = shaderIdx;
                    ShaderProgram sh = ShaderRegistry.getByIdx(shaderIdx);
                    if (sh == null) {
                        sh = ShaderRegistry.get(ShaderMode.TEXTURE_ARRAY.defaultShaderName());
                    }

                    if (sh != curShader) {
                        metricsBatch.setShader(sh, stats);
                        curShader = sh;
                        hasLastParamsHash = false;

                        if (curShader != null) {
                            setUniform1f(curShader, "u_time", time);
                            setUniformLayerOffset(curShader);
                            setUniformAmbientMul(curShader);
                        }
                    }
                }

                final int blendId = state.blend[slot];
                if (blendId != curBlendId) {
                    metricsBatch.flush(stats);
                    BlendMode blendMode = BlendMode.fromId(blendId);
                    Blend.apply(blendMode);
                    metricsBatch.setBlendMode(
                            blendMode.blending,
                            blendMode.srcFactor,
                            blendMode.dstFactor,
                            stats
                    );
                    curBlendId = blendId;
                }

                float packedColor = state.colorPacked[slot];
                if (packedColor != curPackedColor) {
                    metricsBatch.setPackedColor(packedColor);
                    curPackedColor = packedColor;
                }

                if (curShader != null && mShaderParams != null) {
                    final int entityId = state.entityId[slot];
                    if (entityId >= 0 && mShaderParams.has(entityId)) {
                        ShaderParamsComponent params = mShaderParams.get(entityId);
                        if (params != null && params.floats != null && params.floats.size > 0) {
                            int h = hashShaderParams(params.floats);
                            if (!hasLastParamsHash || h != lastParamsHash) {
                                metricsBatch.flush(stats);
                                applyShaderParams(curShader, params.floats);
                                lastParamsHash = h;
                                hasLastParamsHash = true;
                            }
                        }
                    }
                }

                metricsBatch.draw(
                        texHandle,
                        state.x1[slot] + ox, state.y1[slot] + oy,
                        state.x2[slot] + ox, state.y2[slot] + oy,
                        state.x3[slot] + ox, state.y3[slot] + oy,
                        state.x4[slot] + ox, state.y4[slot] + oy,
                        state.u1[slot], state.v1[slot],
                        state.u2[slot], state.v2[slot],
                        stats
                );

                stats.drawnQuads++;
                continue;
            }

            if (atlasOpen) {
                metricsBatch.end(stats);
                atlasOpen = false;
            }

            Texture tex = TextureRegistry.getByHandle(texHandle);
            if (tex == null) continue;

            final int blendId = state.blend[slot];
            if (!standaloneOpen) {
                standaloneBatch.begin(cam.combined, stats);
                curBlendId = Integer.MIN_VALUE;
                ShaderProgram standaloneShader = ShaderRegistry.get(ShaderMode.TEXTURE_2D.defaultShaderName());
                if (standaloneShader == null) {
                    throw new IllegalStateException("Missing standalone shader: "
                            + ShaderMode.TEXTURE_2D.defaultShaderName());
                }

                standaloneBatch.setShader(standaloneShader, stats);
                setUniformAmbientMul(standaloneShader);
                standaloneOpen = true;
            }

            if (blendId != curBlendId) {
                standaloneBatch.flush(stats);
                BlendMode blendMode = BlendMode.fromId(blendId);
                Blend.apply(blendMode);
                standaloneBatch.setBlendMode(
                        blendMode.blending,
                        blendMode.srcFactor,
                        blendMode.dstFactor,
                        stats
                );
                curBlendId = blendId;
            }

            standaloneBatch.setPackedColor(state.colorPacked[slot]);

            standaloneBatch.drawTex(
                    tex,
                    state.x1[slot] + ox, state.y1[slot] + oy,
                    state.x2[slot] + ox, state.y2[slot] + oy,
                    state.x3[slot] + ox, state.y3[slot] + oy,
                    state.x4[slot] + ox, state.y4[slot] + oy,
                    state.u1[slot], state.v1[slot],
                    state.u2[slot], state.v2[slot],
                    stats
            );

            stats.drawnQuads++;
        }

        if (atlasOpen) {
            metricsBatch.end(stats);
        }

        if (standaloneOpen) {
            standaloneBatch.end(stats);
        }
    }

    private void setUniformAmbientMul(ShaderProgram shader) {
        if (shader == null || !shader.hasUniform("u_ambientMul")) return;
        SceneMeta meta = ProjectConfig.getInstance().getCurrentSceneMeta();
        float r = meta != null ? meta.ambientMulR : 1f;
        float g = meta != null ? meta.ambientMulG : 1f;
        float b = meta != null ? meta.ambientMulB : 1f;
        shader.setUniformf("u_ambientMul", r, g, b);
    }

    private static int hashShaderParams(Array<ShaderFloatParam> floats) {
        if (floats == null || floats.size == 0) {
            return 0;
        }
        int h = 0x9E3779B9;

        for (int i = 0; i < floats.size; i++) {
            ShaderFloatParam param = floats.get(i);
            if (param == null || param.name == null || param.name.length() == 0) {
                continue;
            }
            int kh = param.name.hashCode();
            int vh = Float.floatToIntBits(param.value);
            int x = kh * 0x85EBCA6B ^ vh * 0xC2B2AE35;
            x ^= (x >>> 16);
            h ^= x;
            h = Integer.rotateLeft(h, 13) * 5 + 0xE6546B64;
        }
        return h;
    }

    private void applyShaderParams(ShaderProgram shader, Array<ShaderFloatParam> floats) {
        if (shader == null || floats == null || floats.size == 0) {
            return;
        }
        for (int i = 0; i < floats.size; i++) {
            ShaderFloatParam param = floats.get(i);
            if (param == null || param.name == null || param.name.isEmpty()) {
                continue;
            }
            setUniform1f(shader, param.name, param.value);
        }
    }

    private void setUniform1f(ShaderProgram shader, String name, float value) {
        if (shader != null) shader.setUniformf(name, value);
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
