package games.pixscape.studio.component;

import com.artemis.PooledComponent;

/** Studio-only provenance for an object imported from a Tiled Object Layer. */
public final class TiledObjectComponent extends PooledComponent {

    public enum Kind {
        UNKNOWN,
        RECTANGLE,
        POINT,
        TILE
    }

    public Kind kind = Kind.UNKNOWN;
    public String className = "";

    @Override
    protected void reset() {
        kind = Kind.UNKNOWN;
        className = "";
    }
}
