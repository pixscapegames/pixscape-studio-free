package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasRegionMetadata;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.StandaloneTextureCache;

import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Resolves a Studio {@code assetId + atlasTag} to an immutable visual view.
 *
 * <p>An indexed atlas binding always wins. A null or blank atlas tag only
 * disables that atlas attempt; it does not disable standalone resolution.
 * Invalid asset IDs are rejected locally and are never passed to the strict
 * Runtime APIs.</p>
 *
 * <p>Standalone IMAGE and TILE assets use their metadata {@code sourceRelPath}
 * as an image file (PNG, JPG, JPEG or WEBP). TILE sources produced from a
 * spritesheet are the individual split images, never the preserved tileset
 * sheet. Directory tilesets likewise give each TILE its individual image while
 * the TILESET source remains null. TILESET is metadata/profile ownership and is
 * not standalone-drawable. PARTICLE keeps its separate
 * {@code effectPath + atlasTag} pipeline and is not resolved standalone here.</p>
 *
 * <p>An ANIMATION asset ID identifies the whole animation. Its atlas binding is
 * the ordered frame group. Standalone animation metadata points to a directory;
 * its PNG paths are compiled and sorted once, and textures are loaded lazily by
 * frame. Frame indexes clamp to the first frame below zero and the last frame
 * past the end. No path is inferred from logical paths, display names or atlas
 * region names.</p>
 *
 * <p>Positive and negative results are cached. Atlas cache validity is tied to
 * the exact {@link TextureAtlas} instance published under a tag. Standalone
 * entries observe the metadata instance, type and source path. Resolution is
 * allocation-free after warm-up for an unchanged publication and metadata
 * state.</p>
 */
public final class StudioAssetVisualResolver {

    public interface StandaloneAssetAccess {
        Texture resolveTexture(String projectRelativePath);

        String[] listPngFramePaths(String projectRelativeDirectory);
    }

    private static final String[] NO_FRAME_PATHS = new String[0];

    private final AtlasRuntimeService atlasRuntimeService;
    private final StandaloneAssetAccess standaloneAccess;
    private final ObjectMap<String, AtlasVisualBucket> atlasBuckets = new ObjectMap<>();
    private final IntMap<StandaloneVisualEntry> standaloneByAssetId = new IntMap<>();

    private IntFunction<AssetMeta> assetMetaLookup;
    private int atlasColdResolutions;
    private int standaloneColdResolutions;
    private int standaloneLoadAttempts;

    public StudioAssetVisualResolver(AtlasRuntimeService atlasRuntimeService,
                                     IntFunction<AssetMeta> assetMetaLookup,
                                     StandaloneAssetAccess standaloneAccess) {
        this.atlasRuntimeService =
                Objects.requireNonNull(atlasRuntimeService, "atlasRuntimeService");
        this.assetMetaLookup = assetMetaLookup != null ? assetMetaLookup : id -> null;
        this.standaloneAccess =
                Objects.requireNonNull(standaloneAccess, "standaloneAccess");
    }

    public static StandaloneAssetAccess projectStandaloneAccess() {
        return new StandaloneAssetAccess() {
            @Override
            public Texture resolveTexture(String projectRelativePath) {
                return StandaloneTextureCache.getOrLoadProjectRelative(
                        projectRelativePath
                );
            }

            @Override
            public String[] listPngFramePaths(String projectRelativeDirectory) {
                ProjectConfig cfg = ProjectConfig.getInstance();
                if (cfg == null
                        || cfg.projectFileName == null
                        || cfg.projectFileName.isBlank()
                        || projectRelativeDirectory == null
                        || projectRelativeDirectory.isBlank()) {
                    return NO_FRAME_PATHS;
                }

                FileHandle directory = StudioFs.requireStudioProjectDir(cfg)
                        .child(projectRelativeDirectory);
                return listProjectPngFramePaths(
                        directory,
                        projectRelativeDirectory
                );
            }
        };
    }

    static String[] listProjectPngFramePaths(FileHandle directory,
                                             String projectRelativeDirectory) {
        if (directory == null
                || !directory.exists()
                || !directory.isDirectory()
                || isBlank(projectRelativeDirectory)) {
            return NO_FRAME_PATHS;
        }

        Array<FileHandle> pngFrames = new Array<>();
        for (FileHandle child : directory.list()) {
            if (child == null || child.isDirectory()) continue;
            if ("png".equalsIgnoreCase(child.extension())) {
                pngFrames.add(child);
            }
        }
        if (pngFrames.size == 0) return NO_FRAME_PATHS;

        pngFrames.sort((left, right) ->
                left.name().compareToIgnoreCase(right.name()));
        String[] paths = new String[pngFrames.size];
        for (int i = 0; i < pngFrames.size; i++) {
            paths[i] = projectRelativeDirectory + "/" + pngFrames.get(i).name();
        }
        return paths;
    }

    public StudioAssetVisual resolveFirst(int assetId, String atlasTag) {
        return resolveFrame(assetId, atlasTag, 0);
    }

    public StudioAssetVisual resolveFrame(int assetId,
                                          String atlasTag,
                                          int frameIndex) {
        if (assetId <= 0) return null;

        StudioAssetVisual atlasVisual =
                resolveAtlasFrame(assetId, atlasTag, frameIndex);
        if (atlasVisual != null) return atlasVisual;

        return resolveStandaloneFrame(assetId, frameIndex);
    }

    public void setAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        this.assetMetaLookup =
                Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");
        invalidateMetadata();
    }

    public void invalidateAtlasTag(String atlasTag) {
        if (!isBlank(atlasTag)) {
            atlasBuckets.remove(atlasTag);
        }
    }

    public void invalidateMetadata() {
        standaloneByAssetId.clear();
    }

    public void invalidateStandalone() {
        standaloneByAssetId.clear();
    }

    public void invalidateAll() {
        atlasBuckets.clear();
        standaloneByAssetId.clear();
    }

    private StudioAssetVisual resolveAtlasFrame(int assetId,
                                                String atlasTag,
                                                int frameIndex) {
        if (isBlank(atlasTag)) return null;

        TextureAtlas atlas = atlasRuntimeService.getAtlas(atlasTag);
        if (atlas == null) return null;

        AtlasVisualBucket bucket = atlasBuckets.get(atlasTag);
        if (bucket == null || bucket.atlas != atlas) {
            bucket = new AtlasVisualBucket(atlas);
            atlasBuckets.put(atlasTag, bucket);
        }

        AtlasVisualEntry entry = bucket.byAssetId.get(assetId);
        if (entry == null) {
            atlasColdResolutions++;
            AtlasAssetBinding binding =
                    atlasRuntimeService.resolveBinding(assetId, atlasTag);
            entry = new AtlasVisualEntry(binding);
            bucket.byAssetId.put(assetId, entry);
        }
        if (entry.binding == null) return null;

        int clamped = clampFrame(frameIndex, entry.binding.regionCount());
        StudioAssetVisual visual = entry.frames[clamped];
        if (visual != null) return visual;

        TextureAtlas.AtlasRegion region = entry.binding.regionAt(clamped);
        if (clamped == 0) {
            AtlasRegionMetadata metadata = entry.binding.metadata();
            visual = new StudioAssetVisual(
                    StudioAssetVisual.Source.ATLAS,
                    region.getTexture(),
                    metadata.textureHandle(),
                    metadata.u1(),
                    metadata.v1(),
                    metadata.u2(),
                    metadata.v2(),
                    metadata.pixelWidth(),
                    metadata.pixelHeight(),
                    clamped
            );
        } else {
            Texture texture = region.getTexture();
            visual = new StudioAssetVisual(
                    StudioAssetVisual.Source.ATLAS,
                    texture,
                    TextureRegistry.handleOf(texture),
                    region.getU(),
                    region.getV(),
                    region.getU2(),
                    region.getV2(),
                    region.getRegionWidth(),
                    region.getRegionHeight(),
                    clamped
            );
        }
        entry.frames[clamped] = visual;
        return visual;
    }

    private StudioAssetVisual resolveStandaloneFrame(int assetId, int frameIndex) {
        AssetMeta meta = assetMetaLookup.apply(assetId);
        StandaloneVisualEntry entry = standaloneByAssetId.get(assetId);
        if (entry == null || !entry.matches(meta)) {
            standaloneColdResolutions++;
            entry = buildStandaloneEntry(meta);
            standaloneByAssetId.put(assetId, entry);
        }

        if (entry.type == AssetType.ANIMATION) {
            if (entry.framePaths.length == 0) return null;
            int clamped = clampFrame(frameIndex, entry.framePaths.length);
            if (entry.frameLoadAttempted[clamped]) {
                return entry.frameVisuals[clamped];
            }
            entry.frameLoadAttempted[clamped] = true;
            entry.frameVisuals[clamped] =
                    loadStandaloneTexture(entry.framePaths[clamped], clamped);
            return entry.frameVisuals[clamped];
        }
        return entry.staticVisual;
    }

    private StandaloneVisualEntry buildStandaloneEntry(AssetMeta meta) {
        if (meta == null) {
            return new StandaloneVisualEntry(
                    null,
                    null,
                    null,
                    (StudioAssetVisual) null
            );
        }

        AssetType type = meta.type();
        String sourcePath = meta.sourceRelPath();
        if (type == AssetType.ANIMATION) {
            String[] paths = !isBlank(sourcePath)
                    ? standaloneAccess.listPngFramePaths(sourcePath)
                    : NO_FRAME_PATHS;
            if (paths == null) paths = NO_FRAME_PATHS;
            return new StandaloneVisualEntry(meta, type, sourcePath, paths);
        }

        StudioAssetVisual visual = null;
        if ((type == AssetType.IMAGE || type == AssetType.TILE)
                && isSupportedImagePath(sourcePath)) {
            visual = loadStandaloneTexture(sourcePath, 0);
        }
        return new StandaloneVisualEntry(meta, type, sourcePath, visual);
    }

    private StudioAssetVisual loadStandaloneTexture(String sourcePath,
                                                    int frameIndex) {
        standaloneLoadAttempts++;
        Texture texture;
        try {
            texture = standaloneAccess.resolveTexture(sourcePath);
        } catch (RuntimeException ignored) {
            return null;
        }
        if (texture == null) return null;

        return new StudioAssetVisual(
                StudioAssetVisual.Source.STANDALONE,
                texture,
                TextureRegistry.handleOf(texture),
                0f,
                0f,
                1f,
                1f,
                texture.getWidth(),
                texture.getHeight(),
                frameIndex
        );
    }

    private static int clampFrame(int frameIndex, int frameCount) {
        if (frameIndex < 0) return 0;
        if (frameIndex >= frameCount) return frameCount - 1;
        return frameIndex;
    }

    private static boolean isSupportedImagePath(String sourcePath) {
        if (isBlank(sourcePath)) return false;
        String lower = sourcePath.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp");
    }

    private static boolean isBlank(String value) {
        if (value == null || value.length() == 0) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return false;
        }
        return true;
    }

    int atlasColdResolutions() {
        return atlasColdResolutions;
    }

    int standaloneColdResolutions() {
        return standaloneColdResolutions;
    }

    int standaloneLoadAttempts() {
        return standaloneLoadAttempts;
    }

    private static final class AtlasVisualBucket {
        final TextureAtlas atlas;
        final IntMap<AtlasVisualEntry> byAssetId = new IntMap<>();

        AtlasVisualBucket(TextureAtlas atlas) {
            this.atlas = atlas;
        }
    }

    private static final class AtlasVisualEntry {
        final AtlasAssetBinding binding;
        final StudioAssetVisual[] frames;

        AtlasVisualEntry(AtlasAssetBinding binding) {
            this.binding = binding;
            this.frames = binding != null
                    ? new StudioAssetVisual[binding.regionCount()]
                    : null;
        }
    }

    private static final class StandaloneVisualEntry {
        final AssetMeta observedMeta;
        final AssetType type;
        final String sourcePath;
        final StudioAssetVisual staticVisual;
        final String[] framePaths;
        final StudioAssetVisual[] frameVisuals;
        final boolean[] frameLoadAttempted;

        StandaloneVisualEntry(AssetMeta observedMeta,
                              AssetType type,
                              String sourcePath,
                              StudioAssetVisual staticVisual) {
            this.observedMeta = observedMeta;
            this.type = type;
            this.sourcePath = sourcePath;
            this.staticVisual = staticVisual;
            this.framePaths = NO_FRAME_PATHS;
            this.frameVisuals = null;
            this.frameLoadAttempted = null;
        }

        StandaloneVisualEntry(AssetMeta observedMeta,
                              AssetType type,
                              String sourcePath,
                              String[] framePaths) {
            this.observedMeta = observedMeta;
            this.type = type;
            this.sourcePath = sourcePath;
            this.staticVisual = null;
            this.framePaths = framePaths;
            this.frameVisuals = new StudioAssetVisual[framePaths.length];
            this.frameLoadAttempted = new boolean[framePaths.length];
        }

        boolean matches(AssetMeta meta) {
            return observedMeta == meta
                    && (meta == null
                    || (type == meta.type()
                    && Objects.equals(sourcePath, meta.sourceRelPath())));
        }
    }
}
