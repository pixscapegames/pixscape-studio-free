package games.pixscape.studio.ui.widget;

import com.kotcrab.vis.ui.widget.Tooltip;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import games.pixscape.runtime.render.ShaderOrigin;
import games.pixscape.runtime.render.ShaderRole;
import games.pixscape.runtime.service.ShaderRegistry;

/** Display-only badges: selection and history continue to use the exact registry name. */
public final class ShaderSelectBox extends VisSelectBox<String> {
    public static final String UNSPECIFIED = "Unspecified";

    public ShaderSelectBox() {
        new Tooltip.Builder("Unspecified: no Lit / Unlit type is assigned.\n"
                + "Existing shaders keep their current rendering.")
                .target(this).build();
    }

    @Override
    protected String toString(String name) {
        return name + "  [" + badge(name) + "]";
    }

    public static String badge(String name) {
        var surface = ShaderRegistry.getSurface(ShaderRegistry.indexOf(name));
        if (surface != null) return surface.lighting == games.pixscape.runtime.render.SurfaceShader.Lighting.LIT ? "Lit" : "Unlit";
        ShaderRole role = ShaderRegistry.getRole(ShaderRegistry.indexOf(name));
        if (role == ShaderRole.FX) return "FX";
        if (role == ShaderRole.LIGHT) return "Light source";
        if (role == ShaderRole.HUD) return "HUD";
        if (ShaderRegistry.getOrigin(name) == ShaderOrigin.CORE) return "Built-in";
        // Neither the role nor the origin declares a Lit/Unlit surface contract.
        return UNSPECIFIED;
    }
}
