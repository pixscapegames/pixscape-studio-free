package games.pixscape.studio.asset;

import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.AnimationComponent;

public final class AnimationAssetMeta extends AssetMeta {

    public int frameCount;
    public float fps;
    public String currentClip;
    public ObjectMap<String, AnimationComponent.Clip> clips = new ObjectMap<>();

    public AnimationAssetMeta() {
        // required for Json
    }

    public AnimationAssetMeta(int id,
                              String logicalPath,
                              String sourceRelPath,
                              AssetScope scope) {
        super(id, AssetType.ANIMATION, logicalPath, sourceRelPath, scope);
    }
}
