package games.pixscape.studio.importer.tmx;

public record TmxGenericLayerInfo(TmxLayerKind kind,
                                  String name,
                                  boolean visible,
                                  float opacity,
                                  float offsetX,
                                  float offsetY,
                                  float parallaxX,
                                  float parallaxY) implements TmxLayerInfo {
}
