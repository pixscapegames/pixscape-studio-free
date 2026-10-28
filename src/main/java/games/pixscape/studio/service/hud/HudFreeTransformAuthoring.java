package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudPlacementKind;

/** Pixscape document adapter for the narrow, document-first FREE transform mutation. */
final class HudFreeTransformAuthoring {
    record FreeNode(String parentId, HudChild child) {}

    private HudFreeTransformAuthoring() {}

    static FreeNode find(HudDocumentV1 document, String nodeId) {
        return document == null || document.root == null || nodeId == null
                ? null : find(document.root, nodeId);
    }

    static boolean apply(HudDocumentV1 candidate, String nodeId, HudFreeTransform.Result result,
                         boolean writeWidth, boolean writeHeight) {
        FreeNode freeNode = find(candidate, nodeId);
        if (freeNode == null) return false;
        freeNode.child.free.offsetX = result.offsetX();
        freeNode.child.free.offsetY = result.offsetY();
        if (writeWidth) freeNode.child.node.actor.width = result.width();
        if (writeHeight) freeNode.child.node.actor.height = result.height();
        return true;
    }

    private static FreeNode find(HudNode parent, String nodeId) {
        for (HudChild child : parent.children) {
            if (nodeId.equals(child.node.id) && child.placementKind == HudPlacementKind.FREE) {
                return new FreeNode(parent.id, child);
            }
            FreeNode nested = find(child.node, nodeId);
            if (nested != null) return nested;
        }
        return null;
    }
}
