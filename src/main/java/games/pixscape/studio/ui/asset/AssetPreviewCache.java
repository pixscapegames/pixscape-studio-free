package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;

public final class AssetPreviewCache {

    private static final ObjectMap<String, Texture> cache = new ObjectMap<>();

    private AssetPreviewCache() {
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    public static TextureRegion get(AssetNode node) {
        if (node == null) return null;

        String key = node.kind + ":" + node.path;
        Texture tex = cache.get(key);

        if (tex == null) {
            tex = load(node);
            if (tex != null) {
                cache.put(key, tex);
            }
        }

        return tex != null ? new TextureRegion(tex) : null;
    }

    public static void clear() {
        for (Texture t : cache.values()) {
            t.dispose();
        }
        cache.clear();
    }

    // ------------------------------------------------------------------------
    // Internal load
    // ------------------------------------------------------------------------

    private static Texture load(AssetNode data) {
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (cfg == null || cfg.projectFileName == null || cfg.projectFileName.isBlank()) return null;

        FileHandle projectDir = StudioFs.requireStudioProjectDir(cfg);

        try {
            return switch (data.kind) {
                case IMAGE -> loadImage(cfg, projectDir, data);
                case ANIMATION -> loadAnimationPreview(cfg, projectDir, data);
                // FUTUR : PARTICLE, REGION, etc.
                default -> null;
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------------
    // Image preview
    // ------------------------------------------------------------------------

    private static Texture loadImage(ProjectConfig cfg,
                                     FileHandle projectDir,
                                     AssetNode data) {

        FileHandle baseDir = switch (data.root) {
            case IMAGES -> projectDir.child(StudioFs.DIR_ORIG_IMAGES);
            case TILES -> projectDir.child(StudioFs.DIR_ORIG_TILES);
            default -> projectDir.child(StudioFs.DIR_ORIG_IMAGES);
        };
        FileHandle file = baseDir.child(data.path);

        if (!file.exists()) return null;

        Pixmap pm = new Pixmap(file);
        Texture tex = new Texture(pm);
        pm.dispose();

        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return tex;
    }

    // ------------------------------------------------------------------------
    // Animation preview (first frame)
    // ------------------------------------------------------------------------

    private static Texture loadAnimationPreview(ProjectConfig cfg,
                                                FileHandle projectDir,
                                                AssetNode data) {

        FileHandle animationHandle = projectDir
                .child(StudioFs.DIR_ORIG_ANIMATIONS)
                .child(data.path);
        FileHandle pngFile = resolveAnimationFrameFile(animationHandle);

        if (pngFile == null || !pngFile.exists()) return null;

        Pixmap frame = new Pixmap(pngFile);

        Texture tex = new Texture(frame);
        frame.dispose();

        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return tex;
    }

    static Pixmap extractAnimationFirstFramePixmap(
            ProjectConfig cfg,
            FileHandle projectDir,
            AssetNode data
    ) {
        FileHandle animationHandle = projectDir
                .child(StudioFs.DIR_ORIG_ANIMATIONS)
                .child(data.path);
        FileHandle pngFile = resolveAnimationFrameFile(animationHandle);

        if (pngFile == null || !pngFile.exists()) return null;

        return new Pixmap(pngFile); // ⚠️ le caller DOIT disposer
    }

    private static FileHandle resolveAnimationFrameFile(FileHandle animationHandle) {
        if (animationHandle == null || !animationHandle.exists()) return null;

        if (!animationHandle.isDirectory()) {
            String ext = animationHandle.extension();
            return ext != null && ext.equalsIgnoreCase("png") ? animationHandle : null;
        }

        Array<FileHandle> pngFrames = new Array<>();
        for (FileHandle child : animationHandle.list()) {
            if (child == null || child.isDirectory()) continue;
            String ext = child.extension();
            if (ext != null && ext.equalsIgnoreCase("png")) {
                pngFrames.add(child);
            }
        }

        if (pngFrames.size == 0) return null;
        pngFrames.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return pngFrames.first();
    }
}
