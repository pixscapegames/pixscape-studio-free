package games.pixscape.studio.helper;

import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.system.DirtyTrackerSystem;

public final class MaterialKeyHelper {

    private MaterialKeyHelper() {
    }

    /**
     * Computes a materialId from RenderMaterialComponent fields.
     */
    public static int compute(RenderMaterialComponent mat) {
        return SortKey64.packMaterialId(
                mat.getShaderIdx(),
                mat.getBlendModeId(),
                mat.getTextureHandle()
        );
    }

    /**
     * Decomposes a materialId into (shaderIdx, blend, textureHandle).
     * Writes public fields and marks rendering dirty if needed.
     */
    public static void apply(int materialId, int e, RenderMaterialComponent out, DirtyTrackerSystem dirty) {
        int shaderIdx = SortKey64.unpackMaterialShaderIdx(materialId);
        int blendModeId = SortKey64.unpackMaterialBlendModeId(materialId);
        int textureHandle = SortKey64.unpackMaterialTextureHandle(materialId);

        out.shaderIdx = shaderIdx;
        out.blendModeId = blendModeId;
        out.textureHandle = textureHandle;
        if (dirty != null) dirty.material(e);
    }
}
