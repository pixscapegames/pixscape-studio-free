package games.pixscape.studio.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;


/**
 * Batch based on a single TextureArray (sampler2DArray in the shader).
 * <p>
 * Attributs (doivent matcher le shader ShaderMode.TEXTURE_ARRAY) :
 * 0: a_position  (vec2)  -> float2
 * 1: a_texCoord0 (vec2)  -> float2
 * 2: a_color     (vec4)  -> PACKED RGBA8888 (Usage.ColorPacked) - shader-side this remains vec4 0..1
 * 3: a_layer     (float) -> float
 * <p>
 * Format CPU (float[]) :
 * pos2 + uv2 + colorPacked1 + layer1 = 6 floats / vertex
 */
public final class TextureArrayMeshBatchStudio implements MetricsBatch {

    // pos2 + uv2 + colorPacked1 + layer1 = 6 floats
    private static final int VERT_STRIDE = 6;

    private final Mesh mesh;
    private final float[] verts;
    private final int maxQuads;

    private int vertCount = 0;
    private int quadCount = 0;

    // current color (packed RGBA8888 in float bits, like SpriteBatch)
    private float colorPacked = Color.WHITE.toFloatBits();

    /**
     * Current shader used for TextureArray quads.
     */
    private ShaderProgram shader;

    // Uniform-location cache (avoids string lookups and setUniformMatrix during flush)
    private int uProjTransLoc = -1;
    private int uArrayLoc = -1;

    private final Matrix4 combined = new Matrix4();
    private RenderStats stats;
    private boolean drawing = false;

    // Flags to avoid unnecessary resets
    private boolean projDirty = true;      // u_projTrans must be sent again ?
    private boolean arrayBound = false;    // texture array already bound on TU0 for this begin/end ?

    // --- TextureArray + mapping handle(TextureRegistry) -> layer ---
    private TextureArray textureArray;
    private AtlasRuntimeService.TextureArrayBundle bundle;
    private IntIntMap handle2layer;

    public TextureArrayMeshBatchStudio(int maxQuads) {
        this.maxQuads = Math.max(64, maxQuads);
        int maxVerts = this.maxQuads * 4;
        int maxIndices = this.maxQuads * 6;

        // Mesh : a_position (2), a_texCoord0 (2), a_color (packed), a_layer (1)
        this.mesh = new Mesh(
                false,  // vertices dynamiques
                true,   // indices statiques
                maxVerts, maxIndices,
                new VertexAttributes(
                        new VertexAttribute(Usage.Position, 2, "a_position"),
                        new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                        VertexAttribute.ColorPacked(),
                        new VertexAttribute(Usage.Generic, 1, "a_layer")
                )
        );

        // indices (quads -> 2 triangles)
        short[] idx = new short[maxIndices];
        int id = 0, v = 0;
        for (int q = 0; q < this.maxQuads; q++) {
            idx[id++] = (short) (v);
            idx[id++] = (short) (v + 1);
            idx[id++] = (short) (v + 2);
            idx[id++] = (short) (v + 2);
            idx[id++] = (short) (v + 3);
            idx[id++] = (short) (v);
            v += 4;
        }
        mesh.setIndices(idx);

        this.verts = new float[maxVerts * VERT_STRIDE];
    }

    // --------------------------------------------------------------------
    // MetricsBatch
    // --------------------------------------------------------------------

    @Override
    public void begin(Matrix4 combined, RenderStats stats) {
        this.stats = stats;
        this.drawing = true;

        this.combined.set(combined);
        this.projDirty = true;  // projection may differ on each begin()
        this.arrayBound = false; // restart from a clean state on each begin/end

        // Prepare shader + uniforms + TA binding once, not on each flush
        prepareDrawState(stats);
    }

    @Override
    public void end(RenderStats stats) {
        flush(stats);
        this.stats = null;
        this.drawing = false;

        // Do not rely on GL state after end()
        this.arrayBound = false;
        this.projDirty = true;
    }

    @Override
    public void close() {
        mesh.dispose();
    }

    @Override
    public void setShader(ShaderProgram sh, RenderStats stats) {
        if (sh == null) throw new IllegalArgumentException("TextureArrayMeshBatchStudio.setShader(null) called");
        if (sh == shader) return;

        flush(stats);
        shader = sh;

        // Cache uniform locations (not required, but useful and clean)
        cacheUniformLocations(shader);

        // Nouveau shader => il faut renvoyer u_projTrans et u_array
        projDirty = true;

        // Si on est en plein begin/end, on rebinde/configure tout de suite
        if (drawing) {
            // Le bind texture reste global, mais u_array est par-program => on le remet
            arrayBound = false;
            prepareDrawState(stats);
        }

        if (stats != null) stats.shaderSwitches++;
    }

    @Override
    public void setBlendMode(boolean enabled, int srcFunc, int dstFunc, RenderStats stats) {
        if (enabled) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(srcFunc, dstFunc);
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    @Override
    public void setColor(float r, float g, float b, float a) {
        colorPacked = Color.toFloatBits(r, g, b, a);
    }

    @Override
    public void setPackedColor(float packed) {
        colorPacked = packed;
    }

    @Override
    public void draw(int textureHandle,
                     float x1, float y1, float x2, float y2,
                     float x3, float y3, float x4, float y4,
                     float u, float v, float u2, float v2,
                     RenderStats stats) {

        if (!hasBundle() || shader == null) return;

        int layer = handle2layer.get(textureHandle, -1);
        if (layer < 0) return;

        drawTextureArrayQuad(textureHandle, layer,
                x1, y1, x2, y2, x3, y3, x4, y4,
                u, v, u2, v2,
                stats);
    }

    @Override
    public void flush(RenderStats s) {
        if (quadCount == 0 || shader == null || !hasBundle()) return;

        // Upload vertices
        mesh.setVertices(verts, 0, vertCount * VERT_STRIDE);

        // IMPORTANT: do NOT reset u_projTrans here
        // => prepareDrawState will send u_projTrans only if projDirty = true
        prepareDrawState(s);

        mesh.render(shader, GL20.GL_TRIANGLES, 0, quadCount * 6);

        if (s != null) {
            s.flushes++;
            s.drawCalls++;
        }

        vertCount = 0;
        quadCount = 0;
    }

    @Override
    public void setTextureArrayBundle(AtlasRuntimeService.TextureArrayBundle bundle) {
        // Safety: flush if the bundle changes mid-batch
        // (use current stats if begin() was called)
        if (drawing && quadCount > 0) flush(this.stats);

        if (bundle == null) {
            this.bundle = null;
            this.textureArray = null;
            this.handle2layer = null;

            this.arrayBound = false;
            return;
        }

        this.bundle = bundle;
        this.textureArray = bundle.textureArray;
        this.handle2layer = bundle.handle2layer;

        // New texture array => force binding on the next draw/flush
        this.arrayBound = false;
    }

    private boolean hasBundle() {
        return textureArray != null && handle2layer != null;
    }

    // --------------------------------------------------------------------
    // Internes : state + path TextureArray
    // --------------------------------------------------------------------

    private void cacheUniformLocations(ShaderProgram sh) {
        // These uniforms exist in the shaders, but keep it safe
        // (for shader variants, -1 => do not set)
        this.uProjTransLoc = sh.getUniformLocation("u_projTrans");
        this.uArrayLoc = sh.getUniformLocation("u_array");
    }

    /**
     * Ensures shader + uniforms + texture array are ready.
     */
    private void prepareDrawState(RenderStats stats) {
        if (shader == null || !hasBundle()) return;

        shader.bind();

        if (uProjTransLoc < 0 || uArrayLoc < 0) {
            // if setShader was called before shader.getUniformLocation was available (rare),
            // Refresh the cache after rebuilding the texture array.
            cacheUniformLocations(shader);
        }

        // u_projTrans : only when dirty (not on every flush)
        if (projDirty && uProjTransLoc >= 0) {
            shader.setUniformMatrix(uProjTransLoc, combined);
            projDirty = false;
        }

        // TextureArray bind + uniform u_array :
        // do it once per begin/end (and after setShader / bundle change)
        if (!arrayBound) {
            textureArray.bind(0);
            if (uArrayLoc >= 0) shader.setUniformi(uArrayLoc, 0);
            arrayBound = true;

            if (stats != null) stats.textureBinds++;

            // keep TU0 active to keep state clean
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        }
    }

    private void drawTextureArrayQuad(int textureHandle,
                                      int layer,
                                      float x1, float y1, float x2, float y2,
                                      float x3, float y3, float x4, float y4,
                                      float u, float v, float u2, float v2,
                                      RenderStats stats) {
        if (quadCount >= maxQuads) flush(stats);

        float fl = (float) layer;

        // Correct UVs if pages do not all have the same size

        float uu = u;
        float uu2 = u2;
        float vv = v;
        float vv2 = v2;

        int o = vertCount * VERT_STRIDE;

        // BL
        verts[o++] = x1;
        verts[o++] = y1;
        verts[o++] = uu;
        verts[o++] = vv2;
        verts[o++] = colorPacked;
        verts[o++] = fl;

        // TL
        verts[o++] = x2;
        verts[o++] = y2;
        verts[o++] = uu;
        verts[o++] = vv;
        verts[o++] = colorPacked;
        verts[o++] = fl;

        // TR
        verts[o++] = x3;
        verts[o++] = y3;
        verts[o++] = uu2;
        verts[o++] = vv;
        verts[o++] = colorPacked;
        verts[o++] = fl;

        // BR
        verts[o++] = x4;
        verts[o++] = y4;
        verts[o++] = uu2;
        verts[o++] = vv2;
        verts[o++] = colorPacked;
        verts[o++] = fl;

        vertCount += 4;
        quadCount += 1;
    }

    public AtlasRuntimeService.TextureArrayBundle getBundle() {
        return bundle;
    }

}
