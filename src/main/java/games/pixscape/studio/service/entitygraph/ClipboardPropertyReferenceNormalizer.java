package games.pixscape.studio.service.entitygraph;

import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.studio.service.property.PropertyReferenceMapper;

/** Converts internal Scene OBJECT stable IDs into graph-local source IDs. */
final class ClipboardPropertyReferenceNormalizer {
    private ClipboardPropertyReferenceNormalizer() { }

    static PropertySet normalize(PropertySet source, IntMap<Integer> stableToSource) {
        return PropertyReferenceMapper.remap(source, stableId -> {
            if (stableId == -1) return -1;
            if (stableToSource.containsKey(stableId)) return stableToSource.get(stableId);
            throw new IllegalArgumentException("Clipboard hierarchy contains an external OBJECT reference stableId "
                    + stableId + ".");
        });
    }
}
