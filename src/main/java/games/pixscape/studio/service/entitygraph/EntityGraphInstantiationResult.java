package games.pixscape.studio.service.entitygraph;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;

public record EntityGraphInstantiationResult(IntArray createdIds, IntIntMap sourceToCreated) {

    public static EntityGraphInstantiationResult empty() {
        return new EntityGraphInstantiationResult(new IntArray(), new IntIntMap());
    }
}
