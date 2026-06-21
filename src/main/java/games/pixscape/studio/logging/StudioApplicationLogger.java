package games.pixscape.studio.logging;

import com.badlogic.gdx.ApplicationLogger;

final class StudioApplicationLogger implements ApplicationLogger {
    private final ApplicationLogger previousLogger;

    StudioApplicationLogger(ApplicationLogger previousLogger) {
        this.previousLogger = previousLogger;
    }

    ApplicationLogger previousLogger() {
        return previousLogger;
    }

    @Override
    public void log(String tag, String message) {
        try {
            StudioLogCapture.capture(StudioLogCapture.Level.INFO, tag, message, null);
        } finally {
            if (previousLogger != null) previousLogger.log(tag, message);
        }
    }

    @Override
    public void log(String tag, String message, Throwable exception) {
        try {
            StudioLogCapture.capture(StudioLogCapture.Level.INFO, tag, message, exception);
        } finally {
            if (previousLogger != null) previousLogger.log(tag, message, exception);
        }
    }

    @Override
    public void error(String tag, String message) {
        try {
            StudioLogCapture.capture(StudioLogCapture.Level.ERROR, tag, message, null);
        } finally {
            if (previousLogger != null) previousLogger.error(tag, message);
        }
    }

    @Override
    public void error(String tag, String message, Throwable exception) {
        try {
            StudioLogCapture.capture(StudioLogCapture.Level.ERROR, tag, message, exception);
        } finally {
            if (previousLogger != null) previousLogger.error(tag, message, exception);
        }
    }

    @Override
    public void debug(String tag, String message) {
        try {
            StudioLogCapture.capture(StudioLogCapture.Level.DEBUG, tag, message, null);
        } finally {
            if (previousLogger != null) previousLogger.debug(tag, message);
        }
    }

    @Override
    public void debug(String tag, String message, Throwable exception) {
        try {
            StudioLogCapture.capture(StudioLogCapture.Level.DEBUG, tag, message, exception);
        } finally {
            if (previousLogger != null) previousLogger.debug(tag, message, exception);
        }
    }
}
