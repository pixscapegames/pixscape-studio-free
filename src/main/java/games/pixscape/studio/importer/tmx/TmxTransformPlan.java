package games.pixscape.studio.importer.tmx;

public record TmxTransformPlan(boolean hasTransformFlags,
                               boolean horizontalFlip,
                               boolean verticalFlip,
                               boolean diagonalFlip,
                               boolean hexagonal120Flag) {
}
