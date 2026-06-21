package games.pixscape.studio.service.tiled;

import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.studio.event.EventFlow;

public final class TiledToolService {

    public enum Mode {
        BRUSH,
        RECT,
        ERASE,
        FILL
    }

    private Mode activeMode = Mode.BRUSH;
    private byte activeTransformFlags = TileTransformFlags.NONE;

    public void setMode(Mode mode) {
        if (mode == null) return;
        this.activeMode = mode;
        EventFlow.i().publish(new EventFlow.TiledToolChanged(mode, EventFlow.tag(this)));
    }

    public Mode getMode() {
        return activeMode;
    }

    public boolean is(Mode mode) {
        return activeMode == mode;
    }

    public byte getActiveTransformFlags() {
        return activeTransformFlags;
    }

    public void setActiveTransformFlags(byte flags) {
        byte sanitized = TileTransformFlags.sanitize(flags);
        if (this.activeTransformFlags == sanitized) return;
        this.activeTransformFlags = sanitized;
        EventFlow.i().publish(new EventFlow.TiledBrushTransformChanged(sanitized, EventFlow.tag(this)));
    }

    public void flipH() {
        setActiveTransformFlags(TileXformOps.flipH(activeTransformFlags));
    }

    public void flipV() {
        setActiveTransformFlags(TileXformOps.flipV(activeTransformFlags));
    }

    public void rotateCW() {
        setActiveTransformFlags(TileXformOps.rotCW(activeTransformFlags));
    }

    public void rotateCCW() {
        setActiveTransformFlags(TileXformOps.rotCCW(activeTransformFlags));
    }

    public void resetTransform() {
        setActiveTransformFlags(TileTransformFlags.NONE);
    }
}