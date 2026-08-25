package games.pixscape.studio.ui;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StudioColorPickerFactoryTest {

    @Test
    public void prefixesProvideLibGdxGl3CompatibilityMappings() {
        String vertex = StudioColorPickerFactory.vertexPrefix();
        String fragment = StudioColorPickerFactory.fragmentPrefix();

        assertTrue(vertex.startsWith("#version 140\n"));
        assertTrue(vertex.contains("#define varying out\n"));
        assertTrue(vertex.contains("#define attribute in\n"));
        assertTrue(fragment.startsWith("#version 140\n"));
        assertTrue(fragment.contains("#define varying in\n"));
        assertTrue(fragment.contains("#define texture2D texture\n"));
        assertTrue(fragment.contains("#define gl_FragColor fragColor\n"));
        assertTrue(fragment.contains("out vec4 fragColor;\n"));
    }

    @Test
    public void scopedExecutionRestoresPreviousPrefixesAfterSuccess() {
        String originalVertex = ShaderProgram.prependVertexCode;
        String originalFragment = ShaderProgram.prependFragmentCode;
        try {
            ShaderProgram.prependVertexCode = "previous vertex";
            ShaderProgram.prependFragmentCode = "previous fragment";

            String result = StudioColorPickerFactory.withGl3Prefixes(() -> {
                assertEquals(StudioColorPickerFactory.vertexPrefix(), ShaderProgram.prependVertexCode);
                assertEquals(StudioColorPickerFactory.fragmentPrefix(), ShaderProgram.prependFragmentCode);
                return "created";
            });

            assertEquals("created", result);
            assertEquals("previous vertex", ShaderProgram.prependVertexCode);
            assertEquals("previous fragment", ShaderProgram.prependFragmentCode);
        } finally {
            ShaderProgram.prependVertexCode = originalVertex;
            ShaderProgram.prependFragmentCode = originalFragment;
        }
    }

    @Test
    public void scopedExecutionRestoresPreviousPrefixesAfterFailure() {
        String originalVertex = ShaderProgram.prependVertexCode;
        String originalFragment = ShaderProgram.prependFragmentCode;
        try {
            ShaderProgram.prependVertexCode = "previous vertex";
            ShaderProgram.prependFragmentCode = "previous fragment";

            assertThrows(IllegalStateException.class, () ->
                    StudioColorPickerFactory.withGl3Prefixes(() -> {
                        throw new IllegalStateException("construction failed");
                    }));

            assertEquals("previous vertex", ShaderProgram.prependVertexCode);
            assertEquals("previous fragment", ShaderProgram.prependFragmentCode);
        } finally {
            ShaderProgram.prependVertexCode = originalVertex;
            ShaderProgram.prependFragmentCode = originalFragment;
        }
    }
}
