package games.pixscape.studio.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.service.tiled.StudioTilesetProfileResolver;

import java.util.Objects;
import java.util.function.IntFunction;

@All({LayerComponent.class, TiledLayerComponent.class})
public final class TiledFallbackSystem extends IteratingSystem implements ProfiledSystem {

    private ComponentMapper<LayerComponent> mLayer;
    private ComponentMapper<TiledLayerComponent> mTiled;

    private final TiledMapRenderState tiledState;
    private final AtlasRuntimeService atlasRuntimeService;
    private final float[] tmpQuad = new float[8];
    private final IntSet reportedMissingProfileTileAssetIds = new IntSet();

    private IntFunction<AssetMeta> assetMetaLookup;
    private StudioTilesetProfileResolver tilesetProfileResolver;
    private TileAnimationLookup tileAnimationLookup;
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public TiledFallbackSystem(AtlasRuntimeService atlasRuntimeService,
                               IntFunction<AssetMeta> assetMetaLookup,
                               TileAnimationLookup tileAnimationLookup) {
        this(null, atlasRuntimeService, assetMetaLookup, tileAnimationLookup);
    }

    public TiledFallbackSystem(TiledMapRenderState tiledState,
                               AtlasRuntimeService atlasRuntimeService,
                               IntFunction<AssetMeta> assetMetaLookup,
                               TileAnimationLookup tileAnimationLookup) {

        this.tiledState = tiledState;
        this.atlasRuntimeService = atlasRuntimeService;
        this.assetMetaLookup = (assetMetaLookup != null) ? assetMetaLookup : id -> null;
        this.tilesetProfileResolver = new StudioTilesetProfileResolver(this.assetMetaLookup);
        this.tileAnimationLookup = (tileAnimationLookup != null) ? tileAnimationLookup : id -> null;
    }

    public void setAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        this.assetMetaLookup = Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");
        this.tilesetProfileResolver.setAssetMetaLookup(this.assetMetaLookup);
    }

    public void setTileAnimationLookup(TileAnimationLookup tileAnimationLookup) {
        this.tileAnimationLookup = (tileAnimationLookup != null) ? tileAnimationLookup : id -> null;
    }

    AssetMeta resolveAssetMeta(int assetId) {
        if (assetId <= 0) return null;
        return assetMetaLookup.apply(assetId);
    }

    RuntimeTilesetProfile resolveTilesetProfile(int tileAssetId) {
        return tilesetProfileResolver.resolve(tileAssetId);
    }

    @Override
    protected void begin() {
        profiling = profiler.enabled();
        if (profiling) {
            profileStartNs = profiler.begin(SystemProfilePhases.TILED_FALLBACK);
        }
    }

    @Override
    protected void process(int e) {

        LayerComponent layer = mLayer.get(e);
        if (layer.type != LayerComponent.TYPE_TILED) return;

        TiledLayerComponent tiled = mTiled.get(e);
        if (tiled == null || tiled.data == null) return;

        TiledMapLayerData map = tiled.data;
        if (tiledState == null) return;

        IntMap.Values<TileChunk> values = map.getChunks();
        while (values.hasNext()) {
            TileChunk chunk = values.next();
            ensureChunkRenderRefs(chunk);

            for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                for (int lx = 0; lx < chunk.chunkWidth; lx++) {

                    int localIndex = ly * chunk.chunkWidth + lx;
                    int tiledRenderRef = chunk.renderRefFor(lx, ly);

                    if (tiledRenderRef < 0) continue;

                    int logicalAssetId = chunk.assetIds[localIndex];
                    if (logicalAssetId <= 0) continue;

                    byte transformFlags = chunk.transformFlags[localIndex];

                    int frameIndex = chunk.getAnimFrameIndex(localIndex);
                    int visualAssetId = TileAnimationResolver.resolveVisualAssetId(
                            logicalAssetId,
                            frameIndex,
                            tileAnimationLookup
                    );

                    if (visualAssetId <= 0) continue;

                    int gx = chunk.chunkX * map.chunkSize + lx;
                    int gy = chunk.chunkY * map.chunkSize + ly;
                    AtlasRuntimeService.CachedRegion cachedRegion = atlasRuntimeService != null
                            ? atlasRuntimeService.resolveCached(visualAssetId, tiled.atlasTag)
                            : null;

                    if (cachedRegion != null) {
                        continue;
                    }

                    AssetMeta meta = resolveAssetMeta(visualAssetId);
                    if (meta == null || meta.sourceRelPath == null || meta.sourceRelPath.isBlank()) {
                        continue;
                    }
                    if (meta.type != AssetType.TILE) {
                        continue;
                    }

                    Texture tex = StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath);
                    if (tex == null) continue;

                    RuntimeTilesetProfile profile = resolveTilesetProfile(visualAssetId);
                    if (profile == null) {
                        reportMissingProfileOnce(visualAssetId, logicalAssetId, tiled.atlasTag);
                        continue;
                    }

                    writeTileSlot(
                            layer,
                            map,
                            tiledRenderRef,
                            gx,
                            gy,
                            tex.getWidth(),
                            tex.getHeight(),
                            profile,
                            transformFlags,
                            TextureRegistry.handleOf(tex),
                            0f,
                            0f,
                            1f,
                            1f
                    );
                }
            }
        }
    }

    void writeTileSlot(LayerComponent layer,
                       TiledMapLayerData map,
                       int tiledRenderRef,
                       int gx,
                       int gy,
                       int spriteW,
                       int spriteH,
                       RuntimeTilesetProfile profile,
                       byte transformFlags,
                       int textureHandle,
                       float u1,
                       float v1,
                       float u2,
                       float v2) {
        TileQuadTransforms.buildSpriteQuad(
                map,
                gx,
                gy,
                spriteW,
                spriteH,
                profile,
                transformFlags,
                tmpQuad
        );

        int z = 0;
        int tie = 0;

        if (map.projection == SceneMetaRuntime.TiledProjection.ISO) {
            z = clampSortZ(-(gx + gy));
            tie = clampSortTie(gx);
        }

        int shader = 0;
        int blend = BlendMode.ALPHA.id;
        long sortKey = SortKey64.packForBlend(
                shader,
                blend,
                textureHandle,
                layer.layerIndex,
                z,
                tie
        );

        if (tiledState == null || tiledRenderRef < 0) {
            return;
        }

        tiledState.setRenderDataForRef(
                tiledRenderRef,
                textureHandle,
                shader,
                blend,
                layer.layerIndex,
                0,
                0,
                sortKey,
                tmpQuad[0],
                tmpQuad[1],
                tmpQuad[2],
                tmpQuad[3],
                tmpQuad[4],
                tmpQuad[5],
                tmpQuad[6],
                tmpQuad[7],
                u1,
                v1,
                u2,
                v2,
                Color.WHITE.toFloatBits(),
                1f,
                RenderRepeatFlags.NONE
        );
        addVisibleRefIfAbsent(tiledRenderRef);
    }

    private void ensureChunkRenderRefs(TileChunk chunk) {
        if (chunk == null) return;
        int cellCount = chunk.cellCount();
        if (chunk.renderRefStartIndex < 0 || chunk.renderRefCount != cellCount) {
            chunk.renderRefStartIndex = tiledState.registerRefs(cellCount);
            chunk.renderRefCount = cellCount;
        }
    }

    private void addVisibleRefIfAbsent(int tiledRenderRef) {
        int[] visibleRefs = tiledState.getVisibleRefs();
        int count = tiledState.getVisibleRefCount();
        for (int i = 0; i < count; i++) {
            if (visibleRefs[i] == tiledRenderRef) return;
        }
        tiledState.addVisibleRef(tiledRenderRef);
    }

    private void reportMissingProfileOnce(int visualAssetId, int logicalAssetId, String atlasTag) {
        if (visualAssetId <= 0 || reportedMissingProfileTileAssetIds.contains(visualAssetId)) {
            return;
        }
        reportedMissingProfileTileAssetIds.add(visualAssetId);

        String message = "Missing tileset profile for tile asset " + visualAssetId
                + " (logical tile asset " + logicalAssetId
                + ", atlasTag " + (atlasTag != null ? atlasTag : "<none>") + ")";
        if (Gdx.app != null) {
            Gdx.app.error("TiledFallbackSystem", message);
        } else {
            System.err.println("[TiledFallbackSystem] " + message);
        }
    }

    private static int clampSortZ(int value) {
        if (value < -32768) return -32768;
        if (value > 32767) return 32767;
        return value;
    }

    private static int clampSortTie(int value) {
        if (value < 0) return 0;
        return Math.min(value, SortKey64.MAX_TIE);
    }

    @Override
    protected void end() {
        if (profiling) {
            profiler.end(SystemProfilePhases.TILED_FALLBACK, profileStartNs);
            profiling = false;
        }
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
