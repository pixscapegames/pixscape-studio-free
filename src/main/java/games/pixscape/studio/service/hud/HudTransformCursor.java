package games.pixscape.studio.service.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Cursor;

import java.util.Objects;
import java.util.function.Consumer;

/** Owns the system cursor only while the HUD transform gizmo needs a resize cursor. */
final class HudTransformCursor {
    private final Consumer<Cursor.SystemCursor> systemCursor;
    private Cursor.SystemCursor ownedCursor;

    HudTransformCursor() {
        this(cursor -> {
            if (Gdx.graphics != null) Gdx.graphics.setSystemCursor(cursor);
        });
    }

    HudTransformCursor(Consumer<Cursor.SystemCursor> systemCursor) {
        this.systemCursor = Objects.requireNonNull(systemCursor, "systemCursor");
    }

    static Cursor.SystemCursor cursorFor(HudTransformHandle handle) {
        if (handle == null) return null;
        return switch (handle) {
            case N, S -> Cursor.SystemCursor.VerticalResize;
            case E, W -> Cursor.SystemCursor.HorizontalResize;
            case NW, SE -> Cursor.SystemCursor.NWSEResize;
            case NE, SW -> Cursor.SystemCursor.NESWResize;
        };
    }

    void showFor(HudTransformHandle handle) {
        Cursor.SystemCursor next = cursorFor(handle);
        if (next == null) {
            clear();
            return;
        }
        if (ownedCursor == next) return;
        systemCursor.accept(next);
        ownedCursor = next;
    }

    void clear() {
        if (ownedCursor == null) return;
        systemCursor.accept(Cursor.SystemCursor.Arrow);
        ownedCursor = null;
    }

    Cursor.SystemCursor ownedCursor() { return ownedCursor; }
}
