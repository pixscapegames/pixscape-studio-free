package games.pixscape.studio.component;

import com.artemis.PooledComponent;

/** Studio-only marker for a Classic layer imported from a Tiled Object Layer. */
public final class TiledObjectLayerComponent extends PooledComponent {
    /** Keeps an explicit serialized payload for this provenance marker. */
    public boolean imported = true;

    @Override
    protected void reset() {
        imported = true;
    }
}
