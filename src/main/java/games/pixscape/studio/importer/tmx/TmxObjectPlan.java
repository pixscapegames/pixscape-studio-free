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
                            java.util.List<TmxObjectPoint> points,
                            int sourceOrder,
                            int zIndex,
                            int rawGid,
                            int cleanGid,
                            int tilesetPlanIndex,
                            int localTileId,
                            TmxTransformPlan tileTransform,
                            TmxObjectAlignment tileObjectAlignment,
                            int tileOffsetX,
                            int tileOffsetY,
                            int nativeTileWidth,
                            int nativeTileHeight,
                            String effectiveClassName,
                            String effectiveLegacyType,
                            PropertySet properties,
                            java.util.List<TmxObjectPropertyReference> objectPropertyReferences) {

    public TmxObjectPlan {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(properties, "properties");
        if (kind != TmxObjectKind.RECTANGLE
                && kind != TmxObjectKind.POINT
                && kind != TmxObjectKind.TILE
                && kind != TmxObjectKind.POLYGON
                && kind != TmxObjectKind.POLYLINE) {
            throw new IllegalArgumentException("Unsupported V1 planned object kind: " + kind);
        }
        if (kind == TmxObjectKind.TILE) {
            Objects.requireNonNull(tileTransform, "tileTransform");
            Objects.requireNonNull(tileObjectAlignment, "tileObjectAlignment");
            if (cleanGid <= 0 || tilesetPlanIndex < 0 || localTileId < 0) {
                throw new IllegalArgumentException("Tile Object plan requires a resolved GID and tileset.");
            }
        }
        points = java.util.List.copyOf(points);
        properties = properties.copy();
        objectPropertyReferences = java.util.List.copyOf(objectPropertyReferences);
    }

    public boolean hasPositiveSourceId() {
        return sourceId > 0;
    }
}
