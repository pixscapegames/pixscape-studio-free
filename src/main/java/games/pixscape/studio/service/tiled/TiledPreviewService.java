package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class TiledPreviewService {

    private boolean coverageVisible;
    private boolean ghostVisible;
    private boolean tintVisible;
    private TiledMapLayerData map;
    private String atlasTag;
    private int gx;
    private int gy;
    private int assetId;
    private byte flags;
    private float tintR;
    private float tintG;
    private float tintB;
    private float tintA;
    private int visualPixW;
    private int visualPixH;

    public void clear() {
        coverageVisible = false;
        ghostVisible = false;
        tintVisible = false;
        map = null;
        atlasTag = null;
        gx = gy = 0;
        assetId = 0;
        flags = 0;
        tintR = tintG = tintB = tintA = 0f;
        visualPixW = visualPixH = 0;
    }

    public void show(TiledMapLayerData map,
                     String atlasTag,
                     int gx,
                     int gy,
                     int assetId,
                     byte flags) {
        showInternal(map, atlasTag, gx, gy, assetId, flags, true, false, 0f, 0f, 0f, 0f);
    }

    public void showCoverageOnly(TiledMapLayerData map,
                                 String atlasTag,
                                 int gx,
                                 int gy,
                                 int assetId,
                                 byte flags) {
        showInternal(map, atlasTag, gx, gy, assetId, flags, false, false, 0f, 0f, 0f, 0f);
    }

    public void showTintedCoverage(TiledMapLayerData map,
                                   String atlasTag,
                                   int gx,
                                   int gy,
                                   int assetId,
                                   byte flags,
                                   float r,
                                   float g,
                                   float b,
                                   float a) {
        showInternal(map, atlasTag, gx, gy, assetId, flags, false, true, r, g, b, a);
    }

    public boolean isVisible() {
        return isGhostVisible() || isTintVisible();
    }

    public boolean isCoverageVisible() {
        return coverageVisible && map != null && assetId > 0;
    }

    public boolean isGhostVisible() {
        return ghostVisible && isCoverageVisible();
    }

    public boolean isTintVisible() {
        return tintVisible && isCoverageVisible();
    }

    public TiledMapLayerData map() {
        return map;
    }

    public String atlasTag() {
        return atlasTag;
    }

    public int gx() {
        return gx;
    }

    public int gy() {
        return gy;
    }

    public int assetId() {
        return assetId;
    }

    public byte flags() {
        return flags;
    }

    public float tintR() {
        return tintR;
    }

    public float tintG() {
        return tintG;
    }

    public float tintB() {
        return tintB;
    }

    public float tintA() {
        return tintA;
    }

    public void setVisualSize(int pixW, int pixH) {
        this.visualPixW = Math.max(0, pixW);
        this.visualPixH = Math.max(0, pixH);
    }

    public boolean hasVisualSize() {
        return visualPixW > 0 && visualPixH > 0;
    }

    public int visualPixW() {
        return visualPixW;
    }

    public int visualPixH() {
        return visualPixH;
    }

    private void showInternal(TiledMapLayerData map,
                              String atlasTag,
                              int gx,
                              int gy,
                              int assetId,
                              byte flags,
                              boolean ghostVisible,
                              boolean tintVisible,
                              float tintR,
                              float tintG,
                              float tintB,
                              float tintA) {
        this.coverageVisible = true;
        this.ghostVisible = ghostVisible;
        this.tintVisible = tintVisible;
        this.map = map;
        this.atlasTag = atlasTag;
        this.gx = gx;
        this.gy = gy;
        this.assetId = assetId;
        this.flags = flags;
        this.tintR = tintR;
        this.tintG = tintG;
        this.tintB = tintB;
        this.tintA = tintA;
        this.visualPixW = 0;
        this.visualPixH = 0;
    }
}
