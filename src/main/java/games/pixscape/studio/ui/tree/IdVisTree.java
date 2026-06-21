package games.pixscape.studio.ui.tree;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.kotcrab.vis.ui.widget.VisTree;

public class IdVisTree extends VisTree<EntityNode, Integer> {
    private final IntMap<EntityNode> primaryNodesByEntityId = new IntMap<>();
    private final IntMap<EntityNode> bodyNodesByEntityId = new IntMap<>();
    private final IntMap<EntityNode> mapNodes = new IntMap<>();
    private final IntMap<EntityNode> spatialBlockNodes = new IntMap<>();

    /**
     * Clears the tree and resets internal indexes.
     */
    public void clearNodes() {
        super.clearChildren();
        primaryNodesByEntityId.clear();
        bodyNodesByEntityId.clear();
        mapNodes.clear();
        spatialBlockNodes.clear();
    }

    /**
     * Call when creating/inserting a node to assign its logical ID.
     */
    public void registerNode(EntityNode node, int id) {
        node.setValue(id);

        if (node == null) return;
        if (node.getKind() == EntityNode.NodeKind.INFO) return;

        if (node.getKind() == EntityNode.NodeKind.BODY) {
            bodyNodesByEntityId.put(id, node);
        } else if (node.getKind() == EntityNode.NodeKind.SPATIAL_BLOCKS) {
            spatialBlockNodes.put(id, node);
        } else {
            primaryNodesByEntityId.put(id, node);
        }
    }

    public void registerMapNode(EntityNode node, int entityId) {
        if (node != null) {
            mapNodes.put(entityId, node);
        }
    }

    public EntityNode findMapNode(int entityId) {
        return mapNodes.get(entityId);
    }

    public void unregisterNodeById(int id) {
        primaryNodesByEntityId.remove(id);
        bodyNodesByEntityId.remove(id);
    }

    public void unregisterNode(EntityNode node) {
        if (node == null) return;

        int nodeId = node.getEntityId();
        if (node.getKind() == EntityNode.NodeKind.BODY) {
            if (bodyNodesByEntityId.get(nodeId) == node) {
                bodyNodesByEntityId.remove(nodeId);
            }
            return;
        }

        if (node.getKind() == EntityNode.NodeKind.SPATIAL_BLOCKS) {
            if (spatialBlockNodes.get(nodeId) == node) {
                spatialBlockNodes.remove(nodeId);
            }
            return;
        }

        if (primaryNodesByEntityId.get(nodeId) == node) {
            primaryNodesByEntityId.remove(nodeId);
            return;
        }

        IntMap.Keys it = bodyNodesByEntityId.keys();
        while (it.hasNext) {
            int key = it.next();
            if (bodyNodesByEntityId.get(key) == node) {
                bodyNodesByEntityId.remove(key);
                return;
            }
        }
    }

    /**
     * Returns the primary node (layer/entity) for an entityId.
     */
    @Override
    public EntityNode findNode(Integer value) {
        EntityNode n = primaryNodesByEntityId.get(value);
        if (n != null) return n;
        return findNodeByIntLinear(value, null);
    }

    public EntityNode findBodyNode(int entityId) {
        EntityNode n = bodyNodesByEntityId.get(entityId);
        if (n != null) return n;
        return findNodeByIntLinear(entityId, EntityNode.NodeKind.BODY);
    }

    public EntityNode findSpatialBlocksNode(int entityId) {
        EntityNode n = spatialBlockNodes.get(entityId);
        if (n != null) return n;
        return findNodeByIntLinear(entityId, EntityNode.NodeKind.SPATIAL_BLOCKS);
    }

    public EntityNode findNode(int entityId, EntityNode.NodeKind kind) {
        if (kind == EntityNode.NodeKind.BODY) {
            return findBodyNode(entityId);
        }
        if (kind == EntityNode.NodeKind.SPATIAL_BLOCKS) {
            return findSpatialBlocksNode(entityId);
        }
        if (kind == null) {
            return findNode(entityId);
        }
        EntityNode linear = findNodeByIntLinear(entityId, kind);
        if (linear != null) return linear;
        return kind == EntityNode.NodeKind.INFO ? null : primaryNodesByEntityId.get(entityId);
    }

    /**
     * Multi-selection from an IntArray. Selects only primary nodes.
     */
    public void selectIds(IntArray ids, boolean replace) {
        getSelection().setMultiple(true);
        if (replace) getSelection().clear();
        for (int i = 0; i < ids.size; i++) {
            EntityNode n = primaryNodesByEntityId.get(ids.get(i));
            if (n != null) getSelection().add(n);
        }
    }

    /**
     * Varargs variant. Selects only primary nodes.
     */
    public void selectIds(boolean replace, int... ids) {
        getSelection().setMultiple(true);
        if (replace) getSelection().clear();
        for (int id : ids) {
            EntityNode n = primaryNodesByEntityId.get(id);
            if (n != null) getSelection().add(n);
        }
    }

    private EntityNode findNodeByIntLinear(int id, EntityNode.NodeKind wantedKind) {
        Array<EntityNode> roots = getRootNodes();
        for (int i = 0; i < roots.size; i++) {
            EntityNode found = findNodeByIntLinear(roots.get(i), id, wantedKind);
            if (found != null) return found;
        }
        return null;
    }

    private EntityNode findNodeByIntLinear(EntityNode node, int id, EntityNode.NodeKind wantedKind) {
        if (node == null) return null;
        if (node.getEntityId() == id && matchesKind(node.getKind(), wantedKind)) return node;

        Object v = node.getValue();
        if (v instanceof Integer && ((Integer) v) == id && matchesKind(node.getKind(), wantedKind)) {
            return node;
        }

        Actor a = node.getActor();
        if (a != null) {
            Object uo = a.getUserObject();
            if (uo instanceof EntityNode.NodeRef ref) {
                if (ref.entityId() == id && matchesKind(ref.kind(), wantedKind)) {
                    return node;
                }
            }
            if (uo instanceof Integer && ((Integer) uo) == id && matchesKind(node.getKind(), wantedKind)) {
                return node;
            }
        }

        Array<EntityNode> children = node.getChildren();
        for (int i = 0; i < children.size; i++) {
            EntityNode found = findNodeByIntLinear(children.get(i), id, wantedKind);
            if (found != null) return found;
        }
        return null;
    }

    private boolean matchesKind(EntityNode.NodeKind actualKind, EntityNode.NodeKind wantedKind) {
        if (actualKind == null) return false;
        if (wantedKind != null) return actualKind == wantedKind;
        return actualKind != EntityNode.NodeKind.BODY
                && actualKind != EntityNode.NodeKind.SPATIAL_BLOCKS
                && actualKind != EntityNode.NodeKind.INFO;
    }
}
