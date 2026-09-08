package games.pixscape.studio.service.property;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import games.pixscape.runtime.property.PropertyValue;

/** Recursively remaps OBJECT values while preserving every non-reference property value. */
public final class PropertyReferenceMapper {
    private PropertyReferenceMapper() { }

    public interface ObjectReferenceMapper {
        int map(int value);
    }

    public static PropertySet remap(PropertySet source, ObjectReferenceMapper objectReferenceMapper) {
        if (source == null) return null;
        if (objectReferenceMapper == null) {
            throw new IllegalArgumentException("OBJECT reference mapper is required.");
        }
        PropertySet result = new PropertySet(source.size());
        Array<String> names = new Array<>();
        source.copyNamesTo(names);
        for (String name : names) {
            PropertyValue value = source.valueCopy(name);
            if (value.type() == PropertyType.OBJECT) {
                result.putObjectStableId(name, objectReferenceMapper.map(value.asObjectStableId()));
            } else if (value.type() == PropertyType.CLASS) {
                result.putClass(name, value.className(),
                        remap(value.classPropertiesCopy(), objectReferenceMapper));
            } else {
                result.put(name, value);
            }
        }
        return result;
    }
}
