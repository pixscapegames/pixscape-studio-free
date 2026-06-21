package games.pixscape.studio.service;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;

/**
 * Local cache for Studio tools for standalone textures (orig/images).
 * <p>
 * The runtime must not depend on this concept: standalone textures
 * are packed into atlases before execution.
 */
public final class StandaloneTextureCache {

    private static final ObjectMap<String, Texture> path2tex = new ObjectMap<>();

    private StandaloneTextureCache() {
    }

    public static Texture get(String relPath) {
        if (relPath == null || relPath.isEmpty()) return null;

        Texture tex = path2tex.get(relPath);
        if (tex != null) return tex;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || StudioFs.DIR_ORIG_IMAGES == null) {
            return null;
        }

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);
        FileHandle rootImages = projectDir.child(StudioFs.DIR_ORIG_IMAGES);
        FileHandle file = rootImages.child(relPath);

        if (!file.exists()) {
            Gdx.app.error("StandaloneTextureCache", "Texture file not found: " + file.path());
            return null;
        }

        tex = new Texture(file);
        path2tex.put(relPath, tex);
        return tex;
    }

    public static Texture getOrLoadProjectRelative(String projectRelPath) {
        if (projectRelPath == null || projectRelPath.isBlank()) return null;

        Texture t = path2tex.get(projectRelPath);
        if (t != null) return t;

        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg.projectFileName == null || cfg.projectFileName.isBlank()) return null;

        FileHandle file = StudioFs.requireStudioProjectDir(cfg).child(projectRelPath);
        if (!file.exists()) return null;
        if (file.isDirectory()) return null;

        t = new Texture(file);
        path2tex.put(projectRelPath, t);
        return t;
    }

    public static Array<Texture> all() {
        Array<Texture> out = new Array<>(path2tex.size);
        for (ObjectMap.Entry<String, Texture> e : path2tex.entries()) {
            out.add(e.value);
        }
        return out;
    }

    public static void clear(boolean disposeTextures) {
        if (disposeTextures) {
            for (Texture t : path2tex.values()) {
                t.dispose();
            }
        }
        path2tex.clear();
    }
}
