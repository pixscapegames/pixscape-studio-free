package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTree;

/**
 * Nœud du tree items.
 */
public class EntityNode extends VisTree.Node {

    public enum NodeKind {
        LAYER,
        ENTITY,
        BODY,
        TILED_MAP,
        SPATIAL_BLOCKS,
        INFO
    }

    /**
     * Payload stored in the node actor through setUserObject.
     */
    public record NodeRef(int entityId, NodeKind kind) {
    }

    private final int entityId;
    private final NodeKind kind;
    private final VisLabel label;

    public EntityNode(String name, Drawable icon, int entityId, boolean selectable) {
        this(name, icon, entityId, selectable, NodeKind.ENTITY);
    }

    public EntityNode(String name, Drawable icon, int entityId, boolean selectable, NodeKind kind) {
        super();

        this.entityId = entityId;
        this.kind = kind != null ? kind : NodeKind.ENTITY;
        this.label = new VisLabel(name);

        VisTable row = new VisTable();
        row.add(label).padLeft(6).left();
        row.setUserObject(new NodeRef(entityId, this.kind));
        setActor(row);

        if (icon != null) {
            setIcon(icon);
        }
        setSelectable(selectable);
    }

    public int getEntityId() {
        return entityId;
    }

    public NodeKind getKind() {
        return kind;
    }

    public boolean isLayerNode() {
        return kind == NodeKind.LAYER;
    }

    public boolean isEntityNode() {
        return kind == NodeKind.ENTITY;
    }

    public boolean isTiledMapNode() {
        return kind == NodeKind.TILED_MAP;
    }

    public boolean isBodyNode() {
        return kind == NodeKind.BODY;
    }

    public boolean isSpatialBlocksNode() {
        return kind == NodeKind.SPATIAL_BLOCKS;
    }

    public boolean isInfoNode() {
        return kind == NodeKind.INFO;
    }

    public VisLabel getLabel() {
        return label;
    }

    public void setLabelName(String name) {
        label.setText(name);
    }
}
