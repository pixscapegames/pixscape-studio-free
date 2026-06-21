package games.pixscape.studio.logging;

import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Gdx;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class StudioLogCapture {
    public enum Level {
        DEBUG,
        INFO,
        ERROR
    }

    public interface Listener {
        void onLogEntry(Entry entry);
    }

    public record Entry(Level level, String tag, String message, Throwable throwable, String text) {
    }

    private static final int DEFAULT_MAX_ENTRIES = 2000;

    private static final Deque<Entry> entries = new ArrayDeque<>();
    private static final List<Listener> listeners = new ArrayList<>();

    private static int maxEntries = DEFAULT_MAX_ENTRIES;
    private static StudioApplicationLogger installedLogger;

    private StudioLogCapture() {
    }

    public static synchronized void install() {
        if (Gdx.app == null) return;

        ApplicationLogger current = Gdx.app.getApplicationLogger();
        if (current instanceof StudioApplicationLogger) {
            installedLogger = (StudioApplicationLogger) current;
            return;
        }

        installedLogger = new StudioApplicationLogger(current);
        Gdx.app.setApplicationLogger(installedLogger);
    }

    public static synchronized void restorePreviousLogger() {
        if (Gdx.app == null || installedLogger == null) return;

        if (Gdx.app.getApplicationLogger() == installedLogger) {
            Gdx.app.setApplicationLogger(installedLogger.previousLogger());
        }
        installedLogger = null;
    }

    public static synchronized void setMaxEntries(int maxEntries) {
        StudioLogCapture.maxEntries = Math.max(100, maxEntries);
        trim();
    }

    public static void capture(Level level, String tag, String message, Throwable throwable) {
        Entry entry = new Entry(
                level == null ? Level.INFO : level,
                safe(tag),
                safe(message),
                throwable,
                format(level, tag, message, throwable)
        );

        List<Listener> snapshot;
        synchronized (StudioLogCapture.class) {
            entries.addLast(entry);
            trim();
            snapshot = new ArrayList<>(listeners);
        }

        for (Listener listener : snapshot) {
            listener.onLogEntry(entry);
        }
    }

    public static synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public static synchronized void clear() {
        entries.clear();
    }

    public static synchronized void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private static void trim() {
        while (entries.size() > maxEntries) entries.removeFirst();
    }

    private static String format(Level level, String tag, String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('[').append(level == null ? Level.INFO : level).append("] ");
        if (tag != null && !tag.isBlank()) {
            sb.append(tag).append(": ");
        }
        sb.append(message == null ? "null" : message);

        if (throwable != null) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            sb.append('\n').append(sw);
        }

        return sb.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
