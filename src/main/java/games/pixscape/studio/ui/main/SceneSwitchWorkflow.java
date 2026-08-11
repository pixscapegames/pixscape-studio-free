package games.pixscape.studio.ui.main;

import java.util.function.Consumer;

final class SceneSwitchWorkflow {

    interface DecisionRequester {
        void request(String targetScene,
                     Runnable continuation,
                     Runnable onCancel,
                     Consumer<Throwable> onSaveFailure);
    }

    interface SceneChanger {
        void changeSceneNow(String targetScene);
    }

    private final DecisionRequester decisionRequester;
    private final SceneChanger sceneChanger;
    private final Runnable restoreSelector;
    private final Consumer<String> confirmSelector;
    private final Consumer<Throwable> showSaveFailure;
    private final Consumer<RuntimeException> showSceneChangeFailure;
    private final Consumer<Boolean> pendingChanged;
    private boolean pending;

    SceneSwitchWorkflow(DecisionRequester decisionRequester,
                        SceneChanger sceneChanger,
                        Runnable restoreSelector,
                        Consumer<String> confirmSelector,
                        Consumer<Throwable> showSaveFailure,
                        Consumer<RuntimeException> showSceneChangeFailure,
                        Consumer<Boolean> pendingChanged) {
        this.decisionRequester = decisionRequester;
        this.sceneChanger = sceneChanger;
        this.restoreSelector = restoreSelector;
        this.confirmSelector = confirmSelector;
        this.showSaveFailure = showSaveFailure;
        this.showSceneChangeFailure = showSceneChangeFailure;
        this.pendingChanged = pendingChanged;
    }

    void request(String targetScene) {
        if (pending) return;

        setPending(true);
        try {
            decisionRequester.request(
                    targetScene,
                    () -> activate(targetScene),
                    this::restoreAndFinish,
                    failure -> {
                        restoreAndFinish();
                        showSaveFailure.accept(failure);
                    }
            );
        } catch (RuntimeException ex) {
            restoreAndFinish();
            throw ex;
        }
    }

    boolean isPending() {
        return pending;
    }

    private void activate(String targetScene) {
        try {
            sceneChanger.changeSceneNow(targetScene);
            confirmSelector.accept(targetScene);
            finish();
        } catch (RuntimeException ex) {
            restoreAndFinish();
            showSceneChangeFailure.accept(ex);
        }
    }

    private void restoreAndFinish() {
        try {
            restoreSelector.run();
        } finally {
            finish();
        }
    }

    private void finish() {
        setPending(false);
    }

    private void setPending(boolean value) {
        pending = value;
        pendingChanged.accept(value);
    }
}
