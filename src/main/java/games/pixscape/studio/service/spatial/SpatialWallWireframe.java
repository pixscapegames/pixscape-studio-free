package games.pixscape.studio.service.spatial;

/** Writes the complete projected twelve-edge wireframe of one authored wall volume. */
public final class SpatialWallWireframe {
    public static final int SEGMENT_COUNT = 12;
    public static final int FLOATS_PER_SEGMENT = 4;
    public static final int REQUIRED_OUTPUT_FLOATS = SEGMENT_COUNT * FLOATS_PER_SEGMENT;

    private SpatialWallWireframe() {
    }

    public static int write(float[] base, float[] top, float[] out) {
        if (base == null || base.length < 8 || top == null || top.length < 8
                || out == null || out.length < REQUIRED_OUTPUT_FLOATS) return 0;

        int offset = 0;
        for (int edge = 0; edge < 4; edge++) {
            int next = (edge + 1) & 3;
            offset = writeSegment(base, edge, base, next, out, offset);
        }
        for (int edge = 0; edge < 4; edge++) {
            int next = (edge + 1) & 3;
            offset = writeSegment(top, edge, top, next, out, offset);
        }
        for (int corner = 0; corner < 4; corner++) {
            offset = writeSegment(base, corner, top, corner, out, offset);
        }
        return SEGMENT_COUNT;
    }

    private static int writeSegment(float[] from,
                                    int fromVertex,
                                    float[] to,
                                    int toVertex,
                                    float[] out,
                                    int offset) {
        out[offset] = from[fromVertex * 2];
        out[offset + 1] = from[fromVertex * 2 + 1];
        out[offset + 2] = to[toVertex * 2];
        out[offset + 3] = to[toVertex * 2 + 1];
        return offset + FLOATS_PER_SEGMENT;
    }
}
