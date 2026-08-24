package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.IntArray;
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
        PREFAB_INSTANCE,
        INFO
    }

    /**
     * Payload stored in the node actor through setUserObject.
     */
    public record NodeRef(int entityId, NodeKind kind) {
    }

    public record PrefabNodeRef(int prefabInstanceId) {
    }

    private final int entityId;
    private final NodeKind kind;
    private final VisLabel label;
    private final int prefabInstanceId;
    private final String prefabId;
    private final IntArray prefabMemberIds;

    public EntityNode(String name, Drawable icon, int entityId, boolean selectable) {
        this(name, icon, entityId, selectable, NodeKind.ENTITY);
    }

    public EntityNode(String name, Drawable icon, int entityId, boolean selectable, NodeKind kind) {
        this(name, icon, entityId, selectable, kind, -1, "", null);
    }

    public static EntityNode prefabInstance(
            String prefabId,
            Drawable icon,
            int prefabInstanceId,
            IntArray memberEntityIds,
            boolean selectable) {
        if (prefabInstanceId <= 0) {
            throw new IllegalArgumentException("Prefab instance ID must be positive.");
        }
        return new EntityNode(
                prefabId,
                icon,
                -1,
                selectable,
                NodeKind.PREFAB_INSTANCE,
                prefabInstanceId,
                prefabId,
                memberEntityIds);
    }

    private EntityNode(
            String name,
            Drawable icon,
            int entityId,
            boolean selectable,
            NodeKind kind,
            int prefabInstanceId,
            String prefabId,
            IntArray memberEntityIds) {
        super();

        this.entityId = entityId;
        this.kind = kind != null ? kind : NodeKind.ENTITY;
        this.label = new VisLabel(name);
        this.prefabInstanceId = prefabInstanceId;
        this.prefabId = prefabId != null ? prefabId : "";
        this.prefabMemberIds = memberEntityIds != null
                ? new IntArray(memberEntityIds)
                : new IntArray();

        VisTable row = new VisTable();
        row.add(label).left();
        row.setUserObject(this.kind == NodeKind.PREFAB_INSTANCE
                ? new PrefabNodeRef(prefabInstanceId)
                : new NodeRef(entityId, this.kind));
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

    public boolean isPrefabInstanceNode() {
        return kind == NodeKind.PREFAB_INSTANCE;
    }

    public int getPrefabInstanceId() {
        return prefabInstanceId;
    }

    public String getPrefabId() {
        return prefabId;
    }

    public IntArray getPrefabMemberIds() {
        return new IntArray(prefabMemberIds);
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
