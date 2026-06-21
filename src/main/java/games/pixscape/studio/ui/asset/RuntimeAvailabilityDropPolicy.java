package games.pixscape.studio.ui.asset;

import games.pixscape.studio.service.runtimeavailability.RuntimeAvailabilityCategory;
import games.pixscape.studio.ui.asset.dnd.DragPayload;

public final class RuntimeAvailabilityDropPolicy {

    private RuntimeAvailabilityDropPolicy() {
    }

    public static RuntimeAvailabilityCategory resolveCategory(DragPayload payload) {
        if (payload == null || payload.type == null) {
            return null;
        }

        return switch (payload.type) {
            case "image-file" -> RuntimeAvailabilityCategory.SPRITES;
            case "anim-sheet" -> RuntimeAvailabilityCategory.ANIMATIONS;
            case "particle" -> RuntimeAvailabilityCategory.PARTICLES;
            case "prefab" -> RuntimeAvailabilityCategory.PREFABS;
            case "tile-asset" -> RuntimeAvailabilityCategory.TILED_TILES;
            case "tiled-animation" -> RuntimeAvailabilityCategory.TILED_ANIMATIONS;
            default -> null;
        };
    }
}
