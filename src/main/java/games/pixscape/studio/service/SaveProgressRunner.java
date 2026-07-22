package games.pixscape.studio.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.ui.main.SaveProgressDialog;

import java.util.function.Consumer;

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
    private final games.pixscape.studio.ui.main.SaveProgressDialog dialog = new SaveProgressDialog();
    private boolean finished;

    public SaveProgressRunner(Stage uiStage) {
        this.uiStage = uiStage;
    }

    public void run(Array<Step> steps, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        finished = false;
        if (steps == null || steps.size == 0) {
            finished = true;
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        dialog.updateProgress(0f, "Preparing save...");
        dialog.show(uiStage);

        runStep(steps, 0, onSuccess, onError);
    }

    private void runStep(Array<Step> steps,
                         int index,
                         Runnable onSuccess,
                         java.util.function.Consumer<Throwable> onError) {

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
                                Gdx.app.postRunnable(() -> runStep(steps, index + 1, onSuccess, onError)),
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
