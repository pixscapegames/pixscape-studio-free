package games.pixscape.studio.service.hud;

/** Controlled rejection of an authored HUD transaction before publication. */
public final class HudEditRejectedException extends RuntimeException {
    public HudEditRejectedException(String message) {
        super(message);
    }

    public HudEditRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
