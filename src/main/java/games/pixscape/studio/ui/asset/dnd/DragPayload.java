// DragPayload.java  (additions marked)
package games.pixscape.studio.ui.asset.dnd;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;

/**
 * Cross-window drag payload (in-memory, same JVM).
 */
public final class DragPayload {
    public enum Op {COPY, MOVE}

    public String type;       // "image","particle","animation",... ou "atlas-region"
    public String guid;       // optional
    public String path;       // file path (for "image"/"particle"/...)
    public Array<String> paths; // DnD multi-selection
    public int assetId = -1;
    public int tileAnimationId = -1;
    public int rows = 1;
    public int columns = 1;
    public Op op = Op.COPY;

    // --- NEW: for an atlas region ---
    public String atlasTag;    // ex: "main"
    public String regionPath;  // ex: "ui/buttons/play"

    // Ghost cursor
    public Pixmap ghostPixmap;
    public int hotspotX = 0;
    public int hotspotY = 0;
}
