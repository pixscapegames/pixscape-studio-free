package games.pixscape.studio.service.asset;

import com.badlogic.gdx.graphics.Texture;

/**
 * Immutable Studio view of one drawable asset representation.
 *
 * <p>The texture remains owned by the atlas service or the standalone texture
 * cache. This view never disposes or mutates it.</p>
 */
public final class StudioAssetVisual {

    public enum Source {
        ATLAS,
        STANDALONE
    }

    private final Source source;
    private final Texture texture;
    private final int textureHandle;
    private final float u1;
    private final float v1;
    private final float u2;
    private final float v2;
    private final int pixelWidth;
    private final int pixelHeight;
    private final int frameIndex;

    StudioAssetVisual(Source source,
                      Texture texture,
                      int textureHandle,
                      float u1,
                      float v1,
                      float u2,
                      float v2,
                      int pixelWidth,
                      int pixelHeight,
                      int frameIndex) {
        this.source = source;
        this.texture = texture;
        this.textureHandle = textureHandle;
        this.u1 = u1;
        this.v1 = v1;
        this.u2 = u2;
        this.v2 = v2;
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.frameIndex = frameIndex;
    }

    public Source source() {
        return source;
    }

    public Texture texture() {
        return texture;
    }

    public int textureHandle() {
        return textureHandle;
    }

    public float u1() {
        return u1;
    }

    public float v1() {
        return v1;
    }

    public float u2() {
        return u2;
    }

    public float v2() {
        return v2;
    }

    public int pixelWidth() {
        return pixelWidth;
    }

    public int pixelHeight() {
        return pixelHeight;
    }

    /**
     * Returns the clamped frame index represented by this view.
     */
    public int frameIndex() {
        return frameIndex;
    }
}
