package games.pixscape.studio.importer.tmx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.XmlReader;
import games.pixscape.runtime.property.EnumDefinition;
import games.pixscape.runtime.property.PropertyType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicitly selected Tiled project definitions, prepared once per import. */
public final class TiledPropertyTypes {
    private boolean provided;
    public boolean isProvided() {
        return provided;
    }

    private final Map<String, EnumDefinition> enums = new LinkedHashMap<>();
    private final Map<String, JsonValue> classes = new LinkedHashMap<>();

    public static TiledPropertyTypes load(FileHandle file) {
        TiledPropertyTypes result = new TiledPropertyTypes();
        if (file == null) {
            return result;
        }
        if (!file.exists() || file.isDirectory() || !file.extension().equalsIgnoreCase("tiled-project")) {
            throw new IllegalArgumentException("Select an existing .tiled-project file.");
        }
        result.provided = true;
        JsonValue root = new JsonReader().parse(file);
        JsonValue types = root.get("propertyTypes");
        if (types == null || !types.isArray()) {
            throw new IllegalArgumentException("Tiled project has no propertyTypes array: " + file.path());
        }
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (JsonValue type = types.child; type != null; type = type.next) {
            String name = type.getString("name", "");
            int id = type.getInt("id", -1);
            if (name.isBlank() || !names.add(name) || id < 0 || !ids.add(id)) {
                throw new IllegalArgumentException("Ambiguous Tiled type name or ID: '" + name + "' (#" + id + ").");
            }
            switch (type.getString("type", "")) {
                case "enum" -> {
                    String storage = type.getString("storageType", "");
                    if (!storage.equals("string") && !storage.equals("int")) {
                        throw new IllegalArgumentException("Enum '" + name + "' has unsupported storageType '" + storage + "'.");
                    }
                    JsonValue values = type.get("values");
                    if (values == null || !values.isArray()) {
                        throw new IllegalArgumentException("Enum '" + name + "' requires values.");
                    }
                    String[] choices = new String[values.size];
                    int index = 0;
                    for (JsonValue value = values.child; value != null; value = value.next) {
                        if (!value.isString()) {
                            throw new IllegalArgumentException("Enum '" + name + "' choice [" + index + "] must be a string.");
                        }
                        choices[index++] = value.asString();
                    }
                    result.enums.put(name, new EnumDefinition(name, storage.equals("int")
                            ? PropertyType.INTEGER : PropertyType.STRING, type.getBoolean("valuesAsFlags", false), choices));
                }
                case "class" -> result.classes.put(name, type);
                default -> throw new IllegalArgumentException("Unsupported Tiled type definition '" + name + "'.");
            }
        }
        return result;
    }

    public EnumDefinition enumNamed(String name) {
        return enums.get(name);
    }

    public java.util.List<EnumDefinition> enums() {
        return java.util.List.copyOf(enums.values());
    }

    /** External class defaults remain unsupported: diagnose omissions rather than inventing values. */
    public void requireExplicitMembers(String className, XmlReader.Element value) {
        java.util.Set<String> explicit = new java.util.HashSet<>();
        XmlReader.Element properties = value.getChildByName("properties");
        if (properties != null) {
            for (int i = 0; i < properties.getChildCount(); i++) {
                explicit.add(properties.getChild(i).getAttribute("name", ""));
            }
        }
        requireExplicitMembers(className, explicit);
    }

    public void requireExplicitMembers(String className, java.util.Set<String> explicit) {
        JsonValue definition = classes.get(className);
        if (definition == null) {
            return;
        }
        JsonValue members = definition.get("members");
        if (members == null) {
            return;
        }
        for (JsonValue member = members.child; member != null; member = member.next) {
            String name = member.getString("name", "");
            if (!explicit.contains(name)) {
                throw new IllegalArgumentException("CLASS '" + className + "' depends on external default member '"
                        + name + "'; resolving external class defaults is not supported. Save explicit member values.");
            }
        }
    }
}
