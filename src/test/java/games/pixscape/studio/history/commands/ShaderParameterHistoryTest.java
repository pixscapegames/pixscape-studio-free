package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Test;

public class ShaderParameterHistoryTest {
    @Test
    public void entityEditAndShaderSwitchUndoRestoreNamedValues() {
        World world = new World(new WorldConfiguration());
        int entity = world.create();
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).create(entity);
        material.shaderIdx = 1;
        HistoryManager history = new HistoryManager(8);
        Array<ShaderFloatParam> override = new Array<>();
        override.add(new ShaderFloatParam("u_gain", 0.75f));

        history.execute(new ChangeShaderParametersCommand(world, entity, override));
        Assert.assertEquals(0.75f,
                world.getMapper(ShaderParamsComponent.class).get(entity).floats.first().value, 0f);
        history.execute(new ChangeShaderCommand(world, entity, 1, 2));
        Assert.assertFalse(world.getMapper(ShaderParamsComponent.class).has(entity));

        history.undo();
        Assert.assertEquals(1, material.shaderIdx);
        Assert.assertEquals("u_gain",
                world.getMapper(ShaderParamsComponent.class).get(entity).floats.first().name);
        history.undo();
        Assert.assertFalse(world.getMapper(ShaderParamsComponent.class).has(entity));
        history.redo();
        Assert.assertEquals(0.75f,
                world.getMapper(ShaderParamsComponent.class).get(entity).floats.first().value, 0f);
        world.dispose();
    }
}
