package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;

public final class ChangeShaderCommand implements Command {
    private final World world;
    private final int entityId;
    private final int beforeIdx;
    private final int afterIdx;

    private final ComponentMapper<RenderMaterialComponent> mMat;
    private final ComponentMapper<ShaderParamsComponent> mShaderParams;
    private final DirtyTrackerSystem dirtyTracker;
    private final Array<ShaderFloatParam> beforeFloats;
    private final Array<ShaderFloatParam> afterFloats;
    private final boolean hadParameters;

    public ChangeShaderCommand(World world, int entityId,
                               int beforeIdx, int afterIdx) {
        this.world = world;
        this.entityId = entityId;
        this.beforeIdx = beforeIdx;
        this.afterIdx = afterIdx;

        this.mMat = world.getMapper(RenderMaterialComponent.class);
        this.mShaderParams = world.getMapper(ShaderParamsComponent.class);
        this.dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
        ShaderParamsComponent existing = mShaderParams.get(entityId);
        hadParameters = existing != null;
        beforeFloats = copy(existing == null ? null : existing.floats);
        // A newly selected shader starts with no overrides and therefore follows its live defaults.
        afterFloats = ShaderParamsComponent.newShaderFloatArray();
    }

    @Override
    public void redo() {
        applyShader(afterIdx, afterFloats, afterFloats.size > 0);
    }

    @Override
    public void undo() {
        applyShader(beforeIdx, beforeFloats, hadParameters);
    }

    private void applyShader(int idx, Array<ShaderFloatParam> values, boolean hasComponent) {
        if (!world.getEntityManager().isActive(entityId)) return;

        RenderMaterialComponent mat =
                mMat.has(entityId) ? mMat.get(entityId) : mMat.create(entityId);

        mat.shaderIdx = idx;

        if (hasComponent) {
            ShaderParamsComponent comp =
                    mShaderParams.has(entityId) ? mShaderParams.get(entityId) : mShaderParams.create(entityId);

            if (comp.floats == null) {
                comp.floats = ShaderParamsComponent.newShaderFloatArray();
            } else {
                comp.floats.clear();
            }

            for (ShaderFloatParam param : values) {
                if (param == null || param.name == null || param.name.isEmpty()) {
                    continue;
                }

                comp.floats.add(new ShaderFloatParam(param.name, param.value));
            }
        } else if (mShaderParams.has(entityId)) {
            mShaderParams.remove(entityId);
        }

        if (dirtyTracker != null) {
            dirtyTracker.material(entityId);
        }
    }

    private static Array<ShaderFloatParam> copy(Array<ShaderFloatParam> source) {
        Array<ShaderFloatParam> result = ShaderParamsComponent.newShaderFloatArray();
        if (source != null) for (ShaderFloatParam parameter : source) {
            if (parameter != null) result.add(new ShaderFloatParam(parameter.name, parameter.value));
        }
        return result;
    }

    @Override
    public String label() {
        return "Change shader";
    }
}
