package games.pixscape.studio.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.IntIntMap;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;

import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * Batch multi-textures (ES2/desktop-friendly) :
 * - no sampler2DArray, bind N textures on N units (u_textures[i])
 * - chaque sommet porte a_texIndex (=unit index)
 * - if the available unit count is exceeded, flush and rebind
 * <p>
 * Shaders attendus : ShaderMode.MULTI_TEXTURE
 * attributes:
 * a_position (vec2)
 * a_texCoord0 (vec2)
 * a_color     (vec4)  // Unpacked
 * a_texIndex  (float)
 * uniforms :
 * u_projTrans (mat4)
 * u_textures[16] (sampler2D array)
 */
public final class MultiTextureMeshBatchStudio implements MetricsBatch {

    // pos2 + uv2 + color4 + texIndex1 = 9 floats
    private static final int VERT_STRIDE = 9;

    private final Mesh mesh;
    private final float[] verts;
    private final int maxQuads;

    private int vertCount = 0;
    private int quadCount = 0;

    // couleur courante (unpacked RGBA)
    private float cr = 1f, cg = 1f, cb = 1f, ca = 1f;

    // shader + matrix
    private ShaderProgram shader;
    private final Matrix4 combined = new Matrix4();
    private RenderStats stats;

    // texture unit management
    private final int maxUnitsUsed;                // min(maxUnitsHW, 16) — must match u_textures[] size
    private final IntIntMap texToUnit = new IntIntMap(); // key: GL handle, val: unit index
    private final int[] unitHandle;                // GL handle currently bound per unit, -1 otherwise
    private int unitsInUse = 0;

    public MultiTextureMeshBatchStudio(int maxQuads) {
        this.maxQuads = Math.max(64, maxQuads);
        int maxVerts = this.maxQuads * 4;
        int maxIndices = this.maxQuads * 6;

        // --- HW caps: GL_MAX_TEXTURE_IMAGE_UNITS
        IntBuffer ib = BufferUtils.newIntBuffer(1);
        ib.position(0);
        Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS, ib);
        ib.position(0);

        int maxUnitsHW = Math.max(1, ib.get(0));
        this.maxUnitsUsed = Math.min(maxUnitsHW, 16); // les shaders exposent 16 samplers max
        this.unitHandle = new int[this.maxUnitsUsed];
        Arrays.fill(this.unitHandle, -1);

        // Mesh : a_position (2), a_texCoord0 (2), a_color (4), a_texIndex (1)
        this.mesh = new Mesh(
                true, maxVerts, maxIndices,
                new VertexAttribute(Usage.Position, 2, "a_position"),
                new VertexAttribute(Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
                new VertexAttribute(Usage.Generic, 1, "a_texIndex")
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
        this.combined.set(combined);

        // reset unit state / cache
        texToUnit.clear();
        unitsInUse = 0;
        Arrays.fill(unitHandle, -1);

        if (shader != null) {
            shader.bind();
            shader.setUniformMatrix("u_projTrans", this.combined);
            for (int i = 0; i < maxUnitsUsed; i++) {
                shader.setUniformi("u_textures[" + i + "]", i);
            }
        }

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    @Override
    public void end(RenderStats stats) {
        flush(stats);
        this.stats = null;
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    @Override
    public void close() {
        mesh.dispose();
    }

    @Override
    public void setShader(ShaderProgram newShader, RenderStats stats) {
        if (newShader == shader) return;
        flush(stats);

        shader = newShader;
        if (shader != null) {
            shader.bind();
            shader.setUniformMatrix("u_projTrans", combined);
            for (int i = 0; i < maxUnitsUsed; i++) {
                shader.setUniformi("u_textures[" + i + "]", i);
            }
            if (stats != null) stats.shaderSwitches++;
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
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
        this.cr = r;
        this.cg = g;
        this.cb = b;
        this.ca = a;
    }

    @Override
    public void setPackedColor(float packed) {
        int bits = Float.floatToRawIntBits(packed);
        this.cr = (bits & 0xff) / 255f;
        this.cg = ((bits >>> 8) & 0xff) / 255f;
        this.cb = ((bits >>> 16) & 0xff) / 255f;
        this.ca = ((bits >>> 24) & 0xff) / 255f;
    }

    // --------------------------------------------------------------------
    // Draw
    // --------------------------------------------------------------------

    /**
     * Version interne qui prend directement une Texture.
     */
    public void drawTex(Texture tex,
                        float x1, float y1,
                        float x2, float y2,
                        float x3, float y3,
                        float x4, float y4,
                        float u, float v, float u2, float v2,
                        RenderStats stats) {

        if (tex == null) {
            // invalid or unresolved textureHandle: do not draw
            return;
        }

        if (quadCount >= maxQuads) {
            flush(stats);
        }

        int unit = ensureTextureBound(tex);
        if (unit < 0) {
            // Recovery attempt after flush
            flush(stats);
            texToUnit.clear();
            unitsInUse = 0;
            Arrays.fill(unitHandle, -1);
            unit = ensureTextureBound(tex);
            if (unit < 0) {
                throw new IllegalStateException("No free texture unit even after flush.");
            }
        }
        final float fUnit = (float) unit;

        int o = vertCount * VERT_STRIDE;

        // BL
        verts[o++] = x1;
        verts[o++] = y1;
        verts[o++] = u;
        verts[o++] = v2;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;
        verts[o++] = fUnit;

        // TL
        verts[o++] = x2;
        verts[o++] = y2;
        verts[o++] = u;
        verts[o++] = v;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;
        verts[o++] = fUnit;

        // TR
        verts[o++] = x3;
        verts[o++] = y3;
        verts[o++] = u2;
        verts[o++] = v;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;
        verts[o++] = fUnit;

        // BR
        verts[o++] = x4;
        verts[o++] = y4;
        verts[o++] = u2;
        verts[o++] = v2;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;
        verts[o++] = fUnit;

        vertCount += 4;
        quadCount += 1;
    }

    @Override
    public void draw(int textureHandle,
                     float x1, float y1,
                     float x2, float y2,
                     float x3, float y3,
                     float x4, float y4,
                     float u, float v, float u2, float v2,
                     RenderStats stats) {

        if (textureHandle == 0) {
            // no associated texture -> do not draw
            return;
        }

        Texture tex = TextureRegistry.getByHandle(textureHandle);
        if (tex == null) {
            // unknown handle: do not draw (avoids a silent crash)
            return;
        }

        drawTex(tex, x1, y1, x2, y2, x3, y3, x4, y4, u, v, u2, v2, stats);
    }

    @Override
    public void flush(RenderStats s) {
        if (quadCount == 0 || shader == null) return;

        mesh.setVertices(verts, 0, vertCount * VERT_STRIDE);
        shader.bind();
        shader.setUniformMatrix("u_projTrans", combined);
        mesh.render(shader, GL20.GL_TRIANGLES, 0, quadCount * 6);

        // reset state
        texToUnit.clear();
        unitsInUse = 0;
        Arrays.fill(unitHandle, -1);

        if (s != null) {
            s.flushes++;
            s.drawCalls++;
        }

        vertCount = 0;
        quadCount = 0;

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    }

    // --------------------------------------------------------------------
    // internals
    // --------------------------------------------------------------------

    /**
     * Assigns a unit to the texture if needed and forces glBindTexture.
     * Returns the unit used.
     */
    private int ensureTextureBound(Texture t) {
        if (t == null) return -1;

        final int handle = t.getTextureObjectHandle();
        int unit = texToUnit.get(handle, -1);

        if (unit == -1) {
            // no units left? the caller handles a flush if needed
            if (unitsInUse >= maxUnitsUsed) {
                return -1;
            }
            unit = unitsInUse++;
            texToUnit.put(handle, unit);
        }

        // ensure the unit points to this handle
        if (unitHandle[unit] != handle) {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0 + unit);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, handle);
            unitHandle[unit] = handle;
            if (stats != null) stats.textureBinds++;
        }

        return unit;
    }

    @Override
    public void setTextureArrayBundle(AtlasRuntimeService.TextureArrayBundle bundle) {
        // this batch does not depend on a TextureArray, ignore
    }
}
