package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.StandaloneTextureCache;

public final class AssetPreviewCache {

    private static final ObjectMap<String, Texture> cache = new ObjectMap<>();

    private AssetPreviewCache() {
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    public static TextureRegion get(AssetNode node) {
        if (node == null) return null;

        String key = node.root + ":" + node.kind + ":" + node.path;
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

    /** Resolves source frames once at selection/reload time, in the browser's filename order. */
    public static Array<FileHandle> animationFrameFiles(FileHandle source) {
        Array<FileHandle> files = new Array<>();
        if (source == null || !source.exists()) return files;
        if (!source.isDirectory()) {
            if ("png".equalsIgnoreCase(source.extension())) files.add(source);
        } else {
            for (FileHandle child : source.list()) {
                if (!child.isDirectory() && "png".equalsIgnoreCase(child.extension())) files.add(child);
            }
            files.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        }
        return files;
    }

    /** Returned textures are borrowed, never disposed by an Inspector or thumbnail. */
    public static Array<Texture> animationFrames(String sourceRelPath) {
        Array<Texture> frames = new Array<>();
        ProjectConfig cfg = ProjectConfig.getInstance();
        if (sourceRelPath == null || cfg == null || cfg.projectFileName == null) return frames;
        FileHandle source = StudioFs.requireStudioProjectDir(cfg).child(sourceRelPath);
        for (FileHandle file : animationFrameFiles(source)) {
            String path = source.isDirectory() ? sourceRelPath + "/" + file.name() : sourceRelPath;
            Texture texture = StandaloneTextureCache.getOrLoadProjectRelative(path);
            if (texture == null) return new Array<>();
            frames.add(texture);
        }
        return frames;
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
                case IMAGE, NORMAL_MAP -> loadImage(cfg, projectDir, data);
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

        Array<FileHandle> pngFrames = animationFrameFiles(animationHandle);

        if (pngFrames.size == 0) return null;
        return pngFrames.first();
    }
}
