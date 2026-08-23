package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.studio.service.StudioDisplayOffsetResolver;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PickingSystemDisplayOffsetTest {

    @Test
    public void genericObbHitUsesDisplayedCoordinates() {
        World world = new World(new WorldConfiguration());
        try {
            int entity = world.create();
            DynamicEntityRenderState renderState = new DynamicEntityRenderState();
            int slot = renderState.acquireSlotForEntity(entity);
            renderState.offsetX[slot] = 100f;
            renderState.offsetY[slot] = 20f;
            StudioDisplayOffsetResolver resolver = new StudioDisplayOffsetResolver(
                    world, renderState, new LayerStateSOA(1), new OrthographicCamera());
            float[] displayedCorners = {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f};

            resolver.addTo(entity, displayedCorners, 4);

            assertTrue(PickingSystem.isDisplayedObbHit(displayedCorners, 105f, 25f, 0f));
            assertFalse(PickingSystem.isDisplayedObbHit(displayedCorners, 5f, 5f, 0f));
        } finally {
            world.dispose();
        }
    }
}
