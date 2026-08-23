package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.property.PropertySet;

import java.util.Objects;

/**
 * Immutable V1 planning metadata for one supported Tiled object.
 * <p>
 * The plan owns one defensive property snapshot and exposes it as a stable read-only value.
 */
public record TmxObjectPlan(int sourceId,
                            String name,
                            String className,
                            String legacyType,
                            float x,
                            float y,
                            float width,
                            float height,
                            float rotation,
                            boolean visible,
                            TmxObjectKind kind,
                            PropertySet properties) {

    public TmxObjectPlan {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(properties, "properties");
        if (kind != TmxObjectKind.RECTANGLE && kind != TmxObjectKind.POINT) {
            throw new IllegalArgumentException("Unsupported V1 planned object kind: " + kind);
        }
        properties = properties.copy();
    }

    public boolean hasPositiveSourceId() {
        return sourceId > 0;
    }
}
