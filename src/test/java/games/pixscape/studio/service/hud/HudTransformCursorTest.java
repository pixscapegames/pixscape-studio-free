package games.pixscape.studio.service.hud;

import com.badlogic.gdx.graphics.Cursor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HudTransformCursorTest {
    @Test public void mapsEveryResizeHandleToTheMatchingSystemCursor() {
        assertEquals(Cursor.SystemCursor.VerticalResize, HudTransformCursor.cursorFor(HudTransformHandle.N));
        assertEquals(Cursor.SystemCursor.VerticalResize, HudTransformCursor.cursorFor(HudTransformHandle.S));
        assertEquals(Cursor.SystemCursor.HorizontalResize, HudTransformCursor.cursorFor(HudTransformHandle.E));
        assertEquals(Cursor.SystemCursor.HorizontalResize, HudTransformCursor.cursorFor(HudTransformHandle.W));
        assertEquals(Cursor.SystemCursor.NWSEResize, HudTransformCursor.cursorFor(HudTransformHandle.NW));
        assertEquals(Cursor.SystemCursor.NWSEResize, HudTransformCursor.cursorFor(HudTransformHandle.SE));
        assertEquals(Cursor.SystemCursor.NESWResize, HudTransformCursor.cursorFor(HudTransformHandle.NE));
        assertEquals(Cursor.SystemCursor.NESWResize, HudTransformCursor.cursorFor(HudTransformHandle.SW));
        assertNull(HudTransformCursor.cursorFor(null));
    }

    @Test public void releasesOnlyItsOwnCursorAndAvoidsRepeatedNativeRequests() {
        List<Cursor.SystemCursor> applied = new ArrayList<>();
        HudTransformCursor cursor = new HudTransformCursor(applied::add);

        cursor.showFor(HudTransformHandle.E);
        cursor.showFor(HudTransformHandle.E);
        assertEquals(List.of(Cursor.SystemCursor.HorizontalResize), applied);
        assertEquals(Cursor.SystemCursor.HorizontalResize, cursor.ownedCursor());

        cursor.clear();
        cursor.clear();
        assertEquals(List.of(Cursor.SystemCursor.HorizontalResize, Cursor.SystemCursor.Arrow), applied);
        assertNull(cursor.ownedCursor());
    }
}
