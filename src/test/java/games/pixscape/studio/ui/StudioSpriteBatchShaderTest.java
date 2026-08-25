package games.pixscape.studio.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioSpriteBatchShaderTest {

    @Test
    public void shaderSourcesDeclareGlsl140First() {
        assertTrue(StudioSpriteBatch.vertexShaderSource().startsWith("#version 140\n"));
        assertTrue(StudioSpriteBatch.fragmentShaderSource().startsWith("#version 140\n"));
    }

    @Test
    public void vertexShaderRetainsSpriteBatchBindings() {
        String source = StudioSpriteBatch.vertexShaderSource();

        assertTrue(source.contains("in vec4 a_position;"));
        assertTrue(source.contains("in vec4 a_color;"));
        assertTrue(source.contains("in vec2 a_texCoord0;"));
        assertTrue(source.contains("uniform mat4 u_projTrans;"));
        assertTrue(source.contains("v_color.a = v_color.a * (255.0 / 254.0);"));
    }

    @Test
    public void fragmentShaderUsesCoreProfileOutputAndTextureFunction() {
        String source = StudioSpriteBatch.fragmentShaderSource();

        assertTrue(source.contains("uniform sampler2D u_texture;"));
        assertTrue(source.contains("out vec4 fragColor;"));
        assertTrue(source.contains("texture(u_texture, v_texCoords)"));
        assertFalse(source.contains("gl_FragColor"));
        assertFalse(source.contains("texture2D"));
        assertFalse(source.contains("varying"));
    }
}
