package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;
import java.util.List;

public interface TmxLayerPlan {
    PropertySet properties();

    List<TmxObjectPropertyReference> objectPropertyReferences();

    String name();

    String originalName();

    int sourceLayerIndex();
}
