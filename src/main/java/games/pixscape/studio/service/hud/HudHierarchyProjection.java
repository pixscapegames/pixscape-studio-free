package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudPlacementKind;
import games.pixscape.runtime.hud.document.HudTableCell;
import games.pixscape.runtime.hud.document.HudTableRow;
import games.pixscape.runtime.hud.document.HudDialogResultButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic document-order projection; it never creates or mutates HUD DTOs. */
public final class HudHierarchyProjection {
    private HudHierarchyProjection() {
    }

    public static List<HudHierarchyEntry> from(HudDocumentV1 document) {
        if (document == null || document.root == null) return List.of();
        List<HudHierarchyEntry> result = new ArrayList<>();
        append(document.root, HudPlacementKind.DIRECT, 0, result);
        return Collections.unmodifiableList(result);
    }

    private static void append(HudNode node, HudPlacementKind placement, int depth,
                               List<HudHierarchyEntry> result) {
        result.add(new HudHierarchyEntry(node.id, node.kind, placement, depth));
        if (node.dialog != null && node.dialog.resultButtons != null)
            for (HudDialogResultButton entry : node.dialog.resultButtons)
                if (entry != null && entry.button != null)
                    append(entry.button, HudPlacementKind.DIRECT, depth + 1, result);
        if (node.children == null) return;
        for (HudChild child : node.children) {
            if (child != null && child.node != null) {
                append(child.node, child.placementKind, depth + 1, result);
            }
        }
        if (node.table != null && node.table.rows != null) {
            for (HudTableRow row : node.table.rows) if (row != null && row.cells != null)
                for (HudTableCell cell : row.cells)
                    if (cell != null && cell.content != null)
                        append(cell.content, HudPlacementKind.CELL, depth + 1, result);
        }
    }
}
