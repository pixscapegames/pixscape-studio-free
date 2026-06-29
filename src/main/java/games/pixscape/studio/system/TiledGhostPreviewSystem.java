package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.service.StandaloneTextureCache;
import games.pixscape.studio.service.tiled.StudioTilesetProfileResolver;
import games.pixscape.studio.service.tiled.TiledPreviewService;

import java.util.Objects;
import java.util.function.IntFunction;

public final class TiledGhostPreviewSystem extends BaseSystem implements ProfiledSystem {

    private final StudioDrawContext ctx;
    private final AtlasRuntimeService atlasRuntimeService;
    private final TiledPreviewService previewService;

    private IntFunction<AssetMeta> assetMetaLookup;
    private StudioTilesetProfileResolver tilesetProfileResolver;
    private IntFunction<Texture> textureHandleLookup;
    private TileAnimationLookup tileAnimationLookup;
    private final IntSet reportedMissingProfileTileAssetIds = new IntSet();

    private final float[] tmpQuad = new float[8];
    private final float[] tmpVerts = new float[20];

    private float alpha = 0.55f;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public TiledGhostPreviewSystem(StudioDrawContext ctx,
                                   AtlasRuntimeService atlasRuntimeService,
                                   TiledPreviewService previewService,
                                   IntFunction<AssetMeta> assetMetaLookup,
                                   IntFunction<Texture> textureHandleLookup,
                                   TileAnimationLookup tileAnimationLookup) {

        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.atlasRuntimeService = Objects.requireNonNull(atlasRuntimeService, "atlasRuntimeService");
        this.previewService = Objects.requireNonNull(previewService, "previewService");
        this.assetMetaLookup = assetMetaLookup != null ? assetMetaLookup : id -> null;
        this.tilesetProfileResolver = new StudioTilesetProfileResolver(this.assetMetaLookup);
        this.textureHandleLookup = textureHandleLookup != null ? handle -> textureHandleLookup.apply(handle) : handle -> null;
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : id -> null;
    }

    public void setAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        this.assetMetaLookup = Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");
        this.tilesetProfileResolver.setAssetMetaLookup(this.assetMetaLookup);
    }

    public void setTextureHandleLookup(IntFunction<Texture> textureHandleLookup) {
        this.textureHandleLookup = Objects.requireNonNull(textureHandleLookup, "textureHandleLookup");
    }

    public void setTileAnimationLookup(TileAnimationLookup tileAnimationLookup) {
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : id -> null;
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0f, Math.min(1f, alpha));
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.TILED_GHOST_PREVIEW);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.TILED_GHOST_PREVIEW, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        if (!previewService.isCoverageVisible()) return;
        if (ctx.cam.zoom <= 0.000001f) return;

        TiledMapLayerData map = previewService.map();
        if (map == null) return;

        int gx = previewService.gx();
        int gy = previewService.gy();
        int logicalAssetId = previewService.assetId();
        byte flags = previewService.flags();

        if (logicalAssetId <= 0) return;
        if (!map.isInside(gx, gy)) return;

        int previewAssetId = TileAnimationResolver.resolveVisualAssetId(
                logicalAssetId,
                0, // always first frame for placement preview
                tileAnimationLookup
        );

        if (previewAssetId <= 0) {
            return;
        }

        DrawData drawData = resolveDrawData(
                previewAssetId,
                previewService.atlasTag()
        );

        if (drawData == null || drawData.texture == null) {
            return;
        }

        previewService.setVisualSize(drawData.spriteW, drawData.spriteH);

        boolean ghostVisible = previewService.isGhostVisible();
        boolean tintVisible = previewService.isTintVisible();
        if (!ghostVisible && !tintVisible) {
            return;
        }

        RuntimeTilesetProfile profile = resolveTilesetProfile(previewAssetId);
        if (profile == null) {
            reportMissingProfileOnce(previewAssetId, logicalAssetId, previewService.atlasTag());
            return;
        }

        TileQuadTransforms.buildSpriteQuad(
                map,
                gx,
                gy,
                drawData.spriteW,
                drawData.spriteH,
                profile,
                flags,
                tmpQuad
        );

        buildVertices(
                tmpVerts,
                tmpQuad,
                drawData.uBL, drawData.vBL,
                drawData.uTL, drawData.vTL,
                drawData.uTR, drawData.vTR,
                drawData.uBR, drawData.vBR,
                ghostVisible
                        ? Color.toFloatBits(1f, 1f, 1f, alpha)
                        : Color.toFloatBits(
                                previewService.tintR(),
                                previewService.tintG(),
                                previewService.tintB(),
                                previewService.tintA()
                        )
        );

        SpriteBatch batch = ctx.batch;
        batch.setProjectionMatrix(ctx.cam.combined);
        batch.begin();
        try {
            batch.draw(drawData.texture, tmpVerts, 0, 20);
        } finally {
            batch.end();
        }
    }

    private DrawData resolveDrawData(int assetId, String atlasTag) {
        AtlasRuntimeService.CachedRegion cr =
                atlasRuntimeService.resolveCached(assetId, atlasTag);

        if (cr != null) {
            Texture texture = textureHandleLookup.apply(cr.textureHandle);
            if (texture == null) {
                return null;
            }

            DrawData dd = new DrawData();
            dd.texture = texture;
            dd.spriteW = cr.pixW;
            dd.spriteH = cr.pixH;

            // Mapping SpriteBatch vertices:
            // BL, TL, TR, BR
            dd.uBL = cr.u1;
            dd.vBL = cr.v2;

            dd.uTL = cr.u1;
            dd.vTL = cr.v1;

            dd.uTR = cr.u2;
            dd.vTR = cr.v1;

            dd.uBR = cr.u2;
            dd.vBR = cr.v2;

            return dd;
        }

        AssetMeta meta = assetMetaLookup.apply(assetId);
        if (meta == null || meta.sourceRelPath == null || meta.sourceRelPath.isBlank()) {
            return null;
        }
        if (meta.type != AssetType.TILE) {
            return null;
        }

        Texture tex = StandaloneTextureCache.getOrLoadProjectRelative(meta.sourceRelPath);
        if (tex == null) {
            return null;
        }

        DrawData dd = new DrawData();
        dd.texture = tex;
        dd.spriteW = tex.getWidth();
        dd.spriteH = tex.getHeight();

        dd.uBL = 0f;
        dd.vBL = 1f;

        dd.uTL = 0f;
        dd.vTL = 0f;

        dd.uTR = 1f;
        dd.vTR = 0f;

        dd.uBR = 1f;
        dd.vBR = 1f;

        return dd;
    }

    RuntimeTilesetProfile resolveTilesetProfile(int tileAssetId) {
        return tilesetProfileResolver.resolve(tileAssetId);
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
            Gdx.app.error("TiledGhostPreviewSystem", message);
        } else {
            System.err.println("[TiledGhostPreviewSystem] " + message);
        }
    }

    private static void buildVertices(float[] out,
                                      float[] quad,
                                      float uBL, float vBL,
                                      float uTL, float vTL,
                                      float uTR, float vTR,
                                      float uBR, float vBR,
                                      float colorPacked) {

        // BL
        out[0] = quad[0];
        out[1] = quad[1];
        out[2] = colorPacked;
        out[3] = uBL;
        out[4] = vBL;

        // TL
        out[5] = quad[2];
        out[6] = quad[3];
        out[7] = colorPacked;
        out[8] = uTL;
        out[9] = vTL;

        // TR
        out[10] = quad[4];
        out[11] = quad[5];
        out[12] = colorPacked;
        out[13] = uTR;
        out[14] = vTR;

        // BR
        out[15] = quad[6];
        out[16] = quad[7];
        out[17] = colorPacked;
        out[18] = uBR;
        out[19] = vBR;
    }

    private static final class DrawData {
        Texture texture;
        int spriteW;
        int spriteH;

        float uBL, vBL;
        float uTL, vTL;
        float uTR, vTR;
        float uBR, vBR;
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
