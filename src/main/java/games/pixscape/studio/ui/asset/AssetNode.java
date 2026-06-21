package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public final class AssetNode {

    public static final String TILED_ANIMATIONS_NODE_PATH = "__tile_animations__";

    public enum Kind {
        FOLDER,
        REGION,
        IMAGE,
        ANIMATION,              // classic animations
        PARTICLE,
        PREFAB,
        TILED_ANIMATIONS_FOLDER,
        TILED_ANIMATION,
        TILED_ANIMATION_FRAME
    }

    /**
     * Logical root in the project
     */
    public enum Root {
        IMAGES,
        ANIMATIONS,             // classic animations root
        PARTICLES,
        TILES,
        PREFABS
    }

    public Kind kind;
    public Root root;

    public String tag;     // for REGION (atlasTag), null otherwise
    public String path;    // path relative to root, or special logical path
    public String name;
    public int tileWidth = -1;
    public int tileHeight = -1;

    public int assetId = -1;
    public int tileAnimationId = -1;
    public int frameIndex = -1;
    public int durationMs = -1;

    public TextureAtlas.AtlasRegion region; // used only for REGION

    public AssetNode(Kind kind,
                     Root root,
                     String path,
                     String name,
                     TextureAtlas.AtlasRegion region) {
        this.kind = kind;
        this.root = root;
        this.path = path;
        this.name = name;
        this.region = region;
    }
}
