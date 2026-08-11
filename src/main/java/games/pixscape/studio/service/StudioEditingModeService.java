package games.pixscape.studio.service;

import games.pixscape.studio.event.EventFlow;

/** Single Studio authority for the active interactive editing context. */
public final class StudioEditingModeService {
    private boolean tiledActive;
    private boolean physicsActive;
    private boolean spatialActive;
    private boolean lightsActive;
    private StudioEditingMode currentMode = StudioEditingMode.NORMAL;

    public StudioEditingMode getCurrentMode() {
        return currentMode;
    }

    public void setMode(StudioEditingMode mode, int sourceTag) {
        setModeActive(mode, true, sourceTag);
    }

    public void setModeActive(StudioEditingMode mode, boolean active, int sourceTag) {
        if (mode == null) return;
        switch (mode) {
            case NORMAL -> {
                if (active) clearContexts();
            }
            case TILED -> tiledActive = active;
            case PHYSICS -> physicsActive = active;
            case SPATIAL -> spatialActive = active;
            // No dedicated Lights tool exists yet. Its future entry/exit point must call this service.
            case LIGHTS -> lightsActive = active;
        }
        publishIfChanged(sourceTag);
    }

    public void reset(int sourceTag) {
        clearContexts();
        publishIfChanged(sourceTag);
    }

    private void clearContexts() {
        tiledActive = false;
        physicsActive = false;
        spatialActive = false;
        lightsActive = false;
    }

    private void publishIfChanged(int sourceTag) {
        StudioEditingMode resolved = resolveMode();
        if (resolved == currentMode) return;
        currentMode = resolved;
        EventFlow.i().publish(new EventFlow.StudioEditingModeChanged(currentMode, sourceTag));
    }

    private StudioEditingMode resolveMode() {
        if (lightsActive) return StudioEditingMode.LIGHTS;
        if (spatialActive) return StudioEditingMode.SPATIAL;
        if (physicsActive) return StudioEditingMode.PHYSICS;
        if (tiledActive) return StudioEditingMode.TILED;
        return StudioEditingMode.NORMAL;
    }
}
