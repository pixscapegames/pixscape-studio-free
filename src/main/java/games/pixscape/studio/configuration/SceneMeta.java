package games.pixscape.studio.configuration;

import games.pixscape.runtime.loading.SceneMetaRuntime;

public final class SceneMeta extends SceneMetaRuntime {

    public enum EditorMode {
        ENTITY,
        TILE
    }

    public String description; // optional, for the UI

    // --- Physics / Editor (persisted per scene) ---------------------------
    public boolean showPhysicsFixtures = false;
    public boolean showPhysicsJoints = false;
    public EditorMode editorMode = EditorMode.ENTITY;
    public float ambientColorR = Float.NaN;
    public float ambientColorG = Float.NaN;
    public float ambientColorB = Float.NaN;
    public float ambientIntensity = Float.NaN;
    public SceneRuntimeAvailabilityData runtimeAvailability = new SceneRuntimeAvailabilityData();
    public int nextPrefabInstanceId = 1;

    public SceneMeta(String name, String file) {
        super(name, file);
        chunkSize = 16;
    }

    public SceneMeta() {
        super();
    }

    public String getName() {
        return name;
    }

    public String getFile() {
        return file;
    }

    public String getDescription() {
        return description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
