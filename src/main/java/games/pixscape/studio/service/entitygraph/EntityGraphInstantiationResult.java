package games.pixscape.studio.service.entitygraph;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntIntMap;

public record EntityGraphInstantiationResult(
        IntArray createdIds,
        IntIntMap sourceToCreated,
        IntArray createdRootIds) {

    public EntityGraphInstantiationResult(IntArray createdIds, IntIntMap sourceToCreated) {
        this(createdIds, sourceToCreated, new IntArray());
    }

    public static EntityGraphInstantiationResult empty() {
        return new EntityGraphInstantiationResult(new IntArray(), new IntIntMap(), new IntArray());
    }
}
