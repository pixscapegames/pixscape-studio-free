package games.pixscape.studio.importer.tmx;

public record TmxMapInfo(String orientation,
                         int width,
                         int height,
                         int tileWidth,
                         int tileHeight,
                         boolean infinite) {
}
