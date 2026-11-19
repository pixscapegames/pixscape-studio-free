package games.pixscape.studio.service;

import games.pixscape.runtime.tiled.TiledEntityRole;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TiledMapComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.model.EntityKind;

public class IconResolver {
    private final World world;
    private final ComponentMapper<EntityMetaComponent> mMeta;
    private final ComponentMapper<TiledMapComponent> mMaps;
    private final ComponentMapper<TiledLayerComponent> mTileLayers;

    public IconResolver(World world) {
        this.world = world;
        this.mMeta = world.getMapper(EntityMetaComponent.class);
        this.mMaps = world.getMapper(TiledMapComponent.class);
        this.mTileLayers = world.getMapper(TiledLayerComponent.class);
    }

    public Drawable iconForEntity(int e) {
        return getDrawable(kindForEntity(e));
    }

    public EntityKind kindForEntity(int e) {
        if (mMaps.has(e)) {
            return EntityKind.TILED_MAP;
        }
        if (mTileLayers.has(e) || TiledEntityRole.of(world, e) != TiledEntityRole.NONE) {
            return EntityKind.TILED_LAYER;
        }
        EntityMetaComponent meta = mMeta.getSafe(e, null);
        return (meta != null && meta.kind != null) ? meta.kind : EntityKind.UNKNOWN;
    }

    public String displayNameForEntity(int entity) {
        return switch (TiledEntityRole.of(world, entity)) {
            case MAP -> "Tiled Map";
            case TILE_LAYER -> "Tile Layer";
            case OBJECT_LAYER -> "Object Layer";
            case IMAGE_LAYER -> "Image Layer";
            case NONE -> kindForEntity(entity).name();
        };
    }

    public static Drawable iconForEntity(EntityKind kind) {
        return getDrawable(kind);
    }

    public static Drawable getDrawable(EntityKind kind) {
        switch (kind) {
            case ANIMATION -> {
                return VisUI.getSkin().getDrawable("animation_icon16");
            }
            case PARTICLE -> {
                return ParticleOverlayVisual.resolveDrawable();
            }
            case POINT_LIGHT, AMBIENT_LIGHT, CONE_LIGHT -> {
                return VisUI.getSkin().getDrawable("light_point");
            }
            case TILED_MAP -> {
                return VisUI.getSkin().getDrawable("icon-tiled");
            }
            case TILED_LAYER -> {
                return VisUI.getSkin().getDrawable("icon-tiled-layer");
            }
            case TILED_RECTANGLE -> {
                return VisUI.getSkin().getDrawable("tiled-rectangle");
            }
            case POLYGON -> {
                return VisUI.getSkin().getDrawable("tiled-polygon");
            }
            case TILED_POINT -> {
                return VisUI.getSkin().getDrawable("tiled-point");
            }
            case POLYLINE -> {
                return VisUI.getSkin().getDrawable("tiled-polyline");
            }
            case GAME_OBJECT -> {
                return VisUI.getSkin().getDrawable("cube");
            }
            default -> {
                return VisUI.getSkin().getDrawable("image_icon16");
            }
        }
    }

}
