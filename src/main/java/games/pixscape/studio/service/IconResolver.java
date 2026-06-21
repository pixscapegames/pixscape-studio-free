package games.pixscape.studio.service;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.model.EntityKind;

public class IconResolver {
    private final ComponentMapper<EntityMetaComponent> mMeta;

    public IconResolver(World world) {
        this.mMeta = world.getMapper(EntityMetaComponent.class);
    }

    public Drawable iconForEntity(int e) {
        EntityMetaComponent meta = mMeta.getSafe(e, null);
        EntityKind kind = (meta != null && meta.kind != null) ? meta.kind : EntityKind.UNKNOWN;
        return getDrawable(kind);
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
                return VisUI.getSkin().getDrawable("particle_icon16");
            }
            case POINT_LIGHT, AMBIENT_LIGHT, CONE_LIGHT -> {
                return VisUI.getSkin().getDrawable("light_point");
            }
            case TILED_MAP -> {
                return VisUI.getSkin().getDrawable("icon-tiled");
            }
            default -> {
                return VisUI.getSkin().getDrawable("image_icon16");
            }
        }
    }

}
