package games.pixscape.studio.service.hud;

/** The eight conventional resize handles for a rectangular transform gizmo. */
public enum HudTransformHandle {
    NW(true, false, false, true),
    N(false, false, false, true),
    NE(false, true, false, true),
    W(true, false, false, false),
    E(false, true, false, false),
    SW(true, false, true, false),
    S(false, false, true, false),
    SE(false, true, true, false);

    private final boolean west;
    private final boolean east;
    private final boolean south;
    private final boolean north;

    HudTransformHandle(boolean west, boolean east, boolean south, boolean north) {
        this.west = west;
        this.east = east;
        this.south = south;
        this.north = north;
    }

    public boolean movesWest() { return west; }
    public boolean movesEast() { return east; }
    public boolean movesSouth() { return south; }
    public boolean movesNorth() { return north; }
    public boolean changesWidth() { return west || east; }
    public boolean changesHeight() { return south || north; }
}
