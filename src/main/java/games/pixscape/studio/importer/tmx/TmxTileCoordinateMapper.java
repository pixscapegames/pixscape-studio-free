package games.pixscape.studio.importer.tmx;

public final class TmxTileCoordinateMapper {

    private TmxTileCoordinateMapper() {
    }

    public static int pixscapeX(int sourceX) {
        return sourceX;
    }

    public static int pixscapeY(int layerHeight, int sourceY) {
        return layerHeight - 1 - sourceY;
    }
}
