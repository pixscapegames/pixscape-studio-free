package games.pixscape.studio.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import games.pixscape.studio.ui.main.SaveProgressDialog;

public final class SaveProgressRunner {

    @FunctionalInterface
    public interface ProgressHandle {
        void update(float progress, String message);
    }

    @FunctionalInterface
    public interface StepAction {
        void run(ProgressHandle progress, Runnable next);
    }

    public record Step(float progress, String message, StepAction action) {
        public static Step sync(float progress, String message, Runnable action) {
            return new Step(progress, message, (ignoredProgress, next) -> {
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

    public SaveProgressRunner(Stage uiStage) {
        this.uiStage = uiStage;
    }

    public void run(Array<Step> steps, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        if (steps == null || steps.size == 0) {
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

        if (index >= steps.size) {
            dialog.hide();
            if (onSuccess != null) {
                onSuccess.run();
            }
            return;
        }

        Step step = steps.get(index);
        dialog.updateProgress(step.progress(), step.message());

        // leave one frame for the UI to show the step before launching the action
        Gdx.app.postRunnable(() -> {
            try {
                step.action().run(dialog::updateProgress, () ->
                        Gdx.app.postRunnable(() -> runStep(steps, index + 1, onSuccess, onError)));
            } catch (Throwable t) {
                dialog.hide();
                if (onError != null) {
                    onError.accept(t);
                }
            }
        });
    }
}
