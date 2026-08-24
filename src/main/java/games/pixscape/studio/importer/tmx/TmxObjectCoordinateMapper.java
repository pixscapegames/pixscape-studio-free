package games.pixscape.studio.importer.tmx;

/** Pure conversion from Tiled Object coordinates to the imported Pixscape scene space. */
public final class TmxObjectCoordinateMapper {
    private TmxObjectCoordinateMapper() {
    }

    public static Coordinate absolute(TmxScenePlan scene,
                                      float sourceX,
                                      float sourceY,
                                      float layerOffsetX,
                                      float layerOffsetY) {
        if (!isometric(scene)) {
            return new Coordinate(sourceX + layerOffsetX,
                    scene.mapHeightCells() * (float) scene.tileHeight() - sourceY - layerOffsetY);
        }

        float tileHeight = scene.tileHeight();
        float gridX = sourceX / tileHeight;
        float gridY = scene.mapHeightCells() - sourceY / tileHeight;
        float halfWidth = scene.tileWidth() * 0.5f;
        float halfHeight = tileHeight * 0.5f;
        return new Coordinate(
                (gridX - gridY) * halfWidth + halfWidth + layerOffsetX,
                (gridX + gridY) * halfHeight - layerOffsetY);
    }

    public static Coordinate local(TmxScenePlan scene, float sourceLocalX, float sourceLocalY) {
        if (!isometric(scene)) {
            return new Coordinate(sourceLocalX, -sourceLocalY);
        }

        float tileHeight = scene.tileHeight();
        float deltaGridX = sourceLocalX / tileHeight;
        float deltaGridY = -sourceLocalY / tileHeight;
        float halfWidth = scene.tileWidth() * 0.5f;
        float halfHeight = tileHeight * 0.5f;
        return new Coordinate(
                (deltaGridX - deltaGridY) * halfWidth,
                (deltaGridX + deltaGridY) * halfHeight);
    }

    private static boolean isometric(TmxScenePlan scene) {
        if (scene == null || scene.tileHeight() <= 0) {
            throw new IllegalArgumentException("Object coordinate mapping requires a scene with tileHeight > 0.");
        }
        return "isometric".equals(scene.orientation());
    }

    public record Coordinate(float x, float y) {
    }
}
