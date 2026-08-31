package games.pixscape.studio.ui.asset;

import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityCategory;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RuntimeAvailabilityDropPolicyTest {

    @Test
    public void imageMapsToSprites() {
        DragPayload payload = new DragPayload();
        payload.type = "image-file";

        assertEquals(
                RuntimeAvailabilityCategory.SPRITES,
                RuntimeAvailabilityDropPolicy.resolveCategory(payload)
        );
    }

    @Test
    public void animationMapsToAnimations() {
        DragPayload payload = new DragPayload();
        payload.type = "anim-sheet";

        assertEquals(
                RuntimeAvailabilityCategory.ANIMATIONS,
                RuntimeAvailabilityDropPolicy.resolveCategory(payload)
        );
    }

    @Test
    public void particleMapsToParticles() {
        DragPayload payload = new DragPayload();
        payload.type = "particle";

        assertEquals(
                RuntimeAvailabilityCategory.PARTICLES,
                RuntimeAvailabilityDropPolicy.resolveCategory(payload)
        );
    }

    @Test
    public void gameObjectMapsToGameObjects() {
        DragPayload payload = new DragPayload();
        payload.type = "gameObject";

        assertEquals(
                RuntimeAvailabilityCategory.GAME_OBJECTS,
                RuntimeAvailabilityDropPolicy.resolveCategory(payload)
        );
    }

    @Test
    public void tiledAnimationMapsToTiledAnimations() {
        DragPayload payload = new DragPayload();
        payload.type = "tiled-animation";

        assertEquals(
                RuntimeAvailabilityCategory.TILED_ANIMATIONS,
                RuntimeAvailabilityDropPolicy.resolveCategory(payload)
        );
    }

    @Test
    public void tileAssetMapsToTiledTiles() {
        DragPayload payload = new DragPayload();
        payload.type = "tile-asset";

        assertEquals(
                RuntimeAvailabilityCategory.TILED_TILES,
                RuntimeAvailabilityDropPolicy.resolveCategory(payload)
        );
    }

    @Test
    public void unsupportedTypeIsRejected() {
        DragPayload payload = new DragPayload();
        payload.type = "unknown";

        assertNull(RuntimeAvailabilityDropPolicy.resolveCategory(payload));
    }
}
