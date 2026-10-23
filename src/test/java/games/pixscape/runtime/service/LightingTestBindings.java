package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.Array;

/** Native metadata fixture; no GL texture is needed by candidate admission. */
public final class LightingTestBindings {
    public static AtlasAssetBinding tile(int asset,int width,int height){
        return new AtlasAssetBinding(asset,"tile",null,new Array<>(),
                new AtlasRegionMetadata("tile",0,0,1,1,17,width,height));
    }
}
