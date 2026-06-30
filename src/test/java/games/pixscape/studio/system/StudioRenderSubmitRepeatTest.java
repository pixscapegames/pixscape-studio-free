package games.pixscape.studio.system;

import games.pixscape.runtime.render.RenderRepeatFlags;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioRenderSubmitRepeatTest {

    @Test
    public void repeatRangeMatchesRuntimeVisibleRangeBehavior() throws Exception {
        int[] range = new int[4];

        boolean visible = calculateVisibleRange(
                0f, 100f,
                0f, 50f,
                0f, 10f,
                0f, 10f,
                RenderRepeatFlags.REPEAT_X,
                1024,
                range
        );

        assertTrue(visible);
        assertArrayEquals(new int[]{-1, 10, 0, 0}, range);
    }

    @Test
    public void repeatRangeCapsVisibleCopiesLikeRuntime() throws Exception {
        int[] range = new int[4];

        boolean visible = calculateVisibleRange(
                0f, 1000f,
                0f, 1000f,
                0f, 1f,
                0f, 1f,
                RenderRepeatFlags.ANY,
                10,
                range
        );

        assertTrue(visible);
        assertArrayEquals(new int[]{-1, 8, -1, -1}, range);
    }

    @Test
    public void repeatRangeRejectsNonRepeatedInvisibleAxis() throws Exception {
        int[] range = new int[4];

        boolean visible = calculateVisibleRange(
                0f, 100f,
                100f, 200f,
                0f, 10f,
                0f, 10f,
                RenderRepeatFlags.REPEAT_X,
                1024,
                range
        );

        assertFalse(visible);
    }

    @Test
    public void axisAlignedCheckMatchesRuntimeSubmitLimitation() throws Exception {
        assertTrue(isAxisAligned(
                0f, 0f,
                0f, 10f,
                10f, 10f,
                10f, 0f
        ));

        assertFalse(isAxisAligned(
                0f, 0f,
                2f, 10f,
                10f, 10f,
                10f, 0f
        ));
    }

    @Test
    public void studioSubmitRoutesAtlasAndStandaloneThroughRepeatAwareHelpers() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/games/pixscape/studio/system/StudioRenderSubmitSystem.java"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertTrue(source.contains("private static final int MAX_REPEAT_DRAWS_PER_SLOT = 1024;"));
        assertTrue(source.contains("byte repeat = state.repeatFlags[slot];"));
        assertTrue(source.contains("drawRepeatedAtlasSlot(slot, texHandle, ox, oy, repeat);"));
        assertTrue(source.contains("drawRepeatedStandaloneSlot(slot, tex, ox, oy, repeat);"));
        assertTrue(source.contains("metricsBatch.draw(\n                        texHandle,"));
        assertTrue(source.contains("standaloneBatch.drawTex(\n                        tex,"));
        assertFalse(source.contains("state.offsetX[slot]"));
        assertFalse(source.contains("state.offsetY[slot]"));
    }

    private static boolean calculateVisibleRange(float viewportMinX,
                                                 float viewportMaxX,
                                                 float viewportMinY,
                                                 float viewportMaxY,
                                                 float baseMinX,
                                                 float baseMaxX,
                                                 float baseMinY,
                                                 float baseMaxY,
                                                 byte repeatFlags,
                                                 int maxDraws,
                                                 int[] outRange) throws Exception {
        Method method = StudioRenderSubmitSystem.class.getDeclaredMethod(
                "calculateVisibleRange",
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                byte.class,
                int.class,
                int[].class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(
                null,
                viewportMinX,
                viewportMaxX,
                viewportMinY,
                viewportMaxY,
                baseMinX,
                baseMaxX,
                baseMinY,
                baseMaxY,
                repeatFlags,
                maxDraws,
                outRange
        );
    }

    private static boolean isAxisAligned(float x1, float y1,
                                         float x2, float y2,
                                         float x3, float y3,
                                         float x4, float y4) throws Exception {
        Method method = StudioRenderSubmitSystem.class.getDeclaredMethod(
                "isAxisAligned",
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(null, x1, y1, x2, y2, x3, y3, x4, y4);
    }
}
