package games.pixscape.studio.service.tiled;

/** History-time rejection after exact atomic rollback. */
public final class TiledMutationRejectedException extends RuntimeException {
    private final TiledSpatialMutationRejection rejection;

    public TiledMutationRejectedException(TiledSpatialMutationRejection rejection) {
        super(rejection != null ? rejection.userMessage() : "Tiled Spatial mutation was rejected.",
                rejection != null ? rejection.cause() : null);
        this.rejection = rejection;
    }

    public TiledSpatialMutationRejection rejection() { return rejection; }
}
