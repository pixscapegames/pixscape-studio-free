package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.helper.StudioDrawContext;
import games.pixscape.studio.service.asset.StudioAssetVisual;
import games.pixscape.studio.service.asset.StudioAssetVisualResolver;
import games.pixscape.studio.service.tiled.StudioTilesetProfileResolver;
import games.pixscape.studio.service.tiled.TiledBrushSession;
import games.pixscape.studio.service.tiled.TiledPreviewService;

import java.util.Objects;
import java.util.function.IntFunction;

public final class TiledGhostPreviewSystem extends BaseSystem implements ProfiledSystem {

    private final StudioDrawContext ctx;
    private final StudioAssetVisualResolver visualResolver;
    private final TiledPreviewService previewService;

    private IntFunction<AssetMeta> assetMetaLookup;
    private StudioTilesetProfileResolver tilesetProfileResolver;
    private TileAnimationLookup tileAnimationLookup;
    private final IntSet reportedMissingProfileTileAssetIds = new IntSet();

    private final float[] tmpQuad = new float[8];
    private final float[] tmpVerts = new float[20];

    private float alpha = 0.55f;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public TiledGhostPreviewSystem(StudioDrawContext ctx,
                                   StudioAssetVisualResolver visualResolver,
                                   TiledPreviewService previewService,
                                   IntFunction<AssetMeta> assetMetaLookup,
                                   TileAnimationLookup tileAnimationLookup) {

        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.visualResolver =
                Objects.requireNonNull(visualResolver, "visualResolver");
        this.previewService = Objects.requireNonNull(previewService, "previewService");
        this.assetMetaLookup = assetMetaLookup != null ? assetMetaLookup : id -> null;
        this.tilesetProfileResolver = new StudioTilesetProfileResolver(this.assetMetaLookup);
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : id -> null;
    }

    public void setAssetMetaLookup(IntFunction<AssetMeta> assetMetaLookup) {
        this.assetMetaLookup = Objects.requireNonNull(assetMetaLookup, "assetMetaLookup");
        this.tilesetProfileResolver.setAssetMetaLookup(this.assetMetaLookup);
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
        TiledBrushSession session = previewService.brushSession();
        if (session == null && !previewService.isCoverageVisible()) return;
        if (ctx.cam.zoom <= 0.000001f) return;

        TiledMapLayerData map = previewService.map();
        if (map == null) return;

        if (session != null) {
            drawBrushSession(map, session, previewService.atlasTag());
            return;
        }

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

        StudioAssetVisual visual = resolveVisual(
                previewAssetId,
                previewService.atlasTag()
        );

        if (visual == null || visual.texture() == null) {
            return;
        }

        previewService.setVisualSize(visual.pixelWidth(), visual.pixelHeight());

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
                visual.pixelWidth(),
                visual.pixelHeight(),
                profile,
                flags,
                tmpQuad
        );

        buildVertices(
                tmpVerts,
                tmpQuad,
                visual.u1(), visual.v2(),
                visual.u1(), visual.v1(),
                visual.u2(), visual.v1(),
                visual.u2(), visual.v2(),
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
            batch.draw(visual.texture(), tmpVerts, 0, 20);
        } finally {
            batch.end();
        }
    }

    private void drawBrushSession(TiledMapLayerData map, TiledBrushSession session, String atlasTag) {
        SpriteBatch batch = ctx.batch;
        batch.setProjectionMatrix(ctx.cam.combined);
        batch.begin();
        try {
            for (int i = 0; i < session.mutationCount(); i++) {
                int gx = session.gx(i);
                int gy = session.gy(i);
                int logicalAssetId = session.afterAssetId(i);
                boolean erase = logicalAssetId <= 0;
                byte flags = session.afterTransformFlags(i);
                if (erase) {
                    logicalAssetId = map.getTile(gx, gy);
                    flags = map.getTileTransformFlags(gx, gy);
                }
                if (logicalAssetId <= 0) continue;
                int visualAssetId = TileAnimationResolver.resolveVisualAssetId(logicalAssetId, 0, tileAnimationLookup);
                StudioAssetVisual visual =
                        resolveVisual(visualAssetId, atlasTag);
                RuntimeTilesetProfile profile = resolveTilesetProfile(visualAssetId);
                if (visual == null || visual.texture() == null || profile == null) continue;
                TileQuadTransforms.buildSpriteQuad(
                        map,
                        gx,
                        gy,
                        visual.pixelWidth(),
                        visual.pixelHeight(),
                        profile, flags, tmpQuad);
                buildVertices(tmpVerts, tmpQuad,
                        visual.u1(), visual.v2(),
                        visual.u1(), visual.v1(),
                        visual.u2(), visual.v1(),
                        visual.u2(), visual.v2(),
                        erase ? Color.toFloatBits(0.05f, 0.92f, 1f, 0.5f)
                                : Color.toFloatBits(1f, 1f, 1f, alpha));
                batch.draw(visual.texture(), tmpVerts, 0, 20);
            }
        } finally {
            batch.end();
        }
    }

    RuntimeTilesetProfile resolveTilesetProfile(int tileAssetId) {
        return tilesetProfileResolver.resolve(tileAssetId);
    }

    StudioAssetVisual resolveVisual(int visualAssetId, String atlasTag) {
        return visualResolver.resolveFirst(visualAssetId, atlasTag);
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

    static void buildVertices(float[] out,
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

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
