package games.pixscape.studio.service;

import games.pixscape.studio.event.EventFlow;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/** Single Studio authority for the active interactive editing context. */
public final class StudioEditingModeService {
    public interface HudTestModeController {
        boolean canEnter();
        boolean enter();
        void exit();
        boolean isActive();
    }
    enum DocumentContext {
        NONE,
        SCENE,
        HUD
    }

    private boolean tiledActive;
    private boolean physicsActive;
    private boolean spatialActive;
    private boolean lightsActive;
    private DocumentContext documentContext = DocumentContext.NONE;
    private StudioEditingMode currentMode = StudioEditingMode.NORMAL;
    private Consumer<StudioEditingMode> activeSceneSubmodeSink;
    private HudTestModeController hudTestModeController;
    private final List<Runnable> listeners = new ArrayList<>();

    public StudioEditingMode getCurrentMode() {
        return currentMode;
    }

    /** Keeps the attached Scene context authoritative while this service projects effective mode. */
    public void setActiveSceneSubmodeSink(Consumer<StudioEditingMode> sink) {
        activeSceneSubmodeSink = sink;
    }

    /** Central availability rule for every action that mutates the scene/world editor. */
    public boolean allowsWorldEditingActions() {
        return documentContext == DocumentContext.SCENE;
    }

    public boolean hasActiveSceneDocument() {
        return documentContext == DocumentContext.SCENE;
    }

    public boolean hasActiveHudDocument() {
        return documentContext == DocumentContext.HUD;
    }

    public void bindHudTestModeController(HudTestModeController controller) {
        if (hudTestModeController != null && hudTestModeController.isActive()) {
            hudTestModeController.exit();
        }
        hudTestModeController = controller;
        notifyListeners();
    }

    public boolean isHudTestMode() {
        return documentContext == DocumentContext.HUD
                && hudTestModeController != null && hudTestModeController.isActive();
    }

    public boolean canEnterHudTestMode() {
        return documentContext == DocumentContext.HUD
                && hudTestModeController != null && hudTestModeController.canEnter();
    }

    /** Transactional mode request: failed entry leaves the current authoring mode untouched. */
    public boolean setHudTestMode(boolean test) {
        if (hudTestModeController == null || documentContext != DocumentContext.HUD) return false;
        boolean changed;
        if (test) {
            if (hudTestModeController.isActive()) return true;
            changed = hudTestModeController.canEnter() && hudTestModeController.enter();
        } else {
            changed = hudTestModeController.isActive();
            hudTestModeController.exit();
        }
        notifyListeners();
        return test ? changed && hudTestModeController.isActive()
                : !hudTestModeController.isActive();
    }

    public boolean toggleHudTestMode() {
        return setHudTestMode(!isHudTestMode());
    }

    public void addListener(Runnable listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** Refreshes controls after a resource or lifecycle boundary forced the test instance closed. */
    public void refreshHudTestMode() {
        notifyListeners();
    }

    /** Compatibility projection used only by active-document activation. */
    public void activateHudDocument(int sourceTag) {
        boolean contextChanged = documentContext != DocumentContext.HUD;
        documentContext = DocumentContext.HUD;
        clearContexts();
        publishIfChanged(sourceTag, contextChanged);
    }

    /** Restores the one Scene document and its last effective Scene submode atomically. */
    public void activateSceneDocument(StudioEditingMode sceneMode, int sourceTag) {
        exitHudTestMode();
        boolean contextChanged = documentContext != DocumentContext.SCENE;
        documentContext = DocumentContext.SCENE;
        clearContexts();
        setSceneModeFlag(sceneMode);
        publishIfChanged(sourceTag, contextChanged);
    }

    /** Projects the valid state where no editor document owns the center workspace. */
    public void deactivateDocument(int sourceTag) {
        exitHudTestMode();
        boolean contextChanged = documentContext != DocumentContext.NONE;
        documentContext = DocumentContext.NONE;
        clearContexts();
        publishIfChanged(sourceTag, contextChanged);
    }

    public void setMode(StudioEditingMode mode, int sourceTag) {
        setModeActive(mode, true, sourceTag);
    }

    public void setModeActive(StudioEditingMode mode, boolean active, int sourceTag) {
        if (mode == null) return;
        if (mode == StudioEditingMode.HUD) {
            throw new IllegalArgumentException("HUD context is controlled by active editor documents.");
        }
        if (documentContext != DocumentContext.SCENE) return;
        switch (mode) {
            case NORMAL -> {
                if (active) clearContexts();
            }
            case TILED -> tiledActive = active;
            case PHYSICS -> physicsActive = active;
            case SPATIAL -> spatialActive = active;
            // No dedicated Lights tool exists yet. Its future entry/exit point must call this service.
            case LIGHTS -> lightsActive = active;
            case HUD -> throw new IllegalStateException("Handled above.");
        }
        publishIfChanged(sourceTag, false);
    }

    public void reset(int sourceTag) {
        if (documentContext != DocumentContext.SCENE) return;
        clearContexts();
        publishIfChanged(sourceTag, false);
    }

    private void clearContexts() {
        tiledActive = false;
        physicsActive = false;
        spatialActive = false;
        lightsActive = false;
    }

    private void setSceneModeFlag(StudioEditingMode mode) {
        if (mode == null || mode == StudioEditingMode.NORMAL || mode == StudioEditingMode.HUD) return;
        switch (mode) {
            case TILED -> tiledActive = true;
            case PHYSICS -> physicsActive = true;
            case SPATIAL -> spatialActive = true;
            case LIGHTS -> lightsActive = true;
            default -> { }
        }
    }

    private void publishIfChanged(int sourceTag, boolean contextChanged) {
        StudioEditingMode resolved = resolveMode();
        if (documentContext == DocumentContext.SCENE && activeSceneSubmodeSink != null) {
            activeSceneSubmodeSink.accept(resolved);
        }
        if (!contextChanged && resolved == currentMode) return;
        currentMode = resolved;
        EventFlow.i().publish(new EventFlow.StudioEditingModeChanged(currentMode, sourceTag));
        notifyListeners();
    }

    private void exitHudTestMode() {
        if (hudTestModeController != null && hudTestModeController.isActive()) {
            hudTestModeController.exit();
            notifyListeners();
        }
    }

    private void notifyListeners() {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private StudioEditingMode resolveMode() {
        if (documentContext == DocumentContext.HUD) return StudioEditingMode.HUD;
        if (lightsActive) return StudioEditingMode.LIGHTS;
        if (spatialActive) return StudioEditingMode.SPATIAL;
        if (physicsActive) return StudioEditingMode.PHYSICS;
        if (tiledActive) return StudioEditingMode.TILED;
        return StudioEditingMode.NORMAL;
    }
}
