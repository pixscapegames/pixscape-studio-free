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
        JOINT,
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
    private final IntArray prefabZOrderMemberIds;

    public EntityNode(String name, Drawable icon, int entityId, boolean selectable) {
        this(name, icon, entityId, selectable, NodeKind.ENTITY);
    }

    public EntityNode(String name, Drawable icon, int entityId, boolean selectable, NodeKind kind) {
        this(name, icon, entityId, selectable, kind, -1, "", null, null);
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
                memberEntityIds,
                memberEntityIds);
    }

    public static EntityNode prefabInstance(
            String prefabId,
            Drawable icon,
            int prefabInstanceId,
            IntArray allMemberEntityIds,
            IntArray zOrderMemberEntityIds,
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
                allMemberEntityIds,
                zOrderMemberEntityIds);
    }

    private EntityNode(
            String name,
            Drawable icon,
            int entityId,
            boolean selectable,
            NodeKind kind,
            int prefabInstanceId,
            String prefabId,
            IntArray memberEntityIds,
            IntArray zOrderMemberEntityIds) {
        super();

        this.entityId = entityId;
        this.kind = kind != null ? kind : NodeKind.ENTITY;
        this.label = new VisLabel(name);
        this.prefabInstanceId = prefabInstanceId;
        this.prefabId = prefabId != null ? prefabId : "";
        this.prefabMemberIds = memberEntityIds != null
                ? new IntArray(memberEntityIds)
                : new IntArray();
        this.prefabZOrderMemberIds = zOrderMemberEntityIds != null
                ? new IntArray(zOrderMemberEntityIds)
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

    public boolean isJointNode() {
        return kind == NodeKind.JOINT;
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

    public IntArray getPrefabZOrderMemberIds() {
        return new IntArray(prefabZOrderMemberIds);
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
