package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;
import java.util.List;

public interface TmxLayerInfo {
    PropertySet properties();

    List<TmxObjectPropertyReference> objectPropertyReferences();

    TmxLayerKind kind();

    String name();

    boolean visible();

    float opacity();

    float offsetX();

    float offsetY();

    float parallaxX();

    float parallaxY();
}
