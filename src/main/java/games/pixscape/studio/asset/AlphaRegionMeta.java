package games.pixscape.studio.asset;

import games.pixscape.runtime.render.lighting.AlphaBounds;

/** Replaceable derived source metadata; independent of every atlas generation. */
public final class AlphaRegionMeta {
    public int frame;
    public String sourceKey, sourceHash;
    public AlphaBounds bounds;
    public AlphaRegionMeta copy(){AlphaRegionMeta r=new AlphaRegionMeta();r.frame=frame;r.sourceKey=sourceKey;r.sourceHash=sourceHash;r.bounds=bounds==null?null:bounds.copy();return r;}
}
