package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;

public class ShaderService {
    private final StudioApplicationAdapter app;

    public ShaderService(StudioApplicationAdapter app) {
        this.app = app;
    }

    public void detachShaderFromEcs(String shaderName, boolean fx) {
        var world = app.getCanvas().getEcsWorld();


        // Cleanup of entity materials
        var mMat = world.getMapper(RenderMaterialComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        var asm = world.getAspectSubscriptionManager();
        IntBag bag = asm.get(Aspect.all(RenderMaterialComponent.class)).getEntities();
        int[] data = bag.getData();

        // Default shader for THIS mode (the one used to create the batch)
        String defShaderName = app.getCanvas().getDefaultShaderName();
        int defShaderIdx = ShaderRegistry.indexOf(defShaderName);

        // safety fallback
        if (defShaderIdx < 0) {
            defShaderIdx = ShaderRegistry.indexOf("default");
        }
        if (defShaderIdx < 0) {
            // last-resort safety: leave everything unchanged instead of setting -1
            return;
        }

        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            RenderMaterialComponent mat = mMat.get(e);
            if (mat == null) continue;

            int currentIdx = mat.getShaderIdx();
            String currentName = ShaderRegistry.getName(currentIdx);

            if (shaderName.equals(currentName)) {
                // Fallback to the mode default shader
                mat.shaderIdx = defShaderIdx;
                if (dirty != null) dirty.material(e);
            }
        }

    }
}
