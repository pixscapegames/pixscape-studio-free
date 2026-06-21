package games.pixscape.studio.service.physics;

public final class PolygonHash {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private PolygonHash() {
    }

    public static long hash(float[] verts, int count) {
        long h = FNV_OFFSET;
        h = mix(h, count);

        if (verts == null || count <= 0) {
            return h;
        }

        int n = Math.min(verts.length, count * 2);

        for (int i = 0; i < n; i++) {
            float v = verts[i];

            if (v == 0f) {
                v = 0f; // normalize -0.0f
            }

            h = mix(h, Float.floatToIntBits(v));
        }

        return h;
    }

    private static long mix(long h, int value) {
        h ^= value;
        h *= FNV_PRIME;
        return h;
    }
}