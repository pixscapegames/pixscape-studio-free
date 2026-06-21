package games.pixscape.studio.ui.log;

import com.badlogic.gdx.Gdx;

public final class StudioLog {
    private static final String TAG = "Studio";

    private StudioLog() {
    }

    public static void info(String msg) {
        if (Gdx.app != null) Gdx.app.log(TAG, msg);
    }

    public static void warn(String msg) {
        if (Gdx.app != null) Gdx.app.log(TAG, "[WARN] " + msg);
    }

    public static void error(String msg) {
        if (Gdx.app != null) Gdx.app.error(TAG, msg);
    }
}
