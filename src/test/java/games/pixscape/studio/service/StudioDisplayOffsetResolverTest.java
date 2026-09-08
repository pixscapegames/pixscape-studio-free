package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudioDisplayOffsetResolverTest {
    @Test
    public void ignoresBothDynamicAndLayerParallaxInTheAuthoringViewport() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            world.getMapper(EntityIndexComponent.class).create(entity).layerIndex = 1;
            DynamicEntityRenderState renderState = new DynamicEntityRenderState();
            int slot = renderState.acquireSlotForEntity(entity);
            renderState.offsetX[slot] = 42f;
            renderState.offsetY[slot] = -17f;
            LayerStateSOA layers = new LayerStateSOA(2);
            layers.enabled[1] = true;
            layers.parallaxX[1] = .2f;
            layers.parallaxY[1] = .3f;
            OrthographicCamera camera = new OrthographicCamera();
            camera.position.set(100f, 200f, 0f);

            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, renderState, layers, camera);
            Vector2 actual = new Vector2();
            resolver.resolve(entity, actual);

            assertEquals(0f, actual.x, 0f);
            assertEquals(0f, actual.y, 0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void addAndSubtractLeaveEditorGeometryAtAuthoredCoordinates() {
        World world = new World(new WorldConfiguration());
        try {
            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, new DynamicEntityRenderState(), new LayerStateSOA(1),
                    new OrthographicCamera());
            float[] corners = {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f};
            Vector2 pivot = new Vector2(5f, 5f);

            resolver.addTo(0, corners, 4);
            resolver.addTo(0, pivot);
            resolver.subtractFrom(0, pivot);

            assertEquals(0f, corners[0], 0f);
            assertEquals(10f, corners[4], 0f);
            assertEquals(5f, pivot.x, 0f);
            assertEquals(5f, pivot.y, 0f);
        } finally {
            world.dispose();
        }
    }
}
