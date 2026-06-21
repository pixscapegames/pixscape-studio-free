package games.pixscape.studio.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;

/**
 * Studio-only component.
 * <p>
 * Stores authoring physics data that must not be exported to runtime.
 * <p>
 * Runtime export keeps only PhysicsFixturesComponent, which contains the
 * generated convex FixtureDefData shapes.
 */
public final class PhysicsAuthoringComponent extends PooledComponent {

    /**
     * Authoring polygons attached to this physics body.
     * <p>
     * These are UI/authoring sources. They are not runtime fixtures.
     */
    public Array<AuthoredPolygonData> polygons =
            new Array<>(true, 4, AuthoredPolygonData.class);

    @Override
    protected void reset() {
        polygons.clear();
    }
}