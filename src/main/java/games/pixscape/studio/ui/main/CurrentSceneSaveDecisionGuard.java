package games.pixscape.studio.ui.main;

import java.util.function.Consumer;

final class CurrentSceneSaveDecisionGuard {

    interface DecisionDialog {
        void show(String title, String message, Runnable save, Runnable dontSave, Runnable cancel);
    }

    interface SaveOperation {
        void save(Runnable onSuccess, Consumer<Throwable> onFailure);
    }

    private CurrentSceneSaveDecisionGuard() {
    }

    static void request(boolean saveRequired,
                        String title,
                        String message,
                        Runnable continuation,
                        Runnable onCancel,
                        Consumer<Throwable> onSaveFailure,
                        DecisionDialog dialog,
                        SaveOperation saveOperation) {
        if (!saveRequired) {
            continuation.run();
            return;
        }

        Runnable cancel = onCancel != null ? onCancel : () -> { };
        Consumer<Throwable> saveFailure = onSaveFailure != null ? onSaveFailure : throwable -> { };
        dialog.show(
                title,
                message,
                () -> saveOperation.save(continuation, saveFailure),
                continuation,
                cancel
        );
    }
}
