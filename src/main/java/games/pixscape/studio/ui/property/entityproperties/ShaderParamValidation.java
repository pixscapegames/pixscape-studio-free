package games.pixscape.studio.ui.property.entityproperties;

final class ShaderParamValidation {

    private ShaderParamValidation() {
    }

    static boolean isNameValid(String rawName, String rawValue) {
        String name = trim(rawName);
        String value = trim(rawValue);
        if (name.isEmpty() && value.isEmpty()) return true;
        return !name.isEmpty();
    }

    static boolean isValueValid(String rawName, String rawValue) {
        String name = trim(rawName);
        String value = trim(rawValue);
        if (name.isEmpty() && value.isEmpty()) return true;
        if (value.isEmpty()) return false;
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static boolean isRowValid(String rawName, String rawValue) {
        return isNameValid(rawName, rawValue) && isValueValid(rawName, rawValue);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
