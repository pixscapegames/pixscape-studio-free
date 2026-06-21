package games.pixscape.studio.service.physics;

public final class PolygonValidationResult {

    public static final int OK = 0;
    public static final int NULL_VERTICES = 1;
    public static final int NOT_ENOUGH_VERTICES = 2;
    public static final int ARRAY_TOO_SMALL = 3;
    public static final int NON_FINITE_VERTEX = 4;
    public static final int DUPLICATE_VERTEX = 5;
    public static final int DEGENERATE_EDGE = 6;
    public static final int DEGENERATE_ANGLE = 7;
    public static final int ZERO_AREA = 8;
    public static final int SELF_INTERSECTION = 9;
    public static final int TRIANGULATION_FAILED = 10;

    private static final PolygonValidationResult VALID =
            new PolygonValidationResult(true, OK, "OK");

    private final boolean valid;
    private final int code;
    private final String message;

    private PolygonValidationResult(boolean valid, int code, String message) {
        this.valid = valid;
        this.code = code;
        this.message = message;
    }

    public static PolygonValidationResult ok() {
        return VALID;
    }

    public static PolygonValidationResult error(int code, String message) {
        return new PolygonValidationResult(false, code, message);
    }

    public boolean isValid() {
        return valid;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}