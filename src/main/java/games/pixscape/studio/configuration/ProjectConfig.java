package games.pixscape.studio.configuration;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.io.StudioIO;
import games.pixscape.studio.ui.preview.PreviewTarget;

/**
 * STUDIO project configuration.
 * The runtime has its own exported RuntimeConfig + SceneMetaRuntime.
 */
public class ProjectConfig {
    public static final String STUDIO_PROJECT_KIND = "pixscape-studio-project";

    public String projectTitle;
    public String projectFileName;
    public String projectDirectoryPath;
    public String projectKind = STUDIO_PROJECT_KIND;
    public PreviewTarget previewTarget = PreviewTarget.DESKTOP;
    public String version = "1";

    public String exportRootPathDir;


    // --- Scenes (studio) --------------------------------------------------

    private final ObjectMap<String, SceneMeta> scenes = new ObjectMap<>();
    private String currentSceneName;
    public int nextSceneIndex = 1;

    // --- Project options ---------------------------------------------------

    public int glSamples = 0;
    public int previewWidth = 1280;
    public int previewHeight = 720;
    public boolean previewLandscape = true;

    private static ProjectConfig INSTANCE;

    public static ProjectConfig getInstance() {
        if (INSTANCE == null) INSTANCE = new ProjectConfig();
        return INSTANCE;
    }

    public static void setInstance(ProjectConfig config) {
        INSTANCE = config;
    }

    // --- Scenes -----------------------------------------------------------

    public Array<String> getSceneNames() {
        Array<String> names = new Array<>();
        for (ObjectMap.Entry<String, SceneMeta> e : scenes) names.add(e.key);
        names.sort(String::compareTo);
        return names;
    }

    public SceneMeta getCurrentSceneMeta() {
        if (currentSceneName == null) return null;
        return scenes.get(currentSceneName);
    }

    public String getCurrentSceneName() {
        return currentSceneName;
    }

    public SceneMeta getSceneMeta(String name) {
        return scenes.get(name);
    }

    public ObjectMap<String, SceneMeta> getScenesMap() {
        return scenes;
    }

    /**
     * scene1.json -> scene1
     */
    public static String sceneDirName(SceneMeta meta) {
        if (meta == null) return null;
        String file = meta.getFile();
        if (file == null || file.isEmpty()) return null;

        String trimmed = file;
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);

        int lastSlash = trimmed.lastIndexOf('/');
        String lastSegment = (lastSlash >= 0) ? trimmed.substring(lastSlash + 1) : trimmed;

        int dot = lastSegment.lastIndexOf('.');
        return (dot > 0) ? lastSegment.substring(0, dot) : lastSegment;
    }

    public String getSceneDirName(String sceneName) {
        return sceneDirName(getSceneMeta(sceneName));
    }

    public String canonicalSceneTag(String sceneName) {
        return canonicalSceneTagFor(getSceneMeta(sceneName));
    }

    public String canonicalSceneTagFor(SceneMeta meta) {
        return sceneDirName(meta);
    }

    public String canonicalSceneTagCurrent() {
        return canonicalSceneTagFor(getCurrentSceneMeta());
    }

    public void createSceneMeta(String name) {
        String fileName = StudioFs.withExt("scene" + (nextSceneIndex++), StudioFs.EXT_JSON);
        SceneMeta meta = new SceneMeta(name, fileName);
        scenes.put(name, meta);
        currentSceneName = name;
    }

    public void setCurrentSceneByName(String name) {
        if (name == null) {
            currentSceneName = null;
            return;
        }
        SceneMeta meta = scenes.get(name);
        if (meta == null) {
            String fileName = StudioFs.withExt("scene" + (nextSceneIndex++), StudioFs.EXT_JSON);
            meta = new SceneMeta(name, fileName);
            scenes.put(name, meta);
        }
        currentSceneName = name;
    }

    public void renameScene(String oldName, String newName) {
        if (oldName == null || newName == null) return;
        if (oldName.equals(newName)) return;

        SceneMeta meta = scenes.get(oldName);
        if (meta == null) {
            if (scenes.get(newName) == null) {
                String fileName = StudioFs.withExt("scene" + (nextSceneIndex++), StudioFs.EXT_JSON);
                scenes.put(newName, new SceneMeta(newName, fileName));
            }
            currentSceneName = newName;
            return;
        }

        scenes.remove(oldName);
        meta.name = newName;
        scenes.put(newName, meta);

        if (oldName.equals(currentSceneName)) currentSceneName = newName;
    }

    public boolean removeSceneMeta(String name) {
        if (name == null) return false;

        SceneMeta removed = scenes.remove(name);
        if (removed == null) return false;

        // If the active scene was just deleted
        if (name.equals(currentSceneName)) {

            if (scenes.size == 0) {
                currentSceneName = null;
            } else {
                // Choose the first remaining scene
                Array<String> names = getSceneNames();
                currentSceneName = names.first();
            }
        }
        return true;
    }

    // --- Defaults/validation ---------------------------------------------

    public void applyDefaults() {
        if (version == null || version.isBlank()) version = "1";
        if (projectFileName == null) projectFileName = "";
        if (projectDirectoryPath == null || projectDirectoryPath.isBlank()) {
            projectDirectoryPath = StudioFs.defaultProjectDirectoryPath(projectFileName);
        }
        if (glSamples != 0 && glSamples != 2 && glSamples != 4 && glSamples != 8) glSamples = 0;
        if (previewWidth <= 0) previewWidth = 1280;
        if (previewHeight <= 0) previewHeight = 720;

        // IMPORTANT:
        // Do not touch currentSceneName here.
        // If it is invalid, validateOrThrow() must surface the exact domain error.

        for (ObjectMap.Entry<String, SceneMeta> e : scenes) {
            SceneMeta m = e.value;
            if (m == null) continue;

            if (m.description == null) m.description = "";
            if (m.runtimeAvailability == null) m.runtimeAvailability = new SceneRuntimeAvailabilityData();
            if (m.runtimeAvailability.spriteAssetIds == null) m.runtimeAvailability.spriteAssetIds = new java.util.ArrayList<>();
            if (m.runtimeAvailability.animationAssetIds == null) m.runtimeAvailability.animationAssetIds = new java.util.ArrayList<>();
            if (m.runtimeAvailability.particleEffectPaths == null) m.runtimeAvailability.particleEffectPaths = new java.util.ArrayList<>();
            if (m.runtimeAvailability.prefabIds == null) m.runtimeAvailability.prefabIds = new java.util.ArrayList<>();
            if (m.runtimeAvailability.tiledTileAssetIds == null) m.runtimeAvailability.tiledTileAssetIds = new java.util.ArrayList<>();
            if (m.runtimeAvailability.tiledAnimationIds == null) m.runtimeAvailability.tiledAnimationIds = new java.util.ArrayList<>();
            if (m.pixelsPerMeter <= 0f) m.pixelsPerMeter = 100f;

            if (m.ambientMulR <= 0f) m.ambientMulR = 1f;
            if (m.ambientMulG <= 0f) m.ambientMulG = 1f;
            if (m.ambientMulB <= 0f) m.ambientMulB = 1f;

            if (Float.isNaN(m.ambientIntensity)) {
                boolean defaultMul = approx1(m.ambientMulR)
                        && approx1(m.ambientMulG)
                        && approx1(m.ambientMulB);

                if (defaultMul) {
                    m.ambientIntensity = 0f;
                    m.ambientColorR = defaultAmbientColor();
                    m.ambientColorG = defaultAmbientColor();
                    m.ambientColorB = defaultAmbientColorBlue();
                } else {
                    m.ambientIntensity = 1f;
                    m.ambientColorR = clamp01(m.ambientMulR);
                    m.ambientColorG = clamp01(m.ambientMulG);
                    m.ambientColorB = clamp01(m.ambientMulB);
                }
            }

            if (Float.isNaN(m.ambientColorR)) m.ambientColorR = defaultAmbientColor();
            if (Float.isNaN(m.ambientColorG)) m.ambientColorG = defaultAmbientColor();
            if (Float.isNaN(m.ambientColorB)) m.ambientColorB = defaultAmbientColorBlue();
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private static boolean approx1(float v) {
        return Math.abs(v - 1f) < 0.0001f;
    }

    private static float defaultAmbientColor() {
        return 0.20f;
    }

    private static float defaultAmbientColorBlue() {
        return 0.35f;
    }

    // --- IO ---------------------------------------------------------------

    public static final class ProjectIO {
        private static final Json json = new Json();

        static {
            json.setUsePrototypes(false);
            json.setOutputType(JsonWriter.OutputType.json);
            json.setIgnoreUnknownFields(true);
        }

        public static void saveProject(ProjectConfig cfg, FileHandle file) {
            if (cfg == null) throw new IllegalArgumentException("cfg is null");
            if (file == null) throw new IllegalArgumentException("file is null");

            if (cfg.projectKind == null || cfg.projectKind.isBlank()) {
                cfg.projectKind = STUDIO_PROJECT_KIND;
            }

            if (file.parent() != null) {
                cfg.projectDirectoryPath = file.parent().path();
            }
            cfg.applyDefaults();
            validateOrThrow(cfg, file.path());

            StudioIO.writeAtomic(file, out -> {
                try (var w = new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8)) {
                    w.write(json.prettyPrint(cfg));
                    w.flush();
                }
            });
        }

        public static ProjectConfig loadProject(FileHandle file) {
            if (file == null) throw new IllegalArgumentException("file is null");

            String text = file.readString("UTF-8");
            com.badlogic.gdx.utils.JsonValue root =
                    validateRawProjectOrThrow(text, file.path());

            ProjectConfig cfg = json.readValue(ProjectConfig.class, root);
            if (cfg == null) {
                throw new RuntimeException("Invalid project file (null): " + file.path());
            }

            if (file.parent() != null) {
                cfg.projectDirectoryPath = file.parent().path();
            }
            cfg.applyDefaults();
            validateOrThrow(cfg, file.path());

            return cfg;
        }

        private static com.badlogic.gdx.utils.JsonValue validateRawProjectOrThrow(
                String jsonText, String path) {
            com.badlogic.gdx.utils.JsonValue root;
            try {
                root = new com.badlogic.gdx.utils.JsonReader().parse(jsonText);
            } catch (Exception ex) {
                throw new RuntimeException("Invalid project JSON in: " + path, ex);
            }
            if (root == null || !root.has("projectKind")) {
                throw new RuntimeException("Missing project kind in: " + path);
            }

            String kind = root.getString("projectKind", null);

            if (kind == null || kind.isBlank()) {
                throw new RuntimeException("Missing project kind in: " + path);
            }

            if (!STUDIO_PROJECT_KIND.equals(kind)) {
                throw new RuntimeException("Unsupported project kind '" + kind + "' in: " + path);
            }

            com.badlogic.gdx.utils.JsonValue scenes = root.get("scenes");
            if (scenes == null || !scenes.isObject()) {
                throw new RuntimeException("Missing scenes map in: " + path);
            }
            for (com.badlogic.gdx.utils.JsonValue scene = scenes.child;
                 scene != null; scene = scene.next) {
                requirePositiveRawInt(scene, "nextEntityStableId", path);
                requirePositiveRawInt(scene, "nextPhysicsShapeId", path);
            }
            return root;
        }

        private static void requirePositiveRawInt(
                com.badlogic.gdx.utils.JsonValue scene, String field, String path) {
            com.badlogic.gdx.utils.JsonValue value = scene.get(field);
            if (value == null || !value.isNumber() || value.asInt() <= 0) {
                throw new RuntimeException("Scene '" + scene.name + "' requires a positive "
                        + field + " in: " + path);
            }
        }

        private static void validateOrThrow(ProjectConfig cfg, String path) {
            if (cfg.projectKind == null || cfg.projectKind.isBlank())
                throw new RuntimeException("Missing project kind in: " + path);
            if (!STUDIO_PROJECT_KIND.equals(cfg.projectKind))
                throw new RuntimeException("Unsupported project kind '" + cfg.projectKind + "' in: " + path);
            if (cfg.projectTitle == null || cfg.projectTitle.isBlank())
                throw new RuntimeException("Missing project title in: " + path);
            if (cfg.projectFileName == null || cfg.projectFileName.isBlank())
                throw new RuntimeException("Missing project file name in: " + path);
            if (cfg.projectDirectoryPath == null || cfg.projectDirectoryPath.isBlank())
                throw new RuntimeException("Missing project directory in: " + path);
            if (cfg.exportRootPathDir == null || cfg.exportRootPathDir.isBlank())
                throw new RuntimeException("Missing export root path in: " + path);
            if (cfg.version == null || cfg.version.isBlank())
                throw new RuntimeException("Missing project version in: " + path);
            if (!"1".equals(cfg.version))
                throw new RuntimeException("Unsupported project version '" + cfg.version + "' in: " + path);
            if (cfg.glSamples != 0 && cfg.glSamples != 2 && cfg.glSamples != 4 && cfg.glSamples != 8)
                throw new RuntimeException("Invalid glSamples '" + cfg.glSamples + "' in: " + path);
            if (cfg.currentSceneName == null || cfg.currentSceneName.isBlank())
                throw new RuntimeException("Missing current scene name in: " + path);
            if (cfg.scenes.size == 0)
                throw new RuntimeException("Project has no scenes in: " + path);
            for (ObjectMap.Entries<String, SceneMeta> entries = cfg.scenes.entries(); entries.hasNext(); ) {
                ObjectMap.Entry<String, SceneMeta> entry = entries.next();
                SceneMeta scene = entry.value;
                if (scene == null || scene.nextEntityStableId <= 0
                        || scene.nextPhysicsShapeId <= 0) {
                    throw new RuntimeException("Scene '" + entry.key
                            + "' has invalid identity high-water metadata in: " + path);
                }
            }

            SceneMeta currentMeta = cfg.scenes.get(cfg.currentSceneName);
            if (currentMeta == null)
                throw new RuntimeException("Current scene '" + cfg.currentSceneName + "' is not declared in scenes map: " + path);
            if (currentMeta.getName() == null || currentMeta.getName().isBlank())
                throw new RuntimeException("Current scene has missing name in: " + path);
            if (currentMeta.getFile() == null || currentMeta.getFile().isBlank())
                throw new RuntimeException("Current scene '" + cfg.currentSceneName + "' has missing file in: " + path);
        }
    }
}
