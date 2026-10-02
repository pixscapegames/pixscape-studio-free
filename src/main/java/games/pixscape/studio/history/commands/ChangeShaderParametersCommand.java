package games.pixscape.studio.history.commands;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;

/** One undoable entity parameter edit, including component creation/removal. */
public final class ChangeShaderParametersCommand implements Command {
    private final World world;
    private final int entityId;
    private final ComponentMapper<ShaderParamsComponent> mapper;
    private final DirtyTrackerSystem dirtyTracker;
    private final Array<ShaderFloatParam> before;
    private final Array<ShaderFloatParam> after;
    private final boolean hadComponent;

    public ChangeShaderParametersCommand(World world, int entityId, Array<ShaderFloatParam> after) {
        this.world = world;
        this.entityId = entityId;
        mapper = world.getMapper(ShaderParamsComponent.class);
        dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
        ShaderParamsComponent existing = mapper.get(entityId);
        hadComponent = existing != null;
        before = copy(existing == null ? null : existing.floats);
        this.after = copy(after);
    }

    @Override public void redo() { apply(after, after.size > 0); }
    @Override public void undo() { apply(before, hadComponent); }
    @Override public String label() { return "Change shader parameters"; }

    private void apply(Array<ShaderFloatParam> values, boolean keepComponent) {
        if (!world.getEntityManager().isActive(entityId)) return;
        if (keepComponent) {
            ShaderParamsComponent component = mapper.has(entityId) ? mapper.get(entityId) : mapper.create(entityId);
            component.floats = copy(values);
        } else if (mapper.has(entityId)) {
            mapper.remove(entityId);
        }
        if (dirtyTracker != null) dirtyTracker.material(entityId);
    }

    private static Array<ShaderFloatParam> copy(Array<ShaderFloatParam> source) {
        Array<ShaderFloatParam> result = ShaderParamsComponent.newShaderFloatArray();
        if (source != null) for (ShaderFloatParam parameter : source) {
            if (parameter != null) result.add(new ShaderFloatParam(parameter.name, parameter.value));
        }
        return result;
    }
}
