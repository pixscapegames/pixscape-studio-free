package games.pixscape.studio.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.ui.main.SaveProgressDialog;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public final class SaveProgressRunner {

    @FunctionalInterface
    public interface ProgressHandle {
        void update(float progress, String message);
    }

    @FunctionalInterface
    public interface StepAction {
        void run(ProgressHandle progress,
                 Runnable next,
                 Consumer<Throwable> fail);
    }

    public record Step(float progress, String message, StepAction action) {
        public static Step sync(float progress, String message, Runnable action) {
            return new Step(progress, message, (ignoredProgress, next, ignoredFail) -> {
                action.run();
                next.run();
            });
        }

        public static Step async(float progress, String message, StepAction action) {
            return new Step(progress, message, action);
        }
    }

    private final Stage uiStage;
    private final SaveProgressDialog dialog;
    private final String initialMessage;
    private boolean finished;

    public SaveProgressRunner(Stage uiStage) {
        this(uiStage, new SaveProgressDialog(), "Preparing save...");
    }

    public SaveProgressRunner(Stage uiStage, String dialogTitle, String initialMessage) {
        this(uiStage, new SaveProgressDialog(dialogTitle, initialMessage), initialMessage);
        dialog.preventUserClose();
    }

    private SaveProgressRunner(Stage uiStage, SaveProgressDialog dialog, String initialMessage) {
        this.uiStage = uiStage;
        this.dialog = dialog;
        this.initialMessage = initialMessage;
    }

    public void run(Array<Step> steps, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        run(steps, onSuccess, onError, () -> false);
    }

    public void run(Array<Step> steps,
                    Runnable onSuccess,
                    java.util.function.Consumer<Throwable> onError,
                    BooleanSupplier finishAfterCurrentStep) {
        finished = false;
        if (steps == null || steps.size == 0) {
            finished = true;
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        dialog.updateProgress(0f, initialMessage);
        dialog.show(uiStage);

        Gdx.app.postRunnable(() -> runStep(
                steps, 0, onSuccess, onError, finishAfterCurrentStep
        ));
    }

    private void runStep(Array<Step> steps,
                         int index,
                         Runnable onSuccess,
                         java.util.function.Consumer<Throwable> onError,
                         BooleanSupplier finishAfterCurrentStep) {

        if (finished) return;

        if (index >= steps.size) {
            finishSuccessfully(onSuccess);
            return;
        }

        Step step = steps.get(index);
        dialog.updateProgress(step.progress(), step.message());

        // leave one frame for the UI to show the step before launching the action
        Gdx.app.postRunnable(() -> {
            try {
                step.action().run(dialog::updateProgress, () ->
                                Gdx.app.postRunnable(() -> {
                                    if (finishAfterCurrentStep.getAsBoolean()) {
                                        finishSuccessfully(onSuccess);
                                    } else {
                                        runStep(steps, index + 1, onSuccess, onError, finishAfterCurrentStep);
                                    }
                                }),
                        failure -> Gdx.app.postRunnable(() -> finishWithError(failure, onError)));
            } catch (Throwable t) {
                finishWithError(t, onError);
            }
        });
    }

    private void finishSuccessfully(Runnable onSuccess) {
        if (finished) return;
        finished = true;
        dialog.hide();
        if (onSuccess != null) {
            onSuccess.run();
        }
    }

    private void finishWithError(Throwable failure,
                                 java.util.function.Consumer<Throwable> onError) {
        if (finished) return;
        finished = true;
        dialog.hide();
        if (onError != null) {
            onError.accept(failure);
        }
    }
}
