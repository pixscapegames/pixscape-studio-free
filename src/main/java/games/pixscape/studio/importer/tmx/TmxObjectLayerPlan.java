package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.List;
import java.util.Objects;

/**
 * Immutable V1 planning metadata for a Tiled Object Layer.
 * <p>
 * The plan owns one defensive property snapshot and exposes it as a stable read-only value.
 */
public record TmxObjectLayerPlan(String name,
                                 String originalName,
                                 int sourceLayerIndex,
                                 boolean visible,
                                 float parallaxX,
                                 float parallaxY,
                                 float offsetX,
                                 float offsetY,
                                 float opacity,
                                 TmxObjectDrawOrder drawOrder,
                                 PropertySet properties,
                                 List<TmxObjectPlan> objects) implements TmxLayerPlan {

    public TmxObjectLayerPlan {
        Objects.requireNonNull(properties, "properties");
        properties = properties.copy();
        objects = List.copyOf(objects);
    }
}
