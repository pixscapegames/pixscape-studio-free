package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.utils.Array;

import java.util.Objects;
import java.util.function.Consumer;

final class ImportDialogApplySupport {

    private ImportDialogApplySupport() {
    }

    static ApplyResult tryApply(Array<ImportDialog.ImportItem> items, Consumer<Array<ImportDialog.ImportItem>> onApply) {
        Objects.requireNonNull(onApply, "onApply");
        try {
            onApply.accept(items);
            return ApplyResult.success();
        } catch (IllegalArgumentException ex) {
            String message = userMessageFor(ex);
            return ApplyResult.failure(message);
        }
    }

    static boolean applyAndCloseOnSuccess(
            Array<ImportDialog.ImportItem> items,
            Consumer<Array<ImportDialog.ImportItem>> onApply,
            Consumer<String> inlineErrorConsumer,
            Runnable successCloseAction
    ) {
        Objects.requireNonNull(inlineErrorConsumer, "inlineErrorConsumer");
        Objects.requireNonNull(successCloseAction, "successCloseAction");

        if (onApply == null) {
            successCloseAction.run();
            return true;
        }

        ApplyResult applyResult = tryApply(items, onApply);
        if (!applyResult.success) {
            inlineErrorConsumer.accept(applyResult.errorMessage);
            return false;
        }

        successCloseAction.run();
        return true;
    }

    static String userMessageFor(Throwable ex) {
        if (ex == null) return "Import failed.";
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) return "Import failed.";
        return msg;
    }

    static final class ApplyResult {
        final boolean success;
        final String errorMessage;

        private ApplyResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        static ApplyResult success() {
            return new ApplyResult(true, null);
        }

        static ApplyResult failure(String errorMessage) {
            return new ApplyResult(false, errorMessage);
        }
    }
}