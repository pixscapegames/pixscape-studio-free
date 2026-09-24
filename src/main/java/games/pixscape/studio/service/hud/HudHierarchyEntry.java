package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudPlacementKind;

/** Read-only presentation of one persisted HUD node. */
public record HudHierarchyEntry(String nodeId, HudNodeKind kind,
                                HudPlacementKind placement, int depth) {
}
