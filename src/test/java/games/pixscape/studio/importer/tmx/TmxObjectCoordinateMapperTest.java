package games.pixscape.studio.importer.tmx;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TmxObjectCoordinateMapperTest {

    @Test
    public void isometricAbsoluteCoordinatesUseTiledDiamondProjection() {
        TmxScenePlan scene = scene("isometric", 4, 4, 32, 16);

        TmxObjectCoordinateMapper.Coordinate position =
                TmxObjectCoordinateMapper.absolute(scene, 24f, 24f, 0f, 0f);
        TmxObjectCoordinateMapper.Coordinate offsetPosition =
                TmxObjectCoordinateMapper.absolute(scene, 24f, 24f, 3f, 5f);

        assertEquals(0f, position.x(), 0.0001f);
        assertEquals(32f, position.y(), 0.0001f);
        assertEquals(3f, offsetPosition.x(), 0.0001f);
        assertEquals(27f, offsetPosition.y(), 0.0001f);
    }

    @Test
    public void isometricLocalCoordinatesProjectRectangleToDiamond() {
        TmxScenePlan scene = scene("isometric", 4, 4, 32, 16);

        assertCoordinate(scene, 0f, 0f, 0f, 0f);
        assertCoordinate(scene, 16f, 0f, 16f, 8f);
        assertCoordinate(scene, 16f, 16f, 32f, 0f);
        assertCoordinate(scene, 0f, 16f, 16f, -8f);
    }

    @Test
    public void isometricLocalRotationUsesTiledScreenSpaceBeforePixscapeBasisConversion() {
        TmxScenePlan scene = scene("isometric", 4, 4, 32, 16);

        TmxObjectCoordinateMapper.Coordinate first =
                TmxObjectCoordinateMapper.localWithTiledRotation(scene, 16f, 0f, 90f);
        TmxObjectCoordinateMapper.Coordinate second =
                TmxObjectCoordinateMapper.localWithTiledRotation(scene, 16f, 16f, 90f);

        assertEquals(32f, first.x(), 0.0001f);
        assertEquals(-4f, first.y(), 0.0001f);
        assertEquals(0f, second.x(), 0.0001f);
        assertEquals(-8f, second.y(), 0.0001f);
    }

    @Test
    public void isometricLocalRotationSupportsNonRightAngles() {
        TmxScenePlan scene = scene("isometric", 4, 4, 32, 16);

        TmxObjectCoordinateMapper.Coordinate coordinate =
                TmxObjectCoordinateMapper.localWithTiledRotation(scene, 16f, 0f, 30f);

        assertEquals(29.8564f, coordinate.x(), 0.0001f);
        assertEquals(4.9282f, coordinate.y(), 0.0001f);
    }

    @Test
    public void orthogonalCoordinatesRemainExactlyThePreviousConversion() {
        TmxScenePlan scene = scene("orthogonal", 10, 8, 32, 16);

        TmxObjectCoordinateMapper.Coordinate absolute =
                TmxObjectCoordinateMapper.absolute(scene, 24f, 40f, 3f, 5f);
        TmxObjectCoordinateMapper.Coordinate local = TmxObjectCoordinateMapper.local(scene, 7f, 9f);

        assertEquals(27f, absolute.x(), 0.0001f);
        assertEquals(83f, absolute.y(), 0.0001f);
        assertEquals(7f, local.x(), 0.0001f);
        assertEquals(-9f, local.y(), 0.0001f);
    }

    private static void assertCoordinate(TmxScenePlan scene,
                                         float sourceX,
                                         float sourceY,
                                         float expectedX,
                                         float expectedY) {
        TmxObjectCoordinateMapper.Coordinate coordinate =
                TmxObjectCoordinateMapper.local(scene, sourceX, sourceY);
        assertEquals(expectedX, coordinate.x(), 0.0001f);
        assertEquals(expectedY, coordinate.y(), 0.0001f);
    }

    private static TmxScenePlan scene(String orientation,
                                      int mapWidth,
                                      int mapHeight,
                                      int tileWidth,
                                      int tileHeight) {
        return new TmxScenePlan("Scene", "map.tmx", orientation,
                "isometric".equals(orientation)
                        ? SceneMetaRuntime.TiledProjection.ISO
                        : SceneMetaRuntime.TiledProjection.ORTHO,
                mapWidth, mapHeight, tileWidth, tileHeight,
                (long) mapWidth * mapHeight, 0, 0);
    }
}
