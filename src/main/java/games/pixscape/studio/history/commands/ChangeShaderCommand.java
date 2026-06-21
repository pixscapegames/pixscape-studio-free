package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;

public final class ChangeShaderCommand implements Command {
    private final World world;
    private final int entityId;
    private final int beforeIdx;
    private final int afterIdx;

    private final ComponentMapper<RenderMaterialComponent> mMat;
    private final ComponentMapper<ShaderParamsComponent> mShaderParams;
    private final DirtyTrackerSystem dirtyTracker;

    public ChangeShaderCommand(World world, int entityId,
                               int beforeIdx, int afterIdx) {
        this.world = world;
        this.entityId = entityId;
        this.beforeIdx = beforeIdx;
        this.afterIdx = afterIdx;

        this.mMat = world.getMapper(RenderMaterialComponent.class);
        this.mShaderParams = world.getMapper(ShaderParamsComponent.class);
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
    }

    @Override
    public void redo() {
        applyShader(afterIdx);
    }

    @Override
    public void undo() {
        applyShader(beforeIdx);
    }

    private void applyShader(int idx) {
        if (!world.getEntityManager().isActive(entityId)) return;

        RenderMaterialComponent mat =
                mMat.has(entityId) ? mMat.get(entityId) : mMat.create(entityId);

        mat.shaderIdx = idx;

        String shaderName = ShaderRegistry.getName(idx);
        Array<ShaderFloatParam> defaults = ShaderRegistry.getDefaultUniforms(shaderName);

        if (defaults != null && defaults.size > 0) {
            ShaderParamsComponent comp =
                    mShaderParams.has(entityId) ? mShaderParams.get(entityId) : mShaderParams.create(entityId);

            if (comp.floats == null) {
                comp.floats = ShaderParamsComponent.newShaderFloatArray();
            } else {
                comp.floats.clear();
            }

            for (ShaderFloatParam param : defaults) {
                if (param == null || param.name == null || param.name.isEmpty()) {
                    continue;
                }

                comp.floats.add(new ShaderFloatParam(param.name, param.value));
            }
        } else if (mShaderParams.has(entityId)) {
            ShaderParamsComponent comp = mShaderParams.get(entityId);

            if (comp != null && comp.floats != null) {
                comp.floats.clear();
            }
        }

        if (dirtyTracker != null) {
            dirtyTracker.material(entityId);
        }
    }

    @Override
    public String label() {
        return "Change shader";
    }
}
