package games.pixscape.studio.component.physics;

import com.badlogic.gdx.utils.Array;

public final class AuthoredPolygonData {

    public long authoringId = 0L;

    public float[] sourceVerts = new float[0];
    public int sourceCount = 0;

    public int decompositionAlgorithmVersion = 1;
    public long sourceHash = 0L;

    /**
     * Cached convex decomposition generated from sourceVerts/sourceCount.
     * Used by the editor overlay and by materialization into FixtureDefData.
     */
    public Array<ConvexPolygonPartData> convexParts =
            new Array<>(true, 4, ConvexPolygonPartData.class);

    /**
     * FixtureDefData.fixtureId values generated from convexParts.
     */
    public int[] generatedFixtureIds = new int[0];

    public float density = 1f;
    public float friction = 0.2f;
    public float restitution = 0f;
    public boolean isSensor = false;

    public short categoryBits = 0x0001;
    public short maskBits = (short) 0xFFFF;
    public short groupIndex = 0;

    public float offsetX = 0f;
    public float offsetY = 0f;
    public float angleDeg = 0f;
}