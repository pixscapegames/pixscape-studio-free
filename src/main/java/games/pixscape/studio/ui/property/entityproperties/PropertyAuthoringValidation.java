package games.pixscape.studio.ui.property.entityproperties;

/**
 * Type-aware validation shared by the generic custom-properties editor.
 */
final class PropertyAuthoringValidation {

    private PropertyAuthoringValidation() {
    }

    static String requireName(String value, String path) {
        if (value == null || value.isEmpty()) {
            throw invalid(path, "property name must not be empty.");
        }
        if (value.trim().isEmpty()) {
            throw invalid(path, "property name must not contain only whitespace.");
        }
        return value;
    }

    static int parseInteger(String value, String path) {
        String text = value != null ? value.trim() : "";
        if (text.isEmpty()) {
            throw invalid(path, "integer value is required.");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw invalid(path, "integer value must be a signed 32-bit integer.");
        }
    }

    static float parseFloat(String value, String path) {
        String text = value != null ? value.trim().replace(',', '.') : "";
        if (text.isEmpty()) {
            throw invalid(path, "float value is required.");
        }
        try {
            float parsed = Float.parseFloat(text);
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) {
                throw invalid(path, "float value must be finite.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw invalid(path, "float value is malformed.");
        }
    }

    static String requireClassName(String value, String path) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(path, "class type name must not be blank.");
        }
        return value;
    }

    static IllegalArgumentException invalid(String path, String message) {
        String prefix = path != null && !path.isEmpty() ? "Property '" + path + "': " : "Property: ";
        return new IllegalArgumentException(prefix + message);
    }
}
