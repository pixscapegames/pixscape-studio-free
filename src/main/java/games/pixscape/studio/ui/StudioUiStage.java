package games.pixscape.studio.ui;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Scene2D stage whose rendering resources are compatible with the Studio GL3 context. */
public final class StudioUiStage extends Stage {
    private static final String VERTEX_SHADER = """
            #version 140
            in vec4 a_position;
            in vec4 a_color;
            in vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            out vec4 v_color;
            out vec2 v_texCoords;

            void main() {
                v_color = a_color;
                v_color.a = v_color.a * (255.0 / 254.0);
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 140
            in vec4 v_color;
            in vec2 v_texCoords;
            uniform sampler2D u_texture;
            out vec4 fragColor;

            void main() {
                fragColor = v_color * texture(u_texture, v_texCoords);
            }
            """;

    private final SpriteBatch batch;
    private final ShaderProgram shader;

    public StudioUiStage(Viewport viewport) {
        this(viewport, createResources());
    }

    private StudioUiStage(Viewport viewport, Resources resources) {
        super(viewport, resources.batch);
        batch = resources.batch;
        shader = resources.shader;
    }

    static String vertexShaderSource() {
        return VERTEX_SHADER;
    }

    static String fragmentShaderSource() {
        return FRAGMENT_SHADER;
    }

    private static Resources createResources() {
        ShaderProgram shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new GdxRuntimeException("Unable to compile the Studio UI shader:\n" + log);
        }

        try {
            return new Resources(new SpriteBatch(1000, shader), shader);
        } catch (RuntimeException | Error failure) {
            shader.dispose();
            throw failure;
        }
    }

    @Override
    public void dispose() {
        try {
            super.dispose();
        } finally {
            try {
                batch.dispose();
            } finally {
                shader.dispose();
            }
        }
    }

    private record Resources(SpriteBatch batch, ShaderProgram shader) {
    }
}
