package games.pixscape.studio.batch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;

public final class MeshBatchStudio implements MetricsBatch {

    private static final int VERT_STRIDE_FLOATS = 8;  // pos2 + uv2 + color4
    private static final int VERT_STRIDE_BYTES = VERT_STRIDE_FLOATS * 4;

    private final int maxQuads;

    private final Mesh mesh;
    private final float[] verts;   // CPU staging

    private int vertCount = 0;     // floats used
    private int quadCount = 0;     // quads staged

    private ShaderProgram shader = null;
    private Texture texture = null;
    private float cr = 1f, cg = 1f, cb = 1f, ca = 1f;

    private boolean blendingEnabled = false;
    private int blendSrc = GL20.GL_SRC_ALPHA;
    private int blendDst = GL20.GL_ONE_MINUS_SRC_ALPHA;

    private final Matrix4 combined = new Matrix4();

    public MeshBatchStudio(int maxQuads) {
        this.maxQuads = Math.max(64, maxQuads);
        int maxVerts = this.maxQuads * 4;
        int maxIndices = this.maxQuads * 6;

        mesh = new Mesh(
                false,                // isStatic
                maxVerts,
                maxIndices,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color")
        );

        verts = new float[maxVerts * VERT_STRIDE_FLOATS];
        // static IBO
        short[] indices = new short[maxIndices];

        // [0,1,2, 2,3,0] * N
        int idx = 0;
        short base = 0;
        for (int q = 0; q < maxQuads; q++) {
            indices[idx++] = base;
            indices[idx++] = (short) (base + 1);
            indices[idx++] = (short) (base + 2);
            indices[idx++] = (short) (base + 2);
            indices[idx++] = (short) (base + 3);
            indices[idx++] = base;
            base += 4;
        }
        mesh.setIndices(indices);
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

    @Override
    public void begin(Matrix4 combined, RenderStats stats) {
        this.combined.set(combined);
        vertCount = 0;
        quadCount = 0;
        // states set via setShader/setBlendMode on demand
    }

    @Override
    public void setShader(ShaderProgram shader, RenderStats stats) {
        if (shader != this.shader) {
            flush(stats);
            this.shader = shader;
            if (stats != null) stats.shaderSwitches++;
        }
    }

    @Override
    public void setBlendMode(boolean enabled, int sfactor, int dfactor, RenderStats stats) {
        if (enabled != blendingEnabled || sfactor != blendSrc || dfactor != blendDst) {
            flush(stats);
            blendingEnabled = enabled;
            blendSrc = sfactor;
            blendDst = dfactor;
            if (stats != null) stats.blendModeSwitches++;
        }
    }

    public void drawTex(Texture tex,
                        float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                        float u, float v, float u2, float v2,
                        RenderStats stats) {
        if (tex != texture) {
            flush(stats);
            texture = tex;
            if (stats != null && texture != null) stats.textureBinds++;
        }
        if (quadCount >= maxQuads) flush(stats);

        int o = vertCount;

        // BL -> (u, v2)
        verts[o++] = x1;
        verts[o++] = y1;
        verts[o++] = u;
        verts[o++] = v2;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;

        // TL -> (u, v)
        verts[o++] = x2;
        verts[o++] = y2;
        verts[o++] = u;
        verts[o++] = v;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;

        // TR -> (u2, v)
        verts[o++] = x3;
        verts[o++] = y3;
        verts[o++] = u2;
        verts[o++] = v;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;

        // BR -> (u2, v2)
        verts[o++] = x4;
        verts[o++] = y4;
        verts[o++] = u2;
        verts[o++] = v2;
        verts[o++] = cr;
        verts[o++] = cg;
        verts[o++] = cb;
        verts[o++] = ca;

        vertCount = o;
        quadCount++;
    }

    @Override
    public void draw(int textureHandle,
                     float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
                     float u, float v, float u2, float v2,
                     RenderStats stats) {
        Texture tex = TextureRegistry.getByHandle(textureHandle);
        drawTex(tex, x1, y1, x2, y2, x3, y3, x4, y4, u, v, u2, v2, stats);
    }

    @Override
    public void flush(RenderStats stats) {
        if (quadCount == 0) return;
        if (shader == null || texture == null) {
            vertCount = 0;
            quadCount = 0;
            return;
        }

        mesh.setVertices(verts, 0, vertCount);

        if (blendingEnabled) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(blendSrc, blendDst);
        } else {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        texture.bind(0);
        if (stats != null) stats.textureBinds++;

        shader.bind();
        if (stats != null) stats.shaderBinds++;
        shader.setUniformMatrix("u_projTrans", combined);
        shader.setUniformi("u_texture", 0);

        mesh.render(shader, GL20.GL_TRIANGLES, 0, quadCount * 6);

        if (stats != null) {
            stats.flushes++;
            stats.drawCalls++;
        }

        vertCount = 0;
        quadCount = 0;
    }

    @Override
    public void end(RenderStats stats) {
        flush(stats);
    }


    @Override
    public void close() {
        mesh.dispose();
    }

    @Override
    public void setTextureArrayBundle(AtlasRuntimeService.TextureArrayBundle bundle) {
        // this batch does not depend on a TextureArray, ignore
    }
}
