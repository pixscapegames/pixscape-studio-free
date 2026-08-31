package games.pixscape.studio.configuration;

import games.pixscape.runtime.loading.SceneMetaRuntime;

public final class SceneMeta extends SceneMetaRuntime {
    public String description; // optional, for the UI

    public float ambientColorR = Float.NaN;
    public float ambientColorG = Float.NaN;
    public float ambientColorB = Float.NaN;
    public float ambientIntensity = Float.NaN;
    public SceneRuntimeAvailabilityData runtimeAvailability = new SceneRuntimeAvailabilityData();

    public SceneMeta(String name, String file) {
        super(name, file);
    }

    public SceneMeta() {
        super();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
