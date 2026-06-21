package games.pixscape.studio.component;

import com.artemis.PooledComponent;

/**
 * Editor metadata for a camera.
 */
public final class CameraMetaComponent extends PooledComponent {
    public String name = "Camera";
    public String description = "";


    @Override
    protected void reset() {
        name = "Camera";
        description = "";
    }
}

