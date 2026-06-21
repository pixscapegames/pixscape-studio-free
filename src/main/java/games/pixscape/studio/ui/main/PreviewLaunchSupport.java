package games.pixscape.studio.ui.main;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class PreviewLaunchSupport {

    private static final String DEFAULT_ERROR_MESSAGE = "Preview could not be launched.";

    private PreviewLaunchSupport() {
    }

    static boolean launchKeepingEditorAlive(
            boolean dirty,
            Runnable saveAction,
            Runnable launchPreviewAction,
            BiConsumer<String, Throwable> technicalLogger,
            Consumer<String> popupConsumer
    ) {
        Objects.requireNonNull(saveAction, "saveAction");
        Objects.requireNonNull(launchPreviewAction, "launchPreviewAction");
        Objects.requireNonNull(technicalLogger, "technicalLogger");
        Objects.requireNonNull(popupConsumer, "popupConsumer");

        try {
            if (dirty) {
                saveAction.run();
            }
            launchPreviewAction.run();
            return true;
        } catch (RuntimeException ex) {
            if (isInternalInvariantFailure(ex)) {
                throw ex;
            }

            technicalLogger.accept("Preview launch failed", ex);
            popupConsumer.accept(userMessageFor(ex));
            return false;
        }
    }

    static boolean isInternalInvariantFailure(RuntimeException ex) {
        return ex instanceof IllegalStateException;
    }

    static String userMessageFor(Throwable ex) {
        if (ex == null) return DEFAULT_ERROR_MESSAGE;
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return DEFAULT_ERROR_MESSAGE;
        return message;
    }
}
