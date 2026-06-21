package games.pixscape.studio.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.configuration.ProjectConfig;

import java.util.Locale;
import java.nio.file.Paths;

public final class StudioFs {

    private StudioFs() {
    }

    public static final String STUDIO_HOME_DIR = System.getProperty("user.home") + "/.pixscape-studio";
    public static final String DEFAULT_USER_PROJECTS_DIR = Paths.get(System.getProperty("user.home"), "Pixscape Projects").toString();

    public static final String FILE_ASSETS_JSON = "assets.json";

    public static final String DIR_INPUT = "input";
    public static final String DIR_ORIG_IMAGES = "orig/images";
    public static final String DIR_ORIG_TILES = "orig/tiles";
    public static final String DIR_ORIG_ANIMATIONS = "orig/animations";
    public static final String DIR_ORIG_EFFECTS = "orig/effects";
    public static final String DIR_ORIG_SHADERS = "orig/shaders";
    public static final String DIR_ORIG_AUDIO = "orig/audio";
    public static final String DIR_ATLASES = "atlases";
    public static final String DIR_SCENES = "scenes";

    public static final String PREFIX_IMAGES = "images/";
    public static final String PREFIX_ANIMATIONS = "animations/";
    public static final String PREFIX_EFFECTS = "effects/";
    public static final String PREFIX_TILES = "tiles/";

    public static final String EXT_PNG = ".png";
    public static final String EXT_JPG = ".jpg";
    public static final String EXT_JPEG = ".jpeg";
    public static final String EXT_WEBP = ".webp";
    public static final String EXT_PARTICLE = ".p";
    public static final String EXT_ATLAS = ".atlas";
    public static final String EXT_JSON = ".json";

    public static final String DIR_PREFABS = "prefabs";
    public static final String EXT_PREFAB = ".pixprefab";

    public static FileHandle defaultUserProjectsRoot() {
        FileHandle root = Gdx.files.absolute(DEFAULT_USER_PROJECTS_DIR);
        if (!root.exists()) root.mkdirs();
        return root;
    }

    public static String defaultProjectDirectoryPath(String projectFileName) {
        String name = projectFileName;
        if (name == null || name.isBlank()) {
            name = "Untitled";
        }
        return Paths.get(DEFAULT_USER_PROJECTS_DIR, name).toString();
    }

    public static String requireProjectFileName(ProjectConfig cfg) {
        if (cfg == null) throw new IllegalStateException("No ProjectConfig instance.");
        String name = cfg.projectFileName;
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("ProjectConfig.projectFileName is blank.");
        }
        return name;
    }

    public static FileHandle requireStudioProjectDir(ProjectConfig cfg) {
        if (cfg == null) throw new IllegalStateException("No ProjectConfig instance.");
        String projectDirectoryPath = cfg.projectDirectoryPath;
        if (projectDirectoryPath == null || projectDirectoryPath.isBlank()) {
            projectDirectoryPath = defaultProjectDirectoryPath(requireProjectFileName(cfg));
            cfg.projectDirectoryPath = projectDirectoryPath;
        }
        return Gdx.files.absolute(projectDirectoryPath);
    }

    public static FileHandle requireStudioProjectFile(ProjectConfig cfg) {
        String fileName = requireProjectFileName(cfg) + EXT_JSON;
        return requireStudioProjectDir(cfg).child(fileName);
    }

    public static FileHandle requireAssetsFile(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(FILE_ASSETS_JSON);
    }

    public static FileHandle requireScenesDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_SCENES);
    }

    public static FileHandle requireOrigImagesDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_ORIG_IMAGES);
    }

    public static FileHandle requireOrigTilesDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_ORIG_TILES);
    }


    public static FileHandle requireOrigAnimationsDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_ORIG_ANIMATIONS);
    }


    public static FileHandle requireOrigEffectsDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_ORIG_EFFECTS);
    }


    public static FileHandle requireOrigShadersDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_ORIG_SHADERS);
    }


    public static FileHandle requireOrigAudioDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_ORIG_AUDIO);
    }

    public static FileHandle requireAtlasesDir(ProjectConfig cfg) {
        return requireStudioProjectDir(cfg).child(DIR_ATLASES);
    }

    public static FileHandle requireAtlasInputDir(ProjectConfig cfg, String sceneTag) {
        return requireAtlasesDir(cfg).child(DIR_INPUT).child(sceneTag);
    }

    public static FileHandle requirePrefabsDir(ProjectConfig cfg) {
        FileHandle dir = requireStudioProjectDir(cfg).child(DIR_PREFABS);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static FileHandle requirePrefabFile(ProjectConfig cfg, String prefabName) {
        if (prefabName == null || prefabName.trim().isEmpty()) {
            throw new IllegalArgumentException("prefabName is blank.");
        }
        return requirePrefabsDir(cfg).child(withExt(prefabName, EXT_PREFAB));
    }

    public static FileHandle requirePrefabPreviewFile(ProjectConfig cfg, String prefabName) {
        if (prefabName == null || prefabName.trim().isEmpty()) {
            throw new IllegalArgumentException("prefabName is blank.");
        }
        return requirePrefabsDir(cfg).child(removeExtension(prefabName) + ".preview.png");
    }

    public static String baseName(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }

    public static String fileNameFromPath(String path) {
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        return (slash >= 0) ? path.substring(slash + 1) : path;
    }

    public static String removeExtension(String path) {
        return baseName(path);
    }

    public static boolean isImageFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(EXT_PNG) || lower.endsWith(EXT_JPG) || lower.endsWith(EXT_JPEG) || lower.endsWith(EXT_WEBP);
    }

    public static boolean isParticleFile(String name) {
        if (name == null) return false;
        return normalizeExtension(name).equals("p");
    }

    public static String normalizeExtension(String pathOrName) {
        if (pathOrName == null) return "";
        int dot = pathOrName.lastIndexOf('.');
        if (dot < 0 || dot == pathOrName.length() - 1) return "";
        return pathOrName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    public static String logicalImage(String fileName) {
        return PREFIX_IMAGES + removeExtension(fileName);
    }

    public static String logicalAnimation(String animationName) {
        return PREFIX_ANIMATIONS + animationName;
    }

    public static String logicalEffect(String fileName) {
        return PREFIX_EFFECTS + removeExtension(fileName);
    }

    public static String sourceImage(String fileName) {
        return DIR_ORIG_IMAGES + "/" + fileName;
    }

    public static String sourceAnimationDir(String animationName) {
        return DIR_ORIG_ANIMATIONS + "/" + animationName;
    }

    public static String sourceEffect(String fileName) {
        return DIR_ORIG_EFFECTS + "/" + fileName;
    }

    public static String withExt(String baseName, String extensionWithDot) {
        if (baseName == null) return "";
        if (extensionWithDot == null || extensionWithDot.isEmpty()) return baseName;
        return baseName.endsWith(extensionWithDot) ? baseName : baseName + extensionWithDot;
    }
}
