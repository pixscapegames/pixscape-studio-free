package games.pixscape.studio.asset;

/** Studio-authored clip definition owned by an {@link AnimationAssetMeta}. */
public final class AnimationClipMeta {
    public int start;
    public int end;
    public boolean flipX;

    public AnimationClipMeta() {
    }

    public AnimationClipMeta(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public AnimationClipMeta copy() {
        AnimationClipMeta copy = new AnimationClipMeta(start, end);
        copy.flipX = flipX;
        return copy;
    }
}
