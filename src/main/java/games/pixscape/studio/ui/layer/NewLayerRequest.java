package games.pixscape.studio.ui.layer;

public record NewLayerRequest(
        String name,
        int type,
        boolean spatialActorLayer,
        int width,
        int height
) {
    public NewLayerRequest(String name, int type, int width, int height) {
        this(name, type, false, width, height);
    }
}
