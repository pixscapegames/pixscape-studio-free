package games.pixscape.studio.service.hud;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudTableRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Builds the local, non-recursive HUD navigation targets for the current materialization. */
final class HudSelectionOverlayProjection {
    private HudSelectionOverlayProjection() {}

    static List<HudSelectionTarget> from(HudDocumentV1 document,
                                          Map<String, Actor> actorsByNodeId,
                                          Map<String, Cell<?>> cellsById,
                                          String selectedNodeId,
                                          String selectedCellId,
                                          Actor overlay) {
        if (document == null || document.root == null || actorsByNodeId == null || overlay == null) {
            return List.of();
        }
        HudNode selected = find(document.root, selectedNodeId);
        // The document root is structural. It remains available in the hierarchy for
        // inspection, but must never turn empty canvas space into a canvas target.
        // Treat no selection exactly like the root selection: expose its children.
        if (selected == null) selected = document.root;

        List<HudSelectionTarget> targets = new ArrayList<>();
        if (cellsById != null) {
            for (Map.Entry<String, Cell<?>> entry : cellsById.entrySet()) {
                HudTableCell source = findCell(document.root, entry.getKey());
                // An occupied cell remains reachable through its widget's regular actor target;
                // only a blank native cell becomes a selectable canvas surface.
                if (source == null || source.content != null) continue;
                Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(entry.getValue(), overlay,
                        new Rectangle());
                if (usable(bounds)) targets.add(HudSelectionTarget.from(entry.getKey(),
                        HudSelectionTarget.Type.CELL, bounds, entry.getKey().equals(selectedCellId)));
            }
        }
        HudNode parent = findParent(document.root, selected.id);
        Actor selectedActor = actorsByNodeId.get(selected.id);
        Actor parentActor = parent == null ? null : actorsByNodeId.get(parent.id);
        if (parent != null && parent != document.root) {
            if (parentActor != null) addIfVisible(targets, parentTarget(parent.id, parentActor, overlay));
        }

        List<Candidate> selectableSurfaces = new ArrayList<>();

        // Keep immediate siblings reachable from canvas while a non-root node is selected. This is
        // deliberately local navigation: siblings and the selected node's children are the only peers
        // added here; the root itself remains structural and never becomes a target.
        if (parent != null) {
            for (HudNode sibling : childNodes(parent)) {
                if (sibling.id.equals(selected.id)) continue;
                addNodeTarget(selectableSurfaces, sibling, actorsByNodeId, overlay);
            }
        }
        for (HudNode child : childNodes(selected)) {
            addNodeTarget(selectableSurfaces, child, actorsByNodeId, overlay);
        }
        // TargetActor hit testing runs from the last child back. Matching the materialized Actor draw
        // order preserves normal visual stacking when a child and a sibling overlap. Transform handles
        // remain higher priority through HudCanvasSelectionInputListener's transform check.
        selectableSurfaces.sort(Comparator.comparing(Candidate::actor,
                HudSelectionOverlayProjection::compareVisualOrder));
        for (Candidate candidate : selectableSurfaces) targets.add(candidate.target());

        // Keep the selected surface non-interactive so children/back win hits.
        if (selected != document.root && selectedActor != null) {
            addIfVisible(targets, actorTarget(selected.id, selectedActor, overlay, true));
        }
        return List.copyOf(targets);
    }

    private static void addChildTarget(List<Candidate> targets, HudChild child, Actor parentActor,
                                       Map<String, Actor> actorsByNodeId, Actor overlay) {
        Actor childActor = actorsByNodeId.get(child.node.id);
        if (childActor == null) return;
        if (child.placementKind == HudPlacementKind.CELL && parentActor instanceof Table table) {
            Cell<?> cell = table.getCell(childActor);
            if (cell != null) {
                Rectangle bounds = HudOverlayGeometry.visibleCellBoundsInOverlay(
                        cell, childActor, overlay, new Rectangle());
                if (usable(bounds)) targets.add(new Candidate(HudSelectionTarget.from(child.node.id,
                        HudSelectionTarget.Type.CELL, bounds, false), childActor));
                return;
            }
        }
        HudSelectionTarget target = actorTarget(child.node.id, childActor, overlay, false);
        if (usable(target.bounds())) targets.add(new Candidate(target, childActor));
    }

    private static void addNodeTarget(List<Candidate> targets, HudNode node,
                                      Map<String, Actor> actorsByNodeId, Actor overlay) {
        Actor actor = actorsByNodeId.get(node.id);
        if (actor == null) return;
        HudSelectionTarget target = actorTarget(node.id, actor, overlay, false);
        if (usable(target.bounds())) targets.add(new Candidate(target, actor));
    }

    private static HudSelectionTarget actorTarget(String nodeId, Actor actor,
                                                   Actor overlay, boolean selected) {
        Rectangle bounds = HudOverlayGeometry.visibleActorBoundsInOverlay(actor, overlay, new Rectangle());
        return HudSelectionTarget.from(nodeId, HudSelectionTarget.Type.ACTOR, bounds, selected);
    }

    private static HudSelectionTarget parentTarget(String nodeId, Actor actor, Actor overlay) {
        Rectangle bounds = HudOverlayGeometry.visibleActorBoundsInOverlay(actor, overlay, new Rectangle());
        return HudSelectionTarget.from(nodeId, HudSelectionTarget.Type.PARENT, bounds, false);
    }

    private static void addIfVisible(List<HudSelectionTarget> targets, HudSelectionTarget target) {
        if (usable(target.bounds())) targets.add(target);
    }

    private static boolean usable(Rectangle bounds) {
        return bounds.width > 0f && bounds.height > 0f;
    }

    private static int compareVisualOrder(Actor first, Actor second) {
        if (first == second) return 0;
        List<Actor> firstPath = actorPath(first);
        List<Actor> secondPath = actorPath(second);
        int shared = 0;
        int limit = Math.min(firstPath.size(), secondPath.size());
        while (shared < limit && firstPath.get(shared) == secondPath.get(shared)) shared++;
        if (shared == limit) return Integer.compare(firstPath.size(), secondPath.size());
        return Integer.compare(firstPath.get(shared).getZIndex(), secondPath.get(shared).getZIndex());
    }

    private static List<Actor> actorPath(Actor actor) {
        List<Actor> path = new ArrayList<>();
        for (Actor current = actor; current != null; current = current.getParent()) path.add(0, current);
        return path;
    }

    private static HudNode find(HudNode node, String nodeId) {
        if (node == null || nodeId == null) return null;
        if (nodeId.equals(node.id)) return node;
        for (HudNode child : childNodes(node)) {
            HudNode found = find(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    private static HudNode findParent(HudNode node, String childId) {
        for (HudNode child : childNodes(node)) {
            if (childId.equals(child.id)) return node;
            HudNode parent = findParent(child, childId);
            if (parent != null) return parent;
        }
        return null;
    }

    private static HudTableCell findCell(HudNode node, String cellId) {
        if (node == null || cellId == null) return null;
        if (node.table != null && node.table.rows != null) for (HudTableRow row : node.table.rows) {
            if (row == null || row.cells == null) continue;
            for (HudTableCell cell : row.cells) {
                if (cell == null) continue;
                if (cellId.equals(cell.id)) return cell;
                HudTableCell nested = findCell(cell.content, cellId);
                if (nested != null) return nested;
            }
        }
        if (node.children != null) for (HudChild child : node.children) if (child != null) {
            HudTableCell nested = findCell(child.node, cellId);
            if (nested != null) return nested;
        }
        return null;
    }

    private static List<HudNode> childNodes(HudNode node) {
        List<HudNode> children = new ArrayList<>();
        if (node == null) return children;
        if (node.dialog != null && node.dialog.resultButtons != null)
            for (var entry : node.dialog.resultButtons)
                if (entry != null && entry.button != null) children.add(entry.button);
        if (node.children != null) for (HudChild child : node.children) {
            if (child != null && child.node != null) children.add(child.node);
        }
        if (node.table != null && node.table.rows != null) for (HudTableRow row : node.table.rows) {
            if (row == null || row.cells == null) continue;
            for (HudTableCell cell : row.cells) {
                if (cell != null && cell.content != null) children.add(cell.content);
            }
        }
        return children;
    }

    private record Candidate(HudSelectionTarget target, Actor actor) {}
}
