package games.pixscape.studio.service.physics;

import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.component.physics.ConvexPolygonPartData;

public final class PolygonBuildResult {

    private final boolean valid;
    private final PolygonValidationResult validation;
    private final float[] sourceVerts;
    private final int sourceCount;
    private final int algorithmVersion;
    private final long sourceHash;
    private final Array<ConvexPolygonPartData> parts;

    private PolygonBuildResult(
            boolean valid,
            PolygonValidationResult validation,
            float[] sourceVerts,
            int sourceCount,
            int algorithmVersion,
            long sourceHash,
            Array<ConvexPolygonPartData> parts
    ) {
        this.valid = valid;
        this.validation = validation;
        this.sourceVerts = sourceVerts != null ? sourceVerts : new float[0];
        this.sourceCount = Math.max(0, sourceCount);
        this.algorithmVersion = algorithmVersion;
        this.sourceHash = sourceHash;
        this.parts = parts != null
                ? parts
                : new Array<>(true, 0, ConvexPolygonPartData.class);
    }

    public static PolygonBuildResult success(
            float[] sourceVerts,
            int sourceCount,
            int algorithmVersion,
            long sourceHash,
            Array<ConvexPolygonPartData> parts
    ) {
        return new PolygonBuildResult(
                true,
                PolygonValidationResult.ok(),
                sourceVerts,
                sourceCount,
                algorithmVersion,
                sourceHash,
                parts
        );
    }

    public static PolygonBuildResult failure(PolygonValidationResult validation) {
        return new PolygonBuildResult(
                false,
                validation,
                new float[0],
                0,
                PolygonDecomposer.ALGORITHM_VERSION,
                0L,
                new Array<>(true, 0, ConvexPolygonPartData.class)
        );
    }

    public boolean isValid() {
        return valid;
    }

    public PolygonValidationResult validation() {
        return validation;
    }

    public String message() {
        return validation != null ? validation.message() : "Invalid polygon.";
    }

    public float[] sourceVerts() {
        return sourceVerts;
    }

    public int sourceCount() {
        return sourceCount;
    }

    public int algorithmVersion() {
        return algorithmVersion;
    }

    public long sourceHash() {
        return sourceHash;
    }

    public Array<ConvexPolygonPartData> parts() {
        return parts;
    }
}