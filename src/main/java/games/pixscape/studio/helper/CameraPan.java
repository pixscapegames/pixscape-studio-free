package games.pixscape.studio.helper;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;

/** Shared grab-pan translation after both pointers have been resolved in one camera projection. */
public final class CameraPan {
    private CameraPan() {
    }

    public static void translateBetweenWorldPoints(OrthographicCamera camera,
                                                   Vector2 previousWorld,
                                                   Vector2 currentWorld) {
        camera.translate(previousWorld.x - currentWorld.x,
                previousWorld.y - currentWorld.y, 0f);
    }
}
