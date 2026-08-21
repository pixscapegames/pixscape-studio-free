package games.pixscape.studio.ui;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;

public final class StudioSpriteBatch extends SpriteBatch {

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

    private final ShaderProgram ownedShader;

    public static StudioSpriteBatch create() {
        ShaderProgram shader = createShader();

        try {
            return new StudioSpriteBatch(shader);
        } catch (RuntimeException | Error failure) {
            shader.dispose();
            throw failure;
        }
    }

    private StudioSpriteBatch(ShaderProgram shader) {
        super(1000, shader);
        ownedShader = shader;
    }

    @Override
    public void dispose() {
        try {
            super.dispose();
        } finally {
            ownedShader.dispose();
        }
    }

    static String vertexShaderSource() {
        return VERTEX_SHADER;
    }

    static String fragmentShaderSource() {
        return FRAGMENT_SHADER;
    }

    private static ShaderProgram createShader() {
        ShaderProgram shader =
                new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);

        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new GdxRuntimeException(
                    "Unable to compile Studio GL3 shader:\n" + log);
        }

        return shader;
    }
}