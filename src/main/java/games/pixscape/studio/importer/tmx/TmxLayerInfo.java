package games.pixscape.studio.importer.tmx;

public interface TmxLayerInfo {
    TmxLayerKind kind();

    String name();

    boolean visible();

    float opacity();

    float offsetX();

    float offsetY();

    float parallaxX();

    float parallaxY();
}
