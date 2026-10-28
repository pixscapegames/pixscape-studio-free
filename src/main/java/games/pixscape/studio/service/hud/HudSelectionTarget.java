package games.pixscape.studio.service.hud;

import com.badlogic.gdx.math.Rectangle;

/** Immutable, actor-free description of one current HUD canvas interaction region. */
public record HudSelectionTarget(String nodeId, Type type,
                                 float x, float y, float width, float height,
                                 boolean selected) {
    public enum Type { ACTOR, CELL, PARENT }

    public HudSelectionTarget {
        if (nodeId == null || nodeId.isBlank()) throw new IllegalArgumentException("Node ID is required.");
        if (type == null) throw new IllegalArgumentException("Target type is required.");
    }

    public static HudSelectionTarget from(String nodeId, Type type,
                                          Rectangle bounds, boolean selected) {
        return new HudSelectionTarget(nodeId, type, bounds.x, bounds.y,
                bounds.width, bounds.height, selected);
    }

    public Rectangle bounds() { return new Rectangle(x, y, width, height); }
}
