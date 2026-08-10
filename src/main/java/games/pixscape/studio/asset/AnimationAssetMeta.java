package games.pixscape.studio.asset;

import com.badlogic.gdx.utils.ObjectMap;

public final class AnimationAssetMeta extends AssetMeta {

    public int frameCount;
    public float fps;
    public String currentClip;
    public ObjectMap<String, AnimationClipMeta> clips = new ObjectMap<>();

    public AnimationAssetMeta() {
        super(AssetType.ANIMATION);
    }

    public AnimationAssetMeta(int id,
                              String logicalPath,
                              String sourceRelPath,
                              AssetScope scope) {
        super(id, AssetType.ANIMATION, logicalPath, sourceRelPath, scope);
    }
}
