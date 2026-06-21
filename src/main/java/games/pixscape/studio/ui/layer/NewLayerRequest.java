package games.pixscape.studio.ui.layer;

public record NewLayerRequest(
        String name,
        int type,
        int width,
        int height
) {
}
