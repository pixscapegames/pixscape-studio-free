package games.pixscape.studio.logging;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.engine.PixscapeEngine;

public final class StudioLogLevel {
    public enum Selection {
        ALL(Application.LOG_DEBUG),
        DEBUG(Application.LOG_DEBUG),
        INFO(Application.LOG_INFO),
        ERROR(Application.LOG_ERROR),
        NONE(Application.LOG_NONE);

        private final int applicationLogLevel;

        Selection(int applicationLogLevel) {
            this.applicationLogLevel = applicationLogLevel;
        }

        public int applicationLogLevel() {
            return applicationLogLevel;
        }
    }

    private static Selection selected = Selection.INFO;
    private static PixscapeEngine activePreviewEngine;

    private StudioLogLevel() {
    }

    public static synchronized Selection selected() {
        return selected;
    }

    public static synchronized int applicationLogLevel() {
        return selected.applicationLogLevel();
    }

    public static void setSelected(Selection selection) {
        PixscapeEngine engine;
        int logLevel;
        synchronized (StudioLogLevel.class) {
            selected = selection == null ? Selection.INFO : selection;
            logLevel = selected.applicationLogLevel();
            engine = activePreviewEngine;
        }

        applyToGdx(logLevel);
        if (engine != null) {
            engine.setLogLevel(logLevel);
        }
    }

    public static void applyCurrentToGdx() {
        applyToGdx(applicationLogLevel());
    }

    public static void configure(PixscapeEngine engine) {
        if (engine != null) {
            engine.setLogLevel(applicationLogLevel());
        }
    }

    public static synchronized void setActivePreviewEngine(PixscapeEngine engine) {
        activePreviewEngine = engine;
        if (engine != null) {
            engine.setLogLevel(selected.applicationLogLevel());
        }
    }

    public static synchronized void clearActivePreviewEngine(PixscapeEngine engine) {
        if (activePreviewEngine == engine) {
            activePreviewEngine = null;
        }
    }

    private static void applyToGdx(int logLevel) {
        if (Gdx.app != null) {
            Gdx.app.setLogLevel(logLevel);
        }
    }
}
