package games.pixscape.studio.ui;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.kotcrab.vis.ui.widget.color.ColorPicker;

import java.util.function.Supplier;

/** Creates VisUI color pickers without leaving GL3 shader prefixes configured globally. */
public final class StudioColorPickerFactory {
    private static final String VERTEX_PREFIX = """
            #version 140
            #define varying out
            #define attribute in
            """;

    private static final String FRAGMENT_PREFIX = """
            #version 140
            #define varying in
            #define texture2D texture
            #define gl_FragColor fragColor
            out vec4 fragColor;
            """;

    private StudioColorPickerFactory() {
    }

    public static ColorPicker create(String title) {
        return withGl3Prefixes(() -> new ColorPicker(title));
    }

    static <T> T withGl3Prefixes(Supplier<T> operation) {
        String previousVertexPrefix = ShaderProgram.prependVertexCode;
        String previousFragmentPrefix = ShaderProgram.prependFragmentCode;
        try {
            ShaderProgram.prependVertexCode = VERTEX_PREFIX;
            ShaderProgram.prependFragmentCode = FRAGMENT_PREFIX;
            return operation.get();
        } finally {
            ShaderProgram.prependVertexCode = previousVertexPrefix;
            ShaderProgram.prependFragmentCode = previousFragmentPrefix;
        }
    }

    static String vertexPrefix() {
        return VERTEX_PREFIX;
    }

    static String fragmentPrefix() {
        return FRAGMENT_PREFIX;
    }
}
