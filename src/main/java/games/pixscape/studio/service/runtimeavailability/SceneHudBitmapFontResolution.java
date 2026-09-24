package games.pixscape.studio.service.runtimeavailability;

import java.util.List;
import java.util.Map;

/** Shared GL-free mirror of LibGDX Skin's BitmapFont atlas-region selection. */
final class SceneHudBitmapFontResolution {
    enum Result {
        COMPLETE_UNINDEXED,
        COMPLETE_INDEXED,
        ABSENT,
        INCOMPLETE
    }

    private SceneHudBitmapFontResolution() {
    }

    static Result resolve(Map<String, ? extends List<Integer>> indexesByName,
                          String descriptorStem, int pageCount) {
        List<Integer> indexes = indexesByName.get(descriptorStem);
        if (indexes == null || indexes.isEmpty()) return Result.ABSENT;
        boolean indexedZero = indexes.contains(0);
        boolean unindexed = indexes.contains(-1);
        // Skin first asks for stem_0, then only falls through to bare stem when no index zero exists.
        if (!indexedZero && !unindexed) return Result.ABSENT;
        if (!indexedZero) return pageCount == 1 ? Result.COMPLETE_UNINDEXED : Result.INCOMPLETE;
        for (int page = 0; page < pageCount; page++) {
            if (!indexes.contains(page)) return Result.INCOMPLETE;
        }
        return Result.COMPLETE_INDEXED;
    }
}
