package games.pixscape.studio.ui.property.entityproperties;

import com.kotcrab.vis.ui.widget.CollapsibleWidget;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.component.TiledObjectComponent;

/** Read-only imported Tiled Object metadata. */
public final class TiledObjectPanel extends CollapsibleWidget {
    static final String NO_CLASS = "—";

    private final EntityPropertiesContext ctx;
    private final VisLabel classValue = new VisLabel(NO_CLASS);
    private int entityId = -1;

    public TiledObjectPanel(EntityPropertiesContext ctx) {
        super();
        this.ctx = ctx;

        VisTable root = new VisTable(true);
        root.left().top().pad(5);
        root.defaults().left();
        root.add(new VisLabel("Class:")).padRight(8);
        root.add(classValue).growX();
        setTable(root);
    }

    public void setEntityId(int entityId) {
        this.entityId = entityId;
        TiledObjectComponent component = ctx.mTiledObject.getSafe(entityId, null);
        classValue.setText(displayClassName(component != null ? component.className : null));
    }

    public boolean isApplicable() {
        return entityId >= 0 && isApplicable(ctx.mTiledObject.getSafe(entityId, null));
    }

    static boolean isApplicable(TiledObjectComponent component) {
        return component != null;
    }

    static String displayClassName(String className) {
        return className == null || className.isBlank() ? NO_CLASS : className;
    }
}
